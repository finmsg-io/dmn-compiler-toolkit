package io.finmsg.dmn.generator.sparksql;
import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/** Exact decimal multiplication for a two-input Cartesian loop. */
final class SparkSqlExactProductInput {
	private record Plan(RuntimeValueReference left, RuntimeValueReference right) {
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent() || decision.type().kind() != RuntimeTypeKind.LIST
				|| decision.type().elementType() == null
				|| decision.type().elementType().kind() != RuntimeTypeKind.NUMBER
				|| !(decision.expression().orElse(null) instanceof RuntimeForExpression loop) || loop.partialSlot() >= 0
				|| loop.iterations().size() != 2 || !(loop.result() instanceof RuntimeBinaryExpression product)
				|| product.operator() != RuntimeBinaryOperator.MULTIPLY
				|| !(product.left() instanceof RuntimeLocalReference left)
				|| !(product.right() instanceof RuntimeLocalReference right) || left.lexicalDepth() != 0
				|| right.lexicalDepth() != 0)
			return null;
		var a = loop.iterations().get(0);
		var b = loop.iterations().get(1);
		if (a.end().isPresent() || b.end().isPresent() || a.localSlot() == b.localSlot()
				|| a.localSlot() != left.localSlot() || b.localSlot() != right.localSlot()
				|| !(a.source() instanceof RuntimeValueReference x) || !(b.source() instanceof RuntimeValueReference y))
			return null;
		for (var source : List.of(x, y))
			if (!source.type().equals(decision.type()) || model.inputs().stream()
					.noneMatch(i -> i.valueSlot() == source.sourceSlot() && i.type().equals(source.type())))
				return null;
		return new Plan(x, y);
	}
	static boolean supports(RuntimeModel model, RuntimeDecision d) {
		return plan(model, d) != null;
	}
	static Set<Integer> ids(RuntimeModel model) {
		var ids = new LinkedHashSet<Integer>();
		for (var d : model.decisions())
			if (supports(model, d))
				ids.add(d.id());
		return ids;
	}
	static String column(int id, Map<Integer, String> names) {
		String name = "__dmn_product_" + id;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(name.toLowerCase(Locale.ROOT)))
			name += "_";
		return name;
	}
	static StructType type() {
		var number = new StructType().add("digits", DataTypes.StringType).add("scale", DataTypes.LongType)
				.add("negative", DataTypes.BooleanType).add("invalid", DataTypes.BooleanType);
		var list = DataTypes.createArrayType(number, false);
		return new StructType().add("left", list).add("right", list);
	}
	private static Object numbers(Object value) {
		if (value == null)
			return null;
		var rows = new ArrayList<Object>();
		for (Object item : (List<?>) value) {
			if (item == null) {
				rows.add(RowFactory.create(null, 0L, false, false));
				continue;
			}
			try {
				var number = item instanceof BigDecimal d ? d : new BigDecimal(item.toString());
				rows.add(RowFactory.create(number.unscaledValue().abs().toString(), (long) number.scale(),
						number.signum() < 0, false));
			} catch (NumberFormatException invalid) {
				rows.add(RowFactory.create(null, 0L, false, true));
			}
		}
		return rows;
	}
	static Object project(RuntimeModel model, int id, Map<Integer, ?> values) {
		var d = model.decisions().stream().filter(x -> x.id() == id).findFirst().orElseThrow();
		var p = Objects.requireNonNull(plan(model, d));
		return RowFactory.create(numbers(DmnRuntime.coerce(values.get(p.left().sourceSlot()), p.left().type())),
				numbers(DmnRuntime.coerce(values.get(p.right().sourceSlot()), p.right().type())));
	}
	static String sql(RuntimeDecision decision, Map<Integer, String> names) {
		String source = "`" + column(decision.id(), names) + "`";
		return "CASE WHEN " + source + ".left IS NULL THEN CAST(NULL AS ARRAY<BINARY>) WHEN size(" + source
				+ ".left)=0 THEN CAST(array() AS ARRAY<BINARY>) WHEN " + source
				+ ".right IS NULL THEN CAST(NULL AS ARRAY<BINARY>) ELSE flatten(transform(" + source
				+ ".left, product_a -> transform(" + source + ".right, product_b -> " + multiply() + "))) END";
	}
	private static String multiply() {
		String sum = "product_state.carry + aggregate(sequence(greatest(0,product_pos-length(product_bd)+1),least(product_pos,length(product_ad)-1)),0L,(product_sum,product_j) -> product_sum + CAST(substring(product_ad,product_j+1,1) AS BIGINT)*CAST(substring(product_bd,product_pos-product_j+1,1) AS BIGINT))";
		String merge = let("product_sum", sum,
				"named_struct('carry',coalesce(CAST(floor(product_sum/10.0) AS BIGINT),0L),'digits',coalesce(concat(CAST(pmod(product_sum,10) AS STRING),product_state.digits),''))");
		String digits = "aggregate(sequence(0,length(product_ad)+length(product_bd)-2),named_struct('carry',0L,'digits',''),(product_state,product_pos) -> "
				+ merge
				+ ",product_done -> concat(CASE WHEN product_done.carry=0 THEN '' ELSE CAST(product_done.carry AS STRING) END,product_done.digits))";
		digits = let("product_ad", "reverse(product_a.digits)", let("product_bd", "reverse(product_b.digits)", digits));
		String coefficient = "CASE WHEN product_a.digits='0' OR product_b.digits='0' THEN '0' ELSE regexp_replace("
				+ digits + ",'^0+(?!$)','') END";
		String signed = "CASE WHEN product_a.negative<>product_b.negative AND product_digits<>'0' THEN concat('-',product_digits) ELSE product_digits END";
		String scale = "greatest(-2147483648L,least(2147483647L,product_scale))";
		String payload = let("product_signed", signed, "concat(unhex('444D4E0113'),unhex(lpad(right(hex(" + scale
				+ "),8),8,'0')),unhex(lpad(hex(length(product_signed)),8,'0')),encode(product_signed,'UTF-8'))");
		String result = let("product_digits", coefficient, payload);
		result = "CASE WHEN (product_scale < -2147483648L OR product_scale > 2147483647L) AND product_a.digits<>'0' THEN CAST(raise_error('FEEL product scale overflow') AS BINARY) ELSE "
				+ result + " END";
		result = let("product_scale", "product_a.scale+product_b.scale", result);
		return "CASE WHEN product_a.invalid OR product_b.invalid THEN CAST(raise_error('Invalid FEEL product number') AS BINARY) WHEN product_a.digits IS NULL OR product_b.digits IS NULL THEN CAST(raise_error('Null FEEL product result') AS BINARY) ELSE "
				+ result + " END";
	}
	private static String let(String name, String value, String expression) {
		return "element_at(transform(array(" + value + ")," + name + " -> " + expression + "),1)";
	}
}

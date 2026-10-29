package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.*;
import java.math.BigDecimal;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/**
 * Exact nested filters followed by first-item selection and a string result.
 */
final class SparkSqlIndexedJoinInput {
	private record Plan(RuntimeValueReference outer, RuntimeValueReference inner, RuntimeValueReference selector,
			String outerKey, String innerKey, String innerName, String result) {
	}
	private static boolean first(RuntimeFilterExpression filter) {
		if (!(filter.filter() instanceof RuntimeConstant c) || c.kind() != RuntimeConstantKind.NUMBER)
			return false;
		try {
			return new BigDecimal(c.value()).compareTo(BigDecimal.ONE) == 0;
		} catch (NumberFormatException invalid) {
			return false;
		}
	}
	private static boolean field(RuntimeValueReference source, RuntimePathExpression member, RuntimeTypeKind kind) {
		return source.type().kind() == RuntimeTypeKind.LIST && source.type().elementType() != null
				&& source.type().elementType().kind() == RuntimeTypeKind.CONTEXT
				&& source.type().elementType().fieldLayout().stream().anyMatch(f -> f.name().equals(member.member())
						&& f.type().kind() == kind && (member.fieldIndex() < 0 || member.fieldIndex() == f.index()));
	}
	private static boolean local(RuntimePathExpression path, RuntimeFilterExpression filter) {
		return path.source() instanceof RuntimeLocalReference local && local.lexicalDepth() == 0
				&& local.localSlot() == filter.localSlot();
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.type().kind() != RuntimeTypeKind.STRING || decision.decisionTable().isPresent()
				|| !(decision.expression().orElse(null) instanceof RuntimeFilterExpression resultFirst)
				|| !first(resultFirst) || !(resultFirst.source() instanceof RuntimePathExpression result)
				|| !(result.source() instanceof RuntimeFilterExpression outerFilter)
				|| !(outerFilter.source() instanceof RuntimeValueReference outer)
				|| !(outerFilter.filter() instanceof RuntimeBinaryExpression outerEqual)
				|| outerEqual.operator() != RuntimeBinaryOperator.EQUAL
				|| !(outerEqual.left() instanceof RuntimePathExpression outerKey) || !local(outerKey, outerFilter)
				|| !(outerEqual.right() instanceof RuntimeFilterExpression innerFirst) || !first(innerFirst)
				|| !(innerFirst.source() instanceof RuntimePathExpression innerKey)
				|| !(innerKey.source() instanceof RuntimeFilterExpression innerFilter)
				|| !(innerFilter.source() instanceof RuntimeValueReference inner)
				|| !(innerFilter.filter() instanceof RuntimeBinaryExpression innerEqual)
				|| innerEqual.operator() != RuntimeBinaryOperator.EQUAL
				|| !(innerEqual.left() instanceof RuntimePathExpression innerName) || !local(innerName, innerFilter)
				|| !(innerEqual.right() instanceof RuntimeValueReference selector)
				|| selector.type().kind() != RuntimeTypeKind.STRING || !field(outer, outerKey, RuntimeTypeKind.NUMBER)
				|| !field(outer, result, RuntimeTypeKind.STRING) || !field(inner, innerKey, RuntimeTypeKind.NUMBER)
				|| !field(inner, innerName, RuntimeTypeKind.STRING)
				|| !SparkSqlFeelValueCodec.isNativeType(outer.type())
				|| !SparkSqlFeelValueCodec.isNativeType(inner.type()))
			return null;
		for (var source : List.of(outer, inner, selector))
			if (model.inputs().stream()
					.noneMatch(i -> i.valueSlot() == source.sourceSlot() && i.type().equals(source.type())))
				return null;
		return new Plan(outer, inner, selector, outerKey.member(), innerKey.member(), innerName.member(),
				result.member());
	}
	static boolean supports(RuntimeModel model, RuntimeDecision decision) {
		return plan(model, decision) != null;
	}
	static Set<Integer> ids(RuntimeModel model) {
		var ids = new LinkedHashSet<Integer>();
		for (var d : model.decisions())
			if (supports(model, d))
				ids.add(d.id());
		return ids;
	}
	static String column(int id, Map<Integer, String> names) {
		String column = "__dmn_join_" + id;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(column.toLowerCase(Locale.ROOT)))
			column += "_";
		return column;
	}
	static StructType type() {
		var key = new StructType().add("kind", DataTypes.StringType).add("key", DataTypes.StringType).add("invalid",
				DataTypes.BooleanType);
		var item = new StructType().add("number", key).add("name", DataTypes.StringType)
				.add("text", DataTypes.StringType).add("nullRow", DataTypes.BooleanType);
		return new StructType().add("outer", DataTypes.createArrayType(item, false))
				.add("inner", DataTypes.createArrayType(item, false)).add("selector", DataTypes.StringType);
	}
	private static Object numericKey(Object raw) {
		if (raw == null)
			return RowFactory.create("NULL", null, false);
		boolean list = raw instanceof List<?>;
		Object value = list ? ((List<?>) raw).getFirst() : raw;
		String kind = list ? "LIST_NUMBER" : "NUMBER";
		try {
			var decimal = value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
			return RowFactory.create(kind, SparkSqlNumericFilterInput.key(decimal), false);
		} catch (NumberFormatException invalid) {
			return RowFactory.create(kind, null, true);
		}
	}
	// Match Java String equality, including its UTF-16 code units.
	private static String stringKey(Object raw) {
		if (raw == null)
			return "NULL";
		if (!(raw instanceof String text))
			return null;
		var key = new StringBuilder("STRING:");
		for (int i = 0; i < text.length(); i++)
			key.append(String.format(Locale.ROOT, "%04x", (int) text.charAt(i)));
		return key.toString();
	}
	private static Object rows(Object raw, String number, String name, String result) {
		if (raw == null)
			return null;
		var rows = new ArrayList<Object>();
		for (Object item : (List<?>) raw) {
			Map<?, ?> map = item instanceof Map<?, ?> m
					? m
					: item instanceof RuntimeContextValue c ? c.namedFields() : Map.of();
			Object text = result == null
					? null
					: DmnRuntime.coerce(map.get(result), RuntimeType.scalar(RuntimeTypeKind.STRING));
			rows.add(RowFactory.create(numericKey(map.get(number)), stringKey(name == null ? null : map.get(name)),
					text instanceof CharSequence ? text.toString() : null, item == null));
		}
		return rows;
	}
	static Object project(RuntimeModel model, int id, Map<Integer, ?> values) {
		var decision = model.decisions().stream().filter(d -> d.id() == id).findFirst().orElseThrow();
		var p = Objects.requireNonNull(plan(model, decision));
		return RowFactory.create(
				rows(DmnRuntime.coerce(values.get(p.outer().sourceSlot()), p.outer().type()), p.outerKey(), null,
						p.result()),
				rows(DmnRuntime.coerce(values.get(p.inner().sourceSlot()), p.inner().type()), p.innerKey(),
						p.innerName(), null),
				stringKey(DmnRuntime.coerce(values.get(p.selector().sourceSlot()), p.selector().type())));
	}
	static String sql(RuntimeDecision decision, Map<Integer, String> names) {
		String source = "`" + column(decision.id(), names) + "`";
		String selected = "filter(" + source + ".inner, join_i -> join_i.name = " + source + ".selector)";
		String key = "CASE WHEN " + source
				+ ".inner IS NULL THEN named_struct('kind','NULL','key',CAST(NULL AS STRING),'invalid',FALSE) ELSE try_element_at(join_selected,1).number END";
		String match = "CASE WHEN join_d.number.kind = join_key.kind THEN CASE WHEN join_d.number.kind='NULL' THEN TRUE WHEN join_d.number.invalid OR join_key.invalid THEN CAST(raise_error('Invalid FEEL join number') AS BOOLEAN) ELSE join_d.number.key = join_key.key END ELSE FALSE END";
		String predicate = "CASE WHEN (" + match
				+ ") THEN CASE WHEN join_d.nullRow THEN CAST(raise_error('Null matched FEEL join row') AS BOOLEAN) ELSE TRUE END ELSE FALSE END";
		String result = "try_element_at(filter(" + source + ".outer, join_d -> " + predicate + "),1).text";
		result = let("join_key", key, result);
		result = "CASE WHEN exists(join_selected,join_i -> join_i.nullRow) THEN CAST(raise_error('Null matched FEEL join row') AS STRING) ELSE "
				+ result + " END";
		return "CASE WHEN " + source + ".outer IS NULL OR size(" + source + ".outer)=0 THEN CAST(NULL AS STRING) ELSE "
				+ let("join_selected", selected, result) + " END";
	}
	private static String let(String name, String value, String expression) {
		return "element_at(transform(array(" + value + ")," + name + " -> " + expression + "),1)";
	}
}

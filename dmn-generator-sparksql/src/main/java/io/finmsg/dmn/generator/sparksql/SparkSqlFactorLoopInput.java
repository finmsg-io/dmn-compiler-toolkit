package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/** Exact integral-quotient predicates over a direct numeric-list input. */
final class SparkSqlFactorLoopInput {
	private record Plan(int valueSlot, int factorsSlot) {
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent() || decision.type().kind() != RuntimeTypeKind.LIST
				|| decision.type().elementType() == null
				|| decision.type().elementType().kind() != RuntimeTypeKind.BOOLEAN
				|| !(decision.expression().orElse(null) instanceof RuntimeForExpression loop) || loop.partialSlot() >= 0
				|| loop.iterations().size() != 1 || !(loop.result() instanceof RuntimeBinaryExpression equal)
				|| equal.operator() != RuntimeBinaryOperator.EQUAL
				|| !(equal.left() instanceof RuntimeBinaryExpression quotient)
				|| quotient.operator() != RuntimeBinaryOperator.DIVIDE
				|| !(quotient.left() instanceof RuntimeValueReference value)
				|| value.type().kind() != RuntimeTypeKind.NUMBER
				|| !(quotient.right() instanceof RuntimeLocalReference factor) || factor.lexicalDepth() != 0)
			return null;
		var iteration = loop.iterations().getFirst();
		if (iteration.end().isPresent() || iteration.localSlot() != factor.localSlot()
				|| factor.type().kind() != RuntimeTypeKind.NUMBER
				|| !(iteration.source() instanceof RuntimeValueReference factors)
				|| factors.type().kind() != RuntimeTypeKind.LIST || factors.type().elementType() == null
				|| factors.type().elementType().kind() != RuntimeTypeKind.NUMBER)
			return null;
		List<RuntimeExpression> args;
		if (equal.right() instanceof RuntimeInvocationExpression round && round.function().orElse("").equals("decimal")
				&& round.target().isEmpty() && round.namedArguments().isEmpty())
			args = round.positionalArguments();
		else if (equal.right() instanceof RuntimeFunctionCall round && round.function().equals("decimal"))
			args = round.arguments();
		else
			return null;
		if (args.size() != 2 || !args.getFirst().equals(quotient) || !(args.get(1) instanceof RuntimeConstant scale)
				|| scale.kind() != RuntimeConstantKind.NUMBER)
			return null;
		try {
			if (new BigDecimal(scale.value()).signum() != 0)
				return null;
		} catch (NumberFormatException invalid) {
			return null;
		}
		for (var input : List.of(value, factors))
			if (model.inputs().stream()
					.noneMatch(i -> i.valueSlot() == input.sourceSlot() && i.type().equals(input.type())))
				return null;
		return new Plan(value.sourceSlot(), factors.sourceSlot());
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
	static long[] slots(RuntimeModel model, RuntimeDecision decision) {
		var p = Objects.requireNonNull(plan(model, decision));
		return new long[]{p.valueSlot(), p.factorsSlot()};
	}
	static String column(int id, Map<Integer, String> names) {
		String name = "__dmn_factors_" + id;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(name.toLowerCase(Locale.ROOT)))
			name += "_";
		return name;
	}
	static StructType type() {
		return new StructType().add("value", SparkSqlDecimalSql.type()).add("factors",
				DataTypes.createArrayType(SparkSqlDecimalSql.type(), false));
	}
	static Object project(long[] slots, Map<Integer, ?> values) {
		var number = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
		Object source = DmnRuntime.coerce(values.get(Math.toIntExact(slots[1])),
				RuntimeType.element(RuntimeTypeKind.LIST, number));
		Object factors = source == null ? null : ((List<?>) source).stream().map(SparkSqlDecimalSql::row).toList();
		return RowFactory.create(
				SparkSqlDecimalSql.row(DmnRuntime.coerce(values.get(Math.toIntExact(slots[0])), number)), factors);
	}
	static String sql(RuntimeDecision decision, Map<Integer, String> names) {
		String source = "`" + column(decision.id(), names) + "`";
		return "transform(" + source + ".factors,factor_item -> " + SparkSqlDecimalSql.let("factor_quotient",
				SparkSqlDecimalSql.divide(source + ".value", "factor_item"),
				"CASE WHEN factor_quotient.digits IS NULL OR factor_quotient.digits='0' THEN TRUE WHEN abs(factor_quotient.scale)>=715827883L THEN CAST(raise_error('FEEL decimal rounding scale overflow') AS BOOLEAN) WHEN factor_quotient.scale<=0 THEN TRUE ELSE length(factor_quotient.digits)-length(regexp_replace(factor_quotient.digits,'0+$',''))>=factor_quotient.scale END")
				+ ")";
	}
}

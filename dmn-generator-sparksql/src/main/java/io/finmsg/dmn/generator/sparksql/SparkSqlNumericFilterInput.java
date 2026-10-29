package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.*;
import org.apache.spark.sql.RowFactory;

/**
 * Exact numeric predicates for terminal filters and quantifiers over typed
 * context lists.
 */
final class SparkSqlNumericFilterInput {
	private record Plan(RuntimeValueReference source, String number, String text, String key,
			RuntimeBinaryOperator operator, RuntimeQuantifier quantifier, BigDecimal literal) {
	}

	static Set<Integer> ids(RuntimeModel model) {
		var ids = new LinkedHashSet<Integer>();
		for (var decision : model.decisions())
			if (supports(model, decision))
				ids.add(decision.id());
		return ids;
	}
	static boolean supports(RuntimeModel model, RuntimeDecision decision) {
		return plan(model, decision) != null;
	}

	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.expression().orElse(null) instanceof RuntimeQuantifiedExpression quantified)
			return quantifiedPlan(model, decision, quantified);
		if (decision.decisionTable().isPresent()
				|| !(decision.expression().orElse(null) instanceof RuntimePathExpression result)
				|| !(result.source() instanceof RuntimeFilterExpression filter) || filter.localSlot() < 0
				|| filter.localSlot() >= 2048 || !(filter.source() instanceof RuntimeValueReference source)
				|| source.type().kind() != RuntimeTypeKind.LIST || source.type().elementType() == null
				|| source.type().elementType().kind() != RuntimeTypeKind.CONTEXT
				|| !SparkSqlFeelValueCodec.isNativeType(source.type())
				|| model.inputs().stream()
						.noneMatch(i -> i.valueSlot() == source.sourceSlot() && i.type().equals(source.type()))
				|| !(filter.filter() instanceof RuntimeBinaryExpression equal)
				|| equal.operator() != RuntimeBinaryOperator.EQUAL)
			return null;
		var declared = decision.type();
		if (declared.kind() != RuntimeTypeKind.ANY
				&& !(declared.kind() == RuntimeTypeKind.LIST && declared.elementType() != null
						&& Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING).contains(declared.elementType().kind())))
			return null;
		RuntimeExpression left = equal.left(), right = equal.right();
		if (left instanceof RuntimeConstant) {
			left = equal.right();
			right = equal.left();
		}
		if (!(left instanceof RuntimePathExpression member) || member.type().kind() != RuntimeTypeKind.NUMBER
				|| !(member.source() instanceof RuntimeLocalReference local) || local.lexicalDepth() != 0
				|| local.localSlot() != filter.localSlot() || !(right instanceof RuntimeConstant number)
				|| number.kind() != RuntimeConstantKind.NUMBER || number.value().length() > 256)
			return null;
		var fields = source.type().elementType().fieldLayout();
		if (fields.stream()
				.noneMatch(f -> f.name().equals(member.member()) && f.type().kind() == RuntimeTypeKind.NUMBER)
				|| fields.stream()
						.noneMatch(f -> f.name().equals(result.member()) && f.type().kind() == RuntimeTypeKind.STRING))
			return null;
		try {
			var value = new BigDecimal(number.value());
			if (value.precision() > 128 || Math.abs((long) value.scale()) > 1024)
				return null;
			return new Plan(source, member.member(), result.member(), key(value), RuntimeBinaryOperator.EQUAL, null,
					value);
		} catch (NumberFormatException invalid) {
			return null;
		}
	}

	private static Plan quantifiedPlan(RuntimeModel model, RuntimeDecision decision,
			RuntimeQuantifiedExpression quantified) {
		if (decision.decisionTable().isPresent() || quantified.bindings().size() != 1
				|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.BOOLEAN).contains(decision.type().kind())
				|| !(quantified.satisfies() instanceof RuntimeBinaryExpression comparison)
				|| !Set.of(RuntimeBinaryOperator.EQUAL, RuntimeBinaryOperator.NOT_EQUAL, RuntimeBinaryOperator.LESS,
						RuntimeBinaryOperator.LESS_EQUAL, RuntimeBinaryOperator.GREATER,
						RuntimeBinaryOperator.GREATER_EQUAL).contains(comparison.operator()))
			return null;
		var binding = quantified.bindings().getFirst();
		if (binding.localSlot() < 0 || binding.localSlot() >= 2048
				|| !(binding.source() instanceof RuntimeValueReference source)
				|| source.type().kind() != RuntimeTypeKind.LIST || source.type().elementType() == null
				|| source.type().elementType().kind() != RuntimeTypeKind.CONTEXT
				|| !SparkSqlFeelValueCodec.isNativeType(source.type()) || model.inputs().stream()
						.noneMatch(i -> i.valueSlot() == source.sourceSlot() && i.type().equals(source.type())))
			return null;
		RuntimeExpression left = comparison.left(), right = comparison.right();
		RuntimeBinaryOperator operator = comparison.operator();
		if (left instanceof RuntimeConstant) {
			left = comparison.right();
			right = comparison.left();
			operator = switch (operator) {
				case LESS -> RuntimeBinaryOperator.GREATER;
				case LESS_EQUAL -> RuntimeBinaryOperator.GREATER_EQUAL;
				case GREATER -> RuntimeBinaryOperator.LESS;
				case GREATER_EQUAL -> RuntimeBinaryOperator.LESS_EQUAL;
				default -> operator;
			};
		}
		if (!(left instanceof RuntimePathExpression member) || member.type().kind() != RuntimeTypeKind.NUMBER
				|| !(member.source() instanceof RuntimeLocalReference local) || local.lexicalDepth() != 0
				|| local.localSlot() != binding.localSlot()
				|| source.type().elementType().fieldLayout().stream()
						.noneMatch(f -> f.name().equals(member.member()) && f.type().kind() == RuntimeTypeKind.NUMBER)
				|| !(right instanceof RuntimeConstant constant) || constant.kind() != RuntimeConstantKind.NUMBER
				|| constant.value().length() > 256)
			return null;
		try {
			var literal = new BigDecimal(constant.value());
			if (literal.precision() > 128 || Math.abs((long) literal.scale()) > 1024)
				return null;
			return new Plan(source, member.member(), null, key(literal), operator, quantified.quantifier(), literal);
		} catch (NumberFormatException invalid) {
			return null;
		}
	}

	static String key(BigDecimal value) {
		if (value.signum() == 0)
			return "0";
		String coefficient = value.unscaledValue().toString();
		int end = coefficient.length();
		long scale = value.scale();
		while (coefficient.charAt(end - 1) == '0') {
			end--;
			scale--;
		}
		return coefficient.substring(0, end) + ":" + scale;
	}

	static String column(int id, Map<Integer, String> names) {
		String name = "__dmn_numeric_filter_" + id;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(name.toLowerCase(Locale.ROOT)))
			name += "_";
		return name;
	}

	static Object project(RuntimeModel model, int id, Map<Integer, ?> values) {
		var decision = model.decisions().stream().filter(d -> d.id() == id).findFirst().orElseThrow();
		var plan = Objects.requireNonNull(plan(model, decision));
		Object source = io.finmsg.dmn.runtime.DmnRuntime.coerce(values.get(plan.source().sourceSlot()),
				plan.source().type());
		if (source == null)
			return null;
		var rows = new ArrayList<Object>();
		for (Object item : (List<?>) source) {
			Map<?, ?> map = item instanceof Map<?, ?> m
					? m
					: item instanceof io.finmsg.dmn.runtime.RuntimeContextValue c ? c.namedFields() : Map.of();
			Object number = map.get(plan.number()), text = plan.text() == null ? null : map.get(plan.text());
			String key = null, digits = null;
			Integer sign = null;
			Long exponent = null;
			boolean invalid = false;
			if (number instanceof Number) {
				try {
					var decimal = number instanceof BigDecimal d ? d : new BigDecimal(number.toString());
					key = key(decimal);
					sign = decimal.signum();
					exponent = decimal.precision() - (long) decimal.scale();
					String coefficient = key.equals("0") ? "0" : key.substring(0, key.indexOf(':'));
					digits = coefficient.startsWith("-") ? coefficient.substring(1) : coefficient;
				} catch (NumberFormatException failure) {
					invalid = true;
				}
			}
			rows.add(RowFactory.create(key, text instanceof CharSequence ? text.toString() : null, invalid, sign,
					exponent, digits));
		}
		return rows;
	}

	static String sql(RuntimeModel model, RuntimeDecision decision, Map<Integer, String> names) {
		var plan = Objects.requireNonNull(plan(model, decision));
		if (plan.quantifier() != null)
			return quantifiedSql(plan, column(decision.id(), names));
		return "transform(filter(`" + column(decision.id(), names)
				+ "`, _dmn_numeric -> CASE WHEN _dmn_numeric.invalid THEN "
				+ "CAST(raise_error('Invalid FEEL number') AS BOOLEAN) ELSE _dmn_numeric.key = '" + plan.key()
				+ "' END), _dmn_numeric -> _dmn_numeric.text)";
	}
	private static String quantifiedSql(Plan plan, String column) {
		int sign = plan.literal().signum();
		long exponent = plan.literal().precision() - (long) plan.literal().scale();
		String coefficient = plan.key().equals("0") ? "0" : plan.key().substring(0, plan.key().indexOf(':'));
		String digits = coefficient.startsWith("-") ? coefficient.substring(1) : coefficient;
		String order = "(CASE WHEN _dmn_numeric.sign IS NULL THEN CAST(NULL AS INT)" + " WHEN _dmn_numeric.sign < "
				+ sign + " THEN -1 WHEN _dmn_numeric.sign > " + sign + " THEN 1"
				+ " WHEN _dmn_numeric.sign=0 THEN 0 WHEN _dmn_numeric.exponent < " + exponent + " THEN " + (-sign)
				+ " WHEN _dmn_numeric.exponent > " + exponent + " THEN " + sign + " WHEN _dmn_numeric.digits < '"
				+ digits + "' THEN " + (-sign) + " WHEN _dmn_numeric.digits > '" + digits + "' THEN " + sign
				+ " ELSE 0 END)";
		String op = switch (plan.operator()) {
			case EQUAL -> "=";
			case NOT_EQUAL -> "<>";
			case LESS -> "<";
			case LESS_EQUAL -> "<=";
			case GREATER -> ">";
			case GREATER_EQUAL -> ">=";
			default -> throw new IllegalArgumentException();
		};
		String predicate = plan.operator() == RuntimeBinaryOperator.NOT_EQUAL
				? "coalesce(" + order + " <> 0, TRUE)"
				: "coalesce(" + order + " " + op + " 0, FALSE)";
		boolean every = plan.quantifier() == RuntimeQuantifier.EVERY;
		return "CASE WHEN `" + column
				+ "` IS NULL THEN CAST(raise_error('Null FEEL quantified source') AS BOOLEAN) ELSE aggregate(`" + column
				+ "`, " + every + ", (_dmn_acc, _dmn_numeric) -> CASE WHEN _dmn_acc=" + !every + " THEN " + !every
				+ " WHEN _dmn_numeric.invalid THEN CAST(raise_error('Invalid FEEL number') AS BOOLEAN) ELSE "
				+ predicate + " END) END";
	}

}

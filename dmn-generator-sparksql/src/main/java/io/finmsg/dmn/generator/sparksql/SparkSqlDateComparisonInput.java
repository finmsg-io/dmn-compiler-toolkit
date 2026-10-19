package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.time.LocalDate;
import java.util.*;

/** Exact local-date comparisons with narrowly verified terminal branches. */
final class SparkSqlDateComparisonInput {
	private record Plan(int slot, RuntimeBinaryExpression comparison, LocalDate endpoint, boolean inputLeft,
			RuntimeConditionalExpression conditional) {
	}

	static Integer inputSlot(RuntimeModel model, RuntimeDecision decision) {
		Plan plan = plan(model, decision);
		return plan == null ? null : plan.slot();
	}

	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent())
			return null;
		RuntimeExpression expression = decision.expression().orElse(null);
		RuntimeConditionalExpression conditional = expression instanceof RuntimeConditionalExpression c ? c : null;
		RuntimeExpression condition = conditional == null ? expression : conditional.condition();
		if (!(condition instanceof RuntimeBinaryExpression comparison) || !Set.of(RuntimeBinaryOperator.EQUAL,
				RuntimeBinaryOperator.NOT_EQUAL, RuntimeBinaryOperator.LESS, RuntimeBinaryOperator.LESS_EQUAL,
				RuntimeBinaryOperator.GREATER, RuntimeBinaryOperator.GREATER_EQUAL).contains(comparison.operator()))
			return null;
		if (conditional == null) {
			if (!Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.BOOLEAN).contains(decision.type().kind()))
				return null;
		} else if (!Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING).contains(decision.type().kind())
				|| !string(conditional.thenExpression(), model) || !string(conditional.elseExpression(), model))
			return null;
		boolean inputLeft = comparison.left() instanceof RuntimeValueReference;
		RuntimeExpression input = inputLeft ? comparison.left() : comparison.right();
		if (!(input instanceof RuntimeValueReference reference) || reference.type().kind() != RuntimeTypeKind.DATE
				|| model.inputs().stream().noneMatch(
						i -> i.valueSlot() == reference.sourceSlot() && i.type().kind() == RuntimeTypeKind.DATE))
			return null;
		LocalDate endpoint = date(inputLeft ? comparison.right() : comparison.left());
		return endpoint == null ? null : new Plan(reference.sourceSlot(), comparison, endpoint, inputLeft, conditional);
	}

	private static LocalDate date(RuntimeExpression expression) {
		String text = null;
		if (expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.DATE)
			text = constant.value();
		List<RuntimeExpression> args = arguments(expression, Set.of("date"));
		if (args != null && args.size() == 1 && args.getFirst() instanceof RuntimeConstant constant
				&& constant.kind() == RuntimeConstantKind.STRING)
			text = constant.value();
		if (text == null || text.length() > 64)
			return null;
		try {
			return LocalDate.parse(text);
		} catch (java.time.DateTimeException ignored) {
			return null;
		}
	}

	private static boolean string(RuntimeExpression expression, RuntimeModel model) {
		if (expression instanceof RuntimeConstant constant)
			return constant.kind() == RuntimeConstantKind.NULL
					|| constant.kind() == RuntimeConstantKind.STRING && constant.value().length() <= 2048;
		if (expression instanceof RuntimeValueReference reference)
			return reference.type().kind() == RuntimeTypeKind.STRING && model.inputs().stream().anyMatch(
					i -> i.valueSlot() == reference.sourceSlot() && i.type().kind() == RuntimeTypeKind.STRING);
		List<RuntimeExpression> args = arguments(expression, Set.of("substring before", "substring after"));
		return args != null && args.size() == 2
				&& (args.getFirst() instanceof RuntimeConstant || args.getFirst() instanceof RuntimeValueReference)
				&& string(args.getFirst(), model) && args.get(1) instanceof RuntimeConstant delimiter
				&& delimiter.kind() == RuntimeConstantKind.STRING && delimiter.value().length() <= 2048;
	}

	private static List<RuntimeExpression> arguments(RuntimeExpression expression, Set<String> names) {
		if (expression instanceof RuntimeFunctionCall call && names.contains(call.function().toLowerCase(Locale.ROOT)))
			return call.arguments();
		if (expression instanceof RuntimeInvocationExpression call && call.function().isPresent()
				&& names.contains(call.function().get().toLowerCase(Locale.ROOT)) && call.namedArguments().isEmpty())
			return call.positionalArguments();
		return null;
	}

	static String sql(RuntimeModel model, RuntimeDecision decision, String column, Map<Integer, String> names) {
		Plan plan = Objects.requireNonNull(plan(model, decision));
		String source = "`" + column.replace("`", "``") + "`";
		String key = "(CAST(" + source + ".year AS BIGINT)*10000 + " + source + ".month*100 + " + source + ".day)";
		LocalDate endpoint = plan.endpoint();
		String fixed = Long
				.toString(endpoint.getYear() * 10000L + endpoint.getMonthValue() * 100L + endpoint.getDayOfMonth());
		String op = switch (plan.comparison().operator()) {
			case EQUAL -> "=";
			case NOT_EQUAL -> "<>";
			case LESS -> "<";
			case LESS_EQUAL -> "<=";
			case GREATER -> ">";
			case GREATER_EQUAL -> ">=";
			default -> throw new IllegalArgumentException();
		};
		String absent = plan.comparison().operator() == RuntimeBinaryOperator.EQUAL
				? "FALSE"
				: plan.comparison().operator() == RuntimeBinaryOperator.NOT_EQUAL ? "TRUE" : "NULL";
		String comparison = "CASE WHEN " + source + ".kind='DATE' THEN " + (plan.inputLeft() ? key : fixed) + " " + op
				+ " " + (plan.inputLeft() ? fixed : key) + " ELSE " + absent + " END";
		if (plan.conditional() == null)
			return comparison;
		java.util.function.IntFunction<String> resolver = slot -> "`" + names.get(slot).replace("`", "``") + "`";
		return "CASE WHEN (" + comparison + ") THEN "
				+ SparkSqlExpressionEmitter.emit(plan.conditional().thenExpression(), resolver) + " ELSE "
				+ SparkSqlExpressionEmitter.emit(plan.conditional().elseExpression(), resolver) + " END";
	}
}

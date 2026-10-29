package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Closed calendar calls evaluated with shared constructor and binding
 * semantics.
 */
final class SparkSqlCalendarLiteralProof {
	static RuntimeExpression result(RuntimeExpression expression) {
		String name = name(expression);
		if (name == null || !Set.of("day of year", "day of week", "month of year", "week of year").contains(name))
			return null;
		var arguments = arguments(expression);
		int[] budget = {64};
		if (arguments.size() > 4 || !arguments.stream().allMatch(arg -> literal(arg, 0, budget)))
			return null;
		try {
			var any = RuntimeType.scalar(RuntimeTypeKind.ANY);
			var decision = new RuntimeDecision(0, 0, any, List.of(), Optional.of(expression));
			Object value = new io.finmsg.dmn.runtime.DmnRuntime()
					.evaluate(new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0), 1), Map.of())
					.decisionValue(0);
			return SparkSqlExpressionEmitter.nativeContextLiteralValue(value);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static boolean literal(RuntimeExpression expression, int depth, int[] budget) {
		if (depth > 8 || --budget[0] < 0)
			return false;
		if (expression instanceof RuntimeConstant constant) {
			if (constant.value().length() > 256)
				return false;
			if (constant.kind() == RuntimeConstantKind.NUMBER) {
				try {
					var number = new BigDecimal(constant.value());
					return number.precision() <= 16 && Math.abs((long) number.scale()) <= 16;
				} catch (NumberFormatException ignored) {
					return false;
				}
			}
			return Set.of(RuntimeConstantKind.NULL, RuntimeConstantKind.STRING, RuntimeConstantKind.BOOLEAN,
					RuntimeConstantKind.DATE, RuntimeConstantKind.TIME, RuntimeConstantKind.DATE_TIME,
					RuntimeConstantKind.DURATION).contains(constant.kind());
		}
		String name = name(expression);
		if (name == null || !Set.of("date", "date and time", "time", "duration").contains(name))
			return false;
		var arguments = arguments(expression);
		return arguments.size() <= 4 && arguments.stream().allMatch(arg -> literal(arg, depth + 1, budget));
	}

	private static String name(RuntimeExpression expression) {
		return expression instanceof RuntimeFunctionCall call
				? call.function().toLowerCase(Locale.ROOT)
				: expression instanceof RuntimeInvocationExpression call && call.function().isPresent()
						? call.function().get().toLowerCase(Locale.ROOT)
						: null;
	}

	private static List<RuntimeExpression> arguments(RuntimeExpression expression) {
		if (expression instanceof RuntimeFunctionCall call)
			return call.arguments();
		var call = (RuntimeInvocationExpression) expression;
		var result = new ArrayList<>(call.positionalArguments());
		call.namedArguments().forEach(arg -> result.add(arg.expression()));
		return result;
	}
}

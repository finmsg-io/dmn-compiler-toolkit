package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Bounded scalar results of pure literal list builtins, using shared FEEL
 * binding and validation.
 */
final class SparkSqlLiteralBuiltinProof {
	private SparkSqlLiteralBuiltinProof() {
	}

	static RuntimeConstant result(RuntimeExpression expression) {
		String name;
		List<RuntimeExpression> arguments = new ArrayList<>();
		if (expression instanceof RuntimeFunctionCall call) {
			name = call.function();
			arguments.addAll(call.arguments());
		} else if (expression instanceof RuntimeInvocationExpression invocation && invocation.function().isPresent()) {
			name = invocation.function().get();
			arguments.addAll(invocation.positionalArguments());
			invocation.namedArguments().forEach(argument -> arguments.add(argument.expression()));
		} else {
			return null;
		}
		if (!Set.of("all", "any", "string join", "product").contains(name.toLowerCase(Locale.ROOT)))
			return null;
		int[] budget = {512};
		if (!arguments.stream().allMatch(argument -> literal(argument, 0, budget)))
			return null;
		var any = RuntimeType.scalar(RuntimeTypeKind.ANY);
		var decision = new RuntimeDecision(0, 0, any, List.of(), Optional.of(expression));
		var model = new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0), 1);
		Object value = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(0);
		RuntimeConstant result;
		if (value == null)
			result = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		else if (value instanceof Boolean bool)
			result = new RuntimeConstant(RuntimeConstantKind.BOOLEAN, bool.toString(),
					RuntimeType.scalar(RuntimeTypeKind.BOOLEAN));
		else if (value instanceof String text && text.length() <= 32768)
			result = new RuntimeConstant(RuntimeConstantKind.STRING, text, RuntimeType.scalar(RuntimeTypeKind.STRING));
		else if (value instanceof BigDecimal number && exactDecimal(number))
			result = new RuntimeConstant(RuntimeConstantKind.NUMBER, number.toPlainString(),
					RuntimeType.scalar(RuntimeTypeKind.NUMBER));
		else
			return null;
		return result.kind() == RuntimeConstantKind.NULL || expression.type().kind() == RuntimeTypeKind.ANY
				|| expression.type().kind() == result.type().kind() ? result : null;
	}

	private static boolean literal(RuntimeExpression expression, int depth, int[] budget) {
		if (depth > 16 || --budget[0] < 0)
			return false;
		if (expression instanceof RuntimeListExpression list)
			return list.elements().stream().allMatch(element -> literal(element, depth + 1, budget));
		if (!(expression instanceof RuntimeConstant constant) || constant.value().length() > 2048)
			return false;
		return switch (constant.kind()) {
			case NULL, STRING, BOOLEAN -> true;
			case NUMBER -> {
				try {
					var number = new BigDecimal(constant.value());
					yield constant.value().length() <= 64 && number.precision() <= 38
							&& Math.abs((long) number.scale()) <= 38;
				} catch (NumberFormatException ignored) {
					yield false;
				}
			}
			default -> false;
		};
	}

	private static boolean exactDecimal(BigDecimal number) {
		return Math.max(0L, number.precision() - (long) number.scale()) + Math.max(0L, number.scale()) <= 38;
	}
}

package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Bounded results of pure literal list builtins, using shared FEEL binding and
 * validation.
 */
final class SparkSqlLiteralBuiltinProof {
	private SparkSqlLiteralBuiltinProof() {
	}

	static RuntimeExpression result(RuntimeExpression expression) {
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
		if (!Set.of("all", "any", "string join", "product", "median", "mode", "stddev")
				.contains(name.toLowerCase(Locale.ROOT)))
			return null;
		int[] budget = {512};
		if (!arguments.stream().allMatch(argument -> literal(argument, 0, budget)))
			return null;
		var any = RuntimeType.scalar(RuntimeTypeKind.ANY);
		var decision = new RuntimeDecision(0, 0, any, List.of(), Optional.of(expression));
		var model = new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0), 1);
		Object value = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(0);
		RuntimeExpression result;
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
		else if (value instanceof List<?> list && name.equalsIgnoreCase("mode")
				&& list.stream().allMatch(item -> item instanceof BigDecimal number && exactDecimal(number))
				&& exactArrayDecimals(list)) {
			RuntimeType number = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
			List<RuntimeExpression> elements = list.stream()
					.map(item -> (RuntimeExpression) new RuntimeConstant(RuntimeConstantKind.NUMBER,
							((BigDecimal) item).toPlainString(), number))
					.toList();
			result = new RuntimeListExpression(elements, RuntimeType.element(RuntimeTypeKind.LIST, number));
		} else
			return null;
		return result.type().kind() == RuntimeTypeKind.NULL || expression.type().kind() == RuntimeTypeKind.ANY
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

	static boolean exactNumericLiteralArray(RuntimeListExpression list) {
		var values = new ArrayList<BigDecimal>();
		for (RuntimeExpression element : list.elements()) {
			if (!(element instanceof RuntimeConstant constant)
					|| !Set.of(RuntimeConstantKind.NUMBER, RuntimeConstantKind.NULL).contains(constant.kind()))
				return true;
			if (constant.kind() == RuntimeConstantKind.NUMBER) {
				try {
					if (constant.value().length() > 2048)
						return false;
					values.add(new BigDecimal(constant.value()));
				} catch (NumberFormatException ignored) {
					return false;
				}
			}
		}
		return exactArrayDecimals(values);
	}

	static boolean exactArrayDecimals(List<?> values) {
		var present = values.stream().filter(Objects::nonNull).toList();
		if (present.isEmpty())
			return true;
		if (present.stream().allMatch(value -> value instanceof BigDecimal)) {
			long integral = 0, fractional = 0;
			for (Object value : present) {
				var number = (BigDecimal) value;
				integral = Math.max(integral, Math.max(0L, number.precision() - (long) number.scale()));
				fractional = Math.max(fractional, Math.max(0L, number.scale()));
			}
			return integral + fractional <= 38;
		}
		if (present.stream().allMatch(value -> value instanceof List<?>))
			return exactArrayDecimals(present.stream().flatMap(value -> ((List<?>) value).stream()).toList());
		if (present.stream().allMatch(value -> value instanceof Map<?, ?>)) {
			for (Object key : ((Map<?, ?>) present.getFirst()).keySet()) {
				if (!exactArrayDecimals(present.stream().map(value -> ((Map<?, ?>) value).get(key)).toList()))
					return false;
			}
		}
		return true;
	}

	private static boolean exactDecimal(BigDecimal number) {
		return Math.max(0L, number.precision() - (long) number.scale()) + Math.max(0L, number.scale()) <= 38;
	}
}

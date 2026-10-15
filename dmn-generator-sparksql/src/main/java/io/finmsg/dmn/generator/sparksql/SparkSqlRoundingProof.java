package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Exact proofs for rounding that is either invalid or leaves a literal
 * unchanged.
 */
final class SparkSqlRoundingProof {
	private static final Set<String> FUNCTIONS = Set.of("floor", "ceiling", "decimal", "round up", "round down",
			"round half up", "round half down", "round half even");
	private static final class Unsupported extends RuntimeException {
	}

	static String sql(RuntimeExpression expression) {
		String name;
		List<RuntimeExpression> arguments;
		if (expression instanceof RuntimeFunctionCall call) {
			name = call.function().toLowerCase(Locale.ROOT);
			arguments = call.arguments();
		} else if (expression instanceof RuntimeInvocationExpression invocation && invocation.function().isPresent()) {
			name = invocation.function().get().toLowerCase(Locale.ROOT);
			if (!FUNCTIONS.contains(name))
				return null;
			arguments = invocation.namedArguments().isEmpty()
					? invocation.positionalArguments()
					: SparkSqlExpressionEmitter.reorderNamedArguments(name, invocation.namedArguments());
		} else
			return null;
		if (!FUNCTIONS.contains(name) || arguments.size() != 2)
			return null;
		try {
			BigDecimal number = literal(arguments.getFirst(), 0);
			int scale = integer(arguments.get(1), 0, new int[]{64});
			if (scale < -6111 || scale > 6176)
				return "NULL";
			if (scale < number.scale())
				return null;
			int sqlScale = Math.max(number.scale(), 0);
			int precision = Math.max(1, Math.max(0, number.precision() - number.scale()) + sqlScale);
			if (precision > 38 || sqlScale > 38)
				return null;
			return "CAST('" + number.toPlainString() + "' AS DECIMAL(" + precision + "," + sqlScale + "))";
		} catch (Unsupported | ArithmeticException | NumberFormatException ignored) {
			return null;
		}
	}

	private static BigDecimal literal(RuntimeExpression expression, int depth) {
		if (depth > 8)
			throw new Unsupported();
		if (expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.NUMBER
				&& constant.value() != null && constant.value().length() <= 64) {
			BigDecimal value = new BigDecimal(constant.value());
			if (value.precision() > 38 || Math.abs((long) value.scale()) > 38)
				throw new Unsupported();
			return value;
		}
		if (expression instanceof RuntimeUnaryExpression unary && unary.operator() != RuntimeUnaryOperator.NOT) {
			BigDecimal operand = literal(unary.operand(), depth + 1);
			return unary.operator() == RuntimeUnaryOperator.NEGATE ? operand.negate() : operand;
		}
		throw new Unsupported();
	}

	static boolean requiresFallback(RuntimeExpression expression) {
		RuntimeFunctionCall call;
		if (expression instanceof RuntimeFunctionCall function)
			call = function;
		else if (expression instanceof RuntimeInvocationExpression invocation && invocation.function().isPresent()) {
			String name = invocation.function().get().toLowerCase(Locale.ROOT);
			if (!FUNCTIONS.contains(name))
				return false;
			call = new RuntimeFunctionCall(name,
					invocation.namedArguments().isEmpty()
							? invocation.positionalArguments()
							: SparkSqlExpressionEmitter.reorderNamedArguments(name, invocation.namedArguments()),
					invocation.type());
		} else
			return false;
		if (!FUNCTIONS.contains(call.function().toLowerCase(Locale.ROOT)) || call.arguments().size() != 2
				|| sql(expression) != null)
			return false;
		try {
			return Math.abs((long) integer(call.arguments().get(1), 0, new int[]{64})) > 308;
		} catch (Unsupported | ArithmeticException | NumberFormatException ignored) {
			return staticNumericScale(call.arguments().get(1), 0);
		}
	}

	private static boolean staticNumericScale(RuntimeExpression expression, int depth) {
		if (depth > 8)
			return false;
		if (expression instanceof RuntimeConstant constant) {
			if (constant.kind() != RuntimeConstantKind.NUMBER || constant.value() == null
					|| constant.value().length() > 64)
				return false;
			try {
				return new BigDecimal(constant.value()).stripTrailingZeros().scale() <= 0;
			} catch (NumberFormatException ignored) {
				return false;
			}
		}
		if (expression instanceof RuntimeUnaryExpression unary && unary.operator() != RuntimeUnaryOperator.NOT)
			return staticNumericScale(unary.operand(), depth + 1);
		return expression instanceof RuntimeBinaryExpression binary
				&& Set.of(RuntimeBinaryOperator.ADD, RuntimeBinaryOperator.SUBTRACT, RuntimeBinaryOperator.MULTIPLY)
						.contains(binary.operator())
				&& staticNumericScale(binary.left(), depth + 1) && staticNumericScale(binary.right(), depth + 1);
	}

	private static int integer(RuntimeExpression expression, int depth, int[] budget) {
		if (depth > 8 || --budget[0] < 0)
			throw new Unsupported();
		if (expression instanceof RuntimeBinaryExpression binary) {
			long left = integer(binary.left(), depth + 1, budget);
			long right = integer(binary.right(), depth + 1, budget);
			return Math.toIntExact(switch (binary.operator()) {
				case ADD -> left + right;
				case SUBTRACT -> left - right;
				case MULTIPLY -> left * right;
				default -> throw new Unsupported();
			});
		}
		return literal(expression, depth).intValueExact();
	}
}

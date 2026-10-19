package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Bounded closed collection operations, evaluated once using shared FEEL rules.
 */
final class SparkSqlClosedCollectionProof {
	static RuntimeExpression result(RuntimeExpression expression) {
		if (!proven(expression))
			return null;
		try {
			var any = RuntimeType.scalar(RuntimeTypeKind.ANY);
			var decision = new RuntimeDecision(0, 0, any, List.of(), Optional.of(expression), Optional.empty(), 2048);
			Object value = new io.finmsg.dmn.runtime.DmnRuntime()
					.evaluate(new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0), 1), Map.of())
					.decisionValue(0);
			return SparkSqlExpressionEmitter.nativeContextLiteralValue(value);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	static boolean requiresFallback(RuntimeExpression expression) {
		return proven(expression) && result(expression) == null
				&& !(expression instanceof RuntimeFilterExpression filter
						&& SparkSqlCapabilityAnalyzer.nativeFilter(filter));
	}

	private static boolean proven(RuntimeExpression expression) {
		int[] budget = {512};
		boolean proven;
		if (expression instanceof RuntimePathExpression path)
			proven = text(path.member(), 256) && literal(path.source(), 0, budget);
		else if (expression instanceof RuntimeFilterExpression filter)
			proven = filter.localSlot() >= 0 && filter.localSlot() < 2048 && literal(filter.source(), 0, budget)
					&& predicate(filter.filter(), filter.localSlot(), 0, budget);
		else if (expression instanceof RuntimeBinaryExpression binary)
			proven = Set.of(RuntimeBinaryOperator.EQUAL, RuntimeBinaryOperator.NOT_EQUAL).contains(binary.operator())
					&& literal(binary.left(), 0, budget) && literal(binary.right(), 0, budget);
		else if (expression instanceof RuntimeInExpression in)
			proven = literal(in.value(), 0, budget) && in.tests().tests().size() <= 64
					&& in.tests().tests().stream().allMatch(test -> test instanceof RuntimeExpressionUnaryTest endpoint
							&& literal(endpoint.expression(), 0, budget));
		else
			return false;
		return proven;
	}

	private static boolean text(String value, int limit) {
		return value.length() <= limit && value.codePoints().noneMatch(c -> c >= 0xD800 && c <= 0xDFFF);
	}

	private static boolean literal(RuntimeExpression expression, int depth, int[] budget) {
		if (depth > 16 || --budget[0] < 0)
			return false;
		if (expression instanceof RuntimeConstant constant) {
			if (!text(constant.value(), 2048))
				return false;
			if (constant.kind() == RuntimeConstantKind.NUMBER) {
				try {
					var value = new BigDecimal(constant.value());
					return value.precision() <= 38 && Math.abs((long) value.scale()) <= 38;
				} catch (NumberFormatException ignored) {
					return false;
				}
			}
			return Set.of(RuntimeConstantKind.NULL, RuntimeConstantKind.BOOLEAN, RuntimeConstantKind.STRING)
					.contains(constant.kind());
		}
		if (expression instanceof RuntimePathExpression path)
			return text(path.member(), 256) && literal(path.source(), depth + 1, budget);
		if (expression instanceof RuntimeListExpression list)
			return list.elements().size() <= 64
					&& list.elements().stream().allMatch(item -> literal(item, depth + 1, budget));
		if (expression instanceof RuntimeContextExpression context)
			return context.entries().size() <= 64 && context.entries().stream()
					.allMatch(entry -> !entry.name().isEmpty() && !entry.name().equals("__result__")
							&& text(entry.name(), 256) && entry.localSlot() < 2048 && entry.localSlot() >= -1
							&& literal(entry.expression(), depth + 1, budget));
		return false;
	}

	private static boolean predicate(RuntimeExpression expression, int slot, int depth, int[] budget) {
		if (depth > 16 || --budget[0] < 0)
			return false;
		if (expression instanceof RuntimeLocalReference reference)
			return reference.localSlot() == slot && reference.lexicalDepth() == 0;
		if (expression instanceof RuntimePathExpression path)
			return text(path.member(), 256) && predicate(path.source(), slot, depth + 1, budget);
		if (expression instanceof RuntimeBinaryExpression binary)
			return Set
					.of(RuntimeBinaryOperator.EQUAL, RuntimeBinaryOperator.NOT_EQUAL, RuntimeBinaryOperator.LESS,
							RuntimeBinaryOperator.LESS_EQUAL, RuntimeBinaryOperator.GREATER,
							RuntimeBinaryOperator.GREATER_EQUAL, RuntimeBinaryOperator.AND, RuntimeBinaryOperator.OR)
					.contains(binary.operator()) && predicate(binary.left(), slot, depth + 1, budget)
					&& predicate(binary.right(), slot, depth + 1, budget);
		if (expression instanceof RuntimeUnaryExpression unary)
			return unary.operator() == RuntimeUnaryOperator.NOT
					? predicate(unary.operand(), slot, depth + 1, budget)
					: Set.of(RuntimeUnaryOperator.NEGATE, RuntimeUnaryOperator.POSITIVE).contains(unary.operator())
							&& unary.operand() instanceof RuntimeConstant
							&& literal(unary.operand(), depth + 1, budget);
		return literal(expression, depth + 1, budget);
	}
}

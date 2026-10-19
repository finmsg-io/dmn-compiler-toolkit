package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.*;

/** Bounded closed numeric recurrences evaluated with shared FEEL semantics. */
final class SparkSqlPartialLoopProof {

	static RuntimeExpression result(RuntimeForExpression loop) {
		if (loop.partialSlot() < 0 || loop.partialSlot() >= 2048 || loop.iterations().size() != 1)
			return null;
		RuntimeIteration iteration = loop.iterations().getFirst();
		if (iteration.localSlot() >= 2048 || iteration.localSlot() == loop.partialSlot())
			return null;
		try {
			if (iteration.end().isPresent()) {
				int first = integer(iteration.source()), last = integer(iteration.end().get());
				if (Math.abs((long) last - first) >= 64 || Math.abs((long) first) > 1000000
						|| Math.abs((long) last) > 1000000)
					return null;
			} else if (!(iteration.source() instanceof RuntimeListExpression list) || list.elements().size() > 64
					|| !list.elements().stream().allMatch(SparkSqlPartialLoopProof::number))
				return null;
			if (!body(loop.result(), iteration.localSlot(), loop.partialSlot(), 0, new int[]{128, 1}))
				return null;
			var any = RuntimeType.scalar(RuntimeTypeKind.ANY);
			var decision = new RuntimeDecision(0, 0, any, List.of(), Optional.of(loop), Optional.empty(), 2048);
			Object value = new io.finmsg.dmn.runtime.DmnRuntime()
					.evaluate(new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0), 1), Map.of())
					.decisionValue(0);
			return SparkSqlExpressionEmitter.nativeContextLiteralValue(value);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static boolean number(RuntimeExpression expression) {
		if (!(expression instanceof RuntimeConstant constant) || constant.kind() != RuntimeConstantKind.NUMBER
				|| constant.value().length() > 64)
			return false;
		try {
			BigDecimal value = new BigDecimal(constant.value());
			return value.precision() <= 16 && Math.abs((long) value.scale()) <= 16;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private static int integer(RuntimeExpression expression) {
		if (expression instanceof RuntimeUnaryExpression unary && unary.operator() == RuntimeUnaryOperator.NEGATE)
			return Math.negateExact(integer(unary.operand()));
		if (!number(expression))
			throw new IllegalArgumentException();
		return new BigDecimal(((RuntimeConstant) expression).value()).intValueExact();
	}

	private static boolean body(RuntimeExpression expression, int item, int partial, int depth, int[] budget) {
		if (depth > 12 || --budget[0] < 0)
			return false;
		if (number(expression))
			return true;
		if (expression instanceof RuntimeConstant constant)
			return constant.kind() == RuntimeConstantKind.BOOLEAN || constant.kind() == RuntimeConstantKind.NULL;
		if (expression instanceof RuntimeLocalReference reference)
			return reference.lexicalDepth() == 0 && reference.localSlot() == item;
		if (expression instanceof RuntimeFilterExpression filter)
			return filter.localSlot() >= 0 && filter.localSlot() < 2048
					&& filter.source() instanceof RuntimeLocalReference reference && reference.lexicalDepth() == 0
					&& reference.localSlot() == partial && --budget[1] >= 0 && integer(filter.filter()) != 0;
		if (expression instanceof RuntimeUnaryExpression unary)
			return Set.of(RuntimeUnaryOperator.NEGATE, RuntimeUnaryOperator.POSITIVE).contains(unary.operator())
					&& body(unary.operand(), item, partial, depth + 1, budget);
		if (expression instanceof RuntimeBinaryExpression binary)
			return Set
					.of(RuntimeBinaryOperator.ADD, RuntimeBinaryOperator.SUBTRACT, RuntimeBinaryOperator.MULTIPLY,
							RuntimeBinaryOperator.DIVIDE, RuntimeBinaryOperator.EQUAL, RuntimeBinaryOperator.NOT_EQUAL,
							RuntimeBinaryOperator.LESS, RuntimeBinaryOperator.LESS_EQUAL, RuntimeBinaryOperator.GREATER,
							RuntimeBinaryOperator.GREATER_EQUAL)
					.contains(binary.operator()) && body(binary.left(), item, partial, depth + 1, budget)
					&& body(binary.right(), item, partial, depth + 1, budget);
		if (expression instanceof RuntimeConditionalExpression conditional)
			return body(conditional.condition(), item, partial, depth + 1, budget)
					&& body(conditional.thenExpression(), item, partial, depth + 1, budget)
					&& body(conditional.elseExpression(), item, partial, depth + 1, budget);
		return false;
	}
}

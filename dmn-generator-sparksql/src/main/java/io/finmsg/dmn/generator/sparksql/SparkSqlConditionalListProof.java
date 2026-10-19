package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Literal list leaves with dynamic boolean selection and empty alternatives.
 */
final class SparkSqlConditionalListProof {
	static RuntimeType type(RuntimeExpression expression) {
		var numbers = new ArrayList<BigDecimal>();
		RuntimeType result = shape(expression, 0, new int[]{512}, numbers);
		return result != null && result.kind() == RuntimeTypeKind.LIST
				&& SparkSqlLiteralBuiltinProof.exactArrayDecimals(numbers) ? result : null;
	}

	private static RuntimeType shape(RuntimeExpression expression, int depth, int[] budget, List<BigDecimal> numbers) {
		if (depth > 16 || --budget[0] < 0)
			return null;
		if (expression instanceof RuntimeConstant constant) {
			if (constant.value().length() > 2048)
				return null;
			return switch (constant.kind()) {
				case NULL -> RuntimeType.scalar(RuntimeTypeKind.NULL);
				case BOOLEAN -> RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
				case STRING -> RuntimeType.scalar(RuntimeTypeKind.STRING);
				case NUMBER -> {
					try {
						var value = new BigDecimal(constant.value());
						if (value.precision() > 38 || Math.abs((long) value.scale()) > 38)
							yield null;
						numbers.add(value);
						yield RuntimeType.scalar(RuntimeTypeKind.NUMBER);
					} catch (NumberFormatException ignored) {
						yield null;
					}
				}
				default -> null;
			};
		}
		if (expression instanceof RuntimeListExpression list) {
			RuntimeType element = RuntimeType.scalar(RuntimeTypeKind.NULL);
			for (RuntimeExpression item : list.elements()) {
				element = merge(element, shape(item, depth + 1, budget, numbers));
				if (element == null)
					return null;
			}
			return RuntimeType.element(RuntimeTypeKind.LIST, element);
		}
		if (expression instanceof RuntimeConditionalExpression conditional
				&& conditional.condition().type().kind() == RuntimeTypeKind.BOOLEAN) {
			RuntimeType yes = shape(conditional.thenExpression(), depth + 1, budget, numbers);
			RuntimeType no = shape(conditional.elseExpression(), depth + 1, budget, numbers);
			return yes != null && no != null && yes.kind() == RuntimeTypeKind.LIST && no.kind() == RuntimeTypeKind.LIST
					? merge(yes, no)
					: null;
		}
		List<RuntimeExpression> args = expression instanceof RuntimeFunctionCall call
				&& call.function().equalsIgnoreCase("flatten")
						? call.arguments()
						: expression instanceof RuntimeInvocationExpression call && call.function().isPresent()
								&& call.function().get().equalsIgnoreCase("flatten") && call.namedArguments().isEmpty()
										? call.positionalArguments()
										: null;
		if (args == null || args.size() != 1)
			return null;
		RuntimeType source = shape(args.getFirst(), depth + 1, budget, numbers);
		return source != null && source.kind() == RuntimeTypeKind.LIST
				&& source.elementType().kind() == RuntimeTypeKind.LIST
				&& source.elementType().elementType().kind() != RuntimeTypeKind.LIST ? source.elementType() : null;
	}

	private static RuntimeType merge(RuntimeType left, RuntimeType right) {
		if (left == null || right == null)
			return null;
		if (left.kind() == RuntimeTypeKind.NULL)
			return right;
		if (right.kind() == RuntimeTypeKind.NULL || left.equals(right))
			return left;
		if (left.kind() == RuntimeTypeKind.LIST && right.kind() == RuntimeTypeKind.LIST) {
			RuntimeType element = merge(left.elementType(), right.elementType());
			return element == null ? null : RuntimeType.element(RuntimeTypeKind.LIST, element);
		}
		return null;
	}
}

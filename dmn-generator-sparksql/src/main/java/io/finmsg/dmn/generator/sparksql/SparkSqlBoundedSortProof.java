package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Finite specialization of literal string sorts with equality-only comparators.
 */
final class SparkSqlBoundedSortProof {
	static RuntimeExpression result(RuntimeModel model, RuntimeExpression source, RuntimeFunctionDefinition function) {
		if (!(source instanceof RuntimeListExpression list) || list.elements().size() > 16 || function.external()
				|| function.parameters().size() != 2 || function.localSlotCount() > 2048
				|| function.parameters().stream().anyMatch(p -> p.type().kind() != RuntimeTypeKind.STRING)
				|| function.parameters().stream().map(RuntimeFunctionParameter::localSlot).distinct().count() != 2
				|| !(function.body().orElse(null) instanceof RuntimeConditionalExpression conditional)
				|| !bool(conditional.thenExpression()) || !bool(conditional.elseExpression())
				|| !(conditional.condition() instanceof RuntimeBinaryExpression comparison)
				|| comparison.operator() != RuntimeBinaryOperator.EQUAL)
			return null;
		var texts = new LinkedHashSet<String>();
		for (var item : list.elements()) {
			if (!(item instanceof RuntimeConstant constant) || constant.kind() != RuntimeConstantKind.STRING
					|| !text(constant.value()))
				return null;
			texts.add(constant.value());
		}
		RuntimeExpression local = comparison.left(), target = comparison.right();
		if (!(local instanceof RuntimeLocalReference)) {
			local = comparison.right();
			target = comparison.left();
		}
		if (!(local instanceof RuntimeLocalReference reference) || reference.lexicalDepth() != 0
				|| reference.type().kind() != RuntimeTypeKind.STRING
				|| function.parameters().stream().noneMatch(p -> p.localSlot() == reference.localSlot()))
			return null;
		RuntimeValueReference input = target instanceof RuntimeValueReference r ? r : null;
		if (input != null) {
			if (input.type().kind() != RuntimeTypeKind.STRING || model.inputs().stream()
					.noneMatch(i -> i.valueSlot() == input.sourceSlot() && i.type().equals(input.type())))
				return null;
		} else if (!(target instanceof RuntimeConstant constant) || constant.kind() != RuntimeConstantKind.STRING
				|| !text(constant.value()))
			return null;
		RuntimeType returnType = function.type().kind() == RuntimeTypeKind.FUNCTION
				? function.type().returnType()
				: function.type();
		if (returnType == null || !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.BOOLEAN).contains(returnType.kind()))
			return null;
		try {
			var result = evaluate(source, function, input, null);
			if (result == null)
				return null;
			if (input == null)
				return result;
			var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
			for (String text : texts) {
				var branch = evaluate(source, function, input, text);
				if (branch == null)
					return null;
				result = new RuntimeConditionalExpression(
						new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, input,
								new RuntimeConstant(RuntimeConstantKind.STRING, text, input.type()), bool),
						branch, result, result.type());
			}
			return result;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static boolean text(String value) {
		return value.length() <= 256 && value.codePoints().noneMatch(c -> c >= 0xD800 && c <= 0xDFFF);
	}

	private static boolean bool(RuntimeExpression expression) {
		return expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.BOOLEAN
				&& Set.of("true", "false").contains(constant.value());
	}

	private static RuntimeExpression evaluate(RuntimeExpression source, RuntimeFunctionDefinition function,
			RuntimeValueReference input, String value) {
		var any = RuntimeType.scalar(RuntimeTypeKind.ANY);
		if (input != null) {
			var conditional = (RuntimeConditionalExpression) function.body().orElseThrow();
			var comparison = (RuntimeBinaryExpression) conditional.condition();
			var normalized = new RuntimeValueReference(0, input.type());
			var condition = new RuntimeBinaryExpression(comparison.operator(),
					comparison.left() instanceof RuntimeValueReference ? normalized : comparison.left(),
					comparison.right() instanceof RuntimeValueReference ? normalized : comparison.right(),
					comparison.type());
			function = new RuntimeFunctionDefinition(function.parameters(),
					Optional.of(new RuntimeConditionalExpression(condition, conditional.thenExpression(),
							conditional.elseExpression(), conditional.type())),
					false, function.localSlotCount(), function.type());
		}
		var call = new RuntimeFunctionCall("sort", List.of(source, function), any);
		int resultSlot = input == null ? 0 : 1;
		var inputs = input == null ? List.<RuntimeInput>of() : List.of(new RuntimeInput(0, 0, input.type()));
		var decision = new RuntimeDecision(1, resultSlot, any, input == null ? List.of() : List.of(0),
				Optional.of(call));
		Map<Integer, Object> values = new HashMap<>();
		if (input != null)
			values.put(0, value);
		Object result = new io.finmsg.dmn.runtime.DmnRuntime()
				.evaluate(new RuntimeModel(inputs, List.of(decision), List.of(), List.of(1), resultSlot + 1), values)
				.decisionValue(1);
		return SparkSqlExpressionEmitter.nativeContextLiteralValue(result);
	}
}

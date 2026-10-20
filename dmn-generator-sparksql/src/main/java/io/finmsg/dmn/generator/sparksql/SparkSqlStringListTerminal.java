package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/** Terminal typed string selections with shared singleton-result coercion. */
final class SparkSqlStringListTerminal {
	private record Plan(RuntimeExpression selection, RuntimeConstant match, String casing) {
	}

	static boolean supports(RuntimeModel model, RuntimeDecision decision) {
		return plan(model, decision) != null;
	}

	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent() || decision.type() == null)
			return null;
		var type = decision.type();
		if (!Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING).contains(type.kind())
				&& !(type.kind() == RuntimeTypeKind.LIST && type.elementType() != null
						&& Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING).contains(type.elementType().kind())))
			return null;
		RuntimeExpression expression = decision.expression().orElse(null);
		String casing = name(expression);
		if (Set.of("upper case", "lower case").contains(casing == null ? "" : casing)) {
			var args = args(expression);
			if (args == null || args.size() != 1 || type.kind() == RuntimeTypeKind.LIST)
				return null;
			expression = args.getFirst();
		} else
			casing = null;
		if (expression instanceof RuntimeFilterExpression filter && filter.localSlot() >= 0 && filter.localSlot() < 2048
				&& input(model, filter.source()) && filter.filter() instanceof RuntimeBinaryExpression equal
				&& equal.operator() == RuntimeBinaryOperator.EQUAL) {
			RuntimeExpression left = equal.left(), right = equal.right();
			if (left instanceof RuntimeConstant) {
				left = equal.right();
				right = equal.left();
			}
			if (left instanceof RuntimeLocalReference local && local.lexicalDepth() == 0
					&& local.localSlot() == filter.localSlot() && right instanceof RuntimeConstant text
					&& text.kind() == RuntimeConstantKind.STRING && text.value().length() <= 2048
					&& text.value().codePoints().noneMatch(c -> c >= 0xD800 && c <= 0xDFFF))
				return new Plan(expression, text, casing);
		}
		var args = args(expression);
		if (casing == null && "sublist".equals(name(expression)) && args != null && !args.isEmpty()
				&& input(model, args.getFirst()) && SparkSqlListBuiltinTypes.result("sublist", args) != null)
			return new Plan(expression, null, null);
		return null;
	}

	private static boolean input(RuntimeModel model, RuntimeExpression expression) {
		return expression instanceof RuntimeValueReference ref && ref.type().kind() == RuntimeTypeKind.LIST
				&& ref.type().elementType() != null && ref.type().elementType().kind() == RuntimeTypeKind.STRING
				&& model.inputs().stream()
						.anyMatch(i -> i.valueSlot() == ref.sourceSlot() && i.type().equals(ref.type()));
	}

	private static String name(RuntimeExpression expression) {
		return expression instanceof RuntimeFunctionCall call
				? call.function().toLowerCase(Locale.ROOT)
				: expression instanceof RuntimeInvocationExpression call && call.function().isPresent()
						? call.function().get().toLowerCase(Locale.ROOT)
						: null;
	}

	private static List<RuntimeExpression> args(RuntimeExpression expression) {
		return expression instanceof RuntimeFunctionCall call
				? call.arguments()
				: expression instanceof RuntimeInvocationExpression call && call.function().isPresent()
						&& call.namedArguments().isEmpty() ? call.positionalArguments() : null;
	}

	static String sql(RuntimeModel model, RuntimeDecision decision, Map<Integer, String> names) {
		Plan plan = plan(model, decision);
		if (plan == null)
			return null;
		java.util.function.IntFunction<String> resolver = slot -> "`" + names.get(slot).replace("`", "``") + "`";
		String selection;
		if (plan.selection() instanceof RuntimeFilterExpression filter)
			selection = "filter(" + resolver.apply(((RuntimeValueReference) filter.source()).sourceSlot())
					+ ", _dmn_match -> _dmn_match = " + SparkSqlStringInput.sql(plan.match(), names) + ")";
		else {
			var strings = RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType.scalar(RuntimeTypeKind.STRING));
			selection = SparkSqlExpressionEmitter.emitWithBkms(
					new RuntimeFunctionCall("sublist", args(plan.selection()), strings), Map.of(), resolver);
		}
		if (decision.type().kind() != RuntimeTypeKind.STRING && plan.casing() == null)
			return selection;
		String result = "CASE WHEN size(_dmn_selected) = 1 THEN try_element_at(_dmn_selected, 1) ELSE CAST(NULL AS STRING) END";
		if (plan.casing() != null) {
			String value = io.finmsg.dmn.runtime.DmnRuntime.formatFeelString(plan.match().value());
			value = plan.casing().equals("upper case")
					? value.toUpperCase(Locale.ROOT)
					: value.toLowerCase(Locale.ROOT);
			var constant = new RuntimeConstant(RuntimeConstantKind.STRING, value,
					RuntimeType.scalar(RuntimeTypeKind.STRING));
			result = "CASE WHEN _dmn_selected IS NULL THEN CAST(NULL AS STRING) WHEN size(_dmn_selected) = 1 THEN "
					+ SparkSqlStringInput.sql(constant, names)
					+ " ELSE raise_error('FEEL string case requires a singleton selection') END";
		}
		return "element_at(transform(array(" + selection + "), _dmn_selected -> " + result + "), 1)";
	}
}

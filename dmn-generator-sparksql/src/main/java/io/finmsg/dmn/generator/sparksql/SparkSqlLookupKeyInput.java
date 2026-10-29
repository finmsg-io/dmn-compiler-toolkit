package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Terminal string lookups in bounded literal contexts, with canonical input
 * keys.
 */
final class SparkSqlLookupKeyInput {
	private record Plan(RuntimeContextExpression context, RuntimeValueReference key) {
	}

	static Integer inputSlot(RuntimeModel model, RuntimeDecision decision) {
		Plan plan = plan(model, decision);
		return plan == null ? null : plan.key().sourceSlot();
	}

	static Set<Integer> slots(RuntimeModel model) {
		var result = new LinkedHashSet<Integer>();
		for (var decision : model.decisions()) {
			Integer slot = inputSlot(model, decision);
			if (slot != null)
				result.add(slot);
		}
		return result;
	}

	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent() || decision.type() == null
				|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING).contains(decision.type().kind()))
			return null;
		RuntimeExpression expression = decision.expression().orElse(null);
		String name;
		List<RuntimeExpression> args;
		if (expression instanceof RuntimeFunctionCall call) {
			name = call.function();
			args = call.arguments();
		} else if (expression instanceof RuntimeInvocationExpression call && call.function().isPresent()
				&& call.target().isEmpty()) {
			name = call.function().get();
			if (!call.namedArguments().isEmpty()) {
				if (!call.positionalArguments().isEmpty() || call.namedArguments().size() != 2
						|| !new HashSet<>(call.namedArguments().stream().map(RuntimeNamedArgument::name).toList())
								.equals(Set.of("m", "key")))
					return null;
				args = SparkSqlExpressionEmitter.reorderNamedArguments(name, call.namedArguments());
			} else
				args = call.positionalArguments();
		} else
			return null;
		if (!name.equalsIgnoreCase("get value") || args.size() != 2
				|| !(args.getFirst() instanceof RuntimeContextExpression context) || context.entries().isEmpty()
				|| context.entries().size() > 64 || !(args.get(1) instanceof RuntimeValueReference key)
				|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING).contains(key.type().kind()) || model.inputs()
						.stream().noneMatch(i -> i.valueSlot() == key.sourceSlot() && i.type().equals(key.type())))
			return null;
		var names = new HashSet<String>();
		for (var entry : context.entries()) {
			if (entry.name().isEmpty() || entry.name().equals("__result__") || !text(entry.name(), 256)
					|| !names.add(entry.name()) || !(entry.expression() instanceof RuntimeConstant constant)
					|| !Set.of(RuntimeConstantKind.STRING, RuntimeConstantKind.NULL).contains(constant.kind())
					|| !text(constant.value(), 2048))
				return null;
		}
		return new Plan(context, key);
	}

	private static boolean text(String value, int bound) {
		return value.length() <= bound && value.codePoints().noneMatch(c -> c >= 0xD800 && c <= 0xDFFF);
	}

	static String column(int slot, Map<Integer, String> names) {
		String result = "__dmn_lookup_key_" + slot;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(result.toLowerCase(Locale.ROOT)))
			result += "_";
		return result;
	}

	static String sql(RuntimeModel model, RuntimeDecision decision, Map<Integer, String> names) {
		Plan plan = Objects.requireNonNull(plan(model, decision));
		String key = "`" + column(plan.key().sourceSlot(), names).replace("`", "``") + "`";
		StringBuilder sql = new StringBuilder("CASE ");
		for (var entry : plan.context().entries()) {
			var name = new RuntimeConstant(RuntimeConstantKind.STRING, entry.name(),
					RuntimeType.scalar(RuntimeTypeKind.STRING));
			sql.append("WHEN ").append(key).append(" = ").append(SparkSqlStringInput.sql(name, names)).append(" THEN ")
					.append(SparkSqlStringInput.sql(entry.expression(), names)).append(' ');
		}
		return sql.append("ELSE CAST(NULL AS STRING) END").toString();
	}
}

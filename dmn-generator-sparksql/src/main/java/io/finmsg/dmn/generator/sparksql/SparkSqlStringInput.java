package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/** String-only projections for proven concatenations over opaque ANY inputs. */
final class SparkSqlStringInput {
	static Set<Integer> inputSlots(RuntimeModel model, RuntimeDecision decision) {
		var slots = new LinkedHashSet<Integer>();
		if (decision.decisionTable().isPresent() || decision.expression().isEmpty()
				|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING).contains(decision.type().kind()))
			return Set.of();
		return shape(decision.expression().get(), model, slots, 0, new int[]{128}) == 1 ? slots : Set.of();
	}

	// -1 is unsupported, 0 is nullable/unknown string, 1 is proven string.
	private static int shape(RuntimeExpression expression, RuntimeModel model, Set<Integer> slots, int depth,
			int[] budget) {
		if (depth > 32 || --budget[0] < 0)
			return -1;
		if (expression instanceof RuntimeConstant constant)
			return constant.kind() == RuntimeConstantKind.STRING
					? 1
					: constant.kind() == RuntimeConstantKind.NULL ? 0 : -1;
		if (expression instanceof RuntimeValueReference reference) {
			RuntimeInput input = model.inputs().stream().filter(i -> i.valueSlot() == reference.sourceSlot())
					.findFirst().orElse(null);
			if (input == null || !input.type().equals(reference.type()))
				return -1;
			if (input.type().kind() == RuntimeTypeKind.STRING)
				return 1;
			if (input.type().kind() == RuntimeTypeKind.ANY) {
				slots.add(input.valueSlot());
				return 0;
			}
			return -1;
		}
		if (expression instanceof RuntimeBinaryExpression binary && binary.operator() == RuntimeBinaryOperator.ADD) {
			int left = shape(binary.left(), model, slots, depth + 1, budget);
			int right = shape(binary.right(), model, slots, depth + 1, budget);
			return left >= 0 && right >= 0 && (left == 1 || right == 1) ? 1 : -1;
		}
		return -1;
	}

	static Set<Integer> slots(RuntimeModel model) {
		var slots = new LinkedHashSet<Integer>();
		model.decisions().forEach(d -> slots.addAll(inputSlots(model, d)));
		return slots;
	}

	static String column(int slot, Map<Integer, String> names) {
		String result = "__dmn_string_" + slot;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(result.toLowerCase(Locale.ROOT)))
			result += "_";
		return result;
	}

	static String sql(RuntimeExpression expression, Map<Integer, String> names) {
		if (expression instanceof RuntimeConstant constant)
			return constant.kind() == RuntimeConstantKind.NULL
					? "CAST(NULL AS STRING)"
					: "'" + constant.value().replace("\\", "\\\\").replace("'", "''") + "'";
		if (expression instanceof RuntimeValueReference reference) {
			String name = reference.type().kind() == RuntimeTypeKind.ANY
					? column(reference.sourceSlot(), names)
					: names.get(reference.sourceSlot());
			return "`" + name.replace("`", "``") + "`";
		}
		var binary = (RuntimeBinaryExpression) expression;
		return "concat(" + sql(binary.left(), names) + ", " + sql(binary.right(), names) + ")";
	}
}

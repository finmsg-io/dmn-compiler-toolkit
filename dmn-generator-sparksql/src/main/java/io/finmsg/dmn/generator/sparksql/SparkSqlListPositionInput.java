package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/** Exact input positions for terminal homogeneous list edits. */
final class SparkSqlListPositionInput {
	private record Plan(String name, List<RuntimeExpression> args, RuntimeValueReference position) {
	}

	static Integer inputSlot(RuntimeModel model, RuntimeDecision decision) {
		Plan plan = plan(model, decision);
		return plan == null ? null : plan.position().sourceSlot();
	}
	static Set<Integer> slots(RuntimeModel model) {
		var slots = new LinkedHashSet<Integer>();
		for (var decision : model.decisions()) {
			Integer slot = inputSlot(model, decision);
			if (slot != null)
				slots.add(slot);
		}
		return slots;
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent() || decision.type() == null)
			return null;
		var expression = decision.expression().orElse(null);
		String name;
		List<RuntimeExpression> args;
		if (expression instanceof RuntimeFunctionCall call) {
			name = call.function();
			args = call.arguments();
		} else if (expression instanceof RuntimeInvocationExpression call && call.function().isPresent()
				&& call.namedArguments().isEmpty()) {
			name = call.function().get();
			args = call.positionalArguments();
		} else
			return null;
		name = name.toLowerCase(Locale.ROOT);
		if (!(name.equals("remove") && args.size() == 2 || name.equals("insert before") && args.size() == 3))
			return null;
		var list = args.getFirst().type();
		if (!textList(list, 0) || !value(model, args.getFirst(), new HashSet<>(), 0)
				|| !(args.get(1) instanceof RuntimeValueReference position)
				|| position.type().kind() != RuntimeTypeKind.NUMBER || model.inputs().stream()
						.noneMatch(i -> i.valueSlot() == position.sourceSlot() && i.type().equals(position.type())))
			return null;
		if (args.size() == 3
				&& (!list.elementType().equals(args.get(2).type()) || !value(model, args.get(2), new HashSet<>(), 0)))
			return null;
		var declared = decision.type();
		if (declared.kind() != RuntimeTypeKind.ANY && !declared.equals(list)
				&& !(declared.kind() == RuntimeTypeKind.LIST && declared.elementType() != null
						&& declared.elementType().kind() == RuntimeTypeKind.ANY))
			return null;
		return new Plan(name, args, position);
	}
	private static boolean textList(RuntimeType type, int depth) {
		if (type == null || depth > 2)
			return false;
		if (depth > 0 && Set.of(RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN).contains(type.kind()))
			return true;
		return type.kind() == RuntimeTypeKind.LIST && textList(type.elementType(), depth + 1);
	}
	private static boolean value(RuntimeModel model, RuntimeExpression expression, Set<Integer> active, int depth) {
		if (depth > 8)
			return false;
		if (expression instanceof RuntimeValueReference ref) {
			if (model.inputs().stream().anyMatch(i -> i.valueSlot() == ref.sourceSlot() && i.type().equals(ref.type())))
				return true;
			var decision = model.decisions().stream()
					.filter(d -> d.resultSlot() == ref.sourceSlot() && d.type().equals(ref.type())).findFirst()
					.orElse(null);
			if (decision == null || decision.decisionTable().isPresent() || !active.add(ref.sourceSlot()))
				return false;
			boolean result = decision.expression().isPresent()
					&& literal(decision.expression().get(), 0, new int[]{256});
			active.remove(ref.sourceSlot());
			return result;
		}
		return literal(expression, 0, new int[]{256});
	}
	private static boolean literal(RuntimeExpression expression, int depth, int[] budget) {
		if (depth > 2 || --budget[0] < 0)
			return false;
		if (expression instanceof RuntimeListExpression list)
			return list.elements().size() <= 64
					&& list.elements().stream().allMatch(item -> literal(item, depth + 1, budget));
		return expression instanceof RuntimeConstant c
				&& Set.of(RuntimeConstantKind.STRING, RuntimeConstantKind.BOOLEAN, RuntimeConstantKind.NULL)
						.contains(c.kind())
				&& c.value().length() <= 2048 && c.value().codePoints().noneMatch(n -> n >= 0xD800 && n <= 0xDFFF);
	}
	static String column(int slot, Map<Integer, String> names) {
		String column = "__dmn_list_position_" + slot;
		var used = new HashSet<String>();
		names.values().forEach(n -> used.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (used.contains(column.toLowerCase(Locale.ROOT)))
			column += "_";
		return column;
	}
	static String sql(RuntimeModel model, RuntimeDecision decision, Map<Integer, String> names) {
		Plan plan = Objects.requireNonNull(plan(model, decision));
		java.util.function.IntFunction<String> resolver = slot -> "`" + names.get(slot).replace("`", "``") + "`";
		String source = SparkSqlExpressionEmitter.emitWithBkms(plan.args().getFirst(), Map.of(), resolver);
		String position = "`" + column(plan.position().sourceSlot(), names) + "`";
		String p = "CAST(" + position + ".value AS BIGINT)";
		String index = "(CASE WHEN " + p + ">0 THEN " + p + "-1 ELSE CAST(size(_dmn_edit_list) AS BIGINT)+" + p
				+ " END)";
		String prefix = "slice(_dmn_edit_list,1,CAST(" + index + " AS INT))";
		String edited;
		String early = "_dmn_edit_list IS NULL OR (" + position + ".value IS NULL AND NOT " + position + ".invalid)";
		if (plan.name().equals("remove")) {
			String suffix = "CASE WHEN " + index
					+ "+1>=size(_dmn_edit_list) THEN array() ELSE slice(_dmn_edit_list,CAST(" + index
					+ "+2 AS INT),size(_dmn_edit_list)) END";
			edited = "CASE WHEN " + index + "<0 OR " + index + ">=size(_dmn_edit_list) THEN _dmn_edit_list ELSE concat("
					+ prefix + "," + suffix + ") END";
		} else {
			String item = SparkSqlExpressionEmitter.emitWithBkms(plan.args().get(2), Map.of(), resolver);
			early += " OR " + item + " IS NULL";
			String suffix = "CASE WHEN " + index + ">=size(_dmn_edit_list) THEN array() ELSE slice(_dmn_edit_list,CAST("
					+ index + "+1 AS INT),size(_dmn_edit_list)) END";
			edited = "CASE WHEN " + index + "<0 OR " + index + ">size(_dmn_edit_list) THEN NULL ELSE concat(" + prefix
					+ ",array(" + item + ")," + suffix + ") END";
		}
		String result = "element_at(transform(array(" + edited
				+ "), _dmn_edited -> CASE WHEN exists(_dmn_edited,_dmn_item -> _dmn_item IS NULL) THEN raise_error('Null item in FEEL list edit') ELSE _dmn_edited END),1)";
		return "element_at(transform(array(" + source + "), _dmn_edit_list -> CASE WHEN " + early + " THEN NULL WHEN "
				+ position + ".invalid THEN raise_error('Invalid FEEL list position') ELSE " + result + " END),1)";
	}
}

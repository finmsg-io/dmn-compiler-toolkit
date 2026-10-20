package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/** Native calendar calculations over canonical local-date input projections. */
final class SparkSqlCalendarInput {
	static Integer inputSlot(RuntimeModel model, RuntimeDecision decision) {
		Integer comparison = SparkSqlDateComparisonInput.inputSlot(model, decision);
		if (comparison != null)
			return comparison;
		if (decision.decisionTable().isPresent())
			return null;
		RuntimeExpression expression = decision.expression().orElse(null);
		String name;
		List<RuntimeExpression> args;
		if (expression instanceof RuntimeFunctionCall call) {
			name = call.function();
			args = call.arguments();
		} else if (expression instanceof RuntimeInvocationExpression call && call.function().isPresent()) {
			name = call.function().get();
			args = call.namedArguments().isEmpty()
					? call.positionalArguments()
					: SparkSqlExpressionEmitter.reorderNamedArguments(name, call.namedArguments());
		} else
			return null;
		name = name.toLowerCase(Locale.ROOT);
		if (!Set.of("day of year", "day of week", "month of year", "week of year").contains(name) || args.size() != 1
				|| !(args.getFirst() instanceof RuntimeValueReference ref) || ref.type().kind() != RuntimeTypeKind.ANY)
			return null;
		RuntimeTypeKind result = name.equals("day of year") || name.equals("week of year")
				? RuntimeTypeKind.NUMBER
				: RuntimeTypeKind.STRING;
		if (decision.type().kind() != RuntimeTypeKind.ANY && decision.type().kind() != result)
			return null;
		return model.inputs().stream()
				.anyMatch(i -> i.valueSlot() == ref.sourceSlot() && i.type().kind() == RuntimeTypeKind.ANY)
						? ref.sourceSlot()
						: null;
	}

	static Set<Integer> slots(RuntimeModel model) {
		var slots = new LinkedHashSet<Integer>();
		for (var decision : model.decisions()) {
			slots.addAll(SparkSqlDateTableInput.slots(model, decision));
			Integer slot = inputSlot(model, decision);
			if (slot != null)
				slots.add(slot);
		}
		return slots;
	}

	static String column(int slot, Map<Integer, String> names) {
		String column = "__dmn_calendar_" + slot;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(column.toLowerCase(Locale.ROOT)))
			column += "_";
		return column;
	}

	static String sql(RuntimeDecision decision, String column) {
		RuntimeExpression expression = decision.expression().orElseThrow();
		String name = expression instanceof RuntimeFunctionCall call
				? call.function()
				: ((RuntimeInvocationExpression) expression).function().orElseThrow();
		String source = "`" + column.replace("`", "``") + "`";
		String year = "CAST(" + source + ".year AS BIGINT)";
		String month = source + ".month";
		String day = source + ".day";
		String ordinal = "(element_at(array(0,31,59,90,120,151,181,212,243,273,304,334), " + month + ") + " + day
				+ " + CASE WHEN " + month + " > 2 AND " + leap(year) + " THEN 1 ELSE 0 END)";
		String weekday = "(pmod(" + daysBeforeYear(year) + " + " + ordinal + " - 1, 7) + 1)";
		String result = switch (name.toLowerCase(Locale.ROOT)) {
			case "day of year" -> ordinal;
			case "day of week" ->
				"element_at(array('Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'), CAST("
						+ weekday + " AS INT))";
			case "month of year" ->
				"element_at(array('January','February','March','April','May','June','July','August','September','October','November','December'), "
						+ month + ")";
			case "week of year" -> {
				String week = "floor((" + ordinal + " - " + weekday + " + 10) / 7.0)";
				yield "CASE WHEN " + week + " < 1 THEN " + weeksInYear("(" + year + " - 1)") + " WHEN " + week + " > "
						+ weeksInYear(year) + " THEN 1 ELSE " + week + " END";
			}
			default -> throw new IllegalArgumentException(name);
		};
		return "CASE WHEN " + source + ".kind IN ('DATE','DATE_TIME','PARSED_DATE') THEN " + result + " ELSE NULL END";
	}

	private static String leap(String year) {
		return "(pmod(" + year + ",4)=0 AND (pmod(" + year + ",100)<>0 OR pmod(" + year + ",400)=0))";
	}

	private static String daysBeforeYear(String year) {
		String previous = "(" + year + " - 1)";
		return "(365 * " + previous + " + floor(" + previous + " / 4.0) - floor(" + previous + " / 100.0) + floor("
				+ previous + " / 400.0))";
	}

	private static String weeksInYear(String year) {
		String januaryFirst = "(pmod(" + daysBeforeYear(year) + ",7)+1)";
		return "(CASE WHEN " + januaryFirst + "=4 OR (" + januaryFirst + "=3 AND " + leap(year)
				+ ") THEN 53 ELSE 52 END)";
	}
}

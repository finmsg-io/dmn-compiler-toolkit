package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/** Exact terminal date tables with two complementary ordered comparisons. */
final class SparkSqlDateTableInput {
	private record Plan(RuntimeValueReference source, RuntimeExpression endpoint, RuntimeUnaryTestOperator operator,
			RuntimeDecisionTable table, Set<Integer> slots) {
	}

	static Set<Integer> slots(RuntimeModel model, RuntimeDecision decision) {
		Plan plan = plan(model, decision);
		return plan == null ? Set.of() : plan.slots();
	}

	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		RuntimeDecisionTable table = decision.decisionTable().orElse(null);
		if (table == null || table.hitPolicy() != RuntimeHitPolicy.UNIQUE || table.aggregation().isPresent()
				|| table.inputs().size() != 1 || table.outputs().size() != 1 || table.rules().size() != 2
				|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.BOOLEAN).contains(decision.type().kind())
				|| table.inputs().getFirst().allowedValues().isPresent()
				|| table.outputs().getFirst().allowedValues().isPresent()
				|| table.outputs().getFirst().defaultValue().isPresent()
				|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.BOOLEAN)
						.contains(table.outputs().getFirst().type().kind())
				|| !(table.inputs().getFirst().expression() instanceof RuntimeValueReference source)
				|| !dateInput(model, source))
			return null;
		var slots = new LinkedHashSet<Integer>();
		slots.add(source.sourceSlot());
		RuntimeComparisonUnaryTest first = null;
		for (var rule : table.rules()) {
			var tests = rule.inputEntries().getFirst();
			if (tests.negated() || tests.wildcard() || tests.tests().size() != 1
					|| !(tests.tests().getFirst() instanceof RuntimeComparisonUnaryTest test)
					|| !(rule.outputEntries().getFirst() instanceof RuntimeConstant output)
					|| output.kind() != RuntimeConstantKind.BOOLEAN
					|| !Set.of("true", "false").contains(output.value()))
				return null;
			if (first == null)
				first = test;
			else if (!first.endpoint().equals(test.endpoint()) || complement(first.operator()) != test.operator())
				return null;
		}
		RuntimeExpression endpoint = first.endpoint();
		if (endpoint instanceof RuntimeValueReference reference && dateInput(model, reference))
			slots.add(reference.sourceSlot());
		else if (SparkSqlDateComparisonInput.date(endpoint) == null)
			return null;
		if (complement(first.operator()) == null)
			return null;
		return new Plan(source, endpoint, first.operator(), table, Set.copyOf(slots));
	}

	private static boolean dateInput(RuntimeModel model, RuntimeValueReference reference) {
		return reference.type().kind() == RuntimeTypeKind.DATE && model.inputs().stream()
				.anyMatch(i -> i.valueSlot() == reference.sourceSlot() && i.type().kind() == RuntimeTypeKind.DATE);
	}

	private static RuntimeUnaryTestOperator complement(RuntimeUnaryTestOperator operator) {
		return switch (operator) {
			case LESS -> RuntimeUnaryTestOperator.GREATER_EQUAL;
			case LESS_EQUAL -> RuntimeUnaryTestOperator.GREATER;
			case GREATER -> RuntimeUnaryTestOperator.LESS_EQUAL;
			case GREATER_EQUAL -> RuntimeUnaryTestOperator.LESS;
			default -> null;
		};
	}

	private static String column(int slot, Map<Integer, String> names) {
		return "`" + SparkSqlCalendarInput.column(slot, names).replace("`", "``") + "`";
	}

	private static String key(String column) {
		return "(CAST(" + column + ".year AS BIGINT)*10000 + " + column + ".month*100 + " + column + ".day)";
	}

	static String sql(RuntimeModel model, RuntimeDecision decision, Map<Integer, String> names) {
		Plan plan = Objects.requireNonNull(plan(model, decision));
		String source = column(plan.source().sourceSlot(), names);
		String valid = source + ".kind='DATE'";
		String endpoint;
		if (plan.endpoint() instanceof RuntimeValueReference reference) {
			String other = column(reference.sourceSlot(), names);
			valid += " AND " + other + ".kind='DATE'";
			endpoint = key(other);
		} else {
			var date = Objects.requireNonNull(SparkSqlDateComparisonInput.date(plan.endpoint()));
			endpoint = Long.toString(date.getYear() * 10000L + date.getMonthValue() * 100L + date.getDayOfMonth());
		}
		String op = switch (plan.operator()) {
			case LESS -> "<";
			case LESS_EQUAL -> "<=";
			case GREATER -> ">";
			case GREATER_EQUAL -> ">=";
			default -> throw new IllegalArgumentException();
		};
		String yes = ((RuntimeConstant) plan.table().rules().get(0).outputEntries().getFirst()).value();
		String no = ((RuntimeConstant) plan.table().rules().get(1).outputEntries().getFirst()).value();
		return "CASE WHEN " + valid + " THEN CASE WHEN " + key(source) + " " + op + " " + endpoint + " THEN " + yes
				+ " ELSE " + no + " END ELSE CAST(NULL AS BOOLEAN) END";
	}
}

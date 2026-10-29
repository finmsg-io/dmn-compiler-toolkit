package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.*;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.StructType;

/**
 * Exact three-way temporal UNIQUE tables over typed inputs and context members.
 */
final class SparkSqlTemporalTableInput {
	private record Plan(RuntimeValueReference left, RuntimeExpression right, RuntimeDecisionTable table,
			boolean expressionEquality) {
	}
	private static boolean temporal(RuntimeType type) {
		return Set.of(RuntimeTypeKind.DATE, RuntimeTypeKind.TIME, RuntimeTypeKind.DATE_TIME, RuntimeTypeKind.DURATION,
				RuntimeTypeKind.DAYS_TIME_DURATION, RuntimeTypeKind.YEARS_MONTHS_DURATION, RuntimeTypeKind.STRING,
				RuntimeTypeKind.NUMBER, RuntimeTypeKind.BOOLEAN).contains(type.kind());
	}
	static Set<Integer> ids(RuntimeModel model) {
		var ids = new LinkedHashSet<Integer>();
		for (var d : model.decisions())
			if (plan(model, d) != null)
				ids.add(d.id());
		return ids;
	}
	static Set<Integer> slots(RuntimeModel model, RuntimeDecision decision) {
		var plan = plan(model, decision);
		if (plan == null)
			return Set.of();
		int right = plan.right() instanceof RuntimeValueReference r
				? r.sourceSlot()
				: ((RuntimeValueReference) ((RuntimePathExpression) plan.right()).source()).sourceSlot();
		return new HashSet<>(List.of(plan.left().sourceSlot(), right));
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		var table = decision.decisionTable().orElse(null);
		if (table == null || table.hitPolicy() != RuntimeHitPolicy.UNIQUE || table.aggregation().isPresent()
				|| table.inputs().size() != 1 || table.outputs().size() != 1
				|| (table.rules().size() != 3 && table.rules().size() != 2)
				|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING).contains(decision.type().kind())
				|| table.inputs().getFirst().allowedValues().isPresent()
				|| table.outputs().getFirst().allowedValues().isPresent()
				|| table.outputs().getFirst().defaultValue().isPresent()
				|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING)
						.contains(table.outputs().getFirst().type().kind())
				|| !(table.inputs().getFirst().expression() instanceof RuntimeValueReference left)
				|| !temporal(left.type()) || model.inputs().stream()
						.noneMatch(i -> i.valueSlot() == left.sourceSlot() && i.type().equals(left.type())))
			return null;
		var operators = new HashSet<RuntimeUnaryTestOperator>();
		RuntimeExpression endpoint = null;
		boolean expressionEquality = false;
		for (var rule : table.rules()) {
			var tests = rule.inputEntries().getFirst();
			if (tests.wildcard() || tests.tests().size() != 1
					|| !(rule.outputEntries().getFirst() instanceof RuntimeConstant output)
					|| output.kind() != RuntimeConstantKind.STRING || output.value().length() > 2048
					|| output.value().codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF))
				return null;
			RuntimeExpression bound;
			RuntimeUnaryTestOperator operator;
			if (tests.tests().getFirst() instanceof RuntimeComparisonUnaryTest test) {
				bound = test.endpoint();
				operator = test.operator();
			} else if (tests.tests().getFirst() instanceof RuntimeExpressionUnaryTest test) {
				bound = test.expression();
				operator = RuntimeUnaryTestOperator.EQUAL;
				expressionEquality = true;
			} else
				return null;
			if (tests.negated()) {
				if (operator != RuntimeUnaryTestOperator.EQUAL)
					return null;
				operator = RuntimeUnaryTestOperator.NOT_EQUAL;
			}
			if (!Set.of(RuntimeUnaryTestOperator.EQUAL, RuntimeUnaryTestOperator.NOT_EQUAL,
					RuntimeUnaryTestOperator.LESS, RuntimeUnaryTestOperator.GREATER).contains(operator)
					|| !operators.add(operator) || endpoint != null && !endpoint.equals(bound))
				return null;
			endpoint = bound;
		}
		if (!(operators.equals(Set.of(RuntimeUnaryTestOperator.EQUAL, RuntimeUnaryTestOperator.NOT_EQUAL))
				|| operators.equals(Set.of(RuntimeUnaryTestOperator.EQUAL, RuntimeUnaryTestOperator.LESS,
						RuntimeUnaryTestOperator.GREATER))))
			return null;
		if (!left.type().equals(endpoint.type()))
			return null;
		RuntimeValueReference input;
		if (endpoint instanceof RuntimeValueReference r) {
			if (Set.of(RuntimeTypeKind.NUMBER, RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN)
					.contains(left.type().kind()))
				return null;
			input = r;
		} else if (endpoint instanceof RuntimePathExpression path && path.source() instanceof RuntimeValueReference r
				&& r.type().kind() == RuntimeTypeKind.CONTEXT) {
			if (r.type().fieldLayout().stream().noneMatch(f -> f.name().equals(path.member())
					&& f.type().equals(path.type()) && (path.fieldIndex() < 0 || path.fieldIndex() == f.index())))
				return null;
			input = r;
		} else
			return null;
		if (model.inputs().stream()
				.noneMatch(i -> i.valueSlot() == input.sourceSlot() && i.type().equals(input.type())))
			return null;
		return new Plan(left, endpoint, table, expressionEquality);
	}
	static StructType type() {
		return new StructType().add("left", SparkSqlTemporalValue.type()).add("right", SparkSqlTemporalValue.type());
	}
	static String column(int id, Map<Integer, String> names) {
		String column = "__dmn_temporal_table_" + id;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(column.toLowerCase(Locale.ROOT)))
			column += "_";
		return column;
	}
	static Object project(RuntimeModel model, int id, Map<Integer, ?> values) {
		var plan = Objects.requireNonNull(
				plan(model, model.decisions().stream().filter(d -> d.id() == id).findFirst().orElseThrow()));
		Object left = DmnRuntime.coerce(values.get(plan.left().sourceSlot()), plan.left().type());
		RuntimeValueReference reference = plan.right() instanceof RuntimeValueReference r
				? r
				: (RuntimeValueReference) ((RuntimePathExpression) plan.right()).source();
		Object right = DmnRuntime.coerce(values.get(reference.sourceSlot()), reference.type());
		if (plan.right() instanceof RuntimePathExpression path) {
			right = right instanceof RuntimeContextValue c
					? path.fieldIndex() >= 0 ? c.field(path.fieldIndex()) : c.field(path.member())
					: right instanceof Map<?, ?> m ? m.get(path.member()) : null;
		}
		return RowFactory.create(SparkSqlTemporalValue.row(left, false),
				SparkSqlTemporalValue.row(right, plan.expressionEquality()));
	}
	static String sql(RuntimeModel model, RuntimeDecision decision, Map<Integer, String> names) {
		var plan = Objects.requireNonNull(plan(model, decision));
		String column = "`" + column(decision.id(), names) + "`";
		String l = column + ".left", r = column + ".right";
		String equal = "(" + l + ".equalityKind=" + r + ".equalityKind AND " + l + ".equalityKind<>'OTHER' AND struct("
				+ l + ".x," + l + ".y," + l + ".z) = struct(" + r + ".x," + r + ".y," + r + ".z) AND (" + l
				+ ".equalityZone <=> " + r + ".equalityZone))";
		String valid = "(" + l + ".kind=" + r + ".kind AND " + l
				+ ".kind NOT IN ('NULL','OTHER','NAMED_TIME','INVALID'))";
		String a = "struct(" + l + ".a," + l + ".b," + l + ".c)", b = "struct(" + r + ".a," + r + ".b," + r + ".c)";
		var conditions = new ArrayList<String>();
		var outputs = new ArrayList<String>();
		for (var rule : plan.table().rules()) {
			var test = rule.inputEntries().getFirst().tests().getFirst();
			RuntimeUnaryTestOperator op = test instanceof RuntimeComparisonUnaryTest t
					? t.operator()
					: RuntimeUnaryTestOperator.EQUAL;
			if (rule.inputEntries().getFirst().negated())
				op = RuntimeUnaryTestOperator.NOT_EQUAL;
			String comparison = op == RuntimeUnaryTestOperator.LESS ? " < " : " > ";
			String ordered = "CASE WHEN " + l + ".kind IN ('NUMBER','STRING') THEN " + l + ".equalityZone" + comparison
					+ r + ".equalityZone ELSE " + a + comparison + b + " END";
			conditions.add(op == RuntimeUnaryTestOperator.EQUAL
					? equal
					: op == RuntimeUnaryTestOperator.NOT_EQUAL
							? "NOT (" + equal + ")"
							: valid + " AND (" + ordered + ")");
			outputs.add(SparkSqlStringInput.sql(rule.outputEntries().getFirst(), names));
		}
		String count = String.join(" + ",
				conditions.stream().map(c -> "(CASE WHEN " + c + " THEN 1 ELSE 0 END)").toList());
		String invalid = "(" + l + ".equalityKind IN ('NUMBER','INVALID') AND " + r
				+ ".equalityKind IN ('NUMBER','INVALID') AND (" + l + ".equalityKind='INVALID' OR " + r
				+ ".equalityKind='INVALID'))";
		var sql = new StringBuilder(
				"CASE WHEN " + invalid + " THEN CAST(raise_error('Invalid FEEL number') AS STRING) WHEN (" + count
						+ ") > 1 THEN CAST(raise_error('Multiple FEEL UNIQUE matches') AS STRING)");
		for (int i = 0; i < conditions.size(); i++)
			sql.append(" WHEN ").append(conditions.get(i)).append(" THEN ").append(outputs.get(i));
		return sql.append(" ELSE CAST(NULL AS STRING) END").toString();
	}
}

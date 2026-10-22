package io.finmsg.dmn.generator.sparksql;
import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/** Proven terminal numeric contexts over exact scalar arithmetic. */
final class SparkSqlExactArithmeticInput {
	private record Plan(RuntimeContextExpression context, List<Integer> slots) {
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent() || decision.type().kind() != RuntimeTypeKind.CONTEXT
				|| decision.type().fieldLayout().isEmpty()
				|| decision.type().fieldLayout().stream().anyMatch(f -> f.type().kind() != RuntimeTypeKind.NUMBER)
				|| !(decision.expression().orElse(null) instanceof RuntimeContextExpression context)
				|| context.entries().size() != decision.type().fieldLayout().size() || context.entries().size() > 16)
			return null;
		var slots = new TreeSet<Integer>();
		var names = new HashSet<String>();
		int[] budget = {512};
		for (var entry : context.entries())
			if (entry.name().isEmpty() || entry.name().length() > 256 || entry.name().equals("__result__")
					|| !names.add(entry.name().toLowerCase(Locale.ROOT))
					|| entry.name().codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF)
					|| decision.type().fieldLayout().stream().noneMatch(f -> f.name().equals(entry.name()))
					|| !numeric(model, entry.expression(), slots, budget, 0))
				return null;
		return slots.isEmpty() ? null : new Plan(context, List.copyOf(slots));
	}
	private static boolean numeric(RuntimeModel model, RuntimeExpression e, Set<Integer> slots, int[] budget,
			int depth) {
		if (--budget[0] < 0 || depth > 24)
			return false;
		if (e instanceof RuntimeConstant c) {
			if (c.kind() == RuntimeConstantKind.NULL)
				return true;
			if (c.kind() != RuntimeConstantKind.NUMBER || c.value().length() > 256)
				return false;
			try {
				var n = new BigDecimal(c.value());
				return n.precision() <= 128 && Math.abs((long) n.scale()) <= 1024;
			} catch (NumberFormatException invalid) {
				return false;
			}
		}
		if (e instanceof RuntimeValueReference r && r.type().kind() == RuntimeTypeKind.NUMBER && model.inputs().stream()
				.anyMatch(i -> i.valueSlot() == r.sourceSlot() && i.type().equals(r.type()))) {
			slots.add(r.sourceSlot());
			return true;
		}
		if (e instanceof RuntimeBinaryExpression b && Set.of(RuntimeBinaryOperator.ADD, RuntimeBinaryOperator.SUBTRACT,
				RuntimeBinaryOperator.MULTIPLY, RuntimeBinaryOperator.DIVIDE).contains(b.operator()))
			return numeric(model, b.left(), slots, budget, depth + 1)
					&& numeric(model, b.right(), slots, budget, depth + 1);
		if (e instanceof RuntimeUnaryExpression u
				&& Set.of(RuntimeUnaryOperator.POSITIVE, RuntimeUnaryOperator.NEGATE).contains(u.operator()))
			return numeric(model, u.operand(), slots, budget, depth + 1);
		if (e instanceof RuntimeConditionalExpression c)
			return condition(model, c.condition(), slots, budget, depth + 1)
					&& numeric(model, c.thenExpression(), slots, budget, depth + 1)
					&& numeric(model, c.elseExpression(), slots, budget, depth + 1);
		return false;
	}
	private static boolean condition(RuntimeModel model, RuntimeExpression e, Set<Integer> slots, int[] budget,
			int depth) {
		if (--budget[0] < 0 || depth > 24)
			return false;
		if (e instanceof RuntimeConstant c)
			return c.kind() == RuntimeConstantKind.BOOLEAN || c.kind() == RuntimeConstantKind.NULL;
		if (e instanceof RuntimeBinaryExpression b && Set.of(RuntimeBinaryOperator.EQUAL,
				RuntimeBinaryOperator.NOT_EQUAL, RuntimeBinaryOperator.LESS, RuntimeBinaryOperator.LESS_EQUAL,
				RuntimeBinaryOperator.GREATER, RuntimeBinaryOperator.GREATER_EQUAL).contains(b.operator()))
			return numeric(model, b.left(), slots, budget, depth + 1)
					&& numeric(model, b.right(), slots, budget, depth + 1);
		if (e instanceof RuntimeInExpression in && !in.tests().wildcard() && in.tests().tests().size() == 1
				&& in.tests().tests().getFirst() instanceof RuntimeRangeUnaryTest test
				&& test.range().lower().orElse(null) instanceof RuntimeConstant low
				&& low.kind() == RuntimeConstantKind.NUMBER
				&& test.range().upper().orElse(null) instanceof RuntimeConstant high
				&& high.kind() == RuntimeConstantKind.NUMBER && numeric(model, low, slots, budget, depth + 1)
				&& numeric(model, high, slots, budget, depth + 1)
				&& new BigDecimal(low.value()).compareTo(new BigDecimal(high.value())) <= 0)
			return numeric(model, in.value(), slots, budget, depth + 1);
		return false;
	}
	static boolean supports(RuntimeModel model, RuntimeDecision decision) {
		return plan(model, decision) != null;
	}
	static Set<Integer> ids(RuntimeModel model) {
		var ids = new LinkedHashSet<Integer>();
		for (var d : model.decisions())
			if (supports(model, d))
				ids.add(d.id());
		return ids;
	}
	static String column(int id, Map<Integer, String> names) {
		String name = "__dmn_arithmetic_" + id;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(name.toLowerCase(Locale.ROOT)))
			name += "_";
		return name;
	}
	static StructType type(RuntimeModel model, RuntimeDecision decision) {
		var result = new StructType();
		for (int slot : Objects.requireNonNull(plan(model, decision)).slots())
			result = result.add("v" + slot, SparkSqlDecimalSql.type());
		return result;
	}
	static long[] slots(RuntimeModel model, RuntimeDecision decision) {
		return Objects.requireNonNull(plan(model, decision)).slots().stream().mapToLong(Integer::longValue).toArray();
	}
	static Object project(long[] slots, Map<Integer, ?> inputs) {
		return RowFactory.create(java.util.Arrays
				.stream(slots).mapToObj(slot -> SparkSqlDecimalSql.row(DmnRuntime
						.coerce(inputs.get(Math.toIntExact(slot)), RuntimeType.scalar(RuntimeTypeKind.NUMBER))))
				.toArray());
	}

	static String sql(RuntimeModel model, RuntimeDecision decision, Map<Integer, String> names) {
		var p = Objects.requireNonNull(plan(model, decision));
		var fields = new ArrayList<String>();
		String source = "`" + column(decision.id(), names) + "`";
		for (var entry : p.context().entries()) {
			fields.add("'" + entry.name().replace("\\", "\\\\").replace("'", "\\'") + "'");
			fields.add(SparkSqlDecimalSql.payload(number(entry.expression(), source)));
		}
		return "named_struct(" + String.join(",", fields) + ")";
	}
	private static String number(RuntimeExpression e, String source) {
		if (e instanceof RuntimeConstant c)
			return c.kind() == RuntimeConstantKind.NULL
					? SparkSqlDecimalSql.nil()
					: SparkSqlDecimalSql.constant(new BigDecimal(c.value()));
		if (e instanceof RuntimeValueReference r)
			return source + ".v" + r.sourceSlot();
		if (e instanceof RuntimeBinaryExpression b) {
			String a = number(b.left(), source), r = number(b.right(), source);
			if (b.operator() == RuntimeBinaryOperator.DIVIDE)
				return SparkSqlDecimalSql.divide(a, r);
			return b.operator() == RuntimeBinaryOperator.MULTIPLY
					? SparkSqlDecimalSql.multiply(a, r)
					: SparkSqlDecimalSql.add(a, r, b.operator() == RuntimeBinaryOperator.SUBTRACT);
		}
		if (e instanceof RuntimeUnaryExpression u) {
			String value = number(u.operand(), source);
			if (u.operator() == RuntimeUnaryOperator.POSITIVE)
				return value;
			return SparkSqlDecimalSql.let("arithmetic_unary", value,
					"named_struct('digits',arithmetic_unary.digits,'scale',arithmetic_unary.scale,'negative',NOT arithmetic_unary.negative AND arithmetic_unary.digits<>'0','invalid',arithmetic_unary.invalid)");
		}
		var c = (RuntimeConditionalExpression) e;
		return "CASE WHEN " + bool(c.condition(), source) + " THEN " + number(c.thenExpression(), source) + " ELSE "
				+ number(c.elseExpression(), source) + " END";
	}
	private static String bool(RuntimeExpression e, String source) {
		if (e instanceof RuntimeConstant c)
			return c.kind() == RuntimeConstantKind.NULL ? "CAST(NULL AS BOOLEAN)" : c.value();
		if (e instanceof RuntimeInExpression in) {
			var range = ((RuntimeRangeUnaryTest) in.tests().tests().getFirst()).range();
			String a = SparkSqlDecimalSql.compare(number(in.value(), source),
					number(range.lower().orElseThrow(), source));
			String b = SparkSqlDecimalSql.compare(number(in.value(), source),
					number(range.upper().orElseThrow(), source));
			String result = "(" + a + (range.lowerBoundary() == RuntimeRangeBoundary.CLOSED ? ">=0" : " >0") + " AND "
					+ b + (range.upperBoundary() == RuntimeRangeBoundary.CLOSED ? "<=0" : "<0") + ")";
			return in.tests().negated() ? "NOT " + result : result;
		}
		var b = (RuntimeBinaryExpression) e;
		String left = number(b.left(), source), right = number(b.right(), source);
		String op = switch (b.operator()) {
			case EQUAL -> "=";
			case NOT_EQUAL -> "<>";
			case LESS -> "<";
			case LESS_EQUAL -> "<=";
			case GREATER -> ">";
			case GREATER_EQUAL -> ">=";
			default -> throw new IllegalArgumentException();
		};
		if (b.operator() == RuntimeBinaryOperator.EQUAL || b.operator() == RuntimeBinaryOperator.NOT_EQUAL) {
			String equal = SparkSqlDecimalSql.let("arithmetic_eq_a", left, SparkSqlDecimalSql.let("arithmetic_eq_b",
					right,
					"CASE WHEN arithmetic_eq_a.digits IS NULL AND NOT arithmetic_eq_a.invalid AND arithmetic_eq_b.digits IS NULL AND NOT arithmetic_eq_b.invalid THEN TRUE WHEN arithmetic_eq_a.digits IS NULL AND NOT arithmetic_eq_a.invalid OR arithmetic_eq_b.digits IS NULL AND NOT arithmetic_eq_b.invalid THEN FALSE ELSE "
							+ SparkSqlDecimalSql.compare("arithmetic_eq_a", "arithmetic_eq_b") + "=0 END"));
			return b.operator() == RuntimeBinaryOperator.NOT_EQUAL ? "NOT (" + equal + ")" : equal;
		}
		return "(" + SparkSqlDecimalSql.compare(left, right) + op + "0)";
	}
}

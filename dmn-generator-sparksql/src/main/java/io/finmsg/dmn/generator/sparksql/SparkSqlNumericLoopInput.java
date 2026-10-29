package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/** Bounded numeric expressions over fields of a direct context-list input. */
final class SparkSqlNumericLoopInput {
	private record Plan(RuntimeForExpression loop, RuntimeValueReference source, List<RuntimeField> fields) {
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision d) {
		if (d.decisionTable().isPresent() || d.type().kind() != RuntimeTypeKind.LIST || d.type().elementType() == null
				|| d.type().elementType().kind() != RuntimeTypeKind.NUMBER
				|| !(d.expression().orElse(null) instanceof RuntimeForExpression loop) || loop.partialSlot() >= 0
				|| loop.iterations().size() != 1 || loop.iterations().getFirst().end().isPresent()
				|| !(loop.iterations().getFirst().source() instanceof RuntimeValueReference source)
				|| source.type().kind() != RuntimeTypeKind.LIST || source.type().elementType() == null
				|| source.type().elementType().kind() != RuntimeTypeKind.CONTEXT || model.inputs().stream()
						.noneMatch(i -> i.valueSlot() == source.sourceSlot() && i.type().equals(source.type())))
			return null;
		var fields = source.type().elementType().fieldLayout();
		if (fields.isEmpty() || fields.size() > 16
				|| fields.stream().anyMatch(f -> f.type().kind() != RuntimeTypeKind.NUMBER)
				|| new HashSet<>(fields.stream().map(RuntimeField::name).toList()).size() != fields.size())
			return null;
		var p = new Plan(loop, source, fields);
		return numeric(p, loop.result(), new int[]{256}, 0) ? p : null;
	}
	private static int field(Plan p, RuntimePathExpression path) {
		if (!(path.source() instanceof RuntimeLocalReference r) || r.lexicalDepth() != 0
				|| r.localSlot() != p.loop().iterations().getFirst().localSlot()
				|| !r.type().equals(p.source().type().elementType()) || path.type().kind() != RuntimeTypeKind.NUMBER)
			return -1;
		if (path.fieldIndex() >= 0)
			return path.fieldIndex() < p.fields().size() ? path.fieldIndex() : -1;
		for (int i = 0; i < p.fields().size(); i++)
			if (p.fields().get(i).name().equals(path.member()))
				return i;
		return -1;
	}
	private static boolean numeric(Plan p, RuntimeExpression e, int[] budget, int depth) {
		if (--budget[0] < 0 || depth > 20)
			return false;
		if (e instanceof RuntimePathExpression path)
			return field(p, path) >= 0;
		if (e instanceof RuntimeConstant c) {
			if (c.kind() == RuntimeConstantKind.NULL)
				return true;
			if (c.kind() != RuntimeConstantKind.NUMBER || c.value().length() > 256)
				return false;
			try {
				var v = new BigDecimal(c.value());
				return v.precision() <= 128 && Math.abs((long) v.scale()) <= 1024;
			} catch (NumberFormatException invalid) {
				return false;
			}
		}
		if (e instanceof RuntimeBinaryExpression b
				&& Set.of(RuntimeBinaryOperator.ADD, RuntimeBinaryOperator.SUBTRACT, RuntimeBinaryOperator.MULTIPLY,
						RuntimeBinaryOperator.DIVIDE, RuntimeBinaryOperator.POWER).contains(b.operator()))
			return numeric(p, b.left(), budget, depth + 1) && numeric(p, b.right(), budget, depth + 1);
		return e instanceof RuntimeUnaryExpression u
				&& Set.of(RuntimeUnaryOperator.NEGATE, RuntimeUnaryOperator.POSITIVE).contains(u.operator())
				&& numeric(p, u.operand(), budget, depth + 1);
	}
	static boolean supports(RuntimeModel model, RuntimeDecision d) {
		return plan(model, d) != null;
	}
	static Set<Integer> ids(RuntimeModel model) {
		var ids = new LinkedHashSet<Integer>();
		for (var d : model.decisions())
			if (supports(model, d))
				ids.add(d.id());
		return ids;
	}
	static String column(int id, Map<Integer, String> names) {
		String n = "__dmn_numeric_loop_" + id;
		var occupied = new HashSet<String>();
		names.values().forEach(v -> occupied.add(v.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(n.toLowerCase(Locale.ROOT)))
			n += "_";
		return n;
	}
	static long slot(RuntimeModel model, RuntimeDecision d) {
		return Objects.requireNonNull(plan(model, d)).source().sourceSlot();
	}
	static String[] fields(RuntimeModel model, RuntimeDecision d) {
		return Objects.requireNonNull(plan(model, d)).fields().stream().map(RuntimeField::name).toArray(String[]::new);
	}
	static ArrayType type(RuntimeModel model, RuntimeDecision d) {
		var t = new StructType();
		var p = Objects.requireNonNull(plan(model, d));
		for (int i = 0; i < p.fields().size(); i++)
			t = t.add("f" + i, SparkSqlDecimalSql.type());
		return DataTypes.createArrayType(t, false);
	}
	static Object project(RuntimeModel model, int slot, String[] fields, Map<Integer, ?> values) {
		var input = model.inputs().stream().filter(i -> i.valueSlot() == slot).findFirst().orElseThrow();
		Object source = DmnRuntime.coerce(values.get(slot), input.type());
		if (source == null)
			return null;
		var rows = new ArrayList<Object>();
		for (Object item : (List<?>) source) {
			var row = new Object[fields.length];
			for (int i = 0; i < fields.length; i++)
				row[i] = SparkSqlDecimalSql.row(item == null ? null : ((Map<?, ?>) item).get(fields[i]));
			rows.add(RowFactory.create(row));
		}
		return rows;
	}
	static String sql(RuntimeModel model, RuntimeDecision d, Map<Integer, String> names) {
		var p = Objects.requireNonNull(plan(model, d));
		var steps = new ArrayList<RuntimeExpression>();
		var indexes = new LinkedHashMap<RuntimeExpression, Integer>();
		instructions(p.loop().result(), steps, indexes);
		var branches = new StringBuilder("CASE numeric_step");
		for (int i = 0; i < steps.size(); i++)
			branches.append(" WHEN ").append(i).append(" THEN ").append(number(p, steps.get(i), indexes));
		branches.append(" ELSE ").append(SparkSqlDecimalSql.nil()).append(" END");
		String value = "aggregate(sequence(0," + (steps.size() - 1)
				+ "),CAST(array() AS ARRAY<STRUCT<digits:STRING,scale:BIGINT,negative:BOOLEAN,invalid:BOOLEAN>>),(numeric_values,numeric_step) -> concat(numeric_values,array("
				+ branches + ")),numeric_done -> element_at(numeric_done,-1))";
		return "transform(`" + column(d.id(), names) + "`,numeric_item -> " + SparkSqlDecimalSql.let("numeric_result",
				value,
				"CASE WHEN numeric_result.digits IS NULL THEN CAST(raise_error('Null FEEL loop result') AS BINARY) ELSE "
						+ SparkSqlDecimalSql.payload("numeric_result") + " END")
				+ ")";
	}
	private static void instructions(RuntimeExpression e, List<RuntimeExpression> steps,
			Map<RuntimeExpression, Integer> indexes) {
		if (indexes.containsKey(e))
			return;
		if (e instanceof RuntimeBinaryExpression b) {
			instructions(b.left(), steps, indexes);
			instructions(b.right(), steps, indexes);
		}
		if (e instanceof RuntimeUnaryExpression u)
			instructions(u.operand(), steps, indexes);
		indexes.put(e, steps.size());
		steps.add(e);
	}
	private static String operand(RuntimeExpression e, Map<RuntimeExpression, Integer> indexes) {
		return "element_at(numeric_values," + (indexes.get(e) + 1) + ")";
	}
	private static String number(Plan p, RuntimeExpression e, Map<RuntimeExpression, Integer> indexes) {
		if (e instanceof RuntimePathExpression path)
			return "numeric_item.f" + field(p, path);
		if (e instanceof RuntimeConstant c)
			return c.kind() == RuntimeConstantKind.NULL
					? SparkSqlDecimalSql.nil()
					: SparkSqlDecimalSql.constant(new BigDecimal(c.value()));
		if (e instanceof RuntimeUnaryExpression u) {
			String v = operand(u.operand(), indexes);
			return u.operator() == RuntimeUnaryOperator.POSITIVE
					? v
					: SparkSqlDecimalSql.let("numeric_unary", v,
							"named_struct('digits',numeric_unary.digits,'scale',numeric_unary.scale,'negative',NOT numeric_unary.negative AND numeric_unary.digits<>'0','invalid',numeric_unary.invalid)");
		}
		var b = (RuntimeBinaryExpression) e;
		String a = operand(b.left(), indexes), r = operand(b.right(), indexes);
		return switch (b.operator()) {
			case ADD -> SparkSqlDecimalSql.add(a, r, false);
			case SUBTRACT -> SparkSqlDecimalSql.add(a, r, true);
			case MULTIPLY -> SparkSqlDecimalSql.multiply(a, r);
			case DIVIDE -> SparkSqlDecimalSql.divide(a, r);
			case POWER -> SparkSqlDecimalSql.power(a, r);
			default -> throw new IllegalArgumentException();
		};
	}
}

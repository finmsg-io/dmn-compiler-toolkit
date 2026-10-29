package io.finmsg.dmn.generator.sparksql;
import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.*;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/** Exact ordering components and original item payloads for native SQL sort. */
final class SparkSqlExactSortInput {
	private record Plan(RuntimeValueReference source, String member, boolean descending) {
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent())
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
		if (!name.equalsIgnoreCase("sort") || args.size() != 2
				|| !(args.getFirst() instanceof RuntimeValueReference source)
				|| source.type().kind() != RuntimeTypeKind.LIST || source.type().elementType() == null
				|| !decision.type().equals(source.type()) || !SparkSqlFeelValueCodec.isNativeType(source.type())
				|| model.inputs().stream()
						.noneMatch(i -> i.valueSlot() == source.sourceSlot() && i.type().equals(source.type()))
				|| !(args.get(1) instanceof RuntimeFunctionDefinition fn) || fn.external()
				|| fn.parameters().size() != 2
				|| fn.parameters().stream().anyMatch(p -> p.type().kind() != RuntimeTypeKind.ANY)
				|| !(fn.body().orElse(null) instanceof RuntimeBinaryExpression comparison)
				|| !Set.of(RuntimeBinaryOperator.LESS, RuntimeBinaryOperator.GREATER).contains(comparison.operator()))
			return null;
		var resultType = fn.type().kind() == RuntimeTypeKind.FUNCTION ? fn.type().returnType() : fn.type();
		if (resultType == null || !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.BOOLEAN).contains(resultType.kind())
				|| fn.parameters().get(0).localSlot() == fn.parameters().get(1).localSlot())
			return null;
		RuntimeExpression left = comparison.left(), right = comparison.right();
		String member = null;
		var element = source.type().elementType();
		if (left instanceof RuntimePathExpression a && right instanceof RuntimePathExpression b
				&& a.member().equals(b.member())) {
			if (element.kind() != RuntimeTypeKind.CONTEXT)
				return null;
			var field = element.fieldLayout().stream()
					.filter(f -> f.name().equals(a.member()) && (a.fieldIndex() < 0 || a.fieldIndex() == f.index())
							&& (b.fieldIndex() < 0 || b.fieldIndex() == f.index()))
					.findFirst().orElse(null);
			if (field == null)
				return null;
			member = a.member();
			element = field.type();
			left = a.source();
			right = b.source();
		}
		if (!Set.of(RuntimeTypeKind.NUMBER, RuntimeTypeKind.STRING).contains(element.kind())
				|| !(left instanceof RuntimeLocalReference a) || !(right instanceof RuntimeLocalReference b)
				|| a.lexicalDepth() != 0 || b.lexicalDepth() != 0 || a.localSlot() != fn.parameters().get(0).localSlot()
				|| b.localSlot() != fn.parameters().get(1).localSlot())
			return null;
		return new Plan(source, member, comparison.operator() == RuntimeBinaryOperator.GREATER);
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
		String name = "__dmn_sort_" + id;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(name.toLowerCase(Locale.ROOT)))
			name += "_";
		return name;
	}
	static DataType type() {
		return DataTypes.createArrayType(new StructType().add("key", SparkSqlTemporalValue.type())
				.add("payload", DataTypes.BinaryType).add("nullItem", DataTypes.BooleanType), false);
	}
	static Object project(RuntimeModel model, int id, Map<Integer, ?> inputs) {
		var d = model.decisions().stream().filter(x -> x.id() == id).findFirst().orElseThrow();
		var p = Objects.requireNonNull(plan(model, d));
		Object source = DmnRuntime.coerce(inputs.get(p.source().sourceSlot()), p.source().type());
		if (source == null)
			return null;
		var result = new ArrayList<Object>();
		for (Object item : (List<?>) source) {
			Object key = item;
			if (p.member() != null)
				key = item instanceof Map<?, ?> m
						? m.get(p.member())
						: item instanceof RuntimeContextValue c ? c.field(p.member()) : null;
			result.add(RowFactory.create(
					SparkSqlTemporalValue.row(key instanceof CharSequence text ? text.toString() : key, false),
					SparkSqlFeelValueCodec.encode(stringTransport(item)), item == null));
		}
		return result;
	}
	// Preserve the existing native STRING boundary's CharSequence conversion.
	private static Object stringTransport(Object value) {
		if (value instanceof CharSequence text)
			return text.toString();
		if (value instanceof List<?> list)
			return list.stream().map(SparkSqlExactSortInput::stringTransport).toList();
		if (value instanceof Map<?, ?> map) {
			var result = new LinkedHashMap<Object, Object>();
			map.forEach((k, v) -> result.put(k, stringTransport(v)));
			return result;
		}
		return value;
	}

	static String sql(RuntimeModel model, RuntimeDecision decision, Map<Integer, String> names) {
		var p = Objects.requireNonNull(plan(model, decision));
		String source = "`" + column(decision.id(), names) + "`";
		String comparator = "CASE WHEN sort_a.nullItem OR sort_b.nullItem THEN CAST(raise_error('Null FEEL sort item') AS INT) WHEN sort_a.key.kind='NULL' OR sort_b.key.kind='NULL' THEN 0 WHEN sort_a.key.kind='INVALID' OR sort_b.key.kind='INVALID' THEN CAST(raise_error('Invalid FEEL sort number') AS INT) WHEN sort_a.key.kind<>sort_b.key.kind OR sort_a.key.kind='OTHER' THEN 0 WHEN sort_a.key.equalityZone "
				+ (p.descending() ? ">" : "<") + " sort_b.key.equalityZone THEN -1 ELSE 1 END";
		return "CASE WHEN exists(" + source
				+ ",sort_item -> sort_item.nullItem) THEN CAST(raise_error('Null FEEL sort item') AS ARRAY<BINARY>) ELSE transform(array_sort("
				+ source + ",(sort_a,sort_b) -> " + comparator + "),sort_item -> sort_item.payload) END";
	}
}

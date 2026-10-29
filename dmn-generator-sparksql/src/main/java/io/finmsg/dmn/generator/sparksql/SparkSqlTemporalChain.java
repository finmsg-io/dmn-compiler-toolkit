package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.time.*;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/**
 * Bounded component evaluation of temporal expression graphs, without
 * timestamps.
 */
final class SparkSqlTemporalChain {
	private record Node(String op, List<Node> args, String value, int slot) {
		Node(String op, List<Node> args) {
			this(op, args, "", -1);
		}
	}
	private record Context(List<String> names, List<Object> entries) {
	}
	private record Plan(Object root, List<Node> steps, List<Integer> raw) {
	}
	private static final Set<RuntimeTypeKind> INPUTS = Set.of(RuntimeTypeKind.STRING, RuntimeTypeKind.NUMBER,
			RuntimeTypeKind.DURATION, RuntimeTypeKind.YEARS_MONTHS_DURATION, RuntimeTypeKind.DAYS_TIME_DURATION);
	private static final Set<String> PROPERTIES = Set.of("year", "month", "day", "weekday", "hour", "minute", "second",
			"time offset", "timezone", "years", "months", "days", "hours", "minutes", "seconds");
	private static final class Parser {
		final RuntimeModel model;
		final Set<Integer> active = new HashSet<>();
		final List<Integer> raw = new ArrayList<>();
		int budget = 512, temporal;
		Parser(RuntimeModel model) {
			this.model = model;
		}
		Object parse(RuntimeExpression e, Map<Integer, Object> locals, int depth) {
			if (e == null || --budget < 0 || depth > 32)
				throw new IllegalArgumentException();
			if (e instanceof RuntimeValueReference r) {
				var input = model.inputs().stream().filter(i -> i.valueSlot() == r.sourceSlot()).findFirst();
				if (input.isPresent()) {
					if (!INPUTS.contains(input.get().type().kind()))
						throw new IllegalArgumentException();
					if (!raw.contains(r.sourceSlot()))
						raw.add(r.sourceSlot());
					return new Node("input", List.of(), "", r.sourceSlot());
				}
				var d = model.decisions().stream().filter(x -> x.resultSlot() == r.sourceSlot()).findFirst()
						.orElseThrow();
				if (d.decisionTable().isPresent() || !active.add(d.id()))
					throw new IllegalArgumentException();
				var result = parse(d.expression().orElseThrow(), new HashMap<>(), depth + 1);
				active.remove(d.id());
				// Initially prove only declared scalar kinds whose constructor establishes the
				// type.
				return checked(result, d.type());
			}
			if (e instanceof RuntimeLocalReference r) {
				if (r.lexicalDepth() != 0 || !locals.containsKey(r.localSlot()))
					throw new IllegalArgumentException();
				return locals.get(r.localSlot());
			}
			if (e instanceof RuntimeConstant c) {
				if (c.kind() == RuntimeConstantKind.NULL)
					return new Node("null", List.of());
				if (c.kind() == RuntimeConstantKind.STRING && (c.value().contains("@")
						|| !((org.apache.spark.sql.Row) SparkSqlTemporalZoneFacts.row(c.value())).isNullAt(0)))
					throw new IllegalArgumentException();
				if (c.kind() == RuntimeConstantKind.STRING || c.kind() == RuntimeConstantKind.NUMBER)
					return new Node(c.kind() == RuntimeConstantKind.STRING ? "string" : "number", List.of(), c.value(),
							-1);
				throw new IllegalArgumentException();
			}
			if (e instanceof RuntimeContextExpression c) {
				if (c.entries().size() > 16)
					throw new IllegalArgumentException();
				var frame = new HashMap<Integer, Object>();
				var names = new ArrayList<String>();
				var entries = new ArrayList<Object>();
				for (var entry : c.entries()) {
					if (names.contains(entry.name()))
						throw new IllegalArgumentException();
					var value = parse(entry.expression(), frame, depth + 1);
					if (!(value instanceof Node))
						throw new IllegalArgumentException();
					names.add(entry.name());
					entries.add(value);
					frame.put(entry.localSlot(), value);
				}
				return new Context(List.copyOf(names), List.copyOf(entries));
			}
			if (e instanceof RuntimePathExpression p) {
				Object source = parse(p.source(), locals, depth + 1);
				if (source instanceof Context c) {
					int i = c.names().indexOf(p.member());
					if (i < 0 || p.fieldIndex() >= 0 && p.fieldIndex() != i)
						throw new IllegalArgumentException();
					var args = new ArrayList<Node>();
					for (var x : c.entries())
						args.add(node(x));
					args.add(node(c.entries().get(i)));
					return new Node("sequence", List.copyOf(args));
				}
				if (!PROPERTIES.contains(p.member()))
					throw new IllegalArgumentException();
				temporal++;
				return new Node("property", List.of(node(source)), p.member(), -1);
			}
			if (e instanceof RuntimeBinaryExpression b
					&& Set.of(RuntimeBinaryOperator.ADD, RuntimeBinaryOperator.SUBTRACT).contains(b.operator())) {
				if (!Set.of(RuntimeTypeKind.DURATION, RuntimeTypeKind.YEARS_MONTHS_DURATION,
						RuntimeTypeKind.DAYS_TIME_DURATION).contains(b.type().kind()))
					throw new IllegalArgumentException();
				temporal++;
				return new Node(b.operator() == RuntimeBinaryOperator.ADD ? "add" : "subtract",
						List.of(node(parse(b.left(), locals, depth + 1)), node(parse(b.right(), locals, depth + 1))));
			}
			String function;
			List<RuntimeExpression> expressions;
			if (e instanceof RuntimeFunctionCall f) {
				function = f.function();
				expressions = f.arguments();
			} else if (e instanceof RuntimeInvocationExpression f && f.function().isPresent()) {
				function = f.function().get();
				expressions = f.namedArguments().isEmpty()
						? f.positionalArguments()
						: SparkSqlExpressionEmitter.reorderNamedArguments(function, f.namedArguments());
			} else
				throw new IllegalArgumentException();
			function = function.toLowerCase(Locale.ROOT).replace('_', ' ');
			int n = expressions.size();
			if (!(function.equals("date") && (n == 1 || n == 3)
					|| function.equals("time") && (n == 1 || n == 3 || n == 4)
					|| function.equals("date and time") && (n == 1 || n == 2) || function.equals("duration") && n == 1
					|| function.equals("years and months duration") && (n == 1 || n == 2)))
				throw new IllegalArgumentException();
			var args = new ArrayList<Node>();
			for (var x : expressions)
				args.add(node(parse(x, locals, depth + 1)));
			temporal++;
			return new Node(function, List.copyOf(args));
		}
		Object checked(Object value, RuntimeType type) {
			if (value instanceof Context c) {
				if (type.kind() != RuntimeTypeKind.ANY && type.kind() != RuntimeTypeKind.CONTEXT)
					throw new IllegalArgumentException();
				if (!type.fieldLayout().isEmpty()) {
					if (type.fieldLayout().size() != c.names().size())
						throw new IllegalArgumentException();
					for (var f : type.fieldLayout()) {
						int index = c.names().indexOf(f.name());
						if (index < 0 || f.type().kind() != RuntimeTypeKind.DATE
								|| !node(c.entries().get(index)).op().equals("date"))
							throw new IllegalArgumentException();
					}
				}
				return c;
			}
			return type.kind() == RuntimeTypeKind.ANY
					? value
					: new Node("coerce", List.of(node(value)), type.kind().name(), -1);
		}
	}
	private static Node node(Object value) {
		if (value instanceof Node n)
			return n;
		throw new IllegalArgumentException();
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision d) {
		if (d.decisionTable().isPresent())
			return null;
		try {
			var parser = new Parser(model);
			parser.active.add(d.id());
			var root = parser.checked(parser.parse(d.expression().orElseThrow(), new HashMap<>(), 0), d.type());
			if (parser.temporal == 0 || parser.raw.isEmpty())
				return null;
			if (root instanceof Context c) {
				for (var entry : c.entries())
					if (!safeStringResult(node(entry), model))
						return null;
			} else if (!safeStringResult(node(root), model))
				return null;
			var steps = new ArrayList<Node>();
			var seen = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
			if (root instanceof Context c)
				for (var x : c.entries())
					collect(node(x), steps, seen);
			else
				collect(node(root), steps, seen);
			return new Plan(root, List.copyOf(steps), List.copyOf(parser.raw));
		} catch (RuntimeException unsupported) {
			return null;
		}
	}
	private static boolean safeStringResult(Node n, RuntimeModel model) {
		if (n.op().equals("input"))
			return model.inputs().stream().filter(i -> i.valueSlot() == n.slot())
					.noneMatch(i -> i.type().kind() == RuntimeTypeKind.STRING);
		if (n.op().equals("sequence"))
			return safeStringResult(n.args().getLast(), model);
		if (n.op().equals("coerce"))
			return !n.value().equals("STRING") || safeStringResult(n.args().getFirst(), model);
		if (n.op().equals("string")) {
			for (int i = 0; i < n.value().length(); i++) {
				char c = n.value().charAt(i);
				if (Character.isHighSurrogate(c)) {
					if (++i >= n.value().length() || !Character.isLowSurrogate(n.value().charAt(i)))
						return false;
				} else if (Character.isLowSurrogate(c))
					return false;
			}
		}
		return true;
	}

	private static void collect(Node n, List<Node> steps, Set<Node> seen) {
		if (seen.add(n)) {
			for (var a : n.args())
				collect(a, steps, seen);
			steps.add(n);
		}
	}
	static boolean supports(RuntimeModel m, RuntimeDecision d) {
		return plan(m, d) != null;
	}
	static Set<Integer> ids(RuntimeModel m) {
		var r = new LinkedHashSet<Integer>();
		for (var d : m.decisions())
			if (supports(m, d))
				r.add(d.id());
		return r;
	}
	static long[] slots(RuntimeModel m, RuntimeDecision d) {
		return Objects.requireNonNull(plan(m, d)).raw().stream().mapToLong(Integer::longValue).toArray();
	}
	static Set<Integer> dependencies(RuntimeModel m, RuntimeDecision d) {
		var p = plan(m, d);
		if (p == null)
			return null;
		var r = new LinkedHashSet<Integer>();
		for (var i : m.inputs())
			if (p.raw().contains(i.valueSlot()))
				r.add(i.id());
		return r;
	}
	static RuntimeModel lowerDependencies(RuntimeModel m) {
		var ds = m.decisions().stream().map(d -> {
			var deps = dependencies(m, d);
			return deps == null
					? d
					: new RuntimeDecision(d.id(), d.resultSlot(), d.type(), List.copyOf(deps), d.expression(),
							d.decisionTable(), d.localSlotCount());
		}).toList();
		return new RuntimeModel(m.inputs(), ds, m.businessKnowledgeModels(), m.evaluationOrder(), m.valueSlotCount());
	}
	static String column(int id, Map<Integer, String> names) {
		String name = "__dmn_temporal_chain_" + id;
		while (names.containsValue(name))
			name += "_";
		return name;
	}
	static ArrayType type() {
		return DataTypes.createArrayType(SparkSqlTemporalFunctions.type(), false);
	}
	static Object project(RuntimeModel model, long[] slots, Map<Integer, ?> values) {
		var result = new ArrayList<Object>();
		for (long s : slots) {
			int slot = Math.toIntExact(s);
			var input = model.inputs().stream().filter(i -> i.valueSlot() == slot).findFirst().orElseThrow();
			Object v = io.finmsg.dmn.runtime.DmnRuntime.coerce(values.get(slot), input.type());
			String kind = "NULL", text = null;
			int py = 0, pm = 0, pd = 0, nano = 0;
			long ds = 0;
			Object number = SparkSqlDecimalSql.row(null), z = SparkSqlTemporalZoneFacts.row(null), zs = z;
			if (v instanceof CharSequence chars) {
				kind = "STRING";
				text = chars.toString();
				z = SparkSqlTemporalZoneFacts.row(text);
				int at = text.indexOf('@');
				if (at >= 0)
					zs = SparkSqlTemporalZoneFacts.row(text.substring(at + 1));
			} else if (v instanceof Number) {
				kind = "NUMBER";
				number = SparkSqlDecimalSql.row(v);
			} else if (v instanceof Duration dur) {
				kind = "DURATION";
				text = dur.toString();
				ds = dur.getSeconds();
				nano = dur.getNano();
			} else if (v instanceof Period period) {
				kind = "PERIOD";
				text = period.toString();
				py = period.getYears();
				pm = period.getMonths();
				pd = period.getDays();
			}
			result.add(RowFactory.create(kind, text, null, 0, nano, null, py, pm, pd, ds, number, z, zs));
		}
		return result;
	}
	private static String coerce(String cell, String kind) {
		String match = switch (kind) {
			case "NUMBER", "STRING", "DATE", "NULL" -> cell + ".k='" + kind + "'";
			case "TIME" -> cell + ".k LIKE 'TIME_%'";
			case "DATE_TIME" -> cell + ".k LIKE 'DT_%'";
			case "DURATION" -> cell + ".k IN ('PERIOD','DURATION')";
			case "YEARS_MONTHS_DURATION" -> cell + ".k='PERIOD'";
			case "DAYS_TIME_DURATION" -> cell + ".k='DURATION'";
			default -> "false";
		};
		return "CASE WHEN " + match + " THEN " + cell + " ELSE " + SparkSqlTemporalFunctions.nil() + " END";
	}
	private static String expression(Node n, Plan p, String input) {
		var a = n.args();
		String x = "ta", y = "tb", z = "tc", w = "td";
		String body;
		body = switch (n.op()) {
			case "input" -> "element_at(`" + input + "`," + (p.raw().indexOf(n.slot()) + 1) + ")";
			case "null" -> SparkSqlTemporalFunctions.nil();
			case "string" -> {
				if (n.value().contains("@")
						|| !((org.apache.spark.sql.Row) SparkSqlTemporalZoneFacts.row(n.value())).isNullAt(0))
					throw new IllegalArgumentException("Named zone constants require reference metadata");
				yield SparkSqlTemporalFunctions.pack("k", "'STRING'", "t", SparkSqlTemporalFunctions.q(n.value()));
			}
			case "number" -> {
				var v = new java.math.BigDecimal(n.value());
				yield SparkSqlTemporalFunctions.pack("k", "'NUMBER'", "n",
						SparkSqlDecimalSql.number(SparkSqlTemporalFunctions.q(v.unscaledValue().abs().toString()),
								v.scale() + "L", Boolean.toString(v.signum() < 0)));
			}
			case "sequence" -> ref(a.getLast(), p);
			case "coerce" -> coerce(x, n.value());
			case "property" -> SparkSqlTemporalFunctions.property(x, n.value());
			case "date" ->
				a.size() == 1 ? SparkSqlTemporalFunctions.dateOf(x) : SparkSqlTemporalFunctions.dateNumeric(x, y, z);
			case "time" -> a.size() == 1
					? SparkSqlTemporalFunctions.timeOf(x)
					: SparkSqlTemporalFunctions.timeNumeric(x, y, z, a.size() == 4 ? w : null);
			case "date and time" -> a.size() == 1
					? "CASE WHEN " + x + ".k='STRING' THEN " + SparkSqlTemporalFunctions.dateTime(x) + " ELSE "
							+ SparkSqlTemporalFunctions.nil() + " END"
					: SparkSqlTemporalFunctions.let("temporal_date_arg", SparkSqlTemporalFunctions.dateOf(x),
							SparkSqlTemporalFunctions.let("temporal_time_arg",
									"CASE WHEN " + y + ".k='STRING' THEN " + SparkSqlTemporalFunctions.time(y)
											+ " WHEN " + y + ".k LIKE 'TIME_%' THEN " + y + " ELSE "
											+ SparkSqlTemporalFunctions.nil() + " END",
									SparkSqlTemporalFunctions.combine("temporal_date_arg", "temporal_time_arg")));
			case "duration" -> SparkSqlTemporalFunctions.durationParse(x);
			case "years and months duration" -> a.size() == 1
					? SparkSqlTemporalFunctions.durationParse(x)
					: SparkSqlTemporalFunctions.let("temporal_start", SparkSqlTemporalFunctions.localDateForPeriod(x),
							SparkSqlTemporalFunctions.let("temporal_end",
									SparkSqlTemporalFunctions.localDateForPeriod(y),
									SparkSqlTemporalFunctions.monthsBetween("temporal_start", "temporal_end")));
			case "add", "subtract" -> SparkSqlTemporalFunctions.binary(x, y, n.op().equals("subtract"));
			default -> throw new IllegalArgumentException();
		};
		if (!n.op().equals("sequence"))
			for (int i = a.size() - 1; i >= 0; i--)
				body = SparkSqlTemporalFunctions.let(new String[]{x, y, z, w}[i], ref(a.get(i), p), body);
		return body;
	}
	private static String ref(Node n, Plan p) {
		for (int i = 0; i < p.steps().size(); i++)
			if (p.steps().get(i) == n)
				return "element_at(temporal_values," + (i + 1) + ")";
		throw new IllegalArgumentException();
	}
	static String sql(RuntimeModel model, RuntimeDecision d, Map<Integer, String> names) {
		var p = Objects.requireNonNull(plan(model, d));
		String input = column(d.id(), names);
		var cases = new ArrayList<String>();
		for (int i = 0; i < p.steps().size(); i++)
			cases.add("WHEN " + i + " THEN " + expression(p.steps().get(i), p, input));
		String folded = "aggregate(sequence(0," + (p.steps().size() - 1) + "),CAST(array() AS ARRAY<"
				+ SparkSqlTemporalFunctions.type().sql()
				+ ">),(temporal_values,temporal_step) -> concat(temporal_values,array(CASE temporal_step "
				+ String.join(" ", cases) + " ELSE " + SparkSqlTemporalFunctions.nil() + " END)))";
		String payload;
		if (p.root() instanceof Context c) {
			var parts = new ArrayList<String>();
			parts.add("unhex('444d4e0107')");
			parts.add("unhex(lpad(hex(" + c.names().size() + "),8,'0'))");
			for (int i = 0; i < c.names().size(); i++) {
				parts.add(SparkSqlTemporalFunctions.key(c.names().get(i)));
				parts.add(SparkSqlTemporalFunctions.payloadBody(ref(node(c.entries().get(i)), p)));
			}
			payload = "concat(" + String.join(",", parts) + ")";
		} else {
			String r = ref(node(p.root()), p);
			payload = "CASE WHEN " + r + ".k='NULL' THEN CAST(NULL AS BINARY) ELSE concat(unhex('444d4e01'),"
					+ SparkSqlTemporalFunctions.payloadBody(r) + ") END";
		}
		return SparkSqlTemporalFunctions.let("temporal_values", folded, payload);
	}
}

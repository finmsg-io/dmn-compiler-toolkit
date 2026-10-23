package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/**
 * Bounded scalar context/BKM expansion with exact numeric operations and
 * guarded evaluation.
 */
final class SparkSqlScalarGraph {
	private enum Kind {
		NUMBER, STRING, BOOLEAN, NULL
	}
	private record Raw(int slot, List<String> path, Kind kind) {
	}
	private record Input(int slot, List<String> path, RuntimeType type) {
	}
	private record Column(int slot, int id) {
	}
	private record Node(String op, Kind kind, List<Node> args, Object value) {
	}
	private record Context(List<Node> effects, List<String> names, List<Node> values) {
	}
	private record Plan(Node root, List<Raw> raw, List<Integer> dependencies) {
	}
	private static final class Unsupported extends RuntimeException {
	}
	private static Kind kind(RuntimeType t) {
		try {
			return Kind.valueOf(t.kind().name());
		} catch (IllegalArgumentException invalid) {
			throw new Unsupported();
		}
	}
	private static Node node(String op, Kind kind, Node... args) {
		return new Node(op, kind, List.of(args), null);
	}
	private static Node constant(Kind kind, String value) {
		return new Node("constant", kind, List.of(), value);
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent() || decision.expression().isEmpty()
				|| decision.type().kind() != RuntimeTypeKind.BOOLEAN)
			return null;
		try {
			var parser = new Parser(model);
			parser.active.add(decision.resultSlot());
			Node root = parser.scalar(parser.value(decision.expression().get(), new HashMap<>(), 0));
			if (parser.contexts == 0 || parser.calls == 0
					|| root.kind() != kind(decision.type()) && root.kind() != Kind.NULL)
				return null;
			var raw = new LinkedHashSet<Raw>();
			var deps = new LinkedHashSet<Integer>();
			collect(root, raw, deps);
			for (var leaf : raw)
				deps.add(model.inputs().stream().filter(i -> i.valueSlot() == leaf.slot()).findFirst()
						.orElseThrow(Unsupported::new).id());
			steps(root, "TRUE", new ArrayList<>(), new HashMap<>());
			return new Plan(root, List.copyOf(raw), List.copyOf(deps));
		} catch (Unsupported unsupported) {
			return null;
		}
	}
	private static void collect(Node n, Set<Raw> raw, Set<Integer> deps) {
		if (n.value() instanceof Raw r)
			raw.add(r);
		if (n.value() instanceof Column c)
			deps.add(c.id());
		for (var a : n.args())
			collect(a, raw, deps);
	}
	private static final class Parser {
		final RuntimeModel model;
		final Set<Integer> active = new HashSet<>();
		int budget = 1024, contexts, calls;
		Parser(RuntimeModel model) {
			this.model = model;
		}
		Node scalar(Object value) {
			if (value instanceof Node n)
				return n;
			throw new Unsupported();
		}
		Node checked(Object value, RuntimeType type) {
			var n = scalar(value);
			if (type.kind() != RuntimeTypeKind.ANY && n.kind() != Kind.NULL && n.kind() != kind(type))
				throw new Unsupported();
			return n;
		}
		Object input(int slot, List<String> path, RuntimeType type) {
			if (type.kind() == RuntimeTypeKind.CONTEXT)
				return new Input(slot, path, type);
			return new Node("raw", kind(type), List.of(), new Raw(slot, List.copyOf(path), kind(type)));
		}
		Object value(RuntimeExpression e, Map<Integer, Object> frame, int depth) {
			if (--budget < 0 || depth > 48)
				throw new Unsupported();
			if (e instanceof RuntimeConstant c) {
				Kind k;
				try {
					k = Kind.valueOf(c.kind().name());
				} catch (IllegalArgumentException invalid) {
					throw new Unsupported();
				}
				if (c.value().length() > 2048)
					throw new Unsupported();
				if (k == Kind.NUMBER) {
					try {
						var n = new BigDecimal(c.value());
						if (n.precision() > 128 || Math.abs((long) n.scale()) > 1024)
							throw new Unsupported();
					} catch (NumberFormatException invalid) {
						throw new Unsupported();
					}
				}
				if (k == Kind.BOOLEAN && !Set.of("true", "false").contains(c.value()))
					throw new Unsupported();
				return constant(k, c.value());
			}
			if (e instanceof RuntimeLocalReference r) {
				if (r.lexicalDepth() != 0 || !frame.containsKey(r.localSlot()))
					throw new Unsupported();
				return frame.get(r.localSlot());
			}
			if (e instanceof RuntimeValueReference r) {
				for (var i : model.inputs())
					if (i.valueSlot() == r.sourceSlot())
						return input(i.valueSlot(), List.of(), i.type());
				for (var d : model.decisions())
					if (d.resultSlot() == r.sourceSlot()) {
						if (active.contains(d.resultSlot()))
							throw new Unsupported();
						if (Set.of(RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN).contains(d.type().kind())) {
							if (d.type().kind() == RuntimeTypeKind.STRING
									&& !constantStringDecision(d, new HashSet<>(), 0))
								throw new Unsupported();
							return new Node("column", kind(d.type()), List.of(), new Column(d.resultSlot(), d.id()));
						}
						if (d.type().kind() != RuntimeTypeKind.NUMBER || d.expression().isEmpty()
								|| d.decisionTable().isPresent() || !active.add(d.resultSlot()))
							throw new Unsupported();
						try {
							return checked(value(d.expression().get(), new HashMap<>(), depth + 1), d.type());
						} finally {
							active.remove(d.resultSlot());
						}
					}
				throw new Unsupported();
			}
			if (e instanceof RuntimePathExpression p) {
				Object source = value(p.source(), frame, depth + 1);
				if (source instanceof Input i) {
					var fields = i.type().fieldLayout();
					RuntimeField field = null;
					if (p.fieldIndex() >= 0) {
						if (p.fieldIndex() >= fields.size())
							throw new Unsupported();
						field = fields.get(p.fieldIndex());
					} else
						for (var f : fields)
							if (f.name().equals(p.member())) {
								field = f;
								break;
							}
					if (field == null || field.name().isEmpty()
							|| field.name().codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF))
						throw new Unsupported();
					var path = new ArrayList<>(i.path());
					path.add(field.name());
					return input(i.slot(), List.copyOf(path), field.type());
				}
				if (source instanceof Context c) {
					int index = p.fieldIndex() >= 0 ? p.fieldIndex() : c.names().lastIndexOf(p.member());
					if (index < 0 || index >= c.values().size())
						throw new Unsupported();
					var result = c.values().get(index);
					var effects = new ArrayList<>(c.effects());
					effects.add(result);
					return new Node("sequence", result.kind(), List.copyOf(effects), null);
				}
				throw new Unsupported();
			}
			if (e instanceof RuntimeContextExpression context) {
				contexts++;
				var names = new ArrayList<String>();
				var values = new ArrayList<Node>();
				for (var entry : context.entries()) {
					if (entry.name().isEmpty() || names.contains(entry.name()))
						throw new Unsupported();
					Node value = scalar(value(entry.expression(), frame, depth + 1));
					names.add(entry.name());
					values.add(value);
					if (entry.localSlot() >= 0)
						frame.put(entry.localSlot(), value);
				}
				return new Context(List.copyOf(values), List.copyOf(names), List.copyOf(values));
			}
			if (e instanceof RuntimeInvocationExpression call) {
				if (call.function().isPresent() || !(call.target().orElse(null) instanceof RuntimeValueReference ref))
					throw new Unsupported();
				var bkm = model.businessKnowledgeModels().stream().filter(b -> b.resultSlot() == ref.sourceSlot())
						.findFirst().orElseThrow(Unsupported::new);
				if (bkm.functionKind() != RuntimeFunctionKind.FEEL || bkm.function().isEmpty()
						|| !active.add(bkm.resultSlot()))
					throw new Unsupported();
				try {
					var fn = bkm.function().get();
					if (fn.external() || fn.body().isEmpty())
						throw new Unsupported();
					var bindings = new HashMap<Integer, Object>();
					var effects = new ArrayList<Node>();
					if (!call.namedArguments().isEmpty()) {
						if (!call.positionalArguments().isEmpty()
								|| call.namedArguments().size() != fn.parameters().size())
							throw new Unsupported();
						var names = new HashSet<String>();
						for (var a : call.namedArguments()) {
							if (!names.add(a.name()))
								throw new Unsupported();
							var param = fn.parameters().stream().filter(p -> p.name().equals(a.name())).findFirst()
									.orElseThrow(Unsupported::new);
							var n = checked(value(a.expression(), frame, depth + 1), param.type());
							bindings.put(param.localSlot(), n);
							effects.add(n);
						}
					} else {
						if (call.positionalArguments().size() != fn.parameters().size())
							throw new Unsupported();
						for (int j = 0; j < fn.parameters().size(); j++) {
							var param = fn.parameters().get(j);
							var n = checked(value(call.positionalArguments().get(j), frame, depth + 1), param.type());
							bindings.put(param.localSlot(), n);
							effects.add(n);
						}
					}
					Node body = scalar(value(fn.body().get(), bindings, depth + 1));
					RuntimeType output = fn.type().kind() == RuntimeTypeKind.FUNCTION
							? fn.type().returnType()
							: fn.type();
					if (output != null)
						body = checked(body, output);
					effects.add(body);
					calls++;
					return new Node("sequence", body.kind(), List.copyOf(effects), null);
				} finally {
					active.remove(bkm.resultSlot());
				}
			}
			if (e instanceof RuntimeConditionalExpression c) {
				var condition = scalar(value(c.condition(), frame, depth + 1));
				if (condition.kind() != Kind.BOOLEAN && condition.kind() != Kind.NULL)
					throw new Unsupported();
				var yes = scalar(value(c.thenExpression(), new HashMap<>(frame), depth + 1));
				var no = scalar(value(c.elseExpression(), new HashMap<>(frame), depth + 1));
				Kind k = yes.kind() == Kind.NULL ? no.kind() : yes.kind();
				if (no.kind() != k && no.kind() != Kind.NULL)
					throw new Unsupported();
				return node("if", k, condition, yes, no);
			}
			if (e instanceof RuntimeUnaryExpression u) {
				var a = scalar(value(u.operand(), frame, depth + 1));
				if (u.operator() == RuntimeUnaryOperator.NOT && (a.kind() == Kind.BOOLEAN || a.kind() == Kind.NULL))
					return node("not", Kind.BOOLEAN, a);
				if (a.kind() != Kind.NUMBER && a.kind() != Kind.NULL)
					throw new Unsupported();
				if (u.operator() == RuntimeUnaryOperator.POSITIVE)
					return a;
				if (u.operator() == RuntimeUnaryOperator.NEGATE)
					return node("negate", Kind.NUMBER, a);
				throw new Unsupported();
			}
			if (e instanceof RuntimeBinaryExpression b) {
				return binary(b.operator(), scalar(value(b.left(), frame, depth + 1)),
						scalar(value(b.right(), frame, depth + 1)));
			}
			if (e instanceof RuntimeInExpression in) {
				var a = scalar(value(in.value(), frame, depth + 1));
				Node result = constant(Kind.BOOLEAN, "false");
				// RuntimeInExpression evaluates its tests, not the decision-table wildcard
				// flag.
				for (var test : in.tests().tests()) {
					if (!(test instanceof RuntimeExpressionUnaryTest t))
						throw new Unsupported();
					result = binary(RuntimeBinaryOperator.OR, result,
							binary(RuntimeBinaryOperator.EQUAL, a, scalar(value(t.expression(), frame, depth + 1))));
				}
				result = in.tests().negated() ? node("not", Kind.BOOLEAN, result) : result;
				return node("in", Kind.BOOLEAN, a, result);
			}
			throw new Unsupported();
		}
		boolean constantStringDecision(RuntimeDecision d, Set<Integer> visited, int depth) {
			if (depth > 24 || !visited.add(d.resultSlot()))
				return false;
			try {
				if (d.expression().isPresent())
					return constantString(d.expression().get(), visited, depth + 1);
				if (d.decisionTable().isEmpty())
					return false;
				var t = d.decisionTable().get();
				return t.outputs().size() == 1
						&& Set.of(RuntimeHitPolicy.UNIQUE, RuntimeHitPolicy.FIRST, RuntimeHitPolicy.ANY,
								RuntimeHitPolicy.PRIORITY).contains(t.hitPolicy())
						&& t.rules().stream().allMatch(
								r -> r.outputEntries().stream().allMatch(e -> constantString(e, visited, depth + 1)))
						&& t.outputs().getFirst().defaultValue().map(e -> constantString(e, visited, depth + 1))
								.orElse(true);
			} finally {
				visited.remove(d.resultSlot());
			}
		}
		boolean constantString(RuntimeExpression e, Set<Integer> visited, int depth) {
			if (depth > 24)
				return false;
			if (e instanceof RuntimeConstant c)
				return c.kind() == RuntimeConstantKind.NULL || c.kind() == RuntimeConstantKind.STRING
						&& c.value().codePoints().noneMatch(x -> x >= 0xD800 && x <= 0xDFFF);
			if (e instanceof RuntimeConditionalExpression c)
				return constantString(c.thenExpression(), visited, depth + 1)
						&& constantString(c.elseExpression(), visited, depth + 1);
			if (e instanceof RuntimeValueReference r)
				return model.decisions().stream().filter(d -> d.resultSlot() == r.sourceSlot()).findFirst()
						.map(d -> constantStringDecision(d, visited, depth + 1)).orElse(false);
			if (e instanceof RuntimeInvocationExpression call && call.function().isEmpty()
					&& call.target().orElse(null) instanceof RuntimeValueReference r) {
				var b = model.businessKnowledgeModels().stream().filter(x -> x.resultSlot() == r.sourceSlot())
						.findFirst().orElse(null);
				if (b == null || b.functionKind() != RuntimeFunctionKind.FEEL || b.function().isEmpty()
						|| b.function().get().external() || !visited.add(b.resultSlot()))
					return false;
				try {
					return b.function().get().body().map(body -> constantString(body, visited, depth + 1))
							.orElse(false);
				} finally {
					visited.remove(b.resultSlot());
				}
			}
			return false;
		}

		Node binary(RuntimeBinaryOperator op, Node a, Node b) {
			if (Set.of(RuntimeBinaryOperator.AND, RuntimeBinaryOperator.OR).contains(op)) {
				if (a.kind() != Kind.BOOLEAN && a.kind() != Kind.NULL
						|| b.kind() != Kind.BOOLEAN && b.kind() != Kind.NULL)
					throw new Unsupported();
				return node(op == RuntimeBinaryOperator.AND ? "and" : "or", Kind.BOOLEAN, a, b);
			}
			if (Set.of(RuntimeBinaryOperator.EQUAL, RuntimeBinaryOperator.NOT_EQUAL, RuntimeBinaryOperator.LESS,
					RuntimeBinaryOperator.LESS_EQUAL, RuntimeBinaryOperator.GREATER,
					RuntimeBinaryOperator.GREATER_EQUAL).contains(op)) {
				if (a.kind() != b.kind() && a.kind() != Kind.NULL && b.kind() != Kind.NULL)
					throw new Unsupported();
				if (!Set.of(RuntimeBinaryOperator.EQUAL, RuntimeBinaryOperator.NOT_EQUAL).contains(op)
						&& a.kind() != Kind.NUMBER && a.kind() != Kind.NULL)
					throw new Unsupported();
				return node(op.name(), Kind.BOOLEAN, a, b);
			}
			if (a.kind() != Kind.NUMBER && a.kind() != Kind.NULL || b.kind() != Kind.NUMBER && b.kind() != Kind.NULL)
				throw new Unsupported();
			if (!Set.of(RuntimeBinaryOperator.ADD, RuntimeBinaryOperator.SUBTRACT, RuntimeBinaryOperator.MULTIPLY,
					RuntimeBinaryOperator.DIVIDE, RuntimeBinaryOperator.POWER).contains(op))
				throw new Unsupported();
			return node(op.name(), Kind.NUMBER, a, b);
		}
	}
	static boolean supports(RuntimeModel model, RuntimeDecision d) {
		return plan(model, d) != null;
	}
	static List<Integer> dependencies(RuntimeModel model, RuntimeDecision d) {
		var p = plan(model, d);
		return p == null ? null : p.dependencies();
	}
	static RuntimeModel lowerDependencies(RuntimeModel model) {
		var decisions = new ArrayList<RuntimeDecision>();
		for (var d : model.decisions()) {
			var deps = dependencies(model, d);
			decisions.add(deps == null
					? d
					: new RuntimeDecision(d.id(), d.resultSlot(), d.type(), deps, d.expression(), d.decisionTable(),
							d.localSlotCount()));
		}
		return new RuntimeModel(model.inputs(), decisions, model.businessKnowledgeModels(), model.evaluationOrder(),
				model.valueSlotCount());
	}
	static Set<Integer> ids(RuntimeModel model) {
		var ids = new LinkedHashSet<Integer>();
		for (var d : model.decisions())
			if (supports(model, d))
				ids.add(d.id());
		return ids;
	}
	static String column(int id, Map<Integer, String> names) {
		String n = "__dmn_scalar_graph_" + id;
		var occupied = new HashSet<String>();
		names.values().forEach(v -> occupied.add(v.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(n.toLowerCase(Locale.ROOT)))
			n += "_";
		return n;
	}
	static ArrayType type() {
		return DataTypes.createArrayType(new StructType().add("n", SparkSqlDecimalSql.type())
				.add("s", DataTypes.StringType).add("b", DataTypes.BooleanType), false);
	}
	static long[] slots(RuntimeModel m, RuntimeDecision d) {
		return Objects.requireNonNull(plan(m, d)).raw().stream().mapToLong(Raw::slot).toArray();
	}
	static String[] paths(RuntimeModel m, RuntimeDecision d) {
		return Objects.requireNonNull(plan(m, d)).raw().stream().map(r -> String.join(".",
				r.path().stream().map(
						s -> Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)))
						.toList()))
				.toArray(String[]::new);
	}
	static String[] kinds(RuntimeModel m, RuntimeDecision d) {
		return Objects.requireNonNull(plan(m, d)).raw().stream().map(r -> r.kind().name()).toArray(String[]::new);
	}
	static Object project(RuntimeModel model, long[] slots, String[] paths, String[] kinds, Map<Integer, ?> values) {
		var cache = new HashMap<Integer, Object>();
		var rows = new ArrayList<Object>();
		for (int i = 0; i < slots.length; i++) {
			int slot = Math.toIntExact(slots[i]);
			if (!cache.containsKey(slot)) {
				var input = model.inputs().stream().filter(x -> x.valueSlot() == slot).findFirst().orElseThrow();
				cache.put(slot, DmnRuntime.coerce(values.get(slot), input.type()));
			}
			Object v = cache.get(slot);
			if (!paths[i].isEmpty())
				for (var part : paths[i].split("\\.")) {
					var name = new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
					v = v instanceof Map<?, ?> map ? map.get(name) : null;
				}
			var k = Kind.valueOf(kinds[i]);
			rows.add(RowFactory.create(SparkSqlDecimalSql.row(k == Kind.NUMBER ? v : null),
					k == Kind.STRING && v != null ? stringKey(v.toString()) : null, k == Kind.BOOLEAN ? v : null));
		}
		return rows;
	}
	private static String stringKey(String s) {
		var out = new StringBuilder(s.length() * 4);
		String hex = "0123456789ABCDEF";
		for (int i = 0; i < s.length(); i++) {
			int c = s.charAt(i);
			for (int shift = 12; shift >= 0; shift -= 4)
				out.append(hex.charAt((c >>> shift) & 15));
		}
		return out.toString();
	}

	private static String quote(String s) {
		return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
	}
	private static String wrap(String n, String s, String b) {
		return "named_struct('n'," + n + ",'s'," + s + ",'b'," + b + ")";
	}
	private static String nil() {
		return wrap(SparkSqlDecimalSql.nil(), "CAST(NULL AS STRING)", "CAST(NULL AS BOOLEAN)");
	}
	private static String num(String n) {
		return wrap(n, "CAST(NULL AS STRING)", "CAST(NULL AS BOOLEAN)");
	}
	private static String bool(String b) {
		return wrap(SparkSqlDecimalSql.nil(), "CAST(NULL AS STRING)", b);
	}
	private static String string(String s) {
		return wrap(SparkSqlDecimalSql.nil(), s, "CAST(NULL AS BOOLEAN)");
	}
	private record Step(Node node, List<Integer> args, String guard) {
	}
	private record Key(Node node, String guard) {
	}
	private static int steps(Node n, String guard, List<Step> steps, Map<Key, Integer> memo) {
		if (guard.length() > 8192)
			throw new Unsupported();
		var key = new Key(n, guard);
		if (memo.containsKey(key))
			return memo.get(key);
		var args = new ArrayList<Integer>();
		if (n.op().equals("in")) {
			int candidate = steps(n.args().getFirst(), guard, steps, memo);
			args.add(candidate);
			args.add(steps(n.args().get(1),
					"(" + guard + ") AND NOT (" + isNull(ref(candidate), n.args().getFirst().kind()) + ")", steps,
					memo));
		} else if (n.op().equals("if")) {
			int c = steps(n.args().get(0), guard, steps, memo);
			args.add(c);
			args.add(steps(n.args().get(1), "(" + guard + ") AND " + ref(c) + ".b IS TRUE", steps, memo));
			args.add(steps(n.args().get(2), "(" + guard + ") AND " + ref(c) + ".b IS NOT TRUE", steps, memo));
		} else if (n.op().equals("and") || n.op().equals("or")) {
			int c = steps(n.args().get(0), guard, steps, memo);
			args.add(c);
			args.add(steps(n.args().get(1),
					"(" + guard + ") AND " + ref(c) + (n.op().equals("and") ? ".b IS NOT FALSE" : ".b IS NOT TRUE"),
					steps, memo));
		} else
			for (var a : n.args())
				args.add(steps(a, guard, steps, memo));
		int index = steps.size();
		if (index >= 1024)
			throw new Unsupported();
		steps.add(new Step(n, List.copyOf(args), guard));
		memo.put(key, index);
		return index;
	}
	private static String isNull(String value, Kind kind) {
		return switch (kind) {
			case NUMBER -> value + ".n.digits IS NULL AND NOT " + value + ".n.invalid";
			case STRING -> value + ".s IS NULL";
			case BOOLEAN -> value + ".b IS NULL";
			case NULL -> "TRUE";
		};
	}

	private static String ref(int index) {
		return "element_at(scalar_values," + (index + 1) + ")";
	}
	static String sql(RuntimeModel m, RuntimeDecision d, Map<Integer, String> names) {
		var p = Objects.requireNonNull(plan(m, d));
		var steps = new ArrayList<Step>();
		steps(p.root(), "TRUE", steps, new HashMap<>());
		var branches = new StringBuilder("CASE scalar_step");
		for (int i = 0; i < steps.size(); i++) {
			var s = steps.get(i);
			String expr = emit(p, s, names, column(d.id(), names));
			if (!s.guard().equals("TRUE"))
				expr = "CASE WHEN " + s.guard() + " THEN " + expr + " ELSE " + nil() + " END";
			branches.append(" WHEN ").append(i).append(" THEN ").append(expr);
		}
		branches.append(" ELSE ").append(nil()).append(" END");
		return "aggregate(sequence(0," + (steps.size() - 1)
				+ "),CAST(array() AS ARRAY<STRUCT<n:STRUCT<digits:STRING,scale:BIGINT,negative:BOOLEAN,invalid:BOOLEAN>,s:STRING,b:BOOLEAN>>),(scalar_values,scalar_step) -> concat(scalar_values,array("
				+ branches + ")),scalar_done -> element_at(scalar_done,-1)."
				+ (p.root().kind() == Kind.STRING ? "s" : "b") + ")";
	}
	private static String emit(Plan p, Step step, Map<Integer, String> names, String column) {
		var n = step.node();
		String a = step.args().isEmpty() ? null : ref(step.args().getFirst()),
				b = step.args().size() < 2 ? null : ref(step.args().get(1));
		if (n.op().equals("raw"))
			return "element_at(`" + column + "`," + (p.raw().indexOf((Raw) n.value()) + 1) + ")";
		if (n.op().equals("column")) {
			var c = (Column) n.value();
			String value = "`" + names.get(c.slot()).replace("`", "``") + "`";
			return n.kind() == Kind.STRING ? string("hex(encode(" + value + ",'UTF-16BE'))") : bool(value);
		}
		if (n.op().equals("constant"))
			return switch (n.kind()) {
				case NULL -> nil();
				case NUMBER -> num(SparkSqlDecimalSql.constant(new BigDecimal((String) n.value())));
				case STRING -> string(quote(stringKey((String) n.value())));
				case BOOLEAN -> bool((String) n.value());
			};
		if (n.op().equals("in"))
			return bool("CASE WHEN " + isNull(a, n.args().getFirst().kind()) + " THEN CAST(NULL AS BOOLEAN) ELSE " + b
					+ ".b END");
		if (n.op().equals("sequence"))
			return ref(step.args().getLast());
		if (n.op().equals("if"))
			return "CASE WHEN " + a + ".b THEN " + b + " ELSE " + ref(step.args().get(2)) + " END";
		if (n.op().equals("not"))
			return bool("NOT " + a + ".b");
		if (n.op().equals("and") || n.op().equals("or"))
			return bool(a + ".b " + n.op().toUpperCase(Locale.ROOT) + " " + b + ".b");
		if (n.op().equals("negate"))
			return num(SparkSqlDecimalSql.let("scalar_unary", a + ".n",
					"named_struct('digits',scalar_unary.digits,'scale',scalar_unary.scale,'negative',NOT scalar_unary.negative AND scalar_unary.digits<>'0','invalid',scalar_unary.invalid)"));
		if (n.kind() == Kind.NUMBER)
			return num(switch (n.op()) {
				case "ADD" -> SparkSqlDecimalSql.add(a + ".n", b + ".n", false);
				case "SUBTRACT" -> SparkSqlDecimalSql.add(a + ".n", b + ".n", true);
				case "MULTIPLY" -> SparkSqlDecimalSql.multiply(a + ".n", b + ".n");
				case "DIVIDE" -> SparkSqlDecimalSql.divide(a + ".n", b + ".n");
				case "POWER" -> SparkSqlDecimalSql.power(a + ".n", b + ".n");
				default -> throw new Unsupported();
			});
		Kind operand = n.args().getFirst().kind() == Kind.NULL ? n.args().get(1).kind() : n.args().getFirst().kind();
		if (n.op().equals("EQUAL") || n.op().equals("NOT_EQUAL")) {
			String equal;
			if (operand == Kind.NUMBER)
				equal = "CASE WHEN " + a + ".n.digits IS NULL AND NOT " + a + ".n.invalid AND " + b
						+ ".n.digits IS NULL AND NOT " + b + ".n.invalid THEN TRUE WHEN " + a
						+ ".n.digits IS NULL AND NOT " + a + ".n.invalid OR " + b + ".n.digits IS NULL AND NOT " + b
						+ ".n.invalid THEN FALSE ELSE " + SparkSqlDecimalSql.compare(a + ".n", b + ".n") + "=0 END";
			else {
				String field = operand == Kind.STRING ? "s" : "b";
				equal = a + "." + field + " <=> " + b + "." + field;
			}
			return bool(n.op().equals("NOT_EQUAL") ? "NOT (" + equal + ")" : equal);
		}
		String op = switch (n.op()) {
			case "LESS" -> "<";
			case "LESS_EQUAL" -> "<=";
			case "GREATER" -> ">";
			case "GREATER_EQUAL" -> ">=";
			default -> throw new Unsupported();
		};
		return bool(SparkSqlDecimalSql.compare(a + ".n", b + ".n") + op + "0");
	}
}

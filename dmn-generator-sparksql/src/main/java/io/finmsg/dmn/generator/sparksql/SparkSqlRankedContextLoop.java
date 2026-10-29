package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exact context-valued loops over bounded literal relations and numeric
 * rankings.
 */
final class SparkSqlRankedContextLoop {
	private record Ranking(String name, int field, boolean descending) {
	}
	private record Plan(RuntimeRelationExpression source, SparkSqlScalarGraph.ContextBody body,
			List<Ranking> rankings) {
	}
	private static Plan plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent() || decision.type().kind() != RuntimeTypeKind.CONTEXT
				|| !(decision.expression().orElse(null) instanceof RuntimeContextExpression context)
				|| context.entries().isEmpty() || context.entries().size() > 9)
			return null;
		var first = context.entries().getFirst();
		if (!(first.expression() instanceof RuntimeForExpression loop) || loop.iterations().size() != 1
				|| loop.partialSlot() >= 0 || loop.iterations().getFirst().end().isPresent()
				|| !(loop.iterations().getFirst().source() instanceof RuntimeValueReference ref))
			return null;
		var sourceDecision = model.decisions().stream().filter(d -> d.resultSlot() == ref.sourceSlot()).findFirst()
				.orElse(null);
		if (sourceDecision == null || sourceDecision.decisionTable().isPresent())
			return null;
		var relation = literalRelation(sourceDecision.expression().orElse(null), sourceDecision.type());
		if (relation == null || relation.columns().isEmpty() || relation.columns().size() > 16
				|| relation.rows().size() > 256)
			return null;
		var fields = new ArrayList<RuntimeField>();
		var seen = new HashSet<String>();
		for (int i = 0; i < relation.columns().size(); i++) {
			var column = relation.columns().get(i);
			if (!valid(column.name()) || !seen.add(column.name())
					|| !Set.of(RuntimeTypeKind.NUMBER, RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN)
							.contains(column.type().kind()))
				return null;
			fields.add(new RuntimeField(i, column.name(), column.type()));
		}
		for (var row : relation.rows())
			for (int i = 0; i < row.size(); i++) {
				if (!(row.get(i) instanceof RuntimeConstant c) || c.kind() == RuntimeConstantKind.NULL
						|| !c.kind().name().equals(fields.get(i).type().kind().name())
						|| c.kind() == RuntimeConstantKind.STRING && !validText(c.value()))
					return null;
				try {
					SparkSqlScalarGraph.literal(c);
				} catch (RuntimeException unsupported) {
					return null;
				}
			}
		var body = SparkSqlScalarGraph.contextBody(model, loop, fields);
		if (body == null || !SparkSqlScalarGraph.safeContextStrings(body)
				|| body.names().stream().anyMatch(n -> !valid(n)))
			return null;
		var rankings = new ArrayList<Ranking>();
		var localSlots = new HashSet<Integer>();
		seen.clear();
		for (int i = 0; i < context.entries().size(); i++) {
			var entry = context.entries().get(i);
			if (!valid(entry.name()) || !seen.add(entry.name()) || entry.localSlot() < 0
					|| !localSlots.add(entry.localSlot()))
				return null;
			if (i == 0) {
				rankings.add(new Ranking(entry.name(), -1, false));
				continue;
			}
			RuntimeExpression expr = entry.expression();
			if (expr instanceof RuntimeInvocationExpression call && call.function().isPresent()
					&& call.namedArguments().isEmpty())
				expr = new RuntimeFunctionCall(call.function().get(), call.positionalArguments(), call.type());
			if (!(expr instanceof RuntimeFunctionCall call) || !call.function().equalsIgnoreCase("sort")
					|| call.arguments().size() != 2
					|| !(call.arguments().getFirst() instanceof RuntimeLocalReference local)
					|| local.lexicalDepth() != 0 || local.localSlot() != first.localSlot()
					|| !(call.arguments().get(1) instanceof RuntimeFunctionDefinition fn) || fn.external()
					|| fn.parameters().size() != 2
					|| fn.parameters().getFirst().localSlot() == fn.parameters().get(1).localSlot()
					|| !(fn.body().orElse(null) instanceof RuntimeBinaryExpression cmp)
					|| !Set.of(RuntimeBinaryOperator.LESS, RuntimeBinaryOperator.GREATER).contains(cmp.operator())
					|| !(cmp.left() instanceof RuntimePathExpression left)
					|| !(cmp.right() instanceof RuntimePathExpression right)
					|| !(left.source() instanceof RuntimeLocalReference x)
					|| !(right.source() instanceof RuntimeLocalReference y) || x.lexicalDepth() != 0
					|| y.lexicalDepth() != 0 || x.localSlot() != fn.parameters().getFirst().localSlot()
					|| y.localSlot() != fn.parameters().get(1).localSlot() || !left.member().equals(right.member()))
				return null;
			for (var parameter : fn.parameters()) {
				if (parameter.type().kind() == RuntimeTypeKind.ANY)
					continue;
				if (parameter.type().kind() != RuntimeTypeKind.CONTEXT)
					return null;
				if (!parameter.type().fieldLayout().isEmpty()) {
					if (parameter.type().fieldLayout().size() != body.names().size())
						return null;
					for (var f : parameter.type().fieldLayout()) {
						int index = body.names().indexOf(f.name());
						if (index < 0 || !f.type().kind().name().equals(body.kinds().get(index).name()))
							return null;
					}
				}
			}
			var output = fn.type().kind() == RuntimeTypeKind.FUNCTION ? fn.type().returnType() : fn.type();
			if (output == null || !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.BOOLEAN).contains(output.kind()))
				return null;
			int field = body.names().indexOf(left.member());
			if (field < 0 || body.kinds().get(field) != SparkSqlScalarGraph.Kind.NUMBER
					|| left.fieldIndex() >= 0 && left.fieldIndex() != field
					|| right.fieldIndex() >= 0 && right.fieldIndex() != field)
				return null;
			rankings.add(new Ranking(entry.name(), field, cmp.operator() == RuntimeBinaryOperator.GREATER));
		}
		// A structured result is terminal: consumers require their own lossless proof.
		if (!decision.type().fieldLayout().isEmpty()) {
			if (decision.type().fieldLayout().size() != rankings.size())
				return null;
			for (var f : decision.type().fieldLayout()) {
				if (rankings.stream().noneMatch(r -> r.name().equals(f.name()))
						|| f.type().kind() != RuntimeTypeKind.LIST || f.type().elementType() == null)
					return null;
				var type = f.type().elementType();
				if (type.kind() != RuntimeTypeKind.CONTEXT || type.fieldLayout().size() != body.names().size())
					return null;
				for (var member : type.fieldLayout()) {
					int index = body.names().indexOf(member.name());
					if (index < 0 || !member.type().kind().name().equals(body.kinds().get(index).name()))
						return null;
				}
			}
		}
		return new Plan(relation, body, List.copyOf(rankings));
	}
	private static RuntimeRelationExpression literalRelation(RuntimeExpression expression, RuntimeType declared) {
		if (declared.kind() != RuntimeTypeKind.LIST || declared.elementType() == null
				|| declared.elementType().kind() != RuntimeTypeKind.CONTEXT)
			return null;
		if (expression instanceof RuntimeRelationExpression relation) {
			var fields = declared.elementType().fieldLayout();
			if (fields.size() != relation.columns().size())
				return null;
			for (int i = 0; i < fields.size(); i++)
				if (!fields.get(i).name().equals(relation.columns().get(i).name())
						|| !fields.get(i).type().equals(relation.columns().get(i).type()))
					return null;
			return relation;
		}
		if (!(expression instanceof RuntimeListExpression list) || list.type().kind() != RuntimeTypeKind.LIST)
			return null;
		var fields = declared.elementType().fieldLayout();
		if (fields.isEmpty())
			return null;
		var rows = new ArrayList<List<RuntimeExpression>>();
		for (var item : list.elements()) {
			if (!(item instanceof RuntimeContextExpression context) || context.entries().size() != fields.size())
				return null;
			var row = new ArrayList<RuntimeExpression>();
			for (int i = 0; i < fields.size(); i++) {
				if (!context.entries().get(i).name().equals(fields.get(i).name()))
					return null;
				row.add(context.entries().get(i).expression());
			}
			rows.add(List.copyOf(row));
		}
		return new RuntimeRelationExpression(
				fields.stream().map(f -> new RuntimeRelationColumn(f.name(), f.type())).toList(), rows, declared);
	}
	private static boolean valid(String name) {
		return !name.isEmpty() && validText(name);
	}
	private static boolean validText(String text) {
		return text.codePoints().noneMatch(c -> c >= 0xD800 && c <= 0xDFFF);
	}
	static boolean supports(RuntimeModel model, RuntimeDecision d) {
		return plan(model, d) != null;
	}
	static List<Integer> dependencies(RuntimeModel model, RuntimeDecision d) {
		var p = plan(model, d);
		return p == null ? null : p.body().plan().dependencies();
	}
	static Set<Integer> ids(RuntimeModel model) {
		var ids = new LinkedHashSet<Integer>();
		for (var d : model.decisions())
			if (supports(model, d))
				ids.add(d.id());
		return ids;
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
	static long[] slots(RuntimeModel model, RuntimeDecision d) {
		return Objects.requireNonNull(plan(model, d)).body().plan().raw().stream()
				.mapToLong(SparkSqlScalarGraph.Raw::slot).toArray();
	}
	static String[] paths(RuntimeModel model, RuntimeDecision d) {
		return Objects.requireNonNull(plan(model, d)).body().plan().raw().stream().map(r -> String.join(".",
				r.path().stream().map(
						s -> Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)))
						.toList()))
				.toArray(String[]::new);
	}
	static String[] kinds(RuntimeModel model, RuntimeDecision d) {
		return Objects.requireNonNull(plan(model, d)).body().plan().raw().stream().map(r -> r.kind().name())
				.toArray(String[]::new);
	}
	private static String hex(byte[] bytes) {
		return "unhex('" + HexFormat.of().formatHex(bytes) + "')";
	}
	private static String int32(String value) {
		return "unhex(lpad(right(hex(" + value + "),8),8,'0'))";
	}
	private static String key(String value) {
		var bytes = value.getBytes(StandardCharsets.UTF_8);
		return "concat(" + int32(Integer.toString(bytes.length)) + "," + hex(bytes) + ")";
	}
	private static String scalar(String value, SparkSqlScalarGraph.Kind kind) {
		return switch (kind) {
			case NUMBER -> "CASE WHEN " + value + ".n.digits IS NULL AND NOT " + value
					+ ".n.invalid THEN unhex('00') ELSE substring(" + SparkSqlDecimalSql.payload(value + ".n")
					+ ",5) END";
			case STRING ->
				SparkSqlDecimalSql.let("structured_text", "encode(decode(unhex(" + value + ".s),'UTF-16BE'),'UTF-8')",
						"CASE WHEN structured_text IS NULL THEN unhex('00') ELSE concat(unhex('01'),"
								+ int32("length(structured_text)") + ",structured_text) END");
			case BOOLEAN -> "CASE WHEN " + value + ".b IS NULL THEN unhex('00') ELSE concat(unhex('02'),CASE WHEN "
					+ value + ".b THEN unhex('01') ELSE unhex('00') END) END";
			case NULL -> "unhex('00')";
		};
	}
	private static String rows(String array, SparkSqlScalarGraph.ContextBody body) {
		var fields = new ArrayList<String>();
		fields.add("unhex('07')");
		fields.add(int32(Integer.toString(body.names().size())));
		for (int i = 0; i < body.names().size(); i++) {
			fields.add(key(body.names().get(i)));
			fields.add(scalar("structured_row.f" + i, body.kinds().get(i)));
		}
		return "concat(unhex('06')," + int32("size(" + array + ")") + ",aggregate(" + array
				+ ",unhex(''),(structured_bytes,structured_row) -> concat(structured_bytes,concat("
				+ String.join(",", fields) + "))))";
	}
	static String sql(RuntimeModel model, RuntimeDecision d, Map<Integer, String> names) {
		var p = Objects.requireNonNull(plan(model, d));
		var source = new ArrayList<String>();
		for (var row : p.source().rows()) {
			var fields = new ArrayList<String>();
			for (int i = 0; i < row.size(); i++) {
				fields.add("'f" + i + "'");
				fields.add(SparkSqlScalarGraph.literal((RuntimeConstant) row.get(i)));
			}
			source.add("named_struct(" + String.join(",", fields) + ")");
		}
		String universal = "STRUCT<n:STRUCT<digits:STRING,scale:BIGINT,negative:BOOLEAN,invalid:BOOLEAN>,s:STRING,b:BOOLEAN>";
		var types = new ArrayList<String>();
		for (int i = 0; i < p.source().columns().size(); i++)
			types.add("f" + i + ":" + universal);
		String input = source.isEmpty()
				? "CAST(array() AS ARRAY<STRUCT<" + String.join(",", types) + ">>)"
				: "array(" + String.join(",", source) + ")";
		String computed = "transform(" + input + ",structured_item -> "
				+ SparkSqlScalarGraph.contextSql(p.body(), names, SparkSqlScalarGraph.column(d.id(), names)) + ")";
		var fields = new ArrayList<String>();
		fields.add("unhex('444D4E0107')");
		fields.add(int32(Integer.toString(p.rankings().size())));
		for (var ranking : p.rankings()) {
			String list = "structured_rows";
			if (ranking.field() >= 0) {
				String cmp = SparkSqlDecimalSql.compare("structured_left.f" + ranking.field() + ".n",
						"structured_right.f" + ranking.field() + ".n");
				list = "array_sort(structured_rows,(structured_left,structured_right) -> CAST(coalesce("
						+ (ranking.descending() ? "-(" + cmp + ")" : cmp) + ",0) AS INT))";
			}
			fields.add(key(ranking.name()));
			fields.add(rows(list, p.body()));
		}
		return SparkSqlDecimalSql.let("structured_rows", computed, "concat(" + String.join(",", fields) + ")");
	}
}

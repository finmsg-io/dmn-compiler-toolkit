package io.finmsg.dmn.frontend.dsl;

import io.finmsg.dmn.frontend.dsl.DslModel.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic, idempotent canonical formatter for {@code .decision} DSL
 * models.
 */
public final class DmnDslFormatter {

	private static final Pattern SIMPLE_IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	private DmnDslFormatter() {
	}

	public static String format(DslModel model) {
		StringBuilder sb = new StringBuilder();
		sb.append("model ").append(formatName(model.name())).append('\n');
		sb.append("namespace ").append(quote(model.namespace())).append('\n');
		model.id().ifPresent(id -> sb.append("id ").append(quote(id)).append('\n'));

		if (!model.imports().isEmpty()) {
			sb.append('\n');
			for (DslImport imp : model.imports()) {
				sb.append("import ").append(formatName(imp.alias())).append(" from ").append(quote(imp.locationUri()))
						.append(" namespace ").append(quote(imp.namespace())).append('\n');
			}
		}

		for (DslType type : model.types()) {
			sb.append('\n');
			sb.append("type ").append(formatName(type.name()));
			type.id().ifPresent(id -> sb.append(" id ").append(quote(id)));
			switch (type.kind()) {
				case RECORD -> {
					sb.append(" {\n");
					for (DslRecordField field : type.fields()) {
						sb.append("  ").append(formatName(field.name())).append(": ").append(field.typeRef());
						field.constraint().ifPresent(c -> sb.append(" constraint ").append(c));
						sb.append('\n');
					}
					sb.append("}\n");
				}
				case COLLECTION -> sb.append(" list<").append(type.elementOrBaseType().orElseThrow()).append(">\n");
				case ALIAS -> {
					sb.append(" = ").append(type.elementOrBaseType().orElseThrow());
					type.constraint().ifPresent(c -> sb.append(" constraint ").append(c));
					sb.append('\n');
				}
			}
		}

		if (!model.inputs().isEmpty()) {
			sb.append('\n');
			for (DslInput input : model.inputs()) {
				sb.append("input ").append(formatName(input.name()));
				input.id().ifPresent(id -> sb.append(" id ").append(quote(id)));
				sb.append(": ").append(input.typeRef()).append('\n');
			}
		}

		for (DslBkm bkm : model.bkms()) {
			sb.append('\n');
			sb.append("bkm ").append(formatName(bkm.name()));
			bkm.id().ifPresent(id -> sb.append(" id ").append(quote(id)));
			sb.append('(');
			sb.append(bkm.parameters().stream().map(p -> formatName(p.name()) + ": " + p.typeRef())
					.collect(Collectors.joining(", ")));
			sb.append("): ").append(bkm.returnType()).append('\n');
			if (!bkm.uses().isEmpty()) {
				sb.append("  uses ").append(formatRefs(bkm.uses())).append('\n');
			}
			sb.append("  = ").append(bkm.bodyFeel()).append('\n');
		}

		for (DslDecision dec : model.decisions()) {
			sb.append('\n');
			sb.append("decision ").append(formatName(dec.name()));
			dec.id().ifPresent(id -> sb.append(" id ").append(quote(id)));
			sb.append(": ").append(dec.typeRef()).append('\n');
			if (!dec.requires().isEmpty()) {
				sb.append("  requires ").append(formatRefs(dec.requires())).append('\n');
			}
			if (!dec.uses().isEmpty()) {
				sb.append("  uses ").append(formatRefs(dec.uses())).append('\n');
			}
			if (dec.table().isPresent()) {
				DslTable table = dec.table().get();
				sb.append("  table ").append(table.hitPolicy()).append(" {\n");
				sb.append("    when ");
				sb.append(table.inputs().stream()
						.map(in -> in.expressionFeel() + in.typeRef().map(t -> ": " + t).orElse(""))
						.collect(Collectors.joining(" | ")));
				sb.append(" => ");
				sb.append(table.outputs().stream()
						.map(out -> out.name().map(n -> n + ": ").orElse("") + out.typeRef().orElse("")
								+ out.defaultFeel().map(d -> " default " + d).orElse(""))
						.collect(Collectors.joining(" | ")));
				sb.append('\n');
				for (DslTableRule rule : table.rules()) {
					sb.append("    | ").append(String.join(" | ", rule.inputEntries())).append(" | => ")
							.append(String.join(" | ", rule.outputEntries())).append('\n');
				}
				sb.append("  }\n");
			} else {
				sb.append("  = ").append(dec.literalFeel().orElseThrow()).append('\n');
			}
		}

		for (DslTest test : model.tests()) {
			sb.append('\n');
			sb.append("test ").append(quote(test.name())).append(" {\n");
			for (DslTestEntry given : test.givens()) {
				sb.append("  given ").append(formatName(given.name())).append(" = ").append(given.feelExpression())
						.append('\n');
			}
			for (DslTestEntry expect : test.expects()) {
				sb.append("  expect ").append(formatName(expect.name())).append(" = ").append(expect.feelExpression())
						.append('\n');
			}
			sb.append("}\n");
		}

		return sb.toString();
	}

	private static String formatRefs(List<DslRef> refs) {
		return refs.stream().map(ref -> {
			String base = ref.importAlias().map(a -> a + "." + formatName(ref.name()))
					.orElseGet(() -> formatName(ref.name()));
			return ref.explicitElementId().map(id -> base + "#" + id).orElse(base);
		}).collect(Collectors.joining(", "));
	}

	private static String formatName(String name) {
		if (SIMPLE_IDENT.matcher(name).matches()) {
			return name;
		}
		return quote(name);
	}

	private static String quote(String value) {
		String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
				.replace("\t", "\\t");
		return "\"" + escaped + "\"";
	}
}

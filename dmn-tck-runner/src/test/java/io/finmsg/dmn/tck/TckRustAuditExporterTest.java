package io.finmsg.dmn.tck;

import io.finmsg.dmn.dmnir.proto.v0.DmnIrArtifact;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrDecision;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrInput;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrModel;
import io.finmsg.dmn.tck.dmnir.TckDmnIrArchive;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Exports all 3,407 official OMG DMN TCK cases into a
 * {@code dmn-rust-tck-runner} audit directory using the pre-compiled,
 * bounded-verified {@code .dmnir} archive in
 * {@code dmn-tck/src/main/resources/tck-dmnir/} (151 deduplicated groups).
 *
 * <p>
 * Run with:
 * 
 * <pre>
 * mvn test -o -Ptck -pl dmn-tck-runner -Dtest=TckRustAuditExporterTest \
 *     -DfailIfNoTests=false -Dtck.exportRustAudit=true
 * </pre>
 */
class TckRustAuditExporterTest {
	private static final List<String> CATALOGUE_ROOTS = List.of("compliance-level-2", "compliance-level-3");

	@Test
	void exportRustTckAuditDirectory() throws Exception {
		Assumptions.assumeTrue(Boolean.getBoolean("tck.exportRustAudit"),
				"Skipping Rust TCK audit export (set -Dtck.exportRustAudit=true).");

		long startNanos = System.nanoTime();
		URL officialResource = TckRustAuditExporterTest.class.getClassLoader().getResource("tck-official/TestCases");
		if (officialResource == null) {
			throw new IllegalStateException("Official OMG DMN TCK test suite is missing on classpath.");
		}
		Path testCasesDir = Path.of(officialResource.toURI());
		TckCatalogueInventory inventory = new TckCatalogueDiscovery().discover(testCasesDir, CATALOGUE_ROOTS);
		TckDmnIrArchive archive = TckDmnIrArchive.loadFromClasspath()
				.orElseThrow(() -> new IllegalStateException("tck-dmnir archive missing on classpath"));

		String outProp = System.getProperty("tck.rustAuditDir", "../target/rust-tck-3407");
		Path outRoot = Path.of(outProp).normalize().toAbsolutePath();
		if (Files.exists(outRoot)) {
			try (var walk = Files.walk(outRoot)) {
				walk.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			}
		}
		Files.createDirectories(outRoot);

		Map<String, String> artifactToGroup = new LinkedHashMap<>();
		Map<String, DmnIrArtifact> parsedArtifacts = new LinkedHashMap<>();
		Map<String, StringBuilder> groupCases = new LinkedHashMap<>();
		StringBuilder manifest = new StringBuilder();
		manifest.append("entry_id\tcase_id\tstatus\tgroup\tdiagnostic\n");

		int caseIndex = 0;
		for (TckCatalogueEntry entry : inventory.entries()) {
			if (!entry.executable()) {
				continue;
			}
			for (TckTestCase tc : entry.testCases()) {
				String caseKey = entry.id() + "#" + tc.id();
				TckDmnIrArchive.CaseEntry archEntry = archive.entry(caseKey).orElse(null);
				if (archEntry == null || !archEntry.exported()) {
					String diag = archEntry == null ? "Missing from tck-dmnir archive" : archEntry.exportDiagnostic();
					manifest.append(entry.id()).append('\t').append(tc.id()).append('\t').append("EXPORT_REJECTION\t\t")
							.append(sanitize(diag)).append('\n');
					caseIndex++;
					continue;
				}
				String artifactPath = archEntry.artifactPath();
				String groupId = artifactToGroup.computeIfAbsent(artifactPath, p -> {
					String id = String.format("g%04d", artifactToGroup.size());
					try {
						byte[] bytes = archive.readArtifactBytes(p);
						Path groupDir = outRoot.resolve(id);
						Files.createDirectories(groupDir);
						Files.write(groupDir.resolve("artifact.dmnir"), bytes);
						parsedArtifacts.put(id, DmnIrArtifact.parseFrom(bytes));
						groupCases.put(id, new StringBuilder());
					} catch (IOException e) {
						throw new RuntimeException("Failed writing artifact " + p, e);
					}
					return id;
				});

				DmnIrArtifact artifact = parsedArtifacts.get(groupId);
				String caseSrc = generateRustCase(caseIndex, entry.id(), tc, artifact);
				groupCases.get(groupId).append(caseSrc).append('\n');

				manifest.append(entry.id()).append('\t').append(tc.id()).append('\t').append("READY\t").append(groupId)
						.append("\t\n");
				caseIndex++;
			}
		}

		for (Map.Entry<String, StringBuilder> e : groupCases.entrySet()) {
			Files.writeString(outRoot.resolve(e.getKey()).resolve("cases.rs"), e.getValue().toString(),
					StandardCharsets.UTF_8);
		}
		Files.writeString(outRoot.resolve("manifest.tsv"), manifest.toString(), StandardCharsets.UTF_8);

		double elapsed = (System.nanoTime() - startNanos) / 1_000_000_000.0;
		System.out.printf("Rust TCK audit exported: %d cases across %d groups into %s in %.2f s%n", caseIndex,
				artifactToGroup.size(), outRoot, elapsed);
	}

	private static String generateRustCase(int idx, String entryId, TckTestCase tc, DmnIrArtifact artifact) {
		String rootNs = artifact.getRootModelNamespace();
		DmnIrModel rootModel = artifact.getModelsList().stream().filter(m -> m.getNamespace().equals(rootNs))
				.findFirst().orElse(artifact.getModels(0));

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("#[test] fn case_%05d() { ", idx));

		if (tc.invocableName().isPresent()) {
			String invName = tc.invocableName().get();
			sb.append(
					"let named_inputs: std::collections::BTreeMap<String, Value> = std::collections::BTreeMap::from([");
			for (Map.Entry<String, TckValue> in : tc.inputs().entrySet()) {
				sb.append('(').append(rustString(in.getKey())).append(".to_string(), ")
						.append(renderValue(in.getValue().runtimeValue())).append("),");
			}
			sb.append("]); ");
			sb.append(
					"let mut inputs: std::collections::BTreeMap<(&str, &str), Value> = std::collections::BTreeMap::new(); ");
			for (Map.Entry<String, TckValue> in : tc.inputs().entrySet()) {
				String[] key = resolveInputKey(artifact, rootModel, in.getKey(), tc.inputNamespaces().get(in.getKey()));
				if (key != null) {
					sb.append("inputs.insert((").append(rustString(key[0])).append(", ").append(rustString(key[1]))
							.append("), ").append(renderValue(in.getValue().runtimeValue())).append("); ");
				}
			}
			sb.append("match std::panic::catch_unwind(|| evaluate_invocable(").append(rustString(invName))
					.append(", &inputs, &named_inputs)) { ");
			sb.append("Ok(Ok(actual)) => { ");
			for (Map.Entry<String, TckValue> exp : tc.expectedResults().entrySet()) {
				String expectedExpr = renderValue(exp.getValue().runtimeValue());
				sb.append("{ let expected = ").append(expectedExpr).append("; ");
				sb.append("let actual_val = match &actual { Value::Record(m) => m.get(")
						.append(rustString(exp.getKey())).append(").unwrap_or(&actual), other => other }; ");
				sb.append("assert!(equivalent(actual_val, &expected), ")
						.append(rustString("Expected result differs: " + entryId + "#" + tc.id() + "/" + exp.getKey()))
						.append("); } ");
			}
			sb.append("}, ");
			boolean expectErr = !tc.expectedErrorResults().isEmpty();
			sb.append("Ok(Err(error)) => { if !").append(expectErr).append(
					" { println!(\"RUST_TCK_EXECUTION_ERROR: {}\", error); panic!(\"unexpected evaluation error\"); } }, ");
			sb.append(
					"Err(_) => { println!(\"RUST_TCK_EXECUTION_ERROR: panic\"); panic!(\"evaluation panicked\"); } } }");
			return sb.toString();
		}

		sb.append("let mut inputs = std::collections::BTreeMap::new(); ");
		for (DmnIrModel m : artifact.getModelsList()) {
			for (DmnIrInput inp : m.getInputsList()) {
				sb.append("inputs.insert((").append(rustString(m.getNamespace())).append(", ")
						.append(rustString(inp.getIdentity().getId())).append("), Value::Null); ");
			}
		}
		for (Map.Entry<String, TckValue> in : tc.inputs().entrySet()) {
			String[] key = resolveInputKey(artifact, rootModel, in.getKey(), tc.inputNamespaces().get(in.getKey()));
			if (key != null) {
				sb.append("inputs.insert((").append(rustString(key[0])).append(", ").append(rustString(key[1]))
						.append("), ").append(renderValue(in.getValue().runtimeValue())).append("); ");
			}
		}
		sb.append("match std::panic::catch_unwind(|| evaluate(&inputs)) { ");
		sb.append("Ok(Ok(actual)) => { ");
		for (Map.Entry<String, TckValue> exp : tc.expectedResults().entrySet()) {
			String[] key = resolveDecisionKey(artifact, rootModel, exp.getKey());
			String expectedExpr = renderValue(exp.getValue().runtimeValue());
			if (key == null) {
				sb.append("panic!(").append(rustString("Unknown decision " + exp.getKey())).append("); ");
			} else {
				sb.append("assert!(matches!(actual.get(&(").append(rustString(key[0])).append(", ")
						.append(rustString(key[1])).append(")), Some(value) if equivalent(value, &")
						.append(expectedExpr).append(")), \"Expected result differs: ")
						.append(escapeFormat(entryId + "#" + tc.id() + "/" + exp.getKey()))
						.append("; actual={:?}\", actual.get(&(").append(rustString(key[0])).append(", ")
						.append(rustString(key[1])).append("))); ");
			}
		}
		sb.append("}, ");
		boolean expectErr = !tc.expectedErrorResults().isEmpty();
		sb.append("Ok(Err(error)) => { if !").append(expectErr).append(
				" { println!(\"RUST_TCK_EXECUTION_ERROR: {}\", error); panic!(\"unexpected evaluation error\"); } }, ");
		sb.append("Err(_) => { println!(\"RUST_TCK_EXECUTION_ERROR: panic\"); panic!(\"evaluation panicked\"); } } }");
		return sb.toString();
	}

	private static String[] resolveInputKey(DmnIrArtifact artifact, DmnIrModel rootModel, String name,
			String explicitNs) {
		if (explicitNs != null && !explicitNs.isBlank()) {
			for (DmnIrModel model : artifact.getModelsList()) {
				if (model.getNamespace().equals(explicitNs)) {
					for (DmnIrInput input : model.getInputsList()) {
						if (matchesName(input.getIdentity().getName(), input.getVariable().getName(), name)) {
							return new String[]{model.getNamespace(), input.getIdentity().getId()};
						}
					}
				}
			}
		}
		for (DmnIrInput input : rootModel.getInputsList()) {
			if (matchesName(input.getIdentity().getName(), input.getVariable().getName(), name)) {
				return new String[]{rootModel.getNamespace(), input.getIdentity().getId()};
			}
		}
		for (DmnIrModel model : artifact.getModelsList()) {
			for (DmnIrInput input : model.getInputsList()) {
				if (matchesName(input.getIdentity().getName(), input.getVariable().getName(), name)) {
					return new String[]{model.getNamespace(), input.getIdentity().getId()};
				}
			}
		}
		return null;
	}

	private static String[] resolveDecisionKey(DmnIrArtifact artifact, DmnIrModel rootModel, String name) {
		for (DmnIrDecision dec : rootModel.getDecisionsList()) {
			if (matchesName(dec.getIdentity().getName(), dec.getVariable().getName(), name)) {
				return new String[]{rootModel.getNamespace(), dec.getIdentity().getId()};
			}
		}
		for (DmnIrModel model : artifact.getModelsList()) {
			for (DmnIrDecision dec : model.getDecisionsList()) {
				if (matchesName(dec.getIdentity().getName(), dec.getVariable().getName(), name)) {
					return new String[]{model.getNamespace(), dec.getIdentity().getId()};
				}
			}
		}
		return null;
	}

	private static boolean matchesName(String idName, String varName, String target) {
		return Objects.equals(idName, target) || Objects.equals(varName, target);
	}

	@SuppressWarnings("unchecked")
	private static String renderValue(Object value) {
		if (value == null) {
			return "Value::Null";
		}
		if (value instanceof Boolean b) {
			return b ? "Value::Boolean(true)" : "Value::Boolean(false)";
		}
		if (value instanceof BigDecimal bd) {
			return "Value::Number { coefficient: " + rustString(bd.unscaledValue().toString()) + ".into(), scale: "
					+ bd.scale() + " }";
		}
		if (value instanceof String s) {
			return "Value::Text(" + rustString(s) + ".into())";
		}
		if (value instanceof LocalDate d) {
			return "Value::Date { year: " + d.getYear() + ", month: " + d.getMonthValue() + ", day: "
					+ d.getDayOfMonth() + " }";
		}
		if (value instanceof LocalTime t) {
			return "Value::Time(" + rustString(formatLocalTime(t)) + ".into())";
		}
		if (value instanceof OffsetTime ot) {
			return "Value::Time(" + rustString(formatLocalTime(ot.toLocalTime()) + ot.getOffset().toString())
					+ ".into())";
		}
		if (value instanceof LocalDateTime ldt) {
			return "Value::DateTime("
					+ rustString(ldt.toLocalDate().toString() + "T" + formatLocalTime(ldt.toLocalTime())) + ".into())";
		}
		if (value instanceof OffsetDateTime odt) {
			return "Value::DateTime(" + rustString(odt.toLocalDate().toString() + "T"
					+ formatLocalTime(odt.toLocalTime()) + odt.getOffset().toString()) + ".into())";
		}
		if (value instanceof ZonedDateTime zdt) {
			String s = zdt.toLocalDate().toString() + "T" + formatLocalTime(zdt.toLocalTime()) + "@"
					+ zdt.getZone().getId();
			return "Value::DateTime(" + rustString(s) + ".into())";
		}
		if (value instanceof Period p) {
			return "Value::Duration(" + rustString(p.toString()) + ".into())";
		}
		if (value instanceof Duration dur) {
			return "Value::Duration(" + rustString(dur.toString()) + ".into())";
		}
		if (value instanceof List<?> list) {
			StringBuilder sb = new StringBuilder("Value::List(vec![");
			for (Object item : list) {
				sb.append(renderValue(item)).append(',');
			}
			sb.append("])");
			return sb.toString();
		}
		if (value instanceof Map<?, ?> map) {
			StringBuilder sb = new StringBuilder("Value::Record(std::collections::BTreeMap::from([");
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				sb.append('(').append(rustString(String.valueOf(entry.getKey()))).append(".into(), ")
						.append(renderValue(entry.getValue())).append("),");
			}
			sb.append("]))");
			return sb.toString();
		}
		throw new IllegalArgumentException("Unsupported TCK value type: " + value.getClass());
	}

	private static String formatLocalTime(LocalTime t) {
		String base = String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
		if (t.getNano() == 0) {
			return base;
		}
		String frac = String.format("%09d", t.getNano()).replaceAll("0+$", "");
		return base + "." + frac;
	}

	private static String rustString(String s) {
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '\\' -> sb.append("\\\\");
				case '"' -> sb.append("\\\"");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u{%04x}", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		sb.append('"');
		return sb.toString();
	}

	private static String escapeFormat(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("{", "{{").replace("}", "}}");
	}

	private static String sanitize(String s) {
		return s == null ? "" : s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
	}
}

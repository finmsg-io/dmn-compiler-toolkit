package io.finmsg.dmn.compiler.dmnir;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DmnIrDateParityTest {
	@Test
	void constantDatesRetainCalendarAndExtremeYearsThroughExport() throws Exception {
		for (String date : List.of("-999999999-01-01", "-0400-02-29", "-0001-01-01", "0001-01-01", "1900-03-01",
				"2000-02-29", "2024-02-29", "9999-12-31", "10000-01-01", "999999999-12-31")) {
			var source = scalar("date(&quot;" + date + "&quot;)", "");
			var verified = new DmnIrScalarProducer().produce(source);
			assertEquals(4, verified.artifact().getSchemaVersion().getMinor());
			var value = verified.artifact().getModels(0).getDecisions(0).getExpression().getLiteral().getDateValue();
			var expected = LocalDate.of(value.getYear(), value.getMonth(), value.getDay());
			assertEquals(expected,
					new DmnIrRuntimeAdapter().adapt(verified).evaluate(Map.of()).values().iterator().next());
			var bundle = new DmnIrScalarDmnExporter().export(verified);
			validate(bundle);
			assertTrue(bundle.documents().get(bundle.rootDocument()).contains("date(\"" + date + "\")"));
			for (var options : List.of(DmnCompilerOptions.lowered(), DmnCompilerOptions.optimized())) {
				var direct = new DmnCompiler().compile(source, new InMemoryDmnModelResolver(List.of()), options);
				var exported = compile(bundle, options);
				assertTrue(direct.isSuccess(), direct.diagnostics().toString());
				assertEquals(expected, evaluate(direct, "Answer", Map.of()));
				assertEquals(expected, evaluate(exported, "Answer", Map.of()));
			}
			assertArrayEquals(verified.artifact().toByteArray(),
					new DmnIrScalarProducer().produce(source).artifact().toByteArray());
		}
		var literal = new DmnIrScalarProducer().produce(scalar("@&quot;2024-02-29&quot;", ""));
		assertEquals(LocalDate.of(2024, 2, 29),
				new DmnIrRuntimeAdapter().adapt(literal).evaluate(Map.of()).values().iterator().next());
		for (String spelling : List.of("-0400-02-29", "-0001-01-01", "10000-01-01", "999999999-12-31")) {
			var source = scalar("@&quot;" + spelling + "&quot;", "");
			var verified = new DmnIrScalarProducer().produce(source);
			var expected = new DmnIrRuntimeAdapter().adapt(verified).evaluate(Map.of()).values().iterator().next();
			for (var options : List.of(DmnCompilerOptions.lowered(), DmnCompilerOptions.optimized())) {
				var compiled = new DmnCompiler().compile(source, new InMemoryDmnModelResolver(List.of()), options);
				assertTrue(compiled.isSuccess(), compiled.diagnostics().toString());
				assertEquals(expected, evaluate(compiled, "Answer", Map.of()));
			}
		}
	}
	@Test
	void bundledPolicyUsesImportedTypesDateDecisionsNestedRecordsAndLocalScopes() throws Exception {
		var sources = policy();
		var resolver = new InMemoryDmnModelResolver(sources);
		var verified = new DmnIrScalarProducer().produce(sources.get(0), resolver);
		assertEquals(2, verified.artifact().getModelsCount());
		var adapted = new DmnIrRuntimeAdapter().adapt(verified);
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		validate(bundle);
		assertEquals(bundle.documents(), new DmnIrScalarDmnExporter().export(verified).documents());
		for (var options : List.of(DmnCompilerOptions.lowered(), DmnCompilerOptions.optimized())) {
			var direct = new DmnCompiler().compile(sources.get(0), resolver, options);
			assertTrue(direct.isSuccess(), direct.diagnostics().toString());
			var exported = compile(bundle, options);
			for (Object date : Arrays.asList(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1),
					LocalDate.of(10000, 1, 1), null, "2024-01-01")) {
				Map<String, Object> fields = new LinkedHashMap<>();
				fields.put("Expiry", date);
				var applicant = Map.of("Profile", fields);
				var values = adapted.evaluate(Map.of(key("urn:policy", "applicant"), applicant));
				for (String decision : List.of("Eligible", "Effective", "SameDay")) {
					var actual = values.get(key("urn:policy", decision));
					assertEquals(evaluate(direct, decision, Map.of("Applicant", applicant)), actual);
					assertEquals(evaluate(exported, decision, Map.of("Applicant", applicant)), actual);
				}
			}
		}
		assertNull(adapted.evaluate(Map.of(key("urn:policy", "applicant"), Map.of("Profile", Map.of())))
				.get(key("urn:policy", "Effective")));
	}
	@Test
	void rejectsUnreviewedDateConstructorsAndRetainsUnusedDateDeclarations() {
		for (String expression : List.of("date(Value)", "date(&quot;0000-01-01&quot;)",
				"date(&quot;+10000-01-01&quot;)", "date(&quot;-00001-01-01&quot;)", "date(&quot;2023-02-29&quot;)",
				"date(2024, 2, 29)")) {
			var source = scalar(expression,
					"<inputData id=\"value\" name=\"Value\"><variable name=\"Value\" typeRef=\"string\"/></inputData>");
			assertEquals(DmnIrProductionException.Category.UNSUPPORTED_PROFILE,
					assertThrows(DmnIrProductionException.class, () -> new DmnIrScalarProducer().produce(source))
							.category());
		}
		var source = scalar("date(&quot;2024-01-01&quot;)",
				"<itemDefinition id=\"unused\" name=\"Unused\"><itemComponent name=\"When\"><typeRef>date</typeRef></itemComponent></itemDefinition>");
		var verified = new DmnIrScalarProducer().produce(source);
		assertEquals(DmnIrScalarType.SCALAR_TYPE_DATE,
				verified.artifact().getModels(0).getRecordDefinitions(0).getFields(0).getType().getScalarType());
	}
	@Test
	void separateConsumerRunsTheBundledArtifactInDifferentTimezones(@TempDir Path dir) throws Exception {
		var sources = policy();
		var file = dir.resolve("policy.dmnir");
		Files.write(file, new DmnIrScalarProducer().produce(sources.get(0), new InMemoryDmnModelResolver(sources))
				.artifact().toByteArray());
		for (String zone : List.of("Pacific/Kiritimati", "America/Los_Angeles")) {
			var log = dir.resolve(zone.replace('/', '_') + ".log");
			String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
			var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", exe).toString(),
					"-Duser.timezone=" + zone, "-cp",
					System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
					Consumer.class.getName(), file.toString()).redirectErrorStream(true).redirectOutput(log.toFile())
					.start();
			try {
				assertTrue(process.waitFor(30, TimeUnit.SECONDS));
				assertEquals(0, process.exitValue(), Files.readString(log));
			} finally {
				process.destroyForcibly();
			}
		}
	}
	public static final class Consumer {
		public static void main(String[] args) throws Exception {
			var date = LocalDate.of(10000, 1, 1);
			var model = new DmnIrRuntimeAdapter()
					.adapt(new DmnIrArtifactReader().read(Files.readAllBytes(Path.of(args[0]))));
			var result = model
					.evaluate(Map.of(key("urn:policy", "applicant"), Map.of("Profile", Map.of("Expiry", date))));
			if (!date.equals(result.get(key("urn:policy", "Effective"))))
				throw new AssertionError(result);
		}
	}
	private static Object evaluate(DmnCompilationResult result, String decision, Map<String, Object> inputs) {
		var model = result.compiledModel().orElseThrow();
		Map<Integer, Object> slots = new HashMap<>();
		inputs.forEach((name, value) -> slots.put(model.inputSlots().get(name), value));
		return new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model.optimizedModel().model(), slots)
				.value(model.decisionSlots().get(decision));
	}
	private static DmnCompilationResult compile(DmnIrScalarDmnExporter.ExportedDmnBundle bundle,
			DmnCompilerOptions options) {
		var sources = bundle.documents().entrySet().stream().map(e -> source(e.getKey(), e.getValue())).toList();
		var result = new DmnCompiler().compile(
				source(bundle.rootDocument(), bundle.documents().get(bundle.rootDocument())),
				new InMemoryDmnModelResolver(sources), options);
		assertTrue(result.isSuccess(), result.diagnostics().toString());
		return result;
	}
	private static DmnIrRuntimeAdapter.ElementId key(String ns, String id) {
		return new DmnIrRuntimeAdapter.ElementId(ns, id);
	}
	private static DmnSource source(String name, String xml) {
		return new DmnSource(DmnSourceId.of("memory:/" + name), xml.getBytes(StandardCharsets.UTF_8));
	}
	private static DmnSource scalar(String expression, String declarations) {
		return source("date.dmn",
				"<definitions xmlns=\"https://www.omg.org/spec/DMN/20240513/MODEL/\" id=\"model\" name=\"Dates\" namespace=\"urn:dates\">"
						+ declarations
						+ "<decision id=\"answer\" name=\"Answer\"><variable name=\"Answer\" typeRef=\"date\"/>"
						+ (declarations.contains("<inputData")
								? "<informationRequirement><requiredInput href=\"#value\"/></informationRequirement>"
								: "")
						+ "<literalExpression><text>" + expression
						+ "</text></literalExpression></decision></definitions>");
	}
	private static List<DmnSource> policy() {
		var calendar = source("calendar.dmn",
				"""
						<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="calendar" name="Calendar" namespace="urn:calendar">
						<itemDefinition id="validity" name="tValidity"><itemComponent name="Expiry"><typeRef>date</typeRef></itemComponent></itemDefinition>
						<decision id="cutoff" name="Cutoff"><variable name="Cutoff" typeRef="date"/><literalExpression><text>date("2024-01-01")</text></literalExpression></decision></definitions>
						""");
		var policy = source("policy.dmn",
				"""
						<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" xmlns:cal="urn:calendar" id="policy" name="Policy" namespace="urn:policy">
						<import name="cal" namespace="urn:calendar" locationURI="calendar.dmn" importType="DMN"/>
						<itemDefinition id="applicant-type" name="tApplicant"><itemComponent name="Profile"><typeRef>cal:tValidity</typeRef></itemComponent></itemDefinition>
						<inputData id="applicant" name="Applicant"><variable name="Applicant" typeRef="tApplicant"/></inputData>
						<decision id="Eligible" name="Eligible"><variable name="Eligible" typeRef="boolean"/><informationRequirement><requiredInput href="#applicant"/></informationRequirement><informationRequirement><requiredDecision href="urn:calendar#cutoff"/></informationRequirement>
						<context><contextEntry><variable name="Expiry" typeRef="date"/><literalExpression><text>Applicant.Profile.Expiry</text></literalExpression></contextEntry>
						<contextEntry><literalExpression><text>Expiry &gt;= cal.Cutoff</text></literalExpression></contextEntry></context></decision>
						<decision id="Effective" name="Effective"><variable name="Effective" typeRef="date"/><informationRequirement><requiredInput href="#applicant"/></informationRequirement><informationRequirement><requiredDecision href="#Eligible"/></informationRequirement>
						<literalExpression><text>if Eligible then Applicant.Profile.Expiry else null</text></literalExpression></decision>
						<decision id="SameDay" name="SameDay"><variable name="SameDay" typeRef="boolean"/><informationRequirement><requiredInput href="#applicant"/></informationRequirement><informationRequirement><requiredDecision href="urn:calendar#cutoff"/></informationRequirement>
						<literalExpression><text>Applicant.Profile.Expiry = cal.Cutoff</text></literalExpression></decision></definitions>
						""");
		return List.of(policy, calendar);
	}
	private static void validate(DmnIrScalarDmnExporter.ExportedDmnBundle bundle) throws Exception {
		var factory = javax.xml.validation.SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
		factory.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
		var schema = factory
				.newSchema(new java.io.File(DmnIrDateParityTest.class.getResource("/dmn16-schema/DMN16.xsd").toURI()));
		for (var xml : bundle.documents().values()) {
			var validator = schema.newValidator();
			validator.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			validator.validate(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xml)));
		}
	}
}

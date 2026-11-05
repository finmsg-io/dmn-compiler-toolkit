package io.finmsg.dmn.compiler.dmnir;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DmnIrRecordParityTest {
	private static final String NS = "urn:example:record-model";

	@Test
	void producesAndExecutesRecordFieldsAndExportsNamedDefinitions() throws Exception {
		var source = source(false);
		var verified = new DmnIrScalarProducer().produce(source);
		assertEquals(2, verified.artifact().getSchemaVersion().getMinor());
		var adapted = new DmnIrRuntimeAdapter()
				.adapt(new DmnIrArtifactReader().read(verified.artifact().toByteArray()));
		assertEquals(2, verified.artifact().getModels(0).getRecordDefinitionsCount());
		var driver = verified.artifact().getModels(0).getRecordDefinitionsList().stream()
				.filter(r -> r.getIdentity().getName().equals("tDriver")).findFirst().orElseThrow();
		assertEquals(List.of("Name", "Points"),
				driver.getFieldsList().stream().map(DmnIrRecordField::getName).toList());
		assertEquals("points-field", driver.getFields(1).getId());
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		validate(bundle);
		var exported = exported(bundle);
		List<Map<String, Object>> cases = new ArrayList<>();
		cases.add(values(Map.of("Name", "Synthetic", "Points", new BigDecimal("15.0001")),
				Map.of("Points", new BigDecimal("7"))));
		cases.add(values(Map.of("Name", "Synthetic", "Points", BigDecimal.ONE, "Extra", true),
				Map.of("Points", BigDecimal.TEN)));
		cases.add(values(Map.of("Name", "Synthetic"), Map.of("Points", BigDecimal.TEN)));
		cases.add(values(Map.of("Name", "Synthetic", "Points", "invalid"), Map.of("Points", BigDecimal.TEN)));
		cases.add(values(null, Map.of("Points", BigDecimal.TEN)));
		Map<String, Object> nullable = new LinkedHashMap<>();
		nullable.put("Name", "Synthetic");
		nullable.put("Points", null);
		cases.add(values(nullable, Map.of("Points", BigDecimal.TEN)));
		for (var options : List.of(DmnCompilerOptions.lowered(), DmnCompilerOptions.optimized())) {
			var direct = new DmnCompiler().compile(source, new InMemoryDmnModelResolver(List.of()), options);
			var recompiled = new DmnCompiler().compile(exported, new InMemoryDmnModelResolver(List.of()), options);
			assertTrue(direct.isSuccess(), direct.diagnostics().toString());
			assertTrue(recompiled.isSuccess(), recompiled.diagnostics().toString());
			for (var inputs : cases) {
				Map<DmnIrRuntimeAdapter.ElementId, Object> addressed = new LinkedHashMap<>();
				inputs.forEach((k, v) -> addressed.put(key(k), v));
				var actual = adapted.evaluate(addressed);
				for (String decision : List.of("Total", "Copy", "Suspend")) {
					equal(runtimeDecision(direct, decision, inputs), actual.get(key(decision)));
					equal(runtimeDecision(recompiled, decision, inputs), actual.get(key(decision)));
				}
			}
		}
		assertEquals(bundle.documents(), new DmnIrScalarDmnExporter().export(verified).documents());
		assertArrayEquals(verified.artifact().toByteArray(),
				new DmnIrScalarProducer().produce(source).artifact().toByteArray());
	}

	@Test
	void supportsNestedNamedRecordsWithoutInferringIdentityFromShape() throws Exception {
		var verified = new DmnIrScalarProducer().produce(source(true));
		var adapted = new DmnIrRuntimeAdapter().adapt(verified);
		var result = adapted.evaluate(
				Map.of(key("Driver"), Map.of("Profile", Map.of("Name", "Synthetic", "Points", new BigDecimal("12"))),
						key("Fine"), Map.of("Points", new BigDecimal("8"))));
		assertEquals(new BigDecimal("20"), result.get(key("Total")));
		assertEquals("Yes", result.get(key("Suspend")));
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		validate(bundle);
		var recompiled = new DmnCompiler().compile(exported(bundle));
		assertTrue(recompiled.isSuccess(), recompiled.diagnostics().toString());
		equal(result.get(key("Total")),
				recompiled.compiledModel().orElseThrow().evaluateDecision("Total",
						Map.of("Driver", Map.of("Profile", Map.of("Name", "Synthetic", "Points", new BigDecimal("12"))),
								"Fine", Map.of("Points", new BigDecimal("8")))));
	}

	@Test
	void rejectsUnsupportedConstraintsTemporalFieldsAndRecordConstruction() {
		var source = source(false);
		String xml = new String(source.content(), StandardCharsets.UTF_8);
		for (String changed : List.of(xml.replace("<typeRef>string</typeRef>", "<typeRef>date</typeRef>"),
				xml.replace("<typeRef>number</typeRef>",
						"<typeRef>number</typeRef><allowedValues><text>&gt; 0</text></allowedValues>"),
				xml.replace("<text>Driver</text>", "<text>{Name: \"Synthetic\", Points: 1}</text>"))) {
			var error = assertThrows(DmnIrProductionException.class, () -> new DmnIrScalarProducer()
					.produce(new DmnSource(source.id(), changed.getBytes(StandardCharsets.UTF_8))));
			assertEquals(DmnIrProductionException.Category.UNSUPPORTED_PROFILE, error.category(),
					error.diagnostics().toString());
		}
	}

	@Test
	void separateJvmConsumesOnlyTheRecordArtifact(@TempDir Path dir) throws Exception {
		var file = dir.resolve("records.dmnir");
		Files.write(file, new DmnIrScalarProducer().produce(source(false)).artifact().toByteArray());
		var output = dir.resolve("consumer.log");
		String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
		var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", exe).toString(), "-cp",
				System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
				Consumer.class.getName(), file.toString()).redirectErrorStream(true).redirectOutput(output.toFile())
				.start();
		try {
			assertTrue(process.waitFor(30, TimeUnit.SECONDS));
			assertEquals(0, process.exitValue(), Files.readString(output));
		} finally {
			process.destroyForcibly();
		}
	}

	@Test
	void importedRecordTypesKeepNamespaceIdentityAcrossDmnExport() throws Exception {
		var original = source(false);
		String xml = new String(original.content(), StandardCharsets.UTF_8);
		String external = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="types-model" name="Types" namespace="urn:example:types">
				<itemDefinition id="fine-type" name="tDriver"><itemComponent name="Name"><typeRef>string</typeRef></itemComponent>
				<itemComponent name="Points"><typeRef>number</typeRef></itemComponent></itemDefinition></definitions>
				""";
		xml = xml.replaceFirst("(?s)<itemDefinition id=\"driver-type\".*?</itemDefinition>", "")
				.replace("<definitions xmlns=", "<definitions xmlns:types=\"urn:example:types\" xmlns=")
				.replace("typeRef=\"tDriver\"", "typeRef=\"types:tDriver\"").replace("<itemDefinition id=\"fine-type\"",
						"<import name=\"types\" namespace=\"urn:example:types\" locationURI=\"types.dmn\" importType=\"DMN\"/><itemDefinition id=\"fine-type\"");
		var source = new DmnSource(original.id(), xml.getBytes(StandardCharsets.UTF_8));
		var types = new DmnSource(DmnSourceId.of("memory:/types.dmn"), external.getBytes(StandardCharsets.UTF_8));
		var verified = new DmnIrScalarProducer().produce(source, new InMemoryDmnModelResolver(List.of(types)));
		var root = verified.artifact().getModelsList().stream().filter(m -> m.getNamespace().equals(NS)).findFirst()
				.orElseThrow();
		var driver = root.getInputsList().stream().filter(i -> i.getIdentity().getId().equals("Driver")).findFirst()
				.orElseThrow();
		assertEquals("fine-type", driver.getVariable().getRecordType().getElementId());
		assertEquals("urn:example:types", driver.getVariable().getRecordType().getModelNamespace());
		var values = values(Map.of("Name", "Synthetic", "Points", new BigDecimal("15")),
				Map.of("Points", new BigDecimal("7")));
		Map<DmnIrRuntimeAdapter.ElementId, Object> addressed = new LinkedHashMap<>();
		values.forEach((k, v) -> addressed.put(key(k), v));
		var result = new DmnIrRuntimeAdapter().adapt(verified).evaluate(addressed);
		assertEquals(new BigDecimal("22"), result.get(key("Total")));
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		validate(bundle);
		var sources = bundle.documents().entrySet().stream()
				.map(e -> new DmnSource(DmnSourceId.of("memory:/" + e.getKey()),
						e.getValue().getBytes(StandardCharsets.UTF_8)))
				.toList();
		var compiled = new DmnCompiler().compile(exported(bundle), new InMemoryDmnModelResolver(sources));
		assertTrue(compiled.isSuccess(), compiled.diagnostics().toString());
		equal(result.get(key("Total")), runtimeDecision(compiled, "Total", values));
	}

	@Test
	void recordConditionalCanReturnItsNamedTypeOrNull() {
		var original = source(false);
		String xml = new String(original.content(), StandardCharsets.UTF_8).replace("<text>Driver</text>",
				"<text>if true then Driver else null</text>");
		var verified = new DmnIrScalarProducer()
				.produce(new DmnSource(original.id(), xml.getBytes(StandardCharsets.UTF_8)));
		var copy = verified.artifact().getModels(0).getDecisionsList().stream()
				.filter(d -> d.getIdentity().getId().equals("Copy")).findFirst().orElseThrow();
		assertEquals(copy.getVariable().getRecordType(), copy.getExpression().getRecordType());
		assertTrue(copy.getExpression().hasConditional());
		var input = Map.of("Name", "Synthetic", "Points", new BigDecimal("15"));
		var result = new DmnIrRuntimeAdapter().adapt(verified)
				.evaluate(Map.of(key("Driver"), input, key("Fine"), Map.of("Points", BigDecimal.ONE)));
		equal(input, result.get(key("Copy")));
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		var compiled = new DmnCompiler().compile(exported(bundle));
		assertTrue(compiled.isSuccess(), compiled.diagnostics().toString());
		equal(input, runtimeDecision(compiled, "Copy", values(input, Map.of("Points", BigDecimal.ONE))));
	}
	public static class Consumer {
		public static void main(String[] args) throws Exception {
			var verified = new DmnIrArtifactReader().read(Files.readAllBytes(Path.of(args[0])));
			var result = new DmnIrRuntimeAdapter().adapt(verified)
					.evaluate(Map.of(key("Driver"), Map.of("Name", "Synthetic", "Points", new BigDecimal("15")),
							key("Fine"), Map.of("Points", new BigDecimal("7"))));
			if (!new BigDecimal("22").equals(result.get(key("Total"))))
				throw new AssertionError(result);
		}
	}

	@Test
	void nullTypedFieldReadMatchesSourceLoweringEvenWithNonNullNativeData() {
		var original = source(false);
		String xml = new String(original.content(), StandardCharsets.UTF_8)
				.replace("<typeRef>string</typeRef>", "<typeRef>null</typeRef>").replace("</definitions>",
						"<decision id=\"Nil\" name=\"Nil\"><variable name=\"Nil\" typeRef=\"null\"/><informationRequirement><requiredInput href=\"#Driver\"/></informationRequirement><literalExpression><text>Driver.Name</text></literalExpression></decision></definitions>");
		var source = new DmnSource(original.id(), xml.getBytes(StandardCharsets.UTF_8));
		var verified = new DmnIrScalarProducer().produce(source);
		var inputs = values(Map.of("Name", false, "Points", BigDecimal.ONE), Map.of("Points", BigDecimal.TEN));
		Map<DmnIrRuntimeAdapter.ElementId, Object> addressed = new LinkedHashMap<>();
		inputs.forEach((k, v) -> addressed.put(key(k), v));
		var result = new DmnIrRuntimeAdapter().adapt(verified).evaluate(addressed);
		assertNull(result.get(key("Nil")));
		var direct = new DmnCompiler().compile(source);
		assertTrue(direct.isSuccess(), direct.diagnostics().toString());
		equal(runtimeDecision(direct, "Nil", inputs), result.get(key("Nil")));
		var exported = new DmnCompiler().compile(exported(new DmnIrScalarDmnExporter().export(verified)));
		assertTrue(exported.isSuccess(), exported.diagnostics().toString());
		equal(runtimeDecision(exported, "Nil", inputs), result.get(key("Nil")));
	}

	private static void validate(DmnIrScalarDmnExporter.ExportedDmnBundle bundle) throws Exception {

		var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
		var schema = factory.newSchema(
				new java.io.File(DmnIrRecordParityTest.class.getResource("/dmn16-schema/DMN16.xsd").toURI()));
		for (String xml : bundle.documents().values()) {
			var validator = schema.newValidator();
			validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			validator.validate(new StreamSource(new java.io.StringReader(xml)));
		}
	}
	private static DmnSource exported(DmnIrScalarDmnExporter.ExportedDmnBundle bundle) {
		return new DmnSource(DmnSourceId.of("memory:/" + bundle.rootDocument()),
				bundle.documents().get(bundle.rootDocument()).getBytes(StandardCharsets.UTF_8));
	}
	private static Object runtimeDecision(DmnCompilationResult compilation, String decision,
			Map<String, Object> inputs) {
		var model = compilation.compiledModel().orElseThrow();
		Map<Integer, Object> slots = new HashMap<>();
		inputs.forEach((name, value) -> slots.put(model.inputSlots().get(name), value));
		return new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model.optimizedModel().model(), slots)
				.value(model.decisionSlots().get(decision));
	}

	private static void equal(Object expected, Object actual) {
		if (expected instanceof BigDecimal n)
			assertEquals(0, n.compareTo((BigDecimal) actual));
		else
			assertEquals(expected, actual);
	}
	private static DmnIrRuntimeAdapter.ElementId key(String id) {
		return new DmnIrRuntimeAdapter.ElementId(NS, id);
	}
	private static Map<String, Object> values(Object driver, Object fine) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("Driver", driver);
		values.put("Fine", fine);
		return values;
	}
	private static DmnSource source(boolean nested) {
		String record = """
				<itemDefinition id="driver-type" name="tDriver">
				<itemComponent id="name-field" name="Name"><typeRef>string</typeRef></itemComponent>
				<itemComponent id="points-field" name="Points"><typeRef>number</typeRef></itemComponent></itemDefinition>
				<itemDefinition id="fine-type" name="tFine"><itemComponent name="Points"><typeRef>number</typeRef></itemComponent></itemDefinition>
				""";
		String inputType = "tDriver", path = "Driver.Points";
		if (nested) {
			record += "<itemDefinition id=\"nested-type\" name=\"tNested\"><itemComponent name=\"Profile\"><typeRef>tDriver</typeRef></itemComponent></itemDefinition>";
			inputType = "tNested";
			path = "Driver.Profile.Points";
		}
		String xml = "<definitions xmlns=\"https://www.omg.org/spec/DMN/20240513/MODEL/\" id=\"model\" name=\"Records\" namespace=\""
				+ NS + "\">" + record
				+ """
						<inputData id="Driver" name="Driver"><variable id="driver-var" name="Driver" typeRef="%s"/></inputData>
						<inputData id="Fine" name="Fine"><variable name="Fine" typeRef="tFine"/></inputData>
						<decision id="Total" name="Total"><variable name="Total" typeRef="number"/>
						<informationRequirement><requiredInput href="#Driver"/></informationRequirement><informationRequirement><requiredInput href="#Fine"/></informationRequirement>
						<literalExpression><text>%s + Fine.Points</text></literalExpression></decision>
						<decision id="Copy" name="Copy"><variable name="Copy" typeRef="%s"/><informationRequirement><requiredInput href="#Driver"/></informationRequirement>
						<literalExpression><text>Driver</text></literalExpression></decision>
						<decision id="Suspend" name="Suspend"><variable name="Suspend" typeRef="string"/><informationRequirement><requiredDecision href="#Total"/></informationRequirement>
						<literalExpression><text>if Total &gt;= 20 then "Yes" else "No"</text></literalExpression></decision></definitions>
						"""
						.formatted(inputType, path, inputType);
		return new DmnSource(DmnSourceId.of("memory:/records.dmn"), xml.getBytes(StandardCharsets.UTF_8));
	}
}

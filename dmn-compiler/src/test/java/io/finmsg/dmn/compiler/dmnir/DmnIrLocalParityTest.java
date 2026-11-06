package io.finmsg.dmn.compiler.dmnir;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DmnIrLocalParityTest {
	private static final String NS = "urn:example:locals";
	@Test
	void sequentialAndNestedContextsMatchSourceAndExport() throws Exception {
		for (boolean nested : List.of(false, true)) {
			var source = source(nested);
			var verified = new DmnIrScalarProducer().produce(source);
			assertEquals(3, verified.artifact().getSchemaVersion().getMinor());
			assertArrayEquals(verified.artifact().toByteArray(),
					new DmnIrScalarProducer().produce(source).artifact().toByteArray());
			var adapted = new DmnIrRuntimeAdapter().adapt(verified);
			var bundle = new DmnIrScalarDmnExporter().export(verified);
			validate(bundle);
			var exported = new DmnSource(DmnSourceId.of("memory:/" + bundle.rootDocument()),
					bundle.documents().get(bundle.rootDocument()).getBytes(StandardCharsets.UTF_8));
			for (var options : List.of(DmnCompilerOptions.lowered(), DmnCompilerOptions.optimized())) {
				var direct = new DmnCompiler().compile(source, new InMemoryDmnModelResolver(List.of()), options);
				var roundtrip = new DmnCompiler().compile(exported, new InMemoryDmnModelResolver(List.of()), options);
				assertTrue(direct.isSuccess(), direct.diagnostics().toString());
				assertTrue(roundtrip.isSuccess(), roundtrip.diagnostics().toString());
				for (Object points : Arrays.asList(BigDecimal.ZERO, new BigDecimal("15"), new BigDecimal("25.0001"),
						null)) {
					Map<String, Object> fields = new LinkedHashMap<>();
					fields.put("Points", points);
					var inputs = Map.<String, Object>of("Driver", fields);
					var result = adapted.evaluate(Map.of(key("Driver"), fields)).get(key("Suspend"));
					assertEquals(evaluate(direct, inputs), result);
					assertEquals(evaluate(roundtrip, inputs), result);
				}
			}
			assertEquals(bundle.documents(), new DmnIrScalarDmnExporter().export(verified).documents());
			assertEquals(List.of("Driver"), verified.artifact().getModels(0).getDecisions(0).getDependenciesList()
					.stream().map(DmnIrReference::getElementId).toList());
		}
	}
	@Test
	void rejectsContextWithoutFinalResult() {
		var xml = new String(source(false).content(), StandardCharsets.UTF_8).replace(
				"<contextEntry><literalExpression><text>if TotalPoints &gt;= 20 then &quot;Yes&quot; else &quot;No&quot;</text></literalExpression></contextEntry>",
				"");
		assertThrows(DmnIrProductionException.class, () -> new DmnIrScalarProducer()
				.produce(new DmnSource(DmnSourceId.of("memory:/missing.dmn"), xml.getBytes(StandardCharsets.UTF_8))));
	}
	@Test
	void recordValuedLocalRetainsNamedTypeAndFieldAccess() {
		String xml = new String(source(false).content(), StandardCharsets.UTF_8)
				.replace("name=\"TotalPoints\" typeRef=\"number\"", "name=\"TotalPoints\" typeRef=\"tDriver\"")
				.replace("Driver.Points + 7", "Driver")
				.replace("if TotalPoints &gt;= 20", "if TotalPoints.Points &gt;= 20");
		var verified = new DmnIrScalarProducer().produce(
				new DmnSource(DmnSourceId.of("memory:/record-local.dmn"), xml.getBytes(StandardCharsets.UTF_8)));
		var binding = verified.artifact().getModels(0).getDecisions(0).getExpression().getContext().getBindings(0);
		assertTrue(binding.getVariable().hasRecordType());
		var result = new DmnIrRuntimeAdapter().adapt(verified)
				.evaluate(Map.of(key("Driver"), Map.of("Points", new BigDecimal("25"))));
		assertEquals("Yes", result.get(key("Suspend")));
		assertDoesNotThrow(() -> new DmnIrScalarDmnExporter().export(verified));
	}
	@Test
	void identityBasedShadowingExecutesButExportRejectsHiddenOuterName() {
		var artifact = new DmnIrScalarProducer().produce(source(false)).artifact().toBuilder();
		var decision = artifact.getModelsBuilder(0).getDecisionsBuilder(0);
		var context = decision.getExpressionBuilder().getContextBuilder();
		var outer = context.getBindings(0);
		var local = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER)
				.setLocalReference(DmnIrLocalReference.newBuilder().setBindingId(outer.getBindingId())).build();
		var shadow = outer.toBuilder().setBindingId("shadow").setVariable(outer.getVariable().toBuilder().clearId())
				.setValue(
						DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER)
								.setLiteral(DmnIrLiteral.newBuilder()
										.setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("0").setScale(0))))
				.build();
		context.setResult(DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER)
				.setContext(DmnIrContextExpression.newBuilder().addBindings(shadow).setResult(local)));
		decision.getExpressionBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER);
		decision.getVariableBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER);
		var verified = new DmnIrArtifactReader().read(artifact.build().toByteArray());
		assertEquals(new BigDecimal("22"), new DmnIrRuntimeAdapter().adapt(verified)
				.evaluate(Map.of(key("Driver"), Map.of("Points", new BigDecimal("15")))).get(key("Suspend")));
		assertThrows(DmnIrExportException.class, () -> new DmnIrScalarDmnExporter().export(verified));
	}
	@Test
	void separateJvmConsumesContextArtifact(@TempDir Path dir) throws Exception {
		var file = dir.resolve("model.dmnir");
		Files.write(file, new DmnIrScalarProducer().produce(source(true)).artifact().toByteArray());
		var log = dir.resolve("consumer.log");
		String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
		var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", exe).toString(), "-cp",
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
	public static final class Consumer {
		public static void main(String[] args) throws Exception {
			var model = new DmnIrRuntimeAdapter()
					.adapt(new DmnIrArtifactReader().read(Files.readAllBytes(Path.of(args[0]))));
			if (!"Yes".equals(
					model.evaluate(Map.of(key("Driver"), Map.of("Points", new BigDecimal("15")))).get(key("Suspend"))))
				throw new AssertionError("Wrong result");
		}
	}
	private static DmnIrRuntimeAdapter.ElementId key(String id) {
		return new DmnIrRuntimeAdapter.ElementId(NS, id);
	}
	private static DmnSource source(boolean nested) {
		String value = nested
				? "<context><contextEntry><variable id=\"nested-var\" name=\"Bonus\" typeRef=\"number\"/><literalExpression><text>7</text></literalExpression></contextEntry><contextEntry><literalExpression><text>Driver.Points + Bonus</text></literalExpression></contextEntry></context>"
				: "<literalExpression><text>Driver.Points + 7</text></literalExpression>";
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="model" name="Locals" namespace="urn:example:locals">
				<itemDefinition id="driver-type" name="tDriver"><itemComponent name="Points"><typeRef>number</typeRef></itemComponent></itemDefinition>
				<inputData id="Driver" name="Driver"><variable name="Driver" typeRef="tDriver"/></inputData>
				<decision id="Suspend" name="Suspend"><variable name="Suspend" typeRef="string"/><informationRequirement><requiredInput href="#Driver"/></informationRequirement>
				<context><contextEntry><variable id="total-var" name="TotalPoints" typeRef="number"/>%s</contextEntry>
				<contextEntry><literalExpression><text>if TotalPoints &gt;= 20 then &quot;Yes&quot; else &quot;No&quot;</text></literalExpression></contextEntry></context></decision></definitions>
				"""
				.formatted(value);
		return new DmnSource(DmnSourceId.of("memory:/locals.dmn"), xml.getBytes(StandardCharsets.UTF_8));
	}
	private static Object evaluate(DmnCompilationResult result, Map<String, Object> inputs) {
		var model = result.compiledModel().orElseThrow();
		Map<Integer, Object> slots = new HashMap<>();
		inputs.forEach((name, value) -> slots.put(model.inputSlots().get(name), value));
		return new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model.optimizedModel().model(), slots)
				.value(model.decisionSlots().get("Suspend"));
	}
	private static void validate(DmnIrScalarDmnExporter.ExportedDmnBundle bundle) throws Exception {
		var factory = javax.xml.validation.SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
		factory.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
		var schema = factory.newSchema(Path.of("src/test/resources/dmn16-schema/DMN16.xsd").toFile());
		for (var xml : bundle.documents().values())
			schema.newValidator().validate(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xml)));
	}
}

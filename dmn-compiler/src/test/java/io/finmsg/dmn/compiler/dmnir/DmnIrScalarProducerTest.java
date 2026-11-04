package io.finmsg.dmn.compiler.dmnir;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class DmnIrScalarProducerTest {
	private static final String NS = "urn:example:producer";

	@Test
	void producesReloadableArtifactWithExactValuesAndSourceIdentities() {
		var source = source(decision("precise", "Precise", "number", "9007199254740993.0001")
				+ decision("empty", "Empty", "string", "\"\"") + decision("false", "False", "boolean", "false")
				+ decision("null", "Nullable", "number", "null"));
		byte[] bytes = new DmnIrScalarProducer().produce(source).artifact().toByteArray();
		var adapted = new DmnIrRuntimeAdapter().adapt(new DmnIrArtifactReader().read(bytes));
		var values = adapted.evaluate(Map.of());
		assertEquals(new BigDecimal("9007199254740993.0001"), values.get(key("precise")));
		assertEquals("", values.get(key("empty")));
		assertEquals(false, values.get(key("false")));
		assertTrue(values.containsKey(key("null")));
		assertNull(values.get(key("null")));
		assertEquals(Optional.of("precise-var"), adapted.decisions().get(key("precise")).variableId());
		for (var options : List.of(DmnCompilerOptions.lowered(), DmnCompilerOptions.optimized())) {
			var compiled = new DmnCompiler().compile(source, new InMemoryDmnModelResolver(List.of()), options);
			assertTrue(compiled.isSuccess(), compiled.diagnostics().toString());
			for (var binding : adapted.decisions().values()) {
				Object expected = compiled.compiledModel().orElseThrow().evaluateDecision(binding.variableName(),
						Map.of());
				Object actual = values.get(binding.id());
				if (expected instanceof BigDecimal number)
					assertEquals(0, number.compareTo((BigDecimal) actual));
				else
					assertEquals(expected, actual);
			}
		}
	}

	@Test
	void declarationReorderingProducesIdenticalBytesAndResolvedInputReference() {
		String input = "<inputData id=\"input\" name=\"Amount\"><variable id=\"input-var\" name=\"Amount\" typeRef=\"number\"/></inputData>";
		String ref = "<decision id=\"copy\" name=\"Copy\"><variable name=\"Copy\" typeRef=\"number\"/>"
				+ "<informationRequirement><requiredInput href=\"#input\"/></informationRequirement>"
				+ "<literalExpression><text>Amount</text></literalExpression></decision>";
		var producer = new DmnIrScalarProducer();
		var first = producer.produce(source(input + ref));
		assertArrayEquals(first.artifact().toByteArray(),
				producer.produce(source(ref + input)).artifact().toByteArray());
		assertArrayEquals(first.artifact().toByteArray(),
				producer.produce(source(input + ref)).artifact().toByteArray());
		assertEquals(new BigDecimal("42.00"), new DmnIrRuntimeAdapter().adapt(first)
				.evaluate(Map.of(key("input"), new BigDecimal("42.00"))).get(key("copy")));
	}

	@Test
	void rejectsConstantFoldableArithmeticInsteadOfOptimizingIntoProfile() {
		var error = assertThrows(DmnIrProductionException.class,
				() -> new DmnIrScalarProducer().produce(source(decision("sum", "Sum", "number", "1 + 2"))));
		assertEquals(DmnIrProductionException.Category.UNSUPPORTED_PROFILE, error.category());
		assertEquals(Optional.of(key("sum")), error.element());
	}

	@Test
	void preservesCompilerDiagnosticsForInvalidSource() {
		var error = assertThrows(DmnIrProductionException.class,
				() -> new DmnIrScalarProducer().produce(source(decision("bad", "Bad", "number", "1 +"))));
		assertEquals(DmnIrProductionException.Category.SOURCE_INVALID, error.category());
		assertFalse(error.diagnostics().isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> error.diagnostics().clear());
	}

	@Test
	void rejectsNamedTypesInsteadOfDroppingTheirDeclarations() {
		var error = assertThrows(DmnIrProductionException.class,
				() -> new DmnIrScalarProducer().produce(
						source("<itemDefinition id=\"type\" name=\"Amount\"><typeRef>number</typeRef></itemDefinition>"
								+ decision("amount", "AmountValue", "number", "42"))));
		assertEquals(DmnIrProductionException.Category.UNSUPPORTED_PROFILE, error.category());
	}

	@Test
	void bundlesImportsWithoutConflatingRepeatedElementIds() {
		String importedNamespace = "urn:example:rates";
		var imported = source(
				"<inputData id=\"input\" name=\"Rate\"><variable name=\"Rate\" typeRef=\"number\"/></inputData>");
		imported = new DmnSource(DmnSourceId.of("memory:/rates.dmn"),
				new String(imported.content(), StandardCharsets.UTF_8).replace(NS, importedNamespace)
						.getBytes(StandardCharsets.UTF_8));
		var root = source("""
				<import name="rates" namespace="urn:example:rates" locationURI="rates.dmn" importType="DMN"/>
				<inputData id="input" name="Local"><variable name="Local" typeRef="number"/></inputData>
				<decision id="copy" name="Copy"><variable name="Copy" typeRef="number"/>
				<informationRequirement><requiredInput href="urn:example:rates#input"/></informationRequirement>
				<literalExpression><text>Rate</text></literalExpression></decision>
				""");
		var resolver = new InMemoryDmnModelResolver(List.of(imported));
		var verified = new DmnIrScalarProducer().produce(root, resolver);
		var adapted = new DmnIrRuntimeAdapter().adapt(verified);
		var values = adapted.evaluate(Map.of(key("input"), BigDecimal.ONE,
				new DmnIrRuntimeAdapter.ElementId(importedNamespace, "input"), new BigDecimal("97")));
		assertEquals(new BigDecimal("97"), values.get(key("copy")));
		var model = verified.artifact().getModelsList().stream().filter(m -> m.getNamespace().equals(NS)).findFirst()
				.orElseThrow();
		assertEquals("rates", model.getImports(0).getName());
		assertEquals("rates.dmn", model.getImports(0).getLocationUri());
		assertEquals(importedNamespace, model.getDecisions(0).getExpression().getGlobalReference().getModelNamespace());
		assertEquals(new DmnCompiler().compile(root, resolver).compiledModel().orElseThrow().evaluateDecision("Copy",
				Map.of("Local", BigDecimal.ONE, "Rate", new BigDecimal("97"))), values.get(key("copy")));
		assertEquals(DmnIrProductionException.Category.SOURCE_INVALID,
				assertThrows(DmnIrProductionException.class, () -> new DmnIrScalarProducer().produce(root)).category());
	}

	@Test
	void separateJvmExecutesProducedBytesWithoutSourceFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
			throws Exception {
		var file = dir.resolve("model.dmnir");
		java.nio.file.Files.write(file, new DmnIrScalarProducer()
				.produce(source(decision("answer", "Answer", "number", "42"))).artifact().toByteArray());
		var output = dir.resolve("consumer.log");
		var process = new ProcessBuilder(
				java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(), "-cp",
				System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
				ArtifactConsumer.class.getName(), file.toString()).redirectErrorStream(true)
				.redirectOutput(output.toFile()).start();
		try {
			assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "Consumer timed out");
			assertEquals(0, process.exitValue(), java.nio.file.Files.readString(output));
		} finally {
			process.destroyForcibly();
		}
	}

	public static final class ArtifactConsumer {
		public static void main(String[] args) throws Exception {
			var verified = new DmnIrArtifactReader()
					.read(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0])));
			var value = new DmnIrRuntimeAdapter().adapt(verified).evaluate(Map.of()).get(key("answer"));
			if (!new BigDecimal("42").equals(value))
				throw new AssertionError("Artifact execution mismatch");
		}
	}

	@Test
	void rejectsUnrepresentableTemporalType() {
		assertEquals(DmnIrProductionException.Category.UNSUPPORTED_PROFILE,
				assertThrows(DmnIrProductionException.class, () -> new DmnIrScalarProducer().produce(source(
						"<inputData id=\"date\" name=\"Date\"><variable name=\"Date\" typeRef=\"date\"/></inputData>")))
						.category());
	}

	@Test
	void emittedArtifactMustMeetExistingReaderLimits() {
		var error = assertThrows(DmnIrProductionException.class, () -> new DmnIrScalarProducer()
				.produce(source(decision("large", "Large", "string", "\"" + "x".repeat(16385) + "\""))));
		assertEquals(DmnIrProductionException.Category.ARTIFACT_INVALID, error.category());
		assertInstanceOf(io.finmsg.dmn.dmnir.codec.DmnIrValidationException.class, error.getCause());
	}

	@Test
	void rejectsTablesBkmsAndDecisionServices() {
		for (String body : List.of("""
				<businessKnowledgeModel id="bkm" name="Identity"><variable name="Identity"/>
				<encapsulatedLogic kind="FEEL"><formalParameter name="x" typeRef="number"/>
				<literalExpression><text>x</text></literalExpression></encapsulatedLogic></businessKnowledgeModel>
				""", decision("answer", "Answer", "number", "42") + """
				<decisionService id="service" name="Service"><variable name="Service"/>
				<outputDecision href="#answer"/></decisionService>
				""", """
				<decision id="table" name="Table"><variable name="Table" typeRef="number"/>
				<decisionTable hitPolicy="UNIQUE"><output name="result" typeRef="number"/>
				<rule><outputEntry><text>42</text></outputEntry></rule></decisionTable></decision>
				""")) {
			var error = assertThrows(DmnIrProductionException.class,
					() -> new DmnIrScalarProducer().produce(source(body)));
			assertEquals(DmnIrProductionException.Category.UNSUPPORTED_PROFILE, error.category(),
					error.diagnostics().toString());
		}
	}

	private static DmnIrRuntimeAdapter.ElementId key(String id) {
		return new DmnIrRuntimeAdapter.ElementId(NS, id);
	}
	private static String decision(String id, String name, String type, String text) {
		return "<decision id=\"" + id + "\" name=\"" + name + "\"><variable id=\"" + id + "-var\" name=\"" + name
				+ "\" typeRef=\"" + type + "\"/><literalExpression><text>" + text
				+ "</text></literalExpression></decision>";
	}
	private static DmnSource source(String body) {
		return new DmnSource(DmnSourceId.of("memory:/producer.dmn"),
				("<definitions xmlns=\"https://www.omg.org/spec/DMN/20240513/MODEL/\" id=\"model\" name=\"Producer\" namespace=\""
						+ NS + "\">" + body + "</definitions>").getBytes(StandardCharsets.UTF_8));
	}
}

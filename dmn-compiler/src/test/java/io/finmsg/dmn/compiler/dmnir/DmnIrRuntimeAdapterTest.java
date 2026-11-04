package io.finmsg.dmn.compiler.dmnir;

import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter.ElementId;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class DmnIrRuntimeAdapterTest {
	private static final String ROOT = "urn:example:root";
	private static final String RATES = "urn:example:rates";
	private static final DmnIrScalarType NUMBER = DmnIrScalarType.SCALAR_TYPE_NUMBER;

	@Test
	void executesArtifactWithoutSourcesAndPreservesPublicIdentitiesAndExactScale() {
		DmnIrArtifact artifact = model();
		var verified = new DmnIrArtifactReader().read(artifact.toByteArray());
		var adapted = new DmnIrRuntimeAdapter().adapt(verified);
		BigDecimal amount = new BigDecimal("123.4500");
		var values = adapted.evaluate(Map.of(key("amount"), amount));

		assertSame(verified, adapted.source());
		assertEquals(amount, values.get(key("first")));
		assertEquals(amount, values.get(key("second")));
		assertEquals(new BigDecimal("9007199254740993.0000000000000000001"), values.get(key("precise")));
		assertEquals(new BigDecimal("1E+30"), values.get(key("exponent")));
		assertEquals(Boolean.FALSE, values.get(key("false")));
		assertEquals("", values.get(key("empty")));
		assertTrue(values.containsKey(key("nullable")));
		assertNull(values.get(key("nullable")));
		assertEquals("Amount", adapted.inputs().get(key("amount")).elementName());
		assertEquals("Amount", adapted.inputs().get(key("amount")).variableName());
		assertEquals(Optional.of("amount-var"), adapted.inputs().get(key("amount")).variableId());
		assertEquals(8, adapted.runtimeModel().valueSlotCount());
		assertEquals(7, adapted.runtimeModel().decisions().size());
	}

	@Test
	void matchesExistingDmn16PipelineForScalarValuesNullsAndCoercion() {
		var adapted = adapt(model());
		DmnSource source = source("root.dmn", xml(ROOT,
				"""
						<inputData id="amount" name="Amount"><variable id="amount-var" name="Amount" typeRef="number"/></inputData>
						<decision id="first" name="First"><variable id="first-var" name="First" typeRef="number"/>
						  <informationRequirement><requiredInput href="#amount"/></informationRequirement>
						  <literalExpression><text>Amount</text></literalExpression></decision>
						<decision id="second" name="Second"><variable id="second-var" name="Second" typeRef="number"/>
						  <informationRequirement><requiredDecision href="#first"/></informationRequirement>
						  <literalExpression><text>First</text></literalExpression></decision>
						<decision id="precise" name="Precise"><variable id="precise-var" name="Precise" typeRef="number"/>
						  <literalExpression><text>9007199254740993.0000000000000000001</text></literalExpression></decision>
						<decision id="exponent" name="Exponent"><variable id="exponent-var" name="Exponent" typeRef="number"/>
						  <literalExpression><text>1000000000000000000000000000000</text></literalExpression></decision>
						<decision id="false" name="FalseValue"><variable id="false-var" name="FalseValue" typeRef="boolean"/>
						  <literalExpression><text>false</text></literalExpression></decision>
						<decision id="empty" name="Empty"><variable id="empty-var" name="Empty" typeRef="string"/>
						  <literalExpression><text>""</text></literalExpression></decision>
						<decision id="nullable" name="Nullable"><variable id="nullable-var" name="Nullable" typeRef="number"/>
						  <literalExpression><text>null</text></literalExpression></decision>
						"""));
		for (DmnCompilerOptions options : List.of(DmnCompilerOptions.lowered(), DmnCompilerOptions.optimized())) {
			var result = new DmnCompiler().compile(source, new InMemoryDmnModelResolver(List.of()), options);
			assertTrue(result.isSuccess(), result.diagnostics().toString());
			var compiled = result.compiledModel().orElseThrow();
			List<Object> cases = new ArrayList<>(
					List.of(new BigDecimal("123.4500"), "wrong type", List.of(new BigDecimal("42"))));
			cases.add(null);
			for (Object value : cases) {
				Map<ElementId, Object> portableInputs = new LinkedHashMap<>();
				portableInputs.put(key("amount"), value);
				Map<String, Object> xmlInputs = new LinkedHashMap<>();
				xmlInputs.put("Amount", value);
				var actual = adapted.evaluate(portableInputs);
				for (var binding : adapted.decisions().values()) {
					assertValue(compiled.evaluateDecision(binding.variableName(), xmlInputs), actual.get(binding.id()));
				}
			}
		}
	}

	@Test
	void linksIdenticalElementIdsInDifferentNamespacesAndMatchesImportedXml() {
		DmnIrModel root = model().getModels(0).toBuilder()
				.addImports(
						DmnIrImport.newBuilder().setModelNamespace(RATES).setName("rates").setLocationUri("rates.dmn"))
				.addDecisions(reference("imported", "Imported", RATES, "amount")).build();
		DmnIrModel rates = DmnIrModel.newBuilder().setIdentity(identity("rates-model", "Rates")).setNamespace(RATES)
				.addInputs(DmnIrInput.newBuilder().setIdentity(identity("amount", "Rate"))
						.setVariable(variable("rate-var", "Rate", NUMBER)))
				.build();
		var artifact = model().toBuilder().setModels(0, root).addModels(rates).addEvaluationOrder(ref(ROOT, "imported"))
				.build();
		var adapted = adapt(artifact);
		var input = Map.of(key("amount"), new BigDecimal("11"), new ElementId(RATES, "amount"), new BigDecimal("97"));
		var output = adapted.evaluate(input);
		assertEquals(new BigDecimal("11"), output.get(key("first")));
		assertEquals(new BigDecimal("97"), output.get(key("imported")));
		assertNotEquals(adapted.inputs().get(key("amount")).slot(),
				adapted.inputs().get(new ElementId(RATES, "amount")).slot());

		DmnSource rootXml = source("root.dmn", xml(ROOT, """
				<import name="rates" namespace="urn:example:rates" locationURI="rates.dmn" importType="DMN"/>
				<decision id="imported" name="Imported"><variable id="imported-var" name="Imported" typeRef="number"/>
				  <informationRequirement><requiredInput href="urn:example:rates#amount"/></informationRequirement>
				  <literalExpression><text>Rate</text></literalExpression></decision>
				"""));
		DmnSource ratesXml = source("rates.dmn", xml(RATES, """
				<inputData id="amount" name="Rate"><variable id="rate-var" name="Rate" typeRef="number"/></inputData>
				"""));
		var result = new DmnCompiler().compile(rootXml, new InMemoryDmnModelResolver(List.of(ratesXml)));
		assertTrue(result.isSuccess(), result.diagnostics().toString());
		assertValue(
				result.compiledModel().orElseThrow().evaluateDecision("Imported", Map.of("Rate", new BigDecimal("97"))),
				output.get(key("imported")));
	}

	@Test
	void slotAllocationAndResultsAreDeterministicAfterDeclarationReordering() {
		var artifact = model();
		var reorderedRoot = artifact.getModels(0).toBuilder().clearDecisions();
		List<DmnIrDecision> reversed = new ArrayList<>(artifact.getModels(0).getDecisionsList());
		Collections.reverse(reversed);
		reorderedRoot.addAllDecisions(reversed);
		var first = adapt(artifact);
		var second = adapt(artifact.toBuilder().setModels(0, reorderedRoot).build());
		assertEquals(first.runtimeModel(), second.runtimeModel());
		assertEquals(first.inputs(), second.inputs());
		assertEquals(first.decisions(), second.decisions());
		assertEquals(first.evaluate(Map.of(key("amount"), BigDecimal.ONE)),
				second.evaluate(Map.of(key("amount"), BigDecimal.ONE)));
		assertThrows(UnsupportedOperationException.class, () -> first.inputs().clear());
		assertThrows(UnsupportedOperationException.class, () -> first.decisions().clear());
		assertThrows(UnsupportedOperationException.class,
				() -> first.evaluate(Map.of(key("amount"), BigDecimal.ONE)).clear());
	}

	@Test
	void preservesRawStringContentAndDistinctDisplayAndVariableNames() {
		String text = "quote\" backslash\\ newline\nUnicode é";
		var decision = literal("raw", "Display name", DmnIrScalarType.SCALAR_TYPE_STRING,
				DmnIrLiteral.newBuilder().setStringValue(text).build()).toBuilder()
				.setVariable(variable("raw-var", "variable name", DmnIrScalarType.SCALAR_TYPE_STRING));
		var artifact = model().toBuilder().setModels(0, model().getModels(0).toBuilder().addDecisions(decision))
				.addEvaluationOrder(ref(ROOT, "raw")).build();
		var adapted = adapt(artifact);
		assertEquals(text, adapted.evaluate(Map.of(key("amount"), BigDecimal.ONE)).get(key("raw")));
		assertEquals("Display name", adapted.decisions().get(key("raw")).elementName());
		assertEquals("variable name", adapted.decisions().get(key("raw")).variableName());
	}

	@Test
	void rejectsMissingAndUnknownInputIdentitiesAndKeepsExplicitNullDistinct() {
		var adapted = adapt(model());
		assertThrows(IllegalArgumentException.class, () -> adapted.evaluate(Map.of()));
		assertThrows(IllegalArgumentException.class, () -> adapted.evaluate(Map.of(key("unknown"), BigDecimal.ONE)));
		Map<ElementId, Object> nullInput = new LinkedHashMap<>();
		nullInput.put(key("amount"), null);
		var output = adapted.evaluate(nullInput);
		assertTrue(output.containsKey(key("first")));
		assertNull(output.get(key("first")));
		assertNull(output.get(key("second")));
	}

	@Test
	void requiresNullForNullTypedInputs() {
		var source = model().toBuilder();
		var root = source.getModels(0).toBuilder().clearInputs().clearDecisions();
		root.addInputs(DmnIrInput.newBuilder().setIdentity(identity("nothing", "Nothing"))
				.setVariable(variable("nothing-var", "Nothing", DmnIrScalarType.SCALAR_TYPE_NULL)));
		root.addDecisions(reference("result", "Result", ROOT, "nothing").toBuilder()
				.setVariable(variable("result-var", "Result", NUMBER)));
		source.setModels(0, root).clearEvaluationOrder().addEvaluationOrder(ref(ROOT, "result"));
		var adapted = adapt(source.build());
		Map<ElementId, Object> nullInput = new LinkedHashMap<>();
		nullInput.put(key("nothing"), null);
		assertNull(adapted.evaluate(nullInput).get(key("result")));
		assertThrows(IllegalArgumentException.class, () -> adapted.evaluate(Map.of(key("nothing"), BigDecimal.ONE)));
	}

	@Test
	void repeatedAndConcurrentEvaluationHasFreshSlotState() throws Exception {
		var adapted = adapt(model());
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<Map<ElementId, Object>>> results = new ArrayList<>();
			for (int i = 0; i < 20; i++) {
				BigDecimal value = BigDecimal.valueOf(i);
				results.add(executor.submit(() -> adapted.evaluate(Map.of(key("amount"), value))));
			}
			for (int i = 0; i < results.size(); i++) {
				assertEquals(BigDecimal.valueOf(i), results.get(i).get().get(key("second")));
			}
		}
		assertEquals(new BigDecimal("999"),
				adapted.evaluate(Map.of(key("amount"), new BigDecimal("999"))).get(key("first")));
	}

	private static DmnIrRuntimeAdapter.AdaptedModel adapt(DmnIrArtifact artifact) {
		return new DmnIrRuntimeAdapter().adapt(new DmnIrArtifactReader().read(artifact.toByteArray()));
	}

	private static void assertValue(Object expected, Object actual) {
		if (expected instanceof BigDecimal left && actual instanceof BigDecimal right) {
			assertEquals(0, left.compareTo(right));
		} else {
			assertEquals(expected, actual);
		}
	}

	private static ElementId key(String id) {
		return new ElementId(ROOT, id);
	}

	private static DmnIrReference ref(String namespace, String id) {
		return DmnIrReference.newBuilder().setModelNamespace(namespace).setElementId(id).build();
	}

	private static DmnIrElementIdentity identity(String id, String name) {
		return DmnIrElementIdentity.newBuilder().setId(id).setName(name).build();
	}

	private static DmnIrVariable variable(String id, String name, DmnIrScalarType type) {
		return DmnIrVariable.newBuilder().setId(id).setName(name).setType(type).build();
	}

	private static DmnIrDecision reference(String id, String name, String namespace, String target) {
		return DmnIrDecision.newBuilder().setIdentity(identity(id, name))
				.setVariable(variable(id + "-var", name, NUMBER)).addDependencies(ref(namespace, target))
				.setExpression(DmnIrExpression.newBuilder().setType(NUMBER).setGlobalReference(ref(namespace, target)))
				.build();
	}

	private static DmnIrDecision literal(String id, String name, DmnIrScalarType type, DmnIrLiteral literal) {
		return DmnIrDecision.newBuilder().setIdentity(identity(id, name)).setVariable(variable(id + "-var", name, type))
				.setExpression(DmnIrExpression.newBuilder().setType(type).setLiteral(literal)).build();
	}

	private static DmnIrDecision number(String id, String name, String value) {
		BigDecimal decimal = new BigDecimal(value);
		return literal(id, name, NUMBER,
				DmnIrLiteral
						.newBuilder().setNumberValue(DmnIrDecimal.newBuilder()
								.setUnscaledValue(decimal.unscaledValue().toString()).setScale(decimal.scale()))
						.build());
	}

	private static DmnIrArtifact model() {
		var root = DmnIrModel.newBuilder().setIdentity(identity("root-model", "Root")).setNamespace(ROOT)
				.addInputs(DmnIrInput.newBuilder().setIdentity(identity("amount", "Amount"))
						.setVariable(variable("amount-var", "Amount", NUMBER)))
				.addDecisions(reference("first", "First", ROOT, "amount"))
				.addDecisions(reference("second", "Second", ROOT, "first"))
				.addDecisions(number("precise", "Precise", "9007199254740993.0000000000000000001"))
				.addDecisions(number("exponent", "Exponent", "1E+30"))
				.addDecisions(literal("false", "FalseValue", DmnIrScalarType.SCALAR_TYPE_BOOLEAN,
						DmnIrLiteral.newBuilder().setBooleanValue(false).build()))
				.addDecisions(literal("empty", "Empty", DmnIrScalarType.SCALAR_TYPE_STRING,
						DmnIrLiteral.newBuilder().setStringValue("").build()))
				.addDecisions(literal("nullable", "Nullable", NUMBER,
						DmnIrLiteral.newBuilder().setNullValue(DmnIrNull.NULL_VALUE).build()));
		var artifact = DmnIrArtifact.newBuilder()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(0))
				.setSemanticsVersion(DmnIrArtifactReader.SEMANTICS_VERSION)
				.addRequiredCapabilities(DmnIrArtifactReader.SCALAR_CAPABILITY).setRootModelNamespace(ROOT)
				.addModels(root);
		for (String id : List.of("first", "second", "precise", "exponent", "false", "empty", "nullable")) {
			artifact.addEvaluationOrder(ref(ROOT, id));
		}
		return artifact.build();
	}

	private static DmnSource source(String name, String xml) {
		return new DmnSource(DmnSourceId.of("memory:/models/" + name), xml.getBytes(StandardCharsets.UTF_8));
	}

	private static String xml(String namespace, String body) {
		return "<definitions xmlns=\"https://www.omg.org/spec/DMN/20240513/MODEL/\" namespace=\"" + namespace
				+ "\" id=\"model\" name=\"Model\">" + body + "</definitions>";
	}
}

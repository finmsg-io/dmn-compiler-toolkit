package io.finmsg.dmn.compiler.dmnir;

import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DmnIrCollectionParityTest {
	private static final String NS = "urn:collections:policy";
	private static final String CATALOG = """
			<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="catalog" name="Catalog" namespace="urn:collections:catalog">
			<itemDefinition id="dates" name="tDates" isCollection="true"><typeRef>date</typeRef></itemDefinition>
			<itemDefinition id="fees" name="tFees" isCollection="true"><typeRef>number</typeRef></itemDefinition>
			<itemDefinition id="permit" name="tPermit">
			<itemComponent name="Expiry"><typeRef>date</typeRef><typeConstraint><text>&gt;= date("2024-01-01")</text></typeConstraint></itemComponent>
			<itemComponent name="Previous Dates"><typeRef>tDates</typeRef></itemComponent>
			<itemComponent name="Previous Fees"><typeRef>tFees</typeRef></itemComponent>
			</itemDefinition></definitions>
			""";
	private static final String POLICY = """
			<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" xmlns:catalog="urn:collections:catalog"
				id="policy" name="Policy" namespace="urn:collections:policy">
			<import name="catalog" namespace="urn:collections:catalog" locationURI="catalog.dmn" importType="DMN"/>
			<itemDefinition id="applicant-type" name="tApplicant"><itemComponent name="Permit"><typeRef>catalog:tPermit</typeRef></itemComponent></itemDefinition>
			<inputData id="applicant" name="Applicant"><variable name="Applicant" typeRef="tApplicant"/></inputData>
			<inputData id="include" name="Include"><variable name="Include" typeRef="boolean"/></inputData>
			<inputData id="external" name="External"><variable name="External" typeRef="catalog:tFees"/></inputData>
			<decision id="fee" name="Fee"><variable name="Fee" typeRef="number"/>
			<informationRequirement><requiredInput href="#applicant"/></informationRequirement>
			<decisionTable hitPolicy="FIRST"><input><inputExpression typeRef="date"><text>Applicant.Permit.Expiry</text></inputExpression></input>
			<output typeRef="number"><defaultOutputEntry><text>0</text></defaultOutputEntry></output>
			<rule><inputEntry><text>&gt;= date("2024-02-01")</text></inputEntry><outputEntry><text>30.125</text></outputEntry></rule>
			</decisionTable></decision>
			<decision id="history" name="History"><variable name="History" typeRef="catalog:tFees"/>
			<informationRequirement><requiredInput href="#applicant"/></informationRequirement>
			<informationRequirement><requiredInput href="#include"/></informationRequirement>
			<informationRequirement><requiredDecision href="#fee"/></informationRequirement>
			<context><contextEntry><variable name="Prior" typeRef="catalog:tFees"/><literalExpression><text>Applicant.Permit.Previous Fees</text></literalExpression></contextEntry>
			<contextEntry><literalExpression><text>if Include then [Fee, Fee, null, 0.125] else Prior</text></literalExpression></contextEntry></context></decision>
			<decision id="dates" name="Dates"><variable name="Dates" typeRef="catalog:tDates"/>
			<informationRequirement><requiredInput href="#applicant"/></informationRequirement>
			<literalExpression><text>Applicant.Permit.Previous Dates</text></literalExpression></decision>
			<decision id="empty" name="Empty Or Null"><variable name="Empty Or Null" typeRef="catalog:tFees"/>
			<informationRequirement><requiredInput href="#include"/></informationRequirement>
			<literalExpression><text>if Include then [] else null</text></literalExpression></decision>
			<decision id="echo" name="Echo"><variable name="Echo" typeRef="catalog:tFees"/>
			<informationRequirement><requiredInput href="#external"/></informationRequirement>
			<literalExpression><text>External</text></literalExpression></decision></definitions>
			""";

	@Test
	void composedPolicyRetainsCollectionIdentitiesAndValues(@TempDir Path dir) throws Exception {
		var source = source("policy.dmn", POLICY);
		var resolver = new InMemoryDmnModelResolver(List.of(source("catalog.dmn", CATALOG)));
		var producer = new DmnIrScalarProducer();
		var verified = producer.produce(source, resolver);
		byte[] bytes = verified.artifact().toByteArray();
		assertEquals(7, verified.artifact().getSchemaVersion().getMinor());
		assertArrayEquals(bytes, producer.produce(source, resolver).artifact().toByteArray());
		var imported = verified.artifact().getModelsList().stream()
				.filter(model -> model.getNamespace().equals("urn:collections:catalog")).findFirst().orElseThrow();
		assertEquals(List.of("dates", "fees"), imported.getCollectionDefinitionsList().stream()
				.map(collection -> collection.getIdentity().getId()).toList());

		var bundle = new DmnIrScalarDmnExporter().export(verified);
		DmnIrDateParityTest.validate(bundle);
		assertEquals(bundle.documents(), new DmnIrScalarDmnExporter().export(verified).documents());
		assertTrue(bundle.documents().values().stream().anyMatch(xml -> xml.contains("id=\"fees\"")
				&& xml.contains("isCollection=\"true\"") && xml.contains("<typeRef>number</typeRef>")));
		var documents = bundle.documents().entrySet().stream().map(e -> source(e.getKey(), e.getValue())).toList();
		var exported = source(bundle.rootDocument(), bundle.documents().get(bundle.rootDocument()));
		var direct = compile(source, resolver);
		var recompiled = compile(exported, new InMemoryDmnModelResolver(documents));
		var roundTrip = producer.produce(exported, new InMemoryDmnModelResolver(documents));
		assertEquals(bundle.documents(), new DmnIrScalarDmnExporter().export(roundTrip).documents());
		var adapter = new DmnIrRuntimeAdapter();
		var model = adapter.adapt(verified);
		var reproduced = adapter.adapt(roundTrip);
		for (boolean include : List.of(false, true)) {
			var inputs = inputs(include);
			var output = model.evaluate(inputs);
			assertEquals(output, reproduced.evaluate(inputs));
			assertEquals(include
					? Arrays.asList(new BigDecimal("30.125"), new BigDecimal("30.125"), null, new BigDecimal("0.125"))
					: List.of(BigDecimal.TEN), output.get(key("history")));
			assertEquals(include ? List.of() : null, output.get(key("empty")));
			assertEquals(Arrays.asList(LocalDate.of(2024, 2, 29), null, LocalDate.of(10000, 1, 1)),
					output.get(key("dates")));
			assertEquals(List.of(new BigDecimal("1.25")), output.get(key("echo")));
			var namedInputs = Map.of("Applicant", inputs.get(key("applicant")), "Include", include, "External",
					inputs.get(key("external")));
			for (var binding : model.decisions().values()) {
				var expected = output.get(binding.id());
				assertEquals(expected, evaluate(direct, binding.variableName(), namedInputs));
				assertEquals(expected, evaluate(recompiled, binding.variableName(), namedInputs));
			}
		}

		runConsumer(dir, bytes, "collections");
	}

	@Test
	void filtersUseResolvedItemsAndOuterLocalsAndRetainNulls(@TempDir Path dir) throws Exception {
		var source = source("policy.dmn", POLICY.replace(
				"<contextEntry><literalExpression><text>if Include then [Fee, Fee, null, 0.125] else Prior</text>",
				"""
						<contextEntry><variable name="Threshold" typeRef="number"/><literalExpression><text>Fee</text></literalExpression></contextEntry>
						<contextEntry><literalExpression><text>if Include then [Fee, Fee, null, 0.125][item = null or item &gt;= Threshold][true] else Prior[true]</text>
						"""));
		var resolver = new InMemoryDmnModelResolver(List.of(source("catalog.dmn", CATALOG)));
		var producer = new DmnIrScalarProducer();
		var artifact = producer.produce(source, resolver);
		assertArrayEquals(artifact.artifact().toByteArray(),
				producer.produce(source, resolver).artifact().toByteArray());
		assertEquals(8, artifact.artifact().getSchemaVersion().getMinor());
		var adapted = new DmnIrRuntimeAdapter().adapt(artifact);
		var expected = Arrays.asList(new BigDecimal("30.125"), new BigDecimal("30.125"), null);
		assertEquals(expected, adapted.evaluate(inputs(true)).get(key("history")));
		var bundle = new DmnIrScalarDmnExporter().export(artifact);
		DmnIrDateParityTest.validate(bundle);
		var documents = bundle.documents().entrySet().stream().map(e -> source(e.getKey(), e.getValue())).toList();
		var recompiled = compile(source(bundle.rootDocument(), bundle.documents().get(bundle.rootDocument())),
				new InMemoryDmnModelResolver(documents));
		var namedInputs = Map.of("Applicant", inputs(true).get(key("applicant")), "Include", true, "External",
				inputs(true).get(key("external")));
		assertEquals(expected, evaluate(compile(source, resolver), "History", namedInputs));
		assertEquals(expected, evaluate(recompiled, "History", namedInputs));
		for (Object prior : Arrays.asList(null, List.of())) {
			var values = new HashMap<>(inputs(false));
			var permit = new HashMap<String, Object>();
			permit.put("Expiry", LocalDate.of(2024, 2, 1));
			permit.put("Previous Dates", List.of());
			permit.put("Previous Fees", prior);
			values.put(key("applicant"), Map.of("Permit", permit));
			assertEquals(prior, adapted.evaluate(values).get(key("history")));
		}
		var changed = source("policy.dmn", new String(source.content(), StandardCharsets.UTF_8)
				.replace("item = null or item &gt;= Threshold", "if item = null then null else item &gt;= Threshold"));
		var nullable = new DmnIrRuntimeAdapter().adapt(producer.produce(changed, resolver));
		assertEquals(expected.subList(0, 2), nullable.evaluate(inputs(true)).get(key("history")));

		var captured = artifact.artifact().toBuilder();
		var root = captured.getModelsBuilderList().stream().filter(m -> m.getNamespace().equals(NS)).findFirst()
				.orElseThrow();
		var history = root.getDecisionsBuilderList().stream().filter(d -> d.getIdentity().getId().equals("history"))
				.findFirst().orElseThrow();
		history.getExpressionBuilder().getContextBuilder().getBindingsBuilder(1).getVariableBuilder().setName("item");
		var verified = new DmnIrArtifactReader().read(captured.build().toByteArray());
		assertThrows(DmnIrExportException.class, () -> new DmnIrScalarDmnExporter().export(verified));
		runConsumer(dir, artifact.artifact().toByteArray(), "filters");
	}

	private static void runConsumer(Path dir, byte[] bytes, String mode) throws Exception {
		var file = Files.write(dir.resolve("collections.dmnir"), bytes);
		var log = dir.resolve("consumer.log");
		String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
		var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", exe).toString(), "-cp",
				System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
				Consumer.class.getName(), file.toString(), mode).redirectErrorStream(true).redirectOutput(log.toFile())
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
			var verified = new DmnIrArtifactReader().read(Files.readAllBytes(Path.of(args[0])));
			var output = new DmnIrRuntimeAdapter().adapt(verified).evaluate(inputs(true));
			var expected = args[1].equals("closure")
					? List.of(new BigDecimal("30.125"), new BigDecimal("30.125"))
					: args[1].equals("filters") || args[1].equals("builtin")
							? Arrays.asList(new BigDecimal("30.125"), new BigDecimal("30.125"), null)
							: Arrays.asList(new BigDecimal("30.125"), new BigDecimal("30.125"), null,
									new BigDecimal("0.125"));
			if (!expected.equals(output.get(key("history"))))
				throw new AssertionError(output);
			if (args[1].equals("closure") && !new BigDecimal("3").equals(output.get(key("immediate"))))
				throw new AssertionError(output);
			if (args[1].equals("bkms") && (!new BigDecimal("9").equals(output.get(key("difference")))
					|| !LocalDate.of(2024, 2, 1).equals(output.get(key("expiry")))))
				throw new AssertionError(output);
		}
	}

	@Test
	void closuresCapturePriorValuesAndRoundTripBothCallForms(@TempDir Path dir) throws Exception {
		String policy = POLICY.replace(
				"<contextEntry><literalExpression><text>if Include then [Fee, Fee, null, 0.125] else Prior</text></literalExpression></contextEntry>",
				"""
						<contextEntry><variable name="Cutoff" typeRef="number"/><literalExpression><text>Fee</text></literalExpression></contextEntry>
						<contextEntry><variable name="Qualifies"/>
						<functionDefinition kind="FEEL"><formalParameter name="Candidate" typeRef="number"/>
						<literalExpression><text>Candidate &gt;= Cutoff</text></literalExpression>
						</functionDefinition></contextEntry>
						<contextEntry><context>
						<contextEntry><variable name="Cutoff" typeRef="number"/><literalExpression><text>999</text></literalExpression></contextEntry>
						<contextEntry><literalExpression><text>if Include then [Fee, Fee, null, 0.125][Qualifies(item)] else Prior</text></literalExpression></contextEntry>
						</context></contextEntry>
						""")
				.replace("</definitions>",
						"""
								<decision id="immediate" name="Immediate"><variable name="Immediate" typeRef="number"/>
								<literalExpression><text>(function(x: number) x + 1)(2)</text></literalExpression>
								</decision>
								<decision id="structured" name="Structured"><variable name="Structured" typeRef="date"/>
								<informationRequirement><requiredInput href="#applicant"/></informationRequirement>
								<literalExpression><text>(function(p: catalog.tPermit) p.Expiry)(Applicant.Permit)</text></literalExpression>
								</decision></definitions>
								""");
		var source = source("policy.dmn", policy);
		var resolver = new InMemoryDmnModelResolver(List.of(source("catalog.dmn", CATALOG)));
		var producer = new DmnIrScalarProducer();
		var verified = producer.produce(source, resolver);
		var external = source("policy.dmn", policy.replace("kind=\"FEEL\"", "kind=\"Java\""));
		assertEquals(DmnIrProductionException.Category.UNSUPPORTED_PROFILE,
				assertThrows(DmnIrProductionException.class, () -> producer.produce(external, resolver)).category());
		assertEquals(11, verified.artifact().getSchemaVersion().getMinor());
		assertArrayEquals(verified.artifact().toByteArray(),
				producer.produce(source, resolver).artifact().toByteArray());
		var output = new DmnIrRuntimeAdapter().adapt(verified).evaluate(inputs(true));
		assertEquals(List.of(new BigDecimal("30.125"), new BigDecimal("30.125")), output.get(key("history")));
		assertEquals(new BigDecimal("3"), output.get(key("immediate")));
		assertEquals(LocalDate.of(2024, 2, 1), output.get(key("structured")));
		var captured = verified.artifact().toBuilder();
		var root = captured.getModelsBuilderList().stream().filter(model -> model.getNamespace().equals(NS)).findFirst()
				.orElseThrow();
		var history = root.getDecisionsBuilderList().stream()
				.filter(decision -> decision.getIdentity().getId().equals("history")).findFirst().orElseThrow();
		history.getExpressionBuilder().getContextBuilder().getBindingsBuilder(2).getClosureBuilder()
				.getDefinitionBuilder().getParametersBuilder(0).getVariableBuilder().setName("Cutoff");
		var capturedArtifact = new DmnIrArtifactReader().read(captured.build().toByteArray());
		assertThrows(DmnIrExportException.class, () -> new DmnIrScalarDmnExporter().export(capturedArtifact));
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		DmnIrDateParityTest.validate(bundle);
		var documents = bundle.documents().entrySet().stream().map(e -> source(e.getKey(), e.getValue())).toList();
		var exported = source(bundle.rootDocument(), bundle.documents().get(bundle.rootDocument()));
		var exportResolver = new InMemoryDmnModelResolver(documents);
		assertEquals(output,
				new DmnIrRuntimeAdapter().adapt(producer.produce(exported, exportResolver)).evaluate(inputs(true)));
		var named = Map.of("Applicant", inputs(true).get(key("applicant")), "Include", true, "External",
				inputs(true).get(key("external")));
		for (var compiled : List.of(compile(source, resolver), compile(exported, exportResolver))) {
			assertEquals(output.get(key("history")), evaluate(compiled, "History", named));
			assertEquals(output.get(key("immediate")), evaluate(compiled, "Immediate", named));
			assertEquals(output.get(key("structured")), evaluate(compiled, "Structured", named));
		}
		runConsumer(dir, verified.artifact().toByteArray(), "closure");
	}

	@Test
	void namedBkmsPreserveImportedTargetsAndFormalPositions(@TempDir Path dir) throws Exception {
		String catalog = CATALOG.replace("</definitions>", """
				<businessKnowledgeModel id="subtract" name="Subtract">
				<variable name="Subtract" typeRef="number"/>
				<encapsulatedLogic kind="FEEL">
				<formalParameter id="left" name="Left" typeRef="number"/>
				<formalParameter id="right" name="Right" typeRef="number"/>
				<literalExpression><text>Left - Right</text></literalExpression>
				</encapsulatedLogic></businessKnowledgeModel>
				<businessKnowledgeModel id="get-expiry" name="Get Expiry">
				<variable name="Get Expiry" typeRef="date"/>
				<encapsulatedLogic kind="FEEL">
				<formalParameter name="Permit" typeRef="tPermit"/>
				<literalExpression><text>Permit.Expiry</text></literalExpression>
				</encapsulatedLogic></businessKnowledgeModel>
				</definitions>
				""");
		String policy = POLICY.replace("</definitions>",
				"""
								<businessKnowledgeModel id="adjust" name="Adjust">
								<variable name="Adjust" typeRef="number"/>
								<encapsulatedLogic kind="FEEL">
								<formalParameter name="Applicant" typeRef="number"/>
								<formalParameter name="Delta" typeRef="number"/>
						<literalExpression><text>catalog.Subtract(Applicant, Delta) + 1</text></literalExpression>
								</encapsulatedLogic>
								<knowledgeRequirement><requiredKnowledge href="urn:collections:catalog#subtract"/></knowledgeRequirement>
								</businessKnowledgeModel>
								<decision id="difference" name="Difference"><variable name="Difference" typeRef="number"/>
								<knowledgeRequirement><requiredKnowledge href="#adjust"/></knowledgeRequirement>
								<literalExpression><text>Adjust(Delta: 2, Applicant: 10)</text></literalExpression>
								</decision>
								<decision id="nullable" name="Nullable"><variable name="Nullable" typeRef="number"/>
								<knowledgeRequirement><requiredKnowledge href="#adjust"/></knowledgeRequirement>
								<literalExpression><text>Adjust(null, 2)</text></literalExpression>
								</decision>
								<decision id="expiry" name="Expiry"><variable name="Expiry" typeRef="date"/>
								<informationRequirement><requiredInput href="#applicant"/></informationRequirement>
								<knowledgeRequirement><requiredKnowledge href="urn:collections:catalog#get-expiry"/></knowledgeRequirement>
								<literalExpression><text>catalog.Get Expiry(Applicant.Permit)</text></literalExpression>
								</decision></definitions>
								""");
		var source = source("policy.dmn", policy);
		var resolver = new InMemoryDmnModelResolver(List.of(source("catalog.dmn", catalog)));
		var producer = new DmnIrScalarProducer();
		var verified = producer.produce(source, resolver);
		var artifact = verified.artifact();
		assertEquals(10, artifact.getSchemaVersion().getMinor());
		assertArrayEquals(artifact.toByteArray(), producer.produce(source, resolver).artifact().toByteArray());
		var root = artifact.getModelsList().stream().filter(model -> model.getNamespace().equals(NS)).findFirst()
				.orElseThrow();
		var difference = root.getDecisionsList().stream()
				.filter(decision -> decision.getIdentity().getId().equals("difference")).findFirst().orElseThrow();
		assertEquals(List.of(2, 1), difference.getExpression().getBkmCall().getArgumentsList().stream()
				.map(argument -> argument.getParameterPosition()).toList());
		var outputs = new DmnIrRuntimeAdapter().adapt(verified).evaluate(inputs(true));
		assertEquals(new BigDecimal("9"), outputs.get(key("difference")));
		assertNull(outputs.get(key("nullable")));
		assertEquals(LocalDate.of(2024, 2, 1), outputs.get(key("expiry")));
		var boxedSource = source("policy.dmn", policy.replace(
				"<literalExpression><text>Adjust(Delta: 2, Applicant: 10)</text></literalExpression>",
				"""
						<invocation><literalExpression><text>Adjust</text></literalExpression>
						<binding><parameter name="Delta"/><literalExpression><text>2</text></literalExpression></binding>
						<binding><parameter name="Applicant"/><literalExpression><text>10</text></literalExpression></binding>
						</invocation>
						"""));
		var boxed = producer.produce(boxedSource, resolver);
		assertEquals(outputs, new DmnIrRuntimeAdapter().adapt(boxed).evaluate(inputs(true)));
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		DmnIrDateParityTest.validate(bundle);
		var documents = bundle.documents().entrySet().stream().map(e -> source(e.getKey(), e.getValue())).toList();
		var exported = source(bundle.rootDocument(), bundle.documents().get(bundle.rootDocument()));
		var exportResolver = new InMemoryDmnModelResolver(documents);
		var roundTrip = producer.produce(exported, exportResolver);
		assertEquals(outputs, new DmnIrRuntimeAdapter().adapt(roundTrip).evaluate(inputs(true)));
		var named = Map.of("Applicant", inputs(true).get(key("applicant")), "Include", true, "External",
				inputs(true).get(key("external")));
		for (var compiled : List.of(compile(source, resolver), compile(exported, exportResolver))) {
			assertEquals(outputs.get(key("difference")), evaluate(compiled, "Difference", named));
			assertEquals(outputs.get(key("expiry")), evaluate(compiled, "Expiry", named));
			assertNull(evaluate(compiled, "Nullable", named));
		}
		runConsumer(dir, artifact.toByteArray(), "bkms");
	}

	@Test
	void builtinsInComposedPolicySurviveArtifactExecutionAndExport(@TempDir Path dir) throws Exception {
		var source = source("policy.dmn", POLICY.replace("if Include then [Fee, Fee, null, 0.125] else Prior",
				"if Include then [Fee, Fee, null, 0.125][item = null or abs(item) &gt;= Fee] else Prior"));
		var resolver = new InMemoryDmnModelResolver(List.of(source("catalog.dmn", CATALOG)));
		var producer = new DmnIrScalarProducer();
		var verified = producer.produce(source, resolver);
		var bytes = verified.artifact().toByteArray();
		assertEquals(9, verified.artifact().getSchemaVersion().getMinor());
		assertArrayEquals(bytes, producer.produce(source, resolver).artifact().toByteArray());
		var expected = Arrays.asList(new BigDecimal("30.125"), new BigDecimal("30.125"), null);
		assertEquals(expected, new DmnIrRuntimeAdapter().adapt(verified).evaluate(inputs(true)).get(key("history")));
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		DmnIrDateParityTest.validate(bundle);
		var documents = bundle.documents().entrySet().stream().map(e -> source(e.getKey(), e.getValue())).toList();
		var exported = source(bundle.rootDocument(), bundle.documents().get(bundle.rootDocument()));
		var named = Map.of("Applicant", inputs(true).get(key("applicant")), "Include", true, "External",
				inputs(true).get(key("external")));
		assertEquals(expected, evaluate(compile(source, resolver), "History", named));
		assertEquals(expected, evaluate(compile(exported, new InMemoryDmnModelResolver(documents)), "History", named));
		var captured = verified.artifact().toBuilder();
		var root = captured.getModelsBuilderList().stream().filter(model -> model.getNamespace().equals(NS)).findFirst()
				.orElseThrow();
		var history = root.getDecisionsBuilderList().stream()
				.filter(decision -> decision.getIdentity().getId().equals("history")).findFirst().orElseThrow();
		history.getExpressionBuilder().getContextBuilder().getBindingsBuilder(0).getVariableBuilder().setName("abs");
		var capturedArtifact = new DmnIrArtifactReader().read(captured.build().toByteArray());
		assertThrows(DmnIrExportException.class, () -> new DmnIrScalarDmnExporter().export(capturedArtifact));
		runConsumer(dir, bytes, "builtin");
	}

	private static Map<DmnIrRuntimeAdapter.ElementId, Object> inputs(boolean include) {
		var permit = Map.of("Expiry", LocalDate.of(2024, 2, 1), "Previous Dates",
				Arrays.asList(LocalDate.of(2024, 2, 29), null, LocalDate.of(10000, 1, 1)), "Previous Fees",
				List.of(BigDecimal.TEN));
		return Map.of(key("applicant"), Map.of("Permit", permit), key("include"), include, key("external"),
				new BigDecimal("1.25"));
	}

	private static DmnIrRuntimeAdapter.ElementId key(String id) {
		return new DmnIrRuntimeAdapter.ElementId(NS, id);
	}

	private static DmnSource source(String name, String xml) {
		return new DmnSource(DmnSourceId.of("memory:/" + name), xml.getBytes(StandardCharsets.UTF_8));
	}

	private static DmnCompilationResult compile(DmnSource source, DmnModelResolver resolver) {
		var compiled = new DmnCompiler().compile(source, resolver);
		assertTrue(compiled.isSuccess(), compiled.diagnostics().toString());
		return compiled;
	}

	private static Object evaluate(DmnCompilationResult compiled, String decision, Map<String, Object> values) {
		var model = compiled.compiledModel().orElseThrow();
		Map<Integer, Object> inputs = new HashMap<>();
		values.forEach((name, value) -> inputs.put(model.inputSlots().get(name), value));
		return new DmnRuntime().evaluate(model.optimizedModel().model(), inputs)
				.value(model.decisionSlots().get(decision));
	}
}

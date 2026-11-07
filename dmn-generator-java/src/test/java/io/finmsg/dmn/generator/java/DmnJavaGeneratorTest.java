package io.finmsg.dmn.generator.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.tools.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DmnJavaGeneratorTest {
	@Test
	void portableDatesGenerateJavaForABundledNestedPolicy(@TempDir Path tempDir) throws Exception {
		String calendar = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="calendar" name="Calendar" namespace="urn:date:calendar">
				<itemDefinition id="permit" name="tPermit"><itemComponent name="Expiry"><typeRef>date</typeRef></itemComponent></itemDefinition>
				<decision id="cutoff" name="Cutoff"><variable name="Cutoff" typeRef="date"/><literalExpression><text>date("0001-01-01")</text></literalExpression></decision></definitions>
				""";
		String policy = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" xmlns:cal="urn:date:calendar" id="policy" name="Policy" namespace="urn:date:policy">
				<import name="cal" namespace="urn:date:calendar" locationURI="calendar.dmn" importType="DMN"/>
				<itemDefinition id="applicant" name="tApplicant"><itemComponent name="Permit"><typeRef>cal:tPermit</typeRef></itemComponent></itemDefinition>
				<inputData id="input" name="Applicant"><variable name="Applicant" typeRef="tApplicant"/></inputData>
				<decision id="answer" name="Answer"><variable name="Answer" typeRef="date"/><informationRequirement><requiredInput href="#input"/></informationRequirement><informationRequirement><requiredDecision href="urn:date:calendar#cutoff"/></informationRequirement>
				<context><contextEntry><variable name="Expiry" typeRef="date"/><literalExpression><text>Applicant.Permit.Expiry</text></literalExpression></contextEntry>
				<contextEntry><literalExpression><text>if Expiry &gt;= cal.Cutoff then Expiry else null</text></literalExpression></contextEntry></context></decision></definitions>
				""";
		var imported = new DmnSource(DmnSourceId.of("memory:/calendar.dmn"),
				calendar.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var source = new DmnSource(DmnSourceId.of("memory:/policy.dmn"),
				policy.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var verified = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer().produce(source,
				new InMemoryDmnModelResolver(List.of(imported)));
		var adapted = new DmnIrRuntimeAdapter().adapt(verified);
		var generated = generator.generate(new RuntimeIrOptimizer().optimize(adapted.runtimeModel()));
		var engine = (GeneratedDecisionEngine) compileJavaSource(tempDir.resolve("artifact"), generated.mainClassName(),
				generated.mainSource()).getDeclaredConstructor().newInstance();
		var bundle = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarDmnExporter().export(verified);
		var sources = bundle.documents().entrySet().stream()
				.map(e -> new DmnSource(DmnSourceId.of("memory:/" + e.getKey()),
						e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
				.toList();
		var compiled = compiler.compile(
				new DmnSource(DmnSourceId.of("memory:/" + bundle.rootDocument()),
						bundle.documents().get(bundle.rootDocument())
								.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
				new InMemoryDmnModelResolver(sources));
		assertThat(compiled.isSuccess()).as(compiled.diagnostics().toString()).isTrue();
		var roundtrip = compiled.optimizedRuntimeModel().orElseThrow();
		var regenerated = generator.generate(roundtrip);
		var exportedEngine = (GeneratedDecisionEngine) compileJavaSource(tempDir.resolve("exported"),
				regenerated.mainClassName(), regenerated.mainSource()).getDeclaredConstructor().newInstance();
		for (Object date : Arrays.asList(java.time.LocalDate.of(-1, 1, 1), java.time.LocalDate.of(1, 1, 1),
				java.time.LocalDate.of(10000, 1, 1), java.time.LocalDate.MAX, null, "10000-01-01")) {
			Map<String, Object> permit = new LinkedHashMap<>();
			permit.put("Expiry", date);
			var applicant = Map.of("Permit", permit);
			var input = adapted.inputs().values().iterator().next();
			var expected = adapted.evaluate(Map.of(input.id(), applicant));
			Object[] slots = new Object[adapted.runtimeModel().valueSlotCount()];
			slots[input.slot()] = applicant;
			var actual = engine.evaluate(slots);
			for (var binding : adapted.decisions().values())
				assertThat(actual[binding.slot()]).isEqualTo(expected.get(binding.id()));
			Object[] exportedSlots = new Object[roundtrip.model().valueSlotCount()];
			exportedSlots[compiled.compiledModel().orElseThrow().inputSlots().get("Applicant")] = applicant;
			Object exported = exportedEngine.evaluate(exportedSlots)[compiled.compiledModel().orElseThrow()
					.decisionSlots().get("Answer")];
			assertThat(exported)
					.isEqualTo(expected.get(new DmnIrRuntimeAdapter.ElementId("urn:date:policy", "answer")));
		}
	}
	@Test
	void portableTableGeneratesJavaForComposedPolicy(@TempDir Path tempDir) throws Exception {
		assertComposedPolicy(tempDir, false);
	}

	@Test
	void portableJavaEnforcesImportedInputAndTableResultConstraints(@TempDir Path tempDir) throws Exception {
		assertComposedPolicy(tempDir, true);
	}

	private void assertComposedPolicy(Path tempDir, boolean constrained) throws Exception {
		String calendar = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="calendar" name="Calendar" namespace="urn:tables:calendar">
				<itemDefinition id="permit" name="tPermit"><itemComponent name="Expiry"><typeRef>date</typeRef></itemComponent></itemDefinition>
				<decision id="cutoff" name="Cutoff"><variable name="Cutoff" typeRef="date"/><literalExpression><text>date("2024-02-01")</text></literalExpression></decision>
				</definitions>
				""";
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" xmlns:cal="urn:tables:calendar" id="model" name="Policy" namespace="urn:generated:tables">
				<import name="cal" namespace="urn:tables:calendar" locationURI="calendar.dmn" importType="DMN"/>
				<itemDefinition id="applicant" name="tApplicant"><itemComponent name="Permit"><typeRef>cal:tPermit</typeRef></itemComponent></itemDefinition>
				<itemDefinition id="outcome" name="tOutcome"><itemComponent name="Fee"><typeRef>number</typeRef></itemComponent>
				<itemComponent name="Reason"><typeRef>string</typeRef></itemComponent></itemDefinition>
				<inputData id="input" name="Applicant"><variable name="Applicant" typeRef="tApplicant"/></inputData>
				<decision id="decision" name="Outcome"><variable name="Outcome" typeRef="tOutcome"/>
				<informationRequirement><requiredInput href="#input"/></informationRequirement>
				<informationRequirement><requiredDecision href="urn:tables:calendar#cutoff"/></informationRequirement>
				<decisionTable hitPolicy="FIRST">
				<input><inputExpression typeRef="date"><text>Applicant.Permit.Expiry</text></inputExpression></input>
				<output name="Fee" typeRef="number"><defaultOutputEntry><text>100</text></defaultOutputEntry></output>
				<output name="Reason" typeRef="string"/>
				<rule><inputEntry><text>[cal.Cutoff .. date("2024-03-01")), date("2024-04-01")</text></inputEntry>
				<outputEntry><text>0</text></outputEntry><outputEntry><text>"preferred"</text></outputEntry></rule>
				<rule><inputEntry><text>not(&lt; cal.Cutoff, null)</text></inputEntry>
				<outputEntry><text>30</text></outputEntry><outputEntry><text>"regular"</text></outputEntry></rule>
				</decisionTable></decision>
				<decision id="answer" name="Answer"><variable name="Answer" typeRef="number"/>
				<informationRequirement><requiredDecision href="#decision"/></informationRequirement>
				<context><contextEntry><variable name="Base Fee" typeRef="number"/><literalExpression><text>Outcome.Fee</text></literalExpression></contextEntry>
				<contextEntry><literalExpression><text>if Outcome.Reason = null then Base Fee else Base Fee + 5</text></literalExpression></contextEntry></context>
				</decision></definitions>
				""";
		if (constrained) {
			calendar = calendar.replace("<typeRef>date</typeRef>",
					"""
							<typeRef>date</typeRef><allowedValues><text>[date("2024-01-01") .. date("2024-04-01")), null</text></allowedValues>
							""");
			xml = xml.replace("<typeRef>number</typeRef>",
					"<typeRef>number</typeRef><typeConstraint><text>[-1 .. 100)</text></typeConstraint>");
		}
		var imported = new DmnSource(DmnSourceId.of("memory:/calendar.dmn"),
				calendar.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var source = new DmnSource(DmnSourceId.of("memory:/tables.dmn"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var artifact = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer().produce(source,
				new InMemoryDmnModelResolver(List.of(imported)));
		var model = new DmnIrRuntimeAdapter().adapt(new DmnIrArtifactReader().read(artifact.artifact().toByteArray()));
		var generated = generator.generate(new RuntimeIrOptimizer().optimize(model.runtimeModel()));
		var engine = (GeneratedDecisionEngine) compileJavaSource(tempDir, generated.mainClassName(),
				generated.mainSource()).getDeclaredConstructor().newInstance();
		var input = model.inputs().values().iterator().next();
		for (var date : Arrays.asList(java.time.LocalDate.of(2024, 1, 1), java.time.LocalDate.of(2024, 2, 1),
				java.time.LocalDate.of(2024, 2, 29), java.time.LocalDate.of(2024, 3, 1),
				java.time.LocalDate.of(2024, 4, 1), null)) {
			Map<String, Object> permit = new HashMap<>();
			permit.put("Expiry", date);
			var applicant = Map.of("Permit", permit);
			Object[] slots = new Object[model.runtimeModel().valueSlotCount()];
			slots[input.slot()] = applicant;
			if (constrained && (date == null || date.getMonthValue() == 1 || date.getMonthValue() == 4)) {
				var expected = org.junit.jupiter.api.Assertions.assertThrows(
						io.finmsg.dmn.runtime.DmnEvaluationException.class,
						() -> model.evaluate(Map.of(input.id(), applicant)));
				org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.evaluate(slots))
						.isInstanceOf(expected.getClass()).hasMessage(expected.getMessage());
				continue;
			}
			var expected = model.evaluate(Map.of(input.id(), applicant));
			Object[] actual = engine.evaluate(slots);
			for (var decision : model.decisions().values())
				assertThat(actual[decision.slot()]).isEqualTo(expected.get(decision.id()));
		}
	}

	@Test
	void portableTrafficViolationGeneratesBothDecisions(@TempDir Path tempDir) throws Exception {
		DmnSource source;
		try (var fixture = Objects.requireNonNull(getClass().getResourceAsStream("/TrafficViolation.dmn"))) {
			source = new DmnSource(DmnSourceId.of("memory:/traffic-violation.dmn"), fixture.readAllBytes());
		}
		var model = new DmnIrRuntimeAdapter()
				.adapt(new io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer().produce(source));
		var generated = generator.generate(new RuntimeIrOptimizer().optimize(model.runtimeModel()));
		var engine = (GeneratedDecisionEngine) compileJavaSource(tempDir, generated.mainClassName(),
				generated.mainSource()).getDeclaredConstructor().newInstance();
		var driver = Map.of("Name", "Example", "Age", 40, "State", "Example", "City", "Example", "Points", 17);
		var violation = new HashMap<String, Object>(
				Map.of("Code", "example", "Date", java.time.LocalDate.of(2024, 1, 1), "Type", "speed", "SpeedLimit",
						BigDecimal.valueOf(50), "ActualSpeed", BigDecimal.valueOf(60)));
		Map<DmnIrRuntimeAdapter.ElementId, Object> inputs = new HashMap<>();
		Object[] slots = new Object[model.runtimeModel().valueSlotCount()];
		for (var binding : model.inputs().values()) {
			Object value = binding.variableName().equals("Driver") ? driver : violation;
			inputs.put(binding.id(), value);
			slots[binding.slot()] = value;
		}
		var expected = model.evaluate(inputs);
		var actual = engine.evaluate(slots);
		for (var binding : model.decisions().values())
			assertThat(actual[binding.slot()]).isEqualTo(expected.get(binding.id()));
		violation.put("Type", "other");
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.evaluate(slots))
				.isInstanceOf(io.finmsg.dmn.runtime.DmnEvaluationException.class)
				.hasMessage("Field constraint violated at /Type");
	}

	@Test
	void portableJavaEnforcesSingleResultHitPoliciesAndRetainsNullFields(@TempDir Path tempDir) throws Exception {
		String table = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="model" name="Policy" namespace="urn:generated:table-policies">
				<itemDefinition id="outcome" name="tOutcome"><itemComponent name="Fee"><typeRef>number</typeRef></itemComponent>
				<itemComponent name="Reason"><typeRef>string</typeRef></itemComponent></itemDefinition>
				<inputData id="input" name="Amount"><variable name="Amount" typeRef="number"/></inputData>
				<decision id="answer" name="Outcome"><variable name="Outcome" typeRef="tOutcome"/>
				<informationRequirement><requiredInput href="#input"/></informationRequirement>
				<decisionTable hitPolicy="%s">
				<input><inputExpression typeRef="number"><text>Amount</text></inputExpression></input>
				<output name="Fee" typeRef="number"/><output name="Reason" typeRef="string"/>
				<rule><inputEntry><text>&gt; 0</text></inputEntry><outputEntry><text>1</text></outputEntry><outputEntry><text>null</text></outputEntry></rule>
				<rule><inputEntry><text>&gt; 1</text></inputEntry><outputEntry><text>%d</text></outputEntry><outputEntry><text>null</text></outputEntry></rule>
				</decisionTable></decision></definitions>
				""";
		for (String policy : List.of("UNIQUE", "ANY")) {
			for (int secondFee : List.of(1, 2)) {
				var source = new DmnSource(DmnSourceId.of("memory:/policy.dmn"),
						table.formatted(policy, secondFee).getBytes(java.nio.charset.StandardCharsets.UTF_8));
				var model = new DmnIrRuntimeAdapter()
						.adapt(new io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer().produce(source));
				var generated = generator.generate(new RuntimeIrOptimizer().optimize(model.runtimeModel()));
				var engine = (GeneratedDecisionEngine) compileJavaSource(tempDir.resolve(policy + secondFee),
						generated.mainClassName(), generated.mainSource()).getDeclaredConstructor().newInstance();
				var input = model.inputs().values().iterator().next();
				var decision = model.decisions().values().iterator().next();
				for (int amount : List.of(0, 1, 2)) {
					Object[] slots = new Object[model.runtimeModel().valueSlotCount()];
					slots[input.slot()] = amount;
					if (amount == 2 && (policy.equals("UNIQUE") || secondFee != 1)) {
						var expected = org.junit.jupiter.api.Assertions.assertThrows(
								io.finmsg.dmn.runtime.DmnEvaluationException.class,
								() -> model.evaluate(Map.of(input.id(), amount)));
						org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.evaluate(slots))
								.isInstanceOf(expected.getClass()).hasMessage(expected.getMessage());
					} else {
						var expected = model.evaluate(Map.of(input.id(), amount)).get(decision.id());
						assertThat(engine.evaluate(slots)[decision.slot()]).isEqualTo(expected);
					}
				}
			}
		}
	}

	@Test
	void portableLocalBindingsGenerateExecutableJava(@TempDir Path tempDir) throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="model" name="Locals" namespace="urn:generated:locals">
				<inputData id="input" name="Amount"><variable name="Amount" typeRef="number"/></inputData>
				<decision id="answer" name="Answer"><variable name="Answer" typeRef="number"/><informationRequirement><requiredInput href="#input"/></informationRequirement>
				<context><contextEntry><variable name="Base" typeRef="number"/><literalExpression><text>Amount + 1</text></literalExpression></contextEntry>
				<contextEntry><variable name="Total" typeRef="number"/><context><contextEntry><variable name="Bonus" typeRef="number"/><literalExpression><text>2</text></literalExpression></contextEntry>
				<contextEntry><literalExpression><text>Base + Bonus</text></literalExpression></contextEntry></context></contextEntry>
				<contextEntry><literalExpression><text>Total</text></literalExpression></contextEntry></context></decision></definitions>
				""";
		var verified = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer().produce(new DmnSource(
				DmnSourceId.of("memory:/locals.dmn"), xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		var adapted = new DmnIrRuntimeAdapter().adapt(verified);
		var generated = generator.generate(new RuntimeIrOptimizer().optimize(adapted.runtimeModel()));
		var engine = (GeneratedDecisionEngine) compileJavaSource(tempDir.resolve("artifact"), generated.mainClassName(),
				generated.mainSource()).getDeclaredConstructor().newInstance();
		var bundle = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarDmnExporter().export(verified);
		var compiled = compiler.compile(new DmnSource(DmnSourceId.of("memory:/exported.dmn"),
				bundle.documents().get(bundle.rootDocument()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compiled.isSuccess()).as(compiled.diagnostics().toString()).isTrue();
		var roundtrip = compiled.optimizedRuntimeModel().orElseThrow();
		var regenerated = generator.generate(roundtrip);
		var exportedEngine = (GeneratedDecisionEngine) compileJavaSource(tempDir.resolve("exported"),
				regenerated.mainClassName(), regenerated.mainSource()).getDeclaredConstructor().newInstance();
		for (Object amount : Arrays.asList(BigDecimal.ZERO, new BigDecimal("9007199254740993.0001"), null,
				BigDecimal.TEN)) {
			Map<DmnIrRuntimeAdapter.ElementId, Object> inputs = new HashMap<>();
			inputs.put(adapted.inputs().keySet().iterator().next(), amount);
			var expected = adapted.evaluate(inputs).values().iterator().next();
			Object[] slots = new Object[adapted.runtimeModel().valueSlotCount()];
			slots[adapted.inputs().values().iterator().next().slot()] = amount;
			Object actual = engine.evaluate(slots)[adapted.decisions().values().iterator().next().slot()];
			Object[] exportedSlots = new Object[roundtrip.model().valueSlotCount()];
			exportedSlots[roundtrip.model().inputs().getFirst().valueSlot()] = amount;
			Object exported = exportedEngine.evaluate(exportedSlots)[roundtrip.model().decisions().getFirst()
					.resultSlot()];
			if (expected instanceof BigDecimal n) {
				assertThat((BigDecimal) actual).isEqualByComparingTo(n);
				assertThat((BigDecimal) exported).isEqualByComparingTo(n);
			} else {
				assertThat(actual).isEqualTo(expected);
				assertThat(exported).isEqualTo(expected);
			}
		}
	}
	@Test
	void portableRecordsGenerateExecutableJavaAndExportedDmn(@TempDir Path tempDir) throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="model" name="Records" namespace="urn:example:generated-records">
				<itemDefinition id="driver-type" name="tDriver"><itemComponent name="Name"><typeRef>string</typeRef></itemComponent><itemComponent name="Points"><typeRef>number</typeRef></itemComponent></itemDefinition>
				<itemDefinition id="fine-type" name="tFine"><itemComponent name="Points"><typeRef>number</typeRef></itemComponent></itemDefinition>
				<inputData id="Driver" name="Driver"><variable name="Driver" typeRef="tDriver"/></inputData>
				<inputData id="Fine" name="Fine"><variable name="Fine" typeRef="tFine"/></inputData>
				<decision id="Total" name="Total"><variable name="Total" typeRef="number"/>
				<informationRequirement><requiredInput href="#Driver"/></informationRequirement><informationRequirement><requiredInput href="#Fine"/></informationRequirement>
				<literalExpression><text>Driver.Points + Fine.Points</text></literalExpression></decision>
				<decision id="Copy" name="Copy"><variable name="Copy" typeRef="tDriver"/><informationRequirement><requiredInput href="#Driver"/></informationRequirement>
				<literalExpression><text>Driver</text></literalExpression></decision></definitions>
				""";
		var verified = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer().produce(new DmnSource(
				DmnSourceId.of("memory:/records.dmn"), xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		var adapted = new DmnIrRuntimeAdapter().adapt(verified);
		assertThat(verified.artifact().getSchemaVersion().getMinor()).isEqualTo(2);
		var generated = generator.generate(new RuntimeIrOptimizer().optimize(adapted.runtimeModel()));
		var engine = (GeneratedDecisionEngine) compileJavaSource(tempDir.resolve("artifact"), generated.mainClassName(),
				generated.mainSource()).getDeclaredConstructor().newInstance();
		var bundle = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarDmnExporter().export(verified);
		var compiled = compiler.compile(new DmnSource(DmnSourceId.of("memory:/" + bundle.rootDocument()),
				bundle.documents().get(bundle.rootDocument()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compiled.isSuccess()).as(compiled.diagnostics().toString()).isTrue();
		var facade = compiled.compiledModel().orElseThrow();
		var regenerated = generator.generate(compiled.optimizedRuntimeModel().orElseThrow());
		var exportedEngine = (GeneratedDecisionEngine) compileJavaSource(tempDir.resolve("exported"),
				regenerated.mainClassName(), regenerated.mainSource()).getDeclaredConstructor().newInstance();
		Map<String, Object> nullable = new LinkedHashMap<>();
		nullable.put("Name", "Synthetic");
		nullable.put("Points", null);
		for (var driver : List.of(Map.of("Name", "Synthetic", "Points", new BigDecimal("15.0001")),
				Map.of("Name", "Synthetic", "Points", new BigDecimal("15"), "Extra", true), nullable,
				Map.of("Missing", BigDecimal.ONE))) {
			Map<String, Object> inputs = Map.of("Driver", driver, "Fine", Map.of("Points", new BigDecimal("7")));
			Map<DmnIrRuntimeAdapter.ElementId, Object> addressed = new LinkedHashMap<>();
			Object[] slots = new Object[adapted.runtimeModel().valueSlotCount()];
			for (var binding : adapted.inputs().values()) {
				addressed.put(binding.id(), inputs.get(binding.variableName()));
				slots[binding.slot()] = inputs.get(binding.variableName());
			}
			var expected = adapted.evaluate(addressed);
			var actual = engine.evaluate(slots);
			Object[] exportedSlots = new Object[facade.optimizedModel().model().valueSlotCount()];
			facade.inputSlots().forEach((name, slot) -> exportedSlots[slot] = inputs.get(name));
			var reexecuted = exportedEngine.evaluate(exportedSlots);
			for (var binding : adapted.decisions().values()) {
				Object value = expected.get(binding.id());
				Object first = actual[binding.slot()];
				Object second = reexecuted[facade.decisionSlots().get(binding.variableName())];
				if (value instanceof BigDecimal n) {
					assertThat((BigDecimal) first).isEqualByComparingTo(n);
					assertThat((BigDecimal) second).isEqualByComparingTo(n);
				} else if (value instanceof Map<?, ?> map) {
					assertThat(new LinkedHashMap<>((Map<?, ?>) first)).isEqualTo(new LinkedHashMap<>(map));
					assertThat(new LinkedHashMap<>((Map<?, ?>) second)).isEqualTo(new LinkedHashMap<>(map));
				} else {
					assertThat(first).isEqualTo(value);
					assertThat(second).isEqualTo(value);
				}
			}
		}
	}

	@Test
	void structuredPortableArtifactGeneratesExecutableJavaAndDmnParity(@TempDir Path tempDir) throws Exception {
		String[][] examples = {{"(A + B) * (-B) + A ** 2", "number"},
				{"if A - B >= 10 then \"Yes\" else \"No\"", "string"},
				{"not(Flag) or ((A = B) and (A <= B))", "boolean"}, {"if Flag then A / B else A ** B", "number"}};
		for (int i = 0; i < examples.length; i++) {
			String xml = """
					<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="model" name="Expressions" namespace="urn:example:generated-expressions">
					<inputData id="A" name="A"><variable name="A" typeRef="number"/></inputData>
					<inputData id="B" name="B"><variable name="B" typeRef="number"/></inputData>
					<inputData id="Flag" name="Flag"><variable name="Flag" typeRef="boolean"/></inputData>
					<decision id="result" name="Result"><variable name="Result" typeRef="%s"/>
					<informationRequirement><requiredInput href="#A"/></informationRequirement>
					<informationRequirement><requiredInput href="#B"/></informationRequirement>
					<informationRequirement><requiredInput href="#Flag"/></informationRequirement>
					<literalExpression><text>%s</text></literalExpression></decision></definitions>
					"""
					.formatted(examples[i][1], examples[i][0].replace("<", "&lt;"));
			var source = new DmnSource(DmnSourceId.of("memory:/expressions.dmn"),
					xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			var verified = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer().produce(source);
			assertThat(verified.artifact().getSchemaVersion().getMinor()).isEqualTo(1);
			if (i == 0) {
				var artifact = verified.artifact().toBuilder();
				var decision = artifact.getModelsBuilder(0).getDecisionsBuilder(0);
				var operand = decision.getExpression();
				decision.setExpression(DmnIrExpression.newBuilder().setType(operand.getType())
						.setUnary(DmnIrUnaryExpression.newBuilder()
								.setOperator(DmnIrUnaryOperator.UNARY_OPERATOR_POSITIVE).setOperand(operand)));
				verified = new DmnIrArtifactReader().read(artifact.build().toByteArray());
			}
			var adapted = new DmnIrRuntimeAdapter()
					.adapt(new DmnIrArtifactReader().read(verified.artifact().toByteArray()));
			var optimized = new RuntimeIrOptimizer().optimize(adapted.runtimeModel());
			var generated = generator.generate(optimized);
			assertThat(generator.generate(optimized).mainSource()).isEqualTo(generated.mainSource());
			var engine = (GeneratedDecisionEngine) compileJavaSource(tempDir.resolve("artifact-" + i),
					generated.mainClassName(), generated.mainSource()).getDeclaredConstructor().newInstance();
			var bundle = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarDmnExporter().export(verified);
			var exported = compiler.compile(new DmnSource(DmnSourceId.of("memory:/" + bundle.rootDocument()),
					bundle.documents().get(bundle.rootDocument()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
			assertThat(exported.isSuccess()).as(exported.diagnostics().toString()).isTrue();
			var exportedIr = exported.optimizedRuntimeModel().orElseThrow();
			var generatedAgain = generator.generate(exportedIr);
			var exportedEngine = (GeneratedDecisionEngine) compileJavaSource(tempDir.resolve("dmn-" + i),
					generatedAgain.mainClassName(), generatedAgain.mainSource()).getDeclaredConstructor().newInstance();
			for (boolean flag : List.of(true, false)) {
				Map<String, Object> inputs = Map.of("A", new BigDecimal("19.00000000000000001"), "B",
						new BigDecimal(flag ? "2" : "0"), "Flag", flag);
				Map<DmnIrRuntimeAdapter.ElementId, Object> addressed = new LinkedHashMap<>();
				Object[] slots = new Object[adapted.runtimeModel().valueSlotCount()];
				for (var binding : adapted.inputs().values()) {
					addressed.put(binding.id(), inputs.get(binding.variableName()));
					slots[binding.slot()] = inputs.get(binding.variableName());
				}
				Object expected = adapted.evaluate(addressed).values().iterator().next();
				Object actual = engine.evaluate(slots)[adapted.decisions().values().iterator().next().slot()];
				if (expected instanceof BigDecimal n)
					assertThat((BigDecimal) actual).isEqualByComparingTo(n);
				else
					assertThat(actual).isEqualTo(expected);
				Object[] exportedSlots = new Object[exportedIr.model().valueSlotCount()];
				// Emitted declarations are sorted by stable identity: A, B, Flag.
				exportedSlots[0] = inputs.get("A");
				exportedSlots[1] = inputs.get("B");
				exportedSlots[2] = inputs.get("Flag");
				Object reexecuted = exportedEngine.evaluate(exportedSlots)[exportedIr.model().decisions().getFirst()
						.resultSlot()];
				if (expected instanceof BigDecimal n)
					assertThat((BigDecimal) reexecuted).isEqualByComparingTo(n);
				else
					assertThat(reexecuted).isEqualTo(expected);
			}
		}
	}

	@Test
	void producedArtifactGeneratesExecutableJava(@TempDir Path tempDir) throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="model" name="Produced" namespace="urn:example:produced">
				<inputData id="input" name="Amount"><variable name="Amount" typeRef="number"/></inputData>
				<decision id="copy" name="Copy"><variable name="Copy" typeRef="number"/>
				<informationRequirement><requiredInput href="#input"/></informationRequirement>
				<literalExpression><text>Amount</text></literalExpression></decision>
				<decision id="precise" name="Precise"><variable name="Precise" typeRef="number"/>
				<literalExpression><text>9007199254740993.0001</text></literalExpression></decision>
				</definitions>
				""";
		var source = new DmnSource(DmnSourceId.of("memory:/produced.dmn"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		byte[] bytes = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer().produce(source).artifact().toByteArray();
		var adapted = new DmnIrRuntimeAdapter().adapt(new DmnIrArtifactReader().read(bytes));
		var optimized = new RuntimeIrOptimizer().optimize(adapted.runtimeModel());
		var generated = generator.generate(optimized);
		assertThat(generator.generate(optimized).mainSource()).isEqualTo(generated.mainSource());
		var engine = (GeneratedDecisionEngine) compileJavaSource(tempDir, generated.mainClassName(),
				generated.mainSource()).getDeclaredConstructor().newInstance();
		var input = adapted.inputs().values().iterator().next();
		Object[] slots = new Object[adapted.runtimeModel().valueSlotCount()];
		slots[input.slot()] = new BigDecimal("42.00");
		var values = engine.evaluate(slots);
		var expected = adapted.evaluate(Map.of(input.id(), new BigDecimal("42.00")));
		for (var binding : adapted.decisions().values())
			assertThat((BigDecimal) values[binding.slot()])
					.isEqualByComparingTo((BigDecimal) expected.get(binding.id()));

		var bundle = new io.finmsg.dmn.compiler.dmnir.DmnIrScalarDmnExporter().export(adapted.source());
		var exportedSource = new DmnSource(DmnSourceId.of("memory:/" + bundle.rootDocument()),
				bundle.documents().get(bundle.rootDocument()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var recompiled = compiler.compile(exportedSource);
		assertThat(recompiled.isSuccess()).as(recompiled.diagnostics().toString()).isTrue();
		var exportedIr = recompiled.optimizedRuntimeModel().orElseThrow();
		var regenerated = generator.generate(exportedIr);
		var exportedEngine = (GeneratedDecisionEngine) compileJavaSource(tempDir.resolve("exported"),
				regenerated.mainClassName(), regenerated.mainSource()).getDeclaredConstructor().newInstance();
		Object[] exportedSlots = new Object[exportedIr.model().valueSlotCount()];
		int exportedInput = exportedIr.model().inputs().getFirst().valueSlot();
		exportedSlots[exportedInput] = new BigDecimal("42.00");
		var exportedValues = exportedEngine.evaluate(exportedSlots);
		var interpreted = runtime.evaluate(exportedIr.model(), Map.of(exportedInput, new BigDecimal("42.00")));
		for (var decision : exportedIr.model().decisions())
			assertThat((BigDecimal) exportedValues[decision.resultSlot()])
					.isEqualByComparingTo((BigDecimal) interpreted.value(decision.resultSlot()));
	}

	@Test
	void verifiedPortableArtifactGeneratesCompilableJavaWithScalarParity(@TempDir Path tempDir) throws Exception {
		String namespace = "urn:example:portable";
		var inputRef = DmnIrReference.newBuilder().setModelNamespace(namespace).setElementId("input").build();
		var root = DmnIrModel.newBuilder()
				.setIdentity(DmnIrElementIdentity.newBuilder().setId("model").setName("Portable"))
				.setNamespace(namespace)
				.addInputs(DmnIrInput.newBuilder()
						.setIdentity(DmnIrElementIdentity.newBuilder().setId("input").setName("Amount"))
						.setVariable(DmnIrVariable.newBuilder().setId("input-var").setName("Amount")
								.setType(DmnIrScalarType.SCALAR_TYPE_NUMBER)))
				.addDecisions(DmnIrDecision.newBuilder()
						.setIdentity(DmnIrElementIdentity.newBuilder().setId("alias").setName("Alias"))
						.setVariable(DmnIrVariable.newBuilder().setId("alias-var").setName("Alias")
								.setType(DmnIrScalarType.SCALAR_TYPE_NUMBER))
						.addDependencies(inputRef).setExpression(DmnIrExpression.newBuilder()
								.setType(DmnIrScalarType.SCALAR_TYPE_NUMBER).setGlobalReference(inputRef)));
		root.addDecisions(portableLiteral("precise", DmnIrScalarType.SCALAR_TYPE_NUMBER,
				DmnIrLiteral.newBuilder().setNumberValue(
						DmnIrDecimal.newBuilder().setUnscaledValue("90071992547409930000000000000000001").setScale(19))
						.build()));
		root.addDecisions(portableLiteral("exponent", DmnIrScalarType.SCALAR_TYPE_NUMBER, DmnIrLiteral.newBuilder()
				.setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("1").setScale(-30)).build()));
		root.addDecisions(portableLiteral("empty", DmnIrScalarType.SCALAR_TYPE_STRING,
				DmnIrLiteral.newBuilder().setStringValue("").build()));
		root.addDecisions(portableLiteral("text", DmnIrScalarType.SCALAR_TYPE_STRING,
				DmnIrLiteral.newBuilder().setStringValue("quote\" newline\nbackslash\\ Unicode é").build()));
		root.addDecisions(portableLiteral("false", DmnIrScalarType.SCALAR_TYPE_BOOLEAN,
				DmnIrLiteral.newBuilder().setBooleanValue(false).build()));
		root.addDecisions(portableLiteral("nullable", DmnIrScalarType.SCALAR_TYPE_NUMBER,
				DmnIrLiteral.newBuilder().setNullValue(DmnIrNull.NULL_VALUE).build()));
		var artifact = DmnIrArtifact.newBuilder()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(0))
				.setSemanticsVersion(DmnIrArtifactReader.SEMANTICS_VERSION)
				.addRequiredCapabilities(DmnIrArtifactReader.SCALAR_CAPABILITY).setRootModelNamespace(namespace)
				.addModels(root);
		for (String id : List.of("alias", "precise", "exponent", "empty", "text", "false", "nullable")) {
			artifact.addEvaluationOrder(DmnIrReference.newBuilder().setModelNamespace(namespace).setElementId(id));
		}
		var adapted = new DmnIrRuntimeAdapter().adapt(new DmnIrArtifactReader().read(artifact.build().toByteArray()));
		var optimized = new RuntimeIrOptimizer().optimize(adapted.runtimeModel());
		var options = DmnJavaGeneratorOptions.of("io.finmsg.dmn.generated", "PortableScalarModel");
		var generated = generator.generate(optimized, options);
		assertThat(generator.generate(optimized, options).mainSource()).isEqualTo(generated.mainSource());
		Class<?> compiled = compileJavaSource(tempDir, generated.mainClassName(), generated.mainSource());
		var engine = (GeneratedDecisionEngine) compiled.getDeclaredConstructor().newInstance();
		var inputId = new DmnIrRuntimeAdapter.ElementId(namespace, "input");
		Object[] inputSlots = new Object[adapted.runtimeModel().valueSlotCount()];
		inputSlots[adapted.inputs().get(inputId).slot()] = new BigDecimal("123.4500");
		Object[] generatedValues = engine.evaluate(inputSlots);
		var expected = adapted.evaluate(Map.of(inputId, new BigDecimal("123.4500")));
		for (var binding : adapted.decisions().values()) {
			Object referenceValue = expected.get(binding.id());
			Object generatedValue = generatedValues[binding.slot()];
			if (referenceValue instanceof BigDecimal left && generatedValue instanceof BigDecimal right) {
				assertThat(right).isEqualByComparingTo(left);
			} else {
				assertThat(generatedValue).isEqualTo(referenceValue);
			}
		}
	}

	private static DmnIrDecision portableLiteral(String id, DmnIrScalarType type, DmnIrLiteral value) {
		return DmnIrDecision.newBuilder().setIdentity(DmnIrElementIdentity.newBuilder().setId(id).setName(id))
				.setVariable(DmnIrVariable.newBuilder().setId(id + "-var").setName(id).setType(type))
				.setExpression(DmnIrExpression.newBuilder().setType(type).setLiteral(value)).build();
	}

	@Test
	void invalidRuntimePredicatesAndUnconstrainedDurationsPreserveParity(@TempDir Path tempDir) throws Exception {
		List<String> expressions = List.of("duration(null)", "duration(\"\")", "duration(2017)", "duration(\"P\")",
				"duration([])", "[1,2,3,4][if item <= 3 then true else \"bad\"]",
				"some x in [1,2] satisfies if x = 2 then true else \"bad\"",
				"every x in [1,2] satisfies if x = 1 then true else \"bad\"",
				"some x in [1,2] satisfies if x = 1 then true else \"bad\"",
				"every x in [1,2] satisfies if x = 1 then false else \"bad\"", "duration(\"P1D\")",
				"if true then 7 else \"text\"", "\"x\" - 2", "2 ** \"2\"", "-\"2\"", "-date(\"2020-01-01\")",
				"null between 1 and 2", "1 between null and 2", "1 between 0 and null", "{foo:1,foo:2}", "null.b",
				"non_existing_function()", "some x in 1 satisfies \"bad\"", "every x in 1 satisfies \"bad\"",
				"[1,2][\"bad\"]", "if 1 then 2 else 3", "-\"x\"", "1+2", "\"a\"+\"b\"", "some x in 1 satisfies true",
				"every x in 1 satisfies true", "\"text\"");
		List<Object> expected = Arrays.asList(null, null, null, null, null, null, null, null, true, false,
				java.time.Duration.ofDays(1), BigDecimal.valueOf(7), null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, BigDecimal.valueOf(3), "ab", true, true, null);
		StringBuilder decisions = new StringBuilder();
		for (int i = 0; i < expressions.size(); i++) {
			decisions.append("<decision id=\"d" + i + "\" name=\"d" + i + "\"><variable name=\"d" + i + "\" typeRef=\""
					+ (i == expressions.size() - 1 ? "number" : "Loose") + "\"/>" + "<literalExpression><text>"
					+ expressions.get(i).replace("&", "&amp;").replace("<", "&lt;")
					+ "</text></literalExpression></decision>");
		}
		String xml = "<definitions xmlns=\"https://www.omg.org/spec/DMN/20230324/MODEL/\" id=\"d\" name=\"d\" namespace=\"urn:test\">"
				+ "<itemDefinition id=\"Loose\" name=\"Loose\"/>" + decisions + "</definitions>";
		var compilation = compiler.compile(new DmnSource(DmnSourceId.of("memory:runtime-errors"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).isTrue();
		var optimized = compilation.optimizedRuntimeModel().orElseThrow();
		var generated = generator.generate(optimized);
		var type = compileJavaSource(tempDir, generated.mainClassName(), generated.mainSource());
		var engine = (GeneratedDecisionEngine) type.getDeclaredConstructor().newInstance();
		var values = engine.evaluate(new Object[optimized.model().valueSlotCount()]);
		var interpreted = runtime.evaluate(optimized.model(), Map.of());
		for (int i = 0; i < optimized.model().decisions().size(); i++) {
			var decision = optimized.model().decisions().get(i);
			assertThat(interpreted.value(decision.resultSlot())).as(expressions.get(i)).isEqualTo(expected.get(i));
			assertThat(values[decision.resultSlot()]).as(expressions.get(i)).isEqualTo(expected.get(i));
		}
	}

	@Test
	void externalCallsRequireInstancePolicyAndDoNotAuthorizeOtherInstances(@TempDir Path tempDir) throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="d" namespace="urn:test">
				<decision id="result" name="result"><variable name="result"/>
				<literalExpression><text>(function(x) external {java:{class:"java.lang.Math",method signature:"abs(int)"}})(-7)</text></literalExpression>
				</decision></definitions>
				""";
		var compilation = compiler.compile(new DmnSource(DmnSourceId.of("memory:external-policy"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).isTrue();
		var optimized = compilation.optimizedRuntimeModel().orElseThrow();
		var policy = io.finmsg.dmn.runtime.ExternalJavaPolicy
				.allowOnly(Set.of(new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Math", "abs(int)")));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.evaluate(optimized.model(), Map.of()))
				.hasMessageStartingWith("EXTERNAL_JAVA_DENIED:");
		assertThat(new DmnRuntime(policy).evaluate(optimized.model(), Map.of())
				.decisionValue(optimized.model().decisions().getFirst().id())).isEqualTo(BigDecimal.valueOf(7));
		var generated = generator.generate(optimized);
		var type = compileJavaSource(tempDir, generated.mainClassName(), generated.mainSource());
		var denied = (GeneratedDecisionEngine) type.getDeclaredConstructor().newInstance();
		var approved = (GeneratedDecisionEngine) type
				.getDeclaredConstructor(io.finmsg.dmn.runtime.ExternalJavaPolicy.class).newInstance(policy);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> denied.evaluate(new Object[0]))
				.hasMessageStartingWith("EXTERNAL_JAVA_DENIED:");
		int slot = optimized.model().decisions().getFirst().resultSlot();
		assertThat(approved.evaluate(new Object[0])[slot]).isEqualTo(BigDecimal.valueOf(7));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> denied.evaluate(new Object[0]))
				.hasMessageStartingWith("EXTERNAL_JAVA_DENIED:");
	}

	private final DmnCompiler compiler = new DmnCompiler();
	private final DmnRuntime runtime = new DmnRuntime();
	private final DmnJavaGenerator generator = new DmnJavaGenerator();

	@Test
	void comparatorCannotSwallowExternalPolicyDenial(@TempDir Path tempDir) throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="d" namespace="urn:test">
				<decision id="result" name="result"><variable name="result"/>
				<literalExpression><text>sort([2,1], function(a,b) (function(x) external {java:{class:"java.lang.Math",method signature:"abs(int)"}})(a) > 0)</text></literalExpression>
				</decision></definitions>
				""";
		var compilation = compiler.compile(new DmnSource(DmnSourceId.of("memory:comparator-policy"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).isTrue();
		var optimized = compilation.optimizedRuntimeModel().orElseThrow();
		var generated = generator.generate(optimized);
		var type = compileJavaSource(tempDir, generated.mainClassName(), generated.mainSource());
		var engine = (GeneratedDecisionEngine) type.getDeclaredConstructor().newInstance();
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.evaluate(new Object[0]))
				.isInstanceOf(io.finmsg.dmn.runtime.ExternalJavaDeniedException.class);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.evaluate(optimized.model(), Map.of()))
				.isInstanceOf(io.finmsg.dmn.runtime.ExternalJavaDeniedException.class);
	}

	@Test
	void sortComparatorsPreserveRuntimeValuesAndBindings(@TempDir Path tempDir) throws Exception {
		List<String> expressions = List.of("sort([3,1,2], function(a,b) a < b)", "sort([3,1,2], function(a,b) a > b)",
				"sort([3,1,2], function(a,b) true)", "sort([3,1,2], function(a,b) false)",
				"sort([3,1,2], function(a,b) null)", "sort([2,1,2,1], function(a,b) if a = b then null else a < b)",
				"{direction: -1, cmp: function(a,b) a * direction < b * direction, result: sort([3,1,2], cmp)}.result",
				"{cmp: function(left,right) left < right, result: sort(list: [3,1,2], precedes: cmp)}.result");
		for (int i = 0; i < expressions.size(); i++) {
			String xml = """
					<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="d" namespace="urn:test">
					<decision id="result" name="result"><variable name="result"/>
					<literalExpression><text>%s</text></literalExpression></decision></definitions>
					"""
					.formatted(expressions.get(i).replace("&", "&amp;").replace("<", "&lt;"));
			var compilation = compiler.compile(new DmnSource(DmnSourceId.of("memory:sort-" + i),
					xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
			assertThat(compilation.isSuccess()).as(expressions.get(i)).isTrue();
			var optimized = compilation.optimizedRuntimeModel().orElseThrow();
			var generated = generator.generate(optimized);
			var type = compileJavaSource(tempDir.resolve("case-" + i), generated.mainClassName(),
					generated.mainSource());
			var engine = (GeneratedDecisionEngine) type.getDeclaredConstructor().newInstance();
			int slot = optimized.model().decisions().getFirst().resultSlot();
			assertThat(engine.evaluate(new Object[optimized.model().valueSlotCount()])[slot]).as(expressions.get(i))
					.isEqualTo(runtime.evaluate(optimized.model(), Map.of()).value(slot));
		}
	}

	@Test
	void sortAcceptsBkmComparator(@TempDir Path tempDir) throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="d" namespace="urn:test">
				<businessKnowledgeModel id="cmp" name="cmp"><variable name="cmp"/>
				<encapsulatedLogic kind="FEEL"><formalParameter name="left" typeRef="number"/>
				<formalParameter name="right" typeRef="number"/>
				<literalExpression><text>left &lt; right</text></literalExpression></encapsulatedLogic></businessKnowledgeModel>
				<decision id="result" name="result"><variable name="result"/>
				<knowledgeRequirement><requiredKnowledge href="#cmp"/></knowledgeRequirement>
				<literalExpression><text>sort([3,1,2], cmp)</text></literalExpression></decision></definitions>
				""";
		var compilation = compiler.compile(new DmnSource(DmnSourceId.of("memory:sort-bkm"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).isTrue();
		var optimized = compilation.optimizedRuntimeModel().orElseThrow();
		var generated = generator.generate(optimized);
		var type = compileJavaSource(tempDir, generated.mainClassName(), generated.mainSource());
		var engine = (GeneratedDecisionEngine) type.getDeclaredConstructor().newInstance();
		int slot = optimized.model().decisions().getFirst().resultSlot();
		assertThat(engine.evaluate(new Object[optimized.model().valueSlotCount()])[slot])
				.isEqualTo(runtime.evaluate(optimized.model(), Map.of()).value(slot))
				.isEqualTo(List.of(BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.valueOf(3)));
	}

	@Test
	void sortPropagatesHostComparatorFailure(@TempDir Path tempDir) throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="d" namespace="urn:test">
				<inputData id="cmp" name="cmp"><variable name="cmp"/></inputData>
				<decision id="result" name="result"><variable name="result"/>
				<informationRequirement><requiredInput href="#cmp"/></informationRequirement>
				<literalExpression><text>sort([2,1], cmp)</text></literalExpression></decision></definitions>
				""";
		var compilation = compiler.compile(new DmnSource(DmnSourceId.of("memory:sort-host-failure"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).isTrue();
		var optimized = compilation.optimizedRuntimeModel().orElseThrow();
		var generated = generator.generate(optimized);
		assertThat(generated.mainSource()).doesNotContain("java.lang.reflect", "setAccessible", "getMethod(");
		var type = compileJavaSource(tempDir, generated.mainClassName(), generated.mainSource());
		var engine = (GeneratedDecisionEngine) type.getDeclaredConstructor().newInstance();
		Object[] inputs = new Object[optimized.model().valueSlotCount()];
		IllegalStateException failure = new IllegalStateException("comparator failed");
		inputs[0] = (java.util.function.BiFunction<Object, Object, Object>) (a, b) -> {
			throw failure;
		};
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.evaluate(inputs)).isSameAs(failure);
		// Inject a typed FEEL callable through the host input to exercise failures
		// independently of the production external-Java helper's null-on-error
		// contract.
		Class<?> callable = Class.forName(generated.mainClassName() + "$FeelCallable", true, type.getClassLoader());
		inputs[0] = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{callable},
				(proxy, method, arguments) -> {
					throw failure;
				});
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.evaluate(inputs)).isSameAs(failure);

	}

	@Test
	void generatesJavaSourceAndAssertsParityWithInterpreter(@TempDir Path tempDir) throws Exception {
		Path scenarioDir = Path.of("../dmn-compiler/src/test/resources/corpus/p2-09-lending-eligibility");
		Path rootFile = scenarioDir.resolve("credit-application.dmn");
		DmnSource rootSource = new DmnSource(DmnSourceId.of(rootFile.toUri().toString()), Files.readAllBytes(rootFile));
		DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

		DmnCompilationResult compilation = compiler.compile(rootSource, resolver);
		assertThat(compilation.isSuccess()).isTrue();

		DmnCompiledModel compiledModel = compilation.compiledModel().orElseThrow();
		RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();

		// 1. Generate Java Source
		DmnJavaGeneratorOptions options = DmnJavaGeneratorOptions.of("io.finmsg.dmn.generated",
				"CreditAppGeneratedModel");
		DmnJavaGeneratorResult genResult = generator.generate(optimized, options);
		assertThat(genResult.mainSource()).contains("public final class CreditAppGeneratedModel");

		// 2. Dynamically compile generated Java source using standard JDK JavaCompiler
		Class<?> compiledClass = compileJavaSource(tempDir, genResult.mainClassName(), genResult.mainSource());
		GeneratedDecisionEngine engine = (GeneratedDecisionEngine) compiledClass.getDeclaredConstructor().newInstance();

		// 3. Prepare Input Slots for Lending Eligibility Scenario
		// Input slots: RequestedAmount, Applicant map (score, income, monthlyDebt)
		Map<String, Object> inputs = Map.of("RequestedAmount", new BigDecimal("10000"), "Applicant", Map.of("score",
				new BigDecimal("750"), "income", new BigDecimal("50000"), "monthlyDebt", new BigDecimal("1000")));

		// Evaluate using Compiled Model Facade
		Object facadeResult = compiledModel.evaluateDecision("LoanApproval", inputs);
		assertThat(facadeResult).isEqualTo("APPROVED");

		// Evaluate using generated Java Engine
		Object[] inputSlots = new Object[optimized.model().valueSlotCount()];
		Map<Integer, Object> runtimeInputMap = new HashMap<>();

		// Map inputs to slot 0 (Applicant context) and slot 4 (RequestedAmount number)
		inputSlots[0] = inputs.get("Applicant");
		runtimeInputMap.put(0, inputs.get("Applicant"));

		inputSlots[4] = new BigDecimal("10000");
		runtimeInputMap.put(4, new BigDecimal("10000"));

		Object[] outputSlots = engine.evaluate(inputSlots);
		assertThat(outputSlots).isNotNull();
		assertThat(outputSlots.length).isEqualTo(optimized.model().valueSlotCount());

		// 4. Assert 100% Output Parity between DmnRuntime interpreter and generated
		// Java class
		DmnEvaluationResult interpResult = runtime.evaluate(optimized.model(), runtimeInputMap);

		Set<Integer> bkmSlots = new HashSet<>();
		optimized.model().businessKnowledgeModels().forEach(bkm -> bkmSlots.add(bkm.resultSlot()));
		for (int i = 0; i < outputSlots.length; i++) {
			if (bkmSlots.contains(i)) {
				continue;
			}
			Object interpVal = interpResult.value(i);
			Object genVal = outputSlots[i];
			if (interpVal != null && !interpVal.getClass().getName().contains("Lambda")) {
				assertThat(genVal).isEqualTo(interpVal);
			}
		}
	}

	@Test
	void testBetweenExpressionEmissionTrivialAndNonTrivial() {
		RuntimeType numType = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
		RuntimeType boolType = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);

		// Trivial operand: local reference
		RuntimeLocalReference valRef = new RuntimeLocalReference(0, 0, numType);
		RuntimeConstant lower = new RuntimeConstant(RuntimeConstantKind.NUMBER, "10", numType);
		RuntimeConstant upper = new RuntimeConstant(RuntimeConstantKind.NUMBER, "20", numType);
		RuntimeBetweenExpression trivialBetween = new RuntimeBetweenExpression(valRef, lower, upper, boolType);

		String emittedTrivial = JavaExpressionEmitter.emit(trivialBetween);
		assertThat(emittedTrivial).contains(
				"local_0 != null && compare(local_0, new BigDecimal(\"10\")) >= 0 && compare(local_0, new BigDecimal(\"20\")) <= 0");
		assertThat(emittedTrivial).doesNotContain("Supplier");

		// Trivial operand: slot value reference
		RuntimeValueReference slotRef = new RuntimeValueReference(0, numType);
		RuntimeBetweenExpression slotBetween = new RuntimeBetweenExpression(slotRef, lower, upper, boolType);
		String emittedSlot = JavaExpressionEmitter.emit(slotBetween);
		assertThat(emittedSlot).contains(
				"slots[0] != null && compare(slots[0], new BigDecimal(\"10\")) >= 0 && compare(slots[0], new BigDecimal(\"20\")) <= 0");
		assertThat(emittedSlot).doesNotContain("Supplier");

		// Non-trivial operand: binary addition (slots[0] + 5)
		RuntimeConstant five = new RuntimeConstant(RuntimeConstantKind.NUMBER, "5", numType);
		RuntimeBinaryExpression addExpr = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, valRef, five, numType);
		RuntimeBetweenExpression nonTrivialBetween = new RuntimeBetweenExpression(addExpr, lower, upper, boolType);

		String emittedNonTrivial = JavaExpressionEmitter.emit(nonTrivialBetween);
		assertThat(emittedNonTrivial).contains("java.util.function.Supplier<Boolean>");
		assertThat(emittedNonTrivial).contains("_btnVal_");
	}

	private Class<?> compileJavaSource(Path tempDir, String fqcn, String sourceCode) throws Exception {
		String relativePath = fqcn.replace('.', '/') + ".java";
		Path sourceFile = tempDir.resolve(relativePath);
		Files.createDirectories(sourceFile.getParent());
		Files.writeString(sourceFile, sourceCode, java.nio.charset.StandardCharsets.UTF_8);

		JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
		assertThat(javaCompiler).withFailMessage("JDK JavaCompiler not available").isNotNull();

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		StandardJavaFileManager fileManager = javaCompiler.getStandardFileManager(diagnostics, null, null);
		Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sourceFile.toFile());

		JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, diagnostics,
				List.of("-d", tempDir.toString(), "-encoding", "UTF-8"), null, compilationUnits);
		boolean success = task.call();
		fileManager.close();

		if (!success) {
			System.err.println("=== FAILED GENERATED SOURCE CODE ===");
			System.err.println(sourceCode);
			System.err.println("====================================");
		}
		assertThat(success).withFailMessage("Generated Java compilation failed: %s", diagnostics.getDiagnostics())
				.isTrue();

		URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
				getClass().getClassLoader());
		return classLoader.loadClass(fqcn);
	}
}

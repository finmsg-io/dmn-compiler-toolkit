package io.finmsg.dmn.compiler.dmnir;

import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrTableHitPolicy;
import io.finmsg.dmn.runtime.DmnEvaluationException;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DmnIrTableParityTest {
	private static final String NS = "urn:tables:policy";
	private static final String MODEL_START = """
			<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" xmlns:cal="urn:tables:calendar"
			    id="policy" name="Policy" namespace="urn:tables:policy">
			""";
	private static final String COMPOSED = """
			<import name="cal" namespace="urn:tables:calendar" locationURI="calendar.dmn" importType="DMN"/>
			<itemDefinition id="applicant-type" name="tApplicant"><itemComponent name="Permit"><typeRef>cal:tPermit</typeRef></itemComponent></itemDefinition>
			<itemDefinition id="outcome-type" name="tOutcome"><itemComponent name="Fee"><typeRef>number</typeRef></itemComponent>
			<itemComponent name="Reason"><typeRef>string</typeRef></itemComponent></itemDefinition>
			<inputData id="applicant" name="Applicant"><variable name="Applicant" typeRef="tApplicant"/></inputData>
			<decision id="outcome" name="Outcome"><variable name="Outcome" typeRef="tOutcome"/>
			<informationRequirement><requiredInput href="#applicant"/></informationRequirement>
			<informationRequirement><requiredDecision href="urn:tables:calendar#cutoff"/></informationRequirement>
			<decisionTable id="policy-table" hitPolicy="FIRST">
			<input id="expiry-column" label="Permit expiry"><inputExpression typeRef="date"><text>Applicant.Permit.Expiry</text></inputExpression></input>
			<output id="fee-column" name="Fee" typeRef="number"><defaultOutputEntry><text>100</text></defaultOutputEntry></output>
			<output id="reason-column" name="Reason" typeRef="string"/>
			<rule id="preferred"><inputEntry><text>[cal.Cutoff .. date("2024-03-01")), date("2024-04-01")</text></inputEntry>
			<outputEntry><text>0</text></outputEntry><outputEntry><text>"preferred"</text></outputEntry></rule>
			<rule id="regular"><inputEntry><text>not(&lt; cal.Cutoff, null)</text></inputEntry>
			<outputEntry><text>30</text></outputEntry><outputEntry><text>"regular"</text></outputEntry></rule>
			</decisionTable></decision>
			<decision id="answer" name="Answer"><variable name="Answer" typeRef="number"/>
			<informationRequirement><requiredDecision href="#outcome"/></informationRequirement>
			<context><contextEntry><variable name="Base Fee" typeRef="number"/><literalExpression><text>Outcome.Fee</text></literalExpression></contextEntry>
			<contextEntry><literalExpression><text>if Outcome.Reason = null then Base Fee else Base Fee + 5</text></literalExpression></contextEntry></context></decision>
			""";

	@Test
	void composedPolicyPreservesRulesIdentitiesDefaultsAndResults(@TempDir Path dir) throws Exception {
		var source = policy(COMPOSED);
		var calendar = source("calendar.dmn",
				"""
						<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="calendar" name="Calendar" namespace="urn:tables:calendar">
						<itemDefinition id="permit-type" name="tPermit"><itemComponent name="Expiry"><typeRef>date</typeRef></itemComponent></itemDefinition>
						<decision id="cutoff" name="Cutoff"><variable name="Cutoff" typeRef="date"/><literalExpression><text>date("2024-02-01")</text></literalExpression></decision>
						</definitions>
						""");
		var resolver = new InMemoryDmnModelResolver(List.of(calendar));
		var verified = new DmnIrScalarProducer().produce(source, resolver);
		byte[] bytes = verified.artifact().toByteArray();
		assertArrayEquals(bytes, new DmnIrScalarProducer().produce(source, resolver).artifact().toByteArray());
		var model = verified.artifact().getModelsList().stream().filter(m -> m.getNamespace().equals(NS)).findFirst()
				.orElseThrow();
		var table = model.getDecisionsList().stream().filter(d -> d.hasDecisionTable()).findFirst().orElseThrow()
				.getDecisionTable();
		assertEquals(5, verified.artifact().getSchemaVersion().getMinor());
		assertEquals(DmnIrTableHitPolicy.TABLE_HIT_POLICY_FIRST, table.getHitPolicy());
		assertEquals("Permit expiry", table.getInputs(0).getLabel());
		assertEquals("preferred", table.getRules(0).getId());

		var exported = new DmnIrScalarDmnExporter().export(verified);
		DmnIrDateParityTest.validate(exported);
		String xml = exported.documents().get(exported.rootDocument());
		assertTrue(xml.contains("label=\"Permit expiry\""));
		assertTrue(xml.contains("id=\"preferred\""));
		assertEquals(exported.documents(), new DmnIrScalarDmnExporter().export(verified).documents());
		var roundTripSources = exported.documents().entrySet().stream().map(e -> source(e.getKey(), e.getValue()))
				.toList();
		var recompiled = compile(source(exported.rootDocument(), xml), new InMemoryDmnModelResolver(roundTripSources));
		var direct = compile(source, resolver);
		var adapted = new DmnIrRuntimeAdapter().adapt(verified);
		var dates = Arrays.asList(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 1),
				LocalDate.of(2024, 4, 1), LocalDate.of(2024, 1, 31), null);
		var fees = List.of(5, 5, 35, 5, 100, 100);
		for (int i = 0; i < dates.size(); i++) {
			var applicant = applicant(dates.get(i));
			var values = adapted.evaluate(Map.of(key("applicant"), applicant));
			assertEquals(BigDecimal.valueOf(fees.get(i)), values.get(key("answer")));
			for (String name : List.of("Answer", "Outcome")) {
				var actual = values.get(key(name.toLowerCase(Locale.ROOT)));
				assertEquals(evaluate(direct, name, Map.of("Applicant", applicant)), actual);
				assertEquals(evaluate(recompiled, name, Map.of("Applicant", applicant)), actual);
			}
		}

		var file = Files.write(dir.resolve("policy.dmnir"), bytes);
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

	@Test
	void handlesHitPoliciesAndRejectsUnsupportedPolicy() {
		var producer = new DmnIrScalarProducer();
		for (String policy : List.of("FIRST", "UNIQUE", "ANY")) {
			for (boolean equal : List.of(false, true)) {
				var source = scalarTable(policy, equal ? 1 : 2);
				var artifact = producer.produce(source);
				var portable = new DmnIrRuntimeAdapter().adapt(artifact);
				var direct = compile(source, new InMemoryDmnModelResolver(List.of()));
				if (policy.equals("UNIQUE") || policy.equals("ANY") && !equal) {
					assertThrows(DmnEvaluationException.class, () -> portable.evaluate(Map.of()));
					assertThrows(DmnEvaluationException.class, () -> evaluate(direct, "Answer", Map.of()));
				} else {
					assertEquals(BigDecimal.ONE, portable.evaluate(Map.of()).get(key("answer")));
					assertEquals(BigDecimal.ONE, evaluate(direct, "Answer", Map.of()));
				}
			}
		}
		assertEquals(DmnIrProductionException.Category.UNSUPPORTED_PROFILE,
				assertThrows(DmnIrProductionException.class, () -> producer.produce(scalarTable("COLLECT", 2)))
						.category());
	}

	@Test
	void fineTableMatchesBothSpeedBoundariesAndWildcardRows() throws Exception {
		// The upstream Fine table and its declarations, with the deferred item
		// constraint removed.
		DmnSource source;
		try (var fixture = Objects
				.requireNonNull(getClass().getResourceAsStream("/dmnir/traffic-violation-fine.dmn"))) {
			source = new DmnSource(DmnSourceId.of("memory:/fine.dmn"), fixture.readAllBytes());
		}
		var artifact = new DmnIrScalarProducer().produce(source);
		var adapted = new DmnIrRuntimeAdapter().adapt(artifact);
		var direct = compile(source, new InMemoryDmnModelResolver(List.of()));
		var bundle = new DmnIrScalarDmnExporter().export(artifact);
		DmnIrDateParityTest.validate(bundle);
		var exported = compile(source(bundle.rootDocument(), bundle.documents().get(bundle.rootDocument())),
				new InMemoryDmnModelResolver(List.of()));
		var input = adapted.inputs().keySet().iterator().next();
		var decision = adapted.decisions().keySet().iterator().next();
		for (var row : List.of(new FineCase("speed", 59, null, null), new FineCase("speed", 60, 500, 3),
				new FineCase("speed", 79, 500, 3), new FineCase("speed", 80, 1000, 7),
				new FineCase("parking", 50, 100, 1), new FineCase("driving under the influence", 50, 1000, 5))) {
			var violation = Map.of("Code", "example", "Date", LocalDate.of(2024, 1, 1), "Type", row.type(),
					"SpeedLimit", BigDecimal.valueOf(50), "ActualSpeed", BigDecimal.valueOf(row.speed()));
			var actual = adapted.evaluate(Map.of(input, violation)).get(decision);
			var result = assertInstanceOf(io.finmsg.dmn.runtime.RuntimeContextValue.class, actual);
			assertEquals(row.amount() == null ? null : BigDecimal.valueOf(row.amount()), result.field(0));
			assertEquals(row.points() == null ? null : BigDecimal.valueOf(row.points()), result.field(1));
			assertEquals(evaluate(direct, "Fine", Map.of("Violation", violation)), actual);
			assertEquals(evaluate(exported, "Fine", Map.of("Violation", violation)), actual);
		}
	}

	private record FineCase(String type, int speed, Integer amount, Integer points) {
	}

	public static final class Consumer {
		public static void main(String[] args) throws Exception {
			var artifact = new DmnIrArtifactReader().read(Files.readAllBytes(Path.of(args[0])));
			var model = new DmnIrRuntimeAdapter().adapt(artifact);
			var result = model.evaluate(Map.of(key("applicant"), applicant(LocalDate.of(2024, 2, 1))));
			if (!BigDecimal.valueOf(5).equals(result.get(key("answer"))))
				throw new AssertionError(result);
		}
	}

	private static Map<String, Object> applicant(LocalDate date) {
		Map<String, Object> permit = new HashMap<>();
		permit.put("Expiry", date);
		return Map.of("Permit", permit);
	}

	private static DmnCompilationResult compile(DmnSource source, DmnModelResolver resolver) {
		var compiled = new DmnCompiler().compile(source, resolver);
		assertTrue(compiled.isSuccess(), compiled.diagnostics().toString());
		return compiled;
	}

	private static Object evaluate(DmnCompilationResult result, String decision, Map<String, Object> inputs) {
		var model = result.compiledModel().orElseThrow();
		Map<Integer, Object> slots = new HashMap<>();
		inputs.forEach((name, value) -> slots.put(model.inputSlots().get(name), value));
		return new DmnRuntime().evaluate(model.optimizedModel().model(), slots)
				.value(model.decisionSlots().get(decision));
	}

	private static DmnIrRuntimeAdapter.ElementId key(String id) {
		return new DmnIrRuntimeAdapter.ElementId(NS, id);
	}

	private static DmnSource scalarTable(String hitPolicy, int second) {
		return policy("""
				<decision id="answer" name="Answer"><variable name="Answer" typeRef="number"/>
				<decisionTable hitPolicy="%s">
				<input><inputExpression typeRef="number"><text>3</text></inputExpression></input>
				<output typeRef="number"/>
				<rule><inputEntry><text>-</text></inputEntry><outputEntry><text>1</text></outputEntry></rule>
				<rule><inputEntry><text>&gt;= 3</text></inputEntry><outputEntry><text>%d</text></outputEntry></rule>
				</decisionTable></decision>
				""".formatted(hitPolicy, second));
	}

	private static DmnSource policy(String body) {
		return source("policy.dmn", MODEL_START + body + "</definitions>");
	}

	private static DmnSource source(String name, String xml) {
		return new DmnSource(DmnSourceId.of("memory:/" + name), xml.getBytes(StandardCharsets.UTF_8));
	}
}

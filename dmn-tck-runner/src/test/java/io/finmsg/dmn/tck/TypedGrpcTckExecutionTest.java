package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.*;
import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.runtime.ExternalJavaPolicy;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class TypedGrpcTckExecutionTest {
	@Test
	void typedContextInputsCompileAndExecute() throws Exception {
		var root = java.nio.file.Path.of(
				"src/test/resources/tck-official/TestCases/compliance-level-2/0008-LX-arithmetic/0008-LX-arithmetic.dmn");
		var compilation = new DmnCompiler().compile(
				new DmnSource(DmnSourceId.of(root.toUri().toString()), java.nio.file.Files.readAllBytes(root)));
		assertThat(compilation.isSuccess()).as(compilation.diagnostics().toString()).isTrue();
		try (var service = TypedGrpcTckExecution.prepare(compilation, ExternalJavaPolicy.denyAll())) {
			var inputs = Map.<String, Object>of("loan", Map.of("principal", new BigDecimal("600000"), "rate",
					new BigDecimal("0.0375"), "termMonths", new BigDecimal("360")));
			var result = service.execute(inputs, compilation.compiledModel().orElseThrow().decisionSlots(), 30, () -> {
			}, () -> {
			});
			assertThat(result).hasSize(1);
			assertThat((BigDecimal) result.get("payment")).isCloseTo(new BigDecimal("2778.69354943277"),
					within(new BigDecimal("0.000000001")));
		}
	}
	@AfterAll
	static void shutdown() throws Exception {
		TypedGrpcTckExecution.closeTransport();
	}

	@Test
	void generatedTypedHandlerPreservesNullAndExactDecimalsOverHttp2() throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="typed" namespace="urn:typed">
				<inputData id="x" name="x"><variable name="x" typeRef="number"/></inputData>
				<decision id="r" name="Result"><variable name="Result" typeRef="number"/>
				<informationRequirement><requiredInput href="#x"/></informationRequirement>
				<literalExpression><text>x + 1</text></literalExpression></decision>
				<decision id="n" name="Empty"><variable name="Empty" typeRef="number"/>
				<literalExpression><text>null</text></literalExpression></decision>
				<decisionService id="service" name="Echo"><variable name="Echo"/>
				<outputDecision href="#r"/><inputData href="#x"/></decisionService>
				<businessKnowledgeModel id="increment" name="Increment"><variable name="Increment"/>
				<encapsulatedLogic kind="FEEL"><formalParameter name="x" typeRef="number"/>
				<literalExpression><text>x + 1</text></literalExpression></encapsulatedLogic></businessKnowledgeModel>
				</definitions>
				""";
		var compilation = new DmnCompiler().compile(
				new DmnSource(DmnSourceId.of("memory:typed"), xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).as(compilation.diagnostics().toString()).isTrue();
		var sent = new AtomicInteger();
		var received = new AtomicInteger();
		try (var service = TypedGrpcTckExecution.prepare(compilation, ExternalJavaPolicy.denyAll())) {
			var result = service.execute(Map.of("x", BigDecimal.valueOf(42)),
					compilation.compiledModel().orElseThrow().decisionSlots(), 30, sent::incrementAndGet,
					received::incrementAndGet);
			assertThat((BigDecimal) result.get("Result")).isEqualByComparingTo(BigDecimal.valueOf(43));
			assertThat(result).containsEntry("Empty", null);
			assertThat(sent.get()).isEqualTo(2);
			assertThat(received.get()).isEqualTo(2);
			var large = new BigDecimal("9007199254740993");
			var exact = service.execute(Map.of("x", large), compilation.compiledModel().orElseThrow().decisionSlots(),
					30, sent::incrementAndGet, received::incrementAndGet);
			assertThat((BigDecimal) exact.get("Result")).isEqualByComparingTo(large.add(BigDecimal.ONE));
			assertThat(sent.get()).isEqualTo(4);
			assertThat(received.get()).isEqualTo(4);
			var invoked = service.executeInvocation("Echo", Map.of("x", BigDecimal.valueOf(42)), 30,
					sent::incrementAndGet, received::incrementAndGet);
			assertThat((BigDecimal) invoked.get("Result")).isEqualByComparingTo("43");
			assertThat(
					service.executeInvocation("Echo", Map.of(), 30, sent::incrementAndGet, received::incrementAndGet))
					.containsEntry("Result", null);
			assertThat((BigDecimal) service.executeInvocation("Increment", Map.of("x", large), 30,
					sent::incrementAndGet, received::incrementAndGet).get("Increment"))
					.isEqualByComparingTo(large.add(BigDecimal.ONE));
			assertThat(sent.get()).isEqualTo(7);
			assertThat(received.get()).isEqualTo(7);
		}
	}

	@Test
	void typedNestedListsRetainNullMembersAndEmptyLists() throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="nested" namespace="urn:typed-nested">
				<itemDefinition id="row" name="Row" isCollection="true"><typeRef>number</typeRef></itemDefinition>
				<itemDefinition id="rows" name="Rows" isCollection="true"><typeRef>Row</typeRef></itemDefinition>
				<inputData id="x" name="values"><variable name="values" typeRef="Rows"/></inputData>
				<decision id="r" name="Result"><variable name="Result" typeRef="Rows"/>
				<informationRequirement><requiredInput href="#x"/></informationRequirement>
				<literalExpression><text>values</text></literalExpression></decision></definitions>
				""";
		var compilation = new DmnCompiler().compile(new DmnSource(DmnSourceId.of("memory:typed-nested"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).as(compilation.diagnostics().toString()).isTrue();
		var values = java.util.Arrays.asList(java.util.Arrays.asList(BigDecimal.ONE, null), java.util.List.of(),
				java.util.List.of(BigDecimal.valueOf(2)));
		try (var service = TypedGrpcTckExecution.prepare(compilation, ExternalJavaPolicy.denyAll())) {
			var actual = service.execute(Map.of("values", values),
					compilation.compiledModel().orElseThrow().decisionSlots(), 30, () -> {
					}, () -> {
					});
			assertThat(actual.get("Result")).isEqualTo(values);
		}
	}

	@Test
	void typedHandlerEnforcesHostOwnedExternalJavaPolicy() throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="typed" namespace="urn:typed-policy">
				<decision id="r" name="Result"><variable name="Result" typeRef="number"/>
				<literalExpression><text>(function(x) external {java:{class:"java.lang.Math",method signature:"abs(int)"}})(-7)</text></literalExpression>
				</decision></definitions>
				""";
		var compilation = new DmnCompiler().compile(new DmnSource(DmnSourceId.of("memory:typed-policy"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).as(compilation.diagnostics().toString()).isTrue();
		var outputs = compilation.compiledModel().orElseThrow().decisionSlots();
		try (var denied = TypedGrpcTckExecution.prepare(compilation, ExternalJavaPolicy.denyAll())) {
			assertThatThrownBy(() -> denied.execute(Map.of(), outputs, 30, () -> {
			}, () -> {
			})).isInstanceOf(io.grpc.StatusRuntimeException.class).hasMessageContaining("EXTERNAL_JAVA_DENIED:");
		}
		var policy = ExternalJavaPolicy
				.allowOnly(java.util.Set.of(new ExternalJavaPolicy.Method("java.lang.Math", "abs(int)")));
		try (var allowed = TypedGrpcTckExecution.prepare(compilation, policy)) {
			var result = allowed.execute(Map.of(), outputs, 30, () -> {
			}, () -> {
			});
			assertThat((BigDecimal) result.get("Result")).isEqualByComparingTo("7");
		}
	}
}

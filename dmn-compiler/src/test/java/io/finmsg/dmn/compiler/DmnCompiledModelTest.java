package io.finmsg.dmn.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.model.EvaluationRequest;
import io.finmsg.dmn.model.EvaluationResponse;
import io.finmsg.dmn.model.Value;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class DmnCompiledModelTest {

	private final DmnCompiler compiler = new DmnCompiler();

	@Test
	void evaluatesNamedInputsAndQueriesDecisionByName() throws IOException {
		Path scenarioDir = Path.of("src/test/resources/corpus/p2-09-lending-eligibility");
		Path rootFile = scenarioDir.resolve("credit-application.dmn");
		DmnSource rootSource = readSource(rootFile);
		DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

		DmnCompilationResult compilation = compiler.compile(rootSource, resolver);
		assertThat(compilation.isSuccess()).isTrue();

		DmnCompiledModel compiledModel = compilation.compiledModel().orElseThrow();
		assertThat(compiledModel.modelName()).isEqualTo("Credit Application");
		assertThat(compiledModel.metadata().getNamespace()).isEqualTo("urn:p2-09:credit-app");
		assertThat(compiledModel.inputTypes()).containsKey("RequestedAmount");
		assertThat(compiledModel.decisionTypes()).containsKey("LoanApproval");

		Map<String, Object> inputs = Map.of("RequestedAmount", new BigDecimal("10000"), "Applicant", Map.of("score",
				new BigDecimal("750"), "income", new BigDecimal("50000"), "monthlyDebt", new BigDecimal("1000")));

		Object result = compiledModel.evaluateDecision("LoanApproval", inputs);
		assertThat(result).isEqualTo("APPROVED");
	}

	@Test
	void evaluatesProtobufRequestPayload() throws IOException {
		Path scenarioDir = Path.of("src/test/resources/corpus/p2-09-lending-eligibility");
		Path rootFile = scenarioDir.resolve("credit-application.dmn");
		DmnSource rootSource = readSource(rootFile);
		DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

		DmnCompilationResult compilation = compiler.compile(rootSource, resolver);
		DmnCompiledModel compiledModel = compilation.compiledModel().orElseThrow();

		EvaluationRequest request = EvaluationRequest.newBuilder().setNamespace("urn:p2-09:credit-app")
				.setModelName("Credit Application")
				.putInputs("RequestedAmount", DmnCompiledModel.toProtobufValue(new BigDecimal("10000")))
				.putInputs("Applicant", DmnCompiledModel.toProtobufValue(Map.of("score", new BigDecimal("750"),
						"income", new BigDecimal("50000"), "monthlyDebt", new BigDecimal("1000"))))
				.build();

		EvaluationResponse response = compiledModel.evaluate(request);
		assertThat(response.getDecisionResultsMap()).containsKey("LoanApproval");
		Value approvalValue = response.getDecisionResultsMap().get("LoanApproval");
		assertThat(approvalValue.getStringValue()).isEqualTo("APPROVED");
	}

	@Test
	void supportsConcurrentEvaluationAcrossThreads() throws Exception {
		Path scenarioDir = Path.of("src/test/resources/corpus/p2-09-lending-eligibility");
		Path rootFile = scenarioDir.resolve("credit-application.dmn");
		DmnSource rootSource = readSource(rootFile);
		DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

		DmnCompilationResult compilation = compiler.compile(rootSource, resolver);
		DmnCompiledModel compiledModel = compilation.compiledModel().orElseThrow();

		Map<String, Object> lowRiskInputs = Map.of("RequestedAmount", new BigDecimal("10000"), "Applicant",
				Map.of("score", new BigDecimal("750"), "income", new BigDecimal("50000"), "monthlyDebt",
						new BigDecimal("1000")));

		Map<String, Object> highRiskInputs = Map.of("RequestedAmount", new BigDecimal("10000"), "Applicant",
				Map.of("score", new BigDecimal("550"), "income", new BigDecimal("50000"), "monthlyDebt",
						new BigDecimal("1000")));

		ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			CompletableFuture<Object> task1 = CompletableFuture
					.supplyAsync(() -> compiledModel.evaluateDecision("LoanApproval", lowRiskInputs), executor);
			CompletableFuture<Object> task2 = CompletableFuture
					.supplyAsync(() -> compiledModel.evaluateDecision("LoanApproval", highRiskInputs), executor);
			CompletableFuture<Object> task3 = CompletableFuture
					.supplyAsync(() -> compiledModel.evaluateDecision("LoanApproval", lowRiskInputs), executor);
			CompletableFuture<Object> task4 = CompletableFuture
					.supplyAsync(() -> compiledModel.evaluateDecision("LoanApproval", highRiskInputs), executor);

			CompletableFuture.allOf(task1, task2, task3, task4).get();

			assertThat(task1.get()).isEqualTo("APPROVED");
			assertThat(task2.get()).isEqualTo("DECLINED");
			assertThat(task3.get()).isEqualTo("APPROVED");
			assertThat(task4.get()).isEqualTo("DECLINED");
		} finally {
			executor.shutdown();
		}
	}

	private static DmnSource readSource(Path file) throws IOException {
		return new DmnSource(DmnSourceId.of(file.toUri().toString()), java.nio.file.Files.readAllBytes(file));
	}
}

package io.finmsg.dmn.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiledModel;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.model.EvaluationRequest;
import io.finmsg.dmn.model.EvaluationResponse;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end smoke test validating dynamic compilation and interpretation using
 * the public DMN Compiler Toolkit API as an external consumer.
 */
class ConsumerInterpretationSmokeTest {

	private DmnCompiler compiler;
	private DmnCompiledModel compiledModel;

	@BeforeEach
	void setUp() throws Exception {
		compiler = new DmnCompiler();
		try (InputStream is = getClass().getResourceAsStream("/models/loan-eligibility.dmn")) {
			assertThat(is).withFailMessage("Sample model loan-eligibility.dmn must exist on classpath").isNotNull();
			byte[] bytes = is.readAllBytes();
			DmnSource source = new DmnSource(DmnSourceId.of("classpath:/models/loan-eligibility.dmn"), bytes);
			DmnCompilationResult result = compiler.compile(source);
			assertThat(result.isSuccess()).withFailMessage("Compilation failed: %s", result.diagnostics()).isTrue();
			compiledModel = result.compiledModel().orElseThrow();
		}
	}

	@Test
	@DisplayName("Consumer can inspect model metadata and type signatures")
	void verifiesModelMetadata() {
		assertThat(compiledModel.modelName()).isEqualTo("Loan Eligibility Model");
		assertThat(compiledModel.namespace()).isEqualTo("urn:finmsg:smoke:loan-eligibility");
		assertThat(compiledModel.inputTypes()).containsKeys("ApplicantName", "CreditScore", "AnnualIncome",
				"LoanAmount", "ExistingDebts");
		assertThat(compiledModel.decisionTypes()).containsKeys("DebtToIncomeRatio", "RiskCategory", "LoanOffer");
	}

	@Test
	@DisplayName("Evaluates low-risk applicant to APPROVED_PREMIUM")
	void evaluatesLowRiskApplicant() {
		Map<String, Object> inputs = Map.of("ApplicantName", "Alice Smith", "CreditScore", new BigDecimal("780"),
				"AnnualIncome", new BigDecimal("120000"), "LoanAmount", new BigDecimal("10000"), "ExistingDebts",
				new BigDecimal("500"));

		Object risk = compiledModel.evaluateDecision("RiskCategory", inputs);
		assertThat(risk).isEqualTo("LOW");

		Object offer = compiledModel.evaluateDecision("LoanOffer", inputs);
		assertThat(offer).isEqualTo("APPROVED_PREMIUM");
	}

	@Test
	@DisplayName("Evaluates medium-risk applicant to APPROVED_STANDARD")
	void evaluatesMediumRiskApplicant() {
		Map<String, Object> inputs = Map.of("ApplicantName", "Bob Jones", "CreditScore", new BigDecimal("710"),
				"AnnualIncome", new BigDecimal("60000"), "LoanAmount", new BigDecimal("20000"), "ExistingDebts",
				new BigDecimal("1000"));

		Object risk = compiledModel.evaluateDecision("RiskCategory", inputs);
		assertThat(risk).isEqualTo("MEDIUM");

		Object offer = compiledModel.evaluateDecision("LoanOffer", inputs);
		assertThat(offer).isEqualTo("APPROVED_STANDARD");
	}

	@Test
	@DisplayName("Evaluates high-risk applicant to APPROVED_SUBPRIME")
	void evaluatesHighRiskApplicant() {
		Map<String, Object> inputs = Map.of("ApplicantName", "Charlie Brown", "CreditScore", new BigDecimal("630"),
				"AnnualIncome", new BigDecimal("60000"), "LoanAmount", new BigDecimal("20000"), "ExistingDebts",
				new BigDecimal("1200"));

		Object risk = compiledModel.evaluateDecision("RiskCategory", inputs);
		assertThat(risk).isEqualTo("HIGH");

		Object offer = compiledModel.evaluateDecision("LoanOffer", inputs);
		assertThat(offer).isEqualTo("APPROVED_SUBPRIME");
	}

	@Test
	@DisplayName("Evaluates rejected applicant to DECLINED")
	void evaluatesRejectedApplicant() {
		Map<String, Object> inputs = Map.of("ApplicantName", "Dave Miller", "CreditScore", new BigDecimal("580"),
				"AnnualIncome", new BigDecimal("60000"), "LoanAmount", new BigDecimal("20000"), "ExistingDebts",
				new BigDecimal("1200"));

		Object risk = compiledModel.evaluateDecision("RiskCategory", inputs);
		assertThat(risk).isEqualTo("REJECTED");

		Object offer = compiledModel.evaluateDecision("LoanOffer", inputs);
		assertThat(offer).isEqualTo("DECLINED");
	}

	@Test
	@DisplayName("Evaluates Protobuf EvaluationRequest payload cleanly")
	void evaluatesProtobufPayload() {
		EvaluationRequest request = EvaluationRequest.newBuilder().setNamespace("urn:finmsg:smoke:loan-eligibility")
				.setModelName("Loan Eligibility Model")
				.putInputs("ApplicantName", DmnCompiledModel.toProtobufValue("Alice Smith"))
				.putInputs("CreditScore", DmnCompiledModel.toProtobufValue(new BigDecimal("780")))
				.putInputs("AnnualIncome", DmnCompiledModel.toProtobufValue(new BigDecimal("120000")))
				.putInputs("LoanAmount", DmnCompiledModel.toProtobufValue(new BigDecimal("10000")))
				.putInputs("ExistingDebts", DmnCompiledModel.toProtobufValue(new BigDecimal("500"))).build();

		EvaluationResponse response = compiledModel.evaluate(request);
		assertThat(response.getDecisionResultsMap()).containsKey("LoanOffer");
		assertThat(response.getDecisionResultsMap().get("LoanOffer").getStringValue()).isEqualTo("APPROVED_PREMIUM");
	}

	@Test
	@DisplayName("Evaluates concurrent requests across thread pool without interference")
	void evaluatesConcurrently() throws Exception {
		Map<String, Object> lowRiskInputs = Map.of("ApplicantName", "Alice", "CreditScore", new BigDecimal("780"),
				"AnnualIncome", new BigDecimal("120000"), "LoanAmount", new BigDecimal("10000"), "ExistingDebts",
				new BigDecimal("500"));

		Map<String, Object> declinedInputs = Map.of("ApplicantName", "Dave", "CreditScore", new BigDecimal("580"),
				"AnnualIncome", new BigDecimal("60000"), "LoanAmount", new BigDecimal("20000"), "ExistingDebts",
				new BigDecimal("1200"));

		ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			CompletableFuture<Object> f1 = CompletableFuture
					.supplyAsync(() -> compiledModel.evaluateDecision("LoanOffer", lowRiskInputs), executor);
			CompletableFuture<Object> f2 = CompletableFuture
					.supplyAsync(() -> compiledModel.evaluateDecision("LoanOffer", declinedInputs), executor);
			CompletableFuture<Object> f3 = CompletableFuture
					.supplyAsync(() -> compiledModel.evaluateDecision("LoanOffer", lowRiskInputs), executor);
			CompletableFuture<Object> f4 = CompletableFuture
					.supplyAsync(() -> compiledModel.evaluateDecision("LoanOffer", declinedInputs), executor);

			CompletableFuture.allOf(f1, f2, f3, f4).get();

			assertThat(f1.get()).isEqualTo("APPROVED_PREMIUM");
			assertThat(f2.get()).isEqualTo("DECLINED");
			assertThat(f3.get()).isEqualTo("APPROVED_PREMIUM");
			assertThat(f4.get()).isEqualTo("DECLINED");
		} finally {
			executor.shutdown();
		}
	}
}

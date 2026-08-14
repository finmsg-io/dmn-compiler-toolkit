package io.finmsg.dmn.benchmark;

import io.finmsg.dmn.benchmark.data.BenchmarkDataGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkIntegrityTest {

	@Test
	void testTrafficViolationBenchmarkExecutionAndParity() throws Exception {
		TrafficViolationBenchmark benchmark = new TrafficViolationBenchmark();
		benchmark.setup();
		BenchmarkCursor cursor = new BenchmarkCursor();

		Object interpreterResult = benchmark.interpreterCore_TrafficViolation(cursor);
		Object generatedJavaResult = benchmark.generatedDirect_TrafficViolation(cursor);

		assertThat(interpreterResult).isNotNull();
		assertThat(generatedJavaResult).isNotNull();
	}

	@Test
	void testCreditApprovalBenchmarkExecutionAndParity() throws Exception {
		CreditApprovalBenchmark benchmark = new CreditApprovalBenchmark();
		benchmark.setup();
		BenchmarkCursor cursor = new BenchmarkCursor();

		Object interpreterResult = benchmark.interpreterCore_CreditApproval(cursor);
		Object generatedJavaResult = benchmark.generatedDirect_CreditApproval(cursor);

		assertThat(interpreterResult).isNotNull();
		assertThat(generatedJavaResult).isNotNull();
	}

	@Test
	void testScalarArithmeticBenchmarkExecutionAndParity() throws Exception {
		ScalarArithmeticBenchmark benchmark = new ScalarArithmeticBenchmark();
		benchmark.setup();

		Object interpreterResult = benchmark.interpreterCore_ScalarArithmetic();
		Object generatedJavaResult = benchmark.generatedDirect_ScalarArithmetic();

		assertThat(interpreterResult).isNotNull();
		assertThat(generatedJavaResult).isNotNull();
	}

	@Test
	void testOriginationsBenchmarkExecution() throws Exception {
		OriginationsBenchmark benchmark = new OriginationsBenchmark();
		benchmark.setup();

		for (int payloadIndex = 0; payloadIndex < 25; payloadIndex++) {
			Object[] generatedSlots = (Object[]) benchmark.evaluateGeneratedAt(payloadIndex);
			assertThat(generatedSlots)
					.containsExactlyElementsOf(benchmark.evaluateInterpreterAt(payloadIndex).slotValues());
		}
		assertThat(benchmark.generatedEndToEnd_Originations(new BenchmarkCursor())).isNotNull();
	}

	@Test
	void testRankedLoanProductsParity() throws Exception {
		RankedLoanProductsBenchmark benchmark = new RankedLoanProductsBenchmark();
		benchmark.setup();
		for (int payloadIndex = 0; payloadIndex < 25; payloadIndex++) {
			Object[] generatedSlots = (Object[]) benchmark.evaluateGeneratedAt(payloadIndex);
			assertThat(generatedSlots)
					.containsExactlyElementsOf(benchmark.evaluateInterpreterAt(payloadIndex).slotValues());
		}
		assertThat(benchmark.generatedEndToEnd_RankedLoanProducts(new BenchmarkCursor())).isNotNull();
	}

	@Test
	void testMt564ScenariosAndBackendParity() throws Exception {
		Map<String, Integer> expectedViolations = Map.of("validBaseline", 0, "singleViolation", 1, "multipleViolations",
				3, "largeStructure", 0);
		for (var entry : expectedViolations.entrySet()) {
			Mt564DataQualityBenchmark benchmark = new Mt564DataQualityBenchmark();
			benchmark.scenario = entry.getKey();
			benchmark.setup();
			for (int payloadIndex = 0; payloadIndex < 64; payloadIndex++) {
				Object interpreter = benchmark.evaluateInterpreterAt(payloadIndex).value(benchmark.qualityReportSlot());
				Object generated = benchmark.evaluateGeneratedAt(payloadIndex)[benchmark.qualityReportSlot()];
				assertThat(generated).isEqualTo(interpreter);
				assertThat((List<?>) generated).hasSize(entry.getValue());
			}
			assertThat(benchmark.interpreterEndToEnd_Mt564(new BenchmarkCursor()))
					.isEqualTo(benchmark.generatedEndToEnd_Mt564(new BenchmarkCursor()));
		}
	}

	@Test
	void sharedRuntimeModelAndGeneratedEngineAreConcurrencySafe() throws Exception {
		OriginationsBenchmark benchmark = new OriginationsBenchmark();
		benchmark.setup();
		List<List<Object>> expected = IntStream.range(0, 25)
				.mapToObj(index -> benchmark.evaluateInterpreterAt(index).slotValues()).toList();

		try (var executor = Executors.newFixedThreadPool(8)) {
			var futures = IntStream.range(0, 800).mapToObj(invocation -> executor.submit(() -> {
				int payloadIndex = invocation % expected.size();
				Object[] generated = (Object[]) benchmark.evaluateGeneratedAt(payloadIndex);
				assertThat(generated).containsExactlyElementsOf(expected.get(payloadIndex));
				assertThat(benchmark.evaluateInterpreterAt(payloadIndex).slotValues())
						.containsExactlyElementsOf(expected.get(payloadIndex));
			})).toList();
			for (var future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		}
	}

	@Test
	void mt564RuntimeAndGeneratedEngineAreConcurrencySafe() throws Exception {
		Mt564DataQualityBenchmark benchmark = new Mt564DataQualityBenchmark();
		benchmark.scenario = "multipleViolations";
		benchmark.setup();
		try (var executor = Executors.newFixedThreadPool(8)) {
			var futures = IntStream.range(0, 800).mapToObj(invocation -> executor.submit(() -> {
				int payloadIndex = invocation % 64;
				Object interpreter = benchmark.evaluateInterpreterAt(payloadIndex).value(benchmark.qualityReportSlot());
				Object generated = benchmark.evaluateGeneratedAt(payloadIndex)[benchmark.qualityReportSlot()];
				assertThat(generated).isEqualTo(interpreter);
			})).toList();
			for (var future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		}
	}

	@Test
	void testCompilerPhaseBenchmarkExecution() throws Exception {
		CompilerPhaseBenchmark benchmark = new CompilerPhaseBenchmark();
		benchmark.setup();
		assertThat(benchmark.xmlParsing()).isNotNull();
		assertThat(benchmark.semanticAnalysis().hasErrors()).isFalse();
		assertThat(benchmark.runtimeIrLowering()).isNotNull();
		assertThat(benchmark.runtimeIrOptimization()).isNotNull();
		assertThat(benchmark.fullCompilation().isSuccess()).isTrue();
	}

	@Test
	void testDataFakerGenerator() {
		BenchmarkDataGenerator generator = new BenchmarkDataGenerator(42L);
		List<Map<String, Object>> traffic = generator.generateTrafficPayloads(10);
		List<Map<String, Object>> credit = generator.generateCreditPayloads(10);
		List<Map<String, Object>> originations = generator.generateOriginationPayloads(10);
		List<Map<String, Object>> loanProducts = generator.generateLoanProductPayloads(10);

		assertThat(traffic).hasSize(10);
		assertThat(credit).hasSize(10);
		assertThat(originations).hasSize(10);
		assertThat(loanProducts).hasSize(10);
		assertThat(traffic.get(0)).containsKey("Speed");
		assertThat(credit.get(0)).containsKey("CreditScore");
		assertThat(originations.get(0)).containsKeys("Age", "MonthlyIncome", "CreditScore", "RequestedAmount");
	}
}

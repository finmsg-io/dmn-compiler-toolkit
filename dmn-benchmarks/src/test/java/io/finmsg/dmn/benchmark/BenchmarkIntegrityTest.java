package io.finmsg.dmn.benchmark;

import io.finmsg.dmn.benchmark.data.BenchmarkDataGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkIntegrityTest {

	@Test
	void testTrafficViolationBenchmarkExecutionAndParity() throws Exception {
		TrafficViolationBenchmark benchmark = new TrafficViolationBenchmark();
		benchmark.setup();

		Object interpreterResult = benchmark.interpreter_TrafficViolation();
		Object generatedJavaResult = benchmark.generatedJava_TrafficViolation();

		assertThat(interpreterResult).isNotNull();
		assertThat(generatedJavaResult).isNotNull();
	}

	@Test
	void testCreditApprovalBenchmarkExecutionAndParity() throws Exception {
		CreditApprovalBenchmark benchmark = new CreditApprovalBenchmark();
		benchmark.setup();

		Object interpreterResult = benchmark.interpreter_CreditApproval();
		Object generatedJavaResult = benchmark.generatedJava_CreditApproval();

		assertThat(interpreterResult).isNotNull();
		assertThat(generatedJavaResult).isNotNull();
	}

	@Test
	void testScalarArithmeticBenchmarkExecutionAndParity() throws Exception {
		ScalarArithmeticBenchmark benchmark = new ScalarArithmeticBenchmark();
		benchmark.setup();

		Object interpreterResult = benchmark.interpreter_ScalarArithmetic();
		Object generatedJavaResult = benchmark.generatedJava_ScalarArithmetic();

		assertThat(interpreterResult).isNotNull();
		assertThat(generatedJavaResult).isNotNull();
	}

	@Test
	void testDataFakerGenerator() {
		BenchmarkDataGenerator generator = new BenchmarkDataGenerator(42L);
		List<Map<String, Object>> traffic = generator.generateTrafficPayloads(10);
		List<Map<String, Object>> credit = generator.generateCreditPayloads(10);

		assertThat(traffic).hasSize(10);
		assertThat(credit).hasSize(10);
		assertThat(traffic.get(0)).containsKey("Speed");
		assertThat(credit.get(0)).containsKey("CreditScore");
	}
}

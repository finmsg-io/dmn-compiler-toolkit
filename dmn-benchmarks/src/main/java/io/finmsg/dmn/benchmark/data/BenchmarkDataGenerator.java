package io.finmsg.dmn.benchmark.data;

import net.datafaker.Faker;
import java.math.BigDecimal;
import java.util.*;

/**
 * Data generator using Net DataFaker to produce realistic, deterministic
 * payloads for benchmarking DMN models without JIT constant hoisting or
 * dead-code elimination.
 */
public final class BenchmarkDataGenerator {

	private final Faker faker;

	public BenchmarkDataGenerator(long seed) {
		this.faker = new Faker(new Random(seed));
	}

	public BenchmarkDataGenerator() {
		this(42L);
	}

	public List<Map<String, Object>> generateTrafficPayloads(int count) {
		List<Map<String, Object>> payloads = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int speedLimit = faker.options().option(30, 50, 65, 70, 80);
			int delta = faker.number().numberBetween(-10, 45);
			int speed = speedLimit + delta;
			Map<String, Object> inputs = new HashMap<>();
			inputs.put("Speed", BigDecimal.valueOf(speed));
			inputs.put("SpeedLimit", BigDecimal.valueOf(speedLimit));
			payloads.add(inputs);
		}
		return payloads;
	}

	public List<Map<String, Object>> generateCreditPayloads(int count) {
		List<Map<String, Object>> payloads = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int creditScore = faker.number().numberBetween(550, 850);
			double income = faker.number().numberBetween(35000, 250000);
			double loan = faker.number().numberBetween(5000, 75000);
			Map<String, Object> inputs = new HashMap<>();
			inputs.put("CreditScore", BigDecimal.valueOf(creditScore));
			inputs.put("AnnualIncome", BigDecimal.valueOf(income));
			inputs.put("RequestedLoan", BigDecimal.valueOf(loan));
			payloads.add(inputs);
		}
		return payloads;
	}

	public List<Map<String, Object>> generateOriginationPayloads(int count) {
		List<Map<String, Object>> payloads = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			BigDecimal income = BigDecimal.valueOf(faker.number().numberBetween(2500, 15000));
			BigDecimal repayments = BigDecimal.valueOf(faker.number().numberBetween(0, 3500));
			BigDecimal expenses = BigDecimal.valueOf(faker.number().numberBetween(1000, 6000));
			Map<String, Object> inputs = new LinkedHashMap<>();
			inputs.put("Age", BigDecimal.valueOf(faker.number().numberBetween(18, 76)));
			inputs.put("EmploymentYears", BigDecimal.valueOf(faker.number().numberBetween(0, 35)));
			inputs.put("MonthlyIncome", income);
			inputs.put("MonthlyRepayments", repayments);
			inputs.put("MonthlyExpenses", expenses);
			inputs.put("CreditScore", BigDecimal.valueOf(faker.number().numberBetween(300, 900)));
			inputs.put("RequestedAmount", BigDecimal.valueOf(faker.number().numberBetween(5000, 100000)));
			inputs.put("TermMonths", BigDecimal.valueOf(faker.options().option(12, 24, 36, 48, 60)));
			payloads.add(inputs);
		}
		return payloads;
	}

	public List<Map<String, Object>> generateLoanProductPayloads(int count) {
		List<Map<String, Object>> payloads = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Map<String, Object> inputs = new LinkedHashMap<>();
			inputs.put("CreditScore", BigDecimal.valueOf(faker.number().numberBetween(500, 900)));
			inputs.put("AnnualIncome", BigDecimal.valueOf(faker.number().numberBetween(30000, 250000)));
			inputs.put("ExistingDebt", BigDecimal.valueOf(faker.number().numberBetween(0, 100000)));
			inputs.put("RequestedAmount", BigDecimal.valueOf(faker.number().numberBetween(5000, 150000)));
			inputs.put("PreferredTerm", BigDecimal.valueOf(faker.options().option(12, 24, 36, 48, 60)));
			payloads.add(inputs);
		}
		return payloads;
	}
}

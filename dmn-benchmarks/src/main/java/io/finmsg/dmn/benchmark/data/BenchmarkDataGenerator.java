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
}

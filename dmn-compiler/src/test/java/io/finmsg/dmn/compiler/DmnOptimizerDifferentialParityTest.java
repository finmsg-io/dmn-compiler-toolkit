package io.finmsg.dmn.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Differential Testing Suite: Validates that the optimizer pipeline is strictly
 * semantically transparent by comparing results of evaluating LOWERED
 * (unoptimized) vs. OPTIMIZED Runtime IR across models.
 */
class DmnOptimizerDifferentialParityTest {

	private final DmnCompiler compiler = new DmnCompiler();
	private final DmnRuntime runtime = new DmnRuntime();
	private final Path corpusRoot = Paths.get("src/test/resources/corpus");

	private static final DmnCompilerOptions LOWERED_OPTIONS = new DmnCompilerOptions(
			DmnCompilerOptions.defaults().modelLoadOptions(), RuntimeModelMode.LOWERED);

	private static final DmnCompilerOptions OPTIMIZED_OPTIONS = new DmnCompilerOptions(
			DmnCompilerOptions.defaults().modelLoadOptions(), RuntimeModelMode.OPTIMIZED);

	@ParameterizedTest(name = "Differential parity on corpus scenario: {0}")
	@ValueSource(strings = {"p2-01-single-import/root.dmn", "p2-02-transitive/level-a.dmn", "p2-03-diamond/top.dmn",
			"p2-04-imported-bkm/caller.dmn", "p2-06-same-name-diff-ns/main.dmn"})
	void assertDifferentialParityOnCorpus(String relativePath) throws IOException {
		Path rootFile = corpusRoot.resolve(relativePath);
		Path scenarioDir = rootFile.getParent();
		DmnSource rootSource = new DmnSource(new DmnSourceId(rootFile.toAbsolutePath().toUri()),
				Files.readAllBytes(rootFile));
		DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

		// 1. Compile LOWERED (unoptimized)
		DmnCompilationResult loweredComp = compiler.compile(rootSource, resolver, LOWERED_OPTIONS);
		assertThat(loweredComp.isSuccess()).isTrue();
		RuntimeModel loweredModel = loweredComp.optimizedRuntimeModel().orElseThrow().model();

		// 2. Compile OPTIMIZED
		DmnCompilationResult optimizedComp = compiler.compile(rootSource, resolver, OPTIMIZED_OPTIONS);
		assertThat(optimizedComp.isSuccess()).isTrue();
		RuntimeModel optimizedModel = optimizedComp.optimizedRuntimeModel().orElseThrow().model();

		// 3. Evaluate both
		Map<Integer, Object> inputs = Map.of();
		DmnEvaluationResult loweredEval = runtime.evaluate(loweredModel, inputs);
		DmnEvaluationResult optimizedEval = runtime.evaluate(optimizedModel, inputs);

		// 4. Assert bit-for-bit parity on all slot values (excluding function lambdas)
		assertEvaluationParity(loweredModel, loweredEval, optimizedModel, optimizedEval);
	}

	@Test
	@DisplayName("Differential parity on Lending Eligibility scenario with dynamic inputs")
	void assertDifferentialParityOnLendingEligibility() throws IOException {
		Path scenarioDir = corpusRoot.resolve("p2-09-lending-eligibility");
		Path rootFile = scenarioDir.resolve("credit-application.dmn");
		DmnSource rootSource = new DmnSource(new DmnSourceId(rootFile.toAbsolutePath().toUri()),
				Files.readAllBytes(rootFile));
		DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

		DmnCompilationResult loweredComp = compiler.compile(rootSource, resolver, LOWERED_OPTIONS);
		DmnCompilationResult optimizedComp = compiler.compile(rootSource, resolver, OPTIMIZED_OPTIONS);

		assertThat(loweredComp.isSuccess()).isTrue();
		assertThat(optimizedComp.isSuccess()).isTrue();

		DmnCompiledModel loweredCompiled = loweredComp.compiledModel().orElseThrow();
		DmnCompiledModel optimizedCompiled = optimizedComp.compiledModel().orElseThrow();

		// Test multiple input vectors
		List<Map<String, Object>> testInputs = List.of(
				Map.of("RequestedAmount", new BigDecimal("10000"), "Applicant",
						Map.of("score", new BigDecimal("750"), "income", new BigDecimal("50000"), "monthlyDebt",
								new BigDecimal("1000"))),
				Map.of("RequestedAmount", new BigDecimal("50000"), "Applicant",
						Map.of("score", new BigDecimal("550"), "income", new BigDecimal("20000"), "monthlyDebt",
								new BigDecimal("2000"))),
				Map.of("RequestedAmount", new BigDecimal("5000"), "Applicant", Map.of("score", new BigDecimal("800"),
						"income", new BigDecimal("100000"), "monthlyDebt", new BigDecimal("500"))));

		for (Map<String, Object> namedInput : testInputs) {
			for (String decisionName : loweredCompiled.decisionSlots().keySet()) {
				Object lowVal = loweredCompiled.evaluateDecision(decisionName, namedInput);
				Object optVal = optimizedCompiled.evaluateDecision(decisionName, namedInput);

				assertThat(optVal).withFailMessage("Optimizer diverged for decision %s: lowered=%s, optimized=%s",
						decisionName, lowVal, optVal).isEqualTo(lowVal);
			}
		}
	}

	private void assertEvaluationParity(RuntimeModel loweredModel, DmnEvaluationResult loweredEval,
			RuntimeModel optimizedModel, DmnEvaluationResult optimizedEval) {
		Set<Integer> bkmSlots = new HashSet<>();
		loweredModel.businessKnowledgeModels().forEach(bkm -> bkmSlots.add(bkm.resultSlot()));

		for (int slot = 0; slot < loweredModel.valueSlotCount(); slot++) {
			if (bkmSlots.contains(slot)) {
				continue;
			}
			Object lowVal = loweredEval.value(slot);
			Object optVal = optimizedEval.value(slot);

			if (lowVal != null && lowVal.getClass().getName().contains("Lambda")) {
				continue;
			}

			assertThat(optVal)
					.withFailMessage("Optimizer diverged at slot %d: lowered=%s, optimized=%s", slot, lowVal, optVal)
					.isEqualTo(lowVal);
		}
	}
}

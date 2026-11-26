package io.finmsg.dmn.compiler.cli;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.frontend.dsl.DmnDslReadOptions;
import io.finmsg.dmn.frontend.dsl.DmnDslReader;
import io.finmsg.dmn.frontend.dsl.DslModel;
import io.finmsg.dmn.frontend.dsl.DslModel.DslTest;
import io.finmsg.dmn.frontend.dsl.DslModel.DslTestEntry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Executes inline {@code test} blocks from a {@code .decision} DSL source
 * against the compiled model using the shared FEEL/DMN runtime. Inline tests
 * are excluded from lowered {@code Definitions} and production {@code .dmnir}
 * payloads.
 */
public final class DmnDslTestRunner {

	public record CaseResult(String testName, boolean passed, List<String> failures) {
		public CaseResult {
			failures = List.copyOf(failures);
		}
	}

	public record TestReport(int total, int passed, int failed, List<CaseResult> results) {
		public TestReport {
			results = List.copyOf(results);
		}
		public boolean allPassed() {
			return failed == 0;
		}
	}

	private final DmnDslReader dslReader = new DmnDslReader();
	private final DmnCompiler compiler = new DmnCompiler();

	public TestReport run(DmnSource dslSource, DmnModelResolver resolver) {
		Objects.requireNonNull(dslSource, "dslSource");
		Objects.requireNonNull(resolver, "resolver");

		String text = new String(dslSource.content(), StandardCharsets.UTF_8);
		DslModel parsed = dslReader.parse(text, DmnDslReadOptions.defaults());
		DmnCompilationResult compiled = compiler.compile(dslSource, resolver, DmnCompilerOptions.optimized());
		if (!compiled.isSuccess()) {
			throw new IllegalStateException("DSL model failed to compile: " + compiled.diagnostics());
		}
		DmnCompiledModel model = compiled.compiledModel().orElseThrow();

		List<CaseResult> results = new ArrayList<>();
		int passedCount = 0;
		int failedCount = 0;

		for (DslTest test : parsed.tests()) {
			List<String> failures = new ArrayList<>();
			Map<String, Object> inputs = new LinkedHashMap<>();
			for (DslTestEntry given : test.givens()) {
				try {
					inputs.put(given.name(), evaluateFeelConstant(given.feelExpression()));
				} catch (Exception e) {
					failures.add("given '" + given.name() + "' (line " + given.line() + "): " + e.getMessage());
				}
			}
			if (failures.isEmpty()) {
				for (DslTestEntry expect : test.expects()) {
					try {
						Object expectedVal = evaluateFeelConstant(expect.feelExpression());
						Object actualVal = model.evaluateDecision(expect.name(), inputs);
						if (!feelEquals(expectedVal, actualVal)) {
							failures.add("expect '" + expect.name() + "' (line " + expect.line() + "): expected <"
									+ expectedVal + "> but got <" + actualVal + ">");
						}
					} catch (Exception e) {
						failures.add("expect '" + expect.name() + "' (line " + expect.line() + "): " + e.getMessage());
					}
				}
			}
			boolean ok = failures.isEmpty();
			if (ok) {
				passedCount++;
			} else {
				failedCount++;
			}
			results.add(new CaseResult(test.name(), ok, failures));
		}

		return new TestReport(results.size(), passedCount, failedCount, results);
	}

	private Object evaluateFeelConstant(String feelExpression) {
		String wrapperDsl = "model \"__eval\"\nnamespace \"urn:finmsg:dsl:eval\"\ndecision __v: Any\n  = "
				+ feelExpression + "\n";
		DmnSource src = new DmnSource(DmnSourceId.of("memory:/__eval.decision"),
				wrapperDsl.getBytes(StandardCharsets.UTF_8));
		DmnCompilationResult result = compiler.compile(src, new InMemoryDmnModelResolver(List.of()),
				DmnCompilerOptions.optimized());
		if (!result.isSuccess()) {
			throw new IllegalArgumentException(
					"Invalid FEEL expression '" + feelExpression + "': " + result.diagnostics());
		}
		return result.compiledModel().orElseThrow().evaluateDecision("__v", Map.of());
	}

	private static boolean feelEquals(Object expected, Object actual) {
		if (expected == actual) {
			return true;
		}
		if (expected == null || actual == null) {
			return false;
		}
		if (expected instanceof BigDecimal eb && actual instanceof BigDecimal ab) {
			return eb.compareTo(ab) == 0;
		}
		if (expected instanceof List<?> el && actual instanceof List<?> al) {
			if (el.size() != al.size()) {
				return false;
			}
			for (int i = 0; i < el.size(); i++) {
				if (!feelEquals(el.get(i), al.get(i))) {
					return false;
				}
			}
			return true;
		}
		if (expected instanceof Map<?, ?> em && actual instanceof Map<?, ?> am) {
			if (em.size() != am.size()) {
				return false;
			}
			for (var entry : em.entrySet()) {
				if (!am.containsKey(entry.getKey()) || !feelEquals(entry.getValue(), am.get(entry.getKey()))) {
					return false;
				}
			}
			return true;
		}
		return Objects.equals(expected, actual);
	}
}

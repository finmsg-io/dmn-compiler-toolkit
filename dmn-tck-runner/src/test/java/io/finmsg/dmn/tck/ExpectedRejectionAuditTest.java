package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExpectedRejectionAuditTest {
	private final TckTestCase errorCase = new TckTestCase("error", "error", Map.of(),
			Map.of("result", TckValue.nullValue()), Set.of("result"));

	@Test
	void noHistoricalCompilationDiagnosticCanSatisfyExecution() throws Exception {
		try (var input = getClass().getResourceAsStream("/expected-rejection-audit.tsv")) {
			assertThat(input).isNotNull();
			var rows = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().skip(1).toList();
			assertThat(rows).hasSize(676);
			for (String row : rows) {
				var fields = row.split("\t", -1);
				assertThatThrownBy(() -> OfficialTckSuiteTest.requireCompilationSucceeded(fields[9], null))
						.isInstanceOf(AssertionError.class).hasMessageStartingWith("UNEXECUTED_COMPILATION_FAILURE:");
			}
		}
	}
	@Test
	void missingOutputIsNotANullErrorResult() {
		assertThatThrownBy(() -> OfficialTckSuiteTest.assertExpected("case", errorCase, Map.of(), "test"))
				.isInstanceOf(AssertionError.class).hasMessageContaining("did not produce requested results");
	}
	@Test
	void producedNullIsCheckedAndAccepted() {
		Map<String, Object> actual = new HashMap<>();
		actual.put("result", null);
		OfficialTckSuiteTest.assertExpected("case", errorCase, actual, "test");
	}
	@Test
	void wrongProducedValueFailsEvenWhenCaseExpectsError() {
		assertThatThrownBy(() -> OfficialTckSuiteTest.assertExpected("case", errorCase, Map.of("result", true), "test"))
				.isInstanceOf(AssertionError.class).hasMessageContaining("expected FEEL error / null");
	}
}

package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TckCatalogueReportTest {
	private static final String ENTRY_ID = "level/case-test-01.xml";

	@Test
	void conservesEveryCaseAcrossEveryDeclaredBackend() {
		TckCatalogueInventory inventory = executableInventory();
		TckCatalogueReport report = new TckCatalogueReport("revision", List.of("interpreter", "generated-java"),
				inventory, List.of(outcome("interpreter", TckCatalogueStatus.PASSED),
						outcome("generated-java", TckCatalogueStatus.FAILED)));

		assertThat(report.statusCounts()).containsEntry(TckCatalogueStatus.PASSED, 1L)
				.containsEntry(TckCatalogueStatus.FAILED, 1L);
	}

	@Test
	void rejectsMissingAndDuplicateTerminalOutcomes() {
		TckCatalogueInventory inventory = executableInventory();
		TckCatalogueOutcome interpreter = outcome("interpreter", TckCatalogueStatus.PASSED);

		assertThatThrownBy(() -> new TckCatalogueReport("revision", List.of("interpreter", "generated-java"), inventory,
				List.of(interpreter))).hasMessageContaining("missing=");
		assertThatThrownBy(() -> new TckCatalogueReport("revision", List.of("interpreter"), inventory,
				List.of(interpreter, interpreter))).hasMessageContaining("Duplicate terminal outcome");
	}

	@Test
	void accountsInvalidEntryOnceAtCatalogueLevel() {
		Path xml = Path.of("invalid-test-01.xml");
		TckCatalogueEntry entry = new TckCatalogueEntry("invalid-test-01.xml", xml, Optional.empty(), List.of(),
				List.of(), Optional.of("broken XML"));
		TckCatalogueInventory inventory = new TckCatalogueInventory(1, 1, 0, 0, List.of(entry));
		TckCatalogueOutcome invalid = new TckCatalogueOutcome(entry.id(), "", "catalogue", TckCatalogueStatus.INVALID,
				"broken XML");

		assertThat(
				new TckCatalogueReport("revision", List.of("interpreter"), inventory, List.of(invalid)).statusCounts())
				.containsEntry(TckCatalogueStatus.INVALID, 1L);
	}

	private static TckCatalogueInventory executableInventory() {
		Path xml = Path.of(ENTRY_ID);
		TckTestCase testCase = new TckTestCase("case-1", "fixture", Map.of(), Map.of("result", TckValue.string("ok")));
		TckCatalogueEntry entry = new TckCatalogueEntry(ENTRY_ID, xml, Optional.of(Path.of("case.dmn")),
				List.of(Path.of("case.dmn")), List.of(testCase), Optional.empty());
		return new TckCatalogueInventory(2, 1, 1, 1, List.of(entry));
	}

	private static TckCatalogueOutcome outcome(String backend, TckCatalogueStatus status) {
		return new TckCatalogueOutcome(ENTRY_ID, "case-1", backend, status, "");
	}
}

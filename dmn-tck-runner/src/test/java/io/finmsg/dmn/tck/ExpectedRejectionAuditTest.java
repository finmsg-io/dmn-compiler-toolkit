package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExpectedRejectionAuditTest {
	private static final Path ROOT = Path.of("src/test/resources/tck-official/TestCases");
	private static final String ENTRY = "compliance-level-3/0057-feel-context/0057-feel-context-test-01.xml";
	private static final String KEY = ENTRY + "#008";

	private TckCatalogueEntry entry(String id) throws Exception {
		Path test = ROOT.resolve(id);
		Path model = test.getParent().resolve(test.getParent().getFileName() + ".dmn");
		return new TckCatalogueEntry(id, test, Optional.of(model), List.of(model), new TckTestCaseReader().read(test),
				Optional.empty());
	}
	private TckTestCase testCase(TckCatalogueEntry entry, String id) {
		return entry.testCases().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void verifiesEveryReviewedCaseAgainstOfficialFixtures() throws Exception {
		Map<String, TckCatalogueEntry> entries = new HashMap<>();
		int accepted = 0, requiresExecution = 0;
		for (var row : ExpectedRejectionAudit.reviews().entrySet()) {
			String[] key = row.getKey().split("#", 2);
			TckCatalogueEntry entry = entries.get(key[0]);
			if (entry == null) {
				entry = entry(key[0]);
				entries.put(key[0], entry);
			}
			TckTestCase testCase = testCase(entry, key[1]);
			final TckCatalogueEntry current = entry;
			if (row.getValue().classification().equals("RELATED_STATIC_REJECTION")) {
				ExpectedRejectionAudit.requireReviewed(entry, testCase, row.getValue().diagnostic());
				accepted++;
			} else {
				assertThatThrownBy(
						() -> ExpectedRejectionAudit.requireReviewed(current, testCase, row.getValue().diagnostic()))
						.isInstanceOf(AssertionError.class).hasMessageStartingWith("UNVERIFIED_MODEL_REJECTION:");
				requiresExecution++;
			}
		}
		assertThat(accepted).isEqualTo(662);
		assertThat(requiresExecution).isEqualTo(14);
	}

	@Test
	void unrelatedCompilerErrorCannotSatisfyAnExpectedError() throws Exception {
		var entry = entry(ENTRY);
		assertThatThrownBy(() -> ExpectedRejectionAudit.requireReviewed(entry, testCase(entry, "008"),
				"Compilation unsuccessful: SEMANTIC_ANALYSIS/UNKNOWN_NAME: unrelated error"))
				.isInstanceOf(AssertionError.class).hasMessageContaining("diagnostic changed");
	}

	@Test
	void missingCaseReviewFailsClosed() throws Exception {
		var entry = entry(ENTRY);
		assertThatThrownBy(() -> ExpectedRejectionAudit.requireReviewed(entry, testCase(entry, "009"), "anything"))
				.isInstanceOf(AssertionError.class).hasMessageContaining("No case-level review");
	}

	@Test
	void successOrMixedResultExpectationsCannotUseStaticAcceptance() throws Exception {
		var entry = entry(ENTRY);
		var values = Map.of("decision008", TckValue.nullValue(), "other", TckValue.bool(true));
		var mixed = new TckTestCase("008", "mixed", Map.of(), values, Set.of("decision008"));
		assertThatThrownBy(() -> ExpectedRejectionAudit.requireReviewed(entry, mixed,
				ExpectedRejectionAudit.reviews().get(KEY).diagnostic())).isInstanceOf(AssertionError.class)
				.hasMessageContaining("Not every requested result");
	}

	@Test
	void changedRequestedResultCannotReuseReview() throws Exception {
		var entry = entry(ENTRY);
		var changed = new TckTestCase("008", "changed", Map.of(), Map.of("other", TckValue.nullValue()),
				Set.of("other"));
		assertThatThrownBy(() -> ExpectedRejectionAudit.requireReviewed(entry, changed,
				ExpectedRejectionAudit.reviews().get(KEY).diagnostic())).isInstanceOf(AssertionError.class)
				.hasMessageContaining("Requested result nodes changed");
	}

	@Test
	void changedModelCannotReuseReview(@TempDir Path temporary) throws Exception {
		var original = entry(ENTRY);
		Path model = temporary.resolve("changed.dmn");
		Files.writeString(model, "<changed/>");
		var changed = new TckCatalogueEntry(ENTRY, original.testXml(), Optional.of(model), List.of(model),
				original.testCases(), Optional.empty());
		assertThatThrownBy(() -> ExpectedRejectionAudit.requireReviewed(changed, testCase(original, "008"),
				ExpectedRejectionAudit.reviews().get(KEY).diagnostic())).isInstanceOf(AssertionError.class)
				.hasMessageContaining("fixture changed");
	}
}

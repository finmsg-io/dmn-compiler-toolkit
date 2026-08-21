package io.finmsg.dmn.tck;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable report that rejects duplicate or missing terminal outcomes. */
public record TckCatalogueReport(String revision, List<String> backends, TckCatalogueInventory inventory,
		List<TckCatalogueOutcome> outcomes) {
	public TckCatalogueReport {
		if (revision == null || revision.isBlank())
			throw new IllegalArgumentException("TCK revision is required");
		backends = List.copyOf(backends);
		inventory = java.util.Objects.requireNonNull(inventory, "inventory");
		outcomes = List.copyOf(outcomes);
		Set<String> keys = new HashSet<>();
		for (TckCatalogueOutcome outcome : outcomes) {
			if (!keys.add(outcome.key()))
				throw new IllegalArgumentException("Duplicate terminal outcome: " + outcome.key());
		}
		Set<String> expected = new HashSet<>();
		for (TckCatalogueEntry entry : inventory.entries()) {
			if (!entry.executable()) {
				expected.add(entry.id() + "#@catalogue");
				continue;
			}
			for (TckTestCase testCase : entry.testCases())
				for (String backend : backends)
					expected.add(entry.id() + "#" + testCase.id() + "@" + backend);
		}
		if (!keys.equals(expected)) {
			Set<String> missing = new HashSet<>(expected);
			missing.removeAll(keys);
			Set<String> unexpected = new HashSet<>(keys);
			unexpected.removeAll(expected);
			throw new IllegalArgumentException(
					"Terminal outcome conservation failed; missing=" + missing + ", unexpected=" + unexpected);
		}
	}

	public Map<TckCatalogueStatus, Long> statusCounts() {
		Map<TckCatalogueStatus, Long> counts = new EnumMap<>(TckCatalogueStatus.class);
		for (TckCatalogueStatus status : TckCatalogueStatus.values())
			counts.put(status, 0L);
		for (TckCatalogueOutcome outcome : outcomes)
			counts.compute(outcome.status(), (ignored, count) -> count + 1);
		return Map.copyOf(counts);
	}
}

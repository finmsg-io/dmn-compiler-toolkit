package io.finmsg.dmn.tck;

import java.util.List;

/**
 * Immutable inventory proving conservation of discovered TCK catalogue files.
 */
public record TckCatalogueInventory(int directoryCount, int testXmlCount, int dmnFileCount, int decodedCaseCount,
		List<TckCatalogueEntry> entries) {
	public TckCatalogueInventory {
		entries = List.copyOf(entries);
		if (directoryCount < 1 || testXmlCount < 0 || dmnFileCount < 0 || decodedCaseCount < 0) {
			throw new IllegalArgumentException("Catalogue counts must be non-negative");
		}
		if (testXmlCount != entries.size()) {
			throw new IllegalArgumentException("Discovered test XML count does not equal accounted entries");
		}
		int cases = entries.stream().mapToInt(entry -> entry.testCases().size()).sum();
		if (decodedCaseCount != cases) {
			throw new IllegalArgumentException("Decoded test-case count does not equal accounted cases");
		}
	}

	public TckCatalogueEntry requireExactCase(String selector) {
		TckCatalogueEntry match = null;
		for (TckCatalogueEntry entry : entries) {
			for (TckTestCase testCase : entry.testCases()) {
				if ((entry.id() + "#" + testCase.id()).equals(selector)) {
					if (match != null) {
						throw new IllegalArgumentException("Exact TCK selector is ambiguous: " + selector);
					}
					match = entry;
				}
			}
		}
		if (match == null) {
			throw new IllegalArgumentException("Exact TCK selector matched no case: " + selector);
		}
		return match;
	}
}

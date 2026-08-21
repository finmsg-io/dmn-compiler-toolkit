package io.finmsg.dmn.tck;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministically discovered TCK test XML and its local model set. */
public record TckCatalogueEntry(String id, Path testXml, Optional<Path> rootDmn, List<Path> dmnFiles,
		List<TckTestCase> testCases, Optional<String> invalidReason) {
	public TckCatalogueEntry {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(testXml, "testXml");
		rootDmn = Objects.requireNonNull(rootDmn, "rootDmn");
		dmnFiles = List.copyOf(dmnFiles);
		testCases = List.copyOf(testCases);
		invalidReason = Objects.requireNonNull(invalidReason, "invalidReason");
		if (invalidReason.isPresent() == rootDmn.isPresent()) {
			throw new IllegalArgumentException("Catalogue entry must be either executable or invalid");
		}
	}

	public boolean executable() {
		return rootDmn.isPresent();
	}
}

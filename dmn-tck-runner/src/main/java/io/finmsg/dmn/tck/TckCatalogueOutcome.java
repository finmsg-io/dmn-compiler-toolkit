package io.finmsg.dmn.tck;

import java.util.Objects;

/**
 * One terminal, machine-readable outcome for a catalogue entry or backend case
 * execution.
 */
public record TckCatalogueOutcome(String entryId, String caseId, String backend, TckCatalogueStatus status,
		String diagnostic) {
	public TckCatalogueOutcome {
		if (Objects.requireNonNull(entryId, "entryId").isBlank())
			throw new IllegalArgumentException("Entry ID is blank");
		caseId = Objects.requireNonNull(caseId, "caseId");
		if (Objects.requireNonNull(backend, "backend").isBlank())
			throw new IllegalArgumentException("Backend is blank");
		Objects.requireNonNull(status, "status");
		diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
	}

	public String key() {
		return entryId + "#" + caseId + "@" + backend;
	}
}

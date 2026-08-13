package io.finmsg.dmn.compiler;

import java.util.Objects;
import java.util.Optional;

/** Stable source and optional model/import identity for one diagnostic. */
public record DmnDiagnosticOrigin(DmnSourceId sourceId, Optional<DmnModelIdentity> modelIdentity,
		Optional<Integer> importIndex) {

	public DmnDiagnosticOrigin {
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(modelIdentity, "modelIdentity");
		Objects.requireNonNull(importIndex, "importIndex");
		modelIdentity.ifPresent(identity -> Objects.requireNonNull(identity, "modelIdentity value"));
		importIndex.ifPresent(index -> {
			if (index < 0) {
				throw new IllegalArgumentException("importIndex must not be negative");
			}
		});
	}

	public static DmnDiagnosticOrigin source(DmnSourceId sourceId) {
		return new DmnDiagnosticOrigin(sourceId, Optional.empty(), Optional.empty());
	}
}

package io.finmsg.dmn.compiler;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, phase-aware diagnostic exposed at the compiler boundary. */
public record DmnCompilerDiagnostic(DmnDiagnosticSeverity severity, DmnCompilerPhase phase, String code, String message,
		DmnDiagnosticOrigin origin, Optional<DmnImportRequest> importRequest, List<DmnSourceId> relatedSourceIds,
		List<DmnSourceId> cyclePath) {

	public static final Comparator<DmnCompilerDiagnostic> ORDER = Comparator
			.comparing((DmnCompilerDiagnostic diagnostic) -> diagnostic.origin().sourceId())
			.thenComparing(diagnostic -> diagnostic.origin().importIndex(), optionalIntegerOrder())
			.thenComparingInt(diagnostic -> phaseRank(diagnostic.phase()))
			.thenComparingInt(diagnostic -> severityRank(diagnostic.severity()))
			.thenComparing(DmnCompilerDiagnostic::code)
			.thenComparing(diagnostic -> diagnostic.relatedSourceIds().toString())
			.thenComparing(diagnostic -> diagnostic.cyclePath().toString())
			.thenComparing(DmnCompilerDiagnostic::message);

	public DmnCompilerDiagnostic {
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(phase, "phase");
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(importRequest, "importRequest");
		Objects.requireNonNull(relatedSourceIds, "relatedSourceIds");
		Objects.requireNonNull(cyclePath, "cyclePath");
		if (code.isBlank()) {
			throw new IllegalArgumentException("code must not be blank");
		}
		if (message.isBlank()) {
			throw new IllegalArgumentException("message must not be blank");
		}
		importRequest.ifPresent(request -> {
			if (!request.importer().equals(origin.sourceId())) {
				throw new IllegalArgumentException("Import request must belong to the diagnostic source");
			}
			if (origin.importIndex().isEmpty()) {
				throw new IllegalArgumentException("Import diagnostics require an import index");
			}
		});
		relatedSourceIds = relatedSourceIds.stream().map(id -> Objects.requireNonNull(id, "relatedSourceId")).distinct()
				.sorted().toList();
		cyclePath = cyclePath.stream().map(id -> Objects.requireNonNull(id, "cyclePathId")).toList();
		if (DmnDiagnosticCodes.IMPORT_CYCLE.equals(code)) {
			if (cyclePath.size() < 2 || !cyclePath.getFirst().equals(cyclePath.getLast())) {
				throw new IllegalArgumentException("Cycle diagnostics require a closed cycle path");
			}
		} else if (!cyclePath.isEmpty()) {
			throw new IllegalArgumentException("Only cycle diagnostics may contain a cycle path");
		}
	}

	private static Comparator<Optional<Integer>> optionalIntegerOrder() {
		return Comparator.comparingInt(value -> value.orElse(-1));
	}

	private static int phaseRank(DmnCompilerPhase phase) {
		return switch (phase) {
			case SOURCE_RESOLUTION -> 0;
			case XML_FRONTEND -> 1;
			case FEEL_PARSING -> 2;
			case SEMANTIC_ANALYSIS -> 3;
			case RUNTIME_IR_LOWERING -> 4;
			case OPTIMIZATION -> 5;
			case GENERATION -> 6;
		};
	}

	private static int severityRank(DmnDiagnosticSeverity severity) {
		return switch (severity) {
			case INFO -> 0;
			case WARNING -> 1;
			case ERROR -> 2;
		};
	}
}

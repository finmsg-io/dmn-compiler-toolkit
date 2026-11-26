package io.finmsg.dmn.frontend.dsl;

import java.util.List;
import java.util.Objects;

/**
 * Exception thrown when a {@code .decision} DSL source fails bounded parsing or
 * lowering.
 */
public final class DmnDslParseException extends RuntimeException {

	private final List<DmnDslDiagnostic> diagnostics;

	public DmnDslParseException(List<DmnDslDiagnostic> diagnostics) {
		super(formatMessage(diagnostics));
		this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
	}

	public DmnDslParseException(DmnDslDiagnostic diagnostic) {
		this(List.of(Objects.requireNonNull(diagnostic, "diagnostic")));
	}

	public List<DmnDslDiagnostic> diagnostics() {
		return diagnostics;
	}

	private static String formatMessage(List<DmnDslDiagnostic> diagnostics) {
		if (diagnostics == null || diagnostics.isEmpty()) {
			return "Failed to parse .decision DSL source";
		}
		return diagnostics.getFirst().toString();
	}
}

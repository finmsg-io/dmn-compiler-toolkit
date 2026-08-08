package io.finmsg.dmn.feel.parser;

import java.util.List;

/**
 * Thrown by the strict DMN FEEL parsing API when one or more FEEL nodes are
 * invalid.
 */
public final class DmnFeelParseException extends IllegalArgumentException {

	private final List<DmnFeelDiagnostic> diagnostics;

	public DmnFeelParseException(List<DmnFeelDiagnostic> diagnostics) {
		super(format(diagnostics));
		this.diagnostics = List.copyOf(diagnostics);
	}

	public List<DmnFeelDiagnostic> diagnostics() {
		return diagnostics;
	}

	private static String format(List<DmnFeelDiagnostic> diagnostics) {
		String details = diagnostics.stream().map(diagnostic -> diagnostic.path() + ": "
				+ diagnostic.diagnostics().stream().map(
						value -> "line %d:%d %s".formatted(value.line(), value.charPositionInLine(), value.message()))
						.reduce((left, right) -> left + "; " + right).orElse("unknown syntax error"))
				.reduce((left, right) -> left + System.lineSeparator() + right).orElse("unknown FEEL error");
		return "Invalid FEEL expressions in DMN model:" + System.lineSeparator() + details;
	}
}

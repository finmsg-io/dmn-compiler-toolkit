package io.finmsg.dmn.frontend.dsl;

import java.util.Objects;

/**
 * Source-positioned diagnostic produced while parsing or lowering a
 * {@code .decision} DSL model.
 */
public record DmnDslDiagnostic(String code, int line, int column, String message) {

	public DmnDslDiagnostic {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(message, "message");
		if (line < 1) {
			throw new IllegalArgumentException("line must be >= 1");
		}
		if (column < 1) {
			throw new IllegalArgumentException("column must be >= 1");
		}
	}

	@Override
	public String toString() {
		return "[" + code + "] line " + line + ":" + column + " - " + message;
	}
}

package io.finmsg.dmn.feel.parser;

import java.util.List;

/** Thrown by strict parser facade methods when FEEL input is invalid. */
public final class FeelParseException extends IllegalArgumentException {

    private final List<FeelDiagnostic> diagnostics;

    public FeelParseException(String source, List<FeelDiagnostic> diagnostics) {
        super("Invalid FEEL input: " + source + System.lineSeparator() + format(diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<FeelDiagnostic> diagnostics() {
        return diagnostics;
    }

    private static String format(List<FeelDiagnostic> diagnostics) {
        return diagnostics.stream()
                .map(d -> "line %d:%d %s".formatted(
                        d.line(), d.charPositionInLine(), d.message()))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("unknown syntax error");
    }
}

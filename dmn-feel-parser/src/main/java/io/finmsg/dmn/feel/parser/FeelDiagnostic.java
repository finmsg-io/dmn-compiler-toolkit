package io.finmsg.dmn.feel.parser;

/** A lexer or parser diagnostic with a source position. */
public record FeelDiagnostic(
        int line,
        int charPositionInLine,
        String message) {
}

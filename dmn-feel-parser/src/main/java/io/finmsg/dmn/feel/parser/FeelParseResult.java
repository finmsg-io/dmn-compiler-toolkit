package io.finmsg.dmn.feel.parser;

import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;

/** Parse tree plus collected lexer/parser diagnostics. */
public record FeelParseResult<T extends ParserRuleContext>(
        T tree,
        List<FeelDiagnostic> diagnostics) {

    public FeelParseResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean isValid() {
        return diagnostics.isEmpty();
    }
}

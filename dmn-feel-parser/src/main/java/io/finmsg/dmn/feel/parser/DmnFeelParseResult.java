package io.finmsg.dmn.feel.parser;

import io.finmsg.dmn.model.Definitions;
import java.util.List;

/** Copied DMN model plus every FEEL diagnostic collected during the parsing pass. */
public record DmnFeelParseResult(
    Definitions model,
    List<DmnFeelDiagnostic> diagnostics) {

  public DmnFeelParseResult {
    diagnostics = List.copyOf(diagnostics);
  }

  public boolean isSuccess() {
    return diagnostics.isEmpty();
  }
}

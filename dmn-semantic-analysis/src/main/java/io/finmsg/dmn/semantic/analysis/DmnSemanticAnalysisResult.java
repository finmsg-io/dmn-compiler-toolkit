package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.Definitions;
import java.util.List;

/** Semantic model plus diagnostics produced by semantic-analysis passes. */
public record DmnSemanticAnalysisResult(
    Definitions model,
    List<DmnSemanticDiagnostic> diagnostics) {

  public DmnSemanticAnalysisResult {
    diagnostics = List.copyOf(diagnostics);
  }

  public boolean isSuccess() {
    return diagnostics.isEmpty();
  }
}

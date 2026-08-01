package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.Definitions;
import java.util.List;

/** Semantic model plus diagnostics produced by semantic-analysis passes. */
public record DmnSemanticAnalysisResult(
    Definitions model,
    List<DmnSemanticDiagnostic> diagnostics,
    List<DmnSymbolBinding> bindings) {

  public DmnSemanticAnalysisResult {
    diagnostics = List.copyOf(diagnostics);
    bindings = List.copyOf(bindings);
  }

  public DmnSemanticAnalysisResult(
      Definitions model, List<DmnSemanticDiagnostic> diagnostics) {
    this(model, diagnostics, List.of());
  }

  public boolean isSuccess() {
    return diagnostics.isEmpty();
  }
}

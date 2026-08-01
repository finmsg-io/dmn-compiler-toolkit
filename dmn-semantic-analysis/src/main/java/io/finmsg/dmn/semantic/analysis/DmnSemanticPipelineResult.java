package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import java.util.List;

/** Final result of all semantic-analysis passes. */
public record DmnSemanticPipelineResult(
    Definitions model,
    List<DrgElement> compilationOrder,
    List<DmnSemanticDiagnostic> diagnostics) {

  public DmnSemanticPipelineResult {
    compilationOrder = List.copyOf(compilationOrder);
    diagnostics = List.copyOf(diagnostics);
  }

  public boolean isSuccess() {
    return diagnostics.isEmpty();
  }
}

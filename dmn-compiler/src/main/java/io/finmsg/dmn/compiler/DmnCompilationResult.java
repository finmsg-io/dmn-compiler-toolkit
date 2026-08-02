package io.finmsg.dmn.compiler;

import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable evidence returned by one complete compiler-facade invocation. */
public record DmnCompilationResult(
    DmnSourceId rootId,
    DmnModelLoadResult loadedModels,
    DmnModelSetSemanticResult semanticResult,
    Optional<RuntimeOptimizedModel> optimizedRuntimeModel,
    List<DmnCompilerDiagnostic> diagnostics) {

  public DmnCompilationResult {
    Objects.requireNonNull(rootId, "rootId");
    Objects.requireNonNull(loadedModels, "loadedModels");
    Objects.requireNonNull(semanticResult, "semanticResult");
    Objects.requireNonNull(optimizedRuntimeModel, "optimizedRuntimeModel");
    Objects.requireNonNull(diagnostics, "diagnostics");
    if (!rootId.equals(loadedModels.rootId()) || !rootId.equals(semanticResult.rootId())) {
      throw new IllegalArgumentException("Compilation stage results must share the root identity");
    }
    diagnostics = diagnostics.stream()
        .map(diagnostic -> Objects.requireNonNull(diagnostic, "diagnostic"))
        .sorted(DmnCompilerDiagnostic.ORDER)
        .toList();
    if (optimizedRuntimeModel.isPresent() && diagnostics.stream()
        .anyMatch(diagnostic -> diagnostic.severity() == DmnDiagnosticSeverity.ERROR)) {
      throw new IllegalArgumentException("Runtime IR must be absent when compilation has errors");
    }
  }

  public boolean isSuccess() {
    return optimizedRuntimeModel.isPresent() && !hasErrors();
  }

  public boolean hasErrors() {
    return diagnostics.stream()
        .anyMatch(diagnostic -> diagnostic.severity() == DmnDiagnosticSeverity.ERROR);
  }
}

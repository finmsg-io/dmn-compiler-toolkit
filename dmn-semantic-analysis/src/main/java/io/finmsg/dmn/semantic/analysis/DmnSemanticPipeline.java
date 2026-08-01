package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.Definitions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Public entry point that executes all semantic-analysis passes in dependency order. */
public final class DmnSemanticPipeline {

  private final DmnSemanticPass<DmnSemanticAnalysisResult> references;
  private final DmnSemanticPass<DmnSemanticAnalysisResult> types;
  private final DmnSemanticPass<DmnDependencyAnalysisResult> dependencies;

  public DmnSemanticPipeline() {
    this(new DmnSemanticAnalyzer(), new DmnTypeAnalyzer(), new DmnDependencyAnalyzer());
  }

  DmnSemanticPipeline(
      DmnSemanticPass<DmnSemanticAnalysisResult> references,
      DmnSemanticPass<DmnSemanticAnalysisResult> types,
      DmnSemanticPass<DmnDependencyAnalysisResult> dependencies) {
    this.references = Objects.requireNonNull(references, "references");
    this.types = Objects.requireNonNull(types, "types");
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
  }

  public DmnSemanticPipelineResult analyze(Definitions parsedModel) {
    Objects.requireNonNull(parsedModel, "parsedModel");

    DmnSemanticAnalysisResult referenceResult = references.analyze(parsedModel);
    DmnSemanticAnalysisResult typeResult = types.analyze(referenceResult.model());
    DmnDependencyAnalysisResult dependencyResult = dependencies.analyze(typeResult.model());

    List<DmnSemanticDiagnostic> diagnostics = new ArrayList<>();
    Set<DiagnosticKey> seen = new LinkedHashSet<>();
    addDiagnostics(diagnostics, seen, referenceResult.diagnostics());
    addDiagnostics(diagnostics, seen, typeResult.diagnostics());
    addDiagnostics(diagnostics, seen, dependencyResult.diagnostics());

    return new DmnSemanticPipelineResult(
        typeResult.model(), dependencyResult.compilationOrder(), diagnostics);
  }

  private static void addDiagnostics(
      List<DmnSemanticDiagnostic> target,
      Set<DiagnosticKey> seen,
      List<DmnSemanticDiagnostic> source) {
    for (DmnSemanticDiagnostic diagnostic : source) {
      DiagnosticKey key = new DiagnosticKey(diagnostic.code(), diagnostic.path());
      if (seen.add(key)) {
        target.add(diagnostic);
      }
    }
  }

  private record DiagnosticKey(String code, String path) {
  }
}

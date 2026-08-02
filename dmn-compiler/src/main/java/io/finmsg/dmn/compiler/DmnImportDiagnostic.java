package io.finmsg.dmn.compiler;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable P1.4 diagnostic for one invalid import-graph structure. */
public record DmnImportDiagnostic(
    DmnImportDiagnosticCode code,
    DmnSourceId importer,
    int importIndex,
    DmnImportRequest request,
    List<DmnSourceId> relatedSourceIds,
    List<DmnSourceId> cyclePath,
    String message) {

  public DmnImportDiagnostic {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(importer, "importer");
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(relatedSourceIds, "relatedSourceIds");
    Objects.requireNonNull(cyclePath, "cyclePath");
    Objects.requireNonNull(message, "message");
    if (importIndex < 0) {
      throw new IllegalArgumentException("importIndex must not be negative");
    }
    if (!request.importer().equals(importer)) {
      throw new IllegalArgumentException("Import request must belong to the importing source");
    }
    relatedSourceIds = relatedSourceIds.stream()
        .map(id -> Objects.requireNonNull(id, "relatedSourceId"))
        .distinct()
        .sorted()
        .toList();
    cyclePath = cyclePath.stream()
        .map(id -> Objects.requireNonNull(id, "cyclePathId"))
        .toList();
    if (message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
    if (code == DmnImportDiagnosticCode.CYCLE && cyclePath.size() < 2) {
      throw new IllegalArgumentException("Cycle diagnostics require a closed cycle path");
    }
  }

  static final Comparator<DmnImportDiagnostic> ORDER = Comparator
      .comparing(DmnImportDiagnostic::importer)
      .thenComparingInt(DmnImportDiagnostic::importIndex)
      .thenComparing(DmnImportDiagnostic::code)
      .thenComparing(diagnostic -> diagnostic.relatedSourceIds().toString())
      .thenComparing(diagnostic -> diagnostic.cyclePath().toString());
}

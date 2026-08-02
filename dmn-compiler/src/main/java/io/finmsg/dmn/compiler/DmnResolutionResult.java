package io.finmsg.dmn.compiler;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministically ordered candidates returned for an import request. */
public record DmnResolutionResult(DmnImportRequest request, List<DmnSource> candidates) {

  public DmnResolutionResult {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(candidates, "candidates");
    candidates = candidates.stream()
        .map(candidate -> Objects.requireNonNull(candidate, "candidate"))
        .sorted(Comparator.comparing(DmnSource::id))
        .toList();
    long distinctIds = candidates.stream().map(DmnSource::id).distinct().count();
    if (distinctIds != candidates.size()) {
      throw new IllegalArgumentException("Resolution candidates must have distinct source IDs");
    }
  }

  public static DmnResolutionResult missing(DmnImportRequest request) {
    return new DmnResolutionResult(request, List.of());
  }

  public static DmnResolutionResult resolved(DmnImportRequest request, DmnSource source) {
    return new DmnResolutionResult(request, List.of(source));
  }

  public boolean isMissing() {
    return candidates.isEmpty();
  }

  public boolean isAmbiguous() {
    return candidates.size() > 1;
  }

  public Optional<DmnSource> uniqueSource() {
    return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
  }
}

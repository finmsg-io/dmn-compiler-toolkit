package io.finmsg.dmn.compiler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Location-based resolver for tests, embedded models, and programmatic compilation. */
public final class InMemoryDmnModelResolver implements DmnModelResolver {
  private final Map<DmnSourceId, DmnSource> sources;

  public InMemoryDmnModelResolver(Collection<DmnSource> sources) {
    Objects.requireNonNull(sources, "sources");
    Map<DmnSourceId, DmnSource> indexed = new LinkedHashMap<>();
    for (DmnSource source : sources) {
      Objects.requireNonNull(source, "source");
      if (indexed.putIfAbsent(source.id(), source) != null) {
        throw new IllegalArgumentException("Duplicate DMN source identity: " + source.id());
      }
    }
    this.sources = Map.copyOf(indexed);
  }

  @Override
  public DmnResolutionResult resolve(DmnImportRequest request) {
    Objects.requireNonNull(request, "request");
    return request.resolvedLocation()
        .map(sources::get)
        .map(source -> DmnResolutionResult.resolved(request, source))
        .orElseGet(() -> DmnResolutionResult.missing(request));
  }
}

package io.finmsg.dmn.models.stream;

import io.finmsg.dmn.compiler.DmnImportRequest;
import io.finmsg.dmn.compiler.DmnModelResolver;
import io.finmsg.dmn.compiler.DmnResolutionResult;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Policy boundary resolver that resolves location-addressable DMN import requests
 * against an in-memory/streamed DmnStreamBundle.
 */
public final class DmnStreamResolver implements DmnModelResolver {

  private final DmnStreamBundle bundle;

  public DmnStreamResolver(DmnStreamBundle bundle) {
    this.bundle = Objects.requireNonNull(bundle, "bundle");
  }

  @Override
  public DmnResolutionResult resolve(DmnImportRequest request) {
    Objects.requireNonNull(request, "request");
    Optional<DmnSourceId> locationId = request.resolvedLocation();
    if (locationId.isEmpty()) {
      return DmnResolutionResult.missing(request);
    }

    String rawLocation = request.location();
    Optional<DmnSource> exact = bundle.findSource(rawLocation);
    if (exact.isPresent()) {
      return DmnResolutionResult.resolved(request, exact.get());
    }

    List<DmnSource> candidates = bundle.findMatchingSources(rawLocation);
    if (candidates.isEmpty()) {
      return DmnResolutionResult.missing(request);
    }
    if (candidates.size() == 1) {
      return DmnResolutionResult.resolved(request, candidates.getFirst());
    }
    return new DmnResolutionResult(request, candidates);
  }
}

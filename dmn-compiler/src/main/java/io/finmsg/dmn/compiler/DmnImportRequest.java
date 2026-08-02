package io.finmsg.dmn.compiler;

import java.util.Objects;
import java.util.Optional;

/** Import declaration together with the identity of the model that contains it. */
public record DmnImportRequest(
    DmnSourceId importer, String location, String namespace, String modelName) {

  public DmnImportRequest {
    Objects.requireNonNull(importer, "importer");
    location = normalized(location);
    namespace = normalized(namespace);
    modelName = normalized(modelName);
    if (location.isEmpty() && namespace.isEmpty() && modelName.isEmpty()) {
      throw new IllegalArgumentException(
          "An import request requires a location, namespace, or model name");
    }
  }

  public Optional<DmnSourceId> resolvedLocation() {
    return location.isEmpty() ? Optional.empty() : Optional.of(importer.resolve(location));
  }

  private static String normalized(String value) {
    return value == null ? "" : value.trim();
  }
}

package io.finmsg.dmn.compiler;

import java.net.URI;
import java.util.Objects;

/** Stable, resolver-independent identity of one DMN source. */
public record DmnSourceId(URI uri) implements Comparable<DmnSourceId> {

  public DmnSourceId {
    Objects.requireNonNull(uri, "uri");
    if (!uri.isAbsolute()) {
      throw new IllegalArgumentException("DMN source identity must be an absolute URI: " + uri);
    }
    uri = uri.normalize();
  }

  public static DmnSourceId of(String uri) {
    Objects.requireNonNull(uri, "uri");
    return new DmnSourceId(URI.create(uri));
  }

  public DmnSourceId resolve(String location) {
    Objects.requireNonNull(location, "location");
    if (location.isBlank()) {
      throw new IllegalArgumentException("Import location must not be blank");
    }
    return new DmnSourceId(uri.resolve(location));
  }

  @Override
  public int compareTo(DmnSourceId other) {
    return uri.toString().compareTo(other.uri.toString());
  }

  @Override
  public String toString() {
    return uri.toString();
  }
}

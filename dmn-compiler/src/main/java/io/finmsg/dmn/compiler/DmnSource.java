package io.finmsg.dmn.compiler;

import java.util.Arrays;
import java.util.Objects;

/** Immutable bytes and identity supplied to the compiler for one DMN model. */
public record DmnSource(DmnSourceId id, byte[] content) {

  public DmnSource {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(content, "content");
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof DmnSource source
        && id.equals(source.id) && Arrays.equals(content, source.content);
  }

  @Override
  public int hashCode() {
    return 31 * id.hashCode() + Arrays.hashCode(content);
  }
}

package io.finmsg.dmn.compiler;

import java.util.Objects;

/** Immutable options accepted by the public compiler facade. */
public record DmnCompilerOptions(DmnModelLoadOptions modelLoadOptions) {

  public DmnCompilerOptions {
    Objects.requireNonNull(modelLoadOptions, "modelLoadOptions");
  }

  public static DmnCompilerOptions defaults() {
    return new DmnCompilerOptions(DmnModelLoadOptions.defaults());
  }
}

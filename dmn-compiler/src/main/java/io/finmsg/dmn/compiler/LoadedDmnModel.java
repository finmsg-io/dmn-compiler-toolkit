package io.finmsg.dmn.compiler;

import io.finmsg.dmn.model.Definitions;
import java.util.Objects;

/** One immutable source and its parsed DMN definitions. */
public record LoadedDmnModel(DmnSource source, Definitions model) {

  public LoadedDmnModel {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(model, "model");
  }

  public DmnSourceId id() {
    return source.id();
  }
}

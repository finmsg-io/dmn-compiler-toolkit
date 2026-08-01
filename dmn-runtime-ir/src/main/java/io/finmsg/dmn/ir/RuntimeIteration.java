package io.finmsg.dmn.ir;

import java.util.Objects;
import java.util.Optional;

/** A sequential FEEL iteration binding, optionally representing a numeric interval. */
public record RuntimeIteration(
    int localSlot, RuntimeExpression source, Optional<RuntimeExpression> end) {
  public RuntimeIteration {
    if (localSlot < 0) {
      throw new IllegalArgumentException("localSlot must not be negative.");
    }
    Objects.requireNonNull(source, "source");
    end = Objects.requireNonNull(end, "end");
  }
}

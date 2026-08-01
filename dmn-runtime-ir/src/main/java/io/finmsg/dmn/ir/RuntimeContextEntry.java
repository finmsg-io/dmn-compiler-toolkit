package io.finmsg.dmn.ir;

import java.util.Objects;

/** A named, sequentially evaluated entry in a runtime context. */
public record RuntimeContextEntry(
    String name, int localSlot, RuntimeExpression expression) {
  public RuntimeContextEntry {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank.");
    }
    if (localSlot < 0) {
      throw new IllegalArgumentException("localSlot must not be negative.");
    }
    Objects.requireNonNull(expression, "expression");
  }
}

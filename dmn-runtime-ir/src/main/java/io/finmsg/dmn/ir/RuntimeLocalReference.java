package io.finmsg.dmn.ir;

import java.util.Objects;

/** Reference to a lexically scoped value produced inside an expression. */
public record RuntimeLocalReference(int localSlot, RuntimeType type) implements RuntimeExpression {
  public RuntimeLocalReference {
    if (localSlot < 0) {
      throw new IllegalArgumentException("localSlot must not be negative.");
    }
    Objects.requireNonNull(type, "type");
  }
}

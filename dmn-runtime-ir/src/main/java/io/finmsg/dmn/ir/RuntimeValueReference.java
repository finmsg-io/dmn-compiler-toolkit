package io.finmsg.dmn.ir;

import java.util.Objects;

/** Reads a value produced in another Runtime IR slot. */
public record RuntimeValueReference(int sourceSlot, RuntimeType type)
    implements RuntimeExpression {
  public RuntimeValueReference {
    if (sourceSlot < 0) {
      throw new IllegalArgumentException("sourceSlot must not be negative.");
    }
    Objects.requireNonNull(type, "type");
  }
}

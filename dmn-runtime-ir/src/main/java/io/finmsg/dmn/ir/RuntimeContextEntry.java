package io.finmsg.dmn.ir;

import java.util.Objects;

/** A sequentially evaluated context entry; an unnamed entry is the boxed-context result. */
public record RuntimeContextEntry(
    String name, int localSlot, RuntimeExpression expression) {
  public RuntimeContextEntry {
    Objects.requireNonNull(name, "name");
    if (name.isBlank() && localSlot != -1) {
      throw new IllegalArgumentException("Unnamed result entries must use localSlot -1.");
    }
    if (!name.isBlank() && localSlot < 0) {
      throw new IllegalArgumentException("Named entries require a non-negative localSlot.");
    }
    Objects.requireNonNull(expression, "expression");
  }
}

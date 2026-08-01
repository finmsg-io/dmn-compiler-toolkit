package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

/** A FEEL context whose entries are evaluated in declaration order. */
public record RuntimeContextExpression(
    List<RuntimeContextEntry> entries, RuntimeType type) implements RuntimeExpression {
  public RuntimeContextExpression {
    entries = List.copyOf(entries);
    Objects.requireNonNull(type, "type");
  }
}

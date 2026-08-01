package io.finmsg.dmn.ir;

import java.util.Objects;

/** Recursive descendant projection over contexts or context collections. */
public record RuntimeDescendantExpression(
    RuntimeExpression source, String member, int fieldIndex, RuntimeType type)
    implements RuntimeExpression {
  public RuntimeDescendantExpression {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(member, "member");
    if (member.isBlank()) {
      throw new IllegalArgumentException("member must not be blank.");
    }
    if (fieldIndex < -1) {
      throw new IllegalArgumentException("fieldIndex must be -1 or non-negative.");
    }
    Objects.requireNonNull(type, "type");
  }

  public RuntimeDescendantExpression(RuntimeExpression source, String member, RuntimeType type) {
    this(source, member, -1, type);
  }
}

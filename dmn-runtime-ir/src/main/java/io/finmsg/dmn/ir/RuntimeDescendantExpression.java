package io.finmsg.dmn.ir;

import java.util.Objects;

/** Recursive descendant projection over contexts or context collections. */
public record RuntimeDescendantExpression(
    RuntimeExpression source, String member, RuntimeType type) implements RuntimeExpression {
  public RuntimeDescendantExpression {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(member, "member");
    if (member.isBlank()) {
      throw new IllegalArgumentException("member must not be blank.");
    }
    Objects.requireNonNull(type, "type");
  }
}

package io.finmsg.dmn.ir;

import java.util.Objects;

/** Named property access or list projection over a runtime value. */
public record RuntimePathExpression(
    RuntimeExpression source, String member, RuntimeType type) implements RuntimeExpression {
  public RuntimePathExpression {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(member, "member");
    if (member.isBlank()) {
      throw new IllegalArgumentException("member must not be blank.");
    }
    Objects.requireNonNull(type, "type");
  }
}

package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeDecisionTableReference(int decisionSlot, RuntimeType type)
    implements RuntimeExpression {
  public RuntimeDecisionTableReference {
    if (decisionSlot < 0) {
      throw new IllegalArgumentException("decisionSlot must not be negative.");
    }
    Objects.requireNonNull(type, "type");
  }
}

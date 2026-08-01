package io.finmsg.dmn.ir;

import java.util.Objects;
import java.util.Optional;

public record RuntimeDecisionTableInput(
    RuntimeExpression expression, Optional<RuntimeUnaryTests> allowedValues, RuntimeType type) {
  public RuntimeDecisionTableInput {
    Objects.requireNonNull(expression, "expression");
    allowedValues = Objects.requireNonNull(allowedValues, "allowedValues");
    Objects.requireNonNull(type, "type");
  }
}

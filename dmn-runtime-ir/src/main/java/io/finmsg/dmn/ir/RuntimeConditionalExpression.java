package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeConditionalExpression(
    RuntimeExpression condition,
    RuntimeExpression thenExpression,
    RuntimeExpression elseExpression,
    RuntimeType type)
    implements RuntimeExpression {
  public RuntimeConditionalExpression {
    Objects.requireNonNull(condition, "condition");
    Objects.requireNonNull(thenExpression, "thenExpression");
    Objects.requireNonNull(elseExpression, "elseExpression");
    Objects.requireNonNull(type, "type");
  }
}

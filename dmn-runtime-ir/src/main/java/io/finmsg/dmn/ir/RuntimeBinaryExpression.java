package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeBinaryExpression(
    RuntimeBinaryOperator operator,
    RuntimeExpression left,
    RuntimeExpression right,
    RuntimeType type)
    implements RuntimeExpression {
  public RuntimeBinaryExpression {
    Objects.requireNonNull(operator, "operator");
    Objects.requireNonNull(left, "left");
    Objects.requireNonNull(right, "right");
    Objects.requireNonNull(type, "type");
  }
}

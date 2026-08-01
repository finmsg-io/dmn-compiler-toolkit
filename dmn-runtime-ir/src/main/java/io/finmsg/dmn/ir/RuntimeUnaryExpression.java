package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeUnaryExpression(
    RuntimeUnaryOperator operator, RuntimeExpression operand, RuntimeType type)
    implements RuntimeExpression {
  public RuntimeUnaryExpression {
    Objects.requireNonNull(operator, "operator");
    Objects.requireNonNull(operand, "operand");
    Objects.requireNonNull(type, "type");
  }
}

package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeFilterExpression(
    RuntimeExpression source, RuntimeExpression filter, RuntimeType type)
    implements RuntimeExpression {
  public RuntimeFilterExpression {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(filter, "filter");
    Objects.requireNonNull(type, "type");
  }
}

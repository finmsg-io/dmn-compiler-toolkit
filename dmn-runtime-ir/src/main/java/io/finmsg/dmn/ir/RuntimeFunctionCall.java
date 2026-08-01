package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

public record RuntimeFunctionCall(
    String function, List<RuntimeExpression> arguments, RuntimeType type)
    implements RuntimeExpression {
  public RuntimeFunctionCall {
    Objects.requireNonNull(function, "function");
    if (function.isBlank()) {
      throw new IllegalArgumentException("function must not be blank.");
    }
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(type, "type");
  }
}

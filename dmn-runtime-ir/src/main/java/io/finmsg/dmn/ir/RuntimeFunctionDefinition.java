package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeFunctionDefinition(
    List<RuntimeFunctionParameter> parameters,
    Optional<RuntimeExpression> body,
    boolean external,
    RuntimeType type) implements RuntimeExpression {
  public RuntimeFunctionDefinition {
    parameters = List.copyOf(parameters);
    body = Objects.requireNonNull(body, "body");
    if (!external && body.isEmpty()) {
      throw new IllegalArgumentException("A non-external function requires a body.");
    }
    Objects.requireNonNull(type, "type");
  }
}

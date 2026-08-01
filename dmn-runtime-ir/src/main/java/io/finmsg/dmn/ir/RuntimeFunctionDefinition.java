package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeFunctionDefinition(
    List<RuntimeFunctionParameter> parameters,
    Optional<RuntimeExpression> body,
    boolean external,
    int localSlotCount,
    RuntimeType type) implements RuntimeExpression {
  public RuntimeFunctionDefinition {
    parameters = List.copyOf(parameters);
    body = Objects.requireNonNull(body, "body");
    if (!external && body.isEmpty()) {
      throw new IllegalArgumentException("A non-external function requires a body.");
    }
    if (localSlotCount < parameters.size()) {
      throw new IllegalArgumentException(
          "localSlotCount must include every parameter slot.");
    }
    Objects.requireNonNull(type, "type");
  }

  public RuntimeFunctionDefinition(
      List<RuntimeFunctionParameter> parameters,
      Optional<RuntimeExpression> body,
      boolean external,
      RuntimeType type) {
    this(parameters, body, external, parameters.size(), type);
  }
}

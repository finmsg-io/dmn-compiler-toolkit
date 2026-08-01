package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeNamedArgument(String name, RuntimeExpression expression) {
  public RuntimeNamedArgument {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank.");
    }
    Objects.requireNonNull(expression, "expression");
  }
}

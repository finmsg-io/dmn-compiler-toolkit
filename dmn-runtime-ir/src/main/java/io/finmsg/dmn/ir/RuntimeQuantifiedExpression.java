package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

public record RuntimeQuantifiedExpression(
    RuntimeQuantifier quantifier,
    List<RuntimeQuantifiedBinding> bindings,
    RuntimeExpression satisfies,
    RuntimeType type) implements RuntimeExpression {
  public RuntimeQuantifiedExpression {
    Objects.requireNonNull(quantifier, "quantifier");
    bindings = List.copyOf(bindings);
    if (bindings.isEmpty()) {
      throw new IllegalArgumentException("bindings must not be empty.");
    }
    Objects.requireNonNull(satisfies, "satisfies");
    Objects.requireNonNull(type, "type");
  }
}

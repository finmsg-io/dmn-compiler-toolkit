package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeDecision(
    int id,
    int resultSlot,
    RuntimeType type,
    List<Integer> dependencies,
    Optional<RuntimeExpression> expression) {
  public RuntimeDecision {
    Objects.requireNonNull(type, "type");
    dependencies = List.copyOf(dependencies);
    expression = Objects.requireNonNull(expression, "expression");
  }

  public RuntimeDecision(
      int id, int resultSlot, RuntimeType type, List<Integer> dependencies) {
    this(id, resultSlot, type, dependencies, Optional.empty());
  }
}

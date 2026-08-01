package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeDecision(
    int id,
    int resultSlot,
    RuntimeType type,
    List<Integer> dependencies,
    Optional<RuntimeExpression> expression,
    Optional<RuntimeDecisionTable> decisionTable) {
  public RuntimeDecision {
    Objects.requireNonNull(type, "type");
    dependencies = List.copyOf(dependencies);
    expression = Objects.requireNonNull(expression, "expression");
    decisionTable = Objects.requireNonNull(decisionTable, "decisionTable");
    if (expression.isPresent() && decisionTable.isPresent()) {
      throw new IllegalArgumentException("Decision cannot have two runtime logic forms.");
    }
  }

  public RuntimeDecision(
      int id, int resultSlot, RuntimeType type, List<Integer> dependencies,
      Optional<RuntimeExpression> expression) {
    this(id, resultSlot, type, dependencies, expression, Optional.empty());
  }

  public RuntimeDecision(
      int id, int resultSlot, RuntimeType type, List<Integer> dependencies) {
    this(id, resultSlot, type, dependencies, Optional.empty(), Optional.empty());
  }
}

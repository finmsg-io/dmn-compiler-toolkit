package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeDecisionTable(
    RuntimeHitPolicy hitPolicy,
    Optional<RuntimeAggregation> aggregation,
    List<RuntimeDecisionTableInput> inputs,
    List<RuntimeDecisionTableOutput> outputs,
    List<RuntimeDecisionTableRule> rules,
    int annotationColumnCount) {
  public RuntimeDecisionTable {
    Objects.requireNonNull(hitPolicy, "hitPolicy");
    aggregation = Objects.requireNonNull(aggregation, "aggregation");
    inputs = List.copyOf(inputs);
    outputs = List.copyOf(outputs);
    rules = List.copyOf(rules);
    if (annotationColumnCount < 0) {
      throw new IllegalArgumentException("annotationColumnCount must not be negative.");
    }
  }
}

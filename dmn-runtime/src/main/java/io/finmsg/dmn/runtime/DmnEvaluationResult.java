package io.finmsg.dmn.runtime;

import io.finmsg.dmn.ir.RuntimeDecision;
import io.finmsg.dmn.ir.RuntimeModel;
import java.util.List;

public record DmnEvaluationResult(RuntimeModel model, List<Object> slotValues) {
  public DmnEvaluationResult { slotValues = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(slotValues)); }
  public Object value(int slot) { return slotValues.get(slot); }
  public Object decisionValue(int decisionId) {
    RuntimeDecision decision = model.decisions().stream().filter(it -> it.id() == decisionId)
        .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown decision ID " + decisionId));
    return value(decision.resultSlot());
  }
}

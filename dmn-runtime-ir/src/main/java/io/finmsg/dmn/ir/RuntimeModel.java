package io.finmsg.dmn.ir;

import java.util.List;

public record RuntimeModel(
    List<RuntimeInput> inputs,
    List<RuntimeDecision> decisions,
    List<RuntimeBkm> businessKnowledgeModels,
    List<Integer> evaluationOrder,
    int valueSlotCount) {
  public RuntimeModel {
    inputs = List.copyOf(inputs);
    decisions = List.copyOf(decisions);
    businessKnowledgeModels = List.copyOf(businessKnowledgeModels);
    evaluationOrder = List.copyOf(evaluationOrder);
  }
}

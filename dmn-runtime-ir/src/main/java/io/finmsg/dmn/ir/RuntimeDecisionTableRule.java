package io.finmsg.dmn.ir;

import java.util.List;

public record RuntimeDecisionTableRule(
    int ruleIndex,
    List<RuntimeUnaryTests> inputEntries,
    List<RuntimeExpression> outputEntries,
    List<String> annotations) {
  public RuntimeDecisionTableRule {
    if (ruleIndex < 0) {
      throw new IllegalArgumentException("ruleIndex must not be negative.");
    }
    inputEntries = List.copyOf(inputEntries);
    outputEntries = List.copyOf(outputEntries);
    annotations = List.copyOf(annotations);
  }
}

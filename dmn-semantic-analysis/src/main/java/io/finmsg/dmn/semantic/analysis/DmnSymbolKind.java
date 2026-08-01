package io.finmsg.dmn.semantic.analysis;

/** Kind of declaration targeted by a resolved semantic binding. */
public enum DmnSymbolKind {
  ITEM_DEFINITION,
  INPUT_DATA,
  DECISION,
  BUSINESS_KNOWLEDGE_MODEL,
  KNOWLEDGE_SOURCE,
  DECISION_SERVICE,
  PARAMETER,
  LOCAL_VARIABLE
}

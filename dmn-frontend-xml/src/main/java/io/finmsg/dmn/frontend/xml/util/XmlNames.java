package io.finmsg.dmn.frontend.xml.util;

public final class XmlNames {

  private XmlNames() {}

  // root

  public static final String DEFINITIONS = "definitions";

  // DRG

  public static final String DECISION = "decision";
  public static final String INPUT_DATA = "inputData";
  public static final String BUSINESS_KNOWLEDGE_MODEL = "businessKnowledgeModel";
  public static final String KNOWLEDGE_SOURCE = "knowledgeSource";
  public static final String DECISION_SERVICE = "decisionService";

  // datatype

  public static final String ITEM_DEFINITION = "itemDefinition";

  // expressions

  public static final String LITERAL_EXPRESSION = "literalExpression";
  public static final String CONTEXT = "context";
  public static final String DECISION_TABLE = "decisionTable";
  public static final String FUNCTION_DEFINITION = "functionDefinition";
  public static final String INVOCATION = "invocation";
  public static final String LIST = "list";
  public static final String RELATION = "relation";

  // artifacts

  public static final String ASSOCIATION = "association";
  public static final String TEXT_ANNOTATION = "textAnnotation";
  public static final String GROUP = "group";
}

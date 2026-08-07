package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.BusinessKnowledgeModel;

public final class BusinessKnowledgeModelReader {

  private final NodeReader nodeReader = new NodeReader();
  private final VariableReader variableReader = new VariableReader();
  private final KnowledgeRequirementReader knowledgeRequirementReader =
      new KnowledgeRequirementReader();
  private final AuthorityRequirementReader authorityRequirementReader =
      new AuthorityRequirementReader();
  private final FeelReader feelReader = new FeelReader();
  private final FunctionDefinitionReader functionDefinitionReader = new FunctionDefinitionReader();

  public BusinessKnowledgeModel read(XmlCursor cursor) {

    BusinessKnowledgeModel.Builder builder = BusinessKnowledgeModel.newBuilder();
    builder.setNode(nodeReader.read(cursor));

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {

          case "variable" -> builder.setVariable(variableReader.read(cursor));

          case "encapsulatedLogic" ->
              builder.setFunction(functionDefinitionReader.read(cursor));

          case "knowledgeRequirement" ->
              builder.addKnowledgeRequirements(knowledgeRequirementReader.read(cursor));

          case "authorityRequirement" ->
              builder.addAuthorityRequirements(authorityRequirementReader.read(cursor));

          case "documentation", "extensionElements" -> { }

          default -> UnsupportedContent.rejectDmnChild(cursor, "businessKnowledgeModel");
        }

      } while (cursor.nextSibling());

      cursor.parent();
    }

    return builder.build();
  }
}

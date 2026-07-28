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
  private final FeelSourceReader feelSourceReader = new FeelSourceReader();
  private final FunctionDefinitionReader functionDefinitionReader = new FunctionDefinitionReader();

  public BusinessKnowledgeModel read(XmlCursor cursor) {

    BusinessKnowledgeModel.Builder builder = BusinessKnowledgeModel.newBuilder();
    builder.setNode(nodeReader.read(cursor));

    if (cursor.firstChild()) {
      do {
        switch (cursor.localName()) {

          case "variable" -> builder.setVariable(variableReader.read(cursor));

          case "encapsulatedLogic" -> {
            if (cursor.firstChild()) {
              builder.setFunction(functionDefinitionReader.read(cursor));
              cursor.parent();
            }
          }

          case "knowledgeRequirement" ->
              builder.addKnowledgeRequirements(knowledgeRequirementReader.read(cursor));

          case "authorityRequirement" ->
              builder.addAuthorityRequirements(authorityRequirementReader.read(cursor));

          case "extensionElements" -> {
            // later
          }

          default -> {
            // ignore unknown children
          }
        }

      } while (cursor.nextSibling());

      cursor.parent();
    }

    return builder.build();
  }
}

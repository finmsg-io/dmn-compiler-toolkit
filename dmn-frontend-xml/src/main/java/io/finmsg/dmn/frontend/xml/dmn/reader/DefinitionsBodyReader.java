package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.frontend.xml.dmn.DmnXmlContext;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;

public final class DefinitionsBodyReader {

  private final DmnXmlContext context;

  private final ImportReader importReader = new ImportReader();
  private final ItemDefinitionReader itemDefinitionReader = new ItemDefinitionReader();
  private final DecisionReader decisionReader = new DecisionReader();
  private final InputDataReader inputDataReader = new InputDataReader();
  private final BusinessKnowledgeModelReader businessKnowledgeModelReader =
      new BusinessKnowledgeModelReader();
  private final KnowledgeSourceReader knowledgeSourceReader = new KnowledgeSourceReader();
  private final DecisionServiceReader decisionServiceReader = new DecisionServiceReader();

  public DefinitionsBodyReader(DmnXmlContext context) {
    this.context = context;
  }

  public void read(XmlCursor cursor, Definitions.Builder definitions) {

    if (!cursor.firstChild()) {
      return;
    }

    do {
      switch (cursor.localName()) {
        case "import" -> definitions.addImports(importReader.read(cursor));

        case "itemDefinition" -> definitions.addItemDefinitions(itemDefinitionReader.read(cursor));

        case "decision" ->
            definitions.addDrgElements(
                DrgElement.newBuilder().setDecision(decisionReader.read(cursor)).build());

        case "inputData" ->
            definitions.addDrgElements(
                DrgElement.newBuilder().setInputData(inputDataReader.read(cursor)).build());

        case "businessKnowledgeModel" ->
            definitions.addDrgElements(
                DrgElement.newBuilder()
                    .setBusinessKnowledgeModel(businessKnowledgeModelReader.read(cursor))
                    .build());

        case "knowledgeSource" ->
            definitions.addDrgElements(
                DrgElement.newBuilder()
                    .setKnowledgeSource(knowledgeSourceReader.read(cursor))
                    .build());

        case "decisionService" ->
            definitions.addDrgElements(
                DrgElement.newBuilder()
                    .setDecisionService(decisionServiceReader.read(cursor))
                    .build());

        default -> {
          //
          // ignored for now
          //
        }
      }
    } while (cursor.nextSibling());
    cursor.parent();
  }
}

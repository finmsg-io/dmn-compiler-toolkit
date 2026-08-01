package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.FunctionDefinitionText;

public final class FunctionDefinitionTextReader {

  private final ReaderRegistry readers;
  private final VariableReader variableReader = new VariableReader();

  FunctionDefinitionTextReader(ReaderRegistry readers) {
    this.readers = readers;
  }

  public FunctionDefinitionText read(XmlCursor cursor) {

    FunctionDefinitionText.Builder builder = FunctionDefinitionText.newBuilder();

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "formalParameter" -> builder.addParameters(variableReader.read(cursor));
          case "literalExpression", "context", "relation", "list", "functionDefinition" ->
              builder.setBody(readers.expressionNodeReader().readText(cursor));
          case "extensionElements" -> { }
          default -> UnsupportedContent.rejectDmnChild(cursor, "boxed functionDefinition");
        }
      } while (cursor.nextSibling());
      cursor.parent();
    }

    return builder.build();
  }
}

package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.FunctionDefinitionSource;

public final class FunctionDefinitionSourceReader {

  private final VariableReader variableReader = new VariableReader();

  private final ExpressionSourceReader expressionSourceReader = new ExpressionSourceReader();

  public FunctionDefinitionSource read(XmlCursor cursor) {

    FunctionDefinitionSource.Builder builder = FunctionDefinitionSource.newBuilder();

    if (cursor.firstChild()) {
      do {

        switch (cursor.localName()) {

          //
          // Formal parameters
          //
          case "formalParameter" -> builder.addParameters(variableReader.read(cursor));

          //
          // Function body
          //
          case "literalExpression", "context", "relation", "list", "functionDefinition" ->
              builder.setBody(expressionSourceReader.read(cursor));

          //
          // Extension points
          //
          case "extensionElements" -> {}

          default -> {
            // ignore unknown elements
          }
        }

      } while (cursor.nextSibling());

      cursor.parent();
    }

    return builder.build();
  }
}

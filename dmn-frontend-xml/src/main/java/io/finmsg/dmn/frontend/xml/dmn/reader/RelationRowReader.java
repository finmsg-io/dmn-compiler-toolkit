package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.RelationRow;

public final class RelationRowReader {

  private final ExpressionSourceReader expressionSourceReader = new ExpressionSourceReader();

  public RelationRow read(XmlCursor cursor) {

    RelationRow.Builder builder = RelationRow.newBuilder();

    if (cursor.firstChild()) {
      do {

        switch (cursor.localName()) {
          case "literalExpression", "context", "relation", "list", "functionDefinition" ->
              builder.addExpressions(expressionSourceReader.read(cursor));

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

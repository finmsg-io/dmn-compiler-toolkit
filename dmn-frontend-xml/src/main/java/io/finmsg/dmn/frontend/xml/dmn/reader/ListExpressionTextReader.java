package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ListExpressionText;

public final class ListExpressionTextReader {

  private final ReaderRegistry readers;

  ListExpressionTextReader(ReaderRegistry readers) {
    this.readers = readers;
  }

  public ListExpressionText read(XmlCursor cursor) {

    ListExpressionText.Builder builder = ListExpressionText.newBuilder();

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "literalExpression", "context", "relation", "list", "functionDefinition" ->
              builder.addElements(readers.expressionNodeReader().readText(cursor));
          case "extensionElements" -> { }
          default -> { }
        }
      } while (cursor.nextSibling());
      cursor.parent();
    }

    return builder.build();
  }
}

package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ContextEntrySource;

public final class ContextEntrySourceReader {

  public ContextEntrySource read(XmlCursor cursor) {

    ContextEntrySource.Builder builder = ContextEntrySource.newBuilder();

    if (cursor.firstChild()) {

      do {

        switch (cursor.localName()) {
          case "variable" -> builder.setVariable(new InformationItemReader().read(cursor));

          case "literalExpression", "context", "relation", "list", "functionDefinition" ->
              builder.setExpression(new ExpressionSourceReader().read(cursor));

          default -> {
            // ignore
          }
        }

      } while (cursor.nextSibling());
      cursor.parent();
    }
    return builder.build();
  }
}

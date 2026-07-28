package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.RelationColumn;

public final class RelationColumnReader {

  private final VariableReader variableReader = new VariableReader();

  public RelationColumn read(XmlCursor cursor) {

    RelationColumn.Builder builder = RelationColumn.newBuilder();

    if (cursor.firstChild()) {
      do {

        switch (cursor.localName()) {
          case "variable" -> builder.setVariable(variableReader.read(cursor));

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

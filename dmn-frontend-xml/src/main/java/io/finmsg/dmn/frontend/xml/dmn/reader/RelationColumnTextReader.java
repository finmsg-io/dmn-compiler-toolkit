package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.RelationColumnText;

public final class RelationColumnTextReader {

  private final VariableReader variableReader = new VariableReader();

  public RelationColumnText read(XmlCursor cursor) {

    RelationColumnText.Builder builder = RelationColumnText.newBuilder();

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "variable" -> builder.setVariable(variableReader.read(cursor));
          case "extensionElements" -> { }
          default -> UnsupportedContent.rejectDmnChild(cursor, "relation column");
        }
      } while (cursor.nextSibling());
      cursor.parent();
    }

    return builder.build();
  }
}

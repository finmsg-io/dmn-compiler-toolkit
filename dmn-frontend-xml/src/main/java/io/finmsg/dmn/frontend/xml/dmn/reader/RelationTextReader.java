package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.RelationText;

public final class RelationTextReader {

  private final ReaderRegistry readers;
  private final RelationColumnTextReader columnReader = new RelationColumnTextReader();

  RelationTextReader(ReaderRegistry readers) {
    this.readers = readers;
  }

  public RelationText read(XmlCursor cursor) {

    RelationText.Builder builder = RelationText.newBuilder();

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "column" -> builder.addColumns(columnReader.read(cursor));
          case "row" -> builder.addRows(readers.relationRowTextReader().read(cursor));
          case "extensionElements" -> { }
          default -> UnsupportedContent.rejectDmnChild(cursor, "relation");
        }
      } while (cursor.nextSibling());
      cursor.parent();
    }

    return builder.build();
  }
}

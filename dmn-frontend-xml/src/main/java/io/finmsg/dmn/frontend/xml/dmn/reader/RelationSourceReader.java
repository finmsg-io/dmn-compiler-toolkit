package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.RelationSource;

public final class RelationSourceReader {

  private final RelationColumnReader columnReader = new RelationColumnReader();

  private final RelationRowReader rowReader = new RelationRowReader();

  public RelationSource read(XmlCursor cursor) {

    RelationSource.Builder builder = RelationSource.newBuilder();

    if (cursor.firstChild()) {
      do {

        switch (cursor.localName()) {
          case "column" -> builder.addColumns(columnReader.read(cursor));

          case "row" -> builder.addRows(rowReader.read(cursor));

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

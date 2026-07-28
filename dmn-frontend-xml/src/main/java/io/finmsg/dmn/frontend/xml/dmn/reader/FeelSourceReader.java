package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.FeelSource;

public final class FeelSourceReader {

  public FeelSource read(XmlCursor cursor) {

    FeelSource.Builder builder = FeelSource.newBuilder();

    if (cursor.firstChild("text")) {

      if (cursor.hasText()) {
        builder.setText(cursor.text().trim());
      }
      cursor.parent();
    }
    return builder.build();
  }
}

package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ContextSource;

public final class ContextSourceReader {

  private final ContextEntrySourceReader entryReader = new ContextEntrySourceReader();

  public ContextSource read(XmlCursor cursor) {

    ContextSource.Builder builder = ContextSource.newBuilder();

    if (cursor.firstChild()) {
      do {
        if ("contextEntry".equals(cursor.localName())) {
          builder.addEntries(entryReader.read(cursor));
        }
      } while (cursor.nextSibling());
      cursor.parent();
    }
    return builder.build();
  }
}

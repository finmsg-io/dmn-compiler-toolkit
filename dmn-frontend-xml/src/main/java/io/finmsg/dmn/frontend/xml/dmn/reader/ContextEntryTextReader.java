package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ContextEntryText;

public final class ContextEntryTextReader {

  private final ReaderRegistry readers;
  private final InformationItemReader informationItemReader = new InformationItemReader();

  ContextEntryTextReader(ReaderRegistry readers) {
    this.readers = readers;
  }

  public ContextEntryText read(XmlCursor cursor) {

    ContextEntryText.Builder builder = ContextEntryText.newBuilder();

    if (cursor.firstChild()) {
      do {
        switch (cursor.localName()) {
          case "variable" -> builder.setVariable(informationItemReader.read(cursor));
          case "literalExpression", "context", "relation", "list", "functionDefinition" ->
              builder.setExpression(readers.expressionNodeReader().readText(cursor));
          default -> { }
        }
      } while (cursor.nextSibling());
      cursor.parent();
    }

    return builder.build();
  }
}

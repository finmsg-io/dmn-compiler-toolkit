package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.FeelText;

public final class FeelReader {

  public Feel read(XmlCursor cursor) {
    return Feel.newBuilder()
        .setText(readText(cursor))
        .build();
  }

  public FeelText readText(XmlCursor cursor) {

    FeelText.Builder builder = FeelText.newBuilder();

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "text" -> {
            if (cursor.hasText()) {
              builder.setText(cursor.text().trim());
            }
          }
          case "expression", "literalExpression", "context", "functionDefinition", "extensionElements", "documentation" -> {
            FeelText inner = readText(cursor);
            if (!inner.getText().isBlank() && builder.getText().isBlank()) {
              builder.setText(inner.getText());
            }
          }
          default -> UnsupportedContent.rejectDmnChild(cursor, "FEEL expression");
        }
      } while (cursor.nextSibling());
      cursor.parent();
    }

    return builder.build();
  }
}

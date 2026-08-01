package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.Feel;

final class FeelWriter {

  void writeLiteralExpression(XmlEmitter xml, Feel value) {
    if (!value.hasText()) {
      throw new XmlWriteException("Parsed FEEL cannot be written without preserved source text.");
    }
    xml.startElement("literalExpression");
    xml.startElement("text");
    xml.text(value.getText().getText());
    xml.endElement();
    xml.endElement();
  }
}

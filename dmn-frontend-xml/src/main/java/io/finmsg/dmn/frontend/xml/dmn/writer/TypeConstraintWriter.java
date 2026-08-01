package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.TypeConstraint;

final class TypeConstraintWriter {

  void write(XmlEmitter xml, TypeConstraint value) {
    if (!value.hasText()) {
      throw new XmlWriteException(
          "Parsed item-definition constraints cannot be written without source text.");
    }
    InputClauseWriter.writeFeel(xml, "allowedValues", value.getText().getText(), "");
  }
}

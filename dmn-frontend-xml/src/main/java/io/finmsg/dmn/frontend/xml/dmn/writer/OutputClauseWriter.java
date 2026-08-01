package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.OutputClause;

final class OutputClauseWriter {

  private final NodeWriter nodeWriter = new NodeWriter();
  private final TypeReferenceWriter typeWriter = new TypeReferenceWriter();

  void write(XmlEmitter xml, OutputClause value) {
    xml.startElement("output");
    nodeWriter.writeAttributes(xml, value.getNode());
    if (value.hasType()) {
      xml.attribute("typeRef", typeWriter.write(value.getType()));
    }
    if (value.hasOutputValues()) {
      InputClauseWriter.writeFeel(
          xml, "outputValues", value.getOutputValues().getText().getText(), "");
    }
    if (value.hasDefaultOutputEntry()) {
      if (!value.getDefaultOutputEntry().hasText()
          || !value.getDefaultOutputEntry().getText().hasFeel()) {
        throw new XmlWriteException("Default output entry has no writable FEEL source text.");
      }
      InputClauseWriter.writeFeel(
          xml,
          "defaultOutputEntry",
          value.getDefaultOutputEntry().getText().getFeel().getText(),
          "");
    }
    xml.endElement();
  }
}

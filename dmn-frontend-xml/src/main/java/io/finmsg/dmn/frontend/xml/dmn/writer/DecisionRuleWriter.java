package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.DecisionRule;
import io.finmsg.dmn.model.UnaryTest;

final class DecisionRuleWriter {

  private final NodeWriter nodeWriter = new NodeWriter();

  void write(XmlEmitter xml, DecisionRule value) {
    xml.startElement("rule");
    nodeWriter.writeAttributes(xml, value.getNode());
    value.getInputEntriesList().forEach(entry -> writeInputEntry(xml, entry));
    value.getOutputEntriesList().forEach(
        entry -> InputClauseWriter.writeFeel(xml, "outputEntry", entry.getText().getText(), ""));
    value.getAnnotationEntriesList().forEach(annotation -> {
      xml.startElement("annotationEntry");
      xml.startElement("text");
      xml.text(annotation.getText());
      xml.endElement();
      xml.endElement();
    });
    xml.endElement();
  }

  private static void writeInputEntry(XmlEmitter xml, UnaryTest value) {
    if (!value.hasText()) {
      throw new XmlWriteException("Parsed unary tests cannot be written without source text.");
    }
    InputClauseWriter.writeFeel(xml, "inputEntry", value.getText().getText(), "");
  }
}

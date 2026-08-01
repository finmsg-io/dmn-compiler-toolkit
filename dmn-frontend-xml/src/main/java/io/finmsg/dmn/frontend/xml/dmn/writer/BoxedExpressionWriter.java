package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.BoxedExpression;
import io.finmsg.dmn.model.BoxedExpressionText;
import io.finmsg.dmn.model.ContextEntryText;
import io.finmsg.dmn.model.ExpressionText;

final class BoxedExpressionWriter {

  private final InformationItemWriter informationItemWriter = new InformationItemWriter();
  private final FeelWriter feelWriter = new FeelWriter();

  void write(XmlEmitter xml, BoxedExpression value) {
    if (!value.hasText()) {
      throw new XmlWriteException("Parsed boxed expression has no writable source model.");
    }
    writeBoxed(xml, value.getText());
  }

  private void writeExpression(XmlEmitter xml, ExpressionText value) {
    if (value.hasFeel()) {
      feelWriter.writeLiteralExpression(
          xml, io.finmsg.dmn.model.Feel.newBuilder().setText(value.getFeel()).build());
    } else if (value.hasBoxed()) {
      writeBoxed(xml, value.getBoxed());
    } else {
      throw new XmlWriteException("Empty expression cannot be written.");
    }
  }

  private void writeBoxed(XmlEmitter xml, BoxedExpressionText value) {
    if (value.hasContext()) {
      xml.startElement("context");
      value.getContext().getEntriesList().forEach(entry -> writeContextEntry(xml, entry));
      xml.endElement();
      return;
    }
    if (value.hasList()) {
      xml.startElement("list");
      value.getList().getElementsList().forEach(element -> writeExpression(xml, element));
      xml.endElement();
      return;
    }
    if (value.hasRelation()) {
      xml.startElement("relation");
      value.getRelation().getColumnsList().forEach(column -> {
        xml.startElement("column");
        if (column.hasVariable()) {
          informationItemWriter.write(xml, "variable", column.getVariable());
        }
        xml.endElement();
      });
      value.getRelation().getRowsList().forEach(row -> {
        xml.startElement("row");
        row.getExpressionsList().forEach(expression -> writeExpression(xml, expression));
        xml.endElement();
      });
      xml.endElement();
      return;
    }
    if (value.hasFunctionDefinition()) {
      xml.startElement("functionDefinition");
      value.getFunctionDefinition().getParametersList()
          .forEach(parameter -> informationItemWriter.write(xml, "formalParameter", parameter));
      if (value.getFunctionDefinition().hasBody()) {
        writeExpression(xml, value.getFunctionDefinition().getBody());
      }
      xml.endElement();
      return;
    }
    throw new XmlWriteException("Empty boxed expression cannot be written.");
  }

  private void writeContextEntry(XmlEmitter xml, ContextEntryText value) {
    xml.startElement("contextEntry");
    if (value.hasVariable()) {
      informationItemWriter.write(xml, "variable", value.getVariable());
    }
    if (value.hasExpression()) {
      writeExpression(xml, value.getExpression());
    }
    xml.endElement();
  }
}

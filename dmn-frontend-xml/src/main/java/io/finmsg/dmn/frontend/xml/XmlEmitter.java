package io.finmsg.dmn.frontend.xml;

import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import java.io.OutputStream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public final class XmlEmitter implements AutoCloseable {

  private final XMLStreamWriter writer;

  public XmlEmitter(OutputStream output) {
    try {
      writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8");
    } catch (XMLStreamException e) {
      throw new XmlWriteException("Could not create XML writer.", e);
    }
  }

  public void startDocument() {
    execute(() -> writer.writeStartDocument("UTF-8", "1.0"));
  }

  public void startElement(String name) {
    execute(() -> writer.writeStartElement(name));
  }

  public void defaultNamespace(String namespace) {
    execute(() -> writer.writeDefaultNamespace(namespace));
  }

  public void attribute(String name, String value) {
    if (value != null && !value.isEmpty()) {
      execute(() -> writer.writeAttribute(name, value));
    }
  }

  public void text(String value) {
    if (value != null) {
      execute(() -> writer.writeCharacters(value));
    }
  }

  public void endElement() {
    execute(writer::writeEndElement);
  }

  public void endDocument() {
    execute(writer::writeEndDocument);
  }

  public void flush() {
    execute(writer::flush);
  }

  @Override
  public void close() {
    execute(writer::close);
  }

  private void execute(XmlOperation operation) {
    try {
      operation.run();
    } catch (XMLStreamException e) {
      throw new XmlWriteException("Could not write DMN XML.", e);
    }
  }

  @FunctionalInterface
  private interface XmlOperation {
    void run() throws XMLStreamException;
  }
}

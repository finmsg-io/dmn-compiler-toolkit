package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.model.InformationItem;

public final class InformationItemWriter {

  private final NodeWriter nodeWriter = new NodeWriter();
  private final TypeReferenceWriter typeReferenceWriter = new TypeReferenceWriter();

  public void write(XmlEmitter xml, String elementName, InformationItem value) {
    xml.startElement(elementName);
    nodeWriter.writeAttributes(xml, value.getNode());
    if (value.hasType()) {
      xml.attribute("typeRef", typeReferenceWriter.write(value.getType()));
    }
    xml.endElement();
  }
}

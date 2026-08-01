package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.ItemDefinition;

public final class ItemDefinitionWriter implements XmlWriter<ItemDefinition> {

  private final NodeWriter nodeWriter = new NodeWriter();
  private final TypeReferenceWriter typeReferenceWriter = new TypeReferenceWriter();

  @Override
  public void write(XmlEmitter xml, ItemDefinition value) {
    xml.startElement("itemDefinition");
    nodeWriter.writeAttributes(xml, value.getNode());
    if (value.hasType()) {
      xml.attribute("typeRef", typeReferenceWriter.write(value.getType()));
    }
    if (value.getIsCollection()) {
      xml.attribute("isCollection", "true");
    }
    nodeWriter.writeChildren(xml, value.getNode());
    xml.endElement();
  }
}

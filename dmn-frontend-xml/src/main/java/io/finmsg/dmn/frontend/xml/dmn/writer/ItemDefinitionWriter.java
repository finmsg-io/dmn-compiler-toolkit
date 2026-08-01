package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.ItemComponent;

public final class ItemDefinitionWriter implements XmlWriter<ItemDefinition> {

  private final NodeWriter nodeWriter = new NodeWriter();
  private final TypeReferenceWriter typeReferenceWriter = new TypeReferenceWriter();
  private final TypeConstraintWriter typeConstraintWriter = new TypeConstraintWriter();

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
    if (value.hasConstraint()) {
      typeConstraintWriter.write(xml, value.getConstraint());
    }
    value.getComponentsList().forEach(component -> writeComponent(xml, component));
    xml.endElement();
  }

  private void writeComponent(XmlEmitter xml, ItemComponent value) {
    xml.startElement("itemComponent");
    nodeWriter.writeAttributes(xml, value.getNode());
    if (value.getIsCollection()) {
      xml.attribute("isCollection", "true");
    }
    nodeWriter.writeChildren(xml, value.getNode());
    if (value.hasType()) {
      xml.startElement("typeRef");
      xml.text(typeReferenceWriter.write(value.getType()));
      xml.endElement();
    }
    if (value.hasConstraint()) {
      typeConstraintWriter.write(xml, value.getConstraint());
    }
    xml.endElement();
  }
}

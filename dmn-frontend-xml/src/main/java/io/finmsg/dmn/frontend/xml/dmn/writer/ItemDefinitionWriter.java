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
		write(xml, value, false);
	}

	void write(XmlEmitter xml, ItemDefinition value, boolean dmn16) {
		xml.startElement("itemDefinition");
		nodeWriter.writeAttributes(xml, value.getNode());
		if (value.hasType() && !dmn16)
			xml.attribute("typeRef", typeReferenceWriter.write(xml, value.getType()));
		if (value.getIsCollection()) {
			xml.attribute("isCollection", "true");
		}
		nodeWriter.writeChildren(xml, value.getNode());
		if (value.hasType() && dmn16)
			writeType(xml, value.getType());
		if (value.hasConstraint()) {
			typeConstraintWriter.write(xml, value.getConstraint(), dmn16);
		}
		value.getComponentsList().forEach(component -> writeComponent(xml, component, dmn16));
		xml.endElement();
	}

	private void writeComponent(XmlEmitter xml, ItemComponent value, boolean dmn16) {
		xml.startElement("itemComponent");
		nodeWriter.writeAttributes(xml, value.getNode());
		if (value.getIsCollection()) {
			xml.attribute("isCollection", "true");
		}
		nodeWriter.writeChildren(xml, value.getNode());
		if (value.hasType())
			writeType(xml, value.getType());
		if (value.hasConstraint()) {
			typeConstraintWriter.write(xml, value.getConstraint(), dmn16);
		}
		xml.endElement();
	}
	private void writeType(XmlEmitter xml, io.finmsg.dmn.model.TypeReference type) {
		xml.startElement("typeRef");
		xml.text(typeReferenceWriter.write(xml, type));
		xml.endElement();
	}

}

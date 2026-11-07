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

	void write(XmlEmitter xml, ItemDefinition value, boolean typeConstraint) {
		xml.startElement("itemDefinition");
		nodeWriter.writeAttributes(xml, value.getNode());
		if (value.hasType()) {
			xml.attribute("typeRef", typeReferenceWriter.write(xml, value.getType()));
		}
		if (value.getIsCollection()) {
			xml.attribute("isCollection", "true");
		}
		nodeWriter.writeChildren(xml, value.getNode());
		if (value.hasConstraint()) {
			typeConstraintWriter.write(xml, value.getConstraint(), typeConstraint);
		}
		value.getComponentsList().forEach(component -> writeComponent(xml, component, typeConstraint));
		xml.endElement();
	}

	private void writeComponent(XmlEmitter xml, ItemComponent value, boolean typeConstraint) {
		xml.startElement("itemComponent");
		nodeWriter.writeAttributes(xml, value.getNode());
		if (value.getIsCollection()) {
			xml.attribute("isCollection", "true");
		}
		nodeWriter.writeChildren(xml, value.getNode());
		if (value.hasType()) {
			xml.startElement("typeRef");
			xml.text(typeReferenceWriter.write(xml, value.getType()));
			xml.endElement();
		}
		if (value.hasConstraint()) {
			typeConstraintWriter.write(xml, value.getConstraint(), typeConstraint);
		}
		xml.endElement();
	}
}

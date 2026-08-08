package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.model.Node;

public final class NodeWriter {

	public void writeAttributes(XmlEmitter xml, Node node) {
		xml.attribute("id", node.getId());
		xml.attribute("name", node.getName());
		xml.attribute("label", node.getLabel());
	}

	public void writeChildren(XmlEmitter xml, Node node) {
		if (node.hasDocumentation()) {
			xml.startElement("documentation");
			xml.text(node.getDocumentation().getText());
			xml.endElement();
		}
		if (node.hasExtensionElements()) {
			xml.startElement("extensionElements");
			node.getExtensionElements().getElementList().forEach(element -> {
				xml.startElement(element.getNamespace(), element.getName());
				element.getAttributeList().forEach(attribute -> xml.attribute(attribute.getNamespace(),
						attribute.getName(), attribute.getValue()));
				xml.text(element.getValue());
				xml.endElement();
			});
			xml.endElement();
		}
	}
}

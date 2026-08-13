package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.KnowledgeSource;

public final class KnowledgeSourceWriter implements XmlWriter<KnowledgeSource> {

	private final NodeWriter nodeWriter = new NodeWriter();

	@Override
	public void write(XmlEmitter xml, KnowledgeSource value) {
		xml.startElement("knowledgeSource");
		nodeWriter.writeAttributes(xml, value.getNode());
		nodeWriter.writeChildren(xml, value.getNode());
		if (!value.getAuthority().isEmpty()) {
			xml.startElement("owner");
			xml.attribute("href", value.getAuthority());
			xml.endElement();
		}
		if (!value.getLocationUri().isEmpty()) {
			xml.startElement("locationURI");
			xml.text(value.getLocationUri());
			xml.endElement();
		}
		xml.endElement();
	}
}

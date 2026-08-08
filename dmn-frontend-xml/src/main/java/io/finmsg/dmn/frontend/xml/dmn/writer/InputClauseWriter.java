package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.InputClause;

final class InputClauseWriter {

	private final NodeWriter nodeWriter = new NodeWriter();
	private final TypeReferenceWriter typeWriter = new TypeReferenceWriter();

	void write(XmlEmitter xml, InputClause value) {
		if (value.hasType() && !value.hasInputExpression()) {
			throw new XmlWriteException("An input-clause type requires an input expression.");
		}
		xml.startElement("input");
		nodeWriter.writeAttributes(xml, value.getNode());
		nodeWriter.writeChildren(xml, value.getNode());
		if (value.hasInputExpression()) {
			writeFeel(xml, "inputExpression", value.getInputExpression().getText().getText(),
					value.hasType() ? typeWriter.write(xml, value.getType()) : "");
		}
		if (value.hasInputValues()) {
			writeFeel(xml, "inputValues", value.getInputValues().getText().getText(), "");
		}
		xml.endElement();
	}

	static void writeFeel(XmlEmitter xml, String name, String source, String typeRef) {
		xml.startElement(name);
		xml.attribute("typeRef", typeRef);
		xml.startElement("text");
		xml.text(source);
		xml.endElement();
		xml.endElement();
	}
}

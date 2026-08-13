package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.model.Binding;
import io.finmsg.dmn.model.Invocation;

final class InvocationWriter {

	private final FeelWriter feelWriter = new FeelWriter();

	void write(XmlEmitter xml, Invocation value) {
		xml.startElement("invocation");
		if (value.hasExpression()) {
			feelWriter.writeLiteralExpression(xml, value.getExpression());
		}
		value.getBindingsList().forEach(binding -> writeBinding(xml, binding));
		xml.endElement();
	}

	private void writeBinding(XmlEmitter xml, Binding value) {
		xml.startElement("binding");
		xml.startElement("parameter");
		xml.attribute("name", value.getParameter());
		xml.endElement();
		if (value.hasExpression()) {
			feelWriter.writeLiteralExpression(xml, value.getExpression());
		}
		xml.endElement();
	}
}

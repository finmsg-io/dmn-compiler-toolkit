package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.DecisionService;

public final class DecisionServiceWriter implements XmlWriter<DecisionService> {

	private final NodeWriter nodeWriter = new NodeWriter();
	private final ElementReferenceWriter referenceWriter = new ElementReferenceWriter();

	@Override
	public void write(XmlEmitter xml, DecisionService value) {
		xml.startElement("decisionService");
		nodeWriter.writeAttributes(xml, value.getNode());
		nodeWriter.writeChildren(xml, value.getNode());
		value.getOutputDecisionsList().forEach(reference -> referenceWriter.write(xml, "outputDecision", reference));
		value.getEncapsulatedDecisionsList()
				.forEach(reference -> referenceWriter.write(xml, "encapsulatedDecision", reference));
		value.getInputDecisionsList().forEach(reference -> referenceWriter.write(xml, "inputDecision", reference));
		value.getInputDataList().forEach(reference -> referenceWriter.write(xml, "inputData", reference));
		xml.endElement();
	}
}

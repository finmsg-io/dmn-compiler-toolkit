package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.model.AuthorityRequirement;
import io.finmsg.dmn.model.InformationRequirement;
import io.finmsg.dmn.model.KnowledgeRequirement;

final class RequirementWriter {

	private final ElementReferenceWriter referenceWriter = new ElementReferenceWriter();

	void write(XmlEmitter xml, InformationRequirement value) {
		xml.startElement("informationRequirement");
		if (value.hasInput()) {
			referenceWriter.write(xml, "requiredInput", value.getInput());
		} else if (value.hasDecision()) {
			referenceWriter.write(xml, "requiredDecision", value.getDecision());
		}
		xml.endElement();
	}

	void write(XmlEmitter xml, KnowledgeRequirement value) {
		xml.startElement("knowledgeRequirement");
		if (value.hasRequiredKnowledge()) {
			referenceWriter.write(xml, "requiredKnowledge", value.getRequiredKnowledge());
		}
		xml.endElement();
	}

	void write(XmlEmitter xml, AuthorityRequirement value) {
		xml.startElement("authorityRequirement");
		if (value.hasRequiredAuthority()) {
			referenceWriter.write(xml, "requiredAuthority", value.getRequiredAuthority());
		}
		if (value.hasDecision()) {
			referenceWriter.write(xml, "requiredDecision", value.getDecision());
		} else if (value.hasInput()) {
			referenceWriter.write(xml, "requiredInput", value.getInput());
		}
		xml.endElement();
	}
}

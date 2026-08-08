package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.DrgElement;

public final class DrgElementWriter implements XmlWriter<DrgElement> {

	private final InputDataWriter inputDataWriter = new InputDataWriter();
	private final DecisionWriter decisionWriter = new DecisionWriter();
	private final BusinessKnowledgeModelWriter businessKnowledgeModelWriter = new BusinessKnowledgeModelWriter();
	private final KnowledgeSourceWriter knowledgeSourceWriter = new KnowledgeSourceWriter();
	private final DecisionServiceWriter decisionServiceWriter = new DecisionServiceWriter();

	@Override
	public void write(XmlEmitter xml, DrgElement value) {
		if (value.hasInputData()) {
			inputDataWriter.write(xml, value.getInputData());
			return;
		}

		if (value.hasDecision()) {
			decisionWriter.write(xml, value.getDecision());
			return;
		}

		if (value.hasBusinessKnowledgeModel()) {
			businessKnowledgeModelWriter.write(xml, value.getBusinessKnowledgeModel());
			return;
		}

		if (value.hasKnowledgeSource()) {
			knowledgeSourceWriter.write(xml, value.getKnowledgeSource());
			return;
		}

		if (value.hasDecisionService()) {
			decisionServiceWriter.write(xml, value.getDecisionService());
			return;
		}

		throw new XmlWriteException("Unsupported or empty DRG element.");
	}
}

package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.DecisionLogic;

final class DecisionLogicWriter {

	private final FeelWriter feelWriter = new FeelWriter();
	private final InvocationWriter invocationWriter = new InvocationWriter();
	private final DecisionTableWriter decisionTableWriter = new DecisionTableWriter();
	private final BoxedExpressionWriter boxedExpressionWriter = new BoxedExpressionWriter();

	void write(XmlEmitter xml, DecisionLogic value) {
		if (value.hasLiteralExpression()) {
			feelWriter.writeLiteralExpression(xml, value.getLiteralExpression());
			return;
		}
		if (value.hasInvocation()) {
			invocationWriter.write(xml, value.getInvocation());
			return;
		}
		if (value.hasDecisionTable()) {
			decisionTableWriter.write(xml, value.getDecisionTable());
			return;
		}
		if (value.hasBoxedExpression()) {
			boxedExpressionWriter.write(xml, value.getBoxedExpression());
			return;
		}
		throw new XmlWriteException("Unsupported or empty decision logic.");
	}
}

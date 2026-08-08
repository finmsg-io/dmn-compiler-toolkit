package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.frontend.xml.dmn.UnsupportedDmnXmlException;

public final class DecisionLogicReader {

	private final DecisionTableReader decisionTableReader = new DecisionTableReader();

	private final InvocationReader invocationReader = new InvocationReader();

	public DecisionLogic read(XmlCursor cursor) {

		DecisionLogic.Builder builder = DecisionLogic.newBuilder();

		switch (cursor.documentLocalName()) {
			case "literalExpression" -> builder.setLiteralExpression(ReaderRegistry.shared().feelReader().read(cursor));

			case "decisionTable" -> builder.setDecisionTable(decisionTableReader.read(cursor));

			case "invocation" -> builder.setInvocation(invocationReader.read(cursor));

			case "context", "relation", "list", "functionDefinition" -> {
				builder.setBoxedExpression(ReaderRegistry.shared().boxedExpressionReader().read(cursor));
			}

			default -> throw new UnsupportedDmnXmlException(
					"Unsupported decision logic <" + cursor.localName() + "> at " + cursor.path());
		}

		return builder.build();
	}
}

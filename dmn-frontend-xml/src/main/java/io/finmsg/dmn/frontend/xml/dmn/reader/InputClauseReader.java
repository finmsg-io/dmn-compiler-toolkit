package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.InputClause;

public final class InputClauseReader {

	private final NodeReader nodeReader = new NodeReader();
	private final FeelReader feelReader = new FeelReader();
	private final TypeReferenceReader typeReferenceReader = new TypeReferenceReader();

	public InputClause read(XmlCursor cursor) {

		InputClause.Builder builder = InputClause.newBuilder();

		builder.setNode(nodeReader.read(cursor));

		if (cursor.firstChild()) {
			do {
				switch (cursor.documentLocalName()) {
					case "inputExpression" -> {
						builder.setInputExpression(feelReader.read(cursor));

						if (cursor.hasAttribute("typeRef")) {
							builder.setType(typeReferenceReader.read(cursor.requiredAttribute("typeRef"), cursor));
						}
					}

					case "inputValues" -> builder.setInputValues(feelReader.read(cursor));

					case "documentation", "extensionElements" -> {
					}
					default -> UnsupportedContent.rejectDmnChild(cursor, "decision-table input");
				}

			} while (cursor.nextSibling());

			cursor.parent();
		}

		return builder.build();
	}
}

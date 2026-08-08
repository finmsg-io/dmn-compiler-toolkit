package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.RelationRowText;

public final class RelationRowTextReader {

	private final ReaderRegistry readers;

	RelationRowTextReader(ReaderRegistry readers) {
		this.readers = readers;
	}

	public RelationRowText read(XmlCursor cursor) {

		RelationRowText.Builder builder = RelationRowText.newBuilder();

		if (cursor.firstChild()) {
			do {
				switch (cursor.documentLocalName()) {
					case "literalExpression", "context", "relation", "list", "functionDefinition" ->
						builder.addExpressions(readers.expressionNodeReader().readText(cursor));
					case "extensionElements" -> {
					}
					default -> UnsupportedContent.rejectDmnChild(cursor, "relation row");
				}
			} while (cursor.nextSibling());
			cursor.parent();
		}

		return builder.build();
	}
}

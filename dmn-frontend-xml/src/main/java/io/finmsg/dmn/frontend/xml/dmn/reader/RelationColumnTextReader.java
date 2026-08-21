package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.InformationItem;
import io.finmsg.dmn.model.RelationColumnText;

public final class RelationColumnTextReader {

	private final NodeReader nodeReader = new NodeReader();
	private final TypeReferenceReader typeReferenceReader = new TypeReferenceReader();
	private final VariableReader variableReader = new VariableReader();

	public RelationColumnText read(XmlCursor cursor) {

		RelationColumnText.Builder builder = RelationColumnText.newBuilder();
		InformationItem.Builder varBuilder = InformationItem.newBuilder();
		varBuilder.setNode(nodeReader.read(cursor));
		if (cursor.hasAttribute("typeRef")) {
			varBuilder.setType(typeReferenceReader.read(cursor.requiredAttribute("typeRef"), cursor));
		}
		builder.setVariable(varBuilder);

		if (cursor.firstChild()) {
			do {
				switch (cursor.documentLocalName()) {
					case "variable" -> builder.setVariable(variableReader.read(cursor));
					case "extensionElements", "documentation", "description" -> {
					}
					default -> UnsupportedContent.rejectDmnChild(cursor, "relation column");
				}
			} while (cursor.nextSibling());
			cursor.parent();
		}

		return builder.build();
	}
}

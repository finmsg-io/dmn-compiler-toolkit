package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.KnowledgeSource;

public final class KnowledgeSourceReader {

	private final NodeReader nodeReader = new NodeReader();

	public KnowledgeSource read(XmlCursor cursor) {

		KnowledgeSource.Builder builder = KnowledgeSource.newBuilder();

		builder.setNode(nodeReader.read(cursor));

		if (cursor.firstChild()) {

			do {

				switch (cursor.documentLocalName()) {
					case "owner" -> builder.setAuthority(cursor.requiredAttribute("href"));

					case "locationURI" -> builder.setLocationUri(cursor.text().trim());

					case "documentation", "description", "extensionElements", "type", "authorityRequirement" -> {
					}

					default -> UnsupportedContent.rejectDmnChild(cursor, "knowledgeSource");
				}

			} while (cursor.nextSibling());

			cursor.parent();
		}

		return builder.build();
	}
}

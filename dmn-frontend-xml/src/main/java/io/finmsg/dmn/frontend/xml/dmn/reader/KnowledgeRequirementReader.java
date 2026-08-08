package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.KnowledgeRequirement;
import io.finmsg.dmn.model.ElementReference;

public final class KnowledgeRequirementReader {

	public KnowledgeRequirement read(XmlCursor cursor) {

		KnowledgeRequirement.Builder builder = KnowledgeRequirement.newBuilder();

		if (cursor.firstChild()) {

			do {

				switch (cursor.documentLocalName()) {
					case "requiredKnowledge" -> builder.setRequiredKnowledge(
							ElementReference.newBuilder().setHref(cursor.requiredAttribute("href")));

					default -> UnsupportedContent.rejectDmnChild(cursor, "knowledgeRequirement");
				}

			} while (cursor.nextSibling());

			cursor.parent();
		}

		return builder.build();
	}
}

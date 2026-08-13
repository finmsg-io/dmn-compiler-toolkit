package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.AuthorityRequirement;
import io.finmsg.dmn.model.ElementReference;

public final class AuthorityRequirementReader {

	public AuthorityRequirement read(XmlCursor cursor) {

		AuthorityRequirement.Builder builder = AuthorityRequirement.newBuilder();

		if (cursor.firstChild()) {

			do {

				switch (cursor.documentLocalName()) {
					case "requiredAuthority" -> builder.setRequiredAuthority(
							ElementReference.newBuilder().setHref(cursor.requiredAttribute("href")));

					case "requiredDecision" ->
						builder.setDecision(ElementReference.newBuilder().setHref(cursor.requiredAttribute("href")));

					case "requiredInput" ->
						builder.setInput(ElementReference.newBuilder().setHref(cursor.requiredAttribute("href")));

					default -> UnsupportedContent.rejectDmnChild(cursor, "authorityRequirement");
				}

			} while (cursor.nextSibling());

			cursor.parent();
		}

		return builder.build();
	}
}

package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.frontend.xml.dmn.UnsupportedDmnXmlException;
import java.util.Set;

final class UnsupportedContent {

	private UnsupportedContent() {
	}

	static void rejectDmnChild(XmlCursor cursor, String parent) {
		if (!cursor.documentLocalName().isEmpty()) {
			throw new UnsupportedDmnXmlException(
					"Unsupported DMN child <" + cursor.localName() + "> in " + parent + " at " + cursor.path());
		}
	}

	static void validateChildren(XmlCursor cursor, String parent, String... allowed) {
		Set<String> allowedNames = Set.of(allowed);
		if (!cursor.firstChild()) {
			return;
		}
		do {
			String name = cursor.documentLocalName();
			if (!name.isEmpty() && !allowedNames.contains(name)) {
				rejectDmnChild(cursor, parent);
			}
		} while (cursor.nextSibling());
		cursor.parent();
	}
}

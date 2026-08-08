package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ContextText;

public final class ContextTextReader {

	private final ReaderRegistry readers;

	ContextTextReader(ReaderRegistry readers) {
		this.readers = readers;
	}

	public ContextText read(XmlCursor cursor) {

		ContextText.Builder builder = ContextText.newBuilder();

		if (cursor.firstChild()) {
			do {
				if ("contextEntry".equals(cursor.documentLocalName())) {
					builder.addEntries(readers.contextEntryTextReader().read(cursor));
				} else {
					UnsupportedContent.rejectDmnChild(cursor, "context");
				}
			} while (cursor.nextSibling());
			cursor.parent();
		}

		return builder.build();
	}
}

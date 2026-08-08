package io.finmsg.dmn.frontend.xml.dmn;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.frontend.xml.exception.XmlException;

public final class DmnVersionDetector {

	public DmnXmlContext detect(XmlCursor cursor) {

		if (!cursor.isElement("definitions")) {
			throw new XmlException("Root element must be <definitions>");
		}

		String namespace = cursor.namespaceUri();

		DmnVersion version = DmnNamespaces.detect(namespace);

		return new DmnXmlContext(version, namespace, "", "", "");
	}
}

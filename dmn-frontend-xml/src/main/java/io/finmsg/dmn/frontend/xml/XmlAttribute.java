package io.finmsg.dmn.frontend.xml;

/** Immutable expanded-name XML attribute exposed by a cursor. */
public record XmlAttribute(String namespaceUri, String localName, String value) {
}

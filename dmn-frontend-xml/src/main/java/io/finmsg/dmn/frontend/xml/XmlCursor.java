package io.finmsg.dmn.frontend.xml;

import java.util.Optional;
import java.util.Map;
import java.util.List;

/**
 * Cursor abstraction over an XML document.
 *
 * Implementations are stateful. Navigation methods move the current cursor.
 */
public interface XmlCursor extends AutoCloseable {

	// --------------------------------------------------------------------
	// Element
	// --------------------------------------------------------------------

	/**
	 * Qualified element name. Example: dmn:decision
	 */
	String elementName();

	/**
	 * Local element name. Example: decision
	 */
	String localName();

	/**
	 * Namespace prefix. Example: dmn
	 */
	String prefix();

	/**
	 * Namespace URI.
	 */
	String namespaceUri();

	/** Resolves a namespace prefix in the scope of the current element. */
	Optional<String> namespaceUri(String prefix);

	/** Namespace URI of the document element. */
	String documentNamespaceUri();

	/** Namespace declarations present on the document element, keyed by prefix. */
	Map<String, String> documentNamespaceDeclarations();

	/**
	 * Local name when the current element belongs to the document namespace;
	 * otherwise an empty string. Useful for namespace-safe dispatch.
	 */
	default String documentLocalName() {
		return documentNamespaceUri().equals(namespaceUri()) ? localName() : "";
	}

	boolean isElement(String localName);

	// --------------------------------------------------------------------
	// Navigation
	// --------------------------------------------------------------------

	boolean toRoot();

	boolean parent();

	boolean firstChild();

	boolean firstChild(String localName);

	boolean nextSibling();

	boolean nextSibling(String localName);

	boolean hasChild(String localName);

	// // --------------------------------------------------------------------
	// // Convenience
	// // --------------------------------------------------------------------
	//
	// default XmlCursor child(String localName) {
	//
	// return firstChild(localName)
	// ? this
	// : null;
	// }
	//
	// default XmlCursor next(String localName) {
	//
	// return nextSibling(localName)
	// ? this
	// : null;
	// }

	// --------------------------------------------------------------------
	// Attributes
	// --------------------------------------------------------------------

	Optional<String> attribute(String name);

	String requiredAttribute(String name);

	boolean hasAttribute(String name);

	List<XmlAttribute> attributes();

	int attributeAsInt(String name);

	long attributeAsLong(String name);

	boolean attributeAsBoolean(String name);

	// --------------------------------------------------------------------
	// Text
	// --------------------------------------------------------------------

	String text();

	Optional<String> optionalText();

	boolean hasText();

	// --------------------------------------------------------------------
	// Diagnostics
	// --------------------------------------------------------------------

	int depth();

	/** Maximum element depth present in the document. */
	int maximumDepth();

	int line();

	int column();

	String path();

	/** Byte offset of the current element, or {@code -1} when unavailable. */
	int offset();

	/** Source identifier supplied by the caller. */
	String systemId();

	/** Whether model nodes should retain source locations. */
	boolean captureSourceLocations();

	// --------------------------------------------------------------------
	// Lifecycle
	// --------------------------------------------------------------------

	@Override
	void close();
}

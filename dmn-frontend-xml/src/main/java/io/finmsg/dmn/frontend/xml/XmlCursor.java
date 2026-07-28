package io.finmsg.dmn.frontend.xml;

import java.util.Optional;

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
   * Qualified element name.
   * Example: dmn:decision
   */
  String elementName();

  /**
   * Local element name.
   * Example: decision
   */
  String localName();

  /**
   * Namespace prefix.
   * Example: dmn
   */
  String prefix();

  /**
   * Namespace URI.
   */
  String namespaceUri();

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

//  // --------------------------------------------------------------------
//  // Convenience
//  // --------------------------------------------------------------------
//
//  default XmlCursor child(String localName) {
//
//    return firstChild(localName)
//            ? this
//            : null;
//  }
//
//  default XmlCursor next(String localName) {
//
//    return nextSibling(localName)
//            ? this
//            : null;
//  }

  // --------------------------------------------------------------------
  // Attributes
  // --------------------------------------------------------------------

  Optional<String> attribute(String name);

  String requiredAttribute(String name);

  boolean hasAttribute(String name);

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

  int line();

  int column();

  String path();

  // --------------------------------------------------------------------
  // Lifecycle
  // --------------------------------------------------------------------

  @Override
  void close();
}
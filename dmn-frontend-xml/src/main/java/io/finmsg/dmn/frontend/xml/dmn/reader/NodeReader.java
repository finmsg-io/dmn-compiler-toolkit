package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.Documentation;
import io.finmsg.dmn.model.ExtensionAttribute;
import io.finmsg.dmn.model.ExtensionElement;
import io.finmsg.dmn.model.ExtensionElements;
import io.finmsg.dmn.model.SourceLocation;

public final class NodeReader {

  public Node read(XmlCursor cursor) {

    Node.Builder builder = Node.newBuilder();

    if (cursor.hasAttribute("id")) {
      builder.setId(cursor.requiredAttribute("id"));
    }

    if (cursor.hasAttribute("name")) {
      builder.setName(cursor.requiredAttribute("name"));
    }

    if (cursor.hasAttribute("label")) {
      builder.setLabel(cursor.requiredAttribute("label"));
    }

    if (cursor.captureSourceLocations()) {
      SourceLocation.Builder location = SourceLocation.newBuilder().setSystemId(cursor.systemId());
      if (cursor.line() > 0) location.setLine(cursor.line());
      if (cursor.column() > 0) location.setColumn(cursor.column());
      if (cursor.offset() >= 0) location.setOffset(cursor.offset());
      builder.setSourceLocation(location);
    }

    readMetadata(cursor, builder);

    return builder.build();
  }

  private void readMetadata(XmlCursor cursor, Node.Builder builder) {
    if (!cursor.firstChild()) {
      return;
    }
    do {
      switch (cursor.documentLocalName()) {
        case "documentation" ->
            builder.setDocumentation(Documentation.newBuilder().setText(cursor.text()));
        case "extensionElements" -> builder.setExtensionElements(readExtensions(cursor));
        default -> { }
      }
    } while (cursor.nextSibling());
    cursor.parent();
  }

  private ExtensionElements readExtensions(XmlCursor cursor) {
    ExtensionElements.Builder extensions = ExtensionElements.newBuilder();
    if (!cursor.firstChild()) {
      return extensions.build();
    }
    do {
      ExtensionElement.Builder element = ExtensionElement.newBuilder()
          .setNamespace(cursor.namespaceUri())
          .setName(cursor.localName())
          .setValue(cursor.text());
      cursor.attributes().forEach(attribute ->
          element.addAttribute(
              ExtensionAttribute.newBuilder()
                  .setNamespace(attribute.namespaceUri())
                  .setName(attribute.localName())
                  .setValue(attribute.value())));
      extensions.addElement(element);
    } while (cursor.nextSibling());
    cursor.parent();
    return extensions.build();
  }
}

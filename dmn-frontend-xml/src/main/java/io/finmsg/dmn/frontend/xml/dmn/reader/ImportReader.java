package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.Import;

public final class ImportReader {

  private final NodeReader nodeReader = new NodeReader();

  public Import read(XmlCursor cursor) {

    Import.Builder builder = Import.newBuilder();
    builder.setNode(nodeReader.read(cursor).toBuilder().clearName().build());

    if (cursor.hasAttribute("namespace")) {
      builder.setNamespace(cursor.requiredAttribute("namespace"));
    }

    if (cursor.hasAttribute("locationURI")) {
      builder.setLocationUri(cursor.requiredAttribute("locationURI"));
    }

    if (cursor.hasAttribute("importType")) {
      builder.setImportType(cursor.requiredAttribute("importType"));
    }

    if (cursor.hasAttribute("name")) {
      builder.setName(cursor.requiredAttribute("name"));
    }
    UnsupportedContent.validateChildren(
        cursor, "import", "documentation", "extensionElements");
    return builder.build();
  }
}

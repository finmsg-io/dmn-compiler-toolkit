package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.InformationItem;

public final class InformationItemReader {

  private final NodeReader nodeReader = new NodeReader();
  private final TypeReferenceReader typeReferenceReader = new TypeReferenceReader();

  public InformationItem read(XmlCursor cursor) {
    InformationItem.Builder builder = InformationItem.newBuilder();
    builder.setNode(nodeReader.read(cursor));
    if (cursor.hasAttribute("typeRef")) {
      builder.setType(typeReferenceReader.read(cursor.requiredAttribute("typeRef"), cursor));
    }
    return builder.build();
  }
}

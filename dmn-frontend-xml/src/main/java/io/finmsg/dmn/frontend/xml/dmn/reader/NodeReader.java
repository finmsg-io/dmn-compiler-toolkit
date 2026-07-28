package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.Node;

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

    //
    // documentation
    // extensionElements
    // sourceLocation
    //
    // added later
    //

    return builder.build();
  }
}

package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.DecisionService;

public final class DecisionServiceReader {

  private final NodeReader nodeReader = new NodeReader();

  public DecisionService read(XmlCursor cursor) {

    DecisionService.Builder builder = DecisionService.newBuilder();

    builder.setNode(nodeReader.read(cursor));

    if (cursor.firstChild()) {

      do {

        switch (cursor.localName()) {
          case "outputDecision" -> {
            // later
          }

          case "encapsulatedDecision" -> {
            // later
          }

          case "inputDecision" -> {
            // later
          }

          case "inputData" -> {
            // later
          }

          case "extensionElements" -> {
            // later
          }

          default -> {
            // ignore unknown children
          }
        }

      } while (cursor.nextSibling());

      cursor.parent();
    }

    return builder.build();
  }
}

package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.InputData;

public final class InputDataReader {

  private final NodeReader nodeReader = new NodeReader();
  private final VariableReader variableReader = new VariableReader();

  public InputData read(XmlCursor cursor) {

    InputData.Builder builder = InputData.newBuilder();

    builder.setNode(nodeReader.read(cursor));

    if (cursor.firstChild()) {

      do {

        switch (cursor.documentLocalName()) {
          case "variable" -> builder.setVariable(variableReader.read(cursor));

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

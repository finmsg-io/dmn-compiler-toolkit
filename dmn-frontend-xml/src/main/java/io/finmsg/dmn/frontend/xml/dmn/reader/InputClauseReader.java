package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.InputClause;

public final class InputClauseReader {

  private final NodeReader nodeReader = new NodeReader();
  private final FeelSourceReader feelSourceReader = new FeelSourceReader();
  private final TypeReferenceReader typeReferenceReader = new TypeReferenceReader();

  public InputClause read(XmlCursor cursor) {

    InputClause.Builder builder = InputClause.newBuilder();

    builder.setNode(nodeReader.read(cursor));

    if (cursor.firstChild()) {
      do {
        switch (cursor.localName()) {
          case "inputExpression" -> {
            builder.setInputExpression(feelSourceReader.read(cursor));

            if (cursor.hasAttribute("typeRef")) {
              builder.setType(typeReferenceReader.read(cursor.requiredAttribute("typeRef")));
            }
          }

          case "inputValues" -> builder.setInputValues(feelSourceReader.read(cursor));

          default -> {}
        }

      } while (cursor.nextSibling());

      cursor.parent();
    }

    return builder.build();
  }
}

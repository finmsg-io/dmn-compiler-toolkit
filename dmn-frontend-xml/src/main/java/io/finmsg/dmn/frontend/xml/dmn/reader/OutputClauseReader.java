package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.OutputClause;

public final class OutputClauseReader {

  private final NodeReader nodeReader = new NodeReader();
  private final FeelReader feelReader = new FeelReader();
  private final ExpressionNodeReader expressionReader =
      ReaderRegistry.shared().expressionNodeReader();
  private final TypeReferenceReader typeReferenceReader = new TypeReferenceReader();

  public OutputClause read(XmlCursor cursor) {

    OutputClause.Builder builder = OutputClause.newBuilder();

    builder.setNode(nodeReader.read(cursor));

    if (cursor.hasAttribute("typeRef")) {
      builder.setType(typeReferenceReader.read(cursor.requiredAttribute("typeRef")));
    }

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "outputValues" -> builder.setOutputValues(feelReader.read(cursor));

          case "defaultOutputEntry" ->
              builder.setDefaultOutputEntry(expressionReader.read(cursor));

          default -> {}
        }

      } while (cursor.nextSibling());

      cursor.parent();
    }

    return builder.build();
  }
}

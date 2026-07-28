package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ExpressionSource;

public final class ExpressionSourceReader {

  public ExpressionSource read(XmlCursor cursor) {

    ExpressionSource.Builder builder = ExpressionSource.newBuilder();

    switch (cursor.localName()) {
      case "literalExpression" -> builder.setFeel(new FeelSourceReader().read(cursor));

      case "context", "relation", "list", "functionDefinition" ->
          builder.setBoxed(new BoxedExpressionSourceReader().read(cursor));

      default ->
          throw new IllegalArgumentException(
              "Unsupported expression source: " + cursor.localName());
    }

    return builder.build();
  }
}

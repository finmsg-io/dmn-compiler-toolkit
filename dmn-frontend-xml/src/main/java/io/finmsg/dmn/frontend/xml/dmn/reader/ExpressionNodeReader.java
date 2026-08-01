package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ExpressionNode;
import io.finmsg.dmn.model.ExpressionText;
import io.finmsg.dmn.frontend.xml.dmn.UnsupportedDmnXmlException;

public final class ExpressionNodeReader {

  private final ReaderRegistry readers;

  ExpressionNodeReader(ReaderRegistry readers) {
    this.readers = readers;
  }

  public ExpressionNode read(XmlCursor cursor) {
    return ExpressionNode.newBuilder()
        .setText(readText(cursor))
        .build();
  }

  public ExpressionText readText(XmlCursor cursor) {

    ExpressionText.Builder builder = ExpressionText.newBuilder();

        switch (cursor.documentLocalName()) {
      case "literalExpression", "inputExpression", "inputValues", "outputValues",
          "inputEntry", "outputEntry", "defaultOutputEntry", "expression" ->
          builder.setFeel(readers.feelReader().readText(cursor));

      case "context", "relation", "list", "functionDefinition" ->
          builder.setBoxed(readers.boxedExpressionReader().readText(cursor));

      default ->
          throw new UnsupportedDmnXmlException(
              "Unsupported expression <" + cursor.localName() + "> at " + cursor.path());
    }

    return builder.build();
  }
}

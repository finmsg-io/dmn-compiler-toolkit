package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.BoxedExpression;
import io.finmsg.dmn.model.BoxedExpressionText;
import io.finmsg.dmn.frontend.xml.dmn.UnsupportedDmnXmlException;

public final class BoxedExpressionReader {

  private final ReaderRegistry readers;

  BoxedExpressionReader(ReaderRegistry readers) {
    this.readers = readers;
  }

  public BoxedExpression read(XmlCursor cursor) {
    return BoxedExpression.newBuilder()
        .setText(readText(cursor))
        .build();
  }

  public BoxedExpressionText readText(XmlCursor cursor) {

    BoxedExpressionText.Builder builder = BoxedExpressionText.newBuilder();

    switch (cursor.documentLocalName()) {
      case "context" -> builder.setContext(readers.contextTextReader().read(cursor));
      case "relation" -> builder.setRelation(readers.relationTextReader().read(cursor));
      case "list" -> builder.setList(readers.listExpressionTextReader().read(cursor));
      case "functionDefinition" ->
          builder.setFunctionDefinition(readers.functionDefinitionTextReader().read(cursor));
      default ->
          throw new UnsupportedDmnXmlException(
              "Unsupported boxed expression <" + cursor.localName() + "> at " + cursor.path());
    }

    return builder.build();
  }
}

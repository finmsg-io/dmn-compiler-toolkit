package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.BoxedExpressionSource;

public final class BoxedExpressionSourceReader {

  private final ContextSourceReader contextReader = new ContextSourceReader();
  private final RelationSourceReader relationReader = new RelationSourceReader();
  private final ListExpressionSourceReader listReader = new ListExpressionSourceReader();
  private final FunctionDefinitionSourceReader functionDefinitionReader =
      new FunctionDefinitionSourceReader();

  public BoxedExpressionSource read(XmlCursor cursor) {

    BoxedExpressionSource.Builder builder = BoxedExpressionSource.newBuilder();

    switch (cursor.localName()) {
      case "context" -> builder.setContext(contextReader.read(cursor));

      case "relation" -> builder.setRelation(relationReader.read(cursor));

      case "list" -> builder.setList(listReader.read(cursor));

      case "functionDefinition" ->
          builder.setFunctionDefinition(functionDefinitionReader.read(cursor));

      default ->
          throw new IllegalArgumentException("Unsupported boxed expression: " + cursor.localName());
    }

    return builder.build();
  }
}

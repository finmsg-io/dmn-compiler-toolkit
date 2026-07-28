package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.DecisionLogic;

public final class DecisionLogicReader {

  private final ExpressionSourceReader expressionReader = new ExpressionSourceReader();

  private final DecisionTableReader decisionTableReader = new DecisionTableReader();

  private final InvocationReader invocationReader = new InvocationReader();

  public DecisionLogic read(XmlCursor cursor) {

    DecisionLogic.Builder builder = DecisionLogic.newBuilder();

    switch (cursor.localName()) {
      case "literalExpression" -> builder.setLiteralExpression(new FeelSourceReader().read(cursor));

      case "decisionTable" -> builder.setDecisionTable(decisionTableReader.read(cursor));

      case "invocation" -> builder.setInvocation(invocationReader.read(cursor));

      case "context", "relation", "list", "functionDefinition" -> {
        builder.setBoxedExpression(new BoxedExpressionSourceReader().read(cursor));
      }

      default ->
          throw new IllegalArgumentException("Unsupported decision logic: " + cursor.localName());
    }

    return builder.build();
  }
}

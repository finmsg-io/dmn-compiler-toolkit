package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.DecisionLogic;

final class DecisionLogicWriter {

  private final FeelWriter feelWriter = new FeelWriter();
  private final InvocationWriter invocationWriter = new InvocationWriter();

  void write(XmlEmitter xml, DecisionLogic value) {
    if (value.hasLiteralExpression()) {
      feelWriter.writeLiteralExpression(xml, value.getLiteralExpression());
      return;
    }
    if (value.hasInvocation()) {
      invocationWriter.write(xml, value.getInvocation());
      return;
    }
    throw new XmlWriteException("Unsupported or empty decision logic.");
  }
}

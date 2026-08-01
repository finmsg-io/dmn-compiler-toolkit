package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeUnaryTestsExpression(RuntimeUnaryTests tests, RuntimeType type)
    implements RuntimeExpression {
  public RuntimeUnaryTestsExpression {
    Objects.requireNonNull(tests, "tests");
    Objects.requireNonNull(type, "type");
  }
}

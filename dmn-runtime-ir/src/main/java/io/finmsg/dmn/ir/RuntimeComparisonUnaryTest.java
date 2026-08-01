package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeComparisonUnaryTest(
    RuntimeUnaryTestOperator operator, RuntimeExpression endpoint) implements RuntimeUnaryTest {
  public RuntimeComparisonUnaryTest {
    Objects.requireNonNull(operator, "operator");
    Objects.requireNonNull(endpoint, "endpoint");
  }
}

package io.finmsg.dmn.ir;

import java.util.Objects;
import java.util.Optional;

public record RuntimeRangeExpression(
    Optional<RuntimeExpression> lower,
    Optional<RuntimeExpression> upper,
    RuntimeRangeBoundary lowerBoundary,
    RuntimeRangeBoundary upperBoundary,
    RuntimeType type) implements RuntimeExpression {
  public RuntimeRangeExpression {
    lower = Objects.requireNonNull(lower, "lower");
    upper = Objects.requireNonNull(upper, "upper");
    Objects.requireNonNull(lowerBoundary, "lowerBoundary");
    Objects.requireNonNull(upperBoundary, "upperBoundary");
    Objects.requireNonNull(type, "type");
  }
}

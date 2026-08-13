package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeBetweenExpression(RuntimeExpression value, RuntimeExpression lower, RuntimeExpression upper,
		RuntimeType type) implements RuntimeExpression {
	public RuntimeBetweenExpression {
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(lower, "lower");
		Objects.requireNonNull(upper, "upper");
		Objects.requireNonNull(type, "type");
	}
}

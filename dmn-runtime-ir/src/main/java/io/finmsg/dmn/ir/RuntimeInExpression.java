package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeInExpression(RuntimeExpression value, RuntimeUnaryTests tests,
		RuntimeType type) implements RuntimeExpression {
	public RuntimeInExpression {
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(tests, "tests");
		Objects.requireNonNull(type, "type");
	}
}

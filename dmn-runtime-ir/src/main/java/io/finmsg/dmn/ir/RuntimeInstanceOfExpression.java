package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeInstanceOfExpression(RuntimeExpression expression, RuntimeType testedType,
		RuntimeType type) implements RuntimeExpression {
	public RuntimeInstanceOfExpression {
		Objects.requireNonNull(expression, "expression");
		Objects.requireNonNull(testedType, "testedType");
		Objects.requireNonNull(type, "type");
	}
}

package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeExpressionUnaryTest(RuntimeExpression expression) implements RuntimeUnaryTest {
	public RuntimeExpressionUnaryTest {
		Objects.requireNonNull(expression, "expression");
	}
}

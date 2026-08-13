package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeRangeUnaryTest(RuntimeRangeExpression range) implements RuntimeUnaryTest {
	public RuntimeRangeUnaryTest {
		Objects.requireNonNull(range, "range");
	}
}

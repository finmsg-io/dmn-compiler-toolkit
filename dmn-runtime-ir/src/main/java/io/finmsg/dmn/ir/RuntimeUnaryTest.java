package io.finmsg.dmn.ir;

public sealed interface RuntimeUnaryTest
		permits RuntimeComparisonUnaryTest, RuntimeRangeUnaryTest, RuntimeExpressionUnaryTest {
}

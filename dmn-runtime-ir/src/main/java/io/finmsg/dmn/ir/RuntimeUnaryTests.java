package io.finmsg.dmn.ir;

import java.util.List;

public record RuntimeUnaryTests(boolean negated, boolean wildcard, List<RuntimeUnaryTest> tests) {
	public RuntimeUnaryTests {
		tests = List.copyOf(tests);
	}
}

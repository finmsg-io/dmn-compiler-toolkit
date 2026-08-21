package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

public record RuntimeForExpression(List<RuntimeIteration> iterations, RuntimeExpression result,
		int partialSlot, RuntimeType type) implements RuntimeExpression {
	public RuntimeForExpression {
		iterations = List.copyOf(iterations);
		if (iterations.isEmpty()) {
			throw new IllegalArgumentException("iterations must not be empty.");
		}
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(type, "type");
	}
}

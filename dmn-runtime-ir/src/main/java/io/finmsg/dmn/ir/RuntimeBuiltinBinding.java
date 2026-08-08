package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeBuiltinBinding(int expressionOrdinal, RuntimeBuiltinOperation operation) {
	public RuntimeBuiltinBinding {
		if (expressionOrdinal < 0)
			throw new IllegalArgumentException("Ordinal must be non-negative.");
		Objects.requireNonNull(operation, "operation");
	}
}

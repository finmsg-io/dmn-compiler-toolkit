package io.finmsg.dmn.ir;

import java.util.Objects;

/** Reference to a lexically scoped value produced inside an expression. */
public record RuntimeLocalReference(int lexicalDepth, int localSlot, RuntimeType type) implements RuntimeExpression {
	public RuntimeLocalReference {
		if (lexicalDepth < 0) {
			throw new IllegalArgumentException("lexicalDepth must not be negative.");
		}
		if (localSlot < 0) {
			throw new IllegalArgumentException("localSlot must not be negative.");
		}
		Objects.requireNonNull(type, "type");
	}

	public RuntimeLocalReference(int localSlot, RuntimeType type) {
		this(0, localSlot, type);
	}
}

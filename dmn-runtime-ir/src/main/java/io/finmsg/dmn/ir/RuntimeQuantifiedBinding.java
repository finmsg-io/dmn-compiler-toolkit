package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeQuantifiedBinding(int localSlot, RuntimeExpression source) {
	public RuntimeQuantifiedBinding {
		if (localSlot < 0) {
			throw new IllegalArgumentException("localSlot must not be negative.");
		}
		Objects.requireNonNull(source, "source");
	}
}

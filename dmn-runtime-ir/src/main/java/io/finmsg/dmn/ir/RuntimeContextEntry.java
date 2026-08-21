package io.finmsg.dmn.ir;

import java.util.Objects;

/**
 * A sequentially evaluated context entry; an unnamed entry is the boxed-context
 * result.
 */
public record RuntimeContextEntry(String name, int localSlot, RuntimeExpression expression) {
	public RuntimeContextEntry {
		Objects.requireNonNull(name, "name");
		if (localSlot < -1) {
			throw new IllegalArgumentException("localSlot must be >= -1.");
		}
		Objects.requireNonNull(expression, "expression");
	}
}

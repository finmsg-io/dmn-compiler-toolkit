package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeFunctionParameter(String name, int localSlot, RuntimeType type) {
	public RuntimeFunctionParameter {
		Objects.requireNonNull(name, "name");
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank.");
		}
		if (localSlot < 0) {
			throw new IllegalArgumentException("localSlot must not be negative.");
		}
		Objects.requireNonNull(type, "type");
	}
}

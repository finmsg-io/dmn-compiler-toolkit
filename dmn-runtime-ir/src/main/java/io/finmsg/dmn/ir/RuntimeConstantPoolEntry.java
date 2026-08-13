package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeConstantPoolEntry(int id, RuntimeConstantKind kind, RuntimeCanonicalValue value,
		RuntimeType type) {
	public RuntimeConstantPoolEntry {
		if (id < 0)
			throw new IllegalArgumentException("Constant pool ID must be non-negative.");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(type, "type");
	}
}

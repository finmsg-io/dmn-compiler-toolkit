package io.finmsg.dmn.ir;

import java.util.Objects;

/** Named property access or list projection over a runtime value. */
public record RuntimePathExpression(RuntimeExpression source, String member, int fieldIndex,
		RuntimeType type) implements RuntimeExpression {
	public RuntimePathExpression {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(member, "member");
		if (member.isBlank()) {
			throw new IllegalArgumentException("member must not be blank.");
		}
		if (fieldIndex < -1) {
			throw new IllegalArgumentException("fieldIndex must be -1 or non-negative.");
		}
		Objects.requireNonNull(type, "type");
	}

	public RuntimePathExpression(RuntimeExpression source, String member, RuntimeType type) {
		this(source, member, -1, type);
	}
}

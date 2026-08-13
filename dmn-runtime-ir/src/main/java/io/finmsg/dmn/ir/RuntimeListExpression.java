package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

public record RuntimeListExpression(List<RuntimeExpression> elements, RuntimeType type) implements RuntimeExpression {
	public RuntimeListExpression {
		elements = List.copyOf(elements);
		Objects.requireNonNull(type, "type");
	}
}

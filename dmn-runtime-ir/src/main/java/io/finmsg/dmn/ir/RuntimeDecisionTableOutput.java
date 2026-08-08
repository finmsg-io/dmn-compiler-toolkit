package io.finmsg.dmn.ir;

import java.util.Objects;
import java.util.Optional;

public record RuntimeDecisionTableOutput(Optional<String> name, RuntimeType type,
		Optional<RuntimeUnaryTests> allowedValues, Optional<RuntimeExpression> defaultValue) {
	public RuntimeDecisionTableOutput {
		name = Objects.requireNonNull(name, "name");
		Objects.requireNonNull(type, "type");
		allowedValues = Objects.requireNonNull(allowedValues, "allowedValues");
		defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
	}
}

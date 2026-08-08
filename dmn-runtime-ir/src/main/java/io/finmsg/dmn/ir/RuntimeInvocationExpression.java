package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Invocation of either a statically named function or a dynamically evaluated
 * target.
 */
public record RuntimeInvocationExpression(Optional<String> function, Optional<RuntimeExpression> target,
		List<RuntimeNamedArgument> namedArguments, List<RuntimeExpression> positionalArguments,
		RuntimeType type) implements RuntimeExpression {
	public RuntimeInvocationExpression {
		function = Objects.requireNonNull(function, "function");
		target = Objects.requireNonNull(target, "target");
		if (function.isPresent() == target.isPresent()) {
			throw new IllegalArgumentException("Exactly one of function and target must be present.");
		}
		if (function.isPresent() && function.orElseThrow().isBlank()) {
			throw new IllegalArgumentException("function must not be blank.");
		}
		namedArguments = List.copyOf(namedArguments);
		positionalArguments = List.copyOf(positionalArguments);
		Objects.requireNonNull(type, "type");
	}
}

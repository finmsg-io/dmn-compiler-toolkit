package io.finmsg.dmn.compiler;

import java.util.Objects;

/** Namespace and model name identity independent of source location. */
public record DmnModelIdentity(String namespace, String name) {

	public DmnModelIdentity {
		Objects.requireNonNull(namespace, "namespace");
		Objects.requireNonNull(name, "name");
		if (namespace.isBlank()) {
			throw new IllegalArgumentException("namespace must not be blank");
		}
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
	}
}

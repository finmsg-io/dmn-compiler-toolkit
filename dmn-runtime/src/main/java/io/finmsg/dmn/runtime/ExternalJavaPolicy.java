package io.finmsg.dmn.runtime;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable authorization for model-selected Java targets; never loads a class.
 */
public final class ExternalJavaPolicy implements java.io.Serializable {
	private static final long serialVersionUID = 1L;
	public record Method(String className, String signature) implements java.io.Serializable {
		public Method {
			className = Objects.requireNonNull(className, "className").trim();
			signature = Objects.requireNonNull(signature, "signature").trim();
		}
	}

	private final Set<Method> methods;
	private final boolean unrestricted;

	private ExternalJavaPolicy(Set<Method> methods, boolean unrestricted) {
		this.methods = Set.copyOf(methods);
		this.unrestricted = unrestricted;
	}

	public static ExternalJavaPolicy denyAll() {
		return new ExternalJavaPolicy(Set.of(), false);
	}

	public static ExternalJavaPolicy allowOnly(Set<Method> methods) {
		return new ExternalJavaPolicy(methods, false);
	}

	/** Explicit compatibility mode for trusted models only. */
	public static ExternalJavaPolicy unrestrictedTrustedUse() {
		return new ExternalJavaPolicy(Set.of(), true);
	}

	public boolean permits(String className, String signature) {
		return unrestricted || methods.contains(new Method(className, signature));
	}

	public Set<Method> approvedMethods() {
		return methods;
	}

	public boolean isUnrestrictedTrustedUse() {
		return unrestricted;
	}

	void check(String className, String signature) {
		if (!permits(className, signature)) {
			throw new ExternalJavaDeniedException(className, signature);
		}
	}
}

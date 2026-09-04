package io.finmsg.dmn.compiler;

import java.util.Objects;

/** Immutable options accepted by the public compiler facade. */
public record DmnCompilerOptions(DmnModelLoadOptions modelLoadOptions, RuntimeModelMode runtimeModelMode) {

	public DmnCompilerOptions {
		Objects.requireNonNull(modelLoadOptions, "modelLoadOptions");
		Objects.requireNonNull(runtimeModelMode, "runtimeModelMode");
	}

	/**
	 * Preserves the original single-argument construction with production defaults.
	 */
	public DmnCompilerOptions(DmnModelLoadOptions modelLoadOptions) {
		this(modelLoadOptions, RuntimeModelMode.OPTIMIZED);
	}

	public static DmnCompilerOptions defaults() {
		return optimized();
	}

	public static DmnCompilerOptions lowered() {
		return new DmnCompilerOptions(DmnModelLoadOptions.defaults(), RuntimeModelMode.LOWERED);
	}

	public static DmnCompilerOptions optimized() {
		return new DmnCompilerOptions(DmnModelLoadOptions.defaults(), RuntimeModelMode.OPTIMIZED);
	}
}

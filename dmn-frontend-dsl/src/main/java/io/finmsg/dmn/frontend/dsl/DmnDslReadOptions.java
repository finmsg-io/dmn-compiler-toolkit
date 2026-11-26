package io.finmsg.dmn.frontend.dsl;

/**
 * Resource bounds and options for parsing {@code .decision} DSL sources.
 */
public final class DmnDslReadOptions {

	public static final int DEFAULT_MAX_BYTES = 1024 * 1024;
	public static final int DEFAULT_MAX_DECLARATIONS = 4096;

	private final int maxBytes;
	private final int maxDeclarations;

	private DmnDslReadOptions(int maxBytes, int maxDeclarations) {
		if (maxBytes <= 0) {
			throw new IllegalArgumentException("maxBytes must be positive");
		}
		if (maxDeclarations <= 0) {
			throw new IllegalArgumentException("maxDeclarations must be positive");
		}
		this.maxBytes = maxBytes;
		this.maxDeclarations = maxDeclarations;
	}

	public static DmnDslReadOptions defaults() {
		return new DmnDslReadOptions(DEFAULT_MAX_BYTES, DEFAULT_MAX_DECLARATIONS);
	}

	public DmnDslReadOptions withMaxBytes(int maxBytes) {
		return new DmnDslReadOptions(maxBytes, this.maxDeclarations);
	}

	public DmnDslReadOptions withMaxDeclarations(int maxDeclarations) {
		return new DmnDslReadOptions(this.maxBytes, maxDeclarations);
	}

	public int maxBytes() {
		return maxBytes;
	}

	public int maxDeclarations() {
		return maxDeclarations;
	}
}

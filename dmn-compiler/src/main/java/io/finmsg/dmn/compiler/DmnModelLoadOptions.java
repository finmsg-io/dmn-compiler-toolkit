package io.finmsg.dmn.compiler;

/** Resource limits for one transitive model-loading operation. */
public record DmnModelLoadOptions(int maxSources, int maxImportDepth) {

	public static final int DEFAULT_MAX_SOURCES = 1_024;
	public static final int DEFAULT_MAX_IMPORT_DEPTH = 64;

	public DmnModelLoadOptions {
		if (maxSources <= 0) {
			throw new IllegalArgumentException("maxSources must be greater than zero");
		}
		if (maxImportDepth < 0) {
			throw new IllegalArgumentException("maxImportDepth must not be negative");
		}
	}

	public static DmnModelLoadOptions defaults() {
		return new DmnModelLoadOptions(DEFAULT_MAX_SOURCES, DEFAULT_MAX_IMPORT_DEPTH);
	}
}

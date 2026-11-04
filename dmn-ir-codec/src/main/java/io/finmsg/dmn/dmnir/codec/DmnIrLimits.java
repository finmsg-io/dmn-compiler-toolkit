package io.finmsg.dmn.dmnir.codec;

/** Per-read limits. Wire budgets apply before generated message allocation. */
public record DmnIrLimits(int maxBytes, int maxDepth, int maxMessages, int maxFields, int maxStringBytes, int maxModels,
		int maxElements, int maxReferences, int maxDecimalDigits, int maxAbsScale) {
	public DmnIrLimits {
		if (maxBytes < 1 || maxDepth < 1 || maxDepth > 64 || maxMessages < 1 || maxFields < 1 || maxStringBytes < 1
				|| maxModels < 1 || maxElements < 1 || maxReferences < 1 || maxDecimalDigits < 1 || maxAbsScale < 0) {
			throw new IllegalArgumentException("Limits must be positive, depth at most 64, and scale non-negative.");
		}
	}

	public static DmnIrLimits defaults() {
		return new DmnIrLimits(4 * 1024 * 1024, 32, 50_000, 200_000, 16_384, 128, 10_000, 50_000, 4096, 4096);
	}
}

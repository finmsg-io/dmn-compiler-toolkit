package io.finmsg.dmn.optimizer;

/**
 * Configuration options for controlling optimization passes in DmnOptimizer.
 */
public record DmnOptimizerOptions(boolean enableConstantFolding, boolean enableAlgebraicSimplification,
		boolean enableDecisionTableOptimization) {
	public static DmnOptimizerOptions defaultOptions() {
		return new DmnOptimizerOptions(true, true, true);
	}

	public static DmnOptimizerOptions disabled() {
		return new DmnOptimizerOptions(false, false, false);
	}
}

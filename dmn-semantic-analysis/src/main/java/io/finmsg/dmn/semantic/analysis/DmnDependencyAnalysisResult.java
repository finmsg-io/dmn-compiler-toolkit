package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import java.util.List;

/**
 * Dependency diagnostics and deterministic compilation order for one DMN model.
 */
public record DmnDependencyAnalysisResult(Definitions model, List<DrgElement> compilationOrder,
		List<DmnSemanticDiagnostic> diagnostics) {

	public DmnDependencyAnalysisResult {
		compilationOrder = List.copyOf(compilationOrder);
		diagnostics = List.copyOf(diagnostics);
	}

	public boolean isSuccess() {
		return diagnostics.isEmpty();
	}
}

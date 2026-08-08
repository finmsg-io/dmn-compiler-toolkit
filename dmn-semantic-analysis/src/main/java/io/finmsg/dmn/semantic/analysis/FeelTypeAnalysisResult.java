package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.Expression;
import java.util.List;

/** Copied FEEL AST with inferred types plus type-analysis diagnostics. */
public record FeelTypeAnalysisResult(Expression expression, List<DmnSemanticDiagnostic> diagnostics) {

	public FeelTypeAnalysisResult {
		diagnostics = List.copyOf(diagnostics);
	}

	public boolean isSuccess() {
		return diagnostics.isEmpty();
	}
}

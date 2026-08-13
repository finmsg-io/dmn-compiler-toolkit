package io.finmsg.dmn.compiler;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, deterministic semantic result for an entire loaded DMN model set.
 */
public record DmnModelSetSemanticResult(DmnSourceId rootId, List<DmnSemanticModel> models,
		List<DmnImportEdge> importEdges, List<DmnCompilerDiagnostic> diagnostics) {

	private static final Comparator<DmnImportEdge> EDGE_ORDER = Comparator.comparing(DmnImportEdge::importer)
			.thenComparingInt(DmnImportEdge::importIndex).thenComparing(DmnImportEdge::imported);

	public DmnModelSetSemanticResult {
		Objects.requireNonNull(rootId, "rootId");
		Objects.requireNonNull(models, "models");
		Objects.requireNonNull(importEdges, "importEdges");
		Objects.requireNonNull(diagnostics, "diagnostics");
		models = models.stream().map(model -> Objects.requireNonNull(model, "model"))
				.sorted(Comparator.comparing(DmnSemanticModel::sourceId)).toList();
		importEdges = importEdges.stream().map(edge -> Objects.requireNonNull(edge, "importEdge")).sorted(EDGE_ORDER)
				.toList();
		diagnostics = diagnostics.stream().map(diagnostic -> Objects.requireNonNull(diagnostic, "diagnostic"))
				.sorted(DmnCompilerDiagnostic.ORDER).toList();
		if (models.stream().map(DmnSemanticModel::sourceId).distinct().count() != models.size()) {
			throw new IllegalArgumentException("Semantic models must have distinct source IDs");
		}
		if (!models.isEmpty() && models.stream().noneMatch(model -> model.sourceId().equals(rootId))) {
			throw new IllegalArgumentException("A non-empty semantic model set must contain the root");
		}
	}

	public Optional<DmnSemanticModel> model(DmnSourceId sourceId) {
		Objects.requireNonNull(sourceId, "sourceId");
		return models.stream().filter(model -> model.sourceId().equals(sourceId)).findFirst();
	}

	public boolean isValid() {
		return !hasErrors();
	}

	public boolean hasErrors() {
		return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DmnDiagnosticSeverity.ERROR);
	}
}

package io.finmsg.dmn.compiler;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, deterministically ordered graph of parsed DMN sources and resolved
 * imports.
 */
public record DmnModelLoadResult(DmnSourceId rootId, List<LoadedDmnModel> models, List<DmnImportEdge> importEdges,
		List<DmnCompilerDiagnostic> diagnostics) {

	private static final Comparator<DmnImportEdge> EDGE_ORDER = Comparator.comparing(DmnImportEdge::importer)
			.thenComparingInt(DmnImportEdge::importIndex).thenComparing(DmnImportEdge::imported);

	public DmnModelLoadResult {
		Objects.requireNonNull(rootId, "rootId");
		Objects.requireNonNull(models, "models");
		Objects.requireNonNull(importEdges, "importEdges");
		Objects.requireNonNull(diagnostics, "diagnostics");
		models = models.stream().map(model -> Objects.requireNonNull(model, "model"))
				.sorted(Comparator.comparing(LoadedDmnModel::id)).toList();
		importEdges = importEdges.stream().map(edge -> Objects.requireNonNull(edge, "importEdge")).sorted(EDGE_ORDER)
				.toList();
		diagnostics = diagnostics.stream().map(diagnostic -> Objects.requireNonNull(diagnostic, "diagnostic"))
				.sorted(DmnCompilerDiagnostic.ORDER).toList();
		long distinctIds = models.stream().map(LoadedDmnModel::id).distinct().count();
		if (distinctIds != models.size()) {
			throw new IllegalArgumentException("Loaded models must have distinct source IDs");
		}
		if (models.stream().noneMatch(model -> model.id().equals(rootId))) {
			throw new IllegalArgumentException("Loaded models must contain the root source");
		}
		Set<DmnSourceId> loadedIds = new HashSet<>(models.stream().map(LoadedDmnModel::id).toList());
		if (importEdges.stream()
				.anyMatch(edge -> !loadedIds.contains(edge.importer()) || !loadedIds.contains(edge.imported()))) {
			throw new IllegalArgumentException("Import edges must reference loaded source IDs");
		}
	}

	public Optional<LoadedDmnModel> model(DmnSourceId id) {
		Objects.requireNonNull(id, "id");
		return models.stream().filter(model -> model.id().equals(id)).findFirst();
	}

	public boolean isValid() {
		return !hasErrors();
	}

	public boolean hasErrors() {
		return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DmnDiagnosticSeverity.ERROR);
	}
}

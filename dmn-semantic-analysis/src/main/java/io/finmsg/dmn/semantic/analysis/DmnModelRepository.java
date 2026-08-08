package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.Import;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.NamedTypeReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Namespace-indexed set of DMN models available for semantic linking. */
public final class DmnModelRepository {
	private final Map<String, List<Definitions>> modelsByNamespace = new LinkedHashMap<>();

	public DmnModelRepository(Collection<Definitions> models) {
		Objects.requireNonNull(models, "models");
		for (Definitions model : models) {
			Objects.requireNonNull(model, "model");
			modelsByNamespace.computeIfAbsent(model.getNamespace(), ignored -> new ArrayList<>()).add(model);
		}
	}

	public List<DmnSemanticDiagnostic> validateImports(Definitions source) {
		List<DmnSemanticDiagnostic> diagnostics = new ArrayList<>();
		Map<String, Integer> seen = new LinkedHashMap<>();
		for (int i = 0; i < source.getImportsCount(); i++) {
			Import imported = source.getImports(i);
			String path = "definitions/import[" + i + "]";
			String namespace = imported.getNamespace();
			if (namespace.isBlank()) {
				diagnostics
						.add(diagnostic("MISSING_IMPORT_NAMESPACE", path, "Import namespace is required.", imported));
				continue;
			}
			if (seen.putIfAbsent(namespace, i) != null) {
				diagnostics.add(diagnostic("DUPLICATE_IMPORT", path,
						"Namespace '" + namespace + "' is imported more than once.", imported));
			}
			List<Definitions> matches = modelsByNamespace.getOrDefault(namespace, List.of());
			if (matches.isEmpty()) {
				diagnostics.add(diagnostic("UNKNOWN_IMPORT", path,
						"No available DMN model has namespace '" + namespace + "'.", imported));
			} else if (matches.size() > 1) {
				diagnostics.add(diagnostic("AMBIGUOUS_IMPORT", path,
						"Multiple available DMN models have namespace '" + namespace + "'.", imported));
			}
		}
		return List.copyOf(diagnostics);
	}

	List<ResolvedDrgElement> resolveDrg(Definitions source, String href) {
		ReferenceParts parts = ReferenceParts.parse(href);
		List<Definitions> targets = targetModels(source, parts.qualifier());
		List<ResolvedDrgElement> result = new ArrayList<>();
		for (Definitions target : targets) {
			for (DrgElement element : target.getDrgElementsList()) {
				if (nodeId(element).equals(parts.id())) {
					result.add(new ResolvedDrgElement(target, element));
				}
			}
		}
		return result;
	}

	List<ResolvedItemDefinition> resolveType(Definitions source, NamedTypeReference reference) {
		List<Definitions> targets = targetModels(source, reference.getNamespace());
		List<ResolvedItemDefinition> result = new ArrayList<>();
		for (Definitions target : targets) {
			target.getItemDefinitionsList().stream()
					.filter(item -> item.getNode().getName().equals(reference.getName()))
					.map(item -> new ResolvedItemDefinition(target, item)).forEach(result::add);
		}
		return result;
	}

	List<Definitions> visibleModels(Definitions source) {
		List<Definitions> result = new ArrayList<>();
		result.add(source);
		for (Import imported : source.getImportsList()) {
			List<Definitions> matches = modelsByNamespace.getOrDefault(imported.getNamespace(), List.of());
			if (matches.size() == 1 && !result.contains(matches.getFirst())) {
				result.add(matches.getFirst());
			}
		}
		return List.copyOf(result);
	}

	private List<Definitions> targetModels(Definitions source, String qualifier) {
		if (qualifier.isBlank() || qualifier.equals(source.getNamespace())) {
			return List.of(source);
		}
		boolean imported = source.getImportsList().stream()
				.anyMatch(value -> value.getNamespace().equals(qualifier) || value.getName().equals(qualifier));
		if (!imported) {
			return List.of();
		}
		String namespace = source.getImportsList().stream()
				.filter(value -> value.getNamespace().equals(qualifier) || value.getName().equals(qualifier))
				.map(Import::getNamespace).findFirst().orElse(qualifier);
		return List.copyOf(modelsByNamespace.getOrDefault(namespace, List.of()));
	}

	private static DmnSemanticDiagnostic diagnostic(String code, String path, String message, Import imported) {
		return new DmnSemanticDiagnostic(code, path, message, imported.getNode().getSourceLocation());
	}

	private static String nodeId(DrgElement element) {
		return switch (element.getElementCase()) {
			case INPUT_DATA -> element.getInputData().getNode().getId();
			case DECISION -> element.getDecision().getNode().getId();
			case BUSINESS_KNOWLEDGE_MODEL -> element.getBusinessKnowledgeModel().getNode().getId();
			case KNOWLEDGE_SOURCE -> element.getKnowledgeSource().getNode().getId();
			case DECISION_SERVICE -> element.getDecisionService().getNode().getId();
			case ELEMENT_NOT_SET -> "";
		};
	}

	record ResolvedDrgElement(Definitions model, DrgElement element) {
	}
	record ResolvedItemDefinition(Definitions model, ItemDefinition item) {
	}

	private record ReferenceParts(String qualifier, String id) {
		private static ReferenceParts parse(String href) {
			int hash = href.lastIndexOf('#');
			return hash < 0
					? new ReferenceParts("", href)
					: new ReferenceParts(href.substring(0, hash), href.substring(hash + 1));
		}
	}
}

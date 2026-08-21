package io.finmsg.dmn.ir;

import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.semantic.analysis.DmnSemanticPipelineResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic source-address and named-type index for a linked semantic model
 * set.
 */
final class RuntimeModelIndex {
	private final List<ModelView> models;
	private final int valueCount;

	private RuntimeModelIndex(List<ModelView> models, int valueCount) {
		this.models = List.copyOf(models);
		this.valueCount = valueCount;
	}

	static RuntimeModelIndex create(List<DmnSemanticPipelineResult> analyses) {
		Objects.requireNonNull(analyses, "analyses");
		if (analyses.isEmpty()) {
			throw new IllegalArgumentException("At least one semantic model is required.");
		}
		Set<String> namespaces = new HashSet<>();
		for (DmnSemanticPipelineResult analysis : analyses) {
			Objects.requireNonNull(analysis, "analysis");
			if (!analysis.isSuccess()) {
				throw new RuntimeIrLoweringException("Runtime IR requires successful semantic analysis; found "
						+ analysis.diagnostics().size() + " diagnostic(s).");
			}
			if (!namespaces.add(analysis.model().getNamespace())) {
				throw new RuntimeIrLoweringException(
						"Runtime model-set contains duplicate namespace '" + analysis.model().getNamespace() + "'.");
			}
		}

		Map<String, Integer> ids = new HashMap<>();
		Map<String, Integer> slots = new HashMap<>();
		int nextId = 0;
		for (DmnSemanticPipelineResult analysis : analyses) {
			String namespace = analysis.model().getNamespace();
			for (DrgElement element : analysis.model().getDrgElementsList()) {
				Node node = executableNode(element);
				if (node == null) {
					continue;
				}
				if (!node.getId().isBlank()) {
					putAddress(ids, namespace, node.getId(), nextId);
					putAddress(slots, namespace, node.getId(), nextId);
				}
				if (element.hasDecision() && element.getDecision().hasLogic()
						&& element.getDecision().getLogic().hasDecisionTable()
						&& !element.getDecision().getLogic().getDecisionTable().getNode().getId().isBlank()) {
					putAddress(slots, namespace, element.getDecision().getLogic().getDecisionTable().getNode().getId(),
							nextId);
				}
				nextId++;
			}
		}

		List<ModelView> views = new ArrayList<>();
		for (DmnSemanticPipelineResult analysis : analyses) {
			Definitions model = analysis.model();
			views.add(new ModelView(analysis, itemTypes(model, analyses), localAddresses(ids, model.getNamespace()),
					localAddresses(slots, model.getNamespace())));
		}
		return new RuntimeModelIndex(views, nextId);
	}

	List<ModelView> models() {
		return models;
	}

	int valueCount() {
		return valueCount;
	}

	private static void putAddress(Map<String, Integer> addresses, String namespace, String sourceId, int runtimeId) {
		String key = namespace + "#" + sourceId;
		if (addresses.putIfAbsent(key, runtimeId) != null) {
			throw new RuntimeIrLoweringException("Duplicate runtime source address '" + key + "'.");
		}
	}

	private static Map<String, Integer> localAddresses(Map<String, Integer> global, String namespace) {
		Map<String, Integer> result = new HashMap<>(global);
		String prefix = namespace + "#";
		global.forEach((key, value) -> {
			if (key.startsWith(prefix)) {
				result.put(key.substring(prefix.length()), value);
			}
		});
		return Map.copyOf(result);
	}

	private static Map<String, ItemDefinition> itemTypes(Definitions local, List<DmnSemanticPipelineResult> analyses) {
		Map<String, ItemDefinition> result = new LinkedHashMap<>();
		for (DmnSemanticPipelineResult analysis : analyses) {
			String namespace = analysis.model().getNamespace();
			analysis.model().getItemDefinitionsList().forEach(item -> {
				result.put(namespace + "#" + item.getNode().getName(), item);
				result.putIfAbsent(item.getNode().getName(), item);
			});
		}
		local.getItemDefinitionsList().forEach(item -> result.put(item.getNode().getName(), item));
		for (io.finmsg.dmn.model.Import imported : local.getImportsList()) {
			if (!imported.getName().isBlank()) {
				for (DmnSemanticPipelineResult analysis : analyses) {
					if (analysis.model().getNamespace().equals(imported.getNamespace())) {
						analysis.model().getItemDefinitionsList().forEach(item -> {
							result.put(imported.getName() + "." + item.getNode().getName(), item);
						});
					}
				}
			}
		}
		return Map.copyOf(result);
	}

	private static Node executableNode(DrgElement element) {
		return switch (element.getElementCase()) {
			case INPUT_DATA -> element.getInputData().getNode();
			case DECISION -> element.getDecision().getNode();
			case BUSINESS_KNOWLEDGE_MODEL -> element.getBusinessKnowledgeModel().getNode();
			case DECISION_SERVICE -> element.getDecisionService().getNode();
			default -> null;
		};
	}

	record ModelView(DmnSemanticPipelineResult analysis, Map<String, ItemDefinition> itemTypes,
			Map<String, Integer> runtimeIds, Map<String, Integer> valueSlots) {
	}
}

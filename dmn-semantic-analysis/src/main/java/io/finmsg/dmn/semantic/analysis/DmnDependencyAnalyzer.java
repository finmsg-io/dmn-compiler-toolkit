package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.BusinessKnowledgeModel;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.InformationRequirement;
import io.finmsg.dmn.model.KnowledgeRequirement;
import io.finmsg.dmn.model.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds the executable DRG dependency graph and detects cycles. */
public final class DmnDependencyAnalyzer implements DmnSemanticPass<DmnDependencyAnalysisResult> {

	@Override
	public DmnDependencyAnalysisResult analyze(Definitions model) {
		return analyze(model, new DmnModelRepository(List.of(model)));
	}

	public DmnDependencyAnalysisResult analyze(Definitions model, DmnModelRepository repository) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(repository, "repository");
		Session session = new Session(model, repository);
		return session.analyze();
	}

	private static final class Session {

		private final Definitions model;
		private final DmnModelRepository repository;
		private final List<DmnSemanticDiagnostic> diagnostics = new ArrayList<>();
		private final List<GraphNode> nodes = new ArrayList<>();
		private final Map<String, GraphNode> nodesByKey = new LinkedHashMap<>();
		private final Map<GraphNode, VisitState> states = new HashMap<>();
		private final List<GraphNode> stack = new ArrayList<>();
		private final List<GraphNode> order = new ArrayList<>();
		private final Set<String> reportedCycles = new HashSet<>();

		private Session(Definitions model, DmnModelRepository repository) {
			this.model = model;
			this.repository = repository;
		}

		private DmnDependencyAnalysisResult analyze() {
			collectNodes();
			collectEdges();
			for (GraphNode node : nodes) {
				if (states.get(node) == null) {
					visit(node);
				}
			}
			List<DrgElement> compilationOrder = reportedCycles.isEmpty()
					? order.stream().map(node -> node.element).toList()
					: List.of();
			return new DmnDependencyAnalysisResult(model, compilationOrder, diagnostics);
		}

		private void collectNodes() {
			for (Definitions visible : repository.visibleModels(model)) {
				for (int i = 0; i < visible.getDrgElementsCount(); i++) {
					DrgElement element = visible.getDrgElements(i);
					GraphNode node = switch (element.getElementCase()) {
						case DECISION ->
							graphNode(visible, element, element.getDecision().getNode(), NodeKind.DECISION, i);
						case BUSINESS_KNOWLEDGE_MODEL ->
							graphNode(visible, element, element.getBusinessKnowledgeModel().getNode(), NodeKind.BKM, i);
						default -> null;
					};
					if (node == null) {
						continue;
					}
					nodes.add(node);
					if (!node.id.isBlank()) {
						nodesByKey.put(node.key, node);
					}
				}
			}
		}

		private static GraphNode graphNode(Definitions model, DrgElement element, Node node, NodeKind kind,
				int modelIndex) {
			String name = node.getName().isBlank() ? node.getId() : node.getName();
			return new GraphNode(model.getNamespace() + "#" + node.getId(), node.getId(), name, kind, model, element,
					modelIndex);
		}

		private void collectEdges() {
			for (GraphNode node : nodes) {
				Set<String> seen = new LinkedHashSet<>();
				if (node.kind == NodeKind.DECISION) {
					collectDecisionEdges(node, node.element.getDecision(), seen);
				} else {
					collectBkmEdges(node, node.element.getBusinessKnowledgeModel(), seen);
				}
			}
		}

		private void collectDecisionEdges(GraphNode owner, Decision decision, Set<String> seen) {
			for (int i = 0; i < decision.getInformationRequirementsCount(); i++) {
				InformationRequirement requirement = decision.getInformationRequirements(i);
				switch (requirement.getRequiredCase()) {
					case INPUT -> registerRequirement(owner, seen, "input:", requirement.getInput().getHref(),
							owner.path() + "/informationRequirement[" + i + "]");
					case DECISION -> {
						String href = requirement.getDecision().getHref();
						registerRequirement(owner, seen, "decision:", href,
								owner.path() + "/informationRequirement[" + i + "]");
						addDependency(owner, href, NodeKind.DECISION);
					}
					case REQUIRED_NOT_SET -> {
					}
				}
			}
			for (int i = 0; i < decision.getKnowledgeRequirementsCount(); i++) {
				String href = decision.getKnowledgeRequirements(i).getRequiredKnowledge().getHref();
				registerRequirement(owner, seen, "knowledge:", href, owner.path() + "/knowledgeRequirement[" + i + "]");
				addDependency(owner, href, NodeKind.BKM);
			}
		}

		private void collectBkmEdges(GraphNode owner, BusinessKnowledgeModel bkm, Set<String> seen) {
			for (int i = 0; i < bkm.getKnowledgeRequirementsCount(); i++) {
				KnowledgeRequirement requirement = bkm.getKnowledgeRequirements(i);
				String href = requirement.getRequiredKnowledge().getHref();
				registerRequirement(owner, seen, "knowledge:", href, owner.path() + "/knowledgeRequirement[" + i + "]");
				addDependency(owner, href, NodeKind.BKM);
			}
		}

		private void registerRequirement(GraphNode owner, Set<String> seen, String category, String href, String path) {
			String target = referenceId(href);
			if (!target.isBlank() && !seen.add(category + target)) {
				diagnostics.add(new DmnSemanticDiagnostic("DUPLICATE_REQUIREMENT", path,
						"Element '" + owner.name + "' requires '" + href + "' more than once.",
						owner.node().getSourceLocation()));
			}
		}

		private void addDependency(GraphNode owner, String href, NodeKind expected) {
			List<DmnModelRepository.ResolvedDrgElement> matches = repository.resolveDrg(owner.model, href);
			if (matches.size() != 1) {
				return;
			}
			DmnModelRepository.ResolvedDrgElement target = matches.getFirst();
			String targetId = referenceId(href);
			GraphNode dependency = nodesByKey.get(target.model().getNamespace() + "#" + targetId);
			if (dependency == null || dependency.kind != expected) {
				return;
			}
			owner.dependencies.add(dependency);
		}

		private void visit(GraphNode node) {
			states.put(node, VisitState.VISITING);
			stack.add(node);
			for (GraphNode dependency : node.dependencies) {
				VisitState state = states.get(dependency);
				if (state == null) {
					visit(dependency);
				} else if (state == VisitState.VISITING) {
					reportCycle(dependency);
				}
			}
			stack.removeLast();
			states.put(node, VisitState.VISITED);
			order.add(node);
		}

		private void reportCycle(GraphNode first) {
			int start = stack.indexOf(first);
			List<GraphNode> cycle = new ArrayList<>(stack.subList(start, stack.size()));
			cycle.add(first);
			Set<String> members = new java.util.TreeSet<>();
			cycle.subList(0, cycle.size() - 1).forEach(node -> members.add(node.key));
			String signature = String.join("|", members);
			if (!reportedCycles.add(signature)) {
				return;
			}
			String description = cycle.stream().map(node -> node.name)
					.collect(java.util.stream.Collectors.joining(" -> "));
			diagnostics.add(new DmnSemanticDiagnostic("CYCLIC_DEPENDENCY", first.path(),
					"Cyclic DMN dependency: " + description + ".", first.node().getSourceLocation()));
		}

		private static String referenceId(String href) {
			int hash = href.lastIndexOf('#');
			return hash >= 0 ? href.substring(hash + 1) : href;
		}
	}

	private enum NodeKind {
		DECISION, BKM
	}

	private enum VisitState {
		VISITING, VISITED
	}

	private static final class GraphNode {
		private final String key;
		private final String id;
		private final String name;
		private final NodeKind kind;
		private final Definitions model;
		private final DrgElement element;
		private final int modelIndex;
		private final Set<GraphNode> dependencies = new LinkedHashSet<>();

		private GraphNode(String key, String id, String name, NodeKind kind, Definitions model, DrgElement element,
				int modelIndex) {
			this.key = key;
			this.id = id;
			this.name = name;
			this.kind = kind;
			this.model = model;
			this.element = element;
			this.modelIndex = modelIndex;
		}

		private Node node() {
			return kind == NodeKind.DECISION
					? element.getDecision().getNode()
					: element.getBusinessKnowledgeModel().getNode();
		}

		private String path() {
			String kindName = kind == NodeKind.DECISION ? "decision" : "businessKnowledgeModel";
			return "definitions/" + kindName + "[" + (name.isBlank() ? modelIndex : name) + "]";
		}
	}
}

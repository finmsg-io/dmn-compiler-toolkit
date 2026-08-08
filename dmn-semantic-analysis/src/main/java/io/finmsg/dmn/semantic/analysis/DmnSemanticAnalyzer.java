package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * First semantic-analysis pass: declaration collection and FEEL name
 * resolution.
 */
public final class DmnSemanticAnalyzer implements DmnSemanticPass<DmnSemanticAnalysisResult> {

	private static final Set<String> BUILTIN_NAMES = Set.of("date", "time", "date and time", "duration",
			"years and months duration", "days and time duration", "string", "number", "boolean", "context", "list",
			"range", "any", "null");

	@Override
	public DmnSemanticAnalysisResult analyze(Definitions parsedModel) {
		return analyze(parsedModel, new DmnModelRepository(List.of(parsedModel)));
	}

	public DmnSemanticAnalysisResult analyze(Definitions parsedModel, DmnModelRepository repository) {
		Objects.requireNonNull(parsedModel, "parsedModel");
		Objects.requireNonNull(repository, "repository");
		Session session = new Session(parsedModel, repository);
		session.analyze();
		return new DmnSemanticAnalysisResult(parsedModel, session.diagnostics, session.bindings);
	}

	private static final class Session {

		private final Definitions model;
		private final DmnModelRepository repository;
		private final List<DmnSemanticDiagnostic> diagnostics = new ArrayList<>();
		private final List<DmnSymbolBinding> bindings = new ArrayList<>();
		private final Map<String, List<Symbol>> globalsByName = new LinkedHashMap<>();
		private final Map<String, List<Symbol>> globalsById = new HashMap<>();
		private final Map<String, ItemDefinition> itemDefinitions = new LinkedHashMap<>();

		private Session(Definitions model, DmnModelRepository repository) {
			this.model = model;
			this.repository = repository;
		}

		private void analyze() {
			collectItemDefinitions();
			validateItemDefinitions();
			collectGlobalSymbols();
			analyzeTypeConstraints();

			for (int i = 0; i < model.getDrgElementsCount(); i++) {
				DrgElement element = model.getDrgElements(i);
				switch (element.getElementCase()) {
					case INPUT_DATA -> analyzeInputData(element.getInputData(), i);
					case DECISION -> analyzeDecision(element.getDecision(), i);
					case BUSINESS_KNOWLEDGE_MODEL -> analyzeBkm(element.getBusinessKnowledgeModel(), i);
					case DECISION_SERVICE -> analyzeDecisionService(element.getDecisionService(), i);
					case KNOWLEDGE_SOURCE, ELEMENT_NOT_SET -> {
					}
				}
			}
		}

		private void analyzeInputData(InputData input, int index) {
			String path = "definitions/inputData[" + displayName(input.getNode().getName(), index) + "]";
			validateTypeReference(input.getVariable().getType(), path + "/variable/type",
					input.getNode().getSourceLocation());
		}

		private void collectItemDefinitions() {
			for (int i = 0; i < model.getItemDefinitionsCount(); i++) {
				ItemDefinition item = model.getItemDefinitions(i);
				String name = item.getNode().getName();
				String path = "definitions/itemDefinition[" + displayName(name, i) + "]";
				if (!name.isBlank() && itemDefinitions.putIfAbsent(name, item) != null) {
					error("DUPLICATE_TYPE", path, "Duplicate item definition '" + name + "'.",
							item.getNode().getSourceLocation());
				}
			}
		}

		private void validateItemDefinitions() {
			for (int i = 0; i < model.getItemDefinitionsCount(); i++) {
				ItemDefinition item = model.getItemDefinitions(i);
				String path = "definitions/itemDefinition[" + displayName(item.getNode().getName(), i) + "]";
				validateTypeReference(item.getType(), path + "/type", item.getNode().getSourceLocation());
				Set<String> componentNames = new HashSet<>();
				for (int j = 0; j < item.getComponentsCount(); j++) {
					ItemComponent component = item.getComponents(j);
					String componentName = component.getNode().getName();
					String componentPath = path + "/component[" + displayName(componentName, j) + "]";
					if (!componentName.isBlank() && !componentNames.add(componentName)) {
						error("DUPLICATE_COMPONENT", componentPath, "Duplicate component '" + componentName
								+ "' in item definition '" + item.getNode().getName() + "'.",
								component.getNode().getSourceLocation());
					}
					validateTypeReference(component.getType(), componentPath + "/type",
							component.getNode().getSourceLocation());
				}
			}
			Map<String, Integer> states = new HashMap<>();
			List<String> stack = new ArrayList<>();
			Set<String> reported = new HashSet<>();
			itemDefinitions.keySet().forEach(name -> detectTypeCycle(name, states, stack, reported));
		}

		private void validateTypeReference(TypeReference type, String path, SourceLocation location) {
			switch (type.getKindCase()) {
				case NAMED -> {
					String name = type.getNamed().getName();
					if (!name.isBlank() && !BUILTIN_NAMES.contains(name)) {
						List<DmnModelRepository.ResolvedItemDefinition> matches = repository.resolveType(model,
								type.getNamed());
						if (matches.isEmpty()) {
							error("UNKNOWN_TYPE", path, "Unknown type '" + name + "'.", location);
						} else if (matches.size() > 1) {
							error("AMBIGUOUS_TYPE", path, "Type '" + name + "' is ambiguous.", location);
						} else {
							DmnModelRepository.ResolvedItemDefinition resolved = matches.getFirst();
							ItemDefinition item = resolved.item();
							bindings.add(new DmnSymbolBinding(path, "definitions/itemDefinition[" + name + "]", name,
									item.getNode().getId(), DmnSymbolKind.ITEM_DEFINITION, type,
									resolved.model().getNamespace()));
						}
					}
				}
				case LIST -> validateTypeReference(type.getList().getElementType(), path + "/elementType", location);
				case RANGE -> validateTypeReference(type.getRange().getElementType(), path + "/elementType", location);
				case FUNCTION -> {
					for (int i = 0; i < type.getFunction().getParameterTypeCount(); i++) {
						validateTypeReference(type.getFunction().getParameterType(i),
								path + "/parameterType[" + i + "]", location);
					}
					validateTypeReference(type.getFunction().getReturnType(), path + "/returnType", location);
				}
				case CONTEXT -> {
					for (int i = 0; i < type.getContext().getEntriesCount(); i++) {
						validateTypeReference(type.getContext().getEntries(i).getType(), path + "/entry[" + i + "]",
								location);
					}
				}
				case BUILTIN, KIND_NOT_SET -> {
				}
			}
		}

		private void detectTypeCycle(String name, Map<String, Integer> states, List<String> stack,
				Set<String> reported) {
			if (states.getOrDefault(name, 0) == 2) {
				return;
			}
			if (states.getOrDefault(name, 0) == 1) {
				int start = stack.indexOf(name);
				List<String> cycle = new ArrayList<>(stack.subList(start, stack.size()));
				cycle.add(name);
				String key = String.join("->", cycle);
				if (reported.add(key)) {
					ItemDefinition item = itemDefinitions.get(name);
					error("CYCLIC_TYPE_DEFINITION", "definitions/itemDefinition[" + name + "]/type",
							"Cyclic item-definition types: " + String.join(" -> ", cycle) + ".",
							item.getNode().getSourceLocation());
				}
				return;
			}
			states.put(name, 1);
			stack.add(name);
			ItemDefinition item = itemDefinitions.get(name);
			Set<String> dependencies = new LinkedHashSet<>();
			collectLocalTypeNames(item.getType(), dependencies);
			item.getComponentsList().forEach(component -> collectLocalTypeNames(component.getType(), dependencies));
			dependencies.stream().filter(itemDefinitions::containsKey)
					.forEach(dependency -> detectTypeCycle(dependency, states, stack, reported));
			stack.removeLast();
			states.put(name, 2);
		}

		private void collectLocalTypeNames(TypeReference type, Set<String> names) {
			switch (type.getKindCase()) {
				case NAMED -> {
					if (type.getNamed().getNamespace().isBlank()) {
						names.add(type.getNamed().getName());
					}
				}
				case LIST -> collectLocalTypeNames(type.getList().getElementType(), names);
				case RANGE -> collectLocalTypeNames(type.getRange().getElementType(), names);
				case FUNCTION -> {
					type.getFunction().getParameterTypeList()
							.forEach(parameter -> collectLocalTypeNames(parameter, names));
					collectLocalTypeNames(type.getFunction().getReturnType(), names);
				}
				case CONTEXT ->
					type.getContext().getEntriesList().forEach(entry -> collectLocalTypeNames(entry.getType(), names));
				case BUILTIN, KIND_NOT_SET -> {
				}
			}
		}

		private void collectGlobalSymbols() {
			for (int i = 0; i < model.getDrgElementsCount(); i++) {
				DrgElement element = model.getDrgElements(i);
				Symbol symbol = switch (element.getElementCase()) {
					case INPUT_DATA -> symbol(element.getInputData().getNode(),
							element.getInputData().getVariable().getType(), SymbolKind.INPUT_DATA);
					case DECISION -> symbol(element.getDecision().getNode(),
							element.getDecision().getVariable().getType(), SymbolKind.DECISION);
					case BUSINESS_KNOWLEDGE_MODEL -> symbol(element.getBusinessKnowledgeModel().getNode(),
							element.getBusinessKnowledgeModel().getVariable().getType(), SymbolKind.BKM,
							element.getBusinessKnowledgeModel().getFunction().getFormalParametersList().stream()
									.map(parameter -> parameter.getNode().getName()).filter(name -> !name.isBlank())
									.toList());
					case KNOWLEDGE_SOURCE -> symbol(element.getKnowledgeSource().getNode(),
							TypeReference.getDefaultInstance(), SymbolKind.KNOWLEDGE_SOURCE);
					case DECISION_SERVICE -> symbol(element.getDecisionService().getNode(),
							TypeReference.getDefaultInstance(), SymbolKind.DECISION_SERVICE);
					case ELEMENT_NOT_SET -> null;
				};
				if (symbol == null) {
					continue;
				}
				if (!symbol.id.isBlank()) {
					List<Symbol> sameId = globalsById.computeIfAbsent(symbol.id, ignored -> new ArrayList<>());
					sameId.add(symbol);
					if (sameId.size() == 2) {
						error("DUPLICATE_ID", "definitions/drgElement[" + symbol.id + "]",
								"Duplicate DRG element id '" + symbol.id + "'.", symbol.location);
					}
				}
				if (symbol.name.isBlank() || symbol.kind == SymbolKind.KNOWLEDGE_SOURCE
						|| symbol.kind == SymbolKind.DECISION_SERVICE) {
					continue;
				}
				List<Symbol> sameName = globalsByName.computeIfAbsent(symbol.name, ignored -> new ArrayList<>());
				sameName.add(symbol);
				if (sameName.size() == 2) {
					error("DUPLICATE_NAME", "definitions/drgElement[" + symbol.name + "]",
							"Duplicate global name '" + symbol.name + "'.", symbol.location);
				}
			}
		}

		private Symbol symbol(Node node, TypeReference type, SymbolKind kind) {
			return symbol(node, type, kind, List.of());
		}

		private Symbol symbol(Node node, TypeReference type, SymbolKind kind, List<String> parameters) {
			return new Symbol(node.getName(), node.getId(), type, kind, node.getSourceLocation(), parameters,
					"definitions/drgElement[" + displayName(node.getId().isBlank() ? node.getName() : node.getId(), 0)
							+ "]",
					model.getNamespace());
		}

		private void analyzeTypeConstraints() {
			Scope scope = new Scope(null);
			for (int i = 0; i < model.getItemDefinitionsCount(); i++) {
				ItemDefinition item = model.getItemDefinitions(i);
				String path = "definitions/itemDefinition[" + displayName(item.getNode().getName(), i) + "]";
				if (item.hasConstraint() && item.getConstraint().hasParsed()) {
					analyzeUnaryTests(item.getConstraint().getParsed().getTests(), scope, path + "/typeConstraint",
							item.getNode().getSourceLocation());
				}
				for (int j = 0; j < item.getComponentsCount(); j++) {
					ItemComponent component = item.getComponents(j);
					if (component.hasConstraint() && component.getConstraint().hasParsed()) {
						analyzeUnaryTests(
								component.getConstraint().getParsed().getTests(), scope, path + "/component["
										+ displayName(component.getNode().getName(), j) + "]/typeConstraint",
								component.getNode().getSourceLocation());
					}
				}
			}
		}

		private void analyzeDecision(Decision decision, int index) {
			String path = "definitions/decision[" + displayName(decision.getNode().getName(), index) + "]";
			validateTypeReference(decision.getVariable().getType(), path + "/variable/type",
					decision.getNode().getSourceLocation());
			Scope scope = new Scope(null);
			for (int i = 0; i < decision.getInformationRequirementsCount(); i++) {
				InformationRequirement requirement = decision.getInformationRequirements(i);
				switch (requirement.getRequiredCase()) {
					case INPUT -> addRequired(scope, requirement.getInput(), Set.of(SymbolKind.INPUT_DATA),
							path + "/informationRequirement[" + i + "]", decision.getNode().getSourceLocation());
					case DECISION -> addRequired(scope, requirement.getDecision(), Set.of(SymbolKind.DECISION),
							path + "/informationRequirement[" + i + "]", decision.getNode().getSourceLocation());
					case REQUIRED_NOT_SET -> error("MISSING_REFERENCE", path + "/informationRequirement[" + i + "]",
							"Information requirement has no target.", decision.getNode().getSourceLocation());
				}
			}
			for (int i = 0; i < decision.getKnowledgeRequirementsCount(); i++) {
				addRequired(scope, decision.getKnowledgeRequirements(i).getRequiredKnowledge(), Set.of(SymbolKind.BKM),
						path + "/knowledgeRequirement[" + i + "]", decision.getNode().getSourceLocation());
			}
			analyzeAuthorityRequirements(decision.getAuthorityRequirementsList(), path,
					decision.getNode().getSourceLocation());
			if (decision.hasLogic()) {
				analyzeDecisionLogic(decision.getLogic(), scope, path + "/logic",
						decision.getNode().getSourceLocation());
			}
		}

		private void analyzeBkm(BusinessKnowledgeModel bkm, int index) {
			String path = "definitions/businessKnowledgeModel[" + displayName(bkm.getNode().getName(), index) + "]";
			validateTypeReference(bkm.getVariable().getType(), path + "/variable/type",
					bkm.getNode().getSourceLocation());
			Scope scope = new Scope(null);
			for (int i = 0; i < bkm.getKnowledgeRequirementsCount(); i++) {
				addRequired(scope, bkm.getKnowledgeRequirements(i).getRequiredKnowledge(), Set.of(SymbolKind.BKM),
						path + "/knowledgeRequirement[" + i + "]", bkm.getNode().getSourceLocation());
			}
			analyzeAuthorityRequirements(bkm.getAuthorityRequirementsList(), path, bkm.getNode().getSourceLocation());
			if (!bkm.hasFunction()) {
				error("MISSING_BKM_FUNCTION", path + "/function",
						"A business knowledge model must define encapsulated logic.",
						bkm.getNode().getSourceLocation());
				return;
			}
			FunctionDefinition function = bkm.getFunction();
			if (!function.hasLogic()) {
				error("MISSING_BKM_LOGIC", path + "/function/logic", "A BKM function must define its logic.",
						bkm.getNode().getSourceLocation());
			}
			Scope functionScope = new Scope(scope);
			Set<String> parameterNames = new HashSet<>();
			for (int i = 0; i < function.getFormalParametersCount(); i++) {
				InformationItem parameter = function.getFormalParameters(i);
				String parameterPath = path + "/parameter[" + i + "]";
				String parameterName = parameter.getNode().getName();
				validateTypeReference(parameter.getType(), parameterPath + "/type",
						parameter.getNode().getSourceLocation());
				if (parameterName.isBlank()) {
					error("MISSING_PARAMETER_NAME", parameterPath, "A BKM formal parameter must have a name.",
							parameter.getNode().getSourceLocation());
				} else if (!parameterNames.add(parameterName)) {
					error("DUPLICATE_PARAMETER_NAME", parameterPath,
							"Duplicate BKM formal parameter '" + parameterName + "'.",
							parameter.getNode().getSourceLocation());
				} else {
					define(functionScope, parameterName, parameter.getType(), SymbolKind.PARAMETER, parameterPath,
							parameter.getNode().getSourceLocation());
				}
			}
			validateDeclaredBkmFunctionType(bkm, function, path);
			if (function.hasLogic() && function.getLogic().hasParsed()) {
				analyzeExpression(function.getLogic().getParsed().getAst(), functionScope, path + "/logic",
						bkm.getNode().getSourceLocation());
			}
		}

		private void validateDeclaredBkmFunctionType(BusinessKnowledgeModel bkm, FunctionDefinition function,
				String path) {
			TypeReference declared = bkm.getVariable().getType();
			if (!declared.hasFunction()) {
				return;
			}
			FunctionTypeReference functionType = declared.getFunction();
			if (functionType.getParameterTypeCount() != function.getFormalParametersCount()) {
				error("BKM_SIGNATURE_PARAMETER_COUNT_MISMATCH", path + "/variable/type",
						"Declared function type has " + functionType.getParameterTypeCount()
								+ " parameters but the BKM defines " + function.getFormalParametersCount() + ".",
						bkm.getNode().getSourceLocation());
				return;
			}
			for (int i = 0; i < function.getFormalParametersCount(); i++) {
				TypeReference declaredParameter = functionType.getParameterType(i);
				TypeReference formalParameter = function.getFormalParameters(i).getType();
				if (!declaredParameter.equals(formalParameter)) {
					error("BKM_SIGNATURE_PARAMETER_TYPE_MISMATCH", path + "/parameter[" + i + "]/type",
							"Formal parameter type does not match the BKM's declared function type.",
							function.getFormalParameters(i).getNode().getSourceLocation());
				}
			}
		}

		private void addRequired(Scope scope, ElementReference reference, Set<SymbolKind> expected, String path,
				SourceLocation location) {
			Symbol symbol = resolveReference(reference, expected, path, location);
			if (symbol != null) {
				scope.define(symbol);
			}
		}

		private void analyzeAuthorityRequirements(List<AuthorityRequirement> requirements, String parentPath,
				SourceLocation location) {
			for (int i = 0; i < requirements.size(); i++) {
				AuthorityRequirement requirement = requirements.get(i);
				String path = parentPath + "/authorityRequirement[" + i + "]";
				resolveReference(requirement.getRequiredAuthority(), Set.of(SymbolKind.KNOWLEDGE_SOURCE),
						path + "/requiredAuthority", location);
				switch (requirement.getSourceCase()) {
					case DECISION -> resolveReference(requirement.getDecision(), Set.of(SymbolKind.DECISION),
							path + "/decision", location);
					case INPUT -> resolveReference(requirement.getInput(), Set.of(SymbolKind.INPUT_DATA),
							path + "/input", location);
					case SOURCE_NOT_SET -> {
					}
				}
			}
		}

		private void analyzeDecisionService(DecisionService service, int index) {
			String path = "definitions/decisionService[" + displayName(service.getNode().getName(), index) + "]";
			if (service.getOutputDecisionsCount() == 0) {
				error("MISSING_OUTPUT_DECISION", path + "/outputDecision",
						"A decision service must expose at least one output decision.",
						service.getNode().getSourceLocation());
			}
			validateReferences(service.getOutputDecisionsList(), Set.of(SymbolKind.DECISION), path + "/outputDecision",
					service.getNode().getSourceLocation());
			validateReferences(service.getEncapsulatedDecisionsList(), Set.of(SymbolKind.DECISION),
					path + "/encapsulatedDecision", service.getNode().getSourceLocation());
			validateReferences(service.getInputDecisionsList(), Set.of(SymbolKind.DECISION), path + "/inputDecision",
					service.getNode().getSourceLocation());
			validateReferences(service.getInputDataList(), Set.of(SymbolKind.INPUT_DATA), path + "/inputData",
					service.getNode().getSourceLocation());
			validateUniqueReferences(service.getOutputDecisionsList(), path + "/outputDecision",
					service.getNode().getSourceLocation());
			validateUniqueReferences(service.getEncapsulatedDecisionsList(), path + "/encapsulatedDecision",
					service.getNode().getSourceLocation());
			validateUniqueReferences(service.getInputDecisionsList(), path + "/inputDecision",
					service.getNode().getSourceLocation());
			validateUniqueReferences(service.getInputDataList(), path + "/inputData",
					service.getNode().getSourceLocation());
			validateDecisionServiceRoles(service, path, service.getNode().getSourceLocation());
		}

		private void validateUniqueReferences(List<ElementReference> references, String path, SourceLocation location) {
			Set<String> seen = new HashSet<>();
			for (int i = 0; i < references.size(); i++) {
				String id = referenceId(references.get(i).getHref());
				if (!id.isBlank() && !seen.add(id)) {
					error("DUPLICATE_DECISION_SERVICE_REFERENCE", path + "[" + i + "]", "Decision-service reference '"
							+ references.get(i).getHref() + "' is listed more than once.", location);
				}
			}
		}

		private void validateDecisionServiceRoles(DecisionService service, String path, SourceLocation location) {
			Map<String, String> roles = new LinkedHashMap<>();
			registerDecisionServiceRoles(service.getOutputDecisionsList(), "outputDecision", path, location, roles);
			registerDecisionServiceRoles(service.getEncapsulatedDecisionsList(), "encapsulatedDecision", path, location,
					roles);
			registerDecisionServiceRoles(service.getInputDecisionsList(), "inputDecision", path, location, roles);
		}

		private void registerDecisionServiceRoles(List<ElementReference> references, String role, String path,
				SourceLocation location, Map<String, String> roles) {
			Set<String> seenInRole = new HashSet<>();
			for (int i = 0; i < references.size(); i++) {
				String id = referenceId(references.get(i).getHref());
				if (id.isBlank() || !seenInRole.add(id)) {
					continue;
				}
				String previous = roles.putIfAbsent(id, role);
				if (previous != null && !previous.equals(role)) {
					error("CONFLICTING_DECISION_SERVICE_ROLE", path + "/" + role + "[" + i + "]", "Decision '"
							+ references.get(i).getHref() + "' is listed as both " + previous + " and " + role + ".",
							location);
				}
			}
		}

		private void validateReferences(List<ElementReference> references, Set<SymbolKind> expected, String path,
				SourceLocation location) {
			for (int i = 0; i < references.size(); i++) {
				resolveReference(references.get(i), expected, path + "[" + i + "]", location);
			}
		}

		private Symbol resolveReference(ElementReference reference, Set<SymbolKind> expected, String path,
				SourceLocation location) {
			String href = reference.getHref();
			String id = referenceId(href);
			if (id.isBlank()) {
				error("MISSING_REFERENCE", path, "Reference target is empty.", location);
				return null;
			}
			List<Symbol> matches = repository.resolveDrg(model, href).stream().map(this::externalSymbol).toList();
			if (matches == null || matches.isEmpty()) {
				error("UNKNOWN_REFERENCE", path, "Reference '" + href + "' does not resolve to a DRG element.",
						location);
				return null;
			}
			if (matches.size() > 1) {
				error("AMBIGUOUS_REFERENCE", path, "Reference '" + href + "' resolves to multiple DRG elements.",
						location);
				return null;
			}
			Symbol symbol = matches.getFirst();
			if (!expected.contains(symbol.kind)) {
				error("INVALID_REFERENCE_KIND", path, "Reference '" + href + "' resolves to " + symbol.kind.displayName
						+ ", expected " + expectedKinds(expected) + ".", location);
				return null;
			}
			bind(path, symbol);
			return symbol;
		}

		private Symbol externalSymbol(DmnModelRepository.ResolvedDrgElement resolved) {
			DrgElement element = resolved.element();
			Node node;
			TypeReference type;
			SymbolKind kind;
			List<String> parameters = List.of();
			switch (element.getElementCase()) {
				case INPUT_DATA -> {
					node = element.getInputData().getNode();
					type = element.getInputData().getVariable().getType();
					kind = SymbolKind.INPUT_DATA;
				}
				case DECISION -> {
					node = element.getDecision().getNode();
					type = element.getDecision().getVariable().getType();
					kind = SymbolKind.DECISION;
				}
				case BUSINESS_KNOWLEDGE_MODEL -> {
					BusinessKnowledgeModel bkm = element.getBusinessKnowledgeModel();
					node = bkm.getNode();
					type = bkm.getVariable().getType();
					kind = SymbolKind.BKM;
					parameters = bkm.getFunction().getFormalParametersList().stream()
							.map(value -> value.getNode().getName()).filter(value -> !value.isBlank()).toList();
				}
				case KNOWLEDGE_SOURCE -> {
					node = element.getKnowledgeSource().getNode();
					type = TypeReference.getDefaultInstance();
					kind = SymbolKind.KNOWLEDGE_SOURCE;
				}
				case DECISION_SERVICE -> {
					node = element.getDecisionService().getNode();
					type = TypeReference.getDefaultInstance();
					kind = SymbolKind.DECISION_SERVICE;
				}
				case ELEMENT_NOT_SET -> throw new IllegalArgumentException("Empty DRG element");
				default ->
					throw new IllegalArgumentException("Unsupported DRG element kind: " + element.getElementCase());
			}
			type = qualifyExternalType(type, resolved.model().getNamespace());
			return new Symbol(node.getName(), node.getId(), type, kind, node.getSourceLocation(), parameters,
					"definitions/drgElement[" + node.getId() + "]", resolved.model().getNamespace());
		}

		private TypeReference qualifyExternalType(TypeReference type, String namespace) {
			return switch (type.getKindCase()) {
				case NAMED -> type.getNamed().getNamespace().isBlank()
						? type.toBuilder().setNamed(type.getNamed().toBuilder().setNamespace(namespace)).build()
						: type;
				case LIST -> type.toBuilder()
						.setList(type.getList().toBuilder()
								.setElementType(qualifyExternalType(type.getList().getElementType(), namespace)))
						.build();
				case RANGE -> type.toBuilder()
						.setRange(type.getRange().toBuilder()
								.setElementType(qualifyExternalType(type.getRange().getElementType(), namespace)))
						.build();
				case CONTEXT -> {
					ContextTypeReference.Builder context = type.getContext().toBuilder().clearEntries();
					type.getContext().getEntriesList().forEach(entry -> context
							.addEntries(entry.toBuilder().setType(qualifyExternalType(entry.getType(), namespace))));
					yield type.toBuilder().setContext(context).build();
				}
				case FUNCTION -> {
					FunctionTypeReference.Builder function = type.getFunction().toBuilder().clearParameterType();
					type.getFunction().getParameterTypeList()
							.forEach(parameter -> function.addParameterType(qualifyExternalType(parameter, namespace)));
					if (type.getFunction().hasReturnType()) {
						function.setReturnType(qualifyExternalType(type.getFunction().getReturnType(), namespace));
					}
					yield type.toBuilder().setFunction(function).build();
				}
				case BUILTIN, KIND_NOT_SET -> type;
			};
		}

		private static String expectedKinds(Set<SymbolKind> kinds) {
			return kinds.stream().map(kind -> kind.displayName).sorted()
					.collect(java.util.stream.Collectors.joining(" or "));
		}

		private void analyzeDecisionLogic(DecisionLogic logic, Scope scope, String path, SourceLocation location) {
			switch (logic.getTypeCase()) {
				case LITERAL_EXPRESSION ->
					analyzeFeel(logic.getLiteralExpression(), scope, path + "/literalExpression", location);
				case DECISION_TABLE ->
					analyzeDecisionTable(logic.getDecisionTable(), scope, path + "/decisionTable", location);
				case BOXED_EXPRESSION ->
					analyzeBoxed(logic.getBoxedExpression(), scope, path + "/boxedExpression", location);
				case INVOCATION -> analyzeInvocation(logic.getInvocation(), scope, path + "/invocation", location);
				case TYPE_NOT_SET -> {
				}
			}
		}

		private void analyzeDecisionTable(DecisionTable table, Scope scope, String path, SourceLocation location) {
			for (int i = 0; i < table.getInputsCount(); i++) {
				InputClause input = table.getInputs(i);
				validateTypeReference(input.getType(), path + "/input[" + i + "]/type", location);
				analyzeFeel(input.getInputExpression(), scope, path + "/input[" + i + "]", location);
				if (input.hasInputValues() && input.getInputValues().hasParsed()) {
					analyzeUnaryTests(input.getInputValues().getParsed().getAst().getUnaryTests(), scope,
							path + "/input[" + i + "]/inputValues", location);
				}
			}
			for (int i = 0; i < table.getOutputsCount(); i++) {
				OutputClause output = table.getOutputs(i);
				validateTypeReference(output.getType(), path + "/output[" + i + "]/type", location);
				if (output.hasOutputValues() && output.getOutputValues().hasParsed()) {
					analyzeUnaryTests(output.getOutputValues().getParsed().getAst().getUnaryTests(), scope,
							path + "/output[" + i + "]/outputValues", location);
				}
				if (output.hasDefaultOutputEntry() && output.getDefaultOutputEntry().hasParsed()) {
					analyzeExpressionParsed(output.getDefaultOutputEntry().getParsed(), scope,
							path + "/output[" + i + "]/defaultOutputEntry", location);
				}
			}
			for (int i = 0; i < table.getRulesCount(); i++) {
				DecisionRule rule = table.getRules(i);
				for (int j = 0; j < rule.getInputEntriesCount(); j++) {
					UnaryTest entry = rule.getInputEntries(j);
					if (entry.hasParsed()) {
						analyzeUnaryTests(entry.getParsed().getTests(), scope,
								path + "/rule[" + i + "]/inputEntry[" + j + "]", location);
					}
				}
				for (int j = 0; j < rule.getOutputEntriesCount(); j++) {
					analyzeFeel(rule.getOutputEntries(j), scope, path + "/rule[" + i + "]/outputEntry[" + j + "]",
							location);
				}
			}
		}

		private void analyzeInvocation(Invocation invocation, Scope scope, String path, SourceLocation location) {
			analyzeFeel(invocation.getExpression(), scope, path + "/expression", location);
			for (int i = 0; i < invocation.getBindingsCount(); i++) {
				analyzeFeel(invocation.getBindings(i).getExpression(), scope, path + "/binding[" + i + "]", location);
			}
			validateInvocationBindings(invocation, scope, path, location);
		}

		private void validateInvocationBindings(Invocation invocation, Scope scope, String path,
				SourceLocation location) {
			if (!invocation.getExpression().hasParsed() || !invocation.getExpression().getParsed().getAst().hasName()) {
				return;
			}
			String targetName = invocation.getExpression().getParsed().getAst().getName().getName();
			List<Symbol> targets = scope.resolve(targetName);
			if (targets == null || targets.size() != 1 || targets.getFirst().kind != SymbolKind.BKM) {
				return;
			}
			Set<String> expected = new LinkedHashSet<>(targets.getFirst().parameters);
			Set<String> seen = new HashSet<>();
			for (int i = 0; i < invocation.getBindingsCount(); i++) {
				String parameter = invocation.getBindings(i).getParameter();
				String bindingPath = path + "/binding[" + i + "]";
				if (!seen.add(parameter)) {
					error("DUPLICATE_INVOCATION_BINDING", bindingPath,
							"Invocation parameter '" + parameter + "' is bound more than once.", location);
				}
				if (!expected.contains(parameter)) {
					error("UNKNOWN_INVOCATION_PARAMETER", bindingPath,
							"BKM '" + targetName + "' has no parameter '" + parameter + "'.", location);
				}
			}
			expected.removeAll(seen);
			for (String missing : expected) {
				error("MISSING_INVOCATION_BINDING", path,
						"Invocation of BKM '" + targetName + "' is missing parameter '" + missing + "'.", location);
			}
		}

		private void analyzeFeel(Feel feel, Scope scope, String path, SourceLocation location) {
			if (feel.hasParsed()) {
				analyzeExpression(feel.getParsed().getAst(), scope, path, location);
			}
		}

		private void analyzeBoxed(BoxedExpression boxed, Scope scope, String path, SourceLocation location) {
			if (!boxed.hasParsed()) {
				return;
			}
			BoxedExpressionParsed parsed = boxed.getParsed();
			switch (parsed.getTypeCase()) {
				case CONTEXT -> analyzeContext(parsed.getContext(), scope, path + "/context", location);
				case RELATION -> {
					for (int i = 0; i < parsed.getRelation().getColumnsCount(); i++) {
						InformationItem variable = parsed.getRelation().getColumns(i).getVariable();
						validateTypeReference(variable.getType(), path + "/relation/column[" + i + "]/type",
								variable.getNode().getSourceLocation());
					}
					for (int i = 0; i < parsed.getRelation().getRowsCount(); i++) {
						RelationRowParsed row = parsed.getRelation().getRows(i);
						for (int j = 0; j < row.getExpressionsCount(); j++) {
							analyzeExpressionParsed(row.getExpressions(j), scope,
									path + "/relation/row[" + i + "]/cell[" + j + "]", location);
						}
					}
				}
				case LIST -> {
					for (int i = 0; i < parsed.getList().getElementsCount(); i++) {
						analyzeExpressionParsed(parsed.getList().getElements(i), scope,
								path + "/list/element[" + i + "]", location);
					}
				}
				case FUNCTION_DEFINITION -> {
					Scope functionScope = new Scope(scope);
					FunctionDefinitionParsed function = parsed.getFunctionDefinition();
					for (int i = 0; i < function.getParametersCount(); i++) {
						InformationItem parameter = function.getParameters(i);
						validateTypeReference(parameter.getType(), path + "/parameter[" + i + "]/type",
								parameter.getNode().getSourceLocation());
						define(functionScope, parameter.getNode().getName(), parameter.getType(), SymbolKind.PARAMETER,
								path + "/parameter[" + i + "]", location);
					}
					if (function.hasBody()) {
						analyzeExpressionParsed(function.getBody(), functionScope, path + "/body", location);
					}
				}
				case TYPE_NOT_SET -> {
				}
			}
		}

		private void analyzeContext(ContextParsed context, Scope parent, String path, SourceLocation location) {
			Scope scope = new Scope(parent);
			for (int i = 0; i < context.getEntriesCount(); i++) {
				ContextEntryParsed entry = context.getEntries(i);
				if (entry.hasExpression()) {
					analyzeExpressionParsed(entry.getExpression(), scope, path + "/entry[" + i + "]/expression",
							location);
				}
				if (entry.hasVariable()) {
					InformationItem variable = entry.getVariable();
					validateTypeReference(variable.getType(), path + "/entry[" + i + "]/variable/type",
							variable.getNode().getSourceLocation());
					define(scope, variable.getNode().getName(), variable.getType(), SymbolKind.LOCAL,
							path + "/entry[" + i + "]", variable.getNode().getSourceLocation());
				}
			}
		}

		private void analyzeExpressionParsed(ExpressionParsed parsed, Scope scope, String path,
				SourceLocation location) {
			switch (parsed.getTypeCase()) {
				case FEEL -> analyzeExpression(parsed.getFeel().getAst(), scope, path, location);
				case BOXED -> analyzeParsedBoxed(parsed.getBoxed(), scope, path, location);
				case TYPE_NOT_SET -> {
				}
			}
		}

		private void analyzeParsedBoxed(BoxedExpressionParsed parsed, Scope scope, String path,
				SourceLocation location) {
			BoxedExpression wrapper = BoxedExpression.newBuilder().setParsed(parsed).build();
			analyzeBoxed(wrapper, scope, path, location);
		}

		private TypeReference analyzeExpression(Expression expression, Scope scope, String path,
				SourceLocation location) {
			return switch (expression.getNodeCase()) {
				case LITERAL -> TypeReference.getDefaultInstance();
				case NAME -> resolveName(expression.getName().getName(), scope, path, location);
				case UNARY ->
					analyzeExpression(expression.getUnary().getExpression(), scope, path + "/unary", location);
				case BINARY -> {
					analyzeExpression(expression.getBinary().getLeft(), scope, path + "/left", location);
					analyzeExpression(expression.getBinary().getRight(), scope, path + "/right", location);
					yield TypeReference.getDefaultInstance();
				}
				case FUNCTION_CALL -> {
					for (int i = 0; i < expression.getFunctionCall().getArgumentsCount(); i++) {
						analyzeExpression(expression.getFunctionCall().getArguments(i), scope,
								path + "/argument[" + i + "]", location);
					}
					yield TypeReference.getDefaultInstance();
				}
				case IF_EXPRESSION -> {
					analyzeExpression(expression.getIfExpression().getCondition(), scope, path + "/condition",
							location);
					analyzeExpression(expression.getIfExpression().getThenExpression(), scope, path + "/then",
							location);
					analyzeExpression(expression.getIfExpression().getElseExpression(), scope, path + "/else",
							location);
					yield TypeReference.getDefaultInstance();
				}
				case CONTEXT -> analyzeFeelContext(expression.getContext(), scope, path, location);
				case LIST -> {
					for (int i = 0; i < expression.getList().getElementsCount(); i++) {
						analyzeExpression(expression.getList().getElements(i), scope, path + "/element[" + i + "]",
								location);
					}
					yield TypeReference.getDefaultInstance();
				}
				case FOR_EXPRESSION -> analyzeFor(expression.getForExpression(), scope, path, location);
				case QUANTIFIED -> analyzeQuantified(expression.getQuantified(), scope, path, location);
				case FILTER -> {
					analyzeExpression(expression.getFilter().getSource(), scope, path + "/source", location);
					analyzeExpression(expression.getFilter().getFilter(), scope, path + "/filter", location);
					yield TypeReference.getDefaultInstance();
				}
				case PATH -> analyzePath(expression.getPath(), scope, path, location);
				case DESCENDANT -> analyzeDescendant(expression.getDescendant(), scope, path, location);
				case INVOCATION -> analyzeAstInvocation(expression.getInvocation(), scope, path, location);
				case RANGE -> {
					if (expression.getRange().hasLower()) {
						analyzeExpression(expression.getRange().getLower(), scope, path + "/lower", location);
					}
					if (expression.getRange().hasUpper()) {
						analyzeExpression(expression.getRange().getUpper(), scope, path + "/upper", location);
					}
					yield TypeReference.getDefaultInstance();
				}
				case BETWEEN -> {
					analyzeExpression(expression.getBetween().getValue(), scope, path + "/value", location);
					analyzeExpression(expression.getBetween().getLower(), scope, path + "/lower", location);
					analyzeExpression(expression.getBetween().getUpper(), scope, path + "/upper", location);
					yield TypeReference.getDefaultInstance();
				}
				case IN -> {
					analyzeExpression(expression.getIn().getValue(), scope, path + "/value", location);
					analyzeUnaryTests(expression.getIn().getTests(), scope, path + "/tests", location);
					yield TypeReference.getDefaultInstance();
				}
				case INSTANCE_OF -> analyzeExpression(expression.getInstanceOf().getExpression(), scope,
						path + "/expression", location);
				case FUNCTION_DEFINITION ->
					analyzeAstFunction(expression.getFunctionDefinition(), scope, path, location);
				case UNARY_TESTS -> {
					analyzeUnaryTests(expression.getUnaryTests(), scope, path, location);
					yield TypeReference.getDefaultInstance();
				}
				case DECISION_TABLE, NODE_NOT_SET -> TypeReference.getDefaultInstance();
			};
		}

		private TypeReference analyzeFeelContext(ContextExpression context, Scope parent, String path,
				SourceLocation location) {
			Scope scope = new Scope(parent);
			for (int i = 0; i < context.getEntriesCount(); i++) {
				io.finmsg.dmn.model.ContextEntry entry = context.getEntries(i);
				TypeReference type = analyzeExpression(entry.getExpression(), scope, path + "/entry[" + i + "]",
						location);
				define(scope, entry.getName(), type, SymbolKind.LOCAL, path + "/entry[" + i + "]", location);
			}
			return TypeReference.getDefaultInstance();
		}

		private TypeReference analyzeFor(ForExpression value, Scope parent, String path, SourceLocation location) {
			Scope scope = new Scope(parent);
			if (value.getIterationsCount() == 0 && !value.getVariable().isBlank()) {
				TypeReference type = analyzeExpression(value.getIn(), scope, path + "/in", location);
				define(scope, value.getVariable(), type, SymbolKind.LOCAL, path, location);
				return analyzeExpression(value.getReturnExpression(), scope, path + "/return", location);
			}
			for (int i = 0; i < value.getIterationsCount(); i++) {
				IterationContext iteration = value.getIterations(i);
				TypeReference type = analyzeExpression(iteration.getStart(), scope, path + "/iteration[" + i + "]/in",
						location);
				if (iteration.hasEnd()) {
					analyzeExpression(iteration.getEnd(), scope, path + "/iteration[" + i + "]/end", location);
				}
				define(scope, iteration.getVariable(), type, SymbolKind.LOCAL, path + "/iteration[" + i + "]",
						location);
			}
			return analyzeExpression(value.getReturnExpression(), scope, path + "/return", location);
		}

		private TypeReference analyzeQuantified(QuantifiedExpression value, Scope parent, String path,
				SourceLocation location) {
			Scope scope = new Scope(parent);
			if (value.getBindingsCount() == 0 && !value.getVariable().isBlank()) {
				TypeReference type = analyzeExpression(value.getIn(), scope, path + "/in", location);
				define(scope, value.getVariable(), type, SymbolKind.LOCAL, path, location);
				analyzeExpression(value.getSatisfies(), scope, path + "/satisfies", location);
				return TypeReference.getDefaultInstance();
			}
			for (int i = 0; i < value.getBindingsCount(); i++) {
				IterationBinding binding = value.getBindings(i);
				TypeReference type = analyzeExpression(binding.getIn(), scope, path + "/binding[" + i + "]/in",
						location);
				define(scope, binding.getVariable(), type, SymbolKind.LOCAL, path + "/binding[" + i + "]", location);
			}
			analyzeExpression(value.getSatisfies(), scope, path + "/satisfies", location);
			return TypeReference.getDefaultInstance();
		}

		private TypeReference analyzePath(PathExpression value, Scope scope, String path, SourceLocation location) {
			TypeReference source = analyzeExpression(value.getSource(), scope, path + "/source", location);
			return resolveMember(source, value.getMember(), path, location);
		}

		private TypeReference analyzeDescendant(DescendantExpression value, Scope scope, String path,
				SourceLocation location) {
			TypeReference source = analyzeExpression(value.getSource(), scope, path + "/source", location);
			return resolveMember(source, value.getMember(), path, location);
		}

		private TypeReference analyzeAstInvocation(InvocationExpression value, Scope scope, String path,
				SourceLocation location) {
			if (!(value.getTarget().hasName() && BUILTIN_NAMES.contains(value.getTarget().getName().getName()))) {
				analyzeExpression(value.getTarget(), scope, path + "/target", location);
			}
			for (int i = 0; i < value.getArgumentsCount(); i++) {
				analyzeExpression(value.getArguments(i).getExpression(), scope, path + "/argument[" + i + "]",
						location);
			}
			for (int i = 0; i < value.getPositionalArgumentsCount(); i++) {
				analyzeExpression(value.getPositionalArguments(i), scope, path + "/argument[" + i + "]", location);
			}
			return TypeReference.getDefaultInstance();
		}

		private TypeReference analyzeAstFunction(FunctionDefinitionExpression value, Scope parent, String path,
				SourceLocation location) {
			Scope scope = new Scope(parent);
			for (int i = 0; i < value.getParametersCount(); i++) {
				define(scope, value.getParameters(i).getName(), TypeReference.getDefaultInstance(),
						SymbolKind.PARAMETER, path + "/parameter[" + i + "]", location);
			}
			return analyzeExpression(value.getBody(), scope, path + "/body", location);
		}

		private void analyzeUnaryTests(UnaryTestsExpression tests, Scope scope, String path, SourceLocation location) {
			for (int i = 0; i < tests.getTestsCount(); i++) {
				PositiveUnaryTest test = tests.getTests(i);
				switch (test.getTypeCase()) {
					case COMPARISON -> analyzeExpression(test.getComparison().getEndpoint(), scope,
							path + "/test[" + i + "]", location);
					case RANGE -> {
						if (test.getRange().hasLower()) {
							analyzeExpression(test.getRange().getLower(), scope, path + "/test[" + i + "]/lower",
									location);
						}
						if (test.getRange().hasUpper()) {
							analyzeExpression(test.getRange().getUpper(), scope, path + "/test[" + i + "]/upper",
									location);
						}
					}
					case EXPRESSION ->
						analyzeExpression(test.getExpression(), scope, path + "/test[" + i + "]", location);
					case TYPE_NOT_SET -> {
					}
				}
			}
		}

		private TypeReference resolveName(String name, Scope scope, String path, SourceLocation location) {
			List<Symbol> resolved = scope.resolve(name);
			if (resolved != null) {
				if (resolved.size() > 1) {
					error("AMBIGUOUS_NAME", path, "Name '" + name + "' is ambiguous.", location);
					return TypeReference.getDefaultInstance();
				}
				Symbol symbol = resolved.getFirst();
				bind(path, symbol);
				return symbol.type;
			}
			if (globalsByName.containsKey(name)) {
				error("UNAVAILABLE_NAME", path,
						"Name '" + name + "' exists but is not available through a requirement.", location);
			} else {
				error("UNKNOWN_NAME", path, "Unknown name '" + name + "'.", location);
			}
			return TypeReference.getDefaultInstance();
		}

		private TypeReference resolveMember(TypeReference source, String member, String path, SourceLocation location) {
			if (!source.hasNamed()) {
				return TypeReference.getDefaultInstance();
			}
			List<DmnModelRepository.ResolvedItemDefinition> resolvedTypes = repository.resolveType(model,
					source.getNamed());
			if (resolvedTypes.isEmpty()) {
				error("UNKNOWN_TYPE", path, "Unknown type '" + source.getNamed().getName() + "'.", location);
				return TypeReference.getDefaultInstance();
			}
			if (resolvedTypes.size() > 1) {
				error("AMBIGUOUS_TYPE", path, "Type '" + source.getNamed().getName() + "' is ambiguous.", location);
				return TypeReference.getDefaultInstance();
			}
			ItemDefinition item = resolvedTypes.getFirst().item();
			List<ItemComponent> matches = item.getComponentsList().stream()
					.filter(component -> component.getNode().getName().equals(member)).toList();
			if (matches.isEmpty()) {
				error("INVALID_PROPERTY", path,
						"Type '" + source.getNamed().getName() + "' has no property '" + member + "'.", location);
				return TypeReference.getDefaultInstance();
			}
			if (matches.size() > 1) {
				error("AMBIGUOUS_PROPERTY", path,
						"Property '" + member + "' is ambiguous on type '" + source.getNamed().getName() + "'.",
						location);
				return TypeReference.getDefaultInstance();
			}
			return matches.getFirst().getType();
		}

		private void define(Scope scope, String name, TypeReference type, SymbolKind kind, String path,
				SourceLocation location) {
			if (name.isBlank()) {
				return;
			}
			if (!scope.define(new Symbol(name, "", type, kind, location, List.of(), path, model.getNamespace()))) {
				error("DUPLICATE_NAME", path, "Duplicate name '" + name + "'.", location);
			}
		}

		private void bind(String referencePath, Symbol symbol) {
			bindings.add(new DmnSymbolBinding(referencePath, symbol.declarationPath, symbol.name, symbol.id,
					switch (symbol.kind) {
						case INPUT_DATA -> DmnSymbolKind.INPUT_DATA;
						case DECISION -> DmnSymbolKind.DECISION;
						case BKM -> DmnSymbolKind.BUSINESS_KNOWLEDGE_MODEL;
						case KNOWLEDGE_SOURCE -> DmnSymbolKind.KNOWLEDGE_SOURCE;
						case DECISION_SERVICE -> DmnSymbolKind.DECISION_SERVICE;
						case PARAMETER -> DmnSymbolKind.PARAMETER;
						case LOCAL -> DmnSymbolKind.LOCAL_VARIABLE;
					}, symbol.type, symbol.namespace));
		}

		private void error(String code, String path, String message, SourceLocation location) {
			diagnostics.add(new DmnSemanticDiagnostic(code, path, message, location));
		}

		private static String referenceId(String href) {
			int hash = href.lastIndexOf('#');
			return hash >= 0 ? href.substring(hash + 1) : href;
		}

		private static String displayName(String name, int index) {
			return name.isBlank() ? Integer.toString(index) : name;
		}
	}

	private enum SymbolKind {
		INPUT_DATA("input data"), DECISION("decision"), BKM("business knowledge model"), KNOWLEDGE_SOURCE(
				"knowledge source"), DECISION_SERVICE(
						"decision service"), PARAMETER("parameter"), LOCAL("local variable");

		private final String displayName;

		SymbolKind(String displayName) {
			this.displayName = displayName;
		}
	}

	private record Symbol(String name, String id, TypeReference type, SymbolKind kind, SourceLocation location,
			List<String> parameters, String declarationPath, String namespace) {

		private Symbol {
			parameters = List.copyOf(parameters);
		}
	}

	private static final class Scope {
		private final Scope parent;
		private final Map<String, List<Symbol>> symbols = new LinkedHashMap<>();

		private Scope(Scope parent) {
			this.parent = parent;
		}

		private boolean define(Symbol symbol) {
			List<Symbol> values = symbols.computeIfAbsent(symbol.name, ignored -> new ArrayList<>());
			values.add(symbol);
			return values.size() == 1;
		}

		private List<Symbol> resolve(String name) {
			List<Symbol> local = symbols.get(name);
			if (local != null) {
				return local;
			}
			return parent == null ? null : parent.resolve(name);
		}
	}
}

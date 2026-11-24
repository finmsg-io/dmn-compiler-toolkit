package io.finmsg.dmn.compiler.dmnir;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.model.*;
import io.finmsg.dmn.semantic.analysis.DmnSymbolBinding;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;

/** Retains source named identity before structural Runtime IR erases it. */
final class DmnIrRecordProducer {
	private final DmnModelSetSemanticResult semantic;
	private boolean filters;
	private boolean builtinCalls;
	private boolean closures;
	private boolean usedLocalProfile;
	private boolean usedCollectionProfile;
	private Set<String> captures;
	private Set<String> captureScope = Set.of();
	private final Map<String, DmnIrLocalBinding> localBindings = new HashMap<>();
	private final Map<String, DmnSemanticModel> models = new LinkedHashMap<>();
	private record TypeName(String namespace, String name) {
	}
	private final Map<TypeName, DmnIrReference> named = new HashMap<>();
	private final Map<TypeName, DmnIrTypeReference> typeAliases = new HashMap<>();
	private final Map<DmnIrReference, DmnIrTypeReference> typeAliasesByRef = new HashMap<>();
	private final Map<DmnIrReference, DmnIrTypeReference> declarations = new HashMap<>();
	private final Map<DmnIrReference, BusinessKnowledgeModel> bkms = new HashMap<>();
	private Set<DmnIrReference> knowledge = Set.of();
	private final Map<DmnIrReference, DmnIrRecordDefinition> records = new HashMap<>();
	private final Map<DmnIrReference, DmnIrCollectionDefinition> collections = new HashMap<>();
	private final Set<DmnIrReference> collectionTypes = new HashSet<>();
	private static final Comparator<DmnIrReference> ORDER = Comparator.comparing(DmnIrReference::getModelNamespace)
			.thenComparing(DmnIrReference::getElementId);

	DmnIrRecordProducer(DmnModelSetSemanticResult semantic) {
		this.semantic = semantic;
		semantic.models().stream().sorted(Comparator.comparing(m -> m.identity().namespace()))
				.forEach(m -> models.put(m.identity().namespace(), m));
	}

	static boolean containsBuiltinCall(com.google.protobuf.Message source) {
		return DmnIrDates.containsNode(source,
				node -> node instanceof InvocationExpression call && call.getTarget().hasName()
						&& (DmnIrBuiltinSignature.forFeelName(call.getTarget().getName().getName()).isPresent()
								|| call.getTarget().getName().getName().equals("is")));
	}

	DmnIrArtifactReader.VerifiedArtifact produce() {
		boolean fieldConstraintsProfile = models.values().stream()
				.flatMap(m -> m.model().getItemDefinitionsList().stream())
				.flatMap(item -> item.getComponentsList().stream()).anyMatch(ItemComponent::hasConstraint);
		boolean tableProfile = fieldConstraintsProfile
				|| models.values().stream().flatMap(m -> m.model().getDrgElementsList().stream())
						.anyMatch(e -> e.hasDecision() && e.getDecision().getLogic().hasDecisionTable());
		boolean dateProfile = tableProfile
				|| models.values().stream().anyMatch(m -> DmnIrDates.containsDate(m.model()));
		boolean localProfile = models.values().stream()
				.anyMatch(m -> m.model().getDrgElementsList().stream()
						.anyMatch(e -> e.hasDecision() && e.getDecision().getLogic().hasBoxedExpression())
						|| DmnIrDates.containsNode(m.model(), node -> node instanceof ContextExpression));
		usedCollectionProfile = models.values().stream().anyMatch(m -> DmnIrDates.containsNode(m.model(),
				node -> node instanceof ForExpression || node instanceof QuantifiedExpression));
		for (var model : models.values()) {
			for (var item : model.model().getItemDefinitionsList()) {
				String id = itemId(item);
				var reference = ref(model.identity().namespace(), id);
				named.put(new TypeName(model.identity().namespace(), item.getNode().getName()), reference);
				if (item.getIsCollection())
					collectionTypes.add(reference);
			}
		}
		for (var model : models.values()) {
			for (var item : model.model().getItemDefinitionsList()) {
				if (!item.getIsCollection() && item.getComponentsCount() == 0) {
					String id = itemId(item);
					var reference = ref(model.identity().namespace(), id);
					var typeName = new TypeName(model.identity().namespace(), item.getNode().getName());
					var resolved = resolveItemType(item, model, new HashSet<>());
					typeAliases.put(typeName, resolved);
					typeAliasesByRef.put(reference, resolved);
				}
			}
		}
		for (var model : models.values()) {
			for (var item : model.model().getItemDefinitionsList()) {
				if (item.getComponentsCount() > 0) {
					String id = itemId(item);
					String recId = item.getIsCollection() ? id + "$element" : id;
					String baseName = item.getNode().getName().isBlank() ? id : item.getNode().getName();
					String recName = item.getIsCollection() ? baseName + "$element" : baseName;
					var record = DmnIrRecordDefinition.newBuilder()
							.setIdentity(DmnIrElementIdentity.newBuilder().setId(recId).setName(recName));
					for (var component : item.getComponentsList()) {
						if (component.getIsCollection() || component.getComponentsCount() != 0)
							throw unsupported();
						var field = DmnIrRecordField.newBuilder().setName(component.getNode().getName())
								.setType(type(component.getType(), model));
						if (!component.getNode().getId().isBlank())
							field.setId(component.getNode().getId());
						if (component.hasConstraint()) {
							if (!field.getType().hasScalarType() || !component.getConstraint().hasParsed()
									|| field.getType().getScalarType() == DmnIrScalarType.SCALAR_TYPE_NULL)
								throw unsupported();
							String path = "definitions/itemDefinition[" + item.getNode().getName() + "]/component["
									+ component.getNode().getName() + "]/typeConstraint";
							field.setConstraint(tests(component.getConstraint().getParsed().getTests(), path,
									(value, endpointPath) -> constraintEndpoint(value, model, endpointPath)));
						}
						record.addFields(field);
					}
					records.put(ref(model.identity().namespace(), recId), record.build());
				}
			}
		}
		for (var model : models.values()) {
			for (var item : model.model().getItemDefinitionsList()) {
				if (item.getIsCollection()) {
					String id = itemId(item);
					var reference = ref(model.identity().namespace(), id);
					if (item.getComponentsCount() > 0) {
						String recId = id + "$element";
						collections.put(reference,
								DmnIrCollectionDefinition.newBuilder().setIdentity(identity(item.getNode(), id))
										.setRecordType(ref(model.identity().namespace(), recId)).build());
					} else {
						var elemType = type(item.getType(), model);
						var colBuilder = DmnIrCollectionDefinition.newBuilder()
								.setIdentity(identity(item.getNode(), id));
						if (elemType.hasRecordType()) {
							colBuilder.setRecordType(elemType.getRecordType());
						} else if (elemType.hasCollectionType()) {
							colBuilder.setCollectionType(elemType.getCollectionType());
						} else {
							var element = elemType.getScalarType();
							if (element == DmnIrScalarType.SCALAR_TYPE_NULL && !elemType.hasCollectionType())
								element = DmnIrScalarType.SCALAR_TYPE_STRING;
							colBuilder.setElementType(element);
						}
						collections.put(reference, colBuilder.build());
					}
				}
			}
		}
		for (var model : models.values())
			for (var element : model.model().getDrgElementsList()) {
				if (element.hasInputData())
					declarations.put(ref(model.identity().namespace(), element.getInputData().getNode().getId()),
							type(element.getInputData().getVariable().getType(), model));
				else if (element.hasDecision())
					declarations.put(ref(model.identity().namespace(), element.getDecision().getNode().getId()),
							type(element.getDecision().getVariable().getType(), model));
				else if (element.hasBusinessKnowledgeModel()) {
					var bkm = element.getBusinessKnowledgeModel();
					bkms.put(ref(model.identity().namespace(), bkm.getNode().getId()), bkm);
				} else if (element.hasKnowledgeSource() || element.hasDecisionService()) {
					continue;
				} else
					throw unsupported();
			}
		int minor = (!collections.isEmpty() || usedCollectionProfile)
				? 7
				: fieldConstraintsProfile
						? 6
						: tableProfile ? 5 : dateProfile ? 4 : (localProfile || usedLocalProfile) ? 3 : 2;
		if (closures)
			minor = 11;
		else if (!bkms.isEmpty())
			minor = 10;
		else if (builtinCalls)
			minor = 9;
		else if (filters)
			minor = 8;
		var artifact = DmnIrArtifact.newBuilder()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(minor))
				.setSemanticsVersion(switch (minor) {
					case 11 -> DmnIrArtifactReader.CLOSURE_SEMANTICS_VERSION;
					case 10 -> DmnIrArtifactReader.BKM_SEMANTICS_VERSION;
					case 9 -> DmnIrArtifactReader.BUILTIN_SEMANTICS_VERSION;
					case 8 -> DmnIrArtifactReader.FILTER_SEMANTICS_VERSION;
					case 7 -> DmnIrArtifactReader.COLLECTION_SEMANTICS_VERSION;
					case 6 -> DmnIrArtifactReader.FIELD_CONSTRAINT_SEMANTICS_VERSION;
					case 5 -> DmnIrArtifactReader.TABLE_SEMANTICS_VERSION;
					case 4 -> DmnIrArtifactReader.DATE_SEMANTICS_VERSION;
					case 3 -> DmnIrArtifactReader.LOCAL_SEMANTICS_VERSION;
					default -> DmnIrArtifactReader.RECORD_SEMANTICS_VERSION;
				}).addRequiredCapabilities("dmnir.scalar").addRequiredCapabilities("dmnir.scalar-expressions")
				.addRequiredCapabilities("dmnir.records")
				.setRootModelNamespace(semantic.model(semantic.rootId()).orElseThrow().identity().namespace());
		if (minor >= 3)
			artifact.addRequiredCapabilities(DmnIrArtifactReader.LOCAL_CAPABILITY);
		if (minor >= 4)
			artifact.addRequiredCapabilities(DmnIrArtifactReader.DATE_CAPABILITY);
		if (minor >= 5)
			artifact.addRequiredCapabilities(DmnIrArtifactReader.TABLE_CAPABILITY);
		if (minor >= 6)
			artifact.addRequiredCapabilities(DmnIrArtifactReader.FIELD_CONSTRAINT_CAPABILITY);
		if (minor >= 7)
			artifact.addRequiredCapabilities(DmnIrArtifactReader.COLLECTION_CAPABILITY);
		if (minor >= 8)
			artifact.addRequiredCapabilities(DmnIrArtifactReader.FILTER_CAPABILITY);
		if (minor >= 9)
			artifact.addRequiredCapabilities(DmnIrArtifactReader.BUILTIN_CAPABILITY);
		if (minor >= 10)
			artifact.addRequiredCapabilities(DmnIrArtifactReader.BKM_CAPABILITY);
		if (minor >= 11)
			artifact.addRequiredCapabilities(DmnIrArtifactReader.CLOSURE_CAPABILITY);
		for (var model : models.values()) {
			var output = DmnIrModel.newBuilder().setIdentity(identity(model.model().getNode()))
					.setNamespace(model.identity().namespace());
			records.entrySet().stream().filter(e -> e.getKey().getModelNamespace().equals(model.identity().namespace()))
					.sorted(Map.Entry.comparingByKey(ORDER)).forEach(e -> output.addRecordDefinitions(e.getValue()));
			collections.entrySet().stream()
					.filter(e -> e.getKey().getModelNamespace().equals(model.identity().namespace()))
					.sorted(Map.Entry.comparingByKey(ORDER))
					.forEach(e -> output.addCollectionDefinitions(e.getValue()));
			model.model().getImportsList().stream()
					.sorted(Comparator.comparing(io.finmsg.dmn.model.Import::getNamespace)
							.thenComparing(io.finmsg.dmn.model.Import::getName))
					.forEach(i -> {
						var imported = DmnIrImport.newBuilder().setName(i.getName())
								.setModelNamespace(i.getNamespace());
						if (!i.getLocationUri().isEmpty())
							imported.setLocationUri(i.getLocationUri());
						output.addImports(imported);
					});
			List<DmnIrInput> inputs = new ArrayList<>();
			List<DmnIrDecision> decisions = new ArrayList<>();
			List<DmnIrBkm> modelBkms = new ArrayList<>();
			for (var element : model.model().getDrgElementsList()) {
				if (element.hasInputData()) {
					var input = element.getInputData();
					inputs.add(DmnIrInput.newBuilder().setIdentity(identity(input.getNode()))
							.setVariable(variable(input.getVariable(), model)).build());
				} else if (element.hasBusinessKnowledgeModel()) {
					modelBkms.add(bkm(element.getBusinessKnowledgeModel(), model));
				} else {
					var decision = element.getDecision();
					if (!(decision.getLogic().hasLiteralExpression()
							&& decision.getLogic().getLiteralExpression().hasParsed()
							|| decision.getLogic().hasBoxedExpression()
									&& decision.getLogic().getBoxedExpression().hasParsed()
							|| decision.getLogic().hasDecisionTable() || decision.getLogic().hasInvocation())
							|| decision.getAuthorityRequirementsCount() != 0)
						throw unsupported();
					Set<DmnIrReference> dependencies = new TreeSet<>(ORDER);
					for (var requirement : decision.getInformationRequirementsList())
						dependencies.add(requirement(
								requirement.hasInput() ? requirement.getInput() : requirement.getDecision(), model));
					localBindings.clear();
					knowledge = knowledge(decision.getKnowledgeRequirementsList(), model);
					String path = "definitions/decision[" + decision.getNode().getName() + "]/logic";
					var wire = DmnIrDecision.newBuilder().setIdentity(identity(decision.getNode()))
							.addAllKnowledgeDependencies(knowledge);
					var declaredType = decision.getVariable().hasType()
							? type(decision.getVariable().getType(), model)
							: null;
					var expectedResult = declaredType != null
							&& declaredType.getScalarType() != DmnIrScalarType.SCALAR_TYPE_NULL ? declaredType : null;
					if (decision.getLogic().hasDecisionTable()) {
						wire.setDecisionTable(table(decision.getLogic().getDecisionTable(),
								declaredType != null ? declaredType : type(decision.getVariable().getType(), model),
								model, path + "/decisionTable", dependencies));
					} else if (decision.getLogic().hasLiteralExpression()) {
						wire.setExpression(expression(decision.getLogic().getLiteralExpression().getParsed().getAst(),
								model, path + "/literalExpression", dependencies, 1, expectedResult));
					} else if (decision.getLogic().hasInvocation()) {
						var invocation = decision.getLogic().getInvocation();
						var target = invocation.getExpression().getParsed().getAst();
						var call = InvocationExpression.newBuilder().setTarget(target);
						for (var binding : invocation.getBindingsList())
							call.addArguments(NamedArgument.newBuilder().setName(binding.getParameter())
									.setExpression(binding.getExpression().getParsed().getAst()));
						wire.setExpression(bkmCall(call.build(), model, path + "/invocation/expression", dependencies,
								1, index -> path + "/invocation/binding[" + index + "]/expression"));
					} else {
						wire.setExpression(boxed(decision.getLogic().getBoxedExpression().getParsed(), model,
								path + "/boxedExpression", dependencies, 1, expectedResult));
					}
					DmnIrTypeReference resolvedVarType = declaredType;
					if (resolvedVarType == null || (resolvedVarType.hasScalarType()
							&& resolvedVarType.getScalarType() == DmnIrScalarType.SCALAR_TYPE_NULL)) {
						if (wire.hasExpression()) {
							var exprType = expressionType(wire.getExpression());
							if (exprType.hasCollectionType() || exprType.hasRecordType() || (exprType.hasScalarType()
									&& exprType.getScalarType() != DmnIrScalarType.SCALAR_TYPE_NULL)) {
								resolvedVarType = exprType;
							}
						}
					}
					wire.setVariable(variable(decision.getVariable(), model, resolvedVarType));
					declarations.put(ref(model.identity().namespace(), decision.getNode().getId()), resolvedVarType);
					decisions.add(wire.addAllDependencies(dependencies).build());
				}
			}
			inputs.sort(Comparator.comparing(i -> i.getIdentity().getId()));
			decisions.sort(Comparator.comparing(d -> d.getIdentity().getId()));
			modelBkms.sort(Comparator.comparing(b -> b.getIdentity().getId()));
			output.addAllInputs(inputs).addAllDecisions(decisions).addAllBkms(modelBkms);
			artifact.addModels(output);
		}
		Map<DmnIrReference, DmnIrDecision> pending = new TreeMap<>(ORDER);
		Set<DmnIrReference> done = new HashSet<>();
		for (var model : artifact.getModelsList()) {
			for (var input : model.getInputsList())
				done.add(ref(model.getNamespace(), input.getIdentity().getId()));
			for (var decision : model.getDecisionsList())
				pending.put(ref(model.getNamespace(), decision.getIdentity().getId()), decision);
		}
		while (!pending.isEmpty()) {
			var next = pending.entrySet().stream().filter(e -> done.containsAll(e.getValue().getDependenciesList()))
					.findFirst().orElseThrow(DmnIrRecordProducer::unsupported);
			artifact.addEvaluationOrder(next.getKey());
			done.add(next.getKey());
			pending.remove(next.getKey());
		}
		// Built-in literal list arguments may introduce typed collections during
		// expression production.
		for (int i = 0; i < artifact.getModelsCount(); i++) {
			var output = artifact.getModelsBuilder(i);
			output.clearCollectionDefinitions();
			collections.entrySet().stream().filter(e -> e.getKey().getModelNamespace().equals(output.getNamespace()))
					.sorted(Map.Entry.comparingByKey(ORDER))
					.forEach(e -> output.addCollectionDefinitions(e.getValue()));
		}
		int finalMinor = !collections.isEmpty()
				? 7
				: fieldConstraintsProfile ? 6 : tableProfile ? 5 : dateProfile ? 4 : localProfile ? 3 : 2;
		if (closures)
			finalMinor = 11;
		else if (!bkms.isEmpty())
			finalMinor = 10;
		else if (builtinCalls)
			finalMinor = 9;
		else if (filters)
			finalMinor = 8;
		artifact.getSchemaVersionBuilder().setMinor(finalMinor);
		artifact.setSemanticsVersion(switch (finalMinor) {
			case 11 -> DmnIrArtifactReader.CLOSURE_SEMANTICS_VERSION;
			case 10 -> DmnIrArtifactReader.BKM_SEMANTICS_VERSION;
			case 9 -> DmnIrArtifactReader.BUILTIN_SEMANTICS_VERSION;
			case 8 -> DmnIrArtifactReader.FILTER_SEMANTICS_VERSION;
			case 7 -> DmnIrArtifactReader.COLLECTION_SEMANTICS_VERSION;
			case 6 -> DmnIrArtifactReader.FIELD_CONSTRAINT_SEMANTICS_VERSION;
			case 5 -> DmnIrArtifactReader.TABLE_SEMANTICS_VERSION;
			case 4 -> DmnIrArtifactReader.DATE_SEMANTICS_VERSION;
			case 3 -> DmnIrArtifactReader.LOCAL_SEMANTICS_VERSION;
			default -> DmnIrArtifactReader.RECORD_SEMANTICS_VERSION;
		});
		artifact.clearRequiredCapabilities();
		var allCaps = List.of(DmnIrArtifactReader.SCALAR_CAPABILITY, DmnIrArtifactReader.EXPRESSION_CAPABILITY,
				DmnIrArtifactReader.RECORD_CAPABILITY, DmnIrArtifactReader.LOCAL_CAPABILITY,
				DmnIrArtifactReader.DATE_CAPABILITY, DmnIrArtifactReader.TABLE_CAPABILITY,
				DmnIrArtifactReader.FIELD_CONSTRAINT_CAPABILITY, DmnIrArtifactReader.COLLECTION_CAPABILITY,
				DmnIrArtifactReader.FILTER_CAPABILITY, DmnIrArtifactReader.BUILTIN_CAPABILITY,
				DmnIrArtifactReader.BKM_CAPABILITY, DmnIrArtifactReader.CLOSURE_CAPABILITY);
		for (int c = 0; c <= finalMinor; c++) {
			artifact.addRequiredCapabilities(allCaps.get(c));
		}
		try {
			return new DmnIrArtifactReader().read(artifact.build().toByteArray());
		} catch (DmnIrValidationException e) {
			var cat = e.code() == DmnIrValidationException.Code.INVALID_TYPE
					? DmnIrProductionException.Category.UNSUPPORTED_PROFILE
					: DmnIrProductionException.Category.ARTIFACT_INVALID;
			throw new DmnIrProductionException(cat, "DmnIrValidation failed: " + e.getMessage(), null, List.of(), e);
		}
	}

	private DmnIrBkm bkm(BusinessKnowledgeModel source, DmnSemanticModel model) {
		var function = source.getFunction();
		if (!function.getLogic().hasParsed() || source.getAuthorityRequirementsCount() != 0
				|| function.getKind() != FunctionKind.FUNCTION_KIND_UNSPECIFIED
						&& function.getKind() != FunctionKind.FUNCTION_KIND_FEEL)
			throw unsupported();
		localBindings.clear();
		knowledge = knowledge(source.getKnowledgeRequirementsList(), model);
		String path = "definitions/businessKnowledgeModel[" + source.getNode().getName() + "]";
		var bkm = DmnIrBkm.newBuilder().setIdentity(identity(source.getNode()))
				.setVariable(variable(source.getVariable(), model)).addAllKnowledgeDependencies(knowledge);
		for (int i = 0; i < function.getFormalParametersCount(); i++) {
			String binding = path + "/parameter[" + i + "]";
			var formal = variable(function.getFormalParameters(i), model);
			localBindings.put(binding,
					DmnIrLocalBinding.newBuilder().setBindingId(binding).setVariable(formal).build());
			bkm.addParameters(DmnIrBkmParameter.newBuilder().setBindingId(binding).setVariable(formal));
		}
		Set<DmnIrReference> globals = new HashSet<>();
		var body = expression(function.getLogic().getParsed().getAst(), model, path + "/logic", globals, 1,
				type(source.getVariable().getType(), model));
		if (!globals.isEmpty())
			throw unsupported();
		return bkm.setBody(body).build();
	}

	private Set<DmnIrReference> knowledge(List<KnowledgeRequirement> requirements, DmnSemanticModel model) {
		Set<DmnIrReference> targets = new TreeSet<>(ORDER);
		for (var requirement : requirements) {
			var target = address(requirement.getRequiredKnowledge(), model);
			if (!bkms.containsKey(target) || !targets.add(target))
				throw unsupported();
		}
		return targets;
	}

	private DmnIrDecisionTable table(DecisionTable source, DmnIrTypeReference result, DmnSemanticModel model,
			String path, Set<DmnIrReference> dependencies) {
		if (source.getInputsCount() == 0 || source.getOutputsCount() == 0 || source.getRulesCount() == 0
				|| source.getAnnotationsCount() != 0)
			throw unsupported();
		List<DecisionRule> sortedRules = source.getRulesList();
		var hitPolicy = switch (source.getHitPolicy().getPolicy()) {
			case HIT_POLICY_UNSPECIFIED, HIT_POLICY_UNIQUE -> {
				if (source.getHitPolicy().getAggregation() != Aggregation.AGGREGATION_UNSPECIFIED)
					throw unsupported();
				yield DmnIrTableHitPolicy.TABLE_HIT_POLICY_UNIQUE;
			}
			case HIT_POLICY_FIRST -> {
				if (source.getHitPolicy().getAggregation() != Aggregation.AGGREGATION_UNSPECIFIED)
					throw unsupported();
				yield DmnIrTableHitPolicy.TABLE_HIT_POLICY_FIRST;
			}
			case HIT_POLICY_PRIORITY -> {
				if (source.getHitPolicy().getAggregation() != Aggregation.AGGREGATION_UNSPECIFIED)
					throw unsupported();
				sortedRules = sortRulesByPriority(source.getRulesList(), source.getOutputsList());
				yield DmnIrTableHitPolicy.TABLE_HIT_POLICY_FIRST;
			}
			case HIT_POLICY_ANY -> {
				if (source.getHitPolicy().getAggregation() != Aggregation.AGGREGATION_UNSPECIFIED)
					throw unsupported();
				yield DmnIrTableHitPolicy.TABLE_HIT_POLICY_ANY;
			}
			case HIT_POLICY_RULE_ORDER -> {
				if (source.getHitPolicy().getAggregation() != Aggregation.AGGREGATION_UNSPECIFIED)
					throw unsupported();
				yield DmnIrTableHitPolicy.TABLE_HIT_POLICY_RULE_ORDER;
			}
			case HIT_POLICY_OUTPUT_ORDER -> {
				if (source.getHitPolicy().getAggregation() != Aggregation.AGGREGATION_UNSPECIFIED)
					throw unsupported();
				sortedRules = sortRulesByPriority(source.getRulesList(), source.getOutputsList());
				yield DmnIrTableHitPolicy.TABLE_HIT_POLICY_RULE_ORDER;
			}
			case HIT_POLICY_COLLECT -> switch (source.getHitPolicy().getAggregation()) {
				case AGGREGATION_UNSPECIFIED -> DmnIrTableHitPolicy.TABLE_HIT_POLICY_COLLECT;
				case AGGREGATION_SUM -> DmnIrTableHitPolicy.TABLE_HIT_POLICY_COLLECT_SUM;
				case AGGREGATION_MIN -> DmnIrTableHitPolicy.TABLE_HIT_POLICY_COLLECT_MIN;
				case AGGREGATION_MAX -> DmnIrTableHitPolicy.TABLE_HIT_POLICY_COLLECT_MAX;
				case AGGREGATION_COUNT -> DmnIrTableHitPolicy.TABLE_HIT_POLICY_COLLECT_COUNT;
				default -> throw unsupported();
			};
			default -> throw unsupported();
		};
		var table = DmnIrDecisionTable.newBuilder().setHitPolicy(hitPolicy);
		if (!source.getNode().getId().isBlank())
			table.setId(source.getNode().getId());
		for (int i = 0; i < source.getInputsCount(); i++) {
			var clause = source.getInputs(i);
			var value = feel(clause.getInputExpression(), model, path + "/input[" + i + "]", dependencies);
			var columnType = clause.hasType() ? type(clause.getType(), model) : expressionType(value);
			var input = DmnIrTableInput.newBuilder().setExpression(value).setType(tableScalar(columnType));
			if (!clause.getNode().getId().isBlank())
				input.setId(clause.getNode().getId());
			if (!clause.getNode().getLabel().isEmpty())
				input.setLabel(clause.getNode().getLabel());
			table.addInputs(input);
		}
		for (int i = 0; i < source.getOutputsCount(); i++) {
			var clause = source.getOutputs(i);
			DmnIrTypeReference columnType;
			String fieldName = clause.getNode().getName();
			if (clause.hasType()) {
				columnType = type(clause.getType(), model);
			} else if (result.hasCollectionType()) {
				var colDef = collections.get(result.getCollectionType());
				if (colDef != null && colDef.hasRecordType()) {
					var recDef = records.get(colDef.getRecordType());
					columnType = recDef != null && i < recDef.getFieldsCount() ? recDef.getFields(i).getType() : result;
					if (fieldName.isBlank() && recDef != null && i < recDef.getFieldsCount()) {
						fieldName = recDef.getFields(i).getName();
					}
				} else if (colDef != null && colDef.getElementType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
					columnType = DmnIrTypeReference.newBuilder().setScalarType(colDef.getElementType()).build();
				} else {
					columnType = result;
				}
			} else if (result.hasRecordType()) {
				var recDef = records.get(result.getRecordType());
				columnType = recDef != null && i < recDef.getFieldsCount() ? recDef.getFields(i).getType() : result;
				if (fieldName.isBlank() && recDef != null && i < recDef.getFieldsCount()) {
					fieldName = recDef.getFields(i).getName();
				}
			} else {
				columnType = result;
			}
			var output = DmnIrTableOutput.newBuilder().setType(tableScalar(columnType));
			if (!clause.getNode().getId().isBlank())
				output.setId(clause.getNode().getId());
			if (!fieldName.isBlank())
				output.setName(fieldName);
			if (clause.hasDefaultOutputEntry()) {
				var parsed = clause.getDefaultOutputEntry().getParsed();
				if (!parsed.hasFeel())
					throw unsupported();
				output.setDefaultExpression(expression(parsed.getFeel().getAst(), model,
						path + "/output[" + i + "]/defaultOutputEntry", dependencies, 1));
			}
			table.addOutputs(output);
		}
		for (int i = 0; i < sortedRules.size(); i++) {
			var sourceRule = sortedRules.get(i);
			if (sourceRule.getAnnotationEntriesCount() != 0)
				throw unsupported();
			var rule = DmnIrTableRule.newBuilder();
			if (!sourceRule.getNode().getId().isBlank())
				rule.setId(sourceRule.getNode().getId());
			String rulePath = path + "/rule[" + i + "]";
			for (int j = 0; j < sourceRule.getInputEntriesCount(); j++) {
				var entry = sourceRule.getInputEntries(j);
				if (!entry.hasParsed())
					throw unsupported();
				rule.addInputEntries(
						tests(entry.getParsed().getTests(), model, rulePath + "/inputEntry[" + j + "]", dependencies));
			}
			for (int j = 0; j < sourceRule.getOutputEntriesCount(); j++)
				rule.addOutputEntries(feel(sourceRule.getOutputEntries(j), model, rulePath + "/outputEntry[" + j + "]",
						dependencies));
			table.addRules(rule);
		}
		return table.build();
	}

	private static List<DecisionRule> sortRulesByPriority(List<DecisionRule> rules, List<OutputClause> outputs) {
		List<List<Expression>> outputPriorities = new ArrayList<>();
		for (var out : outputs) {
			List<Expression> priorities = new ArrayList<>();
			if (out.hasOutputValues() && out.getOutputValues().hasParsed()
					&& out.getOutputValues().getParsed().getAst().hasUnaryTests()) {
				for (var test : out.getOutputValues().getParsed().getAst().getUnaryTests().getTestsList()) {
					if (test.hasExpression())
						priorities.add(test.getExpression());
				}
			}
			outputPriorities.add(priorities);
		}
		if (outputPriorities.stream().allMatch(List::isEmpty)) {
			return rules;
		}
		record IndexedRule(DecisionRule rule, int originalIndex, List<Integer> priorities) {
		}
		List<IndexedRule> indexed = new ArrayList<>();
		for (int i = 0; i < rules.size(); i++) {
			var rule = rules.get(i);
			List<Integer> rulePrios = new ArrayList<>();
			for (int j = 0; j < rule.getOutputEntriesCount() && j < outputPriorities.size(); j++) {
				var entry = rule.getOutputEntries(j);
				var colPrios = outputPriorities.get(j);
				int prio = Integer.MAX_VALUE;
				if (entry.hasParsed()) {
					var ast = entry.getParsed().getAst();
					for (int p = 0; p < colPrios.size(); p++) {
						if (ast.equals(colPrios.get(p))) {
							prio = p;
							break;
						}
					}
				}
				rulePrios.add(prio);
			}
			indexed.add(new IndexedRule(rule, i, rulePrios));
		}
		indexed.sort((a, b) -> {
			for (int j = 0; j < a.priorities.size() && j < b.priorities.size(); j++) {
				int cmp = Integer.compare(a.priorities.get(j), b.priorities.get(j));
				if (cmp != 0)
					return cmp;
			}
			return Integer.compare(a.originalIndex, b.originalIndex);
		});
		return indexed.stream().map(IndexedRule::rule).toList();
	}

	private DmnIrExpression feel(Feel feel, DmnSemanticModel model, String path, Set<DmnIrReference> dependencies) {
		if (!feel.hasParsed())
			throw unsupported();
		return expression(feel.getParsed().getAst(), model, path, dependencies, 1);
	}

	private static DmnIrScalarType tableScalar(DmnIrTypeReference type) {
		if (!type.hasScalarType())
			throw unsupported();
		return type.getScalarType();
	}

	private DmnIrUnaryTests tests(UnaryTestsExpression source, DmnSemanticModel model, String path,
			Set<DmnIrReference> dependencies) {
		return tests(source, path, (value, endpointPath) -> expression(value, model, endpointPath, dependencies, 1));
	}

	private static DmnIrUnaryTests tests(UnaryTestsExpression source, String path,
			BiFunction<Expression, String, DmnIrExpression> endpoint) {
		var group = DmnIrUnaryTests.newBuilder().setWildcard(source.getWildcard()).setNegated(source.getNegated());
		for (int i = 0; i < source.getTestsCount(); i++) {
			var test = source.getTests(i);
			String testPath = path + "/test[" + i + "]";
			var value = DmnIrUnaryTest.newBuilder();
			switch (test.getTypeCase()) {
				case COMPARISON -> {
					var comparison = test.getComparison();
					value.setComparison(DmnIrTestComparison.newBuilder().setOperator(switch (comparison.getOperator()) {
						case UNARY_TEST_OPERATOR_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL;
						case UNARY_TEST_OPERATOR_NOT_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_NOT_EQUAL;
						case UNARY_TEST_OPERATOR_LESS -> DmnIrBinaryOperator.BINARY_OPERATOR_LESS;
						case UNARY_TEST_OPERATOR_LESS_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_LESS_EQUAL;
						case UNARY_TEST_OPERATOR_GREATER -> DmnIrBinaryOperator.BINARY_OPERATOR_GREATER;
						case UNARY_TEST_OPERATOR_GREATER_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_GREATER_EQUAL;
						default -> throw unsupported();
					}).setEndpoint(endpoint.apply(comparison.getEndpoint(), testPath)));
				}
				case EXPRESSION -> value.setComparison(
						DmnIrTestComparison.newBuilder().setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL)
								.setEndpoint(endpoint.apply(test.getExpression(), testPath)));
				case RANGE -> {
					var range = test.getRange();
					if (!range.hasLower() || !range.hasUpper())
						throw unsupported();
					value.setRange(DmnIrTestRange.newBuilder()
							.setLower(endpoint.apply(range.getLower(), testPath + "/lower"))
							.setUpper(endpoint.apply(range.getUpper(), testPath + "/upper"))
							.setLowerInclusive(range.getLowerBoundary() == RangeBoundary.RANGE_BOUNDARY_CLOSED)
							.setUpperInclusive(range.getUpperBoundary() == RangeBoundary.RANGE_BOUNDARY_CLOSED));
				}
				case TYPE_NOT_SET -> throw unsupported();
			}
			group.addTests(value);
		}
		return group.build();
	}

	private DmnIrExpression constraintEndpoint(Expression value, DmnSemanticModel model, String path) {
		var endpoint = expression(value, model, path, new HashSet<>(), 1);
		if (endpoint.hasLiteral())
			return endpoint;
		if (endpoint.hasUnary()) {
			var unary = endpoint.getUnary();
			var number = unary.getOperand().getLiteral().getNumberValue();
			if (number.hasScale() && unary.getOperator() == DmnIrUnaryOperator.UNARY_OPERATOR_POSITIVE)
				return unary.getOperand();
			if (number.hasScale() && unary.getOperator() == DmnIrUnaryOperator.UNARY_OPERATOR_NEGATE) {
				String coefficient = new java.math.BigInteger(number.getUnscaledValue()).negate().toString();
				return unary.getOperand().toBuilder().setLiteral(
						DmnIrLiteral.newBuilder().setNumberValue(number.toBuilder().setUnscaledValue(coefficient)))
						.build();
			}
		}
		throw unsupported();
	}

	private DmnIrTypeReference type(TypeReference type, DmnSemanticModel model) {
		if (type == null)
			return DmnIrTypeReference.newBuilder().setScalarType(DmnIrScalarType.SCALAR_TYPE_NULL).build();
		if (type.hasNamed()) {
			var name = type.getNamed().getName();
			var namespace = type.getNamed().getNamespace();
			if (namespace.isBlank())
				namespace = model.identity().namespace();
			if (!named.containsKey(new TypeName(namespace, name)) && name.contains(".")) {
				String alias = name.substring(0, name.indexOf('.'));
				var imported = model.model().getImportsList().stream().filter(i -> i.getName().equals(alias))
						.findFirst();
				if (imported.isPresent()) {
					namespace = imported.get().getNamespace();
					name = name.substring(name.indexOf('.') + 1);
				}
			}
			final String qualifier = namespace;
			namespace = model.model().getImportsList().stream().filter(i -> i.getName().equals(qualifier))
					.map(io.finmsg.dmn.model.Import::getNamespace).findFirst().orElse(namespace);
			var typeName = new TypeName(namespace, name);
			if (typeAliases.containsKey(typeName)) {
				return typeAliases.get(typeName);
			}
			var record = named.get(typeName);
			if (record != null && typeAliasesByRef.containsKey(record)) {
				return typeAliasesByRef.get(record);
			}
			if (record == null)
				throw unsupported();
			var reference = DmnIrTypeReference.newBuilder();
			return (collectionTypes.contains(record)
					? reference.setCollectionType(record)
					: reference.setRecordType(record)).build();
		}
		if (!type.hasBuiltin())
			return DmnIrTypeReference.newBuilder().setScalarType(DmnIrScalarType.SCALAR_TYPE_NULL).build();
		return DmnIrTypeReference.newBuilder().setScalarType(switch (type.getBuiltin()) {
			case BUILTIN_TYPE_NULL, BUILTIN_TYPE_ANY, BUILTIN_TYPE_UNSPECIFIED, BUILTIN_TYPE_RANGE ->
				DmnIrScalarType.SCALAR_TYPE_NULL;
			case BUILTIN_TYPE_BOOLEAN -> DmnIrScalarType.SCALAR_TYPE_BOOLEAN;
			case BUILTIN_TYPE_NUMBER -> DmnIrScalarType.SCALAR_TYPE_NUMBER;
			case BUILTIN_TYPE_STRING -> DmnIrScalarType.SCALAR_TYPE_STRING;
			case BUILTIN_TYPE_DATE -> DmnIrScalarType.SCALAR_TYPE_DATE;
			case BUILTIN_TYPE_TIME -> DmnIrScalarType.SCALAR_TYPE_TIME;
			case BUILTIN_TYPE_DATE_AND_TIME -> DmnIrScalarType.SCALAR_TYPE_DATE_TIME;
			case BUILTIN_TYPE_DURATION, BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION, BUILTIN_TYPE_DAYS_AND_TIME_DURATION ->
				DmnIrScalarType.SCALAR_TYPE_DURATION;
			default -> throw unsupported();
		}).build();
	}
	private DmnIrVariable variable(InformationItem item, DmnSemanticModel model) {
		return variable(item, model, null);
	}
	private DmnIrVariable variable(InformationItem item, DmnSemanticModel model, DmnIrTypeReference resolvedType) {
		var output = DmnIrVariable.newBuilder().setName(item.getNode().getName());
		if (!item.getNode().getId().isBlank())
			output.setId(item.getNode().getId());
		var type = resolvedType != null ? resolvedType : type(item.getType(), model);
		if (type.hasCollectionType())
			output.setCollectionType(type.getCollectionType());
		else if (type.hasRecordType())
			output.setRecordType(type.getRecordType());
		else
			output.setType(type.getScalarType());
		return output.build();
	}
	private DmnIrExpression expression(Expression source, DmnSemanticModel model, String path,
			Set<DmnIrReference> dependencies, int depth) {
		return expression(source, model, path, dependencies, depth, null);
	}
	private DmnIrExpression expression(Expression source, DmnSemanticModel model, String path,
			Set<DmnIrReference> dependencies, int depth, DmnIrTypeReference expected) {
		if (depth > 32)
			throw unsupported();
		var binding = model.bindings().stream().filter(b -> b.referencePath().equals(path)).findFirst();
		if (source.hasName() && binding.isEmpty()) {
			var matchingLocal = localBindings.values().stream()
					.filter(l -> !l.hasClosure() && l.getVariable().getName().equals(source.getName().getName()))
					.findFirst();
			if (matchingLocal.isPresent()) {
				var local = matchingLocal.get();
				if (captures != null && captureScope.contains(local.getBindingId()))
					captures.add(local.getBindingId());
				var variable = local.getVariable();
				var localType = DmnIrTypeReference.newBuilder();
				if (variable.hasCollectionType())
					localType.setCollectionType(variable.getCollectionType());
				else if (variable.hasRecordType())
					localType.setRecordType(variable.getRecordType());
				else
					localType.setScalarType(variable.getType());
				return typed(localType.build())
						.setLocalReference(DmnIrLocalReference.newBuilder().setBindingId(local.getBindingId())).build();
			}
		}
		if ((source.hasName() || source.hasPath()) && binding.isPresent()) {
			DmnSymbolBinding symbol = binding.get();
			if (symbol.kind() == io.finmsg.dmn.semantic.analysis.DmnSymbolKind.LOCAL_VARIABLE
					|| symbol.kind() == io.finmsg.dmn.semantic.analysis.DmnSymbolKind.PARAMETER) {
				var local = localBindings.get(symbol.declarationPath());
				if (local == null && source.hasName()) {
					String targetName = source.getName().getName();
					local = localBindings.values().stream()
							.filter(b -> !b.hasClosure() && b.getVariable().getName().equals(targetName)).findFirst()
							.orElse(null);
				}
				if (local == null || local.hasClosure())
					throw unsupported();
				if (captures != null && captureScope.contains(local.getBindingId()))
					captures.add(local.getBindingId());
				var variable = local.getVariable();
				var localType = DmnIrTypeReference.newBuilder();
				if (variable.hasCollectionType())
					localType.setCollectionType(variable.getCollectionType());
				else if (variable.hasRecordType())
					localType.setRecordType(variable.getRecordType());
				else
					localType.setScalarType(variable.getType());
				return typed(localType.build())
						.setLocalReference(DmnIrLocalReference.newBuilder().setBindingId(local.getBindingId())).build();
			}
			var address = ref(
					symbol.targetNamespace().isBlank() ? model.identity().namespace() : symbol.targetNamespace(),
					symbol.symbolId());
			var type = declarations.get(address);
			if (type == null)
				throw unsupported();
			dependencies.add(address);
			return typed(type).setGlobalReference(address).build();
		}
		if (source.hasPath()) {
			var value = source.getPath();
			var parent = expression(value.getSource(), model, path + "/source", dependencies, depth + 1);
			if (!parent.hasRecordType()) {
				var pt = parent.getType();
				if (pt == DmnIrScalarType.SCALAR_TYPE_DATE || pt == DmnIrScalarType.SCALAR_TYPE_TIME
						|| pt == DmnIrScalarType.SCALAR_TYPE_DATE_TIME || pt == DmnIrScalarType.SCALAR_TYPE_DURATION) {
					DmnIrScalarType propType = switch (value.getMember()) {
						case "timezone" -> DmnIrScalarType.SCALAR_TYPE_STRING;
						case "time offset" -> DmnIrScalarType.SCALAR_TYPE_DURATION;
						case "year", "month", "day", "weekday", "hour", "minute", "second", "years", "months", "days",
								"hours", "minutes", "seconds" ->
							DmnIrScalarType.SCALAR_TYPE_NUMBER;
						default -> DmnIrScalarType.SCALAR_TYPE_NULL;
					};
					return DmnIrExpression.newBuilder().setType(propType)
							.setFieldAccess(
									DmnIrFieldAccess.newBuilder().setSource(parent).setFieldName(value.getMember()))
							.build();
				}
				if (pt == DmnIrScalarType.SCALAR_TYPE_NULL || parent.hasCollectionType()) {
					usedLocalProfile = true;
					return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NULL)
							.setFieldAccess(
									DmnIrFieldAccess.newBuilder().setSource(parent).setFieldName(value.getMember()))
							.build();
				}
				throw unsupported();
			}
			var record = records.get(parent.getRecordType());
			if (record == null)
				throw unsupported();
			var field = record.getFieldsList().stream().filter(f -> f.getName().equals(value.getMember())).findFirst()
					.orElseThrow(DmnIrRecordProducer::unsupported);
			return typed(field.getType())
					.setFieldAccess(DmnIrFieldAccess.newBuilder().setSource(parent).setFieldName(value.getMember()))
					.build();
		}
		if (source.hasFilter()) {
			var filter = source.getFilter();
			var input = expression(filter.getSource(), model, path + "/source", dependencies, depth + 1, expected);
			DmnIrScalarType itemScalar = DmnIrScalarType.SCALAR_TYPE_NULL;
			if (input.hasCollectionType()) {
				var collection = collections.get(input.getCollectionType());
				if (collection != null && !collection.hasRecordType() && !collection.hasCollectionType()) {
					itemScalar = collection.getElementType();
				}
			} else {
				builtinCalls = true;
			}
			String itemId = path + "/filter/item";
			localBindings.put(itemId, DmnIrLocalBinding.newBuilder().setBindingId(itemId)
					.setVariable(DmnIrVariable.newBuilder().setName("item").setType(itemScalar)).build());
			try {
				var predicate = expression(filter.getFilter(), model, path + "/filter", dependencies, depth + 1);
				boolean numericIndex = predicate.getType() == DmnIrScalarType.SCALAR_TYPE_NUMBER;
				if (predicate.getType() != DmnIrScalarType.SCALAR_TYPE_BOOLEAN && !numericIndex
						&& predicate.getType() != DmnIrScalarType.SCALAR_TYPE_NULL)
					throw unsupported();
				filters = true;
				if (numericIndex || predicate.getType() == DmnIrScalarType.SCALAR_TYPE_NULL) {
					builtinCalls = true;
					return DmnIrExpression
							.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NULL).setFilter(DmnIrFilterExpression
									.newBuilder().setSource(input).setItemBindingId(itemId).setPredicate(predicate))
							.build();
				}
				return typed(expressionType(input)).setFilter(DmnIrFilterExpression.newBuilder().setSource(input)
						.setItemBindingId(itemId).setPredicate(predicate)).build();
			} finally {
				localBindings.remove(itemId);
			}
		}
		if (source.hasList()) {
			DmnIrTypeReference listType = expected;
			if (listType == null || !listType.hasCollectionType()) {
				DmnIrScalarType elemScalar = findUnderlyingScalarType(source);
				if (elemScalar == DmnIrScalarType.SCALAR_TYPE_NULL) {
					elemScalar = DmnIrScalarType.SCALAR_TYPE_STRING;
				}
				listType = ensureCollectionType(model, elemScalar);
			}
			var list = DmnIrListExpression.newBuilder();
			for (int i = 0; i < source.getList().getElementsCount(); i++)
				list.addElements(expression(source.getList().getElements(i), model, path + "/element[" + i + "]",
						dependencies, depth + 1));
			return typed(listType).setList(list).build();
		}
		if (source.hasIfExpression()) {
			var value = source.getIfExpression();
			var condition = expression(value.getCondition(), model, path + "/condition", dependencies, depth + 1);
			var yes = expression(value.getThenExpression(), model, path + "/then", dependencies, depth + 1, expected);
			var no = expression(value.getElseExpression(), model, path + "/else", dependencies, depth + 1, expected);
			DmnIrTypeReference result;
			var yesType = expressionType(yes);
			var noType = expressionType(no);
			if (!yesType.hasScalarType() || !noType.hasScalarType()) {
				result = yesType.hasScalarType() ? noType : yesType;
				var other = yesType.hasScalarType() ? yesType : noType;
				if (!other.equals(result) && other.getScalarType() != DmnIrScalarType.SCALAR_TYPE_NULL)
					throw unsupported();
			} else if (expected != null && expected.hasCollectionType()
					&& yesType.getScalarType() == DmnIrScalarType.SCALAR_TYPE_NULL
					&& noType.getScalarType() == DmnIrScalarType.SCALAR_TYPE_NULL) {
				result = expected;
			} else {
				result = type(source.getInferredType(), model);
			}
			return typed(result).setConditional(DmnIrConditionalExpression.newBuilder().setCondition(condition)
					.setThenExpression(yes).setElseExpression(no)).build();
		}
		if (source.hasInvocation()) {
			var call = source.getInvocation();
			var targetPath = path + "/target";
			var closureBinding = model.bindings().stream().filter(symbol -> symbol.referencePath().equals(targetPath))
					.map(symbol -> localBindings.get(symbol.declarationPath())).filter(Objects::nonNull)
					.filter(DmnIrLocalBinding::hasClosure).findFirst();
			if (call.getTarget().hasFunctionDefinition() || closureBinding.isPresent())
				return closureCall(call, closureBinding, model, path, dependencies, depth);
			if (call.getTarget().hasName() && call.getTarget().getName().getName().equals("date")
					&& source.getInferredType().getBuiltin() == BuiltinType.BUILTIN_TYPE_DATE
					&& call.getArgumentsCount() == 0 && call.getPositionalArgumentsCount() == 1
					&& call.getPositionalArguments(0).hasLiteral()
					&& call.getPositionalArguments(0).getLiteral().getKind() == LiteralKind.LITERAL_KIND_STRING
					&& model.bindings().stream().noneMatch(b -> b.referencePath().equals(path + "/target"))) {
				var date = DmnIrDates.fromFeel(call.getPositionalArguments(0).getLiteral().getValue());
				if (date == null)
					throw unsupported();
				return typed(type(source.getInferredType(), model))
						.setLiteral(DmnIrLiteral.newBuilder().setDateValue(DmnIrDates.wire(date))).build();
			}
			var signature = DmnIrBuiltinSignature.forFeelName(call.getTarget().getName().getName());
			if (signature.isPresent())
				return builtinCall(call, signature.get(), model, path, dependencies, depth, expected);
			if (boundBkm(model, path + "/target").isPresent())
				return bkmCall(call, model, path + "/target", dependencies, depth,
						index -> path + "/argument[" + index + "]");
		}
		var output = typed(type(source.getInferredType(), model));
		if (!source.hasLiteral() && source.getInferredType().getBuiltin() == BuiltinType.BUILTIN_TYPE_NULL)
			return nullLiteral();
		switch (source.getNodeCase()) {
			case LITERAL -> {
				var literal = DmnIrLiteral.newBuilder();
				var value = source.getLiteral();
				switch (value.getKind()) {
					case LITERAL_KIND_NULL -> literal.setNullValue(DmnIrNull.NULL_VALUE);
					case LITERAL_KIND_BOOLEAN -> literal.setBooleanValue(Boolean.parseBoolean(value.getValue()));
					case LITERAL_KIND_STRING -> literal.setStringValue(value.getValue());
					case LITERAL_KIND_DATE -> {
						var date = DmnIrDates.fromFeel(value.getValue());
						if (date == null)
							throw unsupported();
						literal.setDateValue(DmnIrDates.wire(date));
					}
					case LITERAL_KIND_NUMBER -> {
						var number = new BigDecimal(value.getValue());
						literal.setNumberValue(DmnIrDecimal.newBuilder()
								.setUnscaledValue(number.unscaledValue().toString()).setScale(number.scale()));
					}
					case LITERAL_KIND_TIME -> literal.setTimeValue(value.getValue());
					case LITERAL_KIND_DATE_TIME -> literal.setDateTimeValue(value.getValue());
					case LITERAL_KIND_DURATION -> literal.setDurationValue(value.getValue());
					default -> throw unsupported();
				}
				return output.setLiteral(literal).build();
			}
			case UNARY -> {
				var value = source.getUnary();
				return output.setUnary(DmnIrUnaryExpression.newBuilder().setOperator(switch (value.getOperator()) {
					case UNARY_OPERATOR_PLUS -> DmnIrUnaryOperator.UNARY_OPERATOR_POSITIVE;
					case UNARY_OPERATOR_MINUS -> DmnIrUnaryOperator.UNARY_OPERATOR_NEGATE;
					case UNARY_OPERATOR_NOT -> DmnIrUnaryOperator.UNARY_OPERATOR_NOT;
					default -> throw unsupported();
				}).setOperand(expression(value.getExpression(), model, path + "/unary", dependencies, depth + 1)))
						.build();
			}
			case BINARY -> {
				var value = source.getBinary();
				return output.setBinary(DmnIrBinaryExpression.newBuilder().setOperator(binary(value.getOperator()))
						.setLeft(expression(value.getLeft(), model, path + "/left", dependencies, depth + 1))
						.setRight(expression(value.getRight(), model, path + "/right", dependencies, depth + 1)))
						.build();
			}
			case INVOCATION -> {
				var value = source.getInvocation();
				if (value.getTarget().hasName() && value.getTarget().getName().getName().equals("date")
						&& source.getInferredType().getBuiltin() == BuiltinType.BUILTIN_TYPE_DATE
						&& value.getArgumentsCount() == 0 && value.getPositionalArgumentsCount() == 1
						&& value.getPositionalArguments(0).hasLiteral()
						&& value.getPositionalArguments(0).getLiteral().getKind() == LiteralKind.LITERAL_KIND_STRING
						&& model.bindings().stream().noneMatch(b -> b.referencePath().equals(path + "/target"))) {
					var date = DmnIrDates.fromFeel(value.getPositionalArguments(0).getLiteral().getValue());
					if (date == null)
						throw unsupported();
					return output.setLiteral(DmnIrLiteral.newBuilder().setDateValue(DmnIrDates.wire(date))).build();
				}
				if (value.getTarget().hasName() && value.getTarget().getName().getName().equals("is")) {
					if (value.getArgumentsCount() > 0) {
						if (value.getArgumentsCount() > 2 || value.getArgumentsList().stream()
								.anyMatch(a -> !"value1".equals(a.getName()) && !"value2".equals(a.getName()))) {
							return nullLiteral();
						}
						Expression v1 = null, v2 = null;
						for (var arg : value.getArgumentsList()) {
							if ("value1".equals(arg.getName()))
								v1 = arg.getExpression();
							else if ("value2".equals(arg.getName()))
								v2 = arg.getExpression();
						}
						var left = v1 != null
								? expression(v1, model, path + "/argument[0]", dependencies, depth + 1)
								: nullLiteral();
						var right = v2 != null
								? expression(v2, model, path + "/argument[1]", dependencies, depth + 1)
								: nullLiteral();
						return output.setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
								.setBinary(DmnIrBinaryExpression.newBuilder()
										.setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL).setLeft(left)
										.setRight(right))
								.build();
					} else if (value.getPositionalArgumentsCount() == 2) {
						var left = expression(value.getPositionalArguments(0), model, path + "/argument[0]",
								dependencies, depth + 1);
						var right = expression(value.getPositionalArguments(1), model, path + "/argument[1]",
								dependencies, depth + 1);
						return output.setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
								.setBinary(DmnIrBinaryExpression.newBuilder()
										.setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL).setLeft(left)
										.setRight(right))
								.build();
					} else {
						return nullLiteral();
					}
				}
				if (value.getTarget().hasName()) {
					String targetName = value.getTarget().getName().getName();
					if (targetName.equals("duration") || targetName.equals("time")
							|| targetName.equals("date and time")) {
						return nullLiteral();
					}
				}
				if (!value.getTarget().hasName() || !value.getTarget().getName().getName().equals("not")
						|| value.getArgumentsCount() != 0 || value.getPositionalArgumentsCount() != 1)
					throw unsupported();
				return output
						.setUnary(DmnIrUnaryExpression.newBuilder().setOperator(DmnIrUnaryOperator.UNARY_OPERATOR_NOT)
								.setOperand(expression(value.getPositionalArguments(0), model, path + "/argument[0]",
										dependencies, depth + 1)))
						.build();
			}
			case BETWEEN -> {
				var between = source.getBetween();
				var left = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
						.setBinary(DmnIrBinaryExpression.newBuilder()
								.setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_GREATER_EQUAL)
								.setLeft(
										expression(between.getValue(), model, path + "/value", dependencies, depth + 1))
								.setRight(expression(between.getLower(), model, path + "/lower", dependencies,
										depth + 1)));
				var right = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
						.setBinary(DmnIrBinaryExpression.newBuilder()
								.setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_LESS_EQUAL)
								.setLeft(
										expression(between.getValue(), model, path + "/value", dependencies, depth + 1))
								.setRight(expression(between.getUpper(), model, path + "/upper", dependencies,
										depth + 1)));
				return output.setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
						.setBinary(DmnIrBinaryExpression.newBuilder()
								.setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_AND).setLeft(left).setRight(right))
						.build();
			}
			case IN -> {
				var in = source.getIn();
				return lowerIn(in.getValue(), in.getTests(), model, path, dependencies, depth);
			}
			case FUNCTION_DEFINITION -> {
				var fn = source.getFunctionDefinition();
				if (fn.getExternal())
					throw unsupported();
				var types = source.getInferredType().hasFunction()
						? source.getInferredType().getFunction().getParameterTypeList()
						: java.util.Collections.<TypeReference>nCopies(fn.getParametersCount(),
								TypeReference.getDefaultInstance());
				var formals = new java.util.ArrayList<InformationItem>();
				for (int i = 0; i < fn.getParametersCount(); i++) {
					var type = i < types.size() ? types.get(i) : TypeReference.getDefaultInstance();
					formals.add(InformationItem.newBuilder()
							.setNode(Node.newBuilder().setName(fn.getParameters(i).getName())).setType(type).build());
				}
				var body = ExpressionParsed.newBuilder().setFeel(FeelParsed.newBuilder().setAst(fn.getBody())).build();
				var def = closure(formals, body, model, path + "/function", depth + 1);
				return output.setType(DmnIrScalarType.SCALAR_TYPE_NULL).setClosure(def).build();
			}
			case CONTEXT -> {
				usedLocalProfile = true;
				var ctx = source.getContext();
				var builder = DmnIrContextLiteral.newBuilder();
				var outer = new HashMap<>(localBindings);
				try {
					for (int i = 0; i < ctx.getEntriesCount(); i++) {
						var entry = ctx.getEntries(i);
						String ep = path + "/context/entry[" + i + "]";
						var val = expression(entry.getExpression(), model, ep, dependencies, depth + 1);
						var valType = expressionType(val);
						var varBuilder = DmnIrVariable.newBuilder().setName(entry.getName());
						if (valType.hasCollectionType()) {
							varBuilder.setCollectionType(valType.getCollectionType());
						} else if (valType.hasRecordType()) {
							varBuilder.setRecordType(valType.getRecordType());
						} else {
							varBuilder.setType(valType.getScalarType());
						}
						var ctxBinding = DmnIrLocalBinding.newBuilder().setBindingId(ep).setVariable(varBuilder)
								.setValue(val).build();
						builder.addEntries(ctxBinding);
						localBindings.put(ep, ctxBinding);
					}
					return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NULL)
							.setContextLiteral(builder).build();
				} finally {
					localBindings.clear();
					localBindings.putAll(outer);
				}
			}
			case RANGE -> {
				var range = source.getRange();
				var builder = DmnIrRangeExpression.newBuilder()
						.setLowerInclusive(range.getLowerBoundary() == RangeBoundary.RANGE_BOUNDARY_CLOSED)
						.setUpperInclusive(range.getUpperBoundary() == RangeBoundary.RANGE_BOUNDARY_CLOSED);
				if (range.hasLower()) {
					builder.setLower(expression(range.getLower(), model, path + "/lower", dependencies, depth + 1));
				}
				if (range.hasUpper()) {
					builder.setUpper(expression(range.getUpper(), model, path + "/upper", dependencies, depth + 1));
				}
				return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NULL).setRange(builder).build();
			}
			case INSTANCE_OF -> {
				var inst = source.getInstanceOf();
				var expr = expression(inst.getExpression(), model, path + "/expression", dependencies, depth + 1);
				var testedType = feelTypeDescriptor(inst.getType(), model);
				return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
						.setInstanceOf(
								DmnIrInstanceOfExpression.newBuilder().setExpression(expr).setTestedType(testedType))
						.build();
			}
			case FOR_EXPRESSION -> {
				usedCollectionProfile = true;
				var forExpr = source.getForExpression();
				var builder = DmnIrForExpression.newBuilder();
				var outer = new HashMap<>(localBindings);
				try {
					if (forExpr.getIterationsCount() > 0) {
						for (int i = 0; i < forExpr.getIterationsCount(); i++) {
							var it = forExpr.getIterations(i);
							String ip = path + "/for/iter[" + i + "]";
							var src = expression(it.getStart(), model, ip + "/start", dependencies, depth + 1);
							var itBuilder = DmnIrForIteration.newBuilder().setBindingId(ip)
									.setVariableName(it.getVariable()).setSource(src);
							if (it.hasEnd()) {
								itBuilder.setRangeEnd(
										expression(it.getEnd(), model, ip + "/end", dependencies, depth + 1));
							}
							builder.addIterations(itBuilder);
							localBindings.put(ip, DmnIrLocalBinding
									.newBuilder().setBindingId(ip).setVariable(DmnIrVariable.newBuilder()
											.setName(it.getVariable()).setType(DmnIrScalarType.SCALAR_TYPE_NULL))
									.build());
						}
					} else {
						String ip = path + "/for/iter[0]";
						var src = expression(forExpr.getIn(), model, ip + "/in", dependencies, depth + 1);
						builder.addIterations(DmnIrForIteration.newBuilder().setBindingId(ip)
								.setVariableName(forExpr.getVariable()).setSource(src));
						localBindings.put(ip,
								DmnIrLocalBinding.newBuilder().setBindingId(ip).setVariable(DmnIrVariable.newBuilder()
										.setName(forExpr.getVariable()).setType(DmnIrScalarType.SCALAR_TYPE_NULL))
										.build());
					}
					String partialId = path + "/for/partial";
					builder.setPartialBindingId(partialId);
					localBindings.put(partialId,
							DmnIrLocalBinding.newBuilder().setBindingId(partialId).setVariable(DmnIrVariable
									.newBuilder().setName("partial").setType(DmnIrScalarType.SCALAR_TYPE_NULL))
									.build());
					var body = expression(forExpr.getReturnExpression(), model, path + "/return", dependencies,
							depth + 1);
					builder.setBody(body);
					DmnIrTypeReference listType = expected != null && expected.hasCollectionType()
							? expected
							: ensureCollectionType(model,
									body.getType() != DmnIrScalarType.SCALAR_TYPE_NULL
											&& body.getType() != DmnIrScalarType.SCALAR_TYPE_UNSPECIFIED
													? body.getType()
													: DmnIrScalarType.SCALAR_TYPE_STRING);
					return typed(listType).setForExpression(builder).build();
				} finally {
					localBindings.clear();
					localBindings.putAll(outer);
				}
			}
			case QUANTIFIED -> {
				usedCollectionProfile = true;
				var q = source.getQuantified();
				var builder = DmnIrQuantifiedExpression.newBuilder()
						.setEvery(q.getQuantifier() == Quantifier.QUANTIFIER_EVERY);
				var outer = new HashMap<>(localBindings);
				try {
					if (q.getBindingsCount() > 0) {
						for (int i = 0; i < q.getBindingsCount(); i++) {
							var b = q.getBindings(i);
							String bp = path + "/quantified/binding[" + i + "]";
							var src = expression(b.getIn(), model, bp + "/in", dependencies, depth + 1);
							builder.addBindings(DmnIrForIteration.newBuilder().setBindingId(bp)
									.setVariableName(b.getVariable()).setSource(src));
							localBindings.put(bp,
									DmnIrLocalBinding
											.newBuilder().setBindingId(bp).setVariable(DmnIrVariable.newBuilder()
													.setName(b.getVariable()).setType(DmnIrScalarType.SCALAR_TYPE_NULL))
											.build());
						}
					} else {
						String bp = path + "/quantified/binding[0]";
						var src = expression(q.getIn(), model, bp + "/in", dependencies, depth + 1);
						builder.addBindings(DmnIrForIteration.newBuilder().setBindingId(bp)
								.setVariableName(q.getVariable()).setSource(src));
						localBindings.put(bp,
								DmnIrLocalBinding
										.newBuilder().setBindingId(bp).setVariable(DmnIrVariable.newBuilder()
												.setName(q.getVariable()).setType(DmnIrScalarType.SCALAR_TYPE_NULL))
										.build());
					}
					builder.setSatisfies(
							expression(q.getSatisfies(), model, path + "/satisfies", dependencies, depth + 1));
					return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
							.setQuantified(builder).build();
				} finally {
					localBindings.clear();
					localBindings.putAll(outer);
				}
			}
			default -> throw unsupported("Unsupported expression node: " + source.getNodeCase() + " at " + path);
		}
	}

	private DmnIrTypeDescriptor feelTypeDescriptor(FeelType ft, DmnSemanticModel model) {
		var b = DmnIrTypeDescriptor.newBuilder();
		return switch (ft.getTypeCase()) {
			case QUALIFIED_NAME -> {
				String qn = ft.getQualifiedName().trim();
				String kind = switch (qn) {
					case "Any", "any" -> "ANY";
					case "Null", "null" -> "NULL";
					case "boolean" -> "BOOLEAN";
					case "number" -> "NUMBER";
					case "string" -> "STRING";
					case "date" -> "DATE";
					case "time" -> "TIME";
					case "date and time" -> "DATE_TIME";
					case "days and time duration" -> "DAYS_TIME_DURATION";
					case "years and months duration" -> "YEARS_MONTHS_DURATION";
					case "duration" -> "DURATION";
					case "list" -> "LIST";
					case "range" -> "RANGE";
					case "context" -> "CONTEXT";
					case "function" -> "FUNCTION";
					default -> null;
				};
				if (kind != null) {
					yield b.setKind(kind).build();
				}
				// Resolve named ItemDefinition in current or imported model.
				var itemOpt = model.model().getItemDefinitionsList().stream()
						.filter(i -> i.getNode().getName().equals(qn)).findFirst();
				if (itemOpt.isPresent()) {
					yield itemTypeDescriptor(itemOpt.get(), model);
				}
				yield b.setKind("ANY").build();
			}
			case LIST ->
				b.setKind("LIST").setElementType(feelTypeDescriptor(ft.getList().getElementType(), model)).build();
			case RANGE ->
				b.setKind("RANGE").setElementType(feelTypeDescriptor(ft.getRange().getElementType(), model)).build();
			case CONTEXT -> {
				b.setKind("CONTEXT");
				for (var entry : ft.getContext().getEntriesList()) {
					b.addFields(DmnIrTypeField.newBuilder().setName(entry.getName())
							.setType(feelTypeDescriptor(entry.getType(), model)));
				}
				yield b.build();
			}
			case FUNCTION -> {
				b.setKind("FUNCTION");
				for (var param : ft.getFunction().getParameterTypesList()) {
					b.addParameterTypes(feelTypeDescriptor(param, model));
				}
				if (ft.getFunction().hasReturnType()) {
					b.setReturnType(feelTypeDescriptor(ft.getFunction().getReturnType(), model));
				}
				yield b.build();
			}
			default -> b.setKind("ANY").build();
		};
	}

	private DmnIrTypeDescriptor itemTypeDescriptor(ItemDefinition item, DmnSemanticModel model) {
		DmnIrTypeDescriptor inner;
		if (item.getComponentsCount() > 0) {
			var ctx = DmnIrTypeDescriptor.newBuilder().setKind("CONTEXT");
			for (var comp : item.getComponentsList()) {
				ctx.addFields(DmnIrTypeField.newBuilder().setName(comp.getNode().getName())
						.setType(typeRefDescriptor(comp.getType(), comp.getIsCollection(), model)));
			}
			inner = ctx.build();
		} else {
			inner = typeRefDescriptor(item.getType(), false, model);
		}
		if (item.getIsCollection()) {
			return DmnIrTypeDescriptor.newBuilder().setKind("LIST").setElementType(inner).build();
		}
		return inner;
	}

	private DmnIrTypeDescriptor typeRefDescriptor(TypeReference tr, boolean isCollection, DmnSemanticModel model) {
		DmnIrTypeDescriptor base;
		if (tr.hasBuiltin()) {
			String kind = switch (tr.getBuiltin()) {
				case BUILTIN_TYPE_NULL -> "NULL";
				case BUILTIN_TYPE_BOOLEAN -> "BOOLEAN";
				case BUILTIN_TYPE_NUMBER -> "NUMBER";
				case BUILTIN_TYPE_STRING -> "STRING";
				case BUILTIN_TYPE_DATE -> "DATE";
				case BUILTIN_TYPE_TIME -> "TIME";
				case BUILTIN_TYPE_DATE_AND_TIME -> "DATE_TIME";
				case BUILTIN_TYPE_DAYS_AND_TIME_DURATION -> "DAYS_TIME_DURATION";
				case BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION -> "YEARS_MONTHS_DURATION";
				case BUILTIN_TYPE_DURATION -> "DURATION";
				case BUILTIN_TYPE_RANGE -> "RANGE";
				default -> "ANY";
			};
			base = DmnIrTypeDescriptor.newBuilder().setKind(kind).build();
		} else if (tr.hasNamed()) {
			String name = tr.getNamed().getName();
			var itemOpt = model.model().getItemDefinitionsList().stream()
					.filter(i -> i.getNode().getName().equals(name)).findFirst();
			base = itemOpt.map(i -> itemTypeDescriptor(i, model))
					.orElseGet(() -> DmnIrTypeDescriptor.newBuilder().setKind("ANY").build());
		} else {
			base = DmnIrTypeDescriptor.newBuilder().setKind("ANY").build();
		}
		if (isCollection) {
			return DmnIrTypeDescriptor.newBuilder().setKind("LIST").setElementType(base).build();
		}
		return base;
	}

	private DmnIrExpression lowerIn(Expression subjectExpr, UnaryTestsExpression tests, DmnSemanticModel model,
			String path, Set<DmnIrReference> dependencies, int depth) {
		if (tests.getWildcard()) {
			return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
					.setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(!tests.getNegated())).build();
		}
		if (tests.getTestsCount() == 0) {
			return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
					.setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(tests.getNegated())).build();
		}
		DmnIrExpression disjunction = null;
		for (int i = 0; i < tests.getTestsCount(); i++) {
			var test = tests.getTests(i);
			String testPath = path + "/tests/test[" + i + "]";
			DmnIrExpression term = lowerPositiveUnaryTest(subjectExpr, test, model, path + "/value", testPath,
					dependencies, depth);
			if (term == null)
				continue;
			if (disjunction == null) {
				disjunction = term;
			} else {
				disjunction = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
						.setBinary(
								DmnIrBinaryExpression.newBuilder().setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_OR)
										.setLeft(disjunction).setRight(term))
						.build();
			}
		}
		if (disjunction == null) {
			return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
					.setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(tests.getNegated())).build();
		}
		if (tests.getNegated()) {
			return DmnIrExpression
					.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN).setUnary(DmnIrUnaryExpression
							.newBuilder().setOperator(DmnIrUnaryOperator.UNARY_OPERATOR_NOT).setOperand(disjunction))
					.build();
		}
		return disjunction;
	}

	private DmnIrExpression lowerPositiveUnaryTest(Expression subjectExpr, PositiveUnaryTest test,
			DmnSemanticModel model, String subjectPath, String testPath, Set<DmnIrReference> dependencies, int depth) {
		switch (test.getTypeCase()) {
			case COMPARISON -> {
				var comp = test.getComparison();
				var op = switch (comp.getOperator()) {
					case UNARY_TEST_OPERATOR_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL;
					case UNARY_TEST_OPERATOR_NOT_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_NOT_EQUAL;
					case UNARY_TEST_OPERATOR_LESS -> DmnIrBinaryOperator.BINARY_OPERATOR_LESS;
					case UNARY_TEST_OPERATOR_LESS_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_LESS_EQUAL;
					case UNARY_TEST_OPERATOR_GREATER -> DmnIrBinaryOperator.BINARY_OPERATOR_GREATER;
					case UNARY_TEST_OPERATOR_GREATER_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_GREATER_EQUAL;
					default -> throw unsupported();
				};
				if (op == DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL) {
					var subjectType = findUnderlyingScalarType(subjectExpr);
					var endpointType = findUnderlyingScalarType(comp.getEndpoint());
					if (subjectType != DmnIrScalarType.SCALAR_TYPE_NULL
							&& endpointType != DmnIrScalarType.SCALAR_TYPE_NULL && subjectType != endpointType) {
						return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
								.setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(false)).build();
					}
				}
				return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
						.setBinary(DmnIrBinaryExpression.newBuilder().setOperator(op)
								.setLeft(expression(subjectExpr, model, subjectPath, dependencies, depth + 1))
								.setRight(expression(comp.getEndpoint(), model, testPath + "/endpoint", dependencies,
										depth + 1)))
						.build();
			}
			case RANGE -> {
				return lowerRangeTest(subjectExpr, test.getRange(), model, subjectPath, testPath, dependencies, depth);
			}
			case EXPRESSION -> {
				return lowerExpressionTest(subjectExpr, test.getExpression(), model, subjectPath, testPath,
						dependencies, depth);
			}
			default -> throw unsupported();
		}
	}

	private DmnIrExpression lowerRangeTest(Expression subjectExpr, RangeExpression range, DmnSemanticModel model,
			String subjectPath, String testPath, Set<DmnIrReference> dependencies, int depth) {
		DmnIrExpression lowerCheck = null;
		if (range.hasLower()) {
			var lowerOp = range.getLowerBoundary() == RangeBoundary.RANGE_BOUNDARY_CLOSED
					? DmnIrBinaryOperator.BINARY_OPERATOR_GREATER_EQUAL
					: DmnIrBinaryOperator.BINARY_OPERATOR_GREATER;
			lowerCheck = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
					.setBinary(DmnIrBinaryExpression.newBuilder().setOperator(lowerOp)
							.setLeft(expression(subjectExpr, model, subjectPath, dependencies, depth + 1)).setRight(
									expression(range.getLower(), model, testPath + "/lower", dependencies, depth + 1)))
					.build();
		}
		DmnIrExpression upperCheck = null;
		if (range.hasUpper()) {
			var upperOp = range.getUpperBoundary() == RangeBoundary.RANGE_BOUNDARY_CLOSED
					? DmnIrBinaryOperator.BINARY_OPERATOR_LESS_EQUAL
					: DmnIrBinaryOperator.BINARY_OPERATOR_LESS;
			upperCheck = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
					.setBinary(DmnIrBinaryExpression.newBuilder().setOperator(upperOp)
							.setLeft(expression(subjectExpr, model, subjectPath, dependencies, depth + 1)).setRight(
									expression(range.getUpper(), model, testPath + "/upper", dependencies, depth + 1)))
					.build();
		}
		if (lowerCheck != null && upperCheck != null) {
			return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
					.setBinary(DmnIrBinaryExpression.newBuilder().setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_AND)
							.setLeft(lowerCheck).setRight(upperCheck))
					.build();
		}
		if (lowerCheck != null)
			return lowerCheck;
		if (upperCheck != null)
			return upperCheck;
		return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
				.setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(true)).build();
	}

	private DmnIrExpression lowerExpressionTest(Expression subjectExpr, Expression testExpr, DmnSemanticModel model,
			String subjectPath, String testPath, Set<DmnIrReference> dependencies, int depth) {
		boolean subjectIsList = subjectExpr.hasList()
				|| (subjectExpr.hasInferredType() && subjectExpr.getInferredType().hasList());
		if (testExpr.hasList() && !subjectIsList) {
			var list = testExpr.getList();
			DmnIrExpression disjunction = null;
			for (int i = 0; i < list.getElementsCount(); i++) {
				var elem = list.getElements(i);
				DmnIrExpression term = lowerExpressionTest(subjectExpr, elem, model, subjectPath,
						testPath + "/element[" + i + "]", dependencies, depth);
				if (term == null)
					continue;
				if (disjunction == null) {
					disjunction = term;
				} else {
					disjunction = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
							.setBinary(DmnIrBinaryExpression.newBuilder()
									.setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_OR).setLeft(disjunction)
									.setRight(term))
							.build();
				}
			}
			return disjunction;
		}
		if (testExpr.hasRange()) {
			return lowerRangeTest(subjectExpr, testExpr.getRange(), model, subjectPath, testPath, dependencies, depth);
		}
		var subjectType = findUnderlyingScalarType(subjectExpr);
		var testType = findUnderlyingScalarType(testExpr);
		if (subjectType != DmnIrScalarType.SCALAR_TYPE_NULL && testType != DmnIrScalarType.SCALAR_TYPE_NULL
				&& subjectType != testType) {
			// In FEEL Table 49 / Clause 10.3.2.14: an element of an incompatible type in a
			// positive unary test
			// does not match the subject, evaluating to false (not null).
			return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
					.setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(false)).build();
		}
		return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
				.setBinary(DmnIrBinaryExpression.newBuilder().setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL)
						.setLeft(expression(subjectExpr, model, subjectPath, dependencies, depth + 1))
						.setRight(expression(testExpr, model, testPath, dependencies, depth + 1)))
				.build();
	}

	private DmnIrExpression closureCall(InvocationExpression source, Optional<DmnIrLocalBinding> binding,
			DmnSemanticModel model, String path, Set<DmnIrReference> dependencies, int depth) {
		if (captures != null || source.getArgumentsCount() != 0)
			throw unsupported();
		var call = DmnIrClosureInvocation.newBuilder();
		DmnIrClosure definition;
		if (binding.isPresent()) {
			definition = binding.get().getClosure().getDefinition();
			call.setLocalBindingId(binding.get().getBindingId());
		} else {
			var target = source.getTarget();
			var inline = target.getFunctionDefinition();
			if (inline.getExternal() || !target.getInferredType().hasFunction())
				throw unsupported();
			var types = target.getInferredType().getFunction().getParameterTypeList();
			var formals = new ArrayList<InformationItem>();
			for (int i = 0; i < inline.getParametersCount(); i++)
				formals.add(InformationItem.newBuilder()
						.setNode(Node.newBuilder().setName(inline.getParameters(i).getName())).setType(types.get(i))
						.build());
			var body = ExpressionParsed.newBuilder().setFeel(FeelParsed.newBuilder().setAst(inline.getBody())).build();
			definition = closure(formals, body, model, path + "/target", depth + 1);
			call.setInline(definition);
		}
		if (source.getPositionalArgumentsCount() != definition.getParametersCount())
			throw unsupported();
		for (int i = 0; i < source.getPositionalArgumentsCount(); i++) {
			call.addArguments(expression(source.getPositionalArguments(i), model, path + "/argument[" + i + "]",
					dependencies, depth + 1));
		}
		return typed(definition.getResultType()).setClosureInvocation(call).build();
	}

	private DmnIrClosure closure(List<InformationItem> formals, ExpressionParsed source, DmnSemanticModel model,
			String path, int depth) {
		if (captures != null || depth > 32)
			throw unsupported();
		var outer = new HashMap<>(localBindings);
		captureScope = outer.keySet();
		captures = new TreeSet<>((left, right) -> Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8),
				right.getBytes(StandardCharsets.UTF_8)));
		try {
			var definition = DmnIrClosure.newBuilder();
			for (int i = 0; i < formals.size(); i++) {
				String id = path + "/parameter[" + i + "]";
				var formal = variable(formals.get(i), model);
				localBindings.put(id, DmnIrLocalBinding.newBuilder().setBindingId(id).setVariable(formal).build());
				definition.addParameters(DmnIrBkmParameter.newBuilder().setBindingId(id).setVariable(formal));
			}
			Set<DmnIrReference> globals = new HashSet<>();
			var body = parsed(source, model, path + "/body", globals, depth + 1, null);
			if (!globals.isEmpty())
				throw unsupported();
			closures = true;
			return definition.setResultType(expressionType(body)).addAllCapturedBindingIds(captures).setBody(body)
					.build();
		} finally {
			captures = null;
			captureScope = Set.of();
			localBindings.clear();
			localBindings.putAll(outer);
		}
	}
	private Optional<DmnIrReference> boundBkm(DmnSemanticModel model, String path) {
		return model.bindings().stream().filter(binding -> binding.referencePath().equals(path))
				.map(binding -> ref(
						binding.targetNamespace().isBlank() ? model.identity().namespace() : binding.targetNamespace(),
						binding.symbolId()))
				.filter(bkms::containsKey).findFirst();
	}

	private DmnIrExpression bkmCall(InvocationExpression source, DmnSemanticModel model, String targetPath,
			Set<DmnIrReference> dependencies, int depth, java.util.function.IntFunction<String> argumentPath) {
		var target = boundBkm(model, targetPath).orElseThrow(DmnIrRecordProducer::unsupported);
		if (!knowledge.contains(target) || source.getArgumentsCount() > 0 && source.getPositionalArgumentsCount() > 0)
			throw unsupported();
		var bkm = bkms.get(target);
		var owner = models.get(target.getModelNamespace());
		var parameters = bkm.getFunction().getFormalParametersList();
		var call = DmnIrBkmCall.newBuilder().setTarget(target);
		boolean named = source.getArgumentsCount() > 0;
		int count = named ? source.getArgumentsCount() : source.getPositionalArgumentsCount();
		if (count != parameters.size())
			throw unsupported();
		Map<String, Integer> positions = new HashMap<>();
		for (int i = 0; i < parameters.size(); i++)
			positions.put(parameters.get(i).getNode().getName(), i);
		for (int i = 0; i < count; i++) {
			Integer position = named ? positions.get(source.getArguments(i).getName()) : Integer.valueOf(i);
			if (position == null)
				throw unsupported();
			var value = named ? source.getArguments(i).getExpression() : source.getPositionalArguments(i);
			var parameterType = type(parameters.get(position).getType(), owner);
			call.addArguments(DmnIrBkmArgument.newBuilder().setParameterPosition(position + 1).setExpression(
					expression(value, model, argumentPath.apply(i), dependencies, depth + 1, parameterType)));
		}
		return typed(type(bkm.getVariable().getType(), owner)).setBkmCall(call).build();
	}
	private DmnIrTypeReference ensureCollectionType(DmnSemanticModel model, DmnIrScalarType element) {
		String base = "$dmnir$builtin-list-" + element.name().toLowerCase(java.util.Locale.ROOT);
		String id = base;
		while (named.containsValue(ref(model.identity().namespace(), id))
				|| records.containsKey(ref(model.identity().namespace(), id)))
			id += "$";
		var reference = ref(model.identity().namespace(), id);
		if (!collections.containsKey(reference)) {
			collections.put(reference,
					DmnIrCollectionDefinition.newBuilder()
							.setIdentity(DmnIrElementIdentity.newBuilder().setId(id).setName(id))
							.setElementType(element).build());
			collectionTypes.add(reference);
		}
		return DmnIrTypeReference.newBuilder().setCollectionType(reference).build();
	}

	private DmnIrExpression collectionBuiltinCall(InvocationExpression source, DmnIrBuiltinSignature signature,
			DmnSemanticModel model, String path, Set<DmnIrReference> dependencies, int depth,
			DmnIrTypeReference expected) {
		int count = source.getPositionalArgumentsCount();
		Expression single = null;
		if (source.getArgumentsCount() > 0) {
			if (count != 0 || source.getArgumentsCount() != 1 || !source.getArguments(0).getName().equals("list"))
				return nullLiteral();
			single = source.getArguments(0).getExpression();
			count = 1;
		} else if (count == 1)
			single = source.getPositionalArguments(0);
		if (count == 0)
			return nullLiteral();
		var element = signature.parameters().getFirst();
		if (element == DmnIrScalarType.SCALAR_TYPE_NULL) {
			if (expected != null && expected.hasCollectionType()) {
				var colDef = collections.get(expected.getCollectionType());
				if (colDef != null && colDef.getElementType() != DmnIrScalarType.SCALAR_TYPE_NULL
						&& colDef.getElementType() != DmnIrScalarType.SCALAR_TYPE_UNSPECIFIED) {
					element = colDef.getElementType();
				}
			}
			if (element == DmnIrScalarType.SCALAR_TYPE_NULL && count > 0) {
				Expression probe = single != null ? single : source.getPositionalArguments(0);
				var inferred = findUnderlyingScalarType(probe);
				if (inferred != DmnIrScalarType.SCALAR_TYPE_NULL) {
					element = inferred;
				}
			}
		}
		var expectedListType = element != DmnIrScalarType.SCALAR_TYPE_NULL
				? ensureCollectionType(model, element)
				: null;
		DmnIrExpression argument;
		if (single != null) {
			argument = expression(single, model, path + "/argument[0]", dependencies, depth + 1, expectedListType);
			var actual = expressionType(argument);
			if (actual.hasScalarType() && actual.getScalarType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
				if (actual.getScalarType() != element && element != DmnIrScalarType.SCALAR_TYPE_NULL)
					return nullLiteral();
				if (expectedListType == null) {
					expectedListType = ensureCollectionType(model, actual.getScalarType());
				}
				argument = typed(expectedListType).setList(DmnIrListExpression.newBuilder().addElements(argument))
						.build();
			}
		} else {
			var list = DmnIrListExpression.newBuilder();
			for (int i = 0; i < count; i++) {
				var value = expression(source.getPositionalArguments(i), model, path + "/argument[" + i + "]",
						dependencies, depth + 1);
				var actual = expressionType(value);
				if (element != DmnIrScalarType.SCALAR_TYPE_NULL
						&& (!actual.hasScalarType() || actual.getScalarType() != element
								&& actual.getScalarType() != DmnIrScalarType.SCALAR_TYPE_NULL))
					return nullLiteral();
				if (element == DmnIrScalarType.SCALAR_TYPE_NULL && actual.hasScalarType()
						&& actual.getScalarType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
					element = actual.getScalarType();
				}
				list.addElements(value);
			}
			if (expectedListType == null && element != DmnIrScalarType.SCALAR_TYPE_NULL) {
				expectedListType = ensureCollectionType(model, element);
			}
			if (expectedListType == null)
				return nullLiteral();
			argument = typed(expectedListType).setList(list).build();
		}
		builtinCalls = true;
		var call = DmnIrBuiltinCall.newBuilder().setOperation(signature.operation()).addArguments(argument);
		if (signature.collectionResult()) {
			if (argument.getType() == DmnIrScalarType.SCALAR_TYPE_NULL && !argument.hasCollectionType()) {
				return nullLiteral();
			}
			DmnIrTypeReference resultType = expected != null && expected.hasCollectionType()
					? expected
					: argument.hasCollectionType()
							? DmnIrTypeReference.newBuilder().setCollectionType(argument.getCollectionType()).build()
							: ensureCollectionType(model, signature.result());
			return typed(resultType).setBuiltinCall(call).build();
		}
		return DmnIrExpression.newBuilder().setType(signature.result()).setBuiltinCall(call).build();
	}

	private DmnIrExpression builtinCall(InvocationExpression source, DmnIrBuiltinSignature signature,
			DmnSemanticModel model, String path, Set<DmnIrReference> dependencies, int depth,
			DmnIrTypeReference expected) {
		String name = signature.feelName();
		if (localBindings.values().stream().anyMatch(local -> local.getVariable().getName().equals(name))
				|| model.bindings().stream().anyMatch(binding -> binding.referencePath().equals(path + "/target")))
			throw unsupported();

		if (signature.collectionParameter() && signature != DmnIrBuiltinSignature.STRING_JOIN
				&& signature != DmnIrBuiltinSignature.INDEX_OF && signature != DmnIrBuiltinSignature.LIST_REPLACE
				&& signature != DmnIrBuiltinSignature.INSERT_BEFORE && signature != DmnIrBuiltinSignature.REMOVE)
			return collectionBuiltinCall(source, signature, model, path, dependencies, depth, expected);

		boolean named = source.getArgumentsCount() > 0;
		int positionalCount = source.getPositionalArgumentsCount();
		int paramCount = signature.parameters().size();

		if (named) {
			if (source.getPositionalArgumentsCount() > 0)
				return nullLiteral();
			if (source.getArgumentsCount() < signature.minArity() || source.getArgumentsCount() > signature.maxArity())
				return nullLiteral();
		} else {
			if (positionalCount < signature.minArity() || positionalCount > signature.maxArity())
				return nullLiteral();
		}

		if (signature == DmnIrBuiltinSignature.NUMBER_FROM_STRING
				&& (named ? source.getArgumentsCount() : positionalCount) == 2)
			return nullLiteral();
		DmnIrExpression[] args = new DmnIrExpression[paramCount];
		if (signature == DmnIrBuiltinSignature.STRING_JOIN) {
			DmnIrTypeReference stringListType = ensureCollectionType(model, DmnIrScalarType.SCALAR_TYPE_STRING);
			if (named) {
				for (int i = 0; i < source.getArgumentsCount(); i++) {
					var namedArg = source.getArguments(i);
					Integer pos = signature.parameterIndex(namedArg.getName());
					if (pos == null || pos >= paramCount || args[pos] != null)
						return nullLiteral();
					try {
						DmnIrExpression argExpr;
						if (pos == 0) {
							argExpr = expression(namedArg.getExpression(), model, path + "/argument[" + i + "]",
									dependencies, depth + 1, stringListType);
							var actual = expressionType(argExpr);
							if (actual.hasScalarType() && actual.getScalarType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
								if (actual.getScalarType() != DmnIrScalarType.SCALAR_TYPE_STRING)
									return nullLiteral();
								argExpr = typed(stringListType)
										.setList(DmnIrListExpression.newBuilder().addElements(argExpr)).build();
							} else if (actual.hasCollectionType()) {
								var colDef = collections.get(actual.getCollectionType());
								if (colDef == null || colDef.getElementType() != DmnIrScalarType.SCALAR_TYPE_STRING)
									return nullLiteral();
							} else if (actual.getScalarType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
								return nullLiteral();
							}
						} else {
							argExpr = expression(namedArg.getExpression(), model, path + "/argument[" + i + "]",
									dependencies, depth + 1);
							if (argExpr.getType() != DmnIrScalarType.SCALAR_TYPE_STRING
									&& argExpr.getType() != DmnIrScalarType.SCALAR_TYPE_NULL)
								return nullLiteral();
						}
						args[pos] = argExpr;
					} catch (DmnIrProductionException ex) {
						throw ex;
					} catch (RuntimeException ex) {
						return nullLiteral();
					}
				}
				for (int i = 0; i < signature.minArity(); i++) {
					if (args[i] == null)
						return nullLiteral();
				}
				fillOptionalArguments(signature, args);
			} else {
				for (int i = 0; i < positionalCount; i++) {
					try {
						DmnIrExpression argExpr;
						if (i == 0) {
							argExpr = expression(source.getPositionalArguments(0), model, path + "/argument[0]",
									dependencies, depth + 1, stringListType);
							var actual = expressionType(argExpr);
							if (actual.hasScalarType() && actual.getScalarType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
								if (actual.getScalarType() != DmnIrScalarType.SCALAR_TYPE_STRING)
									return nullLiteral();
								argExpr = typed(stringListType)
										.setList(DmnIrListExpression.newBuilder().addElements(argExpr)).build();
							} else if (actual.hasCollectionType()) {
								var colDef = collections.get(actual.getCollectionType());
								if (colDef == null || colDef.getElementType() != DmnIrScalarType.SCALAR_TYPE_STRING)
									return nullLiteral();
							} else if (actual.getScalarType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
								return nullLiteral();
							}
						} else {
							argExpr = expression(source.getPositionalArguments(i), model, path + "/argument[" + i + "]",
									dependencies, depth + 1);
							if (argExpr.getType() != DmnIrScalarType.SCALAR_TYPE_STRING
									&& argExpr.getType() != DmnIrScalarType.SCALAR_TYPE_NULL)
								return nullLiteral();
						}
						args[i] = argExpr;
					} catch (DmnIrProductionException ex) {
						throw ex;
					} catch (RuntimeException ex) {
						return nullLiteral();
					}
				}
				fillOptionalArguments(signature, args);
			}
		} else if (signature == DmnIrBuiltinSignature.STRING_FROM) {
			if (named) {
				if (source.getArgumentsCount() != 1 || !"from".equals(source.getArguments(0).getName()))
					return nullLiteral();
				args[0] = expression(source.getArguments(0).getExpression(), model, path + "/argument[0]", dependencies,
						depth + 1);
			} else {
				if (positionalCount != 1)
					return nullLiteral();
				args[0] = expression(source.getPositionalArguments(0), model, path + "/argument[0]", dependencies,
						depth + 1);
			}
		} else if (named) {
			for (int i = 0; i < source.getArgumentsCount(); i++) {
				var namedArg = source.getArguments(i);
				Integer pos = signature.parameterIndex(namedArg.getName());
				if (pos == null || pos >= paramCount || args[pos] != null)
					return nullLiteral();
				try {
					var argExpr = expression(namedArg.getExpression(), model, path + "/argument[" + i + "]",
							dependencies, depth + 1);
					var expectedParamType = signature.parameters().get(pos);
					if (signature.collectionParameter(pos)) {
						if (!argExpr.hasCollectionType() && argExpr.getType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
							// FEEL collection coercion: scalar to singleton list
							var colType = ensureCollectionType(model, argExpr.getType());
							argExpr = typed(colType).setList(DmnIrListExpression.newBuilder().addElements(argExpr))
									.build();
						}
					} else if (expectedParamType != DmnIrScalarType.SCALAR_TYPE_NULL
							&& argExpr.getType() != expectedParamType
							&& argExpr.getType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
						return nullLiteral();
					}
					args[pos] = argExpr;
				} catch (DmnIrProductionException ex) {
					throw ex;
				} catch (RuntimeException ex) {
					return nullLiteral();
				}
			}
			for (int i = 0; i < signature.minArity(); i++) {
				if (args[i] == null)
					return nullLiteral();
			}
			fillOptionalArguments(signature, args);
		} else {
			for (int i = 0; i < positionalCount; i++) {
				try {
					var argExpr = expression(source.getPositionalArguments(i), model, path + "/argument[" + i + "]",
							dependencies, depth + 1);
					var expectedParamType = signature.parameters().get(i);
					if (signature.collectionParameter(i)) {
						if (!argExpr.hasCollectionType() && argExpr.getType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
							// FEEL collection coercion: scalar to singleton list
							var colType = ensureCollectionType(model, argExpr.getType());
							argExpr = typed(colType).setList(DmnIrListExpression.newBuilder().addElements(argExpr))
									.build();
						}
					} else if (expectedParamType != DmnIrScalarType.SCALAR_TYPE_NULL
							&& argExpr.getType() != expectedParamType
							&& argExpr.getType() != DmnIrScalarType.SCALAR_TYPE_NULL) {
						return nullLiteral();
					}
					args[i] = argExpr;
				} catch (DmnIrProductionException ex) {
					throw ex;
				} catch (RuntimeException ex) {
					return nullLiteral();
				}
			}
			fillOptionalArguments(signature, args);
		}

		var call = DmnIrBuiltinCall.newBuilder().setOperation(signature.operation());
		for (int i = 0; i < args.length; i++) {
			if (args[i] == null) {
				if (i >= signature.minArity())
					break;
				return nullLiteral();
			}
			call.addArguments(args[i]);
		}
		builtinCalls = true;
		if (signature.collectionResult()) {
			if (args[0].getType() == DmnIrScalarType.SCALAR_TYPE_NULL && !args[0].hasCollectionType()) {
				return nullLiteral();
			}
			DmnIrTypeReference resultType = expected != null && expected.hasCollectionType()
					? expected
					: args[0].hasCollectionType()
							? DmnIrTypeReference.newBuilder().setCollectionType(args[0].getCollectionType()).build()
							: (signature.result() != DmnIrScalarType.SCALAR_TYPE_NULL
									? ensureCollectionType(model, signature.result())
									: DmnIrTypeReference.newBuilder().setScalarType(DmnIrScalarType.SCALAR_TYPE_NULL)
											.build());
			return typed(resultType).setBuiltinCall(call).build();
		}
		return DmnIrExpression.newBuilder().setType(signature.result()).setBuiltinCall(call).build();
	}

	private static DmnIrExpression nullLiteral() {
		return DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NULL)
				.setLiteral(DmnIrLiteral.newBuilder().setNullValue(DmnIrNull.NULL_VALUE)).build();
	}

	private static void fillOptionalArguments(DmnIrBuiltinSignature signature, DmnIrExpression[] args) {
		if (signature == DmnIrBuiltinSignature.FLOOR || signature == DmnIrBuiltinSignature.CEILING) {
			if (args[1] == null) {
				args[1] = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER)
						.setLiteral(DmnIrLiteral.newBuilder()
								.setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("0").setScale(0)))
						.build();
			}
		} else if (signature == DmnIrBuiltinSignature.NUMBER_FROM_STRING) {
			if (args[1] == null)
				args[1] = nullLiteral();
			if (args[2] == null)
				args[2] = nullLiteral();
		} else if (signature == DmnIrBuiltinSignature.SUBSTRING) {
			if (args[2] == null) {
				args[2] = nullLiteral();
			}
		} else if (signature == DmnIrBuiltinSignature.MATCHES) {
			if (args[2] == null) {
				args[2] = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_STRING)
						.setLiteral(DmnIrLiteral.newBuilder().setStringValue("")).build();
			}
		} else if (signature == DmnIrBuiltinSignature.REPLACE) {
			if (args[3] == null) {
				args[3] = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_STRING)
						.setLiteral(DmnIrLiteral.newBuilder().setStringValue("")).build();
			}
		} else if (signature == DmnIrBuiltinSignature.STRING_JOIN) {
			if (args[1] == null) {
				args[1] = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_STRING)
						.setLiteral(DmnIrLiteral.newBuilder().setStringValue("")).build();
			}
		}
	}
	private DmnIrExpression parsed(ExpressionParsed source, DmnSemanticModel model, String path,
			Set<DmnIrReference> dependencies, int depth, DmnIrTypeReference expected) {
		if (source.hasFeel())
			return expression(source.getFeel().getAst(), model, path, dependencies, depth, expected);
		if (source.hasBoxed())
			return boxed(source.getBoxed(), model, path, dependencies, depth, expected);
		throw unsupported();
	}
	private DmnIrExpression boxed(BoxedExpressionParsed source, DmnSemanticModel model, String path,
			Set<DmnIrReference> dependencies, int depth, DmnIrTypeReference expected) {
		if (depth > 32 || !source.hasContext())
			throw unsupported();
		var context = source.getContext();
		if (context.getEntriesCount() == 0)
			throw unsupported();
		var output = DmnIrContextExpression.newBuilder();
		var outer = new HashMap<>(localBindings);
		try {
			for (int i = 0; i < context.getEntriesCount(); i++) {
				var entry = context.getEntries(i);
				String ep = path + "/context/entry[" + i + "]";
				if (!entry.hasExpression())
					throw unsupported();
				if (i == context.getEntriesCount() - 1) {
					if (entry.hasVariable() && !entry.getVariable().getNode().getName().isBlank())
						throw unsupported();
					output.setResult(parsed(entry.getExpression(), model, ep + "/expression", dependencies, depth + 1,
							expected));
				} else {
					if (!entry.hasVariable() || entry.getVariable().getNode().getName().isBlank())
						throw unsupported();
					if (entry.getExpression().getBoxed().hasFunctionDefinition()) {
						var function = entry.getExpression().getBoxed().getFunctionDefinition();
						var item = entry.getVariable();
						if (!function.getSourceKind().isEmpty() && !function.getSourceKind().equals("FEEL"))
							throw unsupported();
						if (item.getType().getKindCase() != TypeReference.KindCase.KIND_NOT_SET || !function.hasBody())
							throw unsupported();
						var local = DmnIrClosureBinding.newBuilder().setName(item.getNode().getName())
								.setDefinition(closure(function.getParametersList(), function.getBody(), model,
										ep + "/expression", depth + 1));
						if (!item.getNode().getId().isBlank())
							local.setId(item.getNode().getId());
						var binding = DmnIrLocalBinding.newBuilder().setBindingId(ep).setClosure(local).build();
						output.addBindings(binding);
						localBindings.put(ep, binding);
						continue;
					}
					var variable = variable(entry.getVariable(), model);
					var value = parsed(entry.getExpression(), model, ep + "/expression", dependencies, depth + 1,
							type(entry.getVariable().getType(), model));
					var binding = DmnIrLocalBinding.newBuilder().setBindingId(ep).setVariable(variable).setValue(value)
							.build();
					output.addBindings(binding);
					localBindings.put(ep, binding);
				}
			}
			return typed(expected == null ? expressionType(output.getResult()) : expected).setContext(output).build();
		} finally {
			localBindings.clear();
			localBindings.putAll(outer);
		}
	}
	private static DmnIrBinaryOperator binary(BinaryOperator op) {
		return switch (op) {
			case BINARY_OPERATOR_ADD -> DmnIrBinaryOperator.BINARY_OPERATOR_ADD;
			case BINARY_OPERATOR_SUBTRACT -> DmnIrBinaryOperator.BINARY_OPERATOR_SUBTRACT;
			case BINARY_OPERATOR_MULTIPLY -> DmnIrBinaryOperator.BINARY_OPERATOR_MULTIPLY;
			case BINARY_OPERATOR_DIVIDE -> DmnIrBinaryOperator.BINARY_OPERATOR_DIVIDE;
			case BINARY_OPERATOR_POWER -> DmnIrBinaryOperator.BINARY_OPERATOR_POWER;
			case BINARY_OPERATOR_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL;
			case BINARY_OPERATOR_NOT_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_NOT_EQUAL;
			case BINARY_OPERATOR_LESS -> DmnIrBinaryOperator.BINARY_OPERATOR_LESS;
			case BINARY_OPERATOR_LESS_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_LESS_EQUAL;
			case BINARY_OPERATOR_GREATER -> DmnIrBinaryOperator.BINARY_OPERATOR_GREATER;
			case BINARY_OPERATOR_GREATER_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_GREATER_EQUAL;
			case BINARY_OPERATOR_AND -> DmnIrBinaryOperator.BINARY_OPERATOR_AND;
			case BINARY_OPERATOR_OR -> DmnIrBinaryOperator.BINARY_OPERATOR_OR;
			default -> throw unsupported();
		};
	}
	private DmnIrReference requirement(ElementReference reference, DmnSemanticModel model) {
		var target = address(reference, model);
		if (!declarations.containsKey(target))
			throw unsupported();
		return target;
	}
	private DmnIrReference address(ElementReference reference, DmnSemanticModel model) {
		String href = reference.getHref();
		int hash = href.lastIndexOf('#');
		String namespace = hash <= 0 ? model.identity().namespace() : href.substring(0, hash);
		String id = hash < 0 ? href : href.substring(hash + 1);
		final String qualifier = namespace;
		namespace = model.model().getImportsList().stream().filter(i -> i.getName().equals(qualifier))
				.map(io.finmsg.dmn.model.Import::getNamespace).findFirst().orElse(namespace);
		return ref(namespace, id);
	}
	private static DmnIrTypeReference expressionType(DmnIrExpression value) {
		var type = DmnIrTypeReference.newBuilder();
		if (value.hasCollectionType())
			return type.setCollectionType(value.getCollectionType()).build();
		if (value.hasRecordType())
			return type.setRecordType(value.getRecordType()).build();
		return type.setScalarType(value.getType()).build();
	}
	private static DmnIrExpression.Builder typed(DmnIrTypeReference type) {
		var b = DmnIrExpression.newBuilder();
		if (type.hasCollectionType())
			return b.setCollectionType(type.getCollectionType());
		return type.hasRecordType() ? b.setRecordType(type.getRecordType()) : b.setType(type.getScalarType());
	}
	private static String itemId(ItemDefinition item) {
		String id = item.getNode().getId();
		if (!id.isBlank())
			return id;
		String name = item.getNode().getName();
		if (!name.isBlank())
			return name;
		throw unsupported();
	}
	private static DmnIrElementIdentity identity(Node node) {
		String id = node.getId().isBlank() ? node.getName() : node.getId();
		return DmnIrElementIdentity.newBuilder().setId(id).setName(node.getName().isBlank() ? id : node.getName())
				.build();
	}
	private static DmnIrElementIdentity identity(Node node, String id) {
		return DmnIrElementIdentity.newBuilder().setId(id).setName(node.getName().isBlank() ? id : node.getName())
				.build();
	}
	private DmnIrTypeReference resolveItemType(ItemDefinition item, DmnSemanticModel model, Set<TypeName> visited) {
		var typeName = new TypeName(model.identity().namespace(), item.getNode().getName());
		if (visited.contains(typeName))
			throw unsupported();
		visited.add(typeName);
		if (item.hasType()) {
			var t = item.getType();
			if (t.hasBuiltin()) {
				return DmnIrTypeReference.newBuilder().setScalarType(switch (t.getBuiltin()) {
					case BUILTIN_TYPE_NULL, BUILTIN_TYPE_ANY, BUILTIN_TYPE_UNSPECIFIED, BUILTIN_TYPE_RANGE ->
						DmnIrScalarType.SCALAR_TYPE_NULL;
					case BUILTIN_TYPE_BOOLEAN -> DmnIrScalarType.SCALAR_TYPE_BOOLEAN;
					case BUILTIN_TYPE_NUMBER -> DmnIrScalarType.SCALAR_TYPE_NUMBER;
					case BUILTIN_TYPE_STRING -> DmnIrScalarType.SCALAR_TYPE_STRING;
					case BUILTIN_TYPE_DATE -> DmnIrScalarType.SCALAR_TYPE_DATE;
					case BUILTIN_TYPE_TIME -> DmnIrScalarType.SCALAR_TYPE_TIME;
					case BUILTIN_TYPE_DATE_AND_TIME -> DmnIrScalarType.SCALAR_TYPE_DATE_TIME;
					case BUILTIN_TYPE_DURATION, BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION,
							BUILTIN_TYPE_DAYS_AND_TIME_DURATION ->
						DmnIrScalarType.SCALAR_TYPE_DURATION;
					default -> throw unsupported();
				}).build();
			}
			if (t.hasNamed()) {
				var targetName = t.getNamed().getName();
				var targetNs = t.getNamed().getNamespace().isBlank()
						? model.identity().namespace()
						: t.getNamed().getNamespace();
				if (targetName.contains(".")) {
					String alias = targetName.substring(0, targetName.indexOf('.'));
					var imported = model.model().getImportsList().stream().filter(i -> i.getName().equals(alias))
							.findFirst();
					if (imported.isPresent()) {
						targetNs = imported.get().getNamespace();
						targetName = targetName.substring(targetName.indexOf('.') + 1);
					}
				}
				final String qualifier = targetNs;
				targetNs = model.model().getImportsList().stream().filter(i -> i.getName().equals(qualifier))
						.map(io.finmsg.dmn.model.Import::getNamespace).findFirst().orElse(targetNs);
				var targetTypeName = new TypeName(targetNs, targetName);
				if (typeAliases.containsKey(targetTypeName)) {
					return typeAliases.get(targetTypeName);
				}
				final String searchName = targetName;
				var targetModel = models.get(targetNs);
				if (targetModel != null) {
					var targetItem = targetModel.model().getItemDefinitionsList().stream()
							.filter(i -> i.getNode().getName().equals(searchName)).findFirst();
					if (targetItem.isPresent() && targetItem.get().getComponentsCount() == 0
							&& !targetItem.get().getIsCollection()) {
						var resolved = resolveItemType(targetItem.get(), targetModel, visited);
						typeAliases.put(targetTypeName, resolved);
						return resolved;
					}
				}
				var ref = named.get(targetTypeName);
				if (ref != null) {
					if (collectionTypes.contains(ref))
						return DmnIrTypeReference.newBuilder().setCollectionType(ref).build();
					return DmnIrTypeReference.newBuilder().setRecordType(ref).build();
				}
			}
		}
		return DmnIrTypeReference.newBuilder().setScalarType(DmnIrScalarType.SCALAR_TYPE_NULL).build();
	}
	private static DmnIrScalarType findUnderlyingScalarType(Expression expr) {
		if (expr == null)
			return DmnIrScalarType.SCALAR_TYPE_NULL;
		if (expr.hasInferredType() && expr.getInferredType().hasBuiltin()) {
			var st = switch (expr.getInferredType().getBuiltin()) {
				case BUILTIN_TYPE_STRING -> DmnIrScalarType.SCALAR_TYPE_STRING;
				case BUILTIN_TYPE_NUMBER -> DmnIrScalarType.SCALAR_TYPE_NUMBER;
				case BUILTIN_TYPE_BOOLEAN -> DmnIrScalarType.SCALAR_TYPE_BOOLEAN;
				case BUILTIN_TYPE_DATE -> DmnIrScalarType.SCALAR_TYPE_DATE;
				default -> DmnIrScalarType.SCALAR_TYPE_NULL;
			};
			if (st != DmnIrScalarType.SCALAR_TYPE_NULL)
				return st;
		}
		if (expr.hasList()) {
			for (var el : expr.getList().getElementsList()) {
				var st = findUnderlyingScalarType(el);
				if (st != DmnIrScalarType.SCALAR_TYPE_NULL)
					return st;
			}
		}
		if (expr.hasLiteral()) {
			var value = expr.getLiteral();
			return switch (value.getKind()) {
				case LITERAL_KIND_STRING -> DmnIrScalarType.SCALAR_TYPE_STRING;
				case LITERAL_KIND_NUMBER -> DmnIrScalarType.SCALAR_TYPE_NUMBER;
				case LITERAL_KIND_BOOLEAN -> DmnIrScalarType.SCALAR_TYPE_BOOLEAN;
				case LITERAL_KIND_DATE -> DmnIrScalarType.SCALAR_TYPE_DATE;
				default -> DmnIrScalarType.SCALAR_TYPE_NULL;
			};
		}
		return DmnIrScalarType.SCALAR_TYPE_NULL;
	}

	private static DmnIrReference ref(String namespace, String id) {
		return DmnIrRecordTypes.ref(namespace, id);
	}
	private static DmnIrProductionException unsupported(String message) {
		return new DmnIrProductionException(DmnIrProductionException.Category.UNSUPPORTED_PROFILE, message, null,
				List.of(), null);
	}
	private static DmnIrProductionException unsupported() {
		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
		return unsupported("Unsupported construct at " + caller.getFileName() + ":" + caller.getLineNumber());
	}
}

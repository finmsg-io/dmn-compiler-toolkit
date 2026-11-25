package io.finmsg.dmn.compiler.dmnir;

import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader.VerifiedArtifact;
import io.finmsg.dmn.dmnir.codec.DmnIrBuiltinSignature;
import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.frontend.xml.dmn.*;
import io.finmsg.dmn.model.*;
import io.finmsg.dmn.semantic.analysis.BuiltinFeelFunctionRegistry;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Artifact-only normalized DMN XML synthesis (DMN 1.6 default; also supports
 * DMN 1.5 and DMN 1.3) for portable DMNIR artifacts.
 */
public final class DmnIrScalarDmnExporter {
	private static final Pattern ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
	private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*( [A-Za-z_][A-Za-z0-9_]*)*");
	private static final Pattern SIMPLE_FEEL_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final Set<String> KEYWORDS = Set.of("true", "false", "null", "if", "then", "else", "for", "return",
			"in", "and", "or", "not", "some", "every", "satisfies", "function", "external", "instance", "of", "between",
			"range", "list", "context");

	/**
	 * Document keys are generated relative filenames; document values contain UTF-8
	 * XML text.
	 */
	public static final class ExportedDmnBundle {
		private final String rootDocument;
		private final Map<String, String> documents;
		private ExportedDmnBundle(String rootDocument, Map<String, String> documents) {
			this.rootDocument = rootDocument;
			this.documents = Collections.unmodifiableMap(new LinkedHashMap<>(documents));
		}
		public String rootDocument() {
			return rootDocument;
		}
		public Map<String, String> documents() {
			return documents;
		}
	}

	public ExportedDmnBundle export(VerifiedArtifact verified) {
		return export(verified, DmnVersion.DMN_1_6);
	}

	public ExportedDmnBundle export(VerifiedArtifact verified, DmnVersion version) {
		Objects.requireNonNull(verified, "verified");
		Objects.requireNonNull(version, "version");
		String dmnNamespace = dmnNamespace(version);
		var artifact = verified.artifact();
		boolean scalarProfile = artifact.getSchemaVersion().getMinor() == 0
				&& artifact.getSemanticsVersion().equals("finmsg-dmnir-scalar/0")
				&& artifact.getRequiredCapabilitiesList().equals(List.of("dmnir.scalar"));
		boolean expressionProfile = artifact.getSchemaVersion().getMinor() == 1
				&& artifact.getSemanticsVersion().equals("finmsg-dmnir-scalar-expressions/0")
				&& artifact.getRequiredCapabilitiesList().equals(List.of("dmnir.scalar", "dmnir.scalar-expressions"));
		boolean recordProfile = artifact.getSchemaVersion().getMinor() == 2
				&& artifact.getSemanticsVersion().equals("finmsg-dmnir-records/0")
				&& artifact.getRequiredCapabilitiesList()
						.equals(List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records"));
		boolean localProfile = artifact.getSchemaVersion().getMinor() == 3
				&& artifact.getSemanticsVersion().equals("finmsg-dmnir-local-bindings/0")
				&& artifact.getRequiredCapabilitiesList().equals(
						List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records", "dmnir.local-bindings"));
		boolean dateProfile = artifact.getSchemaVersion().getMinor() == 4
				&& artifact.getSemanticsVersion().equals("finmsg-dmnir-dates/0")
				&& artifact.getRequiredCapabilitiesList().equals(List.of("dmnir.scalar", "dmnir.scalar-expressions",
						"dmnir.records", "dmnir.local-bindings", "dmnir.date"));
		boolean tableProfile = artifact.getSchemaVersion().getMinor() == 5
				&& artifact.getSemanticsVersion().equals("finmsg-dmnir-tables/0")
				&& artifact.getRequiredCapabilitiesList().equals(List.of("dmnir.scalar", "dmnir.scalar-expressions",
						"dmnir.records", "dmnir.local-bindings", "dmnir.date", "dmnir.tables"));
		boolean constraintProfile = artifact.getSchemaVersion().getMinor() == 6
				&& artifact.getSemanticsVersion().equals("finmsg-dmnir-field-constraints/0")
				&& artifact.getRequiredCapabilitiesList()
						.equals(List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records",
								"dmnir.local-bindings", "dmnir.date", "dmnir.tables", "dmnir.field-constraints"));
		boolean collectionProfile = artifact.getSchemaVersion().getMinor() == 7
				&& artifact.getSemanticsVersion().equals("finmsg-dmnir-scalar-collections/0")
				&& artifact.getRequiredCapabilitiesList()
						.equals(List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records",
								"dmnir.local-bindings", "dmnir.date", "dmnir.tables", "dmnir.field-constraints",
								"dmnir.scalar-collections"));
		boolean filterProfile = artifact.getSchemaVersion().getMinor() == 8
				&& artifact.getSemanticsVersion().equals("finmsg-dmnir-scalar-filters/0")
				&& artifact.getRequiredCapabilitiesList()
						.equals(List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records",
								"dmnir.local-bindings", "dmnir.date", "dmnir.tables", "dmnir.field-constraints",
								"dmnir.scalar-collections", "dmnir.scalar-filters"));
		boolean builtinProfile = artifact.getSchemaVersion().getMinor() == 9
				|| artifact.getSchemaVersion().getMinor() == 10 || artifact.getSchemaVersion().getMinor() == 11;
		boolean fullProfile = artifact.getSchemaVersion().getMinor() >= 7;
		boolean relaxNullTypeRef = artifact.getSchemaVersion().getMinor() >= 2;
		boolean relaxFieldAndOutputNames = artifact.getSchemaVersion().getMinor() >= 5;
		if (artifact.getSchemaVersion().getMajor() != 0
				|| !(scalarProfile || expressionProfile || recordProfile || localProfile || dateProfile || tableProfile
						|| constraintProfile || collectionProfile || filterProfile || builtinProfile))
			throw invalid(null);
		var types = new DmnIrRecordTypes(artifact);
		var models = artifact.getModelsList().stream().sorted(Comparator.comparing(DmnIrModel::getNamespace)).toList();
		Map<String, String> filenames = new LinkedHashMap<>();
		Map<DmnIrReference, Declaration> declarations = new HashMap<>();
		for (var model : models) {
			try {
				var uri = URI.create(model.getNamespace());
				if (!uri.isAbsolute() || uri.getFragment() != null)
					throw invalid(null);
			} catch (IllegalArgumentException e) {
				throw invalid(null);
			}
			xml(model.getNamespace(), null);
			filenames.put(model.getNamespace(), String.format(Locale.ROOT, "model-%04d.dmn", filenames.size()));
			Set<String> ids = new HashSet<>();
			checkIdentity(model.getIdentity(), ids, null, fullProfile);
			for (var collection : model.getCollectionDefinitionsList()) {
				checkIdentity(collection.getIdentity(), ids, null, fullProfile);
				if (!fullProfile && !ID.matcher(collection.getIdentity().getName()).matches())
					throw invalid(null);
			}
			for (var record : model.getRecordDefinitionsList()) {
				checkIdentity(record.getIdentity(), ids, null, fullProfile);
				if (!fullProfile && !ID.matcher(record.getIdentity().getName()).matches())
					throw invalid(null);
				for (var field : record.getFieldsList()) {
					name(field.getName(), null, relaxFieldAndOutputNames);
					if (field.hasId())
						id(field.getId(), ids, null, fullProfile);
				}
			}
			Set<String> names = new HashSet<>();
			for (var input : model.getInputsList())
				admit(model, input.getIdentity(), input.getVariable(), true, !fullProfile, fullProfile, ids, names,
						declarations);
			for (var decision : model.getDecisionsList())
				admit(model, decision.getIdentity(), decision.getVariable(), false,
						!constraintProfile && !collectionProfile && !filterProfile && !builtinProfile, fullProfile, ids,
						names, declarations);
			for (var bkm : model.getBkmsList()) {
				var address = ref(model.getNamespace(), bkm.getIdentity().getId());
				admit(model, bkm.getIdentity(), bkm.getVariable(), false, !fullProfile, fullProfile, ids, names,
						declarations);
				declarations.put(address, new Declaration(bkm.getVariable().getName(), false, bkm, null));
				for (var parameter : bkm.getParametersList()) {
					var formal = parameter.getVariable();
					name(formal.getName(), address, fullProfile);
					if (formal.hasId())
						id(formal.getId(), ids, address, fullProfile);
				}
			}
			for (var ds : model.getDecisionServicesList()) {
				var address = ref(model.getNamespace(), ds.getIdentity().getId());
				admit(model, ds.getIdentity(), ds.getVariable(), false, false, fullProfile, ids, names, declarations);
				declarations.put(address, new Declaration(ds.getVariable().getName(), false, null, ds));
			}
			for (var imported : model.getImportsList()) {
				if (fullProfile && imported.getName().isEmpty()) {
					continue;
				}
				name(imported.getName(), null, fullProfile);
				if (!fullProfile && !names.add(imported.getName()))
					throw invalid(null);
			}
		}
		Map<String, String> documents = new LinkedHashMap<>();
		for (var model : models) {
			var definitions = Definitions.newBuilder().setNode(node(model.getIdentity()))
					.setNamespace(model.getNamespace()).setModelNamespaceUri(dmnNamespace);
			if (recordProfile || localProfile || dateProfile || tableProfile || constraintProfile || collectionProfile
					|| filterProfile || builtinProfile) {
				int prefix = 0;
				for (var namespace : models.stream().map(DmnIrModel::getNamespace).toList())
					definitions.addNamespaces(Namespace.newBuilder().setPrefix("t" + prefix++).setUri(namespace));
				Map<String, DmnIrRecordDefinition> recordsById = new HashMap<>();
				for (var record : model.getRecordDefinitionsList()) {
					recordsById.put(record.getIdentity().getId(), record);
				}
				Set<String> inlinedRecordIds = new HashSet<>();
				for (var collection : model.getCollectionDefinitionsList().stream()
						.sorted(Comparator.comparing(c -> c.getIdentity().getId())).toList()) {
					var colItem = ItemDefinition.newBuilder().setNode(node(collection.getIdentity()))
							.setIsCollection(true);
					DmnIrRecordDefinition inlineRecord = null;
					if (collection.hasRecordType()
							&& collection.getRecordType().getModelNamespace().equals(model.getNamespace())) {
						String recId = collection.getRecordType().getElementId();
						if (recId.equals(collection.getIdentity().getId() + "$element")) {
							inlineRecord = recordsById.get(recId);
						}
					}
					if (inlineRecord != null) {
						inlinedRecordIds.add(inlineRecord.getIdentity().getId());
						addRecordComponents(colItem, inlineRecord, model, declarations, types, fullProfile);
					} else {
						TypeReference elemType = collection.hasCollectionType()
								? collectionType(collection.getCollectionType(), types)
								: dmnType(collection.getElementType(), collection.hasRecordType(),
										collection.getRecordType(), types, fullProfile);
						colItem.setType(elemType);
					}
					definitions.addItemDefinitions(colItem);
				}
				for (var record : model.getRecordDefinitionsList().stream()
						.sorted(Comparator.comparing(r -> r.getIdentity().getId())).toList()) {
					if (inlinedRecordIds.contains(record.getIdentity().getId())) {
						continue;
					}
					var item = ItemDefinition.newBuilder().setNode(node(record.getIdentity()));
					addRecordComponents(item, record, model, declarations, types, fullProfile);
					definitions.addItemDefinitions(item);
				}
			}
			model.getImportsList().stream()
					.sorted(Comparator.comparing(DmnIrImport::getModelNamespace).thenComparing(DmnIrImport::getName))
					.forEach(i -> definitions.addImports(io.finmsg.dmn.model.Import.newBuilder().setName(i.getName())
							.setNamespace(i.getModelNamespace()).setLocationUri(filenames.get(i.getModelNamespace()))
							.setImportType(dmnNamespace)));
			model.getInputsList().stream().sorted(Comparator.comparing(i -> i.getIdentity().getId()))
					.forEach(i -> definitions.addDrgElements(
							DrgElement.newBuilder().setInputData(InputData.newBuilder().setNode(node(i.getIdentity()))
									.setVariable(variable(i.getVariable(), types, fullProfile, relaxNullTypeRef)))));
			for (var bkm : model.getBkmsList().stream().sorted(Comparator.comparing(b -> b.getIdentity().getId()))
					.toList()) {
				var address = ref(model.getNamespace(), bkm.getIdentity().getId());
				var output = BusinessKnowledgeModel.newBuilder().setNode(node(bkm.getIdentity()))
						.setVariable(variable(bkm.getVariable(), types, fullProfile, relaxNullTypeRef));
				var function = FunctionDefinition.newBuilder().setKind(FunctionKind.FUNCTION_KIND_FEEL);
				Map<String, String> locals = new LinkedHashMap<>();
				for (var parameter : bkm.getParametersList()) {
					function.addFormalParameters(
							variable(parameter.getVariable(), types, fullProfile, relaxNullTypeRef));
					locals.put(parameter.getBindingId(), parameter.getVariable().getName());
				}
				function.setLogic(Feel.newBuilder().setText(
						text(expression(bkm.getBody(), model, declarations, address, types, locals, fullProfile))));
				output.setFunction(function)
						.addAllKnowledgeRequirements(knowledgeRequirements(bkm.getKnowledgeDependenciesList(), model,
								declarations, address, new HashSet<>(), fullProfile));
				definitions.addDrgElements(DrgElement.newBuilder().setBusinessKnowledgeModel(output));
			}
			for (var decision : model.getDecisionsList().stream()
					.sorted(Comparator.comparing(d -> d.getIdentity().getId())).toList()) {
				var address = ref(model.getNamespace(), decision.getIdentity().getId());
				var output = Decision.newBuilder().setNode(node(decision.getIdentity()))
						.setVariable(variable(decision.getVariable(), types, fullProfile, relaxNullTypeRef));
				// The source analyzer also exposes imported names unqualified: reject
				// collisions among required names in strict profiles.
				Set<String> requiredNames = new HashSet<>();
				for (var dependency : decision.getDependenciesList().stream().sorted(Comparator
						.comparing(DmnIrReference::getModelNamespace).thenComparing(DmnIrReference::getElementId))
						.toList()) {
					var declaration = declarations.get(dependency);
					if (declaration == null)
						throw invalid(address);
					if (!fullProfile && !requiredNames.add(declaration.name()))
						throw invalid(address);
					var reference = elementRef(dependency, model);
					var requirement = InformationRequirement.newBuilder();
					if (declaration.input())
						requirement.setInput(reference);
					else
						requirement.setDecision(reference);
					output.addInformationRequirements(requirement);
				}
				output.addAllKnowledgeRequirements(knowledgeRequirements(decision.getKnowledgeDependenciesList(), model,
						declarations, address, requiredNames, fullProfile));
				var logic = DecisionLogic.newBuilder();
				if (decision.hasDecisionTable()) {
					logic.setDecisionTable(table(decision.getDecisionTable(), types, address,
							cell -> expression(cell, model, declarations, address, types, Map.of(), fullProfile),
							fullProfile, relaxFieldAndOutputNames));
				} else if (fullProfile && decision.getExpression().hasBkmCall()
						&& shouldEmitBoxedInvocation(decision.getExpression().getBkmCall(), declarations)) {
					logic.setInvocation(boxedInvocation(decision.getExpression().getBkmCall(), model, declarations,
							address, types, fullProfile));
				} else {
					var exported = expressionText(decision.getExpression(), model, declarations, address, types,
							new LinkedHashMap<>(), fullProfile, relaxNullTypeRef);
					if (exported.hasBoxed())
						logic.setBoxedExpression(BoxedExpression.newBuilder().setText(exported.getBoxed()));
					else
						logic.setLiteralExpression(Feel.newBuilder().setText(exported.getFeel()));
				}
				output.setLogic(logic);
				definitions.addDrgElements(DrgElement.newBuilder().setDecision(output));
			}
			for (var ds : model.getDecisionServicesList().stream()
					.sorted(Comparator.comparing(d -> d.getIdentity().getId())).toList()) {
				var builder = DecisionService.newBuilder().setNode(node(ds.getIdentity()))
						.setVariable(variable(ds.getVariable(), types, fullProfile, relaxNullTypeRef));
				for (var outDec : ds.getOutputDecisionsList())
					builder.addOutputDecisions(elementRef(outDec, model));
				for (var encDec : ds.getEncapsulatedDecisionsList())
					builder.addEncapsulatedDecisions(elementRef(encDec, model));
				for (var inDec : ds.getInputDecisionsList())
					builder.addInputDecisions(elementRef(inDec, model));
				for (var inData : ds.getInputDataList())
					builder.addInputData(elementRef(inData, model));
				definitions.addDrgElements(DrgElement.newBuilder().setDecisionService(builder));
			}
			documents.put(filenames.get(model.getNamespace()),
					new String(new DmnWriter().write(definitions.build()), StandardCharsets.UTF_8));
		}
		return new ExportedDmnBundle(filenames.get(artifact.getRootModelNamespace()), documents);
	}

	private static void addRecordComponents(ItemDefinition.Builder item, DmnIrRecordDefinition record, DmnIrModel model,
			Map<DmnIrReference, Declaration> declarations, DmnIrRecordTypes types, boolean fullProfile) {
		for (var field : record.getFieldsList()) {
			var fieldNode = Node.newBuilder().setName(field.getName());
			if (field.hasId())
				fieldNode.setId(field.getId());
			var fieldType = field.getType();
			var component = ItemComponent.newBuilder().setNode(fieldNode)
					.setType(fieldType.hasCollectionType()
							? collectionType(fieldType.getCollectionType(), types)
							: dmnType(fieldType.getScalarType(), fieldType.hasRecordType(), fieldType.getRecordType(),
									types, fullProfile));
			if (field.hasConstraint()) {
				var address = ref(model.getNamespace(), record.getIdentity().getId());
				component.setConstraint(TypeConstraint.newBuilder().setText(text(tests(field.getConstraint(),
						endpoint -> expression(endpoint, model, declarations, address, types, Map.of(), fullProfile),
						address))));
			}
			item.addComponents(component);
		}
	}

	private static boolean shouldEmitBoxedInvocation(DmnIrBkmCall call, Map<DmnIrReference, Declaration> declarations) {
		var target = declarations.get(call.getTarget());
		if (target == null || target.bkm() == null) {
			return false;
		}
		return !NAME.matcher(target.name()).matches();
	}

	private static Invocation boxedInvocation(DmnIrBkmCall call, DmnIrModel model,
			Map<DmnIrReference, Declaration> declarations, DmnIrReference address, DmnIrRecordTypes types,
			boolean fullProfile) {
		var target = declarations.get(call.getTarget());
		if (target == null || target.bkm() == null) {
			throw invalid(address);
		}
		String function = referenceName(call.getTarget(), target.name(), model, address, Map.of(), fullProfile);
		var inv = Invocation.newBuilder().setExpression(Feel.newBuilder().setText(text(function)));
		for (var argument : call.getArgumentsList()) {
			String paramName = target.bkm().getParameters(argument.getParameterPosition() - 1).getVariable().getName();
			String valExpr = expression(argument.getExpression(), model, declarations, address, types, Map.of(),
					fullProfile);
			inv.addBindings(Binding.newBuilder().setParameter(paramName)
					.setExpression(Feel.newBuilder().setText(text(valExpr))));
		}
		return inv.build();
	}

	private static String dmnNamespace(DmnVersion version) {
		return switch (version) {
			case DMN_1_3 -> DmnNamespaces.DMN_1_3;
			case DMN_1_5 -> DmnNamespaces.DMN_1_5;
			case DMN_1_6 -> DmnNamespaces.DMN_1_6;
			default -> throw new DmnIrExportException(DmnIrExportException.Category.UNSUPPORTED_VERSION, null);
		};
	}

	private static ElementReference elementRef(DmnIrReference dependency, DmnIrModel model) {
		return ElementReference.newBuilder()
				.setHref(dependency.getModelNamespace().equals(model.getNamespace())
						? "#" + dependency.getElementId()
						: dependency.getModelNamespace() + "#" + dependency.getElementId())
				.build();
	}

	private static List<KnowledgeRequirement> knowledgeRequirements(List<DmnIrReference> dependencies, DmnIrModel model,
			Map<DmnIrReference, Declaration> declarations, DmnIrReference address, Set<String> requiredNames,
			boolean fullProfile) {
		return dependencies.stream().sorted(
				Comparator.comparing(DmnIrReference::getModelNamespace).thenComparing(DmnIrReference::getElementId))
				.map(dependency -> {
					var decl = declarations.get(dependency);
					if (decl == null)
						throw invalid(address);
					if (!fullProfile && !requiredNames.add(decl.name()))
						throw invalid(address);
					return KnowledgeRequirement.newBuilder().setRequiredKnowledge(elementRef(dependency, model))
							.build();
				}).toList();
	}

	private static DecisionTable table(DmnIrDecisionTable source, DmnIrRecordTypes types, DmnIrReference address,
			Function<DmnIrExpression, String> expression, boolean fullProfile, boolean relaxOutputNames) {
		var ids = new HashSet<String>();
		var hitPolicy = HitPolicySpec.newBuilder().setPolicy(switch (source.getHitPolicy()) {
			case TABLE_HIT_POLICY_UNIQUE -> HitPolicy.HIT_POLICY_UNIQUE;
			case TABLE_HIT_POLICY_FIRST -> HitPolicy.HIT_POLICY_FIRST;
			case TABLE_HIT_POLICY_ANY -> HitPolicy.HIT_POLICY_ANY;
			case TABLE_HIT_POLICY_RULE_ORDER -> HitPolicy.HIT_POLICY_RULE_ORDER;
			case TABLE_HIT_POLICY_OUTPUT_ORDER -> HitPolicy.HIT_POLICY_OUTPUT_ORDER;
			case TABLE_HIT_POLICY_COLLECT, TABLE_HIT_POLICY_COLLECT_SUM, TABLE_HIT_POLICY_COLLECT_MIN,
					TABLE_HIT_POLICY_COLLECT_MAX, TABLE_HIT_POLICY_COLLECT_COUNT ->
				HitPolicy.HIT_POLICY_COLLECT;
			default -> throw invalid(address);
		});
		switch (source.getHitPolicy()) {
			case TABLE_HIT_POLICY_COLLECT_SUM -> hitPolicy.setAggregation(Aggregation.AGGREGATION_SUM);
			case TABLE_HIT_POLICY_COLLECT_MIN -> hitPolicy.setAggregation(Aggregation.AGGREGATION_MIN);
			case TABLE_HIT_POLICY_COLLECT_MAX -> hitPolicy.setAggregation(Aggregation.AGGREGATION_MAX);
			case TABLE_HIT_POLICY_COLLECT_COUNT -> hitPolicy.setAggregation(Aggregation.AGGREGATION_COUNT);
			default -> {
			}
		}
		var table = DecisionTable.newBuilder()
				.setNode(tableNode(source.hasId(), source.getId(), ids, address, fullProfile)).setHitPolicy(hitPolicy);
		for (var input : source.getInputsList()) {
			var node = tableNode(input.hasId(), input.getId(), ids, address, fullProfile);
			if (input.hasLabel()) {
				xml(input.getLabel(), address);
				if (!fullProfile && input.getLabel().chars().anyMatch(c -> c == 9 || c == 10 || c == 13))
					throw invalid(address);
				node.setLabel(input.getLabel());
			}
			var clause = InputClause.newBuilder().setNode(node)
					.setInputExpression(Feel.newBuilder().setText(text(expression.apply(input.getExpression()))));
			if (!fullProfile || (input.getType() != DmnIrScalarType.SCALAR_TYPE_UNSPECIFIED
					&& input.getType() != DmnIrScalarType.SCALAR_TYPE_NULL)) {
				clause.setType(
						dmnType(input.getType(), false, DmnIrReference.getDefaultInstance(), types, fullProfile));
			}
			table.addInputs(clause);
		}
		for (var output : source.getOutputsList()) {
			var node = tableNode(output.hasId(), output.getId(), ids, address, fullProfile);
			if (output.hasName()) {
				name(output.getName(), address, relaxOutputNames);
				node.setName(output.getName());
			}
			var clause = OutputClause.newBuilder().setNode(node);
			if (!fullProfile || (output.getType() != DmnIrScalarType.SCALAR_TYPE_UNSPECIFIED
					&& output.getType() != DmnIrScalarType.SCALAR_TYPE_NULL)) {
				clause.setType(
						dmnType(output.getType(), false, DmnIrReference.getDefaultInstance(), types, fullProfile));
			}
			if (output.hasDefaultExpression())
				clause.setDefaultOutputEntry(ExpressionNode.newBuilder().setText(
						ExpressionText.newBuilder().setFeel(text(expression.apply(output.getDefaultExpression())))));
			table.addOutputs(clause);
		}
		for (var rule : source.getRulesList()) {
			var entry = DecisionRule.newBuilder().setRuleIndex(table.getRulesCount())
					.setNode(tableNode(rule.hasId(), rule.getId(), ids, address, fullProfile));
			for (var tests : rule.getInputEntriesList())
				entry.addInputEntries(UnaryTest.newBuilder().setText(text(tests(tests, expression, address))));
			for (var cell : rule.getOutputEntriesList())
				entry.addOutputEntries(Feel.newBuilder().setText(text(expression.apply(cell))));
			table.addRules(entry);
		}
		return table.build();
	}

	private static Node.Builder tableNode(boolean hasId, String value, Set<String> ids, DmnIrReference address,
			boolean fullProfile) {
		var node = Node.newBuilder();
		if (hasId) {
			id(value, ids, address, fullProfile);
			node.setId(value);
		}
		return node;
	}

	private static FeelText text(String text) {
		return FeelText.newBuilder().setText(text).build();
	}

	private static String tests(DmnIrUnaryTests group, Function<DmnIrExpression, String> expression,
			DmnIrReference address) {
		if (group.getWildcard())
			return "-";
		String alternatives = group.getTestsList().stream().map(test -> switch (test.getTestCase()) {
			case COMPARISON -> {
				var comparison = test.getComparison();
				String operator = switch (comparison.getOperator()) {
					case BINARY_OPERATOR_EQUAL -> "";
					case BINARY_OPERATOR_NOT_EQUAL -> "!= ";
					case BINARY_OPERATOR_LESS -> "< ";
					case BINARY_OPERATOR_LESS_EQUAL -> "<= ";
					case BINARY_OPERATOR_GREATER -> "> ";
					case BINARY_OPERATOR_GREATER_EQUAL -> ">= ";
					default -> throw invalid(address);
				};
				yield operator + expression.apply(comparison.getEndpoint());
			}
			case RANGE -> {
				var range = test.getRange();
				yield (range.getLowerInclusive() ? "[" : "(") + expression.apply(range.getLower()) + " .. "
						+ expression.apply(range.getUpper()) + (range.getUpperInclusive() ? "]" : ")");
			}
			case TEST_NOT_SET -> throw invalid(address);
		}).collect(Collectors.joining(", "));
		return group.getNegated() ? "not(" + alternatives + ")" : alternatives;
	}

	private record Declaration(String name, boolean input, DmnIrBkm bkm, DmnIrDecisionService decisionService) {
	}

	private static void admit(DmnIrModel model, DmnIrElementIdentity identity, DmnIrVariable variable, boolean input,
			boolean feelName, boolean fullProfile, Set<String> ids, Set<String> names,
			Map<DmnIrReference, Declaration> declarations) {
		var address = ref(model.getNamespace(), identity.getId());
		checkIdentity(identity, ids, address, fullProfile);
		if (feelName)
			name(variable.getName(), address, fullProfile);
		if (!fullProfile && (!identity.getName().equals(variable.getName()) || !names.add(variable.getName())))
			throw invalid(address);
		if (variable.hasId())
			id(variable.getId(), ids, address, fullProfile);
		declarations.put(address, new Declaration(variable.getName(), input, null, null));
	}

	private static void checkIdentity(DmnIrElementIdentity identity, Set<String> ids, DmnIrReference address,
			boolean fullProfile) {
		id(identity.getId(), ids, address, fullProfile);
		xml(identity.getName(), address);
		// XML attribute normalization would otherwise change retained names.
		if (!fullProfile && identity.getName().chars().anyMatch(c -> c == 9 || c == 10 || c == 13))
			throw invalid(address);
	}

	private static void id(String value, Set<String> ids, DmnIrReference address, boolean fullProfile) {
		if (fullProfile) {
			if (value.isEmpty())
				throw invalid(address);
			xml(value, address);
			ids.add(value);
			return;
		}
		if (!ID.matcher(value).matches() || !ids.add(value))
			throw invalid(address);
	}

	private static void name(String value, DmnIrReference address, boolean fullProfile) {
		if (fullProfile) {
			if (value.isEmpty())
				throw invalid(address);
			xml(value, address);
			return;
		}
		if (!NAME.matcher(value).matches() || Arrays.stream(value.split(" ")).anyMatch(KEYWORDS::contains))
			throw invalid(address);
	}

	private static void xml(String value, DmnIrReference address) {
		if (value.codePoints().anyMatch(c -> !(c == 9 || c == 10 || c == 13 || c >= 0x20 && c <= 0xD7FF
				|| c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF)))
			throw invalid(address);
	}

	private static Node node(DmnIrElementIdentity value) {
		return Node.newBuilder().setId(value.getId()).setName(value.getName()).build();
	}

	private static InformationItem variable(DmnIrVariable value, DmnIrRecordTypes types, boolean fullProfile,
			boolean relaxNullTypeRef) {
		var node = Node.newBuilder().setName(value.getName());
		if (value.hasId())
			node.setId(value.getId());
		var builder = InformationItem.newBuilder().setNode(node);
		if (value.hasCollectionType()) {
			builder.setType(collectionType(value.getCollectionType(), types));
		} else if (value.hasRecordType()) {
			builder.setType(dmnType(value.getType(), true, value.getRecordType(), types, fullProfile));
		} else if ((!fullProfile || value.getType() != DmnIrScalarType.SCALAR_TYPE_UNSPECIFIED)
				&& (!relaxNullTypeRef || value.getType() != DmnIrScalarType.SCALAR_TYPE_NULL)) {
			builder.setType(dmnType(value.getType(), false, DmnIrReference.getDefaultInstance(), types, fullProfile));
		}
		return builder.build();
	}

	private static TypeReference collectionType(DmnIrReference reference, DmnIrRecordTypes types) {
		return TypeReference.newBuilder()
				.setNamed(NamedTypeReference.newBuilder().setNamespace(reference.getModelNamespace())
						.setName(types.collection(reference).getIdentity().getName()))
				.build();
	}

	private static TypeReference dmnType(DmnIrScalarType scalar, boolean record, DmnIrReference reference,
			DmnIrRecordTypes types, boolean fullProfile) {
		if (record)
			return TypeReference.newBuilder()
					.setNamed(NamedTypeReference.newBuilder().setNamespace(reference.getModelNamespace())
							.setName(types.definition(reference).getIdentity().getName()))
					.build();
		var builtin = switch (scalar) {
			case SCALAR_TYPE_NULL -> fullProfile ? BuiltinType.BUILTIN_TYPE_ANY : BuiltinType.BUILTIN_TYPE_NULL;
			case SCALAR_TYPE_BOOLEAN -> BuiltinType.BUILTIN_TYPE_BOOLEAN;
			case SCALAR_TYPE_NUMBER -> BuiltinType.BUILTIN_TYPE_NUMBER;
			case SCALAR_TYPE_STRING -> BuiltinType.BUILTIN_TYPE_STRING;
			case SCALAR_TYPE_DATE -> BuiltinType.BUILTIN_TYPE_DATE;
			case SCALAR_TYPE_TIME -> BuiltinType.BUILTIN_TYPE_TIME;
			case SCALAR_TYPE_DATE_TIME -> BuiltinType.BUILTIN_TYPE_DATE_AND_TIME;
			case SCALAR_TYPE_DURATION -> BuiltinType.BUILTIN_TYPE_DURATION;
			case SCALAR_TYPE_UNSPECIFIED -> {
				if (fullProfile)
					yield BuiltinType.BUILTIN_TYPE_ANY;
				throw invalid(null);
			}
			default -> throw invalid(null);
		};
		return TypeReference.newBuilder().setBuiltin(builtin).build();
	}

	private static ExpressionText expressionText(DmnIrExpression expression, DmnIrModel model,
			Map<DmnIrReference, Declaration> declarations, DmnIrReference address, DmnIrRecordTypes types,
			Map<String, String> locals, boolean fullProfile, boolean relaxNullTypeRef) {
		if (!expression.hasContext())
			return ExpressionText.newBuilder()
					.setFeel(FeelText.newBuilder()
							.setText(expression(expression, model, declarations, address, types, locals, fullProfile)))
					.build();
		var child = new LinkedHashMap<>(locals);
		var context = ContextText.newBuilder();
		for (var binding : expression.getContext().getBindingsList()) {
			if (binding.hasClosure()) {
				var closure = binding.getClosure();
				name(closure.getName(), address, fullProfile);
				var declaration = Node.newBuilder().setName(closure.getName());
				if (closure.hasId()) {
					if (!fullProfile && !ID.matcher(closure.getId()).matches())
						throw invalid(address);
					declaration.setId(closure.getId());
				}
				var definition = closure.getDefinition();
				if (definition.getExternal()) {
					// Emit external Java function as FEEL `function(...) external ...`
					String fnExpr = formatClosure(definition, model, declarations, address, types, child, fullProfile);
					context.addEntries(
							ContextEntryText.newBuilder().setVariable(InformationItem.newBuilder().setNode(declaration))
									.setExpression(ExpressionText.newBuilder().setFeel(text(fnExpr))));
				} else {
					var function = FunctionDefinitionText.newBuilder();
					for (var formal : definition.getParametersList())
						function.addParameters(variable(formal.getVariable(), types, fullProfile, relaxNullTypeRef));
					function.setBody(expressionText(definition.getBody(), model, declarations, address, types,
							formalScope(definition, child, address, fullProfile), fullProfile, relaxNullTypeRef));
					context.addEntries(
							ContextEntryText.newBuilder().setVariable(InformationItem.newBuilder().setNode(declaration))
									.setExpression(ExpressionText.newBuilder().setBoxed(
											BoxedExpressionText.newBuilder().setFunctionDefinition(function))));
				}
				child.put(binding.getBindingId(), closure.getName());
				continue;
			}
			var variable = binding.getVariable();
			name(variable.getName(), address, fullProfile);
			if (variable.hasId() && !fullProfile && !ID.matcher(variable.getId()).matches())
				throw invalid(address);
			var value = expressionText(binding.getValue(), model, declarations, address, types, child, fullProfile,
					relaxNullTypeRef);
			context.addEntries(ContextEntryText.newBuilder()
					.setVariable(variable(variable, types, fullProfile, relaxNullTypeRef)).setExpression(value));
			child.put(binding.getBindingId(), variable.getName());
		}
		context.addEntries(
				ContextEntryText.newBuilder().setExpression(expressionText(expression.getContext().getResult(), model,
						declarations, address, types, child, fullProfile, relaxNullTypeRef)));
		return ExpressionText.newBuilder().setBoxed(BoxedExpressionText.newBuilder().setContext(context)).build();
	}

	private static String formatClosure(DmnIrClosure closure, DmnIrModel model,
			Map<DmnIrReference, Declaration> declarations, DmnIrReference address, DmnIrRecordTypes types,
			Map<String, String> locals, boolean fullProfile) {
		var scope = formalScope(closure, locals, address, fullProfile);
		var formals = closure.getParametersList().stream().map(formal -> {
			var variable = formal.getVariable();
			if (!fullProfile && variable.hasId())
				throw invalid(address);
			if (fullProfile && !variable.hasRecordType() && !variable.hasCollectionType()
					&& (variable.getType() == DmnIrScalarType.SCALAR_TYPE_UNSPECIFIED
							|| variable.getType() == DmnIrScalarType.SCALAR_TYPE_NULL)) {
				return variable.getName();
			}
			return variable.getName() + ": " + feelTypeName(variable, model, address, types, fullProfile);
		}).collect(Collectors.joining(", "));
		String prefix = closure.getExternal() ? "(function(" + formals + ") external " : "(function(" + formals + ") ";
		return prefix + expression(closure.getBody(), model, declarations, address, types, scope, fullProfile) + ")";
	}

	private static String formatContextKey(String key) {
		if (SIMPLE_FEEL_KEY.matcher(key).matches() && !KEYWORDS.contains(key)) {
			return key;
		}
		return "\"" + escapeFeelString(key) + "\"";
	}

	private static String escapeFeelString(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t",
				"\\t");
	}

	private static String formatTypeDescriptor(DmnIrTypeDescriptor desc) {
		if (desc == null || desc.getKind().isEmpty()) {
			return "Any";
		}
		return switch (desc.getKind()) {
			case "ANY" -> "Any";
			case "NULL" -> "Null";
			case "BOOLEAN" -> "boolean";
			case "NUMBER" -> "number";
			case "STRING" -> "string";
			case "DATE" -> "date";
			case "TIME" -> "time";
			case "DATE_TIME" -> "date and time";
			case "DAYS_TIME_DURATION" -> "days and time duration";
			case "YEARS_MONTHS_DURATION" -> "years and months duration";
			case "DURATION" -> "duration";
			case "LIST" -> desc.hasElementType() ? "list<" + formatTypeDescriptor(desc.getElementType()) + ">" : "list";
			case "RANGE" ->
				desc.hasElementType() ? "range<" + formatTypeDescriptor(desc.getElementType()) + ">" : "range";
			case "CONTEXT" -> {
				if (desc.getFieldsCount() == 0) {
					yield "context";
				}
				String fields = desc.getFieldsList().stream()
						.map(f -> formatContextKey(f.getName()) + ": " + formatTypeDescriptor(f.getType()))
						.collect(Collectors.joining(", "));
				yield "context<" + fields + ">";
			}
			case "FUNCTION" -> {
				if (desc.getParameterTypesCount() == 0 && !desc.hasReturnType()) {
					yield "function";
				}
				String params = desc.getParameterTypesList().stream().map(DmnIrScalarDmnExporter::formatTypeDescriptor)
						.collect(Collectors.joining(", "));
				String ret = desc.hasReturnType() ? formatTypeDescriptor(desc.getReturnType()) : "Any";
				yield "function<" + params + "> -> " + ret;
			}
			default -> desc.getKind();
		};
	}

	private static String expression(DmnIrExpression expression, DmnIrModel model,
			Map<DmnIrReference, Declaration> declarations, DmnIrReference address, DmnIrRecordTypes types,
			Map<String, String> locals, boolean fullProfile) {
		if (expression.hasContext()) {
			if (!fullProfile)
				throw invalid(address); // Boxed contexts require an XML expression position in strict profiles.
			var child = new LinkedHashMap<>(locals);
			List<String> entries = new ArrayList<>();
			for (var binding : expression.getContext().getBindingsList()) {
				if (binding.hasClosure()) {
					var closure = binding.getClosure();
					String fnExpr = formatClosure(closure.getDefinition(), model, declarations, address, types, child,
							fullProfile);
					entries.add(formatContextKey(closure.getName()) + ": " + fnExpr);
					child.put(binding.getBindingId(), closure.getName());
				} else {
					var varName = binding.getVariable().getName();
					String valExpr = expression(binding.getValue(), model, declarations, address, types, child,
							fullProfile);
					entries.add(formatContextKey(varName) + ": " + valExpr);
					child.put(binding.getBindingId(), varName);
				}
			}
			String resultKey = "__finmsg_ctx_res";
			while (child.containsValue(resultKey)) {
				resultKey = resultKey + "_";
			}
			String resExpr = expression(expression.getContext().getResult(), model, declarations, address, types, child,
					fullProfile);
			entries.add(resultKey + ": " + resExpr);
			return "({" + String.join(", ", entries) + "}." + resultKey + ")";
		}
		if (expression.hasContextLiteral()) {
			var child = new LinkedHashMap<>(locals);
			List<String> entries = new ArrayList<>();
			for (var binding : expression.getContextLiteral().getEntriesList()) {
				if (binding.hasClosure()) {
					var closure = binding.getClosure();
					String fnExpr = formatClosure(closure.getDefinition(), model, declarations, address, types, child,
							fullProfile);
					entries.add(formatContextKey(closure.getName()) + ": " + fnExpr);
					child.put(binding.getBindingId(), closure.getName());
				} else {
					var varName = binding.getVariable().getName();
					String valExpr = expression(binding.getValue(), model, declarations, address, types, child,
							fullProfile);
					entries.add(formatContextKey(varName) + ": " + valExpr);
					child.put(binding.getBindingId(), varName);
				}
			}
			return "{" + String.join(", ", entries) + "}";
		}
		if (expression.hasClosure()) {
			return formatClosure(expression.getClosure(), model, declarations, address, types, locals, fullProfile);
		}
		if (expression.hasRange()) {
			var r = expression.getRange();
			boolean hasLower = r.hasLower();
			boolean hasUpper = r.hasUpper();
			if (hasLower && hasUpper) {
				return (r.getLowerInclusive() ? "[" : "(")
						+ expression(r.getLower(), model, declarations, address, types, locals, fullProfile) + " .. "
						+ expression(r.getUpper(), model, declarations, address, types, locals, fullProfile)
						+ (r.getUpperInclusive() ? "]" : ")");
			}
			if (hasLower) {
				String op = (r.getLowerInclusive() && r.getUpperInclusive())
						? "= "
						: (r.getLowerInclusive() ? ">= " : "> ");
				return "(" + op + expression(r.getLower(), model, declarations, address, types, locals, fullProfile)
						+ ")";
			}
			if (hasUpper) {
				return "(" + (r.getUpperInclusive() ? "<= " : "< ")
						+ expression(r.getUpper(), model, declarations, address, types, locals, fullProfile) + ")";
			}
			throw invalid(address);
		}
		if (expression.hasInstanceOf()) {
			var iof = expression.getInstanceOf();
			return "((" + expression(iof.getExpression(), model, declarations, address, types, locals, fullProfile)
					+ ") instance of " + formatTypeDescriptor(iof.getTestedType()) + ")";
		}
		if (expression.hasForExpression()) {
			var fe = expression.getForExpression();
			var child = new LinkedHashMap<>(locals);
			List<String> clauses = new ArrayList<>();
			for (var iter : fe.getIterationsList()) {
				String src = expression(iter.getSource(), model, declarations, address, types, child, fullProfile);
				if (iter.hasRangeEnd()) {
					String end = expression(iter.getRangeEnd(), model, declarations, address, types, child,
							fullProfile);
					clauses.add(iter.getVariableName() + " in (" + src + ") .. (" + end + ")");
				} else {
					clauses.add(iter.getVariableName() + " in (" + src + ")");
				}
				child.put(iter.getBindingId(), iter.getVariableName());
			}
			if (!fe.getPartialBindingId().isEmpty()) {
				child.put(fe.getPartialBindingId(), "partial");
			}
			String body = expression(fe.getBody(), model, declarations, address, types, child, fullProfile);
			return "(for " + String.join(", ", clauses) + " return (" + body + "))";
		}
		if (expression.hasQuantified()) {
			var qe = expression.getQuantified();
			var child = new LinkedHashMap<>(locals);
			List<String> clauses = new ArrayList<>();
			for (var iter : qe.getBindingsList()) {
				String src = expression(iter.getSource(), model, declarations, address, types, child, fullProfile);
				clauses.add(iter.getVariableName() + " in (" + src + ")");
				child.put(iter.getBindingId(), iter.getVariableName());
			}
			String body = expression(qe.getSatisfies(), model, declarations, address, types, child, fullProfile);
			return "(" + (qe.getEvery() ? "every " : "some ") + String.join(", ", clauses) + " satisfies (" + body
					+ "))";
		}
		if (expression.hasDynamicInvocation()) {
			var dyn = expression.getDynamicInvocation();
			String target = expression(dyn.getTarget(), model, declarations, address, types, locals, fullProfile);
			List<String> args = new ArrayList<>();
			for (var pos : dyn.getPositionalArgumentsList()) {
				args.add(expression(pos, model, declarations, address, types, locals, fullProfile));
			}
			for (var named : dyn.getNamedArgumentsList()) {
				args.add(named.getName() + ": "
						+ expression(named.getValue(), model, declarations, address, types, locals, fullProfile));
			}
			return "(" + target + ")(" + String.join(", ", args) + ")";
		}
		if (expression.hasLocalReference()) {
			return localName(expression.getLocalReference().getBindingId(), locals, address, fullProfile);
		}
		if (expression.hasClosureInvocation()) {
			var call = expression.getClosureInvocation();
			String target;
			if (call.hasInline()) {
				target = formatClosure(call.getInline(), model, declarations, address, types, locals, fullProfile);
			} else {
				target = localName(call.getLocalBindingId(), locals, address, fullProfile);
				if (!fullProfile && BuiltinFeelFunctionRegistry.isBuiltin(target))
					throw invalid(address);
			}
			return call.getArgumentsList().stream()
					.map(argument -> expression(argument, model, declarations, address, types, locals, fullProfile))
					.collect(Collectors.joining(", ", target + "(", ")"));
		}
		if (expression.hasFieldAccess()) {
			String fieldName = expression.getFieldAccess().getFieldName();
			name(fieldName, address, fullProfile);
			var sourceNode = expression.getFieldAccess().getSource();
			if (fullProfile && fieldName.equals("item") && sourceNode.hasLocalReference()
					&& sourceNode.getLocalReference().getBindingId().endsWith("/filter/item")) {
				return "item";
			}
			String sourceExpr = expression(sourceNode, model, declarations, address, types, locals, fullProfile);
			if (fullProfile && (!NAME.matcher(fieldName).matches()
					|| Arrays.stream(fieldName.split(" ")).anyMatch(KEYWORDS::contains))) {
				// Use FEEL get value() for field names containing
				// punctuation/operators/keywords
				// that might not parse cleanly after '.'
				return "get value((" + sourceExpr + "), \"" + escapeFeelString(fieldName) + "\")";
			}
			return "(" + sourceExpr + ")." + fieldName;
		}
		if (expression.hasBkmCall()) {
			var call = expression.getBkmCall();
			var target = declarations.get(call.getTarget());
			if (target == null)
				throw invalid(address);
			if (!fullProfile && call.getTarget().getModelNamespace().equals(model.getNamespace())
					&& BuiltinFeelFunctionRegistry.isBuiltin(target.name()))
				throw invalid(address);
			String function = referenceName(call.getTarget(), target.name(), model, address, locals, fullProfile);
			boolean named = false;
			if (target.bkm() != null) {
				for (int i = 0; i < call.getArgumentsCount(); i++)
					named |= call.getArguments(i).getParameterPosition() != i + 1;
			}
			boolean canUseNamed = named && target.bkm() != null;
			if (canUseNamed) {
				for (var argument : call.getArgumentsList()) {
					String paramName = target.bkm().getParameters(argument.getParameterPosition() - 1).getVariable()
							.getName();
					if (!NAME.matcher(paramName).matches()
							|| Arrays.stream(paramName.split(" ")).anyMatch(KEYWORDS::contains)) {
						canUseNamed = false;
						break;
					}
				}
			}
			List<String> arguments = new ArrayList<>();
			if (named && !canUseNamed && target.bkm() != null) {
				int paramCount = target.bkm().getParametersCount();
				Map<Integer, DmnIrExpression> byPos = new HashMap<>();
				for (var argument : call.getArgumentsList()) {
					byPos.put(argument.getParameterPosition(), argument.getExpression());
				}
				for (int p = 1; p <= paramCount; p++) {
					var argExpr = byPos.get(p);
					arguments.add(argExpr == null
							? "null"
							: expression(argExpr, model, declarations, address, types, locals, fullProfile));
				}
			} else {
				for (var argument : call.getArgumentsList()) {
					String value = expression(argument.getExpression(), model, declarations, address, types, locals,
							fullProfile);
					if (canUseNamed)
						value = target.bkm().getParameters(argument.getParameterPosition() - 1).getVariable().getName()
								+ ": " + value;
					arguments.add(value);
				}
			}
			return function + "(" + String.join(", ", arguments) + ")";
		}
		if (expression.hasBuiltinCall()) {
			var call = expression.getBuiltinCall();
			String name = DmnIrBuiltinSignature.forOperation(call.getOperation()).orElseThrow().feelName();
			if (locals.containsValue(name) || (!fullProfile && declarations.entrySet().stream()
					.anyMatch(entry -> entry.getKey().getModelNamespace().equals(model.getNamespace())
							&& entry.getValue().name().equals(name))))
				throw invalid(address);
			return call.getArgumentsList().stream()
					.map(argument -> expression(argument, model, declarations, address, types, locals, fullProfile))
					.collect(Collectors.joining(", ", name + "(", ")"));
		}
		if (expression.hasFilter()) {
			var filter = expression.getFilter();
			var child = new LinkedHashMap<>(locals);
			child.put(filter.getItemBindingId(), "item");
			return "(" + expression(filter.getSource(), model, declarations, address, types, locals, fullProfile) + ")["
					+ expression(filter.getPredicate(), model, declarations, address, types, child, fullProfile) + "]";
		}
		if (expression.hasList())
			return expression.getList().getElementsList().stream()
					.map(element -> expression(element, model, declarations, address, types, locals, fullProfile))
					.collect(Collectors.joining(", ", "[", "]"));
		if (expression.hasGlobalReference()) {
			var reference = expression.getGlobalReference();
			var decl = declarations.get(reference);
			if (decl == null)
				throw invalid(address);
			return referenceName(reference, decl.name(), model, address, locals, fullProfile);
		}
		if (expression.hasUnary()) {
			if (expression.getUnary().getOperator() == DmnIrUnaryOperator.UNARY_OPERATOR_POSITIVE)
				return "(" + expression(expression.getUnary().getOperand(), model, declarations, address, types, locals,
						fullProfile) + ")";
			String token = switch (expression.getUnary().getOperator()) {
				case UNARY_OPERATOR_POSITIVE -> "+";
				case UNARY_OPERATOR_NEGATE -> "-";
				case UNARY_OPERATOR_NOT -> "not ";
				default -> throw invalid(address);
			};
			return "(" + token + "(" + expression(expression.getUnary().getOperand(), model, declarations, address,
					types, locals, fullProfile) + "))";
		}
		if (expression.hasBinary()) {
			var op = expression.getBinary().getOperator();
			var leftExpr = expression.getBinary().getLeft();
			var rightExpr = expression.getBinary().getRight();
			if (fullProfile
					&& (op == DmnIrBinaryOperator.BINARY_OPERATOR_LESS
							|| op == DmnIrBinaryOperator.BINARY_OPERATOR_LESS_EQUAL
							|| op == DmnIrBinaryOperator.BINARY_OPERATOR_GREATER
							|| op == DmnIrBinaryOperator.BINARY_OPERATOR_GREATER_EQUAL)
					&& (isNullLiteral(leftExpr) || isNullLiteral(rightExpr))) {
				return "null";
			}
			String token = switch (op) {
				case BINARY_OPERATOR_ADD -> "+";
				case BINARY_OPERATOR_SUBTRACT -> "-";
				case BINARY_OPERATOR_MULTIPLY -> "*";
				case BINARY_OPERATOR_DIVIDE -> "/";
				case BINARY_OPERATOR_POWER -> "**";
				case BINARY_OPERATOR_EQUAL -> "=";
				case BINARY_OPERATOR_NOT_EQUAL -> "!=";
				case BINARY_OPERATOR_LESS -> "<";
				case BINARY_OPERATOR_LESS_EQUAL -> "<=";
				case BINARY_OPERATOR_GREATER -> ">";
				case BINARY_OPERATOR_GREATER_EQUAL -> ">=";
				case BINARY_OPERATOR_AND -> "and";
				case BINARY_OPERATOR_OR -> "or";
				default -> throw invalid(address);
			};
			return "((" + expression(leftExpr, model, declarations, address, types, locals, fullProfile) + ") " + token
					+ " (" + expression(rightExpr, model, declarations, address, types, locals, fullProfile) + "))";
		}
		if (expression.hasConditional()) {
			var conditional = expression.getConditional();
			return "(if ("
					+ expression(conditional.getCondition(), model, declarations, address, types, locals, fullProfile)
					+ ") then ("
					+ expression(conditional.getThenExpression(), model, declarations, address, types, locals,
							fullProfile)
					+ ") else (" + expression(conditional.getElseExpression(), model, declarations, address, types,
							locals, fullProfile)
					+ "))";
		}
		var value = expression.getLiteral();
		return switch (value.getValueCase()) {
			case NULL_VALUE -> "null";
			case BOOLEAN_VALUE -> Boolean.toString(value.getBooleanValue());
			case NUMBER_VALUE -> new BigDecimal(new BigInteger(value.getNumberValue().getUnscaledValue()),
					value.getNumberValue().getScale()).toPlainString();
			case DATE_VALUE -> "date(\"" + DmnIrDates.feel(value.getDateValue()) + "\")";
			case TIME_VALUE -> "time(\"" + escapeFeelString(value.getTimeValue()) + "\")";
			case DATE_TIME_VALUE -> "@\"" + escapeFeelString(value.getDateTimeValue()) + "\"";
			case DURATION_VALUE -> "duration(\"" + escapeFeelString(value.getDurationValue()) + "\")";
			case STRING_VALUE -> {
				xml(value.getStringValue(), address);
				yield "\"" + escapeFeelString(value.getStringValue()) + "\"";
			}
			default -> throw invalid(address);
		};
	}

	private static DmnIrReference ref(String namespace, String id) {
		return DmnIrReference.newBuilder().setModelNamespace(namespace).setElementId(id).build();
	}

	private static Map<String, String> formalScope(DmnIrClosure closure, Map<String, String> locals,
			DmnIrReference address, boolean fullProfile) {
		var scope = new LinkedHashMap<>(locals);
		for (var formal : closure.getParametersList()) {
			name(formal.getVariable().getName(), address, fullProfile);
			if (!fullProfile && formal.getVariable().hasId() && !ID.matcher(formal.getVariable().getId()).matches())
				throw invalid(address);
			scope.put(formal.getBindingId(), formal.getVariable().getName());
		}
		return scope;
	}

	private static boolean isNullLiteral(DmnIrExpression expression) {
		return expression.hasLiteral() && expression.getLiteral().getValueCase() == DmnIrLiteral.ValueCase.NULL_VALUE;
	}

	private static String localName(String bindingId, Map<String, String> locals, DmnIrReference address,
			boolean fullProfile) {
		String name = locals.get(bindingId);
		if (name == null)
			throw invalid(address);
		String visible = null;
		for (var entry : locals.entrySet())
			if (entry.getValue().equals(name))
				visible = entry.getKey();
		if (!bindingId.equals(visible))
			throw invalid(address);
		return name;
	}

	private static String feelTypeName(DmnIrVariable formal, DmnIrModel model, DmnIrReference address,
			DmnIrRecordTypes types, boolean fullProfile) {
		if (formal.hasRecordType())
			return referenceName(formal.getRecordType(),
					types.definition(formal.getRecordType()).getIdentity().getName(), model, address, Map.of(),
					fullProfile);
		if (formal.hasCollectionType())
			return referenceName(formal.getCollectionType(),
					types.collection(formal.getCollectionType()).getIdentity().getName(), model, address, Map.of(),
					fullProfile);
		return switch (formal.getType()) {
			case SCALAR_TYPE_NUMBER -> "number";
			case SCALAR_TYPE_STRING -> "string";
			case SCALAR_TYPE_BOOLEAN -> "boolean";
			case SCALAR_TYPE_DATE -> "date";
			case SCALAR_TYPE_TIME -> "time";
			case SCALAR_TYPE_DATE_TIME -> "date and time";
			case SCALAR_TYPE_DURATION -> "duration";
			case SCALAR_TYPE_NULL -> fullProfile ? "Null" : throwInvalid(address);
			case SCALAR_TYPE_UNSPECIFIED -> fullProfile ? "Any" : throwInvalid(address);
			default -> throw invalid(address);
		};
	}

	private static String throwInvalid(DmnIrReference address) {
		throw invalid(address);
	}

	private static String referenceName(DmnIrReference reference, String variable, DmnIrModel model,
			DmnIrReference address, Map<String, String> locals, boolean fullProfile) {
		name(variable, address, fullProfile);
		if (reference.getModelNamespace().equals(model.getNamespace())) {
			if (!fullProfile && locals.containsValue(variable))
				throw invalid(address);
			return variable;
		}
		var imports = model.getImportsList().stream()
				.filter(i -> i.getModelNamespace().equals(reference.getModelNamespace())).toList();
		if (imports.isEmpty())
			throw invalid(address);
		if (!fullProfile && (imports.size() != 1 || locals.containsValue(imports.getFirst().getName())))
			throw invalid(address);
		String alias = imports.getFirst().getName();
		if (alias.isEmpty()) {
			return variable;
		}
		return alias + "." + variable;
	}

	private static DmnIrExportException invalid(DmnIrReference reference) {
		return new DmnIrExportException(DmnIrExportException.Category.UNREPRESENTABLE_ARTIFACT,
				reference == null
						? null
						: new DmnIrRuntimeAdapter.ElementId(reference.getModelNamespace(), reference.getElementId()));
	}
}

package io.finmsg.dmn.frontend.dsl;

import io.finmsg.dmn.frontend.dsl.DslModel.*;
import io.finmsg.dmn.model.*;
import java.util.*;

/**
 * Lowers a parsed {@link DslModel} into the canonical {@link Definitions}
 * protobuf model targeting DMN 1.6 by default.
 */
final class DmnDslLowerer {

	static final String DMN_1_6_NAMESPACE = "https://www.omg.org/spec/DMN/20240513/MODEL/";

	private DmnDslLowerer() {
	}

	static Definitions lower(DslModel model) {
		List<DmnDslDiagnostic> diagnostics = new ArrayList<>();
		Map<String, String> importNamespaces = new LinkedHashMap<>();
		for (DslImport imp : model.imports()) {
			if (importNamespaces.put(imp.alias(), imp.namespace()) != null) {
				diagnostics.add(new DmnDslDiagnostic("DSL-DUPLICATE-IMPORT", imp.line(), 1,
						"Duplicate import alias '" + imp.alias() + "'."));
			}
		}

		Set<String> usedIds = new HashSet<>();
		String modelId = model.id().orElseGet(() -> slug("model", model.name()));
		usedIds.add(modelId);

		Map<String, String> inputIds = new LinkedHashMap<>();
		Map<String, String> decisionIds = new LinkedHashMap<>();
		Map<String, String> bkmIds = new LinkedHashMap<>();

		for (DslInput input : model.inputs()) {
			String id = input.id().orElseGet(() -> slug("input", input.name()));
			if (!usedIds.add(id)) {
				diagnostics
						.add(new DmnDslDiagnostic("DSL-DUPLICATE-ID", input.line(), 1, "Duplicate element ID: " + id));
			}
			inputIds.put(input.name(), id);
		}
		for (DslBkm bkm : model.bkms()) {
			String id = bkm.id().orElseGet(() -> slug("bkm", bkm.name()));
			if (!usedIds.add(id)) {
				diagnostics.add(new DmnDslDiagnostic("DSL-DUPLICATE-ID", bkm.line(), 1, "Duplicate element ID: " + id));
			}
			bkmIds.put(bkm.name(), id);
		}
		for (DslDecision decision : model.decisions()) {
			String id = decision.id().orElseGet(() -> slug("decision", decision.name()));
			if (!usedIds.add(id)) {
				diagnostics.add(
						new DmnDslDiagnostic("DSL-DUPLICATE-ID", decision.line(), 1, "Duplicate element ID: " + id));
			}
			decisionIds.put(decision.name(), id);
		}

		Definitions.Builder definitions = Definitions.newBuilder()
				.setNode(Node.newBuilder().setId(modelId).setName(model.name())).setNamespace(model.namespace())
				.setModelNamespaceUri(DMN_1_6_NAMESPACE);

		for (DslImport imp : model.imports()) {
			definitions.addImports(Import.newBuilder().setName(imp.alias()).setNamespace(imp.namespace())
					.setLocationUri(imp.locationUri()).setImportType(DMN_1_6_NAMESPACE));
		}

		for (DslType type : model.types()) {
			String typeId = type.id().orElseGet(() -> slug("type", type.name()));
			if (!usedIds.add(typeId)) {
				diagnostics
						.add(new DmnDslDiagnostic("DSL-DUPLICATE-ID", type.line(), 1, "Duplicate type ID: " + typeId));
			}
			ItemDefinition.Builder item = ItemDefinition.newBuilder()
					.setNode(Node.newBuilder().setId(typeId).setName(type.name()));
			switch (type.kind()) {
				case RECORD -> {
					for (DslRecordField field : type.fields()) {
						ItemComponent.Builder comp = ItemComponent.newBuilder()
								.setNode(Node.newBuilder().setName(field.name()))
								.setType(typeRef(field.typeRef(), importNamespaces, model.namespace()));
						field.constraint()
								.ifPresent(c -> comp.setConstraint(TypeConstraint.newBuilder().setText(feelText(c))));
						item.addComponents(comp);
					}
				}
				case COLLECTION -> {
					item.setIsCollection(true);
					item.setType(typeRef(type.elementOrBaseType().orElseThrow(), importNamespaces, model.namespace()));
				}
				case ALIAS -> {
					item.setType(typeRef(type.elementOrBaseType().orElseThrow(), importNamespaces, model.namespace()));
					type.constraint()
							.ifPresent(c -> item.setConstraint(TypeConstraint.newBuilder().setText(feelText(c))));
				}
			}
			definitions.addItemDefinitions(item);
		}

		for (DslInput input : model.inputs()) {
			String id = inputIds.get(input.name());
			definitions.addDrgElements(DrgElement.newBuilder()
					.setInputData(InputData.newBuilder().setNode(Node.newBuilder().setId(id).setName(input.name()))
							.setVariable(InformationItem.newBuilder().setNode(Node.newBuilder().setName(input.name()))
									.setType(typeRef(input.typeRef(), importNamespaces, model.namespace())))));
		}

		for (DslBkm bkm : model.bkms()) {
			String id = bkmIds.get(bkm.name());
			FunctionDefinition.Builder fn = FunctionDefinition.newBuilder().setKind(FunctionKind.FUNCTION_KIND_FEEL);
			for (DslParam param : bkm.parameters()) {
				fn.addFormalParameters(InformationItem.newBuilder().setNode(Node.newBuilder().setName(param.name()))
						.setType(typeRef(param.typeRef(), importNamespaces, model.namespace())));
			}
			fn.setLogic(Feel.newBuilder().setText(feelText(bkm.bodyFeel())));

			BusinessKnowledgeModel.Builder bkmBuilder = BusinessKnowledgeModel.newBuilder()
					.setNode(Node.newBuilder().setId(id).setName(bkm.name()))
					.setVariable(InformationItem.newBuilder().setNode(Node.newBuilder().setName(bkm.name()))
							.setType(typeRef(bkm.returnType(), importNamespaces, model.namespace())))
					.setFunction(fn);
			for (DslRef ref : bkm.uses()) {
				ElementReference elemRef = resolveKnowledgeRef(ref, bkmIds, importNamespaces, bkm.line(), diagnostics);
				if (elemRef != null) {
					bkmBuilder
							.addKnowledgeRequirements(KnowledgeRequirement.newBuilder().setRequiredKnowledge(elemRef));
				}
			}
			definitions.addDrgElements(DrgElement.newBuilder().setBusinessKnowledgeModel(bkmBuilder));
		}

		for (DslDecision decision : model.decisions()) {
			String id = decisionIds.get(decision.name());
			Decision.Builder decBuilder = Decision.newBuilder()
					.setNode(Node.newBuilder().setId(id).setName(decision.name()))
					.setVariable(InformationItem.newBuilder().setNode(Node.newBuilder().setName(decision.name()))
							.setType(typeRef(decision.typeRef(), importNamespaces, model.namespace())));

			for (DslRef ref : decision.requires()) {
				addRequirement(decBuilder, ref, inputIds, decisionIds, bkmIds, importNamespaces, decision.line(),
						diagnostics);
			}
			for (DslRef ref : decision.uses()) {
				ElementReference elemRef = resolveKnowledgeRef(ref, bkmIds, importNamespaces, decision.line(),
						diagnostics);
				if (elemRef != null) {
					decBuilder
							.addKnowledgeRequirements(KnowledgeRequirement.newBuilder().setRequiredKnowledge(elemRef));
				}
			}

			DecisionLogic.Builder logic = DecisionLogic.newBuilder();
			if (decision.table().isPresent()) {
				logic.setDecisionTable(
						lowerTable(decision.table().get(), importNamespaces, model.namespace(), diagnostics));
			} else {
				logic.setLiteralExpression(Feel.newBuilder().setText(feelText(decision.literalFeel().orElseThrow())));
			}
			decBuilder.setLogic(logic);
			definitions.addDrgElements(DrgElement.newBuilder().setDecision(decBuilder));
		}

		if (!diagnostics.isEmpty()) {
			throw new DmnDslParseException(diagnostics);
		}
		return definitions.build();
	}

	private static DecisionTable lowerTable(DslTable source, Map<String, String> importNamespaces,
			String modelNamespace, List<DmnDslDiagnostic> diagnostics) {
		HitPolicySpec.Builder hp = HitPolicySpec.newBuilder();
		String normalized = source.hitPolicy().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
		switch (normalized) {
			case "unique" -> hp.setPolicy(HitPolicy.HIT_POLICY_UNIQUE);
			case "first" -> hp.setPolicy(HitPolicy.HIT_POLICY_FIRST);
			case "priority" -> hp.setPolicy(HitPolicy.HIT_POLICY_PRIORITY);
			case "any" -> hp.setPolicy(HitPolicy.HIT_POLICY_ANY);
			case "rule order" -> hp.setPolicy(HitPolicy.HIT_POLICY_RULE_ORDER);
			case "output order" -> hp.setPolicy(HitPolicy.HIT_POLICY_OUTPUT_ORDER);
			case "collect" -> hp.setPolicy(HitPolicy.HIT_POLICY_COLLECT);
			case "collect sum" ->
				hp.setPolicy(HitPolicy.HIT_POLICY_COLLECT).setAggregation(Aggregation.AGGREGATION_SUM);
			case "collect min" ->
				hp.setPolicy(HitPolicy.HIT_POLICY_COLLECT).setAggregation(Aggregation.AGGREGATION_MIN);
			case "collect max" ->
				hp.setPolicy(HitPolicy.HIT_POLICY_COLLECT).setAggregation(Aggregation.AGGREGATION_MAX);
			case "collect count" ->
				hp.setPolicy(HitPolicy.HIT_POLICY_COLLECT).setAggregation(Aggregation.AGGREGATION_COUNT);
			default -> diagnostics.add(new DmnDslDiagnostic("DSL-HIT-POLICY", source.line(), 1,
					"Unknown decision table hit policy: '" + source.hitPolicy() + "'."));
		}

		DecisionTable.Builder table = DecisionTable.newBuilder().setHitPolicy(hp);
		for (DslTableInput inCol : source.inputs()) {
			InputClause.Builder clause = InputClause.newBuilder()
					.setInputExpression(Feel.newBuilder().setText(feelText(inCol.expressionFeel())));
			inCol.label().ifPresent(l -> clause.setNode(Node.newBuilder().setLabel(l)));
			inCol.typeRef().ifPresent(t -> clause.setType(typeRef(t, importNamespaces, modelNamespace)));
			table.addInputs(clause);
		}
		for (DslTableOutput outCol : source.outputs()) {
			OutputClause.Builder clause = OutputClause.newBuilder();
			outCol.name().ifPresent(n -> clause.setNode(Node.newBuilder().setName(n)));
			outCol.typeRef().ifPresent(t -> clause.setType(typeRef(t, importNamespaces, modelNamespace)));
			outCol.defaultFeel().ifPresent(d -> clause.setDefaultOutputEntry(
					ExpressionNode.newBuilder().setText(ExpressionText.newBuilder().setFeel(feelText(d)))));
			table.addOutputs(clause);
		}
		int index = 0;
		for (DslTableRule rule : source.rules()) {
			DecisionRule.Builder ruleBuilder = DecisionRule.newBuilder().setRuleIndex(index++);
			for (String entry : rule.inputEntries()) {
				ruleBuilder.addInputEntries(UnaryTest.newBuilder().setText(feelText(entry)));
			}
			for (String entry : rule.outputEntries()) {
				ruleBuilder.addOutputEntries(Feel.newBuilder().setText(feelText(entry)));
			}
			table.addRules(ruleBuilder);
		}
		return table.build();
	}

	private static void addRequirement(Decision.Builder decision, DslRef ref, Map<String, String> inputIds,
			Map<String, String> decisionIds, Map<String, String> bkmIds, Map<String, String> importNamespaces, int line,
			List<DmnDslDiagnostic> diagnostics) {
		if (ref.importAlias().isPresent()) {
			String alias = ref.importAlias().get();
			String ns = importNamespaces.get(alias);
			if (ns == null) {
				diagnostics.add(new DmnDslDiagnostic("DSL-UNKNOWN-IMPORT", line, 1,
						"Unknown import alias '" + alias + "' in requires clause."));
				return;
			}
			String elemId = ref.explicitElementId().orElseGet(() -> slug("decision", ref.name()));
			ElementReference elemRef = ElementReference.newBuilder().setHref(ns + "#" + elemId).build();
			if (elemId.startsWith("input_")) {
				decision.addInformationRequirements(InformationRequirement.newBuilder().setInput(elemRef));
			} else if (elemId.startsWith("bkm_")) {
				decision.addKnowledgeRequirements(KnowledgeRequirement.newBuilder().setRequiredKnowledge(elemRef));
			} else {
				decision.addInformationRequirements(InformationRequirement.newBuilder().setDecision(elemRef));
			}
			return;
		}
		String inputId = inputIds.get(ref.name());
		if (inputId != null) {
			decision.addInformationRequirements(
					InformationRequirement.newBuilder().setInput(ElementReference.newBuilder().setHref("#" + inputId)));
			return;
		}
		String decId = decisionIds.get(ref.name());
		if (decId != null) {
			decision.addInformationRequirements(InformationRequirement.newBuilder()
					.setDecision(ElementReference.newBuilder().setHref("#" + decId)));
			return;
		}
		String bkmId = bkmIds.get(ref.name());
		if (bkmId != null) {
			decision.addKnowledgeRequirements(KnowledgeRequirement.newBuilder()
					.setRequiredKnowledge(ElementReference.newBuilder().setHref("#" + bkmId)));
			return;
		}
		diagnostics.add(
				new DmnDslDiagnostic("DSL-UNKNOWN-REF", line, 1, "Unknown local requirement '" + ref.name() + "'."));
	}

	private static ElementReference resolveKnowledgeRef(DslRef ref, Map<String, String> bkmIds,
			Map<String, String> importNamespaces, int line, List<DmnDslDiagnostic> diagnostics) {
		if (ref.importAlias().isPresent()) {
			String alias = ref.importAlias().get();
			String ns = importNamespaces.get(alias);
			if (ns == null) {
				diagnostics.add(new DmnDslDiagnostic("DSL-UNKNOWN-IMPORT", line, 1,
						"Unknown import alias '" + alias + "' in uses clause."));
				return null;
			}
			String elemId = ref.explicitElementId().orElseGet(() -> slug("bkm", ref.name()));
			return ElementReference.newBuilder().setHref(ns + "#" + elemId).build();
		}
		String bkmId = bkmIds.get(ref.name());
		if (bkmId == null) {
			diagnostics.add(new DmnDslDiagnostic("DSL-UNKNOWN-BKM", line, 1,
					"Unknown local BKM '" + ref.name() + "' in uses clause."));
			return null;
		}
		return ElementReference.newBuilder().setHref("#" + bkmId).build();
	}

	private static TypeReference typeRef(String raw, Map<String, String> importNamespaces, String modelNamespace) {
		String trimmed = raw.trim();
		BuiltinType builtin = switch (trimmed) {
			case "Any" -> BuiltinType.BUILTIN_TYPE_ANY;
			case "Null" -> BuiltinType.BUILTIN_TYPE_NULL;
			case "number" -> BuiltinType.BUILTIN_TYPE_NUMBER;
			case "string" -> BuiltinType.BUILTIN_TYPE_STRING;
			case "boolean" -> BuiltinType.BUILTIN_TYPE_BOOLEAN;
			case "date" -> BuiltinType.BUILTIN_TYPE_DATE;
			case "time" -> BuiltinType.BUILTIN_TYPE_TIME;
			case "date and time" -> BuiltinType.BUILTIN_TYPE_DATE_AND_TIME;
			case "duration" -> BuiltinType.BUILTIN_TYPE_DURATION;
			case "years and months duration" -> BuiltinType.BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION;
			case "days and time duration" -> BuiltinType.BUILTIN_TYPE_DAYS_AND_TIME_DURATION;
			default -> null;
		};
		if (builtin != null) {
			return TypeReference.newBuilder().setBuiltin(builtin).build();
		}
		int dot = trimmed.indexOf('.');
		if (dot > 0) {
			String alias = trimmed.substring(0, dot).trim();
			String name = trimmed.substring(dot + 1).trim();
			String ns = importNamespaces.getOrDefault(alias, modelNamespace);
			return TypeReference.newBuilder().setNamed(NamedTypeReference.newBuilder().setNamespace(ns).setName(name))
					.build();
		}
		return TypeReference.newBuilder()
				.setNamed(NamedTypeReference.newBuilder().setNamespace(modelNamespace).setName(trimmed)).build();
	}

	private static FeelText feelText(String text) {
		return FeelText.newBuilder().setText(text).build();
	}

	static String slug(String prefix, String name) {
		String cleaned = name.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (cleaned.isEmpty()) {
			cleaned = "unnamed";
		}
		return prefix + "_" + cleaned;
	}
}

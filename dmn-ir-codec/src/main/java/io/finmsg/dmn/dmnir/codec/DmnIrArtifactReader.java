package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;

import com.google.protobuf.CodedInputStream;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Strict, bounded reader for experimental portable profiles 0.0 through 0.11.
 */
public final class DmnIrArtifactReader {
	public static final String SEMANTICS_VERSION = "finmsg-dmnir-scalar/0";
	public static final String EXPRESSION_SEMANTICS_VERSION = "finmsg-dmnir-scalar-expressions/0";
	public static final String EXPRESSION_CAPABILITY = "dmnir.scalar-expressions";
	public static final String RECORD_SEMANTICS_VERSION = "finmsg-dmnir-records/0";
	public static final String RECORD_CAPABILITY = "dmnir.records";
	public static final String SCALAR_CAPABILITY = "dmnir.scalar";
	public static final String LOCAL_SEMANTICS_VERSION = "finmsg-dmnir-local-bindings/0";
	public static final String LOCAL_CAPABILITY = "dmnir.local-bindings";
	public static final String DATE_SEMANTICS_VERSION = "finmsg-dmnir-dates/0";
	public static final String DATE_CAPABILITY = "dmnir.date";
	public static final String TABLE_SEMANTICS_VERSION = "finmsg-dmnir-tables/0";
	public static final String TABLE_CAPABILITY = "dmnir.tables";
	public static final String FIELD_CONSTRAINT_SEMANTICS_VERSION = "finmsg-dmnir-field-constraints/0";
	public static final String FIELD_CONSTRAINT_CAPABILITY = "dmnir.field-constraints";
	public static final String COLLECTION_SEMANTICS_VERSION = "finmsg-dmnir-scalar-collections/0";
	public static final String COLLECTION_CAPABILITY = "dmnir.scalar-collections";
	public static final String FILTER_SEMANTICS_VERSION = "finmsg-dmnir-scalar-filters/0";
	public static final String FILTER_CAPABILITY = "dmnir.scalar-filters";
	public static final String BUILTIN_SEMANTICS_VERSION = "finmsg-dmnir-builtin-calls/0";
	public static final String BUILTIN_CAPABILITY = "dmnir.builtin-calls";
	public static final String BKM_SEMANTICS_VERSION = "finmsg-dmnir-named-feel-bkms/0";
	public static final String BKM_CAPABILITY = "dmnir.named-feel-bkms";
	public static final String CLOSURE_SEMANTICS_VERSION = "finmsg-dmnir-inline-feel/0";
	public static final String CLOSURE_CAPABILITY = "dmnir.inline-feel";
	private static final List<String> CAPABILITIES = List.of(SCALAR_CAPABILITY, EXPRESSION_CAPABILITY,
			RECORD_CAPABILITY, LOCAL_CAPABILITY, DATE_CAPABILITY, TABLE_CAPABILITY, FIELD_CONSTRAINT_CAPABILITY,
			COLLECTION_CAPABILITY, FILTER_CAPABILITY, BUILTIN_CAPABILITY, BKM_CAPABILITY, CLOSURE_CAPABILITY);
	private final DmnIrLimits limits;

	public DmnIrArtifactReader() {
		this(DmnIrLimits.defaults());
	}

	public DmnIrArtifactReader(DmnIrLimits limits) {
		this.limits = Objects.requireNonNull(limits, "limits");
	}

	/**
	 * The caller's array is snapshotted before either scan to prevent mutation
	 * between passes.
	 */
	public VerifiedArtifact read(byte[] bytes) {
		Objects.requireNonNull(bytes, "bytes");
		check(bytes.length <= limits.maxBytes(), LIMIT_EXCEEDED, "artifact", "Byte budget exceeded.");
		byte[] snapshot = bytes.clone();
		try {
			new DmnIrWireScanner(limits).scan(snapshot);
			CodedInputStream input = CodedInputStream.newInstance(snapshot);
			input.setRecursionLimit(limits.maxDepth());
			input.setSizeLimit(limits.maxBytes());
			DmnIrArtifact artifact = DmnIrArtifact.parseFrom(input);
			new Validation(limits).validate(artifact);
			return new VerifiedArtifact(artifact);
		} catch (IOException malformed) {
			throw new DmnIrValidationException(MALFORMED_PROTOBUF, "artifact", "Invalid or truncated Protobuf data.");
		}
	}

	/**
	 * Only this reader can construct the immutable successful-validation result.
	 */
	public static final class VerifiedArtifact {
		private final DmnIrArtifact artifact;

		private VerifiedArtifact(DmnIrArtifact artifact) {
			this.artifact = artifact;
		}

		public DmnIrArtifact artifact() {
			return artifact;
		}
	}

	static void check(boolean condition, DmnIrValidationException.Code code, String path, String reason) {
		if (!condition) {
			throw new DmnIrValidationException(code, path, reason);
		}
	}

	private record Address(String namespace, String id) {
		static Address of(DmnIrReference reference) {
			return new Address(reference.getModelNamespace(), reference.getElementId());
		}
	}

	private record Element(DmnIrTypeReference type, DmnIrDecision decision, DmnIrBkm bkm, String path,
			String namespace) {
	}

	private static final class Validation {
		private final DmnIrLimits limits;
		private final Map<String, DmnIrModel> models = new LinkedHashMap<>();
		private final Map<Address, Element> elements = new LinkedHashMap<>();
		private final Map<String, Set<String>> imports = new HashMap<>();
		private int references;
		private boolean expressions;
		private boolean recordsProfile;
		private boolean localsProfile;
		private boolean datesProfile;
		private boolean tablesProfile;
		private boolean fieldConstraintsProfile;
		private boolean collectionsProfile;
		private boolean filtersProfile;
		private boolean builtinProfile;
		private boolean bkmProfile;
		private boolean closureProfile;
		private boolean filterPredicate;
		private boolean tableCell;
		private final Map<String, Set<String>> modelIds = new HashMap<>();
		private final Set<String> bindingIds = new HashSet<>();
		private Map<String, DmnIrTypeReference> visibleLocals = new HashMap<>();
		private Map<String, DmnIrClosure> visibleClosures = new HashMap<>();
		private Set<String> unusedCaptures;
		private Set<Address> visibleKnowledge = Set.of();
		private int declarations;
		private long expandedTypes;
		private final Map<Address, DmnIrScalarType> collections = new HashMap<>();
		private final Map<Address, DmnIrRecordDefinition> records = new LinkedHashMap<>();
		private final Map<Address, Long> costs = new HashMap<>();
		private final Map<Address, String> recordPaths = new HashMap<>();

		Validation(DmnIrLimits limits) {
			this.limits = limits;
		}

		void validate(DmnIrArtifact artifact) {
			check(artifact.hasSchemaVersion() && artifact.getSchemaVersion().hasMajor()
					&& artifact.getSchemaVersion().hasMinor(), INVALID_HEADER, "artifact.schema_version",
					"Explicit major and minor versions are required.");
			int minor = artifact.getSchemaVersion().getMinor();
			String semantics = switch (minor) {
				case 0 -> SEMANTICS_VERSION;
				case 1 -> EXPRESSION_SEMANTICS_VERSION;
				case 2 -> RECORD_SEMANTICS_VERSION;
				case 3 -> LOCAL_SEMANTICS_VERSION;
				case 4 -> DATE_SEMANTICS_VERSION;
				case 5 -> TABLE_SEMANTICS_VERSION;
				case 6 -> FIELD_CONSTRAINT_SEMANTICS_VERSION;
				case 7 -> COLLECTION_SEMANTICS_VERSION;
				case 8 -> FILTER_SEMANTICS_VERSION;
				case 9 -> BUILTIN_SEMANTICS_VERSION;
				case 10 -> BKM_SEMANTICS_VERSION;
				case 11 -> CLOSURE_SEMANTICS_VERSION;
				default -> "";
			};
			check(artifact.getSchemaVersion().getMajor() == 0 && !semantics.isEmpty()
					&& artifact.getSemanticsVersion().equals(semantics), UNSUPPORTED_VERSION, "artifact.schema_version",
					"Unsupported wire or semantic version.");
			filtersProfile = minor >= 8;
			builtinProfile = minor >= 9;
			bkmProfile = minor >= 10;
			closureProfile = minor >= 11;
			collectionsProfile = minor >= 7;
			fieldConstraintsProfile = minor >= 6;
			tablesProfile = minor >= 5;
			datesProfile = minor >= 4;
			localsProfile = minor >= 3;
			recordsProfile = minor >= 2;
			expressions = minor >= 1;
			var expected = CAPABILITIES.subList(0, minor + 1);
			Set<String> capabilities = new HashSet<>();
			for (int i = 0; i < artifact.getRequiredCapabilitiesCount(); i++) {
				String capability = artifact.getRequiredCapabilities(i);
				String path = "artifact.required_capabilities[" + i + "]";
				check(expected.contains(capability), UNSUPPORTED_CAPABILITY, path,
						"Required capability is not supported.");
				check(capabilities.add(capability), INVALID_HEADER, path, "Duplicate required capability.");
			}
			check(capabilities.contains(SCALAR_CAPABILITY), INVALID_HEADER, "artifact.required_capabilities",
					"The scalar capability must be declared.");
			check(artifact.getRequiredCapabilitiesList().equals(expected), INVALID_HEADER,
					"artifact.required_capabilities", "Exact ordered profile capabilities are required.");
			check(artifact.getModelsCount() > 0, INVALID_HEADER, "artifact.models", "A model is required.");
			check(artifact.getModelsCount() <= limits.maxModels(), LIMIT_EXCEEDED, "artifact.models",
					"Model budget exceeded.");
			for (int i = 0; i < artifact.getModelsCount(); i++) {
				DmnIrModel model = artifact.getModels(i);
				String path = "artifact.models[" + i + "]";
				check(!model.getNamespace().isBlank(), INVALID_IDENTITY, path + ".namespace",
						"A model namespace is required.");
				check(models.putIfAbsent(model.getNamespace(), model) == null, INVALID_IDENTITY, path + ".namespace",
						"Duplicate model namespace.");
				Set<String> ids = new HashSet<>();
				modelIds.put(model.getNamespace(), ids);
				Set<String> names = new HashSet<>();
				Set<String> variableNames = new HashSet<>();
				identity(model.getIdentity(), ids, null, path + ".identity");
				check(recordsProfile || model.getRecordDefinitionsCount() == 0, UNSUPPORTED_CAPABILITY,
						path + ".record_definitions", "Records require profile 0.2.");
				Set<String> typeNames = new HashSet<>();
				check(collectionsProfile || model.getCollectionDefinitionsCount() == 0, UNSUPPORTED_CAPABILITY,
						path + ".collection_definitions", "Collections require profile 0.7.");
				for (int j = 0; j < model.getCollectionDefinitionsCount(); j++) {
					var collection = model.getCollectionDefinitions(j);
					String collectionPath = path + ".collection_definitions[" + j + "]";
					budget(collectionPath);
					identity(collection.getIdentity(), ids, typeNames, collectionPath + ".identity");
					var element = collection.getElementType();
					scalar(element, collectionPath + ".element_type");
					check(element != DmnIrScalarType.SCALAR_TYPE_NULL, INVALID_TYPE, collectionPath + ".element_type",
							"A collection needs a non-null scalar element type.");
					collections.put(new Address(model.getNamespace(), collection.getIdentity().getId()), element);
				}
				for (int j = 0; j < model.getRecordDefinitionsCount(); j++) {
					var record = model.getRecordDefinitions(j);
					String recordPath = path + ".record_definitions[" + j + "]";
					budget(recordPath);
					identity(record.getIdentity(), ids, typeNames, recordPath + ".identity");
					check(record.getFieldsCount() > 0, INVALID_TYPE, recordPath, "Non-empty records are required.");
					records.put(new Address(model.getNamespace(), record.getIdentity().getId()), record);
					recordPaths.put(new Address(model.getNamespace(), record.getIdentity().getId()), recordPath);
					Set<String> fieldNames = new HashSet<>();
					for (int k = 0; k < record.getFieldsCount(); k++) {
						var field = record.getFields(k);
						String fieldPath = recordPath + ".fields[" + k + "]";
						budget(fieldPath);
						check(!field.getName().isBlank() && fieldNames.add(field.getName()), INVALID_IDENTITY,
								fieldPath, "Unique non-empty field names are required.");
						if (field.hasId())
							check(!field.getId().isBlank() && ids.add(field.getId()), INVALID_IDENTITY, fieldPath,
									"A unique field ID is required when present.");
					}
				}
				for (int j = 0; j < model.getInputsCount(); j++) {
					DmnIrInput input = model.getInputs(j);
					declaration(model, input.getIdentity(), input.getVariable(), null, null, ids, names, variableNames,
							path + ".inputs[" + j + "]");
				}
				for (int j = 0; j < model.getDecisionsCount(); j++) {
					DmnIrDecision decision = model.getDecisions(j);
					declaration(model, decision.getIdentity(), decision.getVariable(), decision, null, ids, names,
							variableNames, path + ".decisions[" + j + "]");
				}
				check(bkmProfile || model.getBkmsCount() == 0, UNSUPPORTED_CAPABILITY, path + ".bkms",
						"BKMs require profile 0.10.");
				for (int j = 0; j < model.getBkmsCount(); j++) {
					var bkm = model.getBkms(j);
					declaration(model, bkm.getIdentity(), bkm.getVariable(), null, bkm, ids, names, variableNames,
							path + ".bkms[" + j + "]");
				}
			}
			check(models.containsKey(artifact.getRootModelNamespace()), INVALID_HEADER, "artifact.root_model_namespace",
					"Root model is not in the artifact.");
			validateImports(artifact);
			recordGraph();
			for (Element element : elements.values())
				validateType(element.type(), element.namespace(),
						element.path() + (element.type().hasRecordType() ? ".variable.record_type" : ".variable.type"));
			Map<Address, Set<Address>> knowledgeGraph = new LinkedHashMap<>();
			for (var entry : elements.entrySet()) {
				var element = entry.getValue();
				if (element.bkm() != null)
					knowledgeGraph.put(entry.getKey(),
							knowledge(element, element.bkm().getKnowledgeDependenciesList()));
			}
			Map<Address, Integer> heights = new HashMap<>();
			for (var address : knowledgeGraph.keySet())
				knowledgeHeight(address, knowledgeGraph, heights, new HashSet<>(), 1);
			for (Element element : elements.values()) {
				if (element.decision() != null) {
					expression(element);
				} else if (element.bkm() != null) {
					bkmBody(element,
							knowledgeGraph.get(new Address(element.namespace(), element.bkm().getIdentity().getId())));
				}
			}
			order(artifact);
		}

		private void identity(DmnIrElementIdentity identity, Set<String> ids, Set<String> names, String path) {
			check(!identity.getId().isBlank() && !identity.getName().isBlank(), INVALID_IDENTITY, path,
					"An ID and name are required.");
			check(ids.add(identity.getId()), INVALID_IDENTITY, path + ".id", "Duplicate element or variable ID.");
			if (names != null) {
				check(names.add(identity.getName()), INVALID_IDENTITY, path + ".name", "Duplicate declaration name.");
			}
		}

		private void declaration(DmnIrModel model, DmnIrElementIdentity identity, DmnIrVariable variable,
				DmnIrDecision decision, DmnIrBkm bkm, Set<String> ids, Set<String> names, Set<String> variableNames,
				String path) {
			budget(path);
			identity(identity, ids, names, path + ".identity");
			check(!variable.getName().isBlank() && variableNames.add(variable.getName()), INVALID_IDENTITY,
					path + ".variable.name", "A unique variable name is required.");
			if (variable.hasId()) {
				check(!variable.getId().isBlank() && ids.add(variable.getId()), INVALID_IDENTITY, path + ".variable.id",
						"A variable ID must be non-empty and unique.");
			}
			var type = typeOf(variable, path + ".variable.type");
			elements.put(new Address(model.getNamespace(), identity.getId()),
					new Element(type, decision, bkm, path, model.getNamespace()));
		}

		private void validateImports(DmnIrArtifact artifact) {
			for (int i = 0; i < artifact.getModelsCount(); i++) {
				DmnIrModel model = artifact.getModels(i);
				Set<String> targets = new HashSet<>();
				Set<String> aliases = new HashSet<>();
				for (int j = 0; j < model.getImportsCount(); j++) {
					DmnIrImport value = model.getImports(j);
					String path = "artifact.models[" + i + "].imports[" + j + "]";
					check(models.containsKey(value.getModelNamespace())
							&& !value.getModelNamespace().equals(model.getNamespace()), INVALID_IMPORT, path,
							"An import must reference another bundled model.");
					check(!value.getName().isBlank() && aliases.add(value.getName()), INVALID_IMPORT, path + ".name",
							"A unique non-empty import alias is required.");
					targets.add(value.getModelNamespace());
				}
				imports.put(model.getNamespace(), targets);
			}
		}

		private Element reference(DmnIrReference value, Element owner, String path) {
			check(++references <= limits.maxReferences(), LIMIT_EXCEEDED, path, "Reference budget exceeded.");
			Element target = elements.get(Address.of(value));
			check(target != null, INVALID_REFERENCE, path, "Reference does not resolve to a declaration.");
			if (owner != null) {
				check(value.getModelNamespace().equals(owner.namespace())
						|| imports.get(owner.namespace()).contains(value.getModelNamespace()), INVALID_REFERENCE, path,
						"Cross-model reference requires a direct import.");
			}
			return target;
		}

		private void expression(Element owner) {
			bindingIds.clear();
			visibleLocals = new HashMap<>();
			DmnIrDecision decision = owner.decision();
			visibleKnowledge = knowledge(owner, decision.getKnowledgeDependenciesList());
			String path = owner.path();
			Set<Address> dependencies = new HashSet<>();
			for (int i = 0; i < decision.getDependenciesCount(); i++) {
				DmnIrReference value = decision.getDependencies(i);
				String dependencyPath = path + ".dependencies[" + i + "]";
				Element target = reference(value, owner, dependencyPath);
				check(target.bkm() == null && target != owner && dependencies.add(Address.of(value)),
						INVALID_DEPENDENCY, dependencyPath, "Expected a unique input or decision dependency.");
			}
			check(!decision.hasDecisionTable() || tablesProfile, UNSUPPORTED_CAPABILITY, path,
					"Decision tables require profile 0.5.");
			check(decision.hasDecisionTable() != decision.hasExpression(), INVALID_EXPRESSION, path,
					"Exactly one decision body is required.");
			if (decision.hasDecisionTable()) {
				tableCell = true;
				table(decision.getDecisionTable(), owner, dependencies, path + ".decision_table");
				tableCell = false;
				return;
			}
			DmnIrExpression expression = decision.getExpression();
			var resultType = typeOf(expression, path + ".expression.type");
			check(assignable(resultType, owner.type()), INVALID_TYPE, path + ".expression.type",
					"Expression type is incompatible with its declared result.");
			node(expression, owner, dependencies, path + ".expression", 1);
		}

		private Set<Address> knowledge(Element owner, List<DmnIrReference> references) {
			String path = owner.path() + ".knowledge_dependencies";
			check(bkmProfile || references.isEmpty(), UNSUPPORTED_CAPABILITY, path,
					"Knowledge dependencies require profile 0.10.");
			Set<Address> dependencies = new LinkedHashSet<>();
			for (int i = 0; i < references.size(); i++) {
				var value = references.get(i);
				String dependencyPath = path + "[" + i + "]";
				Element target = reference(value, owner, dependencyPath);
				check(target.bkm() != null && target != owner && dependencies.add(Address.of(value)),
						INVALID_DEPENDENCY, dependencyPath, "Expected a unique BKM dependency other than the owner.");
			}
			return dependencies;
		}

		private int knowledgeHeight(Address address, Map<Address, Set<Address>> graph, Map<Address, Integer> heights,
				Set<Address> active, int depth) {
			String path = elements.get(address).path() + ".knowledge_dependencies";
			check(depth <= limits.maxDepth(), LIMIT_EXCEEDED, path, "Knowledge dependency depth exceeded.");
			Integer height = heights.get(address);
			if (height != null) {
				check(depth + height - 1 <= limits.maxDepth(), LIMIT_EXCEEDED, path,
						"Knowledge dependency depth exceeded.");
				return height;
			}
			check(active.add(address), INVALID_DEPENDENCY, path, "Cyclic knowledge dependency.");
			int result = 1;
			for (var dependency : graph.get(address))
				result = Math.max(result, 1 + knowledgeHeight(dependency, graph, heights, active, depth + 1));
			active.remove(address);
			heights.put(address, result);
			return result;
		}

		private void bkmBody(Element owner, Set<Address> knowledge) {
			bindingIds.clear();
			visibleLocals = new HashMap<>();
			visibleKnowledge = knowledge;
			var bkm = owner.bkm();
			formals(bkm.getParametersList(), owner, owner.path());
			String bodyPath = owner.path() + ".body";
			check(bkm.hasBody(), INVALID_EXPRESSION, bodyPath, "A BKM body is required.");
			check(assignable(typeOf(bkm.getBody(), bodyPath), owner.type()), INVALID_TYPE, bodyPath,
					"BKM body type must be compatible with the declared result.");
			node(bkm.getBody(), owner, Set.of(), bodyPath, 1);
		}

		private void formals(List<DmnIrBkmParameter> parameters, Element owner, String declarationPath) {
			Set<String> names = new HashSet<>();
			for (int i = 0; i < parameters.size(); i++) {
				var parameter = parameters.get(i);
				var variable = parameter.getVariable();
				String path = declarationPath + ".parameters[" + i + "]";
				budget(path);
				check(!parameter.getBindingId().isBlank() && bindingIds.add(parameter.getBindingId())
						&& !variable.getName().isBlank() && names.add(variable.getName()), INVALID_IDENTITY, path,
						"Parameters require unique names and binding IDs.");
				if (variable.hasId())
					check(!variable.getId().isBlank() && modelIds.get(owner.namespace()).add(variable.getId()),
							INVALID_IDENTITY, path, "A parameter ID must be non-empty and unique when present.");
				var type = typeOf(variable, path);
				validateType(type, owner.namespace(), path);
				visibleLocals.put(parameter.getBindingId(), type);
			}
		}

		private void closure(DmnIrClosure definition, Element owner, String path, int depth) {
			check(unusedCaptures == null, INVALID_EXPRESSION, path, "Nested closure definitions are unsupported.");
			budget(path);
			check(definition.hasResultType() && definition.hasBody(), INVALID_EXPRESSION, path,
					"Closure requires a result type and body.");
			validateType(definition.getResultType(), owner.namespace(), path + ".result_type");
			var outer = visibleLocals;
			var captures = new HashMap<String, DmnIrTypeReference>();
			byte[] previous = null;
			for (String capture : definition.getCapturedBindingIdsList()) {
				check(++references <= limits.maxReferences(), LIMIT_EXCEEDED, path, "Reference budget exceeded.");
				var type = outer.get(capture);
				check(type != null, INVALID_REFERENCE, path, "Capture must reference a visible value binding.");
				byte[] current = capture.getBytes(StandardCharsets.UTF_8);
				check(previous == null || Arrays.compareUnsigned(previous, current) < 0, INVALID_REFERENCE, path,
						"Capture IDs must be unique and sorted by UTF-8 bytes.");
				captures.put(capture, type);
				previous = current;
			}
			visibleLocals = captures;
			unusedCaptures = new HashSet<>(captures.keySet());
			try {
				formals(definition.getParametersList(), owner, path);
				String bodyPath = path + ".body";
				check(assignable(typeOf(definition.getBody(), bodyPath), definition.getResultType()), INVALID_TYPE,
						bodyPath, "Closure body type must be compatible with the declared result.");
				node(definition.getBody(), owner, Set.of(), bodyPath, depth + 1);
				check(unusedCaptures.isEmpty(), INVALID_REFERENCE, path, "Closure has unused captures.");
			} finally {
				unusedCaptures = null;
				visibleLocals = outer;
			}
		}

		private void table(DmnIrDecisionTable table, Element owner, Set<Address> dependencies, String path) {
			switch (table.getHitPolicy()) {
				case TABLE_HIT_POLICY_UNIQUE, TABLE_HIT_POLICY_FIRST, TABLE_HIT_POLICY_ANY -> {
				}
				default -> check(false, INVALID_EXPRESSION, path + ".hit_policy", "Unsupported hit policy.");
			}
			check(table.getInputsCount() > 0 && table.getOutputsCount() > 0 && table.getRulesCount() > 0,
					INVALID_EXPRESSION, path, "A table requires inputs, outputs and rules.");
			if (table.hasId())
				tableId(table.getId(), owner, path);
			for (int i = 0; i < table.getInputsCount(); i++) {
				var input = table.getInputs(i);
				String inputPath = path + ".inputs[" + i + "]";
				budget(inputPath);
				if (input.hasId())
					tableId(input.getId(), owner, inputPath);
				cell(input.getExpression(), input.getType(), owner, dependencies, inputPath + ".expression");
			}
			var fields = owner.type().hasRecordType()
					? records.get(Address.of(owner.type().getRecordType())).getFieldsList()
					: List.<DmnIrRecordField>of();
			check(table.getOutputsCount() == 1
					? owner.type().hasScalarType()
					: table.getOutputsCount() == fields.size(), INVALID_TYPE, path, "Incompatible table result shape.");
			for (int i = 0; i < table.getOutputsCount(); i++) {
				var output = table.getOutputs(i);
				String outputPath = path + ".outputs[" + i + "]";
				budget(outputPath);
				if (output.hasId())
					tableId(output.getId(), owner, outputPath);
				check(!output.hasName() || !output.getName().isBlank(), INVALID_IDENTITY, outputPath,
						"An output name must be non-empty when present.");
				scalar(output.getType(), outputPath);
				if (fields.isEmpty()) {
					check(output.getType() == owner.type().getScalarType(), INVALID_TYPE, outputPath,
							"Output type must match the decision type.");
				} else {
					var field = fields.get(i);
					check(output.getName().equals(field.getName()) && field.getType().hasScalarType()
							&& output.getType() == field.getType().getScalarType(), INVALID_TYPE, outputPath,
							"Output names and types must match record fields in declaration order.");
				}
				if (output.hasDefaultExpression())
					cell(output.getDefaultExpression(), output.getType(), owner, dependencies,
							outputPath + ".default_expression");
			}
			for (int i = 0; i < table.getRulesCount(); i++) {
				var rule = table.getRules(i);
				String rulePath = path + ".rules[" + i + "]";
				budget(rulePath);
				if (rule.hasId())
					tableId(rule.getId(), owner, rulePath);
				check(rule.getInputEntriesCount() == table.getInputsCount()
						&& rule.getOutputEntriesCount() == table.getOutputsCount(), INVALID_EXPRESSION, rulePath,
						"Rule entry counts must match column counts.");
				for (int j = 0; j < rule.getInputEntriesCount(); j++) {
					var type = table.getInputs(j).getType();
					tests(rule.getInputEntries(j), type, rulePath + ".input_entries[" + j + "]",
							(value, endpointPath) -> cell(value, type, owner, dependencies, endpointPath));
				}
				for (int j = 0; j < rule.getOutputEntriesCount(); j++)
					cell(rule.getOutputEntries(j), table.getOutputs(j).getType(), owner, dependencies,
							rulePath + ".output_entries[" + j + "]");
			}
		}

		private void tableId(String id, Element owner, String path) {
			check(!id.isBlank() && modelIds.get(owner.namespace()).add(id), INVALID_IDENTITY, path + ".id",
					"A unique non-empty ID is required when present.");
		}

		private void tests(DmnIrUnaryTests group, DmnIrScalarType type, String path,
				BiConsumer<DmnIrExpression, String> endpointValidator) {
			check(group.getWildcard() ? !group.getNegated() && group.getTestsCount() == 0 : group.getTestsCount() > 0,
					INVALID_EXPRESSION, path, "Expected a wildcard or non-empty test alternatives.");
			for (int i = 0; i < group.getTestsCount(); i++) {
				var test = group.getTests(i);
				String testPath = path + ".tests[" + i + "]";
				switch (test.getTestCase()) {
					case COMPARISON -> {
						var comparison = test.getComparison();
						switch (comparison.getOperator()) {
							case BINARY_OPERATOR_EQUAL, BINARY_OPERATOR_NOT_EQUAL -> {
								var endpoint = comparison.getEndpoint();
								check(endpoint.getType() != DmnIrScalarType.SCALAR_TYPE_NULL
										|| endpoint.getLiteral().getValueCase() == DmnIrLiteral.ValueCase.NULL_VALUE,
										INVALID_TYPE, testPath, "A null endpoint must be explicit.");
								endpointValidator.accept(endpoint, testPath + ".comparison.endpoint");
							}
							case BINARY_OPERATOR_LESS, BINARY_OPERATOR_LESS_EQUAL, BINARY_OPERATOR_GREATER,
									BINARY_OPERATOR_GREATER_EQUAL ->
								orderedEndpoint(comparison.getEndpoint(), type, endpointValidator,
										testPath + ".comparison.endpoint");
							default -> check(false, INVALID_EXPRESSION, testPath, "Unsupported test comparison.");
						}
					}
					case RANGE -> {
						orderedEndpoint(test.getRange().getLower(), type, endpointValidator, testPath + ".range.lower");
						orderedEndpoint(test.getRange().getUpper(), type, endpointValidator, testPath + ".range.upper");
					}
					case TEST_NOT_SET -> check(false, INVALID_EXPRESSION, testPath, "A test is required.");
				}
			}
		}

		private void orderedEndpoint(DmnIrExpression endpoint, DmnIrScalarType type,
				BiConsumer<DmnIrExpression, String> endpointValidator, String path) {
			check((type == DmnIrScalarType.SCALAR_TYPE_NUMBER || type == DmnIrScalarType.SCALAR_TYPE_STRING
					|| type == DmnIrScalarType.SCALAR_TYPE_DATE) && endpoint.getType() == type, INVALID_TYPE, path,
					"Ordered endpoints require the same ordered scalar type as the input.");
			endpointValidator.accept(endpoint, path);
		}

		private void cell(DmnIrExpression expression, DmnIrScalarType type, Element owner, Set<Address> dependencies,
				String path) {
			scalar(type, path);
			check(!expression.hasRecordType() && !expression.hasCollectionType()
					&& assignable(expression.getType(), type), INVALID_TYPE, path,
					"Cell expression must be compatible with the column's scalar type.");
			node(expression, owner, dependencies, path, 1);
		}

		private void node(DmnIrExpression expression, Element owner, Set<Address> dependencies, String path,
				int depth) {
			check(depth <= limits.maxDepth(), LIMIT_EXCEEDED, path, "Expression depth budget exceeded.");
			String nodeTypePath = path + (expression.hasRecordType() ? ".record_type" : ".type");
			var nodeType = typeOf(expression, nodeTypePath);
			validateType(nodeType, owner.namespace(), nodeTypePath);
			check(!tableCell || !expression.hasContext() && !expression.hasCollectionType(), INVALID_EXPRESSION, path,
					"Boxed expressions and collections are unsupported in table cells.");
			switch (expression.getNodeCase()) {
				case LOCAL_REFERENCE -> {
					check(localsProfile, UNSUPPORTED_CAPABILITY, path, "Local references require profile 0.3.");
					check(++references <= limits.maxReferences(), LIMIT_EXCEEDED, path, "Reference budget exceeded.");
					String bindingId = expression.getLocalReference().getBindingId();
					var target = visibleLocals.get(bindingId);
					check(target != null, INVALID_REFERENCE, path, "Local binding is not visible here.");
					check(target.equals(nodeType), INVALID_TYPE, path,
							"Local reference type must match its declaration.");
					if (unusedCaptures != null)
						unusedCaptures.remove(bindingId);
				}
				case CONTEXT -> {
					check(!filterPredicate, INVALID_EXPRESSION, path, "Boxed predicates cannot be rendered in FEEL.");
					check(localsProfile, UNSUPPORTED_CAPABILITY, path, "Contexts require profile 0.3.");
					var context = expression.getContext();
					check(context.hasResult(), INVALID_EXPRESSION, path, "A final context result is required.");
					var outer = visibleLocals;
					var outerClosures = visibleClosures;
					visibleLocals = new HashMap<>(outer);
					visibleClosures = new HashMap<>(outerClosures);
					Set<String> names = new HashSet<>();
					try {
						for (int i = 0; i < context.getBindingsCount(); i++) {
							var binding = context.getBindings(i);
							String bp = path + ".context.bindings[" + i + "]";
							budget(bp);
							check(!binding.getBindingId().isBlank() && bindingIds.add(binding.getBindingId()),
									INVALID_IDENTITY, bp, "Unique decision-local binding IDs are required.");
							if (binding.hasClosure()) {
								check(closureProfile, UNSUPPORTED_CAPABILITY, bp, "Closures require profile 0.11.");
								var closure = binding.getClosure();
								check(!binding.hasVariable() && !binding.hasValue() && closure.hasDefinition(),
										INVALID_EXPRESSION, bp,
										"Expected a closure definition without a value binding.");
								check(!closure.getName().isBlank() && names.add(closure.getName()), INVALID_IDENTITY,
										bp, "Unique non-empty context variable names are required.");
								if (closure.hasId())
									check(!closure.getId().isBlank()
											&& modelIds.get(owner.namespace()).add(closure.getId()), INVALID_IDENTITY,
											bp, "Source variable ID must be unique.");
								closure(closure.getDefinition(), owner, bp + ".closure.definition", depth + 1);
								visibleClosures.put(binding.getBindingId(), closure.getDefinition());
								continue;
							}
							var variable = binding.getVariable();
							check(!variable.getName().isBlank() && names.add(variable.getName()), INVALID_IDENTITY, bp,
									"Unique non-empty context variable names are required.");
							if (variable.hasId())
								check(!variable.getId().isBlank()
										&& modelIds.get(owner.namespace()).add(variable.getId()), INVALID_IDENTITY,
										bp + ".variable.id", "Source variable ID must be unique.");
							var bt = typeOf(variable, bp + ".variable.type");
							validateType(bt, owner.namespace(), bp + ".variable.type");
							check(binding.hasValue(), INVALID_EXPRESSION, bp, "Binding value is required.");
							node(binding.getValue(), owner, dependencies, bp + ".value", depth + 1);
							var value = binding.getValue();
							check(assignable(typeOf(value, bp), bt), INVALID_TYPE, bp,
									"Binding value type is incompatible.");
							visibleLocals.put(binding.getBindingId(), bt);
						}
						node(context.getResult(), owner, dependencies, path + ".context.result", depth + 1);
						var result = context.getResult();
						check(assignable(typeOf(result, path), nodeType), INVALID_TYPE, path,
								"Context result type is incompatible.");
					} finally {
						visibleLocals = outer;
						visibleClosures = outerClosures;
					}
				}
				case LITERAL -> {
					check(!nodeType.hasCollectionType(), INVALID_TYPE, path + ".type",
							"Use a list constructor or scalar null for a collection value.");
					literal(expression.getLiteral(), expression.getType(), path + ".literal");
				}
				case CLOSURE_INVOCATION -> {
					check(closureProfile, UNSUPPORTED_CAPABILITY, path, "Closure calls require profile 0.11.");
					check(unusedCaptures == null, INVALID_EXPRESSION, path, "Nested closure calls are unsupported.");
					var call = expression.getClosureInvocation();
					var definition = switch (call.getTargetCase()) {
						case INLINE -> {
							closure(call.getInline(), owner, path + ".closure_invocation.inline", depth + 1);
							yield call.getInline();
						}
						case LOCAL_BINDING_ID -> {
							check(++references <= limits.maxReferences(), LIMIT_EXCEEDED, path,
									"Reference budget exceeded.");
							var target = visibleClosures.get(call.getLocalBindingId());
							check(target != null, INVALID_REFERENCE, path, "Closure binding is not visible here.");
							yield target;
						}
						case TARGET_NOT_SET -> throw new DmnIrValidationException(INVALID_EXPRESSION, path,
								"Closure call requires a target.");
					};
					check(nodeType.equals(definition.getResultType()), INVALID_TYPE, path,
							"Closure call type must match the result declaration.");
					check(call.getArgumentsCount() == definition.getParametersCount(), INVALID_EXPRESSION, path,
							"Closure call requires an argument for every formal parameter.");
					for (int i = 0; i < call.getArgumentsCount(); i++) {
						var argument = call.getArguments(i);
						String argumentPath = path + ".closure_invocation.arguments[" + i + "]";
						var expected = typeOf(definition.getParameters(i).getVariable(), argumentPath);
						check(assignable(typeOf(argument, argumentPath), expected), INVALID_TYPE, argumentPath,
								"Argument must match the formal parameter type.");
						node(argument, owner, dependencies, argumentPath, depth + 1);
					}
				}
				case BKM_CALL -> {
					check(bkmProfile, UNSUPPORTED_CAPABILITY, path, "BKM calls require profile 0.10.");
					var call = expression.getBkmCall();
					var target = reference(call.getTarget(), owner, path + ".bkm_call.target");
					check(target.bkm() != null && visibleKnowledge.contains(Address.of(call.getTarget())),
							INVALID_DEPENDENCY, path, "BKM call requires a declared knowledge dependency.");
					check(nodeType.equals(target.type()), INVALID_TYPE, path, "Call must match the BKM result type.");
					var parameters = target.bkm().getParametersList();
					check(call.getArgumentsCount() == parameters.size(), INVALID_EXPRESSION, path,
							"Call requires exactly one argument for every formal parameter.");
					Set<Integer> positions = new HashSet<>();
					for (int i = 0; i < call.getArgumentsCount(); i++) {
						var argument = call.getArguments(i);
						int position = argument.getParameterPosition();
						String argumentPath = path + ".bkm_call.arguments[" + i + "]";
						check(position > 0 && position <= parameters.size() && positions.add(position)
								&& argument.hasExpression(), INVALID_EXPRESSION, argumentPath,
								"Expected a unique valid formal position and an argument expression.");
						var value = argument.getExpression();
						check(assignable(typeOf(value, argumentPath),
								typeOf(parameters.get(position - 1).getVariable(), target.path())), INVALID_TYPE,
								argumentPath, "Argument must match the formal parameter type.");
						node(value, owner, dependencies, argumentPath, depth + 1);
					}
				}
				case BUILTIN_CALL -> {
					check(builtinProfile, UNSUPPORTED_CAPABILITY, path, "Built-in calls require profile 0.9.");
					var call = expression.getBuiltinCall();
					var signature = DmnIrBuiltinSignature.forOperation(call.getOperation())
							.orElseThrow(() -> new DmnIrValidationException(INVALID_EXPRESSION, path,
									"Unsupported built-in operation."));
					check(call.getArgumentsCount() == signature.parameters().size(), INVALID_EXPRESSION, path,
							"Built-in argument count must match its signature.");
					check(nodeType.hasScalarType() && nodeType.getScalarType() == signature.result(), INVALID_TYPE,
							path, "Built-in result type must match its signature.");
					for (int i = 0; i < call.getArgumentsCount(); i++) {
						var argument = call.getArguments(i);
						String argumentPath = path + ".builtin_call.arguments[" + i + "]";
						var argumentType = typeOf(argument, argumentPath);
						check(argumentType.hasScalarType()
								&& argumentType.getScalarType() == signature.parameters().get(i), INVALID_TYPE,
								argumentPath, "Built-in argument type must match its signature.");
						node(argument, owner, dependencies, argumentPath, depth + 1);
					}
				}
				case FILTER -> {
					check(filtersProfile, UNSUPPORTED_CAPABILITY, path, "Filters require profile 0.8.");
					check(!filterPredicate, INVALID_EXPRESSION, path, "Filters inside predicates are unsupported.");
					var filter = expression.getFilter();
					check(filter.hasSource() && filter.hasPredicate(), INVALID_EXPRESSION, path,
							"A filter source and predicate are required.");
					check(nodeType.hasCollectionType()
							&& nodeType.equals(typeOf(filter.getSource(), path + ".filter.source")), INVALID_TYPE, path,
							"Filter source and result must have the same named collection type.");
					node(filter.getSource(), owner, dependencies, path + ".filter.source", depth + 1);
					var binding = filter.getItemBindingId();
					budget(path + ".filter.item_binding_id");
					check(!binding.isBlank() && bindingIds.add(binding), INVALID_IDENTITY, path,
							"A unique filter item binding ID is required.");
					check(filter.getPredicate().getType() == DmnIrScalarType.SCALAR_TYPE_BOOLEAN, INVALID_TYPE,
							path + ".filter.predicate", "A statically boolean predicate is required.");
					var itemType = collections.get(Address.of(nodeType.getCollectionType()));
					visibleLocals.put(binding, DmnIrTypeReference.newBuilder().setScalarType(itemType).build());
					filterPredicate = true;
					try {
						node(filter.getPredicate(), owner, dependencies, path + ".filter.predicate", depth + 1);
					} finally {
						filterPredicate = false;
						visibleLocals.remove(binding);
					}
				}
				case LIST -> {
					check(collectionsProfile, UNSUPPORTED_CAPABILITY, path, "List expressions require profile 0.7.");
					check(nodeType.hasCollectionType(), INVALID_TYPE, path + ".type",
							"A list needs a named collection type.");
					var elementType = collections.get(Address.of(nodeType.getCollectionType()));
					for (int i = 0; i < expression.getList().getElementsCount(); i++) {
						var element = expression.getList().getElements(i);
						String elementPath = path + ".list.elements[" + i + "]";
						check(!element.hasRecordType() && !element.hasCollectionType()
								&& assignable(element.getType(), elementType), INVALID_TYPE, elementPath,
								"List element must be compatible with the collection element type.");
						node(element, owner, dependencies, elementPath, depth + 1);
					}
				}
				case GLOBAL_REFERENCE -> {
					DmnIrReference value = expression.getGlobalReference();
					Element target = reference(value, owner, path + ".global_reference");
					check(dependencies.contains(Address.of(value)), INVALID_DEPENDENCY, path + ".global_reference",
							"Expression reference is not a declared dependency.");
					check(nodeType.hasCollectionType()
							? target.type().equals(nodeType)
							: assignable(target.type(), nodeType), INVALID_TYPE, path + ".type",
							"Reference type does not match the expression type.");
				}
				case UNARY -> {
					check(expressions, UNSUPPORTED_CAPABILITY, path, "Structured expressions require profile 0.1.");
					var value = expression.getUnary();
					check(value.hasOperand(), INVALID_EXPRESSION, path + ".unary", "Operand is required.");
					node(value.getOperand(), owner, dependencies, path + ".unary.operand", depth + 1);
					boolean valid = switch (value.getOperator()) {
						case UNARY_OPERATOR_POSITIVE, UNARY_OPERATOR_NEGATE ->
							value.getOperand().getType() == DmnIrScalarType.SCALAR_TYPE_NUMBER
									&& expression.getType() == DmnIrScalarType.SCALAR_TYPE_NUMBER;
						case UNARY_OPERATOR_NOT -> bool(value.getOperand().getType())
								&& expression.getType() == DmnIrScalarType.SCALAR_TYPE_BOOLEAN;
						default -> false;
					};
					check(valid, INVALID_TYPE, path + ".unary", "Unsupported unary operator signature.");
				}
				case BINARY -> {
					check(expressions, UNSUPPORTED_CAPABILITY, path, "Structured expressions require profile 0.1.");
					var value = expression.getBinary();
					check(value.hasLeft() && value.hasRight(), INVALID_EXPRESSION, path + ".binary",
							"Both operands are required.");
					node(value.getLeft(), owner, dependencies, path + ".binary.left", depth + 1);
					node(value.getRight(), owner, dependencies, path + ".binary.right", depth + 1);
					var left = value.getLeft().getType();
					var right = value.getRight().getType();
					boolean numberPair = left == DmnIrScalarType.SCALAR_TYPE_NUMBER && right == left;
					boolean booleanResult = expression.getType() == DmnIrScalarType.SCALAR_TYPE_BOOLEAN;
					boolean valid = switch (value.getOperator()) {
						case BINARY_OPERATOR_ADD, BINARY_OPERATOR_SUBTRACT, BINARY_OPERATOR_MULTIPLY,
								BINARY_OPERATOR_DIVIDE, BINARY_OPERATOR_POWER ->
							numberPair && expression.getType() == DmnIrScalarType.SCALAR_TYPE_NUMBER;
						case BINARY_OPERATOR_EQUAL, BINARY_OPERATOR_NOT_EQUAL ->
							booleanResult && left != DmnIrScalarType.SCALAR_TYPE_UNSPECIFIED
									&& right != DmnIrScalarType.SCALAR_TYPE_UNSPECIFIED;
						case BINARY_OPERATOR_LESS, BINARY_OPERATOR_LESS_EQUAL, BINARY_OPERATOR_GREATER,
								BINARY_OPERATOR_GREATER_EQUAL ->
							(numberPair || (left == DmnIrScalarType.SCALAR_TYPE_STRING
									|| datesProfile && left == DmnIrScalarType.SCALAR_TYPE_DATE) && right == left)
									&& booleanResult;
						case BINARY_OPERATOR_AND, BINARY_OPERATOR_OR -> bool(left) && bool(right) && booleanResult;
						default -> false;
					};
					check(valid, INVALID_TYPE, path + ".binary", "Unsupported binary operator signature.");
				}
				case CONDITIONAL -> {
					check(expressions, UNSUPPORTED_CAPABILITY, path, "Structured expressions require profile 0.1.");
					var value = expression.getConditional();
					check(value.hasCondition() && value.hasThenExpression() && value.hasElseExpression(),
							INVALID_EXPRESSION, path + ".conditional", "Condition and both branches are required.");
					node(value.getCondition(), owner, dependencies, path + ".conditional.condition", depth + 1);
					node(value.getThenExpression(), owner, dependencies, path + ".conditional.then_expression",
							depth + 1);
					node(value.getElseExpression(), owner, dependencies, path + ".conditional.else_expression",
							depth + 1);
					check(bool(value.getCondition().getType())
							&& assignable(typeOf(value.getThenExpression(), path), nodeType)
							&& assignable(typeOf(value.getElseExpression(), path), nodeType), INVALID_TYPE,
							path + ".conditional", "Conditional types are incompatible.");
				}
				case FIELD_ACCESS -> {
					check(recordsProfile, UNSUPPORTED_CAPABILITY, path, "Field access requires profile 0.2.");
					var access = expression.getFieldAccess();
					check(access.hasSource(), INVALID_EXPRESSION, path, "Field source is required.");
					node(access.getSource(), owner, dependencies, path + ".field_access.source", depth + 1);
					check(access.getSource().hasRecordType(), INVALID_TYPE, path,
							"Field source must be a named record.");
					var record = records.get(Address.of(access.getSource().getRecordType()));
					var field = record.getFieldsList().stream().filter(f -> f.getName().equals(access.getFieldName()))
							.findFirst();
					check(field.isPresent(), INVALID_REFERENCE, path + ".field_access.field_name",
							"Field does not exist.");
					check(field.orElseThrow().getType().equals(nodeType), INVALID_TYPE, path + ".type",
							"Field result type must match its declaration.");
				}
				default -> check(false, INVALID_EXPRESSION, path, "An expression node is required.");
			}
		}

		private void budget(String path) {
			check(++declarations <= limits.maxElements(), LIMIT_EXCEEDED, path, "Declaration budget exceeded.");
		}
		private DmnIrTypeReference typeOf(DmnIrVariable value, String path) {
			return typeOf(value.getType(), value.hasRecordType() ? value.getRecordType() : null,
					value.hasCollectionType() ? value.getCollectionType() : null, path);
		}
		private DmnIrTypeReference typeOf(DmnIrExpression value, String path) {
			return typeOf(value.getType(), value.hasRecordType() ? value.getRecordType() : null,
					value.hasCollectionType() ? value.getCollectionType() : null, path);
		}
		private DmnIrTypeReference typeOf(DmnIrScalarType scalar, DmnIrReference record, DmnIrReference collection,
				String path) {
			if (collection != null) {
				check(collectionsProfile, UNSUPPORTED_CAPABILITY, path, "Collection types require profile 0.7.");
				check(record == null && scalar == DmnIrScalarType.SCALAR_TYPE_UNSPECIFIED, INVALID_TYPE, path,
						"Scalar, record and collection types are mutually exclusive.");
				return DmnIrTypeReference.newBuilder().setCollectionType(collection).build();
			}
			if (record != null) {
				check(recordsProfile, UNSUPPORTED_CAPABILITY, path, "Record types require profile 0.2.");
				check(scalar == DmnIrScalarType.SCALAR_TYPE_UNSPECIFIED, INVALID_TYPE, path,
						"Scalar and record types are mutually exclusive.");
				return DmnIrTypeReference.newBuilder().setRecordType(record).build();
			}
			scalar(scalar, path);
			return DmnIrTypeReference.newBuilder().setScalarType(scalar).build();
		}
		private void validateType(DmnIrTypeReference type, String namespace, String path) {
			if (type.hasScalarType()) {
				scalar(type.getScalarType(), path);
				return;
			}
			if (type.hasCollectionType()) {
				collectionTarget(type.getCollectionType(), namespace, path);
				expandedTypes += 2;
				check(expandedTypes <= limits.maxMessages(), LIMIT_EXCEEDED, path, "Expanded type budget exceeded.");
				return;
			}
			check(type.hasRecordType() && recordsProfile, INVALID_TYPE, path,
					"An explicit supported type is required.");
			typeTarget(type.getRecordType(), namespace, path);
			expandedTypes += costs.get(Address.of(type.getRecordType()));
			check(expandedTypes <= limits.maxMessages(), LIMIT_EXCEEDED, path, "Expanded type budget exceeded.");
		}
		private void collectionTarget(DmnIrReference reference, String namespace, String path) {
			check(collectionsProfile, UNSUPPORTED_CAPABILITY, path, "Collection types require profile 0.7.");
			check(++references <= limits.maxReferences(), LIMIT_EXCEEDED, path, "Reference budget exceeded.");
			var address = Address.of(reference);
			check(collections.containsKey(address), INVALID_REFERENCE, path, "Collection type does not resolve.");
			check(namespace.equals(address.namespace()) || imports.get(namespace).contains(address.namespace()),
					INVALID_IMPORT, path, "Imported type requires a direct bundled import.");
		}

		private Address typeTarget(DmnIrReference reference, String namespace, String path) {
			check(++references <= limits.maxReferences(), LIMIT_EXCEEDED, path, "Reference budget exceeded.");
			var address = Address.of(reference);
			check(records.containsKey(address), INVALID_REFERENCE, path, "Record type does not resolve.");
			check(namespace.equals(address.namespace()) || imports.get(namespace).contains(address.namespace()),
					INVALID_IMPORT, path, "Imported type requires a direct bundled import.");
			return address;
		}
		private void fieldConstraint(DmnIrRecordField field, String path) {
			check(fieldConstraintsProfile, UNSUPPORTED_CAPABILITY, path, "Field constraints require profile 0.6.");
			var type = field.getType().getScalarType();
			check(field.getType().hasScalarType() && type != DmnIrScalarType.SCALAR_TYPE_NULL, INVALID_TYPE, path,
					"A field constraint requires a non-null scalar base type.");
			tests(field.getConstraint(), type, path, (endpoint, endpointPath) -> {
				check(endpoint.hasLiteral(), INVALID_EXPRESSION, endpointPath, "A constant endpoint is required.");
				check(!endpoint.hasRecordType() && !endpoint.hasCollectionType() && assignable(endpoint.getType(), type)
						&& (!endpoint.getLiteral().hasNullValue()
								|| endpoint.getType() == DmnIrScalarType.SCALAR_TYPE_NULL),
						INVALID_TYPE, endpointPath, "Constraint endpoint must be compatible with the field type.");
				literal(endpoint.getLiteral(), endpoint.getType(), endpointPath + ".literal");
			});
		}

		private void recordGraph() {
			Map<Address, Set<Address>> pending = new HashMap<>();
			Map<Address, List<Address>> parents = new HashMap<>();
			for (var entry : records.entrySet()) {
				Set<Address> dependencies = new HashSet<>();
				int fieldIndex = 0;
				for (var field : entry.getValue().getFieldsList()) {
					String fieldPath = recordPaths.get(entry.getKey()) + ".fields[" + fieldIndex++ + "]";
					var type = field.getType();
					if (type.hasScalarType())
						scalar(type.getScalarType(), fieldPath + ".type");
					else if (type.hasCollectionType())
						collectionTarget(type.getCollectionType(), entry.getKey().namespace(), fieldPath + ".type");
					else {
						check(type.hasRecordType(), INVALID_TYPE, fieldPath + ".type", "A field type is required.");
						dependencies
								.add(typeTarget(type.getRecordType(), entry.getKey().namespace(), fieldPath + ".type"));
					}
					if (field.hasConstraint())
						fieldConstraint(field, fieldPath + ".constraint");
				}
				pending.put(entry.getKey(), dependencies);
				for (var dependency : dependencies)
					parents.computeIfAbsent(dependency, k -> new ArrayList<>()).add(entry.getKey());
			}
			var ready = new ArrayDeque<Address>();
			pending.forEach((k, v) -> {
				if (v.isEmpty())
					ready.add(k);
			});
			Map<Address, Integer> heights = new HashMap<>();
			while (!ready.isEmpty()) {
				var address = ready.remove();
				long cost = 1;
				int height = 1;
				for (var field : records.get(address).getFieldsList()) {
					int childHeight = field.getType().hasCollectionType() ? 2 : 1;
					long childCost = childHeight;
					if (field.getType().hasRecordType()) {
						var child = Address.of(field.getType().getRecordType());
						childCost = costs.get(child);
						childHeight = heights.get(child);
					}
					cost += childCost;
					height = Math.max(height, childHeight + 1);
					check(cost <= limits.maxMessages() && height <= limits.maxDepth(), LIMIT_EXCEEDED,
							recordPaths.get(address), "Expanded record type budget exceeded.");
				}
				costs.put(address, cost);
				heights.put(address, height);
				expandedTypes += cost;
				check(expandedTypes <= limits.maxMessages(), LIMIT_EXCEEDED, recordPaths.get(address),
						"Expanded type budget exceeded.");
				for (var parent : parents.getOrDefault(address, List.of()))
					if (pending.get(parent).remove(address) && pending.get(parent).isEmpty())
						ready.add(parent);
			}
			if (costs.size() != records.size()) {
				var cyclic = records.keySet().stream().filter(k -> !costs.containsKey(k)).findFirst().orElseThrow();
				check(false, INVALID_TYPE, recordPaths.get(cyclic), "Recursive records are unsupported.");
			}
		}
		private static boolean assignable(DmnIrTypeReference actual, DmnIrTypeReference expected) {
			return actual.equals(expected)
					|| actual.hasScalarType() && actual.getScalarType() == DmnIrScalarType.SCALAR_TYPE_NULL;
		}

		private static boolean bool(DmnIrScalarType type) {
			return type == DmnIrScalarType.SCALAR_TYPE_BOOLEAN || type == DmnIrScalarType.SCALAR_TYPE_NULL;
		}

		private void literal(DmnIrLiteral literal, DmnIrScalarType type, String path) {
			DmnIrScalarType actual;
			switch (literal.getValueCase()) {
				case NULL_VALUE -> {
					check(literal.getNullValueValue() == 0, INVALID_EXPRESSION, path, "Unknown null literal value.");
					actual = DmnIrScalarType.SCALAR_TYPE_NULL;
				}
				case BOOLEAN_VALUE -> actual = DmnIrScalarType.SCALAR_TYPE_BOOLEAN;
				case STRING_VALUE -> actual = DmnIrScalarType.SCALAR_TYPE_STRING;
				case DATE_VALUE -> {
					check(datesProfile, UNSUPPORTED_CAPABILITY, path, "Date values require profile 0.4.");
					var date = literal.getDateValue();
					check(date.hasYear() && date.hasMonth() && date.hasDay(), INVALID_EXPRESSION, path + ".date_value",
							"Explicit civil components are required.");
					check(date.getYear() != 0, INVALID_EXPRESSION, path + ".date_value.year",
							"Year zero is outside the DMN date profile.");
					try {
						java.time.LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
					} catch (java.time.DateTimeException invalid) {
						throw new DmnIrValidationException(INVALID_EXPRESSION, path + ".date_value",
								"Invalid proleptic Gregorian date.");
					}
					actual = DmnIrScalarType.SCALAR_TYPE_DATE;
				}
				case NUMBER_VALUE -> {
					DmnIrDecimal number = literal.getNumberValue();
					String coefficient = number.getUnscaledValue();
					int digits = coefficient.length() - (coefficient.startsWith("-") ? 1 : 0);
					check(digits <= limits.maxDecimalDigits(), LIMIT_EXCEEDED, path + ".number_value",
							"Decimal coefficient budget exceeded.");
					check(number.hasScale() && coefficient.matches("0|-?[1-9][0-9]*"), INVALID_NUMBER,
							path + ".number_value", "Decimal coefficient or explicit scale is invalid.");
					check(Math.abs((long) number.getScale()) <= limits.maxAbsScale(), LIMIT_EXCEEDED,
							path + ".number_value.scale", "Decimal scale budget exceeded.");
					actual = DmnIrScalarType.SCALAR_TYPE_NUMBER;
				}
				default -> throw new DmnIrValidationException(INVALID_EXPRESSION, path, "A literal value is required.");
			}
			check(assignable(actual, type), INVALID_TYPE, path, "Literal type is incompatible with the expression.");
		}

		private static boolean assignable(DmnIrScalarType actual, DmnIrScalarType expected) {
			return actual == expected || actual == DmnIrScalarType.SCALAR_TYPE_NULL;
		}

		private void scalar(DmnIrScalarType type, String path) {
			boolean supported = switch (type) {
				case SCALAR_TYPE_NULL, SCALAR_TYPE_BOOLEAN, SCALAR_TYPE_NUMBER, SCALAR_TYPE_STRING -> true;
				case SCALAR_TYPE_DATE -> datesProfile;
				default -> false;
			};
			check(supported, INVALID_TYPE, path, "An explicitly supported scalar type is required.");
		}

		private void order(DmnIrArtifact artifact) {
			Map<Address, Integer> positions = new HashMap<>();
			for (int i = 0; i < artifact.getEvaluationOrderCount(); i++) {
				DmnIrReference value = artifact.getEvaluationOrder(i);
				String path = "artifact.evaluation_order[" + i + "]";
				Element target = reference(value, null, path);
				check(target.decision() != null && positions.putIfAbsent(Address.of(value), i) == null,
						INVALID_EVALUATION_ORDER, path, "Schedule must contain each decision exactly once.");
			}
			for (Map.Entry<Address, Element> entry : elements.entrySet()) {
				Element element = entry.getValue();
				if (element.decision() == null) {
					continue;
				}
				Integer position = positions.get(entry.getKey());
				check(position != null, INVALID_EVALUATION_ORDER, "artifact.evaluation_order",
						"Schedule omits a decision.");
				for (DmnIrReference dependency : element.decision().getDependenciesList()) {
					Element target = elements.get(Address.of(dependency));
					if (target.decision() != null) {
						check(positions.containsKey(Address.of(dependency))
								&& positions.get(Address.of(dependency)) < position, INVALID_EVALUATION_ORDER,
								element.path() + ".dependencies", "Decision dependency must precede its consumer.");
					}
				}
			}
		}
	}
}

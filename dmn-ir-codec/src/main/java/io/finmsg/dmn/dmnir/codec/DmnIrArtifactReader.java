package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;

import com.google.protobuf.CodedInputStream;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

/**
 * Strict, bounded reader for experimental portable profiles 0.0 through 0.3.
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

	private record Element(DmnIrTypeReference type, DmnIrDecision decision, String path, String namespace) {
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
		private final Map<String, Set<String>> modelIds = new HashMap<>();
		private final Set<String> bindingIds = new HashSet<>();
		private Map<String, DmnIrTypeReference> visibleLocals = new HashMap<>();
		private int declarations;
		private long expandedTypes;
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
			localsProfile = artifact.getSchemaVersion().getMinor() == 3;
			recordsProfile = artifact.getSchemaVersion().getMinor() == 2 || localsProfile;
			expressions = artifact.getSchemaVersion().getMinor() == 1 || recordsProfile;
			check(artifact.getSchemaVersion().getMajor() == 0
					&& (artifact.getSchemaVersion().getMinor() == 0 || expressions)
					&& artifact.getSemanticsVersion()
							.equals(localsProfile
									? LOCAL_SEMANTICS_VERSION
									: recordsProfile
											? RECORD_SEMANTICS_VERSION
											: expressions ? EXPRESSION_SEMANTICS_VERSION : SEMANTICS_VERSION),
					UNSUPPORTED_VERSION, "artifact.schema_version", "Unsupported wire or semantic version.");
			Set<String> capabilities = new HashSet<>();
			for (int i = 0; i < artifact.getRequiredCapabilitiesCount(); i++) {
				String capability = artifact.getRequiredCapabilities(i);
				String path = "artifact.required_capabilities[" + i + "]";
				check(capability.equals(SCALAR_CAPABILITY) || expressions && capability.equals(EXPRESSION_CAPABILITY)
						|| recordsProfile && capability.equals(RECORD_CAPABILITY)
						|| localsProfile && capability.equals(LOCAL_CAPABILITY), UNSUPPORTED_CAPABILITY, path,
						"Required capability is not supported.");
				check(capabilities.add(capability), INVALID_HEADER, path, "Duplicate required capability.");
			}
			check(capabilities.contains(SCALAR_CAPABILITY), INVALID_HEADER, "artifact.required_capabilities",
					"The scalar capability must be declared.");
			check(artifact.getRequiredCapabilitiesList()
					.equals(localsProfile
							? List.of(SCALAR_CAPABILITY, EXPRESSION_CAPABILITY, RECORD_CAPABILITY, LOCAL_CAPABILITY)
							: recordsProfile
									? List.of(SCALAR_CAPABILITY, EXPRESSION_CAPABILITY, RECORD_CAPABILITY)
									: expressions
											? List.of(SCALAR_CAPABILITY, EXPRESSION_CAPABILITY)
											: List.of(SCALAR_CAPABILITY)),
					INVALID_HEADER, "artifact.required_capabilities",
					"Exact ordered profile capabilities are required.");
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
					declaration(model, input.getIdentity(), input.getVariable(), null, ids, names, variableNames,
							path + ".inputs[" + j + "]");
				}
				for (int j = 0; j < model.getDecisionsCount(); j++) {
					DmnIrDecision decision = model.getDecisions(j);
					declaration(model, decision.getIdentity(), decision.getVariable(), decision, ids, names,
							variableNames, path + ".decisions[" + j + "]");
				}
			}
			check(models.containsKey(artifact.getRootModelNamespace()), INVALID_HEADER, "artifact.root_model_namespace",
					"Root model is not in the artifact.");
			validateImports(artifact);
			recordGraph();
			for (Element element : elements.values())
				validateType(element.type(), element.namespace(),
						element.path() + (element.type().hasRecordType() ? ".variable.record_type" : ".variable.type"));
			for (Element element : elements.values()) {
				if (element.decision() != null) {
					expression(element);
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
				DmnIrDecision decision, Set<String> ids, Set<String> names, Set<String> variableNames, String path) {
			budget(path);
			identity(identity, ids, names, path + ".identity");
			check(!variable.getName().isBlank() && variableNames.add(variable.getName()), INVALID_IDENTITY,
					path + ".variable.name", "A unique variable name is required.");
			if (variable.hasId()) {
				check(!variable.getId().isBlank() && ids.add(variable.getId()), INVALID_IDENTITY, path + ".variable.id",
						"A variable ID must be non-empty and unique.");
			}
			var type = typeOf(variable.getType(), variable.hasRecordType(), variable.getRecordType(),
					path + ".variable.type");
			elements.put(new Address(model.getNamespace(), identity.getId()),
					new Element(type, decision, path, model.getNamespace()));
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
			String path = owner.path();
			Set<Address> dependencies = new HashSet<>();
			for (int i = 0; i < decision.getDependenciesCount(); i++) {
				DmnIrReference value = decision.getDependencies(i);
				String dependencyPath = path + ".dependencies[" + i + "]";
				Element target = reference(value, owner, dependencyPath);
				check(target != owner && dependencies.add(Address.of(value)), INVALID_DEPENDENCY, dependencyPath,
						"Dependencies must be unique and must not reference their owner.");
			}
			DmnIrExpression expression = decision.getExpression();
			var resultType = typeOf(expression.getType(), expression.hasRecordType(), expression.getRecordType(),
					path + ".expression.type");
			check(assignable(resultType, owner.type()), INVALID_TYPE, path + ".expression.type",
					"Expression type is incompatible with its declared result.");
			node(expression, owner, dependencies, path + ".expression", 1);
		}

		private void node(DmnIrExpression expression, Element owner, Set<Address> dependencies, String path,
				int depth) {
			check(depth <= limits.maxDepth(), LIMIT_EXCEEDED, path, "Expression depth budget exceeded.");
			String nodeTypePath = path + (expression.hasRecordType() ? ".record_type" : ".type");
			var nodeType = typeOf(expression.getType(), expression.hasRecordType(), expression.getRecordType(),
					nodeTypePath);
			validateType(nodeType, owner.namespace(), nodeTypePath);
			switch (expression.getNodeCase()) {
				case LOCAL_REFERENCE -> {
					check(localsProfile, UNSUPPORTED_CAPABILITY, path, "Local references require profile 0.3.");
					check(++references <= limits.maxReferences(), LIMIT_EXCEEDED, path, "Reference budget exceeded.");
					var target = visibleLocals.get(expression.getLocalReference().getBindingId());
					check(target != null, INVALID_REFERENCE, path, "Local binding is not visible here.");
					check(target.equals(nodeType), INVALID_TYPE, path,
							"Local reference type must match its declaration.");
				}
				case CONTEXT -> {
					check(localsProfile, UNSUPPORTED_CAPABILITY, path, "Contexts require profile 0.3.");
					var context = expression.getContext();
					check(context.hasResult(), INVALID_EXPRESSION, path, "A final context result is required.");
					var outer = visibleLocals;
					visibleLocals = new HashMap<>(outer);
					Set<String> names = new HashSet<>();
					try {
						for (int i = 0; i < context.getBindingsCount(); i++) {
							var binding = context.getBindings(i);
							String bp = path + ".context.bindings[" + i + "]";
							budget(bp);
							check(!binding.getBindingId().isBlank() && bindingIds.add(binding.getBindingId()),
									INVALID_IDENTITY, bp, "Unique decision-local binding IDs are required.");
							var variable = binding.getVariable();
							check(!variable.getName().isBlank() && names.add(variable.getName()), INVALID_IDENTITY, bp,
									"Unique non-empty context variable names are required.");
							if (variable.hasId())
								check(!variable.getId().isBlank()
										&& modelIds.get(owner.namespace()).add(variable.getId()), INVALID_IDENTITY,
										bp + ".variable.id", "Source variable ID must be unique.");
							var bt = typeOf(variable.getType(), variable.hasRecordType(), variable.getRecordType(),
									bp + ".variable.type");
							validateType(bt, owner.namespace(), bp + ".variable.type");
							check(binding.hasValue(), INVALID_EXPRESSION, bp, "Binding value is required.");
							node(binding.getValue(), owner, dependencies, bp + ".value", depth + 1);
							var value = binding.getValue();
							check(assignable(typeOf(value.getType(), value.hasRecordType(), value.getRecordType(), bp),
									bt), INVALID_TYPE, bp, "Binding value type is incompatible.");
							visibleLocals.put(binding.getBindingId(), bt);
						}
						node(context.getResult(), owner, dependencies, path + ".context.result", depth + 1);
						var result = context.getResult();
						check(assignable(typeOf(result.getType(), result.hasRecordType(), result.getRecordType(), path),
								nodeType), INVALID_TYPE, path, "Context result type is incompatible.");
					} finally {
						visibleLocals = outer;
					}
				}
				case LITERAL -> literal(expression.getLiteral(), expression.getType(), path + ".literal");
				case GLOBAL_REFERENCE -> {
					DmnIrReference value = expression.getGlobalReference();
					Element target = reference(value, owner, path + ".global_reference");
					check(dependencies.contains(Address.of(value)), INVALID_DEPENDENCY, path + ".global_reference",
							"Expression reference is not a declared dependency.");
					check(assignable(target.type(), nodeType), INVALID_TYPE, path + ".type",
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
							booleanResult && !value.getLeft().hasRecordType() && !value.getRight().hasRecordType();
						case BINARY_OPERATOR_LESS, BINARY_OPERATOR_LESS_EQUAL, BINARY_OPERATOR_GREATER,
								BINARY_OPERATOR_GREATER_EQUAL ->
							(numberPair || left == DmnIrScalarType.SCALAR_TYPE_STRING && right == left)
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
							&& assignable(typeOf(value.getThenExpression().getType(),
									value.getThenExpression().hasRecordType(),
									value.getThenExpression().getRecordType(), path), nodeType)
							&& assignable(typeOf(value.getElseExpression().getType(),
									value.getElseExpression().hasRecordType(),
									value.getElseExpression().getRecordType(), path), nodeType),
							INVALID_TYPE, path + ".conditional", "Conditional types are incompatible.");
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
		private DmnIrTypeReference typeOf(DmnIrScalarType scalar, boolean hasRecord, DmnIrReference record,
				String path) {
			if (hasRecord) {
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
			check(type.hasRecordType() && recordsProfile, INVALID_TYPE, path,
					"An explicit supported type is required.");
			typeTarget(type.getRecordType(), namespace, path);
			expandedTypes += costs.get(Address.of(type.getRecordType()));
			check(expandedTypes <= limits.maxMessages(), LIMIT_EXCEEDED, path, "Expanded type budget exceeded.");
		}
		private Address typeTarget(DmnIrReference reference, String namespace, String path) {
			check(++references <= limits.maxReferences(), LIMIT_EXCEEDED, path, "Reference budget exceeded.");
			var address = Address.of(reference);
			check(records.containsKey(address), INVALID_REFERENCE, path, "Record type does not resolve.");
			check(namespace.equals(address.namespace()) || imports.get(namespace).contains(address.namespace()),
					INVALID_IMPORT, path, "Imported type requires a direct bundled import.");
			return address;
		}
		private void recordGraph() {
			Map<Address, Set<Address>> pending = new HashMap<>();
			Map<Address, List<Address>> parents = new HashMap<>();
			for (var entry : records.entrySet()) {
				Set<Address> dependencies = new HashSet<>();
				int fieldIndex = 0;
				for (var field : entry.getValue().getFieldsList()) {
					String fieldPath = recordPaths.get(entry.getKey()) + ".fields[" + fieldIndex++ + "].type";
					var type = field.getType();
					if (type.hasScalarType())
						scalar(type.getScalarType(), fieldPath);
					else {
						check(type.hasRecordType(), INVALID_TYPE, fieldPath, "A field type is required.");
						dependencies.add(typeTarget(type.getRecordType(), entry.getKey().namespace(), fieldPath));
					}
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
					long childCost = 1;
					int childHeight = 1;
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

		private static void scalar(DmnIrScalarType type, String path) {
			boolean supported = switch (type) {
				case SCALAR_TYPE_NULL, SCALAR_TYPE_BOOLEAN, SCALAR_TYPE_NUMBER, SCALAR_TYPE_STRING -> true;
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

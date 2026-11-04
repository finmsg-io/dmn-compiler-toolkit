package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;

import com.google.protobuf.CodedInputStream;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict, bounded reader for schema 0.0 and finmsg-dmnir-scalar/0 only. */
public final class DmnIrArtifactReader {
	public static final String SEMANTICS_VERSION = "finmsg-dmnir-scalar/0";
	public static final String SCALAR_CAPABILITY = "dmnir.scalar";
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

	private record Element(DmnIrScalarType type, DmnIrDecision decision, String path, String namespace) {
	}

	private static final class Validation {
		private final DmnIrLimits limits;
		private final Map<String, DmnIrModel> models = new LinkedHashMap<>();
		private final Map<Address, Element> elements = new LinkedHashMap<>();
		private final Map<String, Set<String>> imports = new HashMap<>();
		private int references;

		Validation(DmnIrLimits limits) {
			this.limits = limits;
		}

		void validate(DmnIrArtifact artifact) {
			check(artifact.hasSchemaVersion() && artifact.getSchemaVersion().hasMajor()
					&& artifact.getSchemaVersion().hasMinor(), INVALID_HEADER, "artifact.schema_version",
					"Explicit major and minor versions are required.");
			check(artifact.getSchemaVersion().getMajor() == 0 && artifact.getSchemaVersion().getMinor() == 0
					&& artifact.getSemanticsVersion().equals(SEMANTICS_VERSION), UNSUPPORTED_VERSION,
					"artifact.schema_version", "Unsupported wire or semantic version.");
			Set<String> capabilities = new HashSet<>();
			for (int i = 0; i < artifact.getRequiredCapabilitiesCount(); i++) {
				String capability = artifact.getRequiredCapabilities(i);
				String path = "artifact.required_capabilities[" + i + "]";
				check(capability.equals(SCALAR_CAPABILITY), UNSUPPORTED_CAPABILITY, path,
						"Required capability is not supported.");
				check(capabilities.add(capability), INVALID_HEADER, path, "Duplicate required capability.");
			}
			check(capabilities.contains(SCALAR_CAPABILITY), INVALID_HEADER, "artifact.required_capabilities",
					"The scalar capability must be declared.");
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
				Set<String> names = new HashSet<>();
				Set<String> variableNames = new HashSet<>();
				identity(model.getIdentity(), ids, null, path + ".identity");
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
			check(elements.size() < limits.maxElements(), LIMIT_EXCEEDED, path, "Declaration budget exceeded.");
			identity(identity, ids, names, path + ".identity");
			check(!variable.getName().isBlank() && variableNames.add(variable.getName()), INVALID_IDENTITY,
					path + ".variable.name", "A unique variable name is required.");
			if (variable.hasId()) {
				check(!variable.getId().isBlank() && ids.add(variable.getId()), INVALID_IDENTITY, path + ".variable.id",
						"A variable ID must be non-empty and unique.");
			}
			scalar(variable.getType(), path + ".variable.type");
			elements.put(new Address(model.getNamespace(), identity.getId()),
					new Element(variable.getType(), decision, path, model.getNamespace()));
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
			scalar(expression.getType(), path + ".expression.type");
			check(assignable(expression.getType(), owner.type()), INVALID_TYPE, path + ".expression.type",
					"Expression type is incompatible with its declared result.");
			switch (expression.getNodeCase()) {
				case LITERAL -> literal(expression.getLiteral(), expression.getType(), path + ".expression.literal");
				case GLOBAL_REFERENCE -> {
					DmnIrReference value = expression.getGlobalReference();
					Element target = reference(value, owner, path + ".expression.global_reference");
					check(dependencies.contains(Address.of(value)), INVALID_DEPENDENCY,
							path + ".expression.global_reference",
							"Expression reference is not a declared dependency.");
					check(assignable(target.type(), expression.getType()), INVALID_TYPE, path + ".expression.type",
							"Reference type does not match the expression type.");
				}
				default -> check(false, INVALID_EXPRESSION, path + ".expression", "An expression node is required.");
			}
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

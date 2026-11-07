package io.finmsg.dmn.compiler.dmnir;

import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader.VerifiedArtifact;
import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.frontend.xml.dmn.*;
import io.finmsg.dmn.model.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Artifact-only normalized DMN 1.6 synthesis for an explicitly admitted scalar
 * subset.
 */
public final class DmnIrScalarDmnExporter {
	private static final Pattern ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
	private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*( [A-Za-z_][A-Za-z0-9_]*)*");
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
		if (version != DmnVersion.DMN_1_6)
			throw new DmnIrExportException(DmnIrExportException.Category.UNSUPPORTED_VERSION, null);
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
		if (artifact.getSchemaVersion().getMajor() != 0
				|| !(scalarProfile || expressionProfile || recordProfile || localProfile || dateProfile))
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
			checkIdentity(model.getIdentity(), ids, null);
			for (var record : model.getRecordDefinitionsList()) {
				checkIdentity(record.getIdentity(), ids, null);
				if (!ID.matcher(record.getIdentity().getName()).matches())
					throw invalid(null);
				for (var field : record.getFieldsList()) {
					name(field.getName(), null);
					if (field.hasId())
						id(field.getId(), ids, null);
				}
			}
			Set<String> names = new HashSet<>();
			for (var input : model.getInputsList())
				admit(model, input.getIdentity(), input.getVariable(), true, ids, names, declarations);
			for (var decision : model.getDecisionsList())
				admit(model, decision.getIdentity(), decision.getVariable(), false, ids, names, declarations);
			for (var imported : model.getImportsList()) {
				name(imported.getName(), null);
				if (!names.add(imported.getName()))
					throw invalid(null);
			}
		}
		Map<String, String> documents = new LinkedHashMap<>();
		for (var model : models) {
			var definitions = Definitions.newBuilder().setNode(node(model.getIdentity()))
					.setNamespace(model.getNamespace()).setModelNamespaceUri(DmnNamespaces.DMN_1_6);
			if (recordProfile || localProfile || dateProfile) {
				int prefix = 0;
				for (var namespace : models.stream().map(DmnIrModel::getNamespace).toList())
					definitions.addNamespaces(Namespace.newBuilder().setPrefix("t" + prefix++).setUri(namespace));
				for (var record : model.getRecordDefinitionsList().stream()
						.sorted(Comparator.comparing(r -> r.getIdentity().getId())).toList()) {
					var item = ItemDefinition.newBuilder().setNode(node(record.getIdentity()));
					for (var field : record.getFieldsList()) {
						var fieldNode = Node.newBuilder().setName(field.getName());
						if (field.hasId())
							fieldNode.setId(field.getId());
						item.addComponents(ItemComponent.newBuilder().setNode(fieldNode)
								.setType(dmnType(field.getType().getScalarType(), field.getType().hasRecordType(),
										field.getType().getRecordType(), types)));
					}
					definitions.addItemDefinitions(item);
				}
			}
			model.getImportsList().stream()
					.sorted(Comparator.comparing(DmnIrImport::getModelNamespace).thenComparing(DmnIrImport::getName))
					.forEach(i -> definitions.addImports(io.finmsg.dmn.model.Import.newBuilder().setName(i.getName())
							.setNamespace(i.getModelNamespace()).setLocationUri(filenames.get(i.getModelNamespace()))
							.setImportType(DmnNamespaces.DMN_1_6)));
			model.getInputsList().stream().sorted(Comparator.comparing(i -> i.getIdentity().getId()))
					.forEach(i -> definitions.addDrgElements(DrgElement.newBuilder().setInputData(InputData.newBuilder()
							.setNode(node(i.getIdentity())).setVariable(variable(i.getVariable(), types)))));
			for (var decision : model.getDecisionsList().stream()
					.sorted(Comparator.comparing(d -> d.getIdentity().getId())).toList()) {
				var address = ref(model.getNamespace(), decision.getIdentity().getId());
				var output = Decision.newBuilder().setNode(node(decision.getIdentity()))
						.setVariable(variable(decision.getVariable(), types));
				// The source analyzer also exposes imported names unqualified: reject
				// collisions among required names.
				Set<String> requiredNames = new HashSet<>();
				for (var dependency : decision.getDependenciesList().stream().sorted(Comparator
						.comparing(DmnIrReference::getModelNamespace).thenComparing(DmnIrReference::getElementId))
						.toList()) {
					var declaration = declarations.get(dependency);
					if (!requiredNames.add(declaration.name()))
						throw invalid(address);
					var reference = ElementReference.newBuilder()
							.setHref(dependency.getModelNamespace().equals(model.getNamespace())
									? "#" + dependency.getElementId()
									: dependency.getModelNamespace() + "#" + dependency.getElementId());
					var requirement = InformationRequirement.newBuilder();
					if (declaration.input())
						requirement.setInput(reference);
					else
						requirement.setDecision(reference);
					output.addInformationRequirements(requirement);
				}
				var exported = expressionText(decision.getExpression(), model, declarations, address, types,
						new LinkedHashMap<>());
				var logic = DecisionLogic.newBuilder();
				if (exported.hasBoxed())
					logic.setBoxedExpression(BoxedExpression.newBuilder().setText(exported.getBoxed()));
				else
					logic.setLiteralExpression(Feel.newBuilder().setText(exported.getFeel()));
				output.setLogic(logic);
				definitions.addDrgElements(DrgElement.newBuilder().setDecision(output));
			}
			documents.put(filenames.get(model.getNamespace()),
					new String(new DmnWriter().write(definitions.build()), StandardCharsets.UTF_8));
		}
		return new ExportedDmnBundle(filenames.get(artifact.getRootModelNamespace()), documents);
	}

	private record Declaration(String name, boolean input) {
	}
	private static void admit(DmnIrModel model, DmnIrElementIdentity identity, DmnIrVariable variable, boolean input,
			Set<String> ids, Set<String> names, Map<DmnIrReference, Declaration> declarations) {
		var address = ref(model.getNamespace(), identity.getId());
		checkIdentity(identity, ids, address);
		name(variable.getName(), address);
		if (!identity.getName().equals(variable.getName()) || !names.add(variable.getName()))
			throw invalid(address);
		if (variable.hasId())
			id(variable.getId(), ids, address);
		declarations.put(address, new Declaration(variable.getName(), input));
	}
	private static void checkIdentity(DmnIrElementIdentity identity, Set<String> ids, DmnIrReference address) {
		id(identity.getId(), ids, address);
		xml(identity.getName(), address);
		// XML attribute normalization would otherwise change retained names.
		if (identity.getName().chars().anyMatch(c -> c == 9 || c == 10 || c == 13))
			throw invalid(address);
	}
	private static void id(String value, Set<String> ids, DmnIrReference address) {
		if (!ID.matcher(value).matches() || !ids.add(value))
			throw invalid(address);
	}
	private static void name(String value, DmnIrReference address) {
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
	private static InformationItem variable(DmnIrVariable value, DmnIrRecordTypes types) {
		var node = Node.newBuilder().setName(value.getName());
		if (value.hasId())
			node.setId(value.getId());
		return InformationItem.newBuilder().setNode(node)
				.setType(dmnType(value.getType(), value.hasRecordType(), value.getRecordType(), types)).build();
	}
	private static TypeReference dmnType(DmnIrScalarType scalar, boolean record, DmnIrReference reference,
			DmnIrRecordTypes types) {
		if (record)
			return TypeReference.newBuilder()
					.setNamed(NamedTypeReference.newBuilder().setNamespace(reference.getModelNamespace())
							.setName(types.definition(reference).getIdentity().getName()))
					.build();
		var builtin = switch (scalar) {
			case SCALAR_TYPE_NULL -> BuiltinType.BUILTIN_TYPE_NULL;
			case SCALAR_TYPE_BOOLEAN -> BuiltinType.BUILTIN_TYPE_BOOLEAN;
			case SCALAR_TYPE_NUMBER -> BuiltinType.BUILTIN_TYPE_NUMBER;
			case SCALAR_TYPE_STRING -> BuiltinType.BUILTIN_TYPE_STRING;
			case SCALAR_TYPE_DATE -> BuiltinType.BUILTIN_TYPE_DATE;
			default -> throw invalid(null);
		};
		return TypeReference.newBuilder().setBuiltin(builtin).build();
	}

	private static ExpressionText expressionText(DmnIrExpression expression, DmnIrModel model,
			Map<DmnIrReference, Declaration> declarations, DmnIrReference address, DmnIrRecordTypes types,
			Map<String, String> locals) {
		if (!expression.hasContext())
			return ExpressionText.newBuilder()
					.setFeel(
							FeelText.newBuilder().setText(expression(expression, model, declarations, address, locals)))
					.build();
		var child = new LinkedHashMap<>(locals);
		var context = ContextText.newBuilder();
		for (var binding : expression.getContext().getBindingsList()) {
			var variable = binding.getVariable();
			name(variable.getName(), address);
			if (variable.hasId() && !ID.matcher(variable.getId()).matches())
				throw invalid(address);
			var value = expressionText(binding.getValue(), model, declarations, address, types, child);
			context.addEntries(
					ContextEntryText.newBuilder().setVariable(variable(variable, types)).setExpression(value));
			child.put(binding.getBindingId(), variable.getName());
		}
		context.addEntries(ContextEntryText.newBuilder().setExpression(
				expressionText(expression.getContext().getResult(), model, declarations, address, types, child)));
		return ExpressionText.newBuilder().setBoxed(BoxedExpressionText.newBuilder().setContext(context)).build();
	}
	private static String expression(DmnIrExpression expression, DmnIrModel model,
			Map<DmnIrReference, Declaration> declarations, DmnIrReference address, Map<String, String> locals) {
		if (expression.hasContext())
			throw invalid(address); // Boxed contexts require an XML expression position.
		if (expression.hasLocalReference()) {
			String id = expression.getLocalReference().getBindingId();
			String localName = locals.get(id);
			String visible = null;
			for (var entry : locals.entrySet())
				if (entry.getValue().equals(localName))
					visible = entry.getKey();
			if (localName == null || !id.equals(visible))
				throw invalid(address);
			return localName;
		}
		if (expression.hasFieldAccess()) {
			name(expression.getFieldAccess().getFieldName(), address);
			return "(" + expression(expression.getFieldAccess().getSource(), model, declarations, address, locals)
					+ ")." + expression.getFieldAccess().getFieldName();
		}
		if (expression.hasGlobalReference()) {
			var reference = expression.getGlobalReference();
			String variable = declarations.get(reference).name();
			if (reference.getModelNamespace().equals(model.getNamespace()) && locals.containsValue(variable))
				throw invalid(address);
			if (reference.getModelNamespace().equals(model.getNamespace()))
				return variable;
			var imports = model.getImportsList().stream()
					.filter(i -> i.getModelNamespace().equals(reference.getModelNamespace())).toList();
			if (imports.size() != 1)
				throw invalid(address);
			if (locals.containsValue(imports.getFirst().getName()))
				throw invalid(address);
			return imports.getFirst().getName() + "." + variable;
		}
		if (expression.hasUnary()) {
			if (expression.getUnary().getOperator() == DmnIrUnaryOperator.UNARY_OPERATOR_POSITIVE)
				return "(" + expression(expression.getUnary().getOperand(), model, declarations, address, locals) + ")";
			String token = switch (expression.getUnary().getOperator()) {
				case UNARY_OPERATOR_POSITIVE -> "+";
				case UNARY_OPERATOR_NEGATE -> "-";
				case UNARY_OPERATOR_NOT -> "not ";
				default -> throw invalid(address);
			};
			return "(" + token + "("
					+ expression(expression.getUnary().getOperand(), model, declarations, address, locals) + "))";
		}
		if (expression.hasBinary()) {
			String token = switch (expression.getBinary().getOperator()) {
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
			return "((" + expression(expression.getBinary().getLeft(), model, declarations, address, locals) + ") "
					+ token + " (" + expression(expression.getBinary().getRight(), model, declarations, address, locals)
					+ "))";
		}
		if (expression.hasConditional()) {
			var conditional = expression.getConditional();
			return "(if (" + expression(conditional.getCondition(), model, declarations, address, locals) + ") then ("
					+ expression(conditional.getThenExpression(), model, declarations, address, locals) + ") else ("
					+ expression(conditional.getElseExpression(), model, declarations, address, locals) + "))";
		}
		var value = expression.getLiteral();
		return switch (value.getValueCase()) {
			case NULL_VALUE -> "null";
			case BOOLEAN_VALUE -> Boolean.toString(value.getBooleanValue());
			case NUMBER_VALUE -> new BigDecimal(new BigInteger(value.getNumberValue().getUnscaledValue()),
					value.getNumberValue().getScale()).toPlainString();
			case DATE_VALUE -> "date(\"" + DmnIrDates.feel(value.getDateValue()) + "\")";
			case STRING_VALUE -> {
				xml(value.getStringValue(), address);
				yield "\"" + value.getStringValue().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
						.replace("\r", "\\r").replace("\t", "\\t") + "\"";
			}
			default -> throw invalid(address);
		};
	}
	private static DmnIrReference ref(String namespace, String id) {
		return DmnIrReference.newBuilder().setModelNamespace(namespace).setElementId(id).build();
	}
	private static DmnIrExportException invalid(DmnIrReference reference) {
		return new DmnIrExportException(DmnIrExportException.Category.UNREPRESENTABLE_ARTIFACT,
				reference == null
						? null
						: new DmnIrRuntimeAdapter.ElementId(reference.getModelNamespace(), reference.getElementId()));
	}
}

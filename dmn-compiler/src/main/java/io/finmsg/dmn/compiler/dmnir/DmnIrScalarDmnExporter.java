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
		if (artifact.getSchemaVersion().getMajor() != 0 || artifact.getSchemaVersion().getMinor() != 0
				|| !artifact.getSemanticsVersion().equals("finmsg-dmnir-scalar/0")
				|| !artifact.getRequiredCapabilitiesList().equals(List.of("dmnir.scalar")))
			throw invalid(null);
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
			model.getImportsList().stream()
					.sorted(Comparator.comparing(DmnIrImport::getModelNamespace).thenComparing(DmnIrImport::getName))
					.forEach(i -> definitions.addImports(io.finmsg.dmn.model.Import.newBuilder().setName(i.getName())
							.setNamespace(i.getModelNamespace()).setLocationUri(filenames.get(i.getModelNamespace()))
							.setImportType(DmnNamespaces.DMN_1_6)));
			model.getInputsList().stream().sorted(Comparator.comparing(i -> i.getIdentity().getId()))
					.forEach(i -> definitions.addDrgElements(DrgElement.newBuilder().setInputData(InputData.newBuilder()
							.setNode(node(i.getIdentity())).setVariable(variable(i.getVariable())))));
			for (var decision : model.getDecisionsList().stream()
					.sorted(Comparator.comparing(d -> d.getIdentity().getId())).toList()) {
				var address = ref(model.getNamespace(), decision.getIdentity().getId());
				var output = Decision.newBuilder().setNode(node(decision.getIdentity()))
						.setVariable(variable(decision.getVariable()));
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
				String text = expression(decision.getExpression(), model, declarations, address);
				output.setLogic(DecisionLogic.newBuilder()
						.setLiteralExpression(Feel.newBuilder().setText(FeelText.newBuilder().setText(text))));
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
	private static InformationItem variable(DmnIrVariable value) {
		var node = Node.newBuilder().setName(value.getName());
		if (value.hasId())
			node.setId(value.getId());
		var builtin = switch (value.getType()) {
			case SCALAR_TYPE_NULL -> BuiltinType.BUILTIN_TYPE_NULL;
			case SCALAR_TYPE_BOOLEAN -> BuiltinType.BUILTIN_TYPE_BOOLEAN;
			case SCALAR_TYPE_NUMBER -> BuiltinType.BUILTIN_TYPE_NUMBER;
			case SCALAR_TYPE_STRING -> BuiltinType.BUILTIN_TYPE_STRING;
			default -> throw invalid(null);
		};
		return InformationItem.newBuilder().setNode(node).setType(TypeReference.newBuilder().setBuiltin(builtin))
				.build();
	}
	private static String expression(DmnIrExpression expression, DmnIrModel model,
			Map<DmnIrReference, Declaration> declarations, DmnIrReference address) {
		if (expression.hasGlobalReference()) {
			var reference = expression.getGlobalReference();
			String variable = declarations.get(reference).name();
			if (reference.getModelNamespace().equals(model.getNamespace()))
				return variable;
			var imports = model.getImportsList().stream()
					.filter(i -> i.getModelNamespace().equals(reference.getModelNamespace())).toList();
			if (imports.size() != 1)
				throw invalid(address);
			return imports.getFirst().getName() + "." + variable;
		}
		var value = expression.getLiteral();
		return switch (value.getValueCase()) {
			case NULL_VALUE -> "null";
			case BOOLEAN_VALUE -> Boolean.toString(value.getBooleanValue());
			case NUMBER_VALUE -> new BigDecimal(new BigInteger(value.getNumberValue().getUnscaledValue()),
					value.getNumberValue().getScale()).toPlainString();
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

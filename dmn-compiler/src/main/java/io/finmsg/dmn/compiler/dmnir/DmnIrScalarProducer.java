package io.finmsg.dmn.compiler.dmnir;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader.VerifiedArtifact;
import io.finmsg.dmn.dmnir.codec.DmnIrValidationException;
import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.model.*;
import io.finmsg.dmn.semantic.analysis.DmnSemanticPipelineResult;
import java.math.BigDecimal;
import java.util.*;
import static io.finmsg.dmn.compiler.dmnir.DmnIrProductionException.Category.*;

/** Produces the experimental scalar profile using existing source semantics. */
public final class DmnIrScalarProducer {
	public VerifiedArtifact produce(DmnSource source) {
		return produce(source, new InMemoryDmnModelResolver(List.of()));
	}

	public VerifiedArtifact produce(DmnSource source, DmnModelResolver resolver) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(resolver, "resolver");
		var loaded = new DmnModelLoader().load(source, resolver);
		var semantic = new DmnModelSetSemanticAnalyzer().analyze(loaded);
		if (semantic.hasErrors()) {
			throw new DmnIrProductionException(SOURCE_INVALID, null, semantic.diagnostics(), null);
		}
		if (semantic.models().stream().anyMatch(m -> m.model().getItemDefinitionsCount() != 0))
			return new DmnIrRecordProducer(semantic).produce();
		var models = semantic.models().stream().sorted(Comparator.comparing(m -> m.identity().namespace())).toList();
		// Match the lowerer's ordered model/declaration index before emitting stable
		// identities.
		Map<Integer, DmnIrReference> addresses = new HashMap<>();
		int slot = 0;
		for (var model : models) {
			if (model.model().getItemDefinitionsCount() != 0)
				throw unsupported(null);
			for (var element : model.model().getDrgElementsList()) {
				Node node = switch (element.getElementCase()) {
					case INPUT_DATA -> element.getInputData().getNode();
					case DECISION -> element.getDecision().getNode();
					default -> throw unsupported(null);
				};
				if (element.hasDecision() && element.getDecision().getLogic().hasDecisionTable())
					throw unsupported(reference(model.identity().namespace(), node.getId()));
				if (element.hasDecision()) {
					var logic = element.getDecision().getLogic();
					var address = reference(model.identity().namespace(), node.getId());
					if (!logic.hasLiteralExpression() || !logic.getLiteralExpression().hasParsed())
						throw unsupported(address);
					sourceAdmission(logic.getLiteralExpression().getParsed().getAst(), address, 1);
				}
				addresses.put(slot++, reference(model.identity().namespace(), node.getId()));
			}
		}
		RuntimeModel runtime;
		try {
			runtime = new RuntimeIrLowerer().lowerModelSet(models.stream()
					.map(m -> new DmnSemanticPipelineResult(m.model(), m.compilationOrder(), List.of(), m.bindings()))
					.toList());
		} catch (RuntimeIrLoweringException e) {
			throw new DmnIrProductionException(UNSUPPORTED_PROFILE, null, List.of(), e);
		}
		Map<Integer, RuntimeInput> inputs = new HashMap<>();
		runtime.inputs().forEach(i -> inputs.put(i.valueSlot(), i));
		Map<Integer, RuntimeDecision> decisions = new HashMap<>();
		runtime.decisions().forEach(d -> decisions.put(d.resultSlot(), d));
		var artifact = DmnIrArtifact.newBuilder()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(0))
				.setSemanticsVersion("finmsg-dmnir-scalar/0").addRequiredCapabilities("dmnir.scalar")
				.setRootModelNamespace(semantic.model(semantic.rootId()).orElseThrow().identity().namespace());
		slot = 0;
		for (var model : models) {
			var output = DmnIrModel.newBuilder().setNamespace(model.identity().namespace())
					.setIdentity(identity(model.model().getNode()));
			for (var imported : model.model().getImportsList().stream()
					.sorted(Comparator.comparing(io.finmsg.dmn.model.Import::getNamespace)
							.thenComparing(io.finmsg.dmn.model.Import::getName))
					.toList()) {
				var value = DmnIrImport.newBuilder().setModelNamespace(imported.getNamespace())
						.setName(imported.getName());
				if (!imported.getLocationUri().isEmpty())
					value.setLocationUri(imported.getLocationUri());
				output.addImports(value);
			}
			List<DmnIrInput> wireInputs = new ArrayList<>();
			List<DmnIrDecision> wireDecisions = new ArrayList<>();
			for (var element : model.model().getDrgElementsList()) {
				var address = addresses.get(slot);
				if (element.hasInputData()) {
					var input = element.getInputData();
					wireInputs.add(DmnIrInput.newBuilder().setIdentity(identity(input.getNode()))
							.setVariable(variable(input.getVariable(), inputs.get(slot).type(), address)).build());
				} else {
					var sourceDecision = element.getDecision();
					var decision = decisions.get(slot);
					if (decision.decisionTable().isPresent() || decision.expression().isEmpty())
						throw unsupported(address);
					var wire = DmnIrDecision.newBuilder().setIdentity(identity(sourceDecision.getNode()))
							.setVariable(variable(sourceDecision.getVariable(), decision.type(), address))
							.setExpression(expression(decision.expression().orElseThrow(), addresses, address, 1));
					decision.dependencies().stream().map(addresses::get).sorted(REFERENCE_ORDER)
							.forEach(wire::addDependencies);
					wireDecisions.add(wire.build());
				}
				slot++;
			}
			wireInputs.sort(Comparator.comparing(i -> i.getIdentity().getId()));
			wireDecisions.sort(Comparator.comparing(d -> d.getIdentity().getId()));
			output.addAllInputs(wireInputs).addAllDecisions(wireDecisions);
			artifact.addModels(output);
		}
		// Stable topological order independent of source declaration order and local
		// slots.
		Map<DmnIrReference, DmnIrDecision> remaining = new TreeMap<>(REFERENCE_ORDER);
		for (var m : artifact.getModelsList())
			for (var d : m.getDecisionsList())
				remaining.put(reference(m.getNamespace(), d.getIdentity().getId()), d);
		Set<DmnIrReference> completed = new HashSet<>();
		for (var m : artifact.getModelsList())
			for (var i : m.getInputsList())
				completed.add(reference(m.getNamespace(), i.getIdentity().getId()));
		while (!remaining.isEmpty()) {
			var next = remaining.entrySet().stream()
					.filter(e -> completed.containsAll(e.getValue().getDependenciesList())).findFirst()
					.orElseThrow(() -> unsupported(null));
			artifact.addEvaluationOrder(next.getKey());
			completed.add(next.getKey());
			remaining.remove(next.getKey());
		}
		boolean extended = artifact.getModelsList().stream().flatMap(m -> m.getDecisionsList().stream())
				.anyMatch(d -> d.getExpression().hasUnary() || d.getExpression().hasBinary()
						|| d.getExpression().hasConditional());
		if (extended)
			artifact.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(1))
					.setSemanticsVersion("finmsg-dmnir-scalar-expressions/0")
					.addRequiredCapabilities("dmnir.scalar-expressions");
		try {
			return new DmnIrArtifactReader().read(artifact.build().toByteArray());
		} catch (DmnIrValidationException e) {
			throw new DmnIrProductionException(
					e.code() == DmnIrValidationException.Code.INVALID_TYPE ? UNSUPPORTED_PROFILE : ARTIFACT_INVALID,
					null, List.of(), e);
		}
	}

	private static final Comparator<DmnIrReference> REFERENCE_ORDER = Comparator
			.comparing(DmnIrReference::getModelNamespace).thenComparing(DmnIrReference::getElementId);

	private static DmnIrReference reference(String namespace, String id) {
		return DmnIrReference.newBuilder().setModelNamespace(namespace).setElementId(id).build();
	}

	private static DmnIrElementIdentity identity(Node node) {
		return DmnIrElementIdentity.newBuilder().setId(node.getId()).setName(node.getName()).build();
	}

	private static DmnIrVariable variable(InformationItem item, RuntimeType type, DmnIrReference address) {
		var result = DmnIrVariable.newBuilder().setName(item.getNode().getName()).setType(type(type, address));
		if (!item.getNode().getId().isEmpty())
			result.setId(item.getNode().getId());
		return result.build();
	}

	private static DmnIrScalarType type(RuntimeType type, DmnIrReference address) {
		return switch (type.kind()) {
			case NULL -> DmnIrScalarType.SCALAR_TYPE_NULL;
			case BOOLEAN -> DmnIrScalarType.SCALAR_TYPE_BOOLEAN;
			case NUMBER -> DmnIrScalarType.SCALAR_TYPE_NUMBER;
			case STRING -> DmnIrScalarType.SCALAR_TYPE_STRING;
			default -> throw unsupported(address);
		};
	}

	private static DmnIrExpression expression(RuntimeExpression expression, Map<Integer, DmnIrReference> addresses,
			DmnIrReference address, int depth) {
		if (depth > 32)
			throw unsupported(address);
		var output = DmnIrExpression.newBuilder().setType(type(expression.type(), address));
		if (expression instanceof RuntimeInvocationExpression call && call.function().orElse("").equals("not")
				&& call.target().isEmpty() && call.namedArguments().isEmpty()
				&& call.positionalArguments().size() == 1) {
			return output.setUnary(DmnIrUnaryExpression.newBuilder().setOperator(DmnIrUnaryOperator.UNARY_OPERATOR_NOT)
					.setOperand(expression(call.positionalArguments().getFirst(), addresses, address, depth + 1)))
					.build();
		}
		if (expression instanceof RuntimeFunctionCall call && call.function().equals("not")
				&& call.arguments().size() == 1) {
			return output
					.setUnary(DmnIrUnaryExpression.newBuilder().setOperator(DmnIrUnaryOperator.UNARY_OPERATOR_NOT)
							.setOperand(expression(call.arguments().getFirst(), addresses, address, depth + 1)))
					.build();
		}
		if (expression instanceof RuntimeValueReference ref)
			return output.setGlobalReference(addresses.get(ref.sourceSlot())).build();
		if (expression instanceof RuntimeUnaryExpression unary) {
			return output.setUnary(DmnIrUnaryExpression.newBuilder().setOperator(switch (unary.operator()) {
				case POSITIVE -> DmnIrUnaryOperator.UNARY_OPERATOR_POSITIVE;
				case NEGATE -> DmnIrUnaryOperator.UNARY_OPERATOR_NEGATE;
				case NOT -> DmnIrUnaryOperator.UNARY_OPERATOR_NOT;
			}).setOperand(expression(unary.operand(), addresses, address, depth + 1))).build();
		}
		if (expression instanceof RuntimeBinaryExpression binary) {
			return output.setBinary(DmnIrBinaryExpression.newBuilder().setOperator(switch (binary.operator()) {
				case ADD -> DmnIrBinaryOperator.BINARY_OPERATOR_ADD;
				case SUBTRACT -> DmnIrBinaryOperator.BINARY_OPERATOR_SUBTRACT;
				case MULTIPLY -> DmnIrBinaryOperator.BINARY_OPERATOR_MULTIPLY;
				case DIVIDE -> DmnIrBinaryOperator.BINARY_OPERATOR_DIVIDE;
				case POWER -> DmnIrBinaryOperator.BINARY_OPERATOR_POWER;
				case EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL;
				case NOT_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_NOT_EQUAL;
				case LESS -> DmnIrBinaryOperator.BINARY_OPERATOR_LESS;
				case LESS_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_LESS_EQUAL;
				case GREATER -> DmnIrBinaryOperator.BINARY_OPERATOR_GREATER;
				case GREATER_EQUAL -> DmnIrBinaryOperator.BINARY_OPERATOR_GREATER_EQUAL;
				case AND -> DmnIrBinaryOperator.BINARY_OPERATOR_AND;
				case OR -> DmnIrBinaryOperator.BINARY_OPERATOR_OR;
			}).setLeft(expression(binary.left(), addresses, address, depth + 1))
					.setRight(expression(binary.right(), addresses, address, depth + 1))).build();
		}
		if (expression instanceof RuntimeConditionalExpression conditional) {
			return output
					.setConditional(DmnIrConditionalExpression.newBuilder()
							.setCondition(expression(conditional.condition(), addresses, address, depth + 1))
							.setThenExpression(expression(conditional.thenExpression(), addresses, address, depth + 1))
							.setElseExpression(expression(conditional.elseExpression(), addresses, address, depth + 1)))
					.build();
		}
		if (!(expression instanceof RuntimeConstant constant))
			throw unsupported(address);
		var literal = DmnIrLiteral.newBuilder();
		switch (constant.kind()) {
			case NULL -> literal.setNullValue(DmnIrNull.NULL_VALUE);
			case BOOLEAN -> literal.setBooleanValue(Boolean.parseBoolean(constant.value()));
			case STRING -> literal.setStringValue(constant.value());
			case NUMBER -> {
				BigDecimal number = new BigDecimal(constant.value());
				literal.setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue(number.unscaledValue().toString())
						.setScale(number.scale()));
			}
			default -> throw unsupported(address);
		}
		return output.setLiteral(literal).build();
	}

	private static void sourceAdmission(com.google.protobuf.Message message, DmnIrReference address, int depth) {
		if (depth > 64)
			throw unsupported(address);
		if (message instanceof Expression expression) {
			if (!expression.hasLiteral() && !expression.hasName() && expression.getInferredType().hasBuiltin()
					&& expression.getInferredType().getBuiltin() == BuiltinType.BUILTIN_TYPE_NULL)
				throw unsupported(address);
			boolean admitted = switch (expression.getNodeCase()) {
				case LITERAL, NAME, PATH, UNARY, BINARY, IF_EXPRESSION -> true;
				case INVOCATION -> expression.getInvocation().getTarget().hasName()
						&& expression.getInvocation().getTarget().getName().getName().equals("not")
						&& expression.getInvocation().getPositionalArgumentsCount() == 1
						&& expression.getInvocation().getArgumentsCount() == 0;
				default -> false;
			};
			if (!admitted)
				throw unsupported(address);
		}
		for (var entry : message.getAllFields().entrySet()) {
			if (entry.getKey().getJavaType() != com.google.protobuf.Descriptors.FieldDescriptor.JavaType.MESSAGE)
				continue;
			if (entry.getKey().isRepeated()) {
				for (var child : (List<?>) entry.getValue())
					sourceAdmission((com.google.protobuf.Message) child, address, depth + 1);
			} else
				sourceAdmission((com.google.protobuf.Message) entry.getValue(), address, depth + 1);
		}
	}

	private static DmnIrProductionException unsupported(DmnIrReference ref) {
		return new DmnIrProductionException(UNSUPPORTED_PROFILE,
				ref == null || ref.getElementId().isBlank()
						? null
						: new DmnIrRuntimeAdapter.ElementId(ref.getModelNamespace(), ref.getElementId()),
				List.of(), null);
	}
}

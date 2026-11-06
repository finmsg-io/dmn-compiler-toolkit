package io.finmsg.dmn.compiler.dmnir;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.model.*;
import io.finmsg.dmn.semantic.analysis.DmnSymbolBinding;
import java.math.BigDecimal;
import java.util.*;

/** Retains source named identity before structural Runtime IR erases it. */
final class DmnIrRecordProducer {
	private final DmnModelSetSemanticResult semantic;
	private final Map<String, DmnIrLocalBinding> localBindings = new HashMap<>();
	private boolean localProfile;
	private final Map<String, DmnSemanticModel> models = new LinkedHashMap<>();
	private record TypeName(String namespace, String name) {
	}
	private final Map<TypeName, DmnIrReference> named = new HashMap<>();
	private final Map<DmnIrReference, DmnIrTypeReference> declarations = new HashMap<>();
	private final Map<DmnIrReference, DmnIrRecordDefinition> records = new HashMap<>();
	private static final Comparator<DmnIrReference> ORDER = Comparator.comparing(DmnIrReference::getModelNamespace)
			.thenComparing(DmnIrReference::getElementId);

	DmnIrRecordProducer(DmnModelSetSemanticResult semantic) {
		this.semantic = semantic;
		semantic.models().stream().sorted(Comparator.comparing(m -> m.identity().namespace()))
				.forEach(m -> models.put(m.identity().namespace(), m));
	}

	DmnIrArtifactReader.VerifiedArtifact produce() {
		localProfile = models.values().stream().anyMatch(m -> m.model().getDrgElementsList().stream()
				.anyMatch(e -> e.hasDecision() && e.getDecision().getLogic().hasBoxedExpression()));
		for (var model : models.values())
			for (var item : model.model().getItemDefinitionsList()) {
				if (item.getNode().getId().isBlank() || item.getComponentsCount() == 0 || item.getIsCollection()
						|| item.hasType() || item.hasConstraint())
					throw unsupported();
				named.put(new TypeName(model.identity().namespace(), item.getNode().getName()),
						ref(model.identity().namespace(), item.getNode().getId()));
			}
		for (var model : models.values())
			for (var item : model.model().getItemDefinitionsList()) {
				var record = DmnIrRecordDefinition.newBuilder().setIdentity(identity(item.getNode()));
				for (var component : item.getComponentsList()) {
					if (component.getIsCollection() || component.hasConstraint() || component.getComponentsCount() != 0)
						throw unsupported();
					var field = DmnIrRecordField.newBuilder().setName(component.getNode().getName())
							.setType(type(component.getType(), model));
					if (!component.getNode().getId().isBlank())
						field.setId(component.getNode().getId());
					record.addFields(field);
				}
				records.put(ref(model.identity().namespace(), item.getNode().getId()), record.build());
			}
		for (var model : models.values())
			for (var element : model.model().getDrgElementsList()) {
				if (element.hasInputData())
					declarations.put(ref(model.identity().namespace(), element.getInputData().getNode().getId()),
							type(element.getInputData().getVariable().getType(), model));
				else if (element.hasDecision())
					declarations.put(ref(model.identity().namespace(), element.getDecision().getNode().getId()),
							type(element.getDecision().getVariable().getType(), model));
				else
					throw unsupported();
			}
		var artifact = DmnIrArtifact.newBuilder()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(localProfile ? 3 : 2))
				.setSemanticsVersion(localProfile
						? DmnIrArtifactReader.LOCAL_SEMANTICS_VERSION
						: DmnIrArtifactReader.RECORD_SEMANTICS_VERSION)
				.addRequiredCapabilities("dmnir.scalar").addRequiredCapabilities("dmnir.scalar-expressions")
				.addRequiredCapabilities("dmnir.records")
				.setRootModelNamespace(semantic.model(semantic.rootId()).orElseThrow().identity().namespace());
		if (localProfile)
			artifact.addRequiredCapabilities(DmnIrArtifactReader.LOCAL_CAPABILITY);
		for (var model : models.values()) {
			var output = DmnIrModel.newBuilder().setIdentity(identity(model.model().getNode()))
					.setNamespace(model.identity().namespace());
			model.model().getItemDefinitionsList().stream().sorted(Comparator.comparing(i -> i.getNode().getId()))
					.forEach(i -> output
							.addRecordDefinitions(records.get(ref(model.identity().namespace(), i.getNode().getId()))));
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
			for (var element : model.model().getDrgElementsList()) {
				if (element.hasInputData()) {
					var input = element.getInputData();
					inputs.add(DmnIrInput.newBuilder().setIdentity(identity(input.getNode()))
							.setVariable(variable(input.getVariable(), model)).build());
				} else {
					var decision = element.getDecision();
					if (!(decision.getLogic().hasLiteralExpression()
							&& decision.getLogic().getLiteralExpression().hasParsed()
							|| decision.getLogic().hasBoxedExpression()
									&& decision.getLogic().getBoxedExpression().hasParsed())
							|| decision.getKnowledgeRequirementsCount() != 0
							|| decision.getAuthorityRequirementsCount() != 0)
						throw unsupported();
					Set<DmnIrReference> dependencies = new TreeSet<>(ORDER);
					for (var requirement : decision.getInformationRequirementsList())
						dependencies.add(requirement(
								requirement.hasInput() ? requirement.getInput() : requirement.getDecision(), model));
					localBindings.clear();
					String path = "definitions/decision[" + decision.getNode().getName() + "]/logic";
					var expression = decision.getLogic().hasLiteralExpression()
							? expression(decision.getLogic().getLiteralExpression().getParsed().getAst(), model,
									path + "/literalExpression", dependencies, 1)
							: boxed(decision.getLogic().getBoxedExpression().getParsed(), model,
									path + "/boxedExpression", dependencies, 1,
									type(decision.getVariable().getType(), model));
					decisions.add(DmnIrDecision.newBuilder().setIdentity(identity(decision.getNode()))
							.setVariable(variable(decision.getVariable(), model)).addAllDependencies(dependencies)
							.setExpression(expression).build());
				}
			}
			inputs.sort(Comparator.comparing(i -> i.getIdentity().getId()));
			decisions.sort(Comparator.comparing(d -> d.getIdentity().getId()));
			output.addAllInputs(inputs).addAllDecisions(decisions);
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
		try {
			return new DmnIrArtifactReader().read(artifact.build().toByteArray());
		} catch (DmnIrValidationException e) {
			throw new DmnIrProductionException(e.code() == DmnIrValidationException.Code.INVALID_TYPE
					? DmnIrProductionException.Category.UNSUPPORTED_PROFILE
					: DmnIrProductionException.Category.ARTIFACT_INVALID, null, List.of(), e);
		}
	}

	private DmnIrTypeReference type(TypeReference type, DmnSemanticModel model) {
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
			var record = named.get(new TypeName(namespace, name));
			if (record == null)
				throw unsupported();
			return DmnIrTypeReference.newBuilder().setRecordType(record).build();
		}
		if (!type.hasBuiltin())
			throw unsupported();
		return DmnIrTypeReference.newBuilder().setScalarType(switch (type.getBuiltin()) {
			case BUILTIN_TYPE_NULL -> DmnIrScalarType.SCALAR_TYPE_NULL;
			case BUILTIN_TYPE_BOOLEAN -> DmnIrScalarType.SCALAR_TYPE_BOOLEAN;
			case BUILTIN_TYPE_NUMBER -> DmnIrScalarType.SCALAR_TYPE_NUMBER;
			case BUILTIN_TYPE_STRING -> DmnIrScalarType.SCALAR_TYPE_STRING;
			default -> throw unsupported();
		}).build();
	}
	private DmnIrVariable variable(InformationItem item, DmnSemanticModel model) {
		var output = DmnIrVariable.newBuilder().setName(item.getNode().getName());
		if (!item.getNode().getId().isBlank())
			output.setId(item.getNode().getId());
		var type = type(item.getType(), model);
		if (type.hasRecordType())
			output.setRecordType(type.getRecordType());
		else
			output.setType(type.getScalarType());
		return output.build();
	}
	private DmnIrExpression expression(Expression source, DmnSemanticModel model, String path,
			Set<DmnIrReference> dependencies, int depth) {
		if (depth > 32)
			throw unsupported();
		var binding = model.bindings().stream().filter(b -> b.referencePath().equals(path)).findFirst();
		if ((source.hasName() || source.hasPath()) && binding.isPresent()) {
			DmnSymbolBinding symbol = binding.get();
			if (symbol.kind() == io.finmsg.dmn.semantic.analysis.DmnSymbolKind.LOCAL_VARIABLE) {
				var local = localBindings.get(symbol.declarationPath());
				if (local == null)
					throw unsupported();
				var variable = local.getVariable();
				var localType = variable.hasRecordType()
						? DmnIrTypeReference.newBuilder().setRecordType(variable.getRecordType()).build()
						: DmnIrTypeReference.newBuilder().setScalarType(variable.getType()).build();
				return typed(localType)
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
			if (!parent.hasRecordType())
				throw unsupported();
			var record = records.get(parent.getRecordType());
			if (record == null)
				throw unsupported();
			var field = record.getFieldsList().stream().filter(f -> f.getName().equals(value.getMember())).findFirst()
					.orElseThrow(DmnIrRecordProducer::unsupported);
			return typed(field.getType())
					.setFieldAccess(DmnIrFieldAccess.newBuilder().setSource(parent).setFieldName(value.getMember()))
					.build();
		}
		if (source.hasIfExpression()) {
			var value = source.getIfExpression();
			var condition = expression(value.getCondition(), model, path + "/condition", dependencies, depth + 1);
			var yes = expression(value.getThenExpression(), model, path + "/then", dependencies, depth + 1);
			var no = expression(value.getElseExpression(), model, path + "/else", dependencies, depth + 1);
			DmnIrTypeReference result;
			if (yes.hasRecordType() || no.hasRecordType()) {
				result = expressionType(yes.hasRecordType() ? yes : no);
				var other = expressionType(yes.hasRecordType() ? no : yes);
				if (!other.equals(result)
						&& !(other.hasScalarType() && other.getScalarType() == DmnIrScalarType.SCALAR_TYPE_NULL))
					throw unsupported();
			} else
				result = type(source.getInferredType(), model);
			return typed(result).setConditional(DmnIrConditionalExpression.newBuilder().setCondition(condition)
					.setThenExpression(yes).setElseExpression(no)).build();
		}
		var output = typed(type(source.getInferredType(), model));
		if (!source.hasLiteral() && source.getInferredType().getBuiltin() == BuiltinType.BUILTIN_TYPE_NULL)
			throw unsupported();
		switch (source.getNodeCase()) {
			case LITERAL -> {
				var literal = DmnIrLiteral.newBuilder();
				var value = source.getLiteral();
				switch (value.getKind()) {
					case LITERAL_KIND_NULL -> literal.setNullValue(DmnIrNull.NULL_VALUE);
					case LITERAL_KIND_BOOLEAN -> literal.setBooleanValue(Boolean.parseBoolean(value.getValue()));
					case LITERAL_KIND_STRING -> literal.setStringValue(value.getValue());
					case LITERAL_KIND_NUMBER -> {
						var number = new BigDecimal(value.getValue());
						literal.setNumberValue(DmnIrDecimal.newBuilder()
								.setUnscaledValue(number.unscaledValue().toString()).setScale(number.scale()));
					}
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
				if (!value.getTarget().hasName() || !value.getTarget().getName().getName().equals("not")
						|| value.getArgumentsCount() != 0 || value.getPositionalArgumentsCount() != 1)
					throw unsupported();
				return output
						.setUnary(DmnIrUnaryExpression.newBuilder().setOperator(DmnIrUnaryOperator.UNARY_OPERATOR_NOT)
								.setOperand(expression(value.getPositionalArguments(0), model, path + "/argument[0]",
										dependencies, depth + 1)))
						.build();
			}
			default -> throw unsupported();
		}
	}
	private DmnIrExpression parsed(ExpressionParsed source, DmnSemanticModel model, String path,
			Set<DmnIrReference> dependencies, int depth, DmnIrTypeReference expected) {
		if (source.hasFeel())
			return expression(source.getFeel().getAst(), model, path, dependencies, depth);
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
		String href = reference.getHref();
		int hash = href.lastIndexOf('#');
		String namespace = hash <= 0 ? model.identity().namespace() : href.substring(0, hash);
		String id = hash < 0 ? href : href.substring(hash + 1);
		final String qualifier = namespace;
		namespace = model.model().getImportsList().stream().filter(i -> i.getName().equals(qualifier))
				.map(io.finmsg.dmn.model.Import::getNamespace).findFirst().orElse(namespace);
		var target = ref(namespace, id);
		if (!declarations.containsKey(target))
			throw unsupported();
		return target;
	}
	private static DmnIrTypeReference expressionType(DmnIrExpression value) {
		return value.hasRecordType()
				? DmnIrTypeReference.newBuilder().setRecordType(value.getRecordType()).build()
				: DmnIrTypeReference.newBuilder().setScalarType(value.getType()).build();
	}
	private static DmnIrExpression.Builder typed(DmnIrTypeReference type) {
		var b = DmnIrExpression.newBuilder();
		return type.hasRecordType() ? b.setRecordType(type.getRecordType()) : b.setType(type.getScalarType());
	}
	private static DmnIrElementIdentity identity(Node node) {
		return DmnIrElementIdentity.newBuilder().setId(node.getId()).setName(node.getName()).build();
	}
	private static DmnIrReference ref(String namespace, String id) {
		return DmnIrRecordTypes.ref(namespace, id);
	}
	private static DmnIrProductionException unsupported() {
		return new DmnIrProductionException(DmnIrProductionException.Category.UNSUPPORTED_PROFILE, null, List.of(),
				null);
	}
}

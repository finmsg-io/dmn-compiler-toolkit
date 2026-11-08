package io.finmsg.dmn.compiler.dmnir;

import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader.VerifiedArtifact;
import io.finmsg.dmn.dmnir.codec.DmnIrBuiltinSignature;
import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

/**
 * Derives process-local scalar Runtime IR from the verified, authoritative
 * portable graph.
 */
public final class DmnIrRuntimeAdapter {
	public record ElementId(String modelNamespace, String elementId) {
		public ElementId {
			Objects.requireNonNull(modelNamespace, "modelNamespace");
			Objects.requireNonNull(elementId, "elementId");
			if (modelNamespace.isBlank() || elementId.isBlank()) {
				throw new IllegalArgumentException("Namespace and element ID must be non-empty.");
			}
		}
	}

	/**
	 * Public identity and preserved interchange metadata; slot is derived, not a
	 * wire identity.
	 */
	public record Binding(ElementId id, String elementName, Optional<String> variableId, String variableName,
			RuntimeType type, int slot) {
		public Binding {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(elementName, "elementName");
			Objects.requireNonNull(variableId, "variableId");
			Objects.requireNonNull(variableName, "variableName");
			Objects.requireNonNull(type, "type");
			if (slot < 0) {
				throw new IllegalArgumentException("Slot must be non-negative.");
			}
		}
	}

	public AdaptedModel adapt(VerifiedArtifact verified) {
		Objects.requireNonNull(verified, "verified");
		DmnIrArtifact artifact = verified.artifact();
		// Pin this consumer's profile independently of future reader extensions.
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
		if (artifact.getSchemaVersion().getMajor() != 0 || !(scalarProfile || expressionProfile || recordProfile
				|| localProfile || dateProfile || tableProfile || constraintProfile || collectionProfile
				|| filterProfile || artifact.getSchemaVersion().getMinor() == 9))
			throw new IllegalArgumentException("Unsupported runtime adapter profile.");
		var types = new DmnIrRecordTypes(artifact);
		List<Declaration> declarations = new ArrayList<>();
		for (DmnIrModel model : artifact.getModelsList()) {
			for (DmnIrInput input : model.getInputsList()) {
				declarations.add(new Declaration(new ElementId(model.getNamespace(), input.getIdentity().getId()),
						input.getIdentity(), input.getVariable(), null));
			}
			for (DmnIrDecision decision : model.getDecisionsList()) {
				declarations.add(new Declaration(new ElementId(model.getNamespace(), decision.getIdentity().getId()),
						decision.getIdentity(), decision.getVariable(), decision));
			}
		}
		declarations.sort(Comparator.comparing(Declaration::id,
				Comparator.comparing(ElementId::modelNamespace).thenComparing(ElementId::elementId)));
		Map<ElementId, Integer> slots = new LinkedHashMap<>();
		for (Declaration declaration : declarations) {
			slots.put(declaration.id(), slots.size());
		}
		List<RuntimeInput> runtimeInputs = new ArrayList<>();
		List<RuntimeDecision> runtimeDecisions = new ArrayList<>();
		Map<ElementId, Binding> inputs = new LinkedHashMap<>();
		Map<ElementId, Binding> decisions = new LinkedHashMap<>();
		for (Declaration declaration : declarations) {
			int slot = slots.get(declaration.id());
			var variable = declaration.variable();
			RuntimeType type = variable.hasCollectionType()
					? types.collectionType(variable.getCollectionType())
					: types.type(variable.getType(), variable.hasRecordType(), variable.getRecordType());
			Binding binding = new Binding(declaration.id(), declaration.identity().getName(),
					declaration.variable().hasId() ? Optional.of(declaration.variable().getId()) : Optional.empty(),
					declaration.variable().getName(), type, slot);
			if (declaration.decision() == null) {
				inputs.put(declaration.id(), binding);
				runtimeInputs.add(new RuntimeInput(slot, slot, type));
			} else {
				decisions.put(declaration.id(), binding);
				DmnIrDecision decision = declaration.decision();
				List<Integer> dependencies = decision.getDependenciesList().stream()
						.map(reference -> slots.get(id(reference))).toList();
				int[] nextLocalSlot = {0};
				if (decision.hasDecisionTable()) {
					var table = table(decision.getDecisionTable(), types,
							cell -> expression(cell, slots, types, Map.of(), nextLocalSlot));
					runtimeDecisions.add(new RuntimeDecision(slot, slot, type, dependencies, Optional.empty(),
							Optional.of(table), nextLocalSlot[0]));
				} else {
					var body = expression(decision.getExpression(), slots, types, new HashMap<>(), nextLocalSlot);
					runtimeDecisions.add(new RuntimeDecision(slot, slot, type, dependencies, Optional.of(body),
							Optional.empty(), nextLocalSlot[0]));
				}
			}
		}
		List<Integer> order = artifact.getEvaluationOrderList().stream().map(value -> slots.get(id(value))).toList();
		RuntimeModel model = new RuntimeModel(runtimeInputs, runtimeDecisions, List.of(), order, slots.size());
		return new AdaptedModel(verified, model, inputs, decisions);
	}

	private static RuntimeDecisionTable table(DmnIrDecisionTable table, DmnIrRecordTypes types,
			Function<DmnIrExpression, RuntimeExpression> expression) {
		var inputs = table.getInputsList().stream().map(input -> {
			var value = expression.apply(input.getExpression());
			return new RuntimeDecisionTableInput(value, Optional.empty(),
					types.type(input.getType(), false, DmnIrReference.getDefaultInstance()));
		}).toList();
		var outputs = table.getOutputsList().stream()
				.map(output -> new RuntimeDecisionTableOutput(
						output.hasName() ? Optional.of(output.getName()) : Optional.empty(),
						types.type(output.getType(), false, DmnIrReference.getDefaultInstance()), Optional.empty(),
						output.hasDefaultExpression()
								? Optional.of(expression.apply(output.getDefaultExpression()))
								: Optional.empty()))
				.toList();
		List<RuntimeDecisionTableRule> rules = new ArrayList<>();
		for (var rule : table.getRulesList()) {
			rules.add(new RuntimeDecisionTableRule(rules.size(), rule.getInputEntriesList().stream()
					.map(group -> new RuntimeUnaryTests(group.getNegated(), group.getWildcard(),
							group.getTestsList().stream().map(test -> test(test, expression)).toList()))
					.toList(), rule.getOutputEntriesList().stream().map(expression).toList(), List.of()));
		}
		return new RuntimeDecisionTable(switch (table.getHitPolicy()) {
			case TABLE_HIT_POLICY_UNIQUE -> RuntimeHitPolicy.UNIQUE;
			case TABLE_HIT_POLICY_FIRST -> RuntimeHitPolicy.FIRST;
			case TABLE_HIT_POLICY_ANY -> RuntimeHitPolicy.ANY;
			default -> throw new IllegalArgumentException("Unsupported verified hit policy.");
		}, Optional.empty(), inputs, outputs, rules, 0);
	}

	static RuntimeUnaryTest test(DmnIrUnaryTest test, Function<DmnIrExpression, RuntimeExpression> expression) {
		return switch (test.getTestCase()) {
			case COMPARISON -> new RuntimeComparisonUnaryTest(switch (test.getComparison().getOperator()) {
				case BINARY_OPERATOR_EQUAL -> RuntimeUnaryTestOperator.EQUAL;
				case BINARY_OPERATOR_NOT_EQUAL -> RuntimeUnaryTestOperator.NOT_EQUAL;
				case BINARY_OPERATOR_LESS -> RuntimeUnaryTestOperator.LESS;
				case BINARY_OPERATOR_LESS_EQUAL -> RuntimeUnaryTestOperator.LESS_EQUAL;
				case BINARY_OPERATOR_GREATER -> RuntimeUnaryTestOperator.GREATER;
				case BINARY_OPERATOR_GREATER_EQUAL -> RuntimeUnaryTestOperator.GREATER_EQUAL;
				default -> throw new IllegalArgumentException("Unsupported verified comparison.");
			}, expression.apply(test.getComparison().getEndpoint()));
			case RANGE -> {
				var range = test.getRange();
				var lower = expression.apply(range.getLower());
				yield new RuntimeRangeUnaryTest(
						new RuntimeRangeExpression(Optional.of(lower), Optional.of(expression.apply(range.getUpper())),
								range.getLowerInclusive() ? RuntimeRangeBoundary.CLOSED : RuntimeRangeBoundary.OPEN,
								range.getUpperInclusive() ? RuntimeRangeBoundary.CLOSED : RuntimeRangeBoundary.OPEN,
								RuntimeType.element(RuntimeTypeKind.RANGE, lower.type())));
			}
			case TEST_NOT_SET -> throw new IllegalArgumentException("Missing verified test.");
		};
	}

	private record Local(int depth, int slot) {
	}

	private static RuntimeExpression expression(DmnIrExpression expression, Map<ElementId, Integer> slots,
			DmnIrRecordTypes types, Map<String, Local> locals, int[] nextSlot) {
		RuntimeType type = expression.hasCollectionType()
				? types.collectionType(expression.getCollectionType())
				: types.type(expression.getType(), expression.hasRecordType(), expression.getRecordType());
		return switch (expression.getNodeCase()) {
			case LOCAL_REFERENCE -> {
				var local = locals.get(expression.getLocalReference().getBindingId());
				yield new RuntimeLocalReference(local.depth(), local.slot(), type);
			}
			case CONTEXT -> {
				Map<String, Local> child = new HashMap<>();
				// Context scopes share the decision frame, matching source lowering.
				// Distinct binding IDs get distinct slots; scopes control visibility.
				child.putAll(locals);
				List<RuntimeContextEntry> entries = new ArrayList<>();
				for (var binding : expression.getContext().getBindingsList()) {
					var value = expression(binding.getValue(), slots, types, child, nextSlot);
					int slot = nextSlot[0]++;
					entries.add(new RuntimeContextEntry(binding.getVariable().getName(), slot, value));
					child.put(binding.getBindingId(), new Local(0, slot));
				}
				entries.add(new RuntimeContextEntry("", -1,
						expression(expression.getContext().getResult(), slots, types, child, nextSlot)));
				yield new RuntimeContextExpression(entries, type);
			}
			case FIELD_ACCESS -> {
				// Match source lowering's type-proven null plan, including native invalid
				// input.
				if (type.kind() == RuntimeTypeKind.NULL)
					yield new RuntimeConstant(RuntimeConstantKind.NULL, "null", type);
				var access = expression.getFieldAccess();
				var source = expression(access.getSource(), slots, types, locals, nextSlot);
				int index = source.type().fieldLayout().stream().filter(f -> f.name().equals(access.getFieldName()))
						.findFirst().orElseThrow().index();
				yield new RuntimePathExpression(source, access.getFieldName(), index, type);
			}
			case GLOBAL_REFERENCE -> new RuntimeValueReference(slots.get(id(expression.getGlobalReference())), type);
			case UNARY -> new RuntimeUnaryExpression(switch (expression.getUnary().getOperator()) {
				case UNARY_OPERATOR_POSITIVE -> RuntimeUnaryOperator.POSITIVE;
				case UNARY_OPERATOR_NEGATE -> RuntimeUnaryOperator.NEGATE;
				case UNARY_OPERATOR_NOT -> RuntimeUnaryOperator.NOT;
				default -> throw new IllegalArgumentException("Unsupported unary operator.");
			}, expression(expression.getUnary().getOperand(), slots, types, locals, nextSlot), type);
			case BINARY -> new RuntimeBinaryExpression(switch (expression.getBinary().getOperator()) {
				case BINARY_OPERATOR_ADD -> RuntimeBinaryOperator.ADD;
				case BINARY_OPERATOR_SUBTRACT -> RuntimeBinaryOperator.SUBTRACT;
				case BINARY_OPERATOR_MULTIPLY -> RuntimeBinaryOperator.MULTIPLY;
				case BINARY_OPERATOR_DIVIDE -> RuntimeBinaryOperator.DIVIDE;
				case BINARY_OPERATOR_POWER -> RuntimeBinaryOperator.POWER;
				case BINARY_OPERATOR_EQUAL -> RuntimeBinaryOperator.EQUAL;
				case BINARY_OPERATOR_NOT_EQUAL -> RuntimeBinaryOperator.NOT_EQUAL;
				case BINARY_OPERATOR_LESS -> RuntimeBinaryOperator.LESS;
				case BINARY_OPERATOR_LESS_EQUAL -> RuntimeBinaryOperator.LESS_EQUAL;
				case BINARY_OPERATOR_GREATER -> RuntimeBinaryOperator.GREATER;
				case BINARY_OPERATOR_GREATER_EQUAL -> RuntimeBinaryOperator.GREATER_EQUAL;
				case BINARY_OPERATOR_AND -> RuntimeBinaryOperator.AND;
				case BINARY_OPERATOR_OR -> RuntimeBinaryOperator.OR;
				default -> throw new IllegalArgumentException("Unsupported binary operator.");
			}, expression(expression.getBinary().getLeft(), slots, types, locals, nextSlot),
					expression(expression.getBinary().getRight(), slots, types, locals, nextSlot), type);
			case CONDITIONAL -> new RuntimeConditionalExpression(
					expression(expression.getConditional().getCondition(), slots, types, locals, nextSlot),
					expression(expression.getConditional().getThenExpression(), slots, types, locals, nextSlot),
					expression(expression.getConditional().getElseExpression(), slots, types, locals, nextSlot), type);
			case LITERAL -> literal(expression.getLiteral(), type);
			case BUILTIN_CALL -> {
				var call = expression.getBuiltinCall();
				var signature = DmnIrBuiltinSignature.forOperation(call.getOperation()).orElseThrow();
				var arguments = call.getArgumentsList().stream()
						.map(argument -> expression(argument, slots, types, locals, nextSlot)).toList();
				yield new RuntimeFunctionCall(signature.feelName(), arguments, type);
			}
			case FILTER -> {
				var filter = expression.getFilter();
				var source = expression(filter.getSource(), slots, types, locals, nextSlot);
				Map<String, Local> child = new HashMap<>(locals);
				int slot = nextSlot[0]++;
				child.put(filter.getItemBindingId(), new Local(0, slot));
				yield new RuntimeFilterExpression(source, slot,
						expression(filter.getPredicate(), slots, types, child, nextSlot), type);
			}
			case LIST -> new RuntimeListExpression(expression.getList().getElementsList().stream()
					.map(element -> expression(element, slots, types, locals, nextSlot)).toList(), type);
			default -> throw new IllegalArgumentException("Unsupported expression in runtime adapter.");
		};
	}

	static RuntimeConstant literal(DmnIrLiteral literal, RuntimeType type) {
		return switch (literal.getValueCase()) {
			case NULL_VALUE -> new RuntimeConstant(RuntimeConstantKind.NULL, "null", type);
			case BOOLEAN_VALUE ->
				new RuntimeConstant(RuntimeConstantKind.BOOLEAN, Boolean.toString(literal.getBooleanValue()), type);
			case DATE_VALUE -> new RuntimeConstant(RuntimeConstantKind.DATE,
					DmnIrDates.value(literal.getDateValue()).toString(), type);
			case STRING_VALUE -> new RuntimeConstant(RuntimeConstantKind.STRING, literal.getStringValue(), type);
			case NUMBER_VALUE -> {
				DmnIrDecimal decimal = literal.getNumberValue();
				// toString round-trips signed scale; do not expand scientific notation.
				String value = new BigDecimal(new BigInteger(decimal.getUnscaledValue()), decimal.getScale())
						.toString();
				yield new RuntimeConstant(RuntimeConstantKind.NUMBER, value, type);
			}
			default -> throw new IllegalArgumentException("Unsupported literal in runtime adapter.");
		};
	}

	private static ElementId id(DmnIrReference value) {
		return new ElementId(value.getModelNamespace(), value.getElementId());
	}

	private record Declaration(ElementId id, DmnIrElementIdentity identity, DmnIrVariable variable,
			DmnIrDecision decision) {
	}

	private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
		// Map.copyOf would reject legitimate null FEEL results and does not preserve
		// order.
		return Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	/**
	 * Immutable model/binding metadata; evaluation allocates fresh runtime state
	 * for every call.
	 */
	public static final class AdaptedModel {
		private final VerifiedArtifact source;
		private final RuntimeModel runtimeModel;
		private final Map<ElementId, Binding> inputs;
		private final Map<ElementId, Binding> decisions;
		private final DmnRuntime runtime = new DmnRuntime();

		private AdaptedModel(VerifiedArtifact source, RuntimeModel runtimeModel, Map<ElementId, Binding> inputs,
				Map<ElementId, Binding> decisions) {
			this.source = source;
			this.runtimeModel = runtimeModel;
			this.inputs = immutableMap(inputs);
			this.decisions = immutableMap(decisions);
		}

		public VerifiedArtifact source() {
			return source;
		}

		public RuntimeModel runtimeModel() {
			return runtimeModel;
		}

		public Map<ElementId, Binding> inputs() {
			return inputs;
		}

		public Map<ElementId, Binding> decisions() {
			return decisions;
		}

		/**
		 * Evaluates all bundled decisions using namespace-qualified source element IDs.
		 */
		public Map<ElementId, Object> evaluate(Map<ElementId, ?> inputValues) {
			Objects.requireNonNull(inputValues, "inputValues");
			Map<ElementId, ?> snapshot = new LinkedHashMap<>(inputValues);
			for (ElementId key : snapshot.keySet()) {
				if (!inputs.containsKey(key)) {
					throw new IllegalArgumentException("Input identity is not declared by this artifact.");
				}
			}
			Map<Integer, Object> slots = new HashMap<>();
			for (Binding binding : inputs.values()) {
				if (!snapshot.containsKey(binding.id())) {
					throw new IllegalArgumentException("Missing input value for a declared model input.");
				}
				Object value = snapshot.get(binding.id());
				if (binding.type().kind() == RuntimeTypeKind.NULL && value != null) {
					throw new IllegalArgumentException("A null-typed input requires a null value.");
				}
				slots.put(binding.slot(), value);
			}
			var evaluated = runtime.evaluate(runtimeModel, slots);
			Map<ElementId, Object> results = new LinkedHashMap<>();
			for (Binding binding : decisions.values()) {
				results.put(binding.id(), evaluated.value(binding.slot()));
			}
			return immutableMap(results);
		}
	}
}

package io.finmsg.dmn.ir;

import io.finmsg.dmn.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Recursive FEEL AST lowering and lexical-local allocation. */
final class RuntimeExpressionLowerer {
	private RuntimeExpressionLowerer() {
	}

	static RuntimeExpression lowerExpression(Expression expression, String path,
			List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings, Map<String, Integer> slots,
			Map<String, ItemDefinition> itemTypes, Map<String, LocalSlotAddress> localSlots, int[] nextLocalSlot) {
		RuntimeType type = RuntimeTypeLowerer.lower(expression.getInferredType(), itemTypes);
		return switch (expression.getNodeCase()) {
			case LITERAL -> new RuntimeConstant(constantKind(expression.getLiteral().getKind()),
					expression.getLiteral().getValue(), type);
			case NAME -> {
				var bindingOpt = bindings.stream().filter(value -> value.referencePath().equals(path)).findFirst();
				if (bindingOpt.isEmpty()) {
					String name = expression.getName().getName();
					var builtinOp = RuntimeBuiltinOperation.find(name);
					if (builtinOp.isPresent()) {
						yield new RuntimeFunctionDefinition(List.of(),
								Optional.of(new RuntimeInvocationExpression(Optional.of(builtinOp.get().feelName()),
										Optional.empty(), List.of(), List.of(), type)),
								false, type);
					}
					throw new RuntimeIrLoweringException(
							"Missing semantic binding for decision expression at " + path + ".");
				}
				var binding = bindingOpt.get();
				if (binding.kind() == io.finmsg.dmn.semantic.analysis.DmnSymbolKind.LOCAL_VARIABLE
						|| binding.kind() == io.finmsg.dmn.semantic.analysis.DmnSymbolKind.PARAMETER) {
					LocalSlotAddress localSlot = localSlots.get(binding.declarationPath());
					if (localSlot == null) {
						if (binding.declarationPath().contains("/filter/item/")) {
							String prefix = binding.declarationPath().substring(0,
									binding.declarationPath().indexOf("/filter/item/") + 12);
							LocalSlotAddress itemSlot = localSlots.get(prefix);
							if (itemSlot != null) {
								String memberName = binding.declarationPath()
										.substring(binding.declarationPath().indexOf("/filter/item/") + 13);
								yield new RuntimePathExpression(new RuntimeLocalReference(itemSlot.lexicalDepth(),
										itemSlot.localSlot(), RuntimeType.scalar(RuntimeTypeKind.ANY)), memberName,
										type);
							}
						}
						throw new RuntimeIrLoweringException(
								"Local expression binding is not in scope at " + path + ".");
					}
					yield new RuntimeLocalReference(localSlot.lexicalDepth(), localSlot.localSlot(), type);
				}
				String slotKey = binding.targetNamespace().isBlank()
						? binding.symbolId()
						: binding.targetNamespace() + "#" + binding.symbolId();
				Integer slot = slots.get(slotKey);
				if (slot == null) {
					throw new RuntimeIrLoweringException(
							"Expression binding targets a value outside the current runtime model: '"
									+ binding.symbolName() + "'.");
				}
				yield new RuntimeValueReference(slot, type);
			}
			case UNARY -> new RuntimeUnaryExpression(unaryOperator(expression.getUnary().getOperator()),
					lowerExpression(expression.getUnary().getExpression(), path + "/unary", bindings, slots, itemTypes,
							localSlots, nextLocalSlot),
					type);
			case BINARY -> new RuntimeBinaryExpression(binaryOperator(expression.getBinary().getOperator()),
					lowerExpression(expression.getBinary().getLeft(), path + "/left", bindings, slots, itemTypes,
							localSlots, nextLocalSlot),
					lowerExpression(expression.getBinary().getRight(), path + "/right", bindings, slots, itemTypes,
							localSlots, nextLocalSlot),
					type);
			case IF_EXPRESSION -> new RuntimeConditionalExpression(
					lowerExpression(expression.getIfExpression().getCondition(), path + "/condition", bindings, slots,
							itemTypes, localSlots, nextLocalSlot),
					lowerExpression(expression.getIfExpression().getThenExpression(), path + "/then", bindings, slots,
							itemTypes, localSlots, nextLocalSlot),
					lowerExpression(expression.getIfExpression().getElseExpression(), path + "/else", bindings, slots,
							itemTypes, localSlots, nextLocalSlot),
					type);
			case LIST -> {
				List<RuntimeExpression> elements = new ArrayList<>();
				for (int index = 0; index < expression.getList().getElementsCount(); index++) {
					elements.add(lowerExpression(expression.getList().getElements(index),
							path + "/element[" + index + "]", bindings, slots, itemTypes, localSlots, nextLocalSlot));
				}
				yield new RuntimeListExpression(elements, type);
			}
			case FUNCTION_CALL -> {
				List<RuntimeExpression> arguments = new ArrayList<>();
				for (int index = 0; index < expression.getFunctionCall().getArgumentsCount(); index++) {
					arguments.add(lowerExpression(expression.getFunctionCall().getArguments(index),
							path + "/argument[" + index + "]", bindings, slots, itemTypes, localSlots, nextLocalSlot));
				}
				io.finmsg.dmn.semantic.analysis.DmnSymbolBinding binding = bindings.stream()
						.filter(b -> b.referencePath().equals(path)).findFirst().orElse(null);
				if (binding != null) {
					String slotKey = binding.targetNamespace().isBlank()
							? binding.symbolId()
							: binding.targetNamespace() + "#" + binding.symbolId();
					Integer slot = slots.get(slotKey);
					if (slot != null) {
						yield new RuntimeInvocationExpression(Optional.empty(),
								Optional.of(new RuntimeValueReference(slot, type)), List.of(), arguments, type);
					}
				}
				yield new RuntimeFunctionCall(expression.getFunctionCall().getFunction(), arguments, type);
			}
			case CONTEXT -> {
				List<RuntimeContextEntry> entries = new ArrayList<>();
				Map<String, LocalSlotAddress> contextSlots = RuntimeLexicalFrame.childScope(localSlots);
				for (int index = 0; index < expression.getContext().getEntriesCount(); index++) {
					ContextEntry entry = expression.getContext().getEntries(index);
					String entryPath = path + "/entry[" + index + "]";
					RuntimeExpression value = lowerExpression(entry.getExpression(), entryPath, bindings, slots,
							itemTypes, contextSlots, nextLocalSlot);
					int localSlot = nextLocalSlot[0]++;
					contextSlots.put(entryPath, new LocalSlotAddress(0, localSlot));
					entries.add(new RuntimeContextEntry(entry.getName(), localSlot, value));
				}
				yield new RuntimeContextExpression(entries, type);
			}
			case PATH -> {
				var binding = bindings.stream().filter(value -> value.referencePath().equals(path)).findFirst()
						.orElse(null);
				if (binding != null) {
					String slotKey = binding.targetNamespace().isBlank()
							? binding.symbolId()
							: binding.targetNamespace() + "#" + binding.symbolId();
					Integer slot = slots.get(slotKey);
					if (slot != null) {
						yield new RuntimeValueReference(slot, type);
					}
				}
				RuntimeExpression source = lowerExpression(expression.getPath().getSource(), path + "/source", bindings,
						slots, itemTypes, localSlots, nextLocalSlot);
				yield new RuntimePathExpression(source, expression.getPath().getMember(),
						RuntimeTypeLowerer.resolvedFieldIndex(source.type(), expression.getPath().getMember()), type);
			}
			case RANGE ->
				lowerRange(expression.getRange(), type, path, bindings, slots, itemTypes, localSlots, nextLocalSlot);
			case FILTER -> {
				RuntimeExpression source = lowerExpression(expression.getFilter().getSource(), path + "/source",
						bindings, slots, itemTypes, localSlots, nextLocalSlot);
				Map<String, LocalSlotAddress> filterSlots = RuntimeLexicalFrame.childScope(localSlots);
				int localSlot = nextLocalSlot[0]++;
				filterSlots.put(path + "/filter/item", new LocalSlotAddress(0, localSlot));
				RuntimeExpression filter = lowerExpression(expression.getFilter().getFilter(), path + "/filter",
						bindings, slots, itemTypes, filterSlots, nextLocalSlot);
				yield new RuntimeFilterExpression(source, localSlot, filter, type);
			}
			case BETWEEN -> new RuntimeBetweenExpression(
					lowerExpression(expression.getBetween().getValue(), path + "/value", bindings, slots, itemTypes,
							localSlots, nextLocalSlot),
					lowerExpression(expression.getBetween().getLower(), path + "/lower", bindings, slots, itemTypes,
							localSlots, nextLocalSlot),
					lowerExpression(expression.getBetween().getUpper(), path + "/upper", bindings, slots, itemTypes,
							localSlots, nextLocalSlot),
					type);
			case IN -> new RuntimeInExpression(
					lowerExpression(expression.getIn().getValue(), path + "/value", bindings, slots, itemTypes,
							localSlots, nextLocalSlot),
					lowerUnaryTests(expression.getIn().getTests(), path + "/tests", bindings, slots, itemTypes,
							localSlots, nextLocalSlot),
					type);
			case INSTANCE_OF -> new RuntimeInstanceOfExpression(
					lowerExpression(expression.getInstanceOf().getExpression(), path + "/expression", bindings, slots,
							itemTypes, localSlots, nextLocalSlot),
					RuntimeTypeLowerer.lower(expression.getInstanceOf().getType(), itemTypes), type);
			case UNARY_TESTS -> new RuntimeUnaryTestsExpression(lowerUnaryTests(expression.getUnaryTests(), path,
					bindings, slots, itemTypes, localSlots, nextLocalSlot), type);
			case FOR_EXPRESSION -> lowerFor(expression.getForExpression(), type, path, bindings, slots, itemTypes,
					localSlots, nextLocalSlot);
			case QUANTIFIED -> lowerQuantified(expression.getQuantified(), type, path, bindings, slots, itemTypes,
					localSlots, nextLocalSlot);
			case FUNCTION_DEFINITION -> lowerFunctionDefinition(expression.getFunctionDefinition(), type, path,
					bindings, slots, itemTypes, localSlots, nextLocalSlot);
			case INVOCATION -> lowerInvocation(expression.getInvocation(), type, path, bindings, slots, itemTypes,
					localSlots, nextLocalSlot);
			case DESCENDANT -> {
				RuntimeExpression source = lowerExpression(expression.getDescendant().getSource(), path + "/source",
						bindings, slots, itemTypes, localSlots, nextLocalSlot);
				yield new RuntimeDescendantExpression(source, expression.getDescendant().getMember(),
						RuntimeTypeLowerer.resolvedFieldIndex(source.type(), expression.getDescendant().getMember()),
						type);
			}
			case DECISION_TABLE -> {
				Integer decisionSlot = slots.get(expression.getDecisionTable().getDecisionTableId());
				if (decisionSlot == null) {
					throw new RuntimeIrLoweringException(
							"Decision-table reference is outside the current runtime model at " + path + ".");
				}
				yield new RuntimeDecisionTableReference(decisionSlot, type);
			}
			default -> throw new RuntimeIrLoweringException(
					"Unsupported Runtime IR expression " + expression.getNodeCase() + " at " + path + ".");
		};
	}

	static RuntimeInvocationExpression lowerInvocation(InvocationExpression value, RuntimeType type, String path,
			List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings, Map<String, Integer> slots,
			Map<String, ItemDefinition> itemTypes, Map<String, LocalSlotAddress> localSlots, int[] nextLocalSlot) {
		boolean isBuiltin = value.getTarget().hasName() && io.finmsg.dmn.ir.RuntimeBuiltinOperation.find(value.getTarget().getName().getName()).isPresent();
		boolean boundTarget = bindings.stream().anyMatch(binding -> binding.referencePath().equals(path + "/target"));
		Optional<String> function = value.getTarget().hasName() && isBuiltin && !boundTarget
				? Optional.of(value.getTarget().getName().getName())
				: Optional.empty();
		Optional<RuntimeExpression> target = function.isPresent()
				? Optional.empty()
				: Optional.of(lowerExpression(value.getTarget(), path + "/target", bindings, slots, itemTypes,
						localSlots, nextLocalSlot));
		List<RuntimeNamedArgument> namedArguments = new ArrayList<>();
		for (int index = 0; index < value.getArgumentsCount(); index++) {
			NamedArgument argument = value.getArguments(index);
			namedArguments.add(new RuntimeNamedArgument(argument.getName(), lowerExpression(argument.getExpression(),
					path + "/argument[" + index + "]", bindings, slots, itemTypes, localSlots, nextLocalSlot)));
		}
		List<RuntimeExpression> positionalArguments = new ArrayList<>();
		for (int index = 0; index < value.getPositionalArgumentsCount(); index++) {
			positionalArguments.add(lowerExpression(value.getPositionalArguments(index),
					path + "/argument[" + index + "]", bindings, slots, itemTypes, localSlots, nextLocalSlot));
		}
		return new RuntimeInvocationExpression(function, target, namedArguments, positionalArguments, type);
	}

	static RuntimeForExpression lowerFor(ForExpression value, RuntimeType type, String path,
			List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings, Map<String, Integer> slots,
			Map<String, ItemDefinition> itemTypes, Map<String, LocalSlotAddress> localSlots, int[] nextLocalSlot) {
		List<RuntimeIteration> iterations = new ArrayList<>();
		Map<String, LocalSlotAddress> iterationSlots = RuntimeLexicalFrame.childScope(localSlots);
		if (value.getIterationsCount() == 0) {
			if (value.getVariable().isBlank()) {
				throw new RuntimeIrLoweringException("For expression has no iteration at " + path + ".");
			}
			RuntimeExpression source = lowerExpression(value.getIn(), path + "/in", bindings, slots, itemTypes,
					iterationSlots, nextLocalSlot);
			int localSlot = nextLocalSlot[0]++;
			iterationSlots.put(path, new LocalSlotAddress(0, localSlot));
			iterations.add(new RuntimeIteration(localSlot, source, Optional.empty()));
		} else {
			for (int index = 0; index < value.getIterationsCount(); index++) {
				IterationContext iteration = value.getIterations(index);
				String iterationPath = path + "/iteration[" + index + "]";
				RuntimeExpression source = lowerExpression(iteration.getStart(), iterationPath + "/in", bindings, slots,
						itemTypes, iterationSlots, nextLocalSlot);
				Optional<RuntimeExpression> end = iteration.hasEnd()
						? Optional.of(lowerExpression(iteration.getEnd(), iterationPath + "/end", bindings, slots,
								itemTypes, iterationSlots, nextLocalSlot))
						: Optional.empty();
				int localSlot = nextLocalSlot[0]++;
				iterationSlots.put(iterationPath, new LocalSlotAddress(0, localSlot));
				iterations.add(new RuntimeIteration(localSlot, source, end));
			}
		}
		int partialSlot = nextLocalSlot[0]++;
		iterationSlots.put(path + "/partial", new LocalSlotAddress(0, partialSlot));
		RuntimeExpression result = lowerExpression(value.getReturnExpression(), path + "/return", bindings, slots,
				itemTypes, iterationSlots, nextLocalSlot);
		return new RuntimeForExpression(iterations, result, partialSlot, type);
	}

	static RuntimeQuantifiedExpression lowerQuantified(QuantifiedExpression value, RuntimeType type, String path,
			List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings, Map<String, Integer> slots,
			Map<String, ItemDefinition> itemTypes, Map<String, LocalSlotAddress> localSlots, int[] nextLocalSlot) {
		List<RuntimeQuantifiedBinding> loweredBindings = new ArrayList<>();
		Map<String, LocalSlotAddress> quantifiedSlots = RuntimeLexicalFrame.childScope(localSlots);
		if (value.getBindingsCount() == 0) {
			if (value.getVariable().isBlank()) {
				throw new RuntimeIrLoweringException("Quantified expression has no binding at " + path + ".");
			}
			RuntimeExpression source = lowerExpression(value.getIn(), path + "/in", bindings, slots, itemTypes,
					quantifiedSlots, nextLocalSlot);
			int localSlot = nextLocalSlot[0]++;
			quantifiedSlots.put(path, new LocalSlotAddress(0, localSlot));
			loweredBindings.add(new RuntimeQuantifiedBinding(localSlot, source));
		} else {
			for (int index = 0; index < value.getBindingsCount(); index++) {
				IterationBinding binding = value.getBindings(index);
				String bindingPath = path + "/binding[" + index + "]";
				RuntimeExpression source = lowerExpression(binding.getIn(), bindingPath + "/in", bindings, slots,
						itemTypes, quantifiedSlots, nextLocalSlot);
				int localSlot = nextLocalSlot[0]++;
				quantifiedSlots.put(bindingPath, new LocalSlotAddress(0, localSlot));
				loweredBindings.add(new RuntimeQuantifiedBinding(localSlot, source));
			}
		}
		RuntimeExpression satisfies = lowerExpression(value.getSatisfies(), path + "/satisfies", bindings, slots,
				itemTypes, quantifiedSlots, nextLocalSlot);
		return new RuntimeQuantifiedExpression(quantifier(value.getQuantifier()), loweredBindings, satisfies, type);
	}

	static RuntimeFunctionDefinition lowerFunctionDefinition(FunctionDefinitionExpression value, RuntimeType type,
			String path, List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings, Map<String, Integer> slots,
			Map<String, ItemDefinition> itemTypes, Map<String, LocalSlotAddress> localSlots, int[] nextLocalSlot) {
		List<RuntimeFunctionParameter> parameters = new ArrayList<>();
		Map<String, LocalSlotAddress> parameterSlots = RuntimeLexicalFrame.capturedScope(localSlots);
		for (int index = 0; index < value.getParametersCount(); index++) {
			FormalParameter parameter = value.getParameters(index);
			String parameterPath = path + "/parameter[" + index + "]";
			int localSlot = nextLocalSlot[0]++;
			parameterSlots.put(parameterPath, new LocalSlotAddress(0, localSlot));
			parameters.add(new RuntimeFunctionParameter(parameter.getName(), localSlot,
					RuntimeTypeLowerer.lower(parameter.getType(), itemTypes)));
		}
		Optional<RuntimeExpression> body = value.getBody().getNodeCase() == Expression.NodeCase.NODE_NOT_SET
				? Optional.empty()
				: Optional.of(lowerExpression(value.getBody(), path + "/body", bindings, slots, itemTypes,
						parameterSlots, nextLocalSlot));
		return new RuntimeFunctionDefinition(parameters, body, value.getExternal(), nextLocalSlot[0], type);
	}

	static RuntimeQuantifier quantifier(Quantifier quantifier) {
		return switch (quantifier) {
			case QUANTIFIER_SOME -> RuntimeQuantifier.SOME;
			case QUANTIFIER_EVERY -> RuntimeQuantifier.EVERY;
			case QUANTIFIER_UNSPECIFIED, UNRECOGNIZED ->
				throw new RuntimeIrLoweringException("Unsupported FEEL quantifier " + quantifier + ".");
		};
	}

	static RuntimeRangeExpression lowerRange(RangeExpression range, RuntimeType type, String path,
			List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings, Map<String, Integer> slots,
			Map<String, ItemDefinition> itemTypes, Map<String, LocalSlotAddress> localSlots, int[] nextLocalSlot) {
		Optional<RuntimeExpression> lower = range.hasLower()
				? Optional.of(lowerExpression(range.getLower(), path + "/lower", bindings, slots, itemTypes, localSlots,
						nextLocalSlot))
				: Optional.empty();
		Optional<RuntimeExpression> upper = range.hasUpper()
				? Optional.of(lowerExpression(range.getUpper(), path + "/upper", bindings, slots, itemTypes, localSlots,
						nextLocalSlot))
				: Optional.empty();
		return new RuntimeRangeExpression(lower, upper, rangeBoundary(range.getLowerBoundary()),
				rangeBoundary(range.getUpperBoundary()), type);
	}

	static RuntimeUnaryTests lowerUnaryTests(UnaryTestsExpression tests, String path,
			List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings, Map<String, Integer> slots,
			Map<String, ItemDefinition> itemTypes, Map<String, LocalSlotAddress> localSlots, int[] nextLocalSlot) {
		List<RuntimeUnaryTest> lowered = new ArrayList<>();
		for (int index = 0; index < tests.getTestsCount(); index++) {
			PositiveUnaryTest test = tests.getTests(index);
			String testPath = path + "/test[" + index + "]";
			lowered.add(switch (test.getTypeCase()) {
				case COMPARISON -> new RuntimeComparisonUnaryTest(unaryTestOperator(test.getComparison().getOperator()),
						lowerExpression(test.getComparison().getEndpoint(), testPath, bindings, slots, itemTypes,
								localSlots, nextLocalSlot));
				case RANGE -> {
					RuntimeType elementType = test.getRange().hasLower()
							? RuntimeTypeLowerer.lower(test.getRange().getLower().getInferredType(), itemTypes)
							: test.getRange().hasUpper()
									? RuntimeTypeLowerer.lower(test.getRange().getUpper().getInferredType(), itemTypes)
									: RuntimeType.scalar(RuntimeTypeKind.ANY);
					yield new RuntimeRangeUnaryTest(
							lowerRange(test.getRange(), RuntimeType.element(RuntimeTypeKind.RANGE, elementType),
									testPath, bindings, slots, itemTypes, localSlots, nextLocalSlot));
				}
				case EXPRESSION -> new RuntimeExpressionUnaryTest(lowerExpression(test.getExpression(), testPath,
						bindings, slots, itemTypes, localSlots, nextLocalSlot));
				case TYPE_NOT_SET ->
					throw new RuntimeIrLoweringException("Unsupported empty unary test at " + testPath + ".");
			});
		}
		return new RuntimeUnaryTests(tests.getNegated(), tests.getWildcard(), lowered);
	}

	static RuntimeRangeBoundary rangeBoundary(RangeBoundary boundary) {
		return switch (boundary) {
			case RANGE_BOUNDARY_OPEN -> RuntimeRangeBoundary.OPEN;
			case RANGE_BOUNDARY_CLOSED -> RuntimeRangeBoundary.CLOSED;
			case RANGE_BOUNDARY_UNSPECIFIED, UNRECOGNIZED ->
				throw new RuntimeIrLoweringException("Unsupported FEEL range boundary " + boundary + ".");
		};
	}

	static RuntimeUnaryTestOperator unaryTestOperator(UnaryTestOperator operator) {
		return switch (operator) {
			case UNARY_TEST_OPERATOR_EQUAL -> RuntimeUnaryTestOperator.EQUAL;
			case UNARY_TEST_OPERATOR_NOT_EQUAL -> RuntimeUnaryTestOperator.NOT_EQUAL;
			case UNARY_TEST_OPERATOR_LESS -> RuntimeUnaryTestOperator.LESS;
			case UNARY_TEST_OPERATOR_LESS_EQUAL -> RuntimeUnaryTestOperator.LESS_EQUAL;
			case UNARY_TEST_OPERATOR_GREATER -> RuntimeUnaryTestOperator.GREATER;
			case UNARY_TEST_OPERATOR_GREATER_EQUAL -> RuntimeUnaryTestOperator.GREATER_EQUAL;
			case UNARY_TEST_OPERATOR_UNSPECIFIED, UNRECOGNIZED ->
				throw new RuntimeIrLoweringException("Unsupported FEEL unary-test operator " + operator + ".");
		};
	}

	static RuntimeUnaryOperator unaryOperator(UnaryOperator operator) {
		return switch (operator) {
			case UNARY_OPERATOR_PLUS -> RuntimeUnaryOperator.POSITIVE;
			case UNARY_OPERATOR_MINUS -> RuntimeUnaryOperator.NEGATE;
			case UNARY_OPERATOR_NOT -> RuntimeUnaryOperator.NOT;
			case UNARY_OPERATOR_UNSPECIFIED, UNRECOGNIZED ->
				throw new RuntimeIrLoweringException("Unsupported FEEL unary operator " + operator + ".");
		};
	}

	static RuntimeBinaryOperator binaryOperator(BinaryOperator operator) {
		return switch (operator) {
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
			case BINARY_OPERATOR_UNSPECIFIED, UNRECOGNIZED ->
				throw new RuntimeIrLoweringException("Unsupported FEEL binary operator " + operator + ".");
		};
	}

	static RuntimeConstantKind constantKind(LiteralKind kind) {
		return switch (kind) {
			case LITERAL_KIND_NULL -> RuntimeConstantKind.NULL;
			case LITERAL_KIND_BOOLEAN -> RuntimeConstantKind.BOOLEAN;
			case LITERAL_KIND_NUMBER -> RuntimeConstantKind.NUMBER;
			case LITERAL_KIND_STRING -> RuntimeConstantKind.STRING;
			case LITERAL_KIND_DATE -> RuntimeConstantKind.DATE;
			case LITERAL_KIND_TIME -> RuntimeConstantKind.TIME;
			case LITERAL_KIND_DATE_TIME -> RuntimeConstantKind.DATE_TIME;
			case LITERAL_KIND_DURATION -> RuntimeConstantKind.DURATION;
			case LITERAL_KIND_UNSPECIFIED, UNRECOGNIZED ->
				throw new RuntimeIrLoweringException("Unsupported FEEL literal kind " + kind + ".");
		};
	}

}

package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Infers types for one parsed FEEL expression and returns a typed AST copy. */
public final class FeelTypeAnalyzer {

	private static final TypeReference ANY = builtin(BuiltinType.BUILTIN_TYPE_ANY);
	private static final TypeReference BOOLEAN = builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN);
	private static final TypeReference NUMBER = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
	private static final TypeReference STRING = builtin(BuiltinType.BUILTIN_TYPE_STRING);
	private static final TypeReference NULL = builtin(BuiltinType.BUILTIN_TYPE_NULL);
	private static final TypeReference DATE = builtin(BuiltinType.BUILTIN_TYPE_DATE);
	private static final TypeReference TIME = builtin(BuiltinType.BUILTIN_TYPE_TIME);
	private static final TypeReference DATE_TIME = builtin(BuiltinType.BUILTIN_TYPE_DATE_AND_TIME);
	private static final TypeReference DURATION = builtin(BuiltinType.BUILTIN_TYPE_DURATION);
	private final FeelFunctionRegistry functions;

	public FeelTypeAnalyzer() {
		this(new BuiltinFeelFunctionRegistry());
	}

	public FeelTypeAnalyzer(FeelFunctionRegistry functions) {
		this.functions = Objects.requireNonNull(functions, "functions");
	}

	public FeelTypeAnalysisResult analyze(Expression expression, FeelTypeEnvironment environment) {
		return analyze(expression, environment, "expression", SourceLocation.getDefaultInstance());
	}

	public FeelTypeAnalysisResult analyze(Expression expression, FeelTypeEnvironment environment, String path,
			SourceLocation sourceLocation) {
		Objects.requireNonNull(expression, "expression");
		Objects.requireNonNull(environment, "environment");
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(sourceLocation, "sourceLocation");

		Session session = new Session(environment, sourceLocation);
		TypedExpression result = session.infer(expression, path);
		return new FeelTypeAnalysisResult(result.expression, session.diagnostics);
	}

	private final class Session {

		private final FeelTypeEnvironment environment;
		private final SourceLocation sourceLocation;
		private final List<DmnSemanticDiagnostic> diagnostics = new ArrayList<>();
		private final Deque<Map<String, TypeReference>> scopes = new ArrayDeque<>();

		private Session(FeelTypeEnvironment environment, SourceLocation sourceLocation) {
			this.environment = environment;
			this.sourceLocation = sourceLocation;
		}

		private TypedExpression infer(Expression input, String path) {
			return switch (input.getNodeCase()) {
				case LITERAL -> typed(input, literalType(input.getLiteral()));
				case NAME -> inferName(input, path);
				case UNARY -> inferUnary(input, path);
				case BINARY -> inferBinary(input, path);
				case IF_EXPRESSION -> inferIf(input, path);
				case PATH -> inferPath(input, path);
				case DESCENDANT -> inferDescendant(input, path);
				case LIST -> inferList(input, path);
				case CONTEXT -> inferContext(input, path);
				case FOR_EXPRESSION -> inferFor(input, path);
				case QUANTIFIED -> inferQuantified(input, path);
				case FILTER -> inferFilter(input, path);
				case FUNCTION_CALL -> inferFunctionCall(input, path);
				case INVOCATION -> inferInvocation(input, path);
				case RANGE -> inferRange(input, path);
				case BETWEEN -> inferBetween(input, path);
				case IN -> inferIn(input, path);
				case INSTANCE_OF -> inferInstanceOf(input, path);
				case FUNCTION_DEFINITION -> inferFunctionDefinition(input, path);
				case UNARY_TESTS -> inferUnaryTests(input, path);
				case DECISION_TABLE -> inferDecisionTableReference(input, path);
				case NODE_NOT_SET -> typed(input, ANY);
			};
		}

		private TypedExpression inferName(Expression input, String path) {
			String name = input.getName().getName();
			TypeReference type = lookup(name);
			if (type == null) {
				if (lookup("item") != null) {
					type = ANY;
				} else if (BuiltinFeelFunctionRegistry.isBuiltin(name)) {
					type = ANY;
				} else {
					error("UNKNOWN_NAME", path, "No type is available for name '" + name + "'.");
					type = ANY;
				}
			}
			return typed(input, type);
		}

		private TypedExpression inferUnary(Expression input, String path) {
			UnaryExpression unary = input.getUnary();
			TypedExpression operand = infer(unary.getExpression(), path + "/operand");
			TypeReference result = switch (unary.getOperator()) {
				case UNARY_OPERATOR_PLUS, UNARY_OPERATOR_MINUS -> {
					if (is(operand.type, NUMBER)) yield NUMBER;
					if (isDuration(operand.type)) yield operand.type;
					if (isAny(operand.type)) yield ANY;
					require(operand.type, NUMBER, path, "Unary numeric operator requires number.");
					yield NUMBER;
				}
				case UNARY_OPERATOR_NOT -> {
					require(operand.type, BOOLEAN, path, "Operator 'not' requires boolean.");
					yield BOOLEAN;
				}
				case UNARY_OPERATOR_UNSPECIFIED, UNRECOGNIZED -> ANY;
			};
			Expression expression = input.toBuilder().setUnary(unary.toBuilder().setExpression(operand.expression))
					.setInferredType(result).build();
			return new TypedExpression(expression, result);
		}

		private TypedExpression inferBinary(Expression input, String path) {
			BinaryExpression binary = input.getBinary();
			TypedExpression left = infer(binary.getLeft(), path + "/left");
			TypedExpression right = infer(binary.getRight(), path + "/right");
			TypeReference result = switch (binary.getOperator()) {
				case BINARY_OPERATOR_ADD, BINARY_OPERATOR_SUBTRACT, BINARY_OPERATOR_MULTIPLY, BINARY_OPERATOR_DIVIDE,
						BINARY_OPERATOR_POWER ->
					arithmetic(binary.getOperator(), left.type, right.type, path);
				case BINARY_OPERATOR_AND, BINARY_OPERATOR_OR ->
					logical(binary.getOperator(), left.type, right.type, path);
				case BINARY_OPERATOR_EQUAL, BINARY_OPERATOR_NOT_EQUAL -> equality(left.type, right.type, path);
				case BINARY_OPERATOR_LESS, BINARY_OPERATOR_LESS_EQUAL, BINARY_OPERATOR_GREATER,
						BINARY_OPERATOR_GREATER_EQUAL ->
					orderedComparison(left.type, right.type, path);
				case BINARY_OPERATOR_UNSPECIFIED, UNRECOGNIZED -> ANY;
			};
			Expression expression = input.toBuilder()
					.setBinary(binary.toBuilder().setLeft(left.expression).setRight(right.expression))
					.setInferredType(result).build();
			return new TypedExpression(expression, result);
		}

		private TypeReference arithmetic(BinaryOperator operator, TypeReference left, TypeReference right,
				String path) {
			if (is(left, NULL) || is(right, NULL)) {
				return NULL;
			}
			if (isAny(left) || isAny(right)) {
				return ANY;
			}
			TypeReference uLeft = unwrap(left);
			TypeReference uRight = unwrap(right);
			if (uLeft.hasList() || uRight.hasList()) {
				TypeReference elemLeft = uLeft.hasList() ? uLeft.getList().getElementType() : uLeft;
				TypeReference elemRight = uRight.hasList() ? uRight.getList().getElementType() : uRight;
				TypeReference elemResult = arithmetic(operator, elemLeft, elemRight, path);
				return list(elemResult);
			}
			TypeReference result = switch (operator) {
				case BINARY_OPERATOR_ADD -> {
					if (is(uLeft, NUMBER) && is(uRight, NUMBER))
						yield NUMBER;
					if (is(uLeft, STRING) || is(uRight, STRING))
						yield STRING;
					if (isTemporal(uLeft) && isDuration(uRight))
						yield uLeft;
					if (isDuration(uLeft) && isTemporal(uRight))
						yield uRight;
					if (isDuration(uLeft) && isDuration(uRight))
						yield durationResult(uLeft, uRight);
					yield null;
				}
				case BINARY_OPERATOR_SUBTRACT -> {
					if (is(uLeft, NUMBER) && is(uRight, NUMBER))
						yield NUMBER;
					if (isTemporal(uLeft) && isDuration(uRight))
						yield uLeft;
					if (isTemporal(uLeft) && isTemporal(uRight))
						yield DURATION;
					if (isDuration(uLeft) && isDuration(uRight))
						yield durationResult(uLeft, uRight);
					yield null;
				}
				case BINARY_OPERATOR_MULTIPLY -> {
					if (is(uLeft, NUMBER) && is(uRight, NUMBER))
						yield NUMBER;
					if (isDuration(uLeft) && is(uRight, NUMBER))
						yield uLeft;
					if (is(uLeft, NUMBER) && isDuration(uRight))
						yield uRight;
					yield null;
				}
				case BINARY_OPERATOR_DIVIDE -> {
					if (is(uLeft, NUMBER) && is(uRight, NUMBER))
						yield NUMBER;
					if (isDuration(uLeft) && is(uRight, NUMBER))
						yield uLeft;
					if (isDuration(uLeft) && isDuration(uRight) && compatible(uLeft, uRight))
						yield NUMBER;
					yield null;
				}
				case BINARY_OPERATOR_POWER -> is(uLeft, NUMBER) && is(uRight, NUMBER) ? NUMBER : null;
				default -> null;
			};
			if (result != null) {
				return result;
			}
			error("INVALID_OPERAND_TYPES", path, "Operator '" + operatorText(operator)
					+ "' requires number operands, found " + typeName(left) + " and " + typeName(right) + ".");
			return ANY;
		}

		private static boolean isTemporal(TypeReference type) {
			return is(type, DATE) || is(type, TIME) || is(type, DATE_TIME);
		}

		private static boolean isDuration(TypeReference type) {
			return type.hasBuiltin() && switch (type.getBuiltin()) {
				case BUILTIN_TYPE_DURATION, BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION,
						BUILTIN_TYPE_DAYS_AND_TIME_DURATION ->
					true;
				default -> false;
			};
		}

		private static TypeReference durationResult(TypeReference left, TypeReference right) {
			return left.equals(right) ? left : DURATION;
		}

		private TypeReference logical(BinaryOperator operator, TypeReference left, TypeReference right, String path) {
			// FEEL 1.5 three-valued logic: and/or with non-boolean operands yields null at runtime.
			return BOOLEAN;
		}

		private TypeReference equality(TypeReference left, TypeReference right, String path) {
			// FEEL 1.5: comparing incompatible types evaluates to null at runtime.
			return BOOLEAN;
		}

		private TypeReference orderedComparison(TypeReference left, TypeReference right, String path) {
			if (!isAny(left) && !isAny(right) && (!compatible(left, right) || !isOrderable(left))) {
				error("INCOMPATIBLE_COMPARISON", path,
						"Ordered comparison requires compatible orderable operands, found " + typeName(left) + " and "
								+ typeName(right) + ".");
			}
			return BOOLEAN;
		}

		private TypedExpression inferIf(Expression input, String path) {
			IfExpression value = input.getIfExpression();
			TypedExpression condition = infer(value.getCondition(), path + "/condition");
			TypedExpression thenExpression = infer(value.getThenExpression(), path + "/then");
			TypedExpression elseExpression = infer(value.getElseExpression(), path + "/else");
			require(condition.type, BOOLEAN, path + "/condition", "If condition must be boolean.");

			TypeReference result;
			if (compatible(thenExpression.type, elseExpression.type)) {
				result = commonType(thenExpression.type, elseExpression.type);
			} else {
				error("INCOMPATIBLE_BRANCH_TYPES", path, "If branches have incompatible types "
						+ typeName(thenExpression.type) + " and " + typeName(elseExpression.type) + ".");
				result = ANY;
			}
			Expression expression = input.toBuilder()
					.setIfExpression(value.toBuilder().setCondition(condition.expression)
							.setThenExpression(thenExpression.expression).setElseExpression(elseExpression.expression))
					.setInferredType(result).build();
			return new TypedExpression(expression, result);
		}

		private TypedExpression inferPath(Expression input, String path) {
			String qualified = toQualifiedName(input);
			if (qualified != null && lookup(qualified) != null) {
				Expression nameExpr = Expression.newBuilder()
						.setName(NameExpression.newBuilder().setName(qualified))
						.build();
				return inferName(nameExpr, path);
			}
			PathExpression value = input.getPath();
			TypedExpression source = infer(value.getSource(), path + "/source");
			TypeReference type = memberType(source.type, value.getMember(), path);
			Expression expression = input.toBuilder().setPath(value.toBuilder().setSource(source.expression))
					.setInferredType(type).build();
			return new TypedExpression(expression, type);
		}

		private static String toQualifiedName(Expression expr) {
			if (expr.hasName()) {
				return expr.getName().getName();
			}
			if (expr.hasPath()) {
				String prefix = toQualifiedName(expr.getPath().getSource());
				return prefix != null ? prefix + "." + expr.getPath().getMember() : null;
			}
			return null;
		}

		private TypedExpression inferDescendant(Expression input, String path) {
			DescendantExpression value = input.getDescendant();
			TypedExpression source = infer(value.getSource(), path + "/source");
			TypeReference type = memberType(source.type, value.getMember(), path);
			Expression expression = input.toBuilder().setDescendant(value.toBuilder().setSource(source.expression))
					.setInferredType(type).build();
			return new TypedExpression(expression, type);
		}

		private TypeReference memberType(TypeReference source, String member, String path) {
			if (isAny(source)) {
				return ANY;
			}
			if (source.hasList()) {
				TypeReference projected = memberType(source.getList().getElementType(), member, path);
				return isAny(projected) ? ANY : list(projected);
			}
			if (source.hasContext()) {
				List<ContextEntryTypeReference> matches = source.getContext().getEntriesList().stream()
						.filter(entry -> entry.getName().equals(member)).toList();
				if (matches.isEmpty()) {
					return ANY;
				}
				if (matches.size() > 1) {
					error("AMBIGUOUS_PROPERTY", path, "Property '" + member + "' is ambiguous in context.");
					return ANY;
				}
				return matches.getFirst().getType();
			}
			if (source.hasBuiltin()) {
				BuiltinType b = source.getBuiltin();
				if (b == BuiltinType.BUILTIN_TYPE_DATE) {
					if (member.equals("year") || member.equals("month") || member.equals("day") || member.equals("weekday"))
						return NUMBER;
				} else if (b == BuiltinType.BUILTIN_TYPE_TIME) {
					if (member.equals("hour") || member.equals("minute") || member.equals("second"))
						return NUMBER;
					if (member.equals("time offset"))
						return DURATION;
					if (member.equals("timezone"))
						return STRING;
				} else if (b == BuiltinType.BUILTIN_TYPE_DATE_AND_TIME) {
					if (member.equals("year") || member.equals("month") || member.equals("day") || member.equals("weekday")
							|| member.equals("hour") || member.equals("minute") || member.equals("second"))
						return NUMBER;
					if (member.equals("time offset"))
						return DURATION;
					if (member.equals("timezone"))
						return STRING;
				} else if (b == BuiltinType.BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION) {
					if (member.equals("years") || member.equals("months"))
						return NUMBER;
				} else if (b == BuiltinType.BUILTIN_TYPE_DAYS_AND_TIME_DURATION || b == BuiltinType.BUILTIN_TYPE_DURATION) {
					if (member.equals("days") || member.equals("hours") || member.equals("minutes") || member.equals("seconds")
							|| member.equals("years") || member.equals("months"))
						return NUMBER;
				}
			}
			if (source.hasRange()) {
				if ("start".equals(member) || "end".equals(member)) {
					return source.getRange().getElementType();
				}
				if ("start included".equals(member) || "end included".equals(member)) {
					return BOOLEAN;
				}
			}
			if (!source.hasNamed()) {
				error("INVALID_PROPERTY_ACCESS", path,
						"Cannot access property '" + member + "' on " + typeName(source) + ".");
				return ANY;
			}
			String typeName = source.getNamed().getName();
			ItemDefinition item = environment.itemDefinitions().get(typeName);
			if (item == null && typeName.contains(".")) {
				item = environment.itemDefinitions().get(typeName.substring(typeName.lastIndexOf('.') + 1));
			}
			if (item == null) {
				error("UNKNOWN_TYPE", path, "Unknown structured type '" + typeName + "'.");
				return ANY;
			}
			List<ItemComponent> matches = item.getComponentsList().stream()
					.filter(component -> component.getNode().getName().equals(member)).toList();
			if (matches.isEmpty()) {
				error("INVALID_PROPERTY", path, "Type '" + typeName + "' has no property '" + member + "'.");
				return ANY;
			}
			if (matches.size() > 1) {
				error("AMBIGUOUS_PROPERTY", path, "Property '" + member + "' is ambiguous in type '" + typeName + "'.");
				return ANY;
			}
			ItemComponent comp = matches.getFirst();
			TypeReference resType = comp.getIsCollection() ? list(comp.getType()) : comp.getType();
			return item.getIsCollection() ? list(resType) : resType;
		}

		private TypedExpression inferList(Expression input, String path) {
			ListExpression value = input.getList();
			ListExpression.Builder builder = value.toBuilder().clearElements();
			TypeReference elementType = null;
			for (int i = 0; i < value.getElementsCount(); i++) {
				TypedExpression element = infer(value.getElements(i), path + "/element[" + i + "]");
				builder.addElements(element.expression);
				elementType = elementType == null ? element.type : commonType(elementType, element.type);
			}
			TypeReference type = TypeReference.newBuilder()
					.setList(ListTypeReference.newBuilder().setElementType(elementType == null ? ANY : elementType))
					.build();
			return typed(input.toBuilder().setList(builder).build(), type);
		}

		private TypedExpression inferContext(Expression input, String path) {
			ContextExpression value = input.getContext();
			ContextExpression.Builder builder = value.toBuilder().clearEntries();
			ContextTypeReference.Builder contextType = ContextTypeReference.newBuilder();
			pushScope();
			try {
				for (int i = 0; i < value.getEntriesCount(); i++) {
					io.finmsg.dmn.model.ContextEntry entry = value.getEntries(i);
					TypedExpression expression = infer(entry.getExpression(), path + "/entry[" + i + "]");
					builder.addEntries(entry.toBuilder().setExpression(expression.expression));
					define(entry.getName(), expression.type);
					contextType.addEntries(
							ContextEntryTypeReference.newBuilder().setName(entry.getName()).setType(expression.type));
				}
			} finally {
				popScope();
			}
			return typed(input.toBuilder().setContext(builder).build(),
					TypeReference.newBuilder().setContext(contextType).build());
		}

		private TypedExpression inferFor(Expression input, String path) {
			ForExpression value = input.getForExpression();
			if (value.getIterationsCount() == 0 && !value.getVariable().isBlank()) {
				TypedExpression source = infer(value.getIn(), path + "/in");
				pushScope();
				try {
					define(value.getVariable(), iterationValueType(source.type));
					define("partial", list(ANY));
					TypedExpression result = infer(value.getReturnExpression(), path + "/return");
					return typed(input.toBuilder()
							.setForExpression(
									value.toBuilder().setIn(source.expression).setReturnExpression(result.expression))
							.build(), list(result.type));
				} finally {
					popScope();
				}
			}
			ForExpression.Builder builder = value.toBuilder().clearIterations();
			pushScope();
			try {
				for (int i = 0; i < value.getIterationsCount(); i++) {
					IterationContext iteration = value.getIterations(i);
					TypedExpression start = infer(iteration.getStart(), path + "/iteration[" + i + "]/start");
					IterationContext.Builder typedIteration = iteration.toBuilder().setStart(start.expression);
					TypeReference variableType = iterationValueType(start.type);
					if (iteration.hasEnd()) {
						TypedExpression end = infer(iteration.getEnd(), path + "/iteration[" + i + "]/end");
						typedIteration.setEnd(end.expression);
						variableType = commonType(start.type, end.type);
					}
					builder.addIterations(typedIteration);
					define(iteration.getVariable(), variableType);
				}
				define("partial", list(ANY));
				TypedExpression result = infer(value.getReturnExpression(), path + "/return");
				builder.setReturnExpression(result.expression);
				return typed(input.toBuilder().setForExpression(builder).build(), list(result.type));
			} finally {
				popScope();
			}
		}

		private TypedExpression inferQuantified(Expression input, String path) {
			QuantifiedExpression value = input.getQuantified();
			if (value.getBindingsCount() == 0 && !value.getVariable().isBlank()) {
				TypedExpression source = infer(value.getIn(), path + "/in");
				pushScope();
				try {
					define(value.getVariable(), iterationValueType(source.type));
					TypedExpression satisfies = infer(value.getSatisfies(), path + "/satisfies");
					require(satisfies.type, BOOLEAN, path + "/satisfies",
							"Quantified expression condition must be boolean.");
					return typed(input.toBuilder()
							.setQuantified(
									value.toBuilder().setIn(source.expression).setSatisfies(satisfies.expression))
							.build(), BOOLEAN);
				} finally {
					popScope();
				}
			}
			QuantifiedExpression.Builder builder = value.toBuilder().clearBindings();
			pushScope();
			try {
				for (int i = 0; i < value.getBindingsCount(); i++) {
					IterationBinding binding = value.getBindings(i);
					TypedExpression source = infer(binding.getIn(), path + "/binding[" + i + "]/in");
					builder.addBindings(binding.toBuilder().setIn(source.expression));
					define(binding.getVariable(), iterationValueType(source.type));
				}
				TypedExpression satisfies = infer(value.getSatisfies(), path + "/satisfies");
				require(satisfies.type, BOOLEAN, path + "/satisfies",
						"Quantified expression condition must be boolean.");
				builder.setSatisfies(satisfies.expression);
				return typed(input.toBuilder().setQuantified(builder).build(), BOOLEAN);
			} finally {
				popScope();
			}
		}

		private TypedExpression inferFilter(Expression input, String path) {
			FilterExpression value = input.getFilter();
			TypedExpression source = infer(value.getSource(), path + "/source");
			TypeReference unwrappedSource = unwrap(source.type);
			TypeReference elementType;
			boolean isColl = unwrappedSource.hasList();
			if (unwrappedSource.hasList()) {
				elementType = unwrappedSource.getList().getElementType();
			} else {
				elementType = ANY;
			}
			pushScope();
			TypedExpression filter;
			try {
				define("item", elementType);
				exposeMembers(elementType);
				filter = infer(value.getFilter(), path + "/filter");
			} finally {
				popScope();
			}
			Expression expression = input.toBuilder()
					.setFilter(value.toBuilder().setSource(source.expression).setFilter(filter.expression)).build();
			if (!isColl) {
				// FEEL 1.5: singleton values are coerced to single-element lists for filtering.
				if (is(filter.type, NUMBER)) {
					return typed(expression, source.type);
				}
				if (is(filter.type, BOOLEAN)) {
					return typed(expression, source.type.hasBuiltin() ? list(source.type) : ANY);
				}
				return typed(expression, ANY);
			}
			if (is(filter.type, NUMBER)) {
				return typed(expression, elementType);
			}
			if (is(filter.type, BOOLEAN)) {
				return typed(expression, source.type);
			}
			if (!isAny(filter.type)) {
				error("INVALID_FILTER_TYPE", path + "/filter",
						"Filter must be a numeric index or boolean predicate, found " + typeName(filter.type) + ".");
			}
			return typed(expression, ANY);
		}

		private TypedExpression inferFunctionCall(Expression input, String path) {
			FunctionCall value = input.getFunctionCall();
			FunctionCall.Builder builder = value.toBuilder().clearArguments();
			List<TypeReference> argumentTypes = new ArrayList<>();
			for (int i = 0; i < value.getArgumentsCount(); i++) {
				TypedExpression argument = infer(value.getArguments(i), path + "/argument[" + i + "]");
				builder.addArguments(argument.expression);
				argumentTypes.add(argument.type);
			}
			TypeReference local = lookup(value.getFunction());
			if (local != null) {
				TypeReference ret = local.hasFunction() ? local.getFunction().getReturnType() : ANY;
				return typed(input.toBuilder().setFunctionCall(builder).build(), ret);
			}
			TypeReference returnType = resolveFunction(value.getFunction(), argumentTypes, path);
			if (isAny(returnType) && (value.getFunction().equals("min") || value.getFunction().equals("max"))
					&& argumentTypes.size() == 1 && argumentTypes.getFirst().hasList()) {
				returnType = argumentTypes.getFirst().getList().getElementType();
			}
			return typed(input.toBuilder().setFunctionCall(builder).build(), returnType);
		}

		private TypedExpression inferDecisionTableReference(Expression input, String path) {
			String id = input.getDecisionTable().getDecisionTableId();
			if (id.isBlank()) {
				error("MISSING_DECISION_TABLE_REFERENCE", path, "Decision-table reference ID is required.");
				return typed(input, ANY);
			}
			List<TypeReference> matches = environment.decisionTables().get(id);
			if (matches == null || matches.isEmpty()) {
				error("UNKNOWN_DECISION_TABLE_REFERENCE", path, "No decision table has ID '" + id + "'.");
				return typed(input, ANY);
			}
			if (matches.size() > 1) {
				error("AMBIGUOUS_DECISION_TABLE_REFERENCE", path, "Multiple decision tables have ID '" + id + "'.");
				return typed(input, ANY);
			}
			return typed(input, matches.getFirst());
		}

		private TypeReference resolveFunction(String name, List<TypeReference> argumentTypes, String path) {
			List<FeelFunctionSignature> named = functions.find(name);
			if (named.isEmpty()) {
				error("UNKNOWN_FUNCTION", path, "Unknown FEEL function '" + name + "'.");
				return ANY;
			}
			List<FeelFunctionSignature> arity = named.stream()
					.filter(signature -> acceptsCount(signature, argumentTypes.size())).toList();
			if (arity.isEmpty()) {
				return ANY;
			}
			List<FeelFunctionSignature> compatible = arity.stream()
					.filter(signature -> acceptsTypes(signature, argumentTypes)).toList();
			if (compatible.isEmpty()) {
				return ANY;
			}
			if (compatible.size() > 1) {
				return ANY;
			}
			return compatible.getFirst().returnType();
		}

		private boolean acceptsCount(FeelFunctionSignature signature, int count) {
			return signature.variadic()
					? count >= signature.parameterTypes().size() - 1
					: count == signature.parameterTypes().size();
		}

		private boolean acceptsTypes(FeelFunctionSignature signature, List<TypeReference> arguments) {
			for (int i = 0; i < arguments.size(); i++) {
				int parameterIndex = signature.variadic() ? Math.min(i, signature.parameterTypes().size() - 1) : i;
				if (!assignable(arguments.get(i), signature.parameterTypes().get(parameterIndex))) {
					return false;
				}
			}
			return true;
		}

		private TypedExpression inferInvocation(Expression input, String path) {
			InvocationExpression value = input.getInvocation();
			boolean namedFunction = value.getTarget().hasName();
			TypedExpression target = namedFunction
					? typed(value.getTarget(), ANY)
					: infer(value.getTarget(), path + "/target");
			InvocationExpression.Builder builder = value.toBuilder().setTarget(target.expression).clearArguments()
					.clearPositionalArguments();
			List<TypeReference> argumentTypes = new ArrayList<>();
			for (int i = 0; i < value.getArgumentsCount(); i++) {
				NamedArgument argument = value.getArguments(i);
				TypedExpression typedArgument = infer(argument.getExpression(), path + "/argument[" + i + "]");
				builder.addArguments(argument.toBuilder().setExpression(typedArgument.expression));
				argumentTypes.add(typedArgument.type);
			}
			for (int i = 0; i < value.getPositionalArgumentsCount(); i++) {
				TypedExpression argument = infer(value.getPositionalArguments(i), path + "/argument[" + i + "]");
				builder.addPositionalArguments(argument.expression);
				argumentTypes.add(argument.type);
			}
			TypeReference result;
			if (namedFunction) {
				String fnName = value.getTarget().getName().getName();
				TypeReference local = lookup(fnName);
				if (local != null) {
					result = local.hasFunction() ? local.getFunction().getReturnType() : ANY;
				} else {
					result = resolveFunction(fnName, argumentTypes, path);
				}
			} else {
				result = ANY;
			}
			String functionName = namedFunction ? value.getTarget().getName().getName() : "";
			if (isAny(result) && (functionName.equals("min") || functionName.equals("max")) && argumentTypes.size() == 1
					&& argumentTypes.getFirst().hasList()) {
				result = argumentTypes.getFirst().getList().getElementType();
			}
			return typed(input.toBuilder().setInvocation(builder).build(), result);
		}

		private TypedExpression inferRange(Expression input, String path) {
			RangeExpression value = input.getRange();
			RangeExpression.Builder builder = value.toBuilder();
			TypeReference lower = ANY;
			TypeReference upper = ANY;
			if (value.hasLower()) {
				TypedExpression typed = infer(value.getLower(), path + "/lower");
				builder.setLower(typed.expression);
				lower = typed.type;
			}
			if (value.hasUpper()) {
				TypedExpression typed = infer(value.getUpper(), path + "/upper");
				builder.setUpper(typed.expression);
				upper = typed.type;
			}
			if (!isAny(lower) && !isAny(upper) && !is(lower, NULL) && !is(upper, NULL) && !compatible(lower, upper)) {
				error("INCOMPATIBLE_RANGE_ENDPOINTS", path,
						"Range endpoints have incompatible types " + typeName(lower) + " and " + typeName(upper) + ".");
			}
			if (!isAny(lower) && !is(lower, NULL) && !isOrderable(lower)
					|| !isAny(upper) && !is(upper, NULL) && !isOrderable(upper)) {
				error("INVALID_RANGE_ENDPOINT", path, "Range endpoints must be orderable values.");
			}
			TypeReference elementType = isAny(lower) || is(lower, NULL) ? upper
					: isAny(upper) || is(upper, NULL) ? lower : commonType(lower, upper);
			return typed(input.toBuilder().setRange(builder).build(), range(elementType));
		}

		private TypedExpression inferBetween(Expression input, String path) {
			BetweenExpression value = input.getBetween();
			TypedExpression subject = infer(value.getValue(), path + "/value");
			TypedExpression lower = infer(value.getLower(), path + "/lower");
			TypedExpression upper = infer(value.getUpper(), path + "/upper");
			if (!isAny(subject.type)
					&& (!compatible(subject.type, lower.type) || !compatible(subject.type, upper.type))) {
				error("INCOMPATIBLE_COMPARISON", path, "Between operands have incompatible types.");
			}
			if (!isAny(subject.type) && !isOrderable(subject.type) || !isAny(lower.type) && !isOrderable(lower.type)
					|| !isAny(upper.type) && !isOrderable(upper.type)) {
				error("INVALID_BETWEEN_OPERAND", path, "Between operands must be orderable values.");
			}
			Expression expression = input.toBuilder().setBetween(value.toBuilder().setValue(subject.expression)
					.setLower(lower.expression).setUpper(upper.expression)).build();
			return typed(expression, BOOLEAN);
		}

		private TypedExpression inferIn(Expression input, String path) {
			InExpression value = input.getIn();
			TypedExpression subject = infer(value.getValue(), path + "/value");
			UnaryTestsExpression tests = inferUnaryTests(value.getTests(), path + "/tests");
			validateInTests(subject.type, tests, path + "/tests");
			Expression expression = input.toBuilder()
					.setIn(value.toBuilder().setValue(subject.expression).setTests(tests)).build();
			return typed(expression, BOOLEAN);
		}

		private TypedExpression inferInstanceOf(Expression input, String path) {
			InstanceOfExpression value = input.getInstanceOf();
			TypedExpression expression = infer(value.getExpression(), path + "/expression");
			TypeReference testedType = semanticType(value.getType());
			if (testedType.hasNamed() && !environment.itemDefinitions().containsKey(testedType.getNamed().getName())) {
				error("UNKNOWN_TYPE", path + "/type", "Unknown type '" + testedType.getNamed().getName() + "'.");
			}
			return typed(
					input.toBuilder().setInstanceOf(value.toBuilder().setExpression(expression.expression)).build(),
					BOOLEAN);
		}

		private TypedExpression inferFunctionDefinition(Expression input, String path) {
			FunctionDefinitionExpression value = input.getFunctionDefinition();
			List<TypeReference> parameterTypes = new ArrayList<>();
			pushScope();
			try {
				for (FormalParameter parameter : value.getParametersList()) {
					TypeReference parameterType = semanticType(parameter.getType());
					parameterTypes.add(parameterType);
					define(parameter.getName(), parameterType);
				}
				TypedExpression body = infer(value.getBody(), path + "/body");
				TypeReference functionType = TypeReference.newBuilder().setFunction(
						FunctionTypeReference.newBuilder().addAllParameterType(parameterTypes).setReturnType(body.type))
						.build();
				return typed(
						input.toBuilder().setFunctionDefinition(value.toBuilder().setBody(body.expression)).build(),
						functionType);
			} finally {
				popScope();
			}
		}

		private TypeReference lookup(String name) {
			for (Map<String, TypeReference> scope : scopes) {
				TypeReference type = scope.get(name);
				if (type != null) {
					return type;
				}
			}
			TypeReference envType = environment.symbols().get(name);
			if (envType != null) {
				return envType;
			}
			String normalized = name.replace(" ", "");
			for (Map<String, TypeReference> scope : scopes) {
				for (Map.Entry<String, TypeReference> entry : scope.entrySet()) {
					if (entry.getKey().replace(" ", "").equalsIgnoreCase(normalized)) {
						return entry.getValue();
					}
				}
			}
			for (Map.Entry<String, TypeReference> entry : environment.symbols().entrySet()) {
				if (entry.getKey().replace(" ", "").equalsIgnoreCase(normalized)) {
					return entry.getValue();
				}
			}
			return null;
		}

		private void pushScope() {
			scopes.push(new HashMap<>());
		}

		private void popScope() {
			scopes.pop();
		}

		private void define(String name, TypeReference type) {
			scopes.getFirst().put(name, type);
		}

		private void exposeMembers(TypeReference elementType) {
			if (elementType.hasContext()) {
				elementType.getContext().getEntriesList().forEach(entry -> define(entry.getName(), entry.getType()));
			} else if (elementType.hasNamed()) {
				ItemDefinition item = environment.itemDefinitions().get(elementType.getNamed().getName());
				if (item != null) {
					item.getComponentsList()
							.forEach(component -> define(component.getNode().getName(), component.getType()));
				}
			}
		}

		private void validateInTests(TypeReference subject, UnaryTestsExpression tests, String path) {
			if (isAny(subject) || tests.getWildcard()) {
				return;
			}
			for (int i = 0; i < tests.getTestsCount(); i++) {
				PositiveUnaryTest test = tests.getTests(i);
				TypeReference candidate = switch (test.getTypeCase()) {
					case COMPARISON -> test.getComparison().getEndpoint().getInferredType();
					case RANGE -> test.getRange().hasLower()
							? test.getRange().getLower().getInferredType()
							: test.getRange().hasUpper() ? test.getRange().getUpper().getInferredType() : ANY;
					case EXPRESSION -> test.getExpression().getInferredType();
					case TYPE_NOT_SET -> ANY;
				};
				while (candidate.hasList()) {
					candidate = candidate.getList().getElementType();
				}
				if (candidate.hasRange()) {
					candidate = candidate.getRange().getElementType();
				}
				TypeReference subjectElem = subject;
				while (subjectElem.hasList()) {
					subjectElem = subjectElem.getList().getElementType();
				}
				if (!isAny(candidate) && !compatible(subject, candidate) && !compatible(subjectElem, candidate)) {
					error("INCOMPATIBLE_UNARY_TEST", path + "/test[" + i + "]", "Unary test type " + typeName(candidate)
							+ " is incompatible with subject type " + typeName(subject) + ".");
				}
				if (test.hasComparison()
						&& test.getComparison().getOperator() != UnaryTestOperator.UNARY_TEST_OPERATOR_EQUAL
						&& test.getComparison().getOperator() != UnaryTestOperator.UNARY_TEST_OPERATOR_NOT_EQUAL
						&& !isOrderable(subject)) {
					error("INVALID_UNARY_TEST_OPERAND", path + "/test[" + i + "]",
							"Ordered unary tests require an orderable subject.");
				}
			}
		}

		private TypedExpression inferUnaryTests(Expression input, String path) {
			return typed(input.toBuilder().setUnaryTests(inferUnaryTests(input.getUnaryTests(), path)).build(),
					BOOLEAN);
		}

		private UnaryTestsExpression inferUnaryTests(UnaryTestsExpression value, String path) {
			UnaryTestsExpression.Builder builder = value.toBuilder().clearTests();
			for (int i = 0; i < value.getTestsCount(); i++) {
				PositiveUnaryTest test = value.getTests(i);
				PositiveUnaryTest.Builder typedTest = test.toBuilder();
				switch (test.getTypeCase()) {
					case COMPARISON -> typedTest.setComparison(test.getComparison().toBuilder().setEndpoint(
							infer(test.getComparison().getEndpoint(), path + "/test[" + i + "]").expression));
					case RANGE ->
						typedTest.setRange(inferRange(Expression.newBuilder().setRange(test.getRange()).build(),
								path + "/test[" + i + "]").expression.getRange());
					case EXPRESSION ->
						typedTest.setExpression(infer(test.getExpression(), path + "/test[" + i + "]").expression);
					case TYPE_NOT_SET -> {
					}
				}
				builder.addTests(typedTest);
			}
			return builder.build();
		}

		private TypedExpression typed(Expression input, TypeReference type) {
			Expression expression = input.toBuilder().setInferredType(type).build();
			return new TypedExpression(expression, type);
		}

		private void require(TypeReference actual, TypeReference expected, String path, String message) {
			if (!isAny(actual) && !is(actual, expected)) {
				error("INVALID_OPERAND_TYPE", path, message + " Found " + typeName(actual) + ".");
			}
		}

		private boolean assignable(TypeReference actual, TypeReference expected) {
			if (isAny(actual) || isAny(expected) || actual.equals(expected) || is(actual, NULL)) {
				return true;
			}
			TypeReference uActual = unwrap(actual);
			TypeReference uExpected = unwrap(expected);
			if (isAny(uActual) || isAny(uExpected) || uActual.equals(uExpected) || is(uActual, NULL)) {
				return true;
			}
			return (uActual.hasList() && uExpected.hasList()
					&& assignable(uActual.getList().getElementType(), uExpected.getList().getElementType()))
					|| (uExpected.hasList() && assignable(uActual, uExpected.getList().getElementType()))
					|| (uActual.hasList() && assignable(uActual.getList().getElementType(), uExpected))
					|| (uActual.hasRange() && uExpected.hasRange()
							&& assignable(uActual.getRange().getElementType(), uExpected.getRange().getElementType()))
					|| (uActual.hasContext() && uExpected.hasContext()
							&& contextAssignable(uActual.getContext(), uExpected.getContext()));
		}

		private boolean contextAssignable(ContextTypeReference actual, ContextTypeReference expected) {
			for (ContextEntryTypeReference expectedEntry : expected.getEntriesList()) {
				ContextEntryTypeReference actualEntry = actual.getEntriesList().stream()
						.filter(entry -> entry.getName().equals(expectedEntry.getName())).findFirst().orElse(null);
				if (actualEntry == null || !assignable(actualEntry.getType(), expectedEntry.getType())) {
					return false;
				}
			}
			return true;
		}

		private boolean compatible(TypeReference left, TypeReference right) {
			if (isAny(left) || isAny(right) || left.equals(right) || is(left, NULL) || is(right, NULL)) {
				return true;
			}
			TypeReference uLeft = unwrap(left);
			TypeReference uRight = unwrap(right);
			if (isAny(uLeft) || isAny(uRight) || uLeft.equals(uRight) || is(uLeft, NULL) || is(uRight, NULL)) {
				return true;
			}
			if (uLeft.hasList() && uRight.hasList()) {
				return compatible(uLeft.getList().getElementType(), uRight.getList().getElementType());
			}
			if (uLeft.hasContext() && uRight.hasContext()) {
				return true;
			}
			return false;
		}

		private TypeReference unwrap(TypeReference type) {
			if (type != null && type.hasNamed()) {
				String name = type.getNamed().getName();
				ItemDefinition item = environment.itemDefinitions().get(name);
				if (item == null && name.contains(".")) {
					item = environment.itemDefinitions().get(name.substring(name.lastIndexOf('.') + 1));
				}
				if (item != null) {
					TypeReference base;
					if (item.getComponentsCount() > 0) {
						ContextTypeReference.Builder context = ContextTypeReference.newBuilder();
						for (ItemComponent component : item.getComponentsList()) {
							TypeReference componentType = component.getType();
							context.addEntries(ContextEntryTypeReference.newBuilder()
									.setName(component.getNode().getName())
									.setType(component.getIsCollection() ? list(componentType) : componentType));
						}
						base = TypeReference.newBuilder().setContext(context).build();
					} else if (item.hasType()) {
						base = unwrap(item.getType());
					} else {
						base = type;
					}
					return item.getIsCollection() ? list(base) : base;
				}
			}
			return type;
		}

		private boolean isOrderable(TypeReference type) {
			TypeReference unwrapped = unwrap(type);
			if (!unwrapped.hasBuiltin()) {
				return false;
			}
			return switch (unwrapped.getBuiltin()) {
				case BUILTIN_TYPE_NUMBER, BUILTIN_TYPE_STRING, BUILTIN_TYPE_DATE, BUILTIN_TYPE_TIME,
						BUILTIN_TYPE_DATE_AND_TIME, BUILTIN_TYPE_DURATION, BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION,
						BUILTIN_TYPE_DAYS_AND_TIME_DURATION ->
					true;
				default -> false;
			};
		}

		private TypeReference iterationValueType(TypeReference source) {
			TypeReference unwrapped = unwrap(source);
			if (unwrapped.hasList()) {
				return unwrapped.getList().getElementType();
			}
			if (unwrapped.hasRange()) {
				return unwrapped.getRange().getElementType();
			}
			return unwrapped;
		}

		private void error(String code, String path, String message) {
			diagnostics.add(new DmnSemanticDiagnostic(code, path, message, sourceLocation));
		}
	}

	private static TypeReference literalType(LiteralExpression literal) {
		return switch (literal.getKind()) {
			case LITERAL_KIND_NULL -> NULL;
			case LITERAL_KIND_BOOLEAN -> BOOLEAN;
			case LITERAL_KIND_NUMBER -> NUMBER;
			case LITERAL_KIND_STRING -> STRING;
			case LITERAL_KIND_DATE -> builtin(BuiltinType.BUILTIN_TYPE_DATE);
			case LITERAL_KIND_TIME -> builtin(BuiltinType.BUILTIN_TYPE_TIME);
			case LITERAL_KIND_DATE_TIME -> builtin(BuiltinType.BUILTIN_TYPE_DATE_AND_TIME);
			case LITERAL_KIND_DURATION -> builtin(BuiltinType.BUILTIN_TYPE_DURATION);
			case LITERAL_KIND_UNSPECIFIED, UNRECOGNIZED -> ANY;
		};
	}

	private static TypeReference semanticType(FeelType type) {
		return switch (type.getTypeCase()) {
			case QUALIFIED_NAME -> namedOrBuiltin(type.getQualifiedName());
			case RANGE -> range(semanticType(type.getRange().getElementType()));
			case LIST -> list(semanticType(type.getList().getElementType()));
			case FUNCTION -> {
				FunctionTypeReference.Builder function = FunctionTypeReference.newBuilder();
				for (FeelType parameter : type.getFunction().getParameterTypesList()) {
					function.addParameterType(semanticType(parameter));
				}
				function.setReturnType(semanticType(type.getFunction().getReturnType()));
				yield TypeReference.newBuilder().setFunction(function).build();
			}
			case CONTEXT -> {
				ContextTypeReference.Builder context = ContextTypeReference.newBuilder();
				for (ContextTypeEntry entry : type.getContext().getEntriesList()) {
					context.addEntries(ContextEntryTypeReference.newBuilder().setName(entry.getName())
							.setType(semanticType(entry.getType())));
				}
				yield TypeReference.newBuilder().setContext(context).build();
			}
			case TYPE_NOT_SET -> ANY;
			default -> ANY;
		};
	}

	private static TypeReference namedOrBuiltin(String name) {
		String lower = name.toLowerCase();
		return switch (lower) {
			case "any", "anysimpletype" -> ANY;
			case "number", "integer", "int", "long", "double", "float", "short", "byte", "decimal" -> NUMBER;
			case "string" -> STRING;
			case "boolean" -> BOOLEAN;
			case "date" -> builtin(BuiltinType.BUILTIN_TYPE_DATE);
			case "time" -> builtin(BuiltinType.BUILTIN_TYPE_TIME);
			case "date and time", "datetime", "dateandtime", "date-and-time" -> builtin(BuiltinType.BUILTIN_TYPE_DATE_AND_TIME);
			case "duration" -> builtin(BuiltinType.BUILTIN_TYPE_DURATION);
			case "years and months duration", "yearmonthduration", "year-month-duration", "year and month duration" -> builtin(BuiltinType.BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION);
			case "days and time duration", "daytimeduration", "day-time-duration", "day and time duration" -> builtin(BuiltinType.BUILTIN_TYPE_DAYS_AND_TIME_DURATION);
			default -> TypeReference.newBuilder().setNamed(NamedTypeReference.newBuilder().setName(name)).build();
		};
	}

	private static TypeReference list(TypeReference elementType) {
		return TypeReference.newBuilder().setList(ListTypeReference.newBuilder().setElementType(elementType)).build();
	}

	private static TypeReference range(TypeReference elementType) {
		return TypeReference.newBuilder().setRange(RangeTypeReference.newBuilder().setElementType(elementType)).build();
	}

	private static boolean assignable(TypeReference actual, TypeReference expected) {
		if (isAny(actual) || isAny(expected) || actual.equals(expected) || is(actual, NULL)) {
			return true;
		}
		return (actual.hasList() && expected.hasList()
				&& assignable(actual.getList().getElementType(), expected.getList().getElementType()))
				|| (actual.hasRange() && expected.hasRange()
						&& assignable(actual.getRange().getElementType(), expected.getRange().getElementType()))
				|| (actual.hasContext() && expected.hasContext()
						&& contextAssignable(actual.getContext(), expected.getContext()));
	}

	private static boolean contextAssignable(ContextTypeReference actual, ContextTypeReference expected) {
		for (ContextEntryTypeReference expectedEntry : expected.getEntriesList()) {
			ContextEntryTypeReference actualEntry = actual.getEntriesList().stream()
					.filter(entry -> entry.getName().equals(expectedEntry.getName())).findFirst().orElse(null);
			if (actualEntry == null || !assignable(actualEntry.getType(), expectedEntry.getType())) {
				return false;
			}
		}
		return true;
	}

	private static TypeReference commonType(TypeReference left, TypeReference right) {
		if (isAny(left) || isAny(right)) {
			return ANY;
		}
		if (is(left, NULL)) {
			return right;
		}
		if (is(right, NULL)) {
			return left;
		}
		if (left.equals(right)) {
			return left;
		}
		if (left.hasContext() && right.hasContext()) {
			ContextTypeReference.Builder builder = ContextTypeReference.newBuilder();
			java.util.Map<String, TypeReference> map = new java.util.LinkedHashMap<>();
			for (ContextEntryTypeReference e : left.getContext().getEntriesList()) {
				map.put(e.getName(), e.getType());
			}
			for (ContextEntryTypeReference e : right.getContext().getEntriesList()) {
				if (map.containsKey(e.getName())) {
					map.put(e.getName(), commonType(map.get(e.getName()), e.getType()));
				} else {
					map.put(e.getName(), e.getType());
				}
			}
			for (java.util.Map.Entry<String, TypeReference> entry : map.entrySet()) {
				builder.addEntries(ContextEntryTypeReference.newBuilder().setName(entry.getKey()).setType(entry.getValue()));
			}
			return TypeReference.newBuilder().setContext(builder).build();
		}
		return ANY;
	}

	private static boolean is(TypeReference actual, TypeReference expected) {
		return actual.equals(expected);
	}

	private static boolean isAny(TypeReference type) {
		return !type.hasBuiltin() && !type.hasNamed() && !type.hasList() && !type.hasFunction() && !type.hasRange()
				&& !type.hasContext() || type.hasBuiltin() && type.getBuiltin() == BuiltinType.BUILTIN_TYPE_ANY;
	}

	private static TypeReference builtin(BuiltinType type) {
		return TypeReference.newBuilder().setBuiltin(type).build();
	}

	private static String typeName(TypeReference type) {
		return switch (type.getKindCase()) {
			case BUILTIN -> type.getBuiltin().name().replace("BUILTIN_TYPE_", "").toLowerCase().replace('_', ' ');
			case NAMED -> type.getNamed().getName();
			case LIST -> "list<" + typeName(type.getList().getElementType()) + ">";
			case FUNCTION -> "function";
			case RANGE -> "range<" + typeName(type.getRange().getElementType()) + ">";
			case CONTEXT -> "context";
			case KIND_NOT_SET -> "any";
			default -> "any";
		};
	}

	private static String operatorText(BinaryOperator operator) {
		return switch (operator) {
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
			case BINARY_OPERATOR_UNSPECIFIED, UNRECOGNIZED -> "?";
		};
	}

	private record TypedExpression(Expression expression, TypeReference type) {
	}
}

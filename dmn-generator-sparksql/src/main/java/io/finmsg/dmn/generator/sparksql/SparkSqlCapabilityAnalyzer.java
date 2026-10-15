package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Static decision-subgraph routing. No SQL execution, exception probing, or
 * null-result heuristics.
 */
public final class SparkSqlCapabilityAnalyzer {
	public record Capability(List<String> reasons, List<String> runtimeLimitations) {
		public Capability {
			reasons = List.copyOf(reasons);
			runtimeLimitations = List.copyOf(runtimeLimitations);
		}
		public boolean nativeSql() {
			return reasons.isEmpty() && runtimeLimitations.isEmpty();
		}
	}

	public Capability analyze(RuntimeModel model, int decisionId, boolean hybridBoundary) {
		Set<Integer> required = SparkSqlDecisionGraph.required(model, decisionId);
		Set<String> reasons = new LinkedHashSet<>();
		Set<String> limitations = new LinkedHashSet<>();
		Set<Integer> staticFunctions = new HashSet<>();
		model.businessKnowledgeModels().forEach(b -> staticFunctions.add(b.resultSlot()));
		model.decisions().stream().filter(d -> d.expression().orElse(null) instanceof RuntimeFunctionDefinition)
				.forEach(d -> staticFunctions.add(d.resultSlot()));
		for (RuntimeBkm bkm : model.businessKnowledgeModels()) {
			if (!required.contains(bkm.id()))
				continue;
			if (bkm.functionKind() != RuntimeFunctionKind.FEEL || bkm.function().isEmpty()) {
				limitations.add("External BKM " + bkm.id() + " is not supported by DmnRuntime");
			}
			inspect(bkm, hybridBoundary, reasons, staticFunctions);
		}
		Integer calendarSlot = hybridBoundary
				? model.decisions().stream().filter(d -> d.id() == decisionId)
						.map(d -> SparkSqlCalendarInput.inputSlot(model, d)).filter(Objects::nonNull).findFirst()
						.orElse(null)
				: null;
		for (RuntimeDecision decision : model.decisions()) {
			if (required.contains(decision.id()) && decision.id() != decisionId
					&& SparkSqlExpressionEmitter.nativeTerminalTemporalValue(decision) != null)
				reasons.add("Temporal ANY dependency requires lossless FEEL transport for its consumer");
			if (required.contains(decision.id()) && !(decision.id() == decisionId && (calendarSlot != null
					|| nativeConstantTemporalResult(decision) || nativeEmptyListResult(decision)
					|| SparkSqlExpressionEmitter.nativeTerminalTemporalValue(decision) != null)))
				inspect(decision, hybridBoundary, reasons, staticFunctions);
		}
		if (hybridBoundary) {
			for (RuntimeInput input : model.inputs()) {
				if (required.contains(input.id()) && !Objects.equals(calendarSlot, input.valueSlot())
						&& !SparkSqlFeelValueCodec.isNativeType(input.type())) {
					reasons.add("Input slot " + input.valueSlot() + " requires lossless FEEL transport");
				}
			}
		}
		return new Capability(new ArrayList<>(reasons), new ArrayList<>(limitations));
	}

	// An empty terminal result has no element values requiring lossless transport.
	// Restrict this to the requested decision; consumers still need their own
	// proof.
	static boolean nativeEmptyListResult(RuntimeDecision decision) {
		return decision.decisionTable().isEmpty()
				&& decision.expression().orElse(null) instanceof RuntimeListExpression list && list.elements().isEmpty()
				&& decision.type() != null
				&& Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.LIST).contains(decision.type().kind());
	}

	static boolean nativeConstantTemporalResult(RuntimeDecision decision) {
		if (!(decision.expression().orElse(null) instanceof RuntimeConstant constant) || decision.type() == null)
			return false;
		if (decision.decisionTable().isPresent())
			return false;
		if (nativeLocalTemporal(constant) || nativeDate(constant))
			return false;
		try {
			return switch (decision.type().kind()) {
				case DATE ->
					constant.kind() == RuntimeConstantKind.DATE && java.time.LocalDate.parse(constant.value()) != null;
				case TIME -> constant.kind() == RuntimeConstantKind.TIME
						&& io.finmsg.dmn.runtime.DmnRuntime.parseTime(constant.value()) != null;
				case DATE_TIME -> constant.kind() == RuntimeConstantKind.DATE_TIME
						&& io.finmsg.dmn.runtime.DmnRuntime.parseDateTime(constant.value()) != null;
				default -> false;
			};
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static final Map<String, String> NON_NATIVE_FUNCTIONS = Map.of("context",
			"Native context construction does not preserve heterogeneous struct values", "context put",
			"Native context updates require verified struct layouts and constant keys", "context merge",
			"Native map merging is incompatible with the context struct representation");

	private static void inspect(Object node, boolean hybrid, Set<String> reasons, Set<Integer> staticFunctions) {
		boolean yearMonthResult = node instanceof RuntimeDecision decision && nativeYearMonthDurationResult(decision);
		boolean durationResult = node instanceof RuntimeDecision decision
				&& (nativeConstantDurationResult(decision) || yearMonthResult);
		Set<RuntimeTypeKind> temporalKinds = EnumSet.noneOf(RuntimeTypeKind.class);
		SparkSqlPayloadCodec.walk(node, value -> {
			if (value instanceof RuntimeQuantifiedExpression quantified && nativeEmptyQuantified(quantified))
				return;
			if (value instanceof RuntimeExpression expression
					&& SparkSqlExpressionEmitter.nativeInvalidExpression(expression))
				return;
			if (value instanceof RuntimeExpression expression && SparkSqlRoundingProof.sql(expression) != null)
				return;
			if (value instanceof RuntimeExpression expression && SparkSqlRoundingProof.requiresFallback(expression))
				reasons.add("Rounding scale needs lossless evaluation beyond the verified native arithmetic bounds");
			if (value instanceof RuntimeExpression expression && expression.type() != null) {
				RuntimeTypeKind kind = expression.type().kind();
				if (value instanceof RuntimeConstant constant) {
					kind = switch (constant.kind()) {
						case DATE -> RuntimeTypeKind.DATE;
						case TIME -> RuntimeTypeKind.TIME;
						case DATE_TIME -> RuntimeTypeKind.DATE_TIME;
						default -> kind;
					};
				}
				if (Set.of(RuntimeTypeKind.DATE, RuntimeTypeKind.TIME, RuntimeTypeKind.DATE_TIME).contains(kind))
					temporalKinds.add(kind);
			}
			if (value instanceof RuntimeFunctionCall fc) {
				String fn = fc.function().toLowerCase(Locale.ROOT);
				inspectBooleanArguments(fn, fc.arguments(), reasons);
				inspectContextLookup(fn, fc.arguments(), reasons);
				inspectRoundingScale(fn, fc.arguments(), reasons);
				inspectStatistics(fn, fc.arguments(), reasons);
				if (fn.equals("matches") && !SparkSqlExpressionEmitter.nativeMatches(fc.arguments()))
					reasons.add("Native matches requires constant patterns and flags");
				if (fn.equals("date") && !nativeDate(fc))
					reasons.add(
							"Date requires lossless representation or constructor validation outside the verified native bounds");
				if (NON_NATIVE_FUNCTIONS.containsKey(fn)
						&& SparkSqlExpressionEmitter.nativeContextPutType(fc) == null) {
					reasons.add("Function " + fc.function() + ": " + NON_NATIVE_FUNCTIONS.get(fn));
				}
			}
			if (value instanceof RuntimeFunctionDefinition f && (f.external()
					|| f.type().returnType() != null && f.type().returnType().kind() == RuntimeTypeKind.FUNCTION)) {
				reasons.add(f.external() ? "External function" : "Function definition or closure");
			}
			if (value instanceof RuntimeLocalReference ref && ref.lexicalDepth() > 0)
				reasons.add("Captured lexical variable");
			if (value instanceof RuntimeInvocationExpression invocation) {
				if (invocation.function().isPresent()) {
					String fn = invocation.function().get().toLowerCase(Locale.ROOT);
					if (fn.equals("matches")
							&& !SparkSqlExpressionEmitter.nativeMatches(invocation.namedArguments().isEmpty()
									? invocation.positionalArguments()
									: SparkSqlExpressionEmitter.reorderNamedArguments(fn, invocation.namedArguments())))
						reasons.add("Native matches requires constant patterns and flags");
					if (fn.equals("date") && !nativeDate(new RuntimeFunctionCall(fn,
							invocation.namedArguments().isEmpty()
									? invocation.positionalArguments()
									: SparkSqlExpressionEmitter.reorderNamedArguments(fn, invocation.namedArguments()),
							invocation.type())))
						reasons.add(
								"Date requires lossless representation or constructor validation outside the verified native bounds");
					inspectStatistics(fn,
							invocation.namedArguments().isEmpty()
									? invocation.positionalArguments()
									: SparkSqlExpressionEmitter.reorderNamedArguments(fn, invocation.namedArguments()),
							reasons);
					inspectContextLookup(fn,
							invocation.namedArguments().isEmpty()
									? invocation.positionalArguments()
									: SparkSqlExpressionEmitter.reorderNamedArguments(fn, invocation.namedArguments()),
							reasons);
					inspectBooleanArguments(fn, invocation.positionalArguments(), reasons);
					inspectRoundingScale(fn,
							invocation.namedArguments().isEmpty()
									? invocation.positionalArguments()
									: SparkSqlExpressionEmitter.reorderNamedArguments(fn, invocation.namedArguments()),
							reasons);
					inspectBooleanArguments(fn,
							invocation.namedArguments().stream().map(RuntimeNamedArgument::expression).toList(),
							reasons);
					if (NON_NATIVE_FUNCTIONS.containsKey(fn)
							&& SparkSqlExpressionEmitter.nativeContextPutType(invocation) == null) {
						reasons.add("Function " + fn + ": " + NON_NATIVE_FUNCTIONS.get(fn));
					}
				}
				if (invocation.function().isEmpty() && (invocation.target().isEmpty()
						|| !staticTarget(invocation.target().get(), staticFunctions))) {
					reasons.add("Invocation requires Java function binding");
				}
			}
			if (value instanceof RuntimeDecisionTableReference)
				reasons.add("Decision-table function reference");
			if (value instanceof RuntimeListExpression list) {
				if (nativeContextListType(list) == null
						&& (list.elements().stream().map(RuntimeExpression::type).distinct().count() > 1
								|| list.type().elementType() == null
								|| list.type().elementType().kind() == RuntimeTypeKind.ANY)) {
					reasons.add("Heterogeneous or untyped list");
				}
			}
			if (hybrid && value instanceof RuntimeExpression expression) {
				RuntimeConstant property = expression instanceof RuntimePathExpression path
						? SparkSqlExpressionEmitter.nativeTemporalPropertyConstant(path)
						: null;
				if (expression.type() != null && expression.type().kind() != RuntimeTypeKind.ANY
						&& !(property != null && property.kind() == RuntimeConstantKind.NULL)
						&& !(expression instanceof RuntimeRangeExpression)
						&& !(expression instanceof RuntimeConstant c
								&& ("null".equals(c.value()) || c.kind() == RuntimeConstantKind.NULL))
						&& !SparkSqlFeelValueCodec.isNativeType(expression.type()) && !nativeDate(expression)
						&& !nativeLocalTemporal(expression) && !durationResult
						&& !(expression instanceof RuntimeListExpression list && nativeContextListType(list) != null)
						&& !(expression instanceof RuntimeFilterExpression filter
								&& nativeContextFilterResult(filter, expression.type()))
						&& SparkSqlExpressionEmitter.nativeContextPutType(expression) == null
						&& SparkSqlExpressionEmitter.nativeListReplaceType(expression) == null
						&& !(expression instanceof RuntimeContextExpression context && context.entries().isEmpty()))
					reasons.add("Expression requires lossless FEEL transport");
				if (!(expression instanceof RuntimeConstant || expression instanceof RuntimeValueReference
						|| (expression instanceof RuntimeLocalReference ref && ref.lexicalDepth() == 0)
						|| expression instanceof RuntimeConditionalExpression
						|| expression instanceof RuntimeBinaryExpression || expression instanceof RuntimeUnaryExpression
						|| expression instanceof RuntimeUnaryTestsExpression
						|| expression instanceof RuntimeRangeExpression
						|| expression instanceof RuntimeBetweenExpression || expression instanceof RuntimeInExpression
						|| expression instanceof RuntimePathExpression || expression instanceof RuntimeContextExpression
						|| expression instanceof RuntimeListExpression
						|| expression instanceof RuntimeInstanceOfExpression
						|| expression instanceof RuntimeForExpression loop && nativeFor(loop)
						|| expression instanceof RuntimeFilterExpression filter && nativeFilter(filter)
						|| expression instanceof RuntimeQuantifiedExpression quantified && nativeQuantified(quantified)
						|| expression instanceof RuntimeFunctionDefinition
						|| expression instanceof RuntimeInvocationExpression
						|| expression instanceof RuntimeFunctionCall)) {
					reasons.add("Native semantics not yet verified for " + expression.getClass().getSimpleName());
				}
			}
			if (hybrid && value instanceof RuntimeDecision decision) {
				if (decision.expression().filter(SparkSqlExpressionEmitter::nativeInvalidExpression).isPresent())
					return;
				if (decision.expression().orElse(null) instanceof RuntimeForExpression loop && nativeCartesianFor(loop)
						&& !compatibleCollectionResult(decision.type(), loop.type()))
					reasons.add("Cartesian loop declared-result coercion requires fallback");
				RuntimeListExpression contexts = decision.expression()
						.orElse(null) instanceof RuntimeListExpression list
								? list
								: decision.expression().orElse(null) instanceof RuntimeFilterExpression filter
										&& filter.source() instanceof RuntimeListExpression list ? list : null;
				RuntimeType contextListType = contexts == null ? null : nativeContextListType(contexts);
				if (contextListType != null && !compatibleCollectionResult(decision.type(), contextListType))
					reasons.add("Context list declared-result coercion requires fallback");
				RuntimeConstant absolute = decision.expression()
						.map(SparkSqlExpressionEmitter::nativeDurationAbsOperand).orElse(null);
				if (absolute != null && absolute.kind() != RuntimeConstantKind.NULL
						&& !nativeConstantDurationResult(decision))
					reasons.add("Native duration abs requires a compatible declared duration result type");
				if (decision.expression().orElse(null) instanceof RuntimePathExpression path) {
					RuntimeConstant property = SparkSqlExpressionEmitter.nativeTemporalPropertyConstant(path);
					if (property != null && property.kind() == RuntimeConstantKind.DURATION
							&& !nativeConstantDurationResult(decision))
						reasons.add("Native duration property requires a compatible declared result type");
				}
				RuntimeType updated = decision.expression().map(SparkSqlExpressionEmitter::nativeContextPutType)
						.orElse(null);
				if (decision.expression().map(SparkSqlExpressionEmitter::nativeListReplaceType).isPresent()
						&& decision.type().kind() != RuntimeTypeKind.ANY && !nativeListReplaceResult(decision))
					reasons.add("Native list replacement requires compatible declared-result coercion");
				if (updated != null && updated.kind() != RuntimeTypeKind.NULL
						&& decision.type().kind() != RuntimeTypeKind.ANY && !nativeContextPutResult(decision))
					reasons.add("Native context update result requires declared-type coercion");
				if (decision.type() != null && decision.type().kind() != RuntimeTypeKind.ANY
						&& !SparkSqlFeelValueCodec.isNativeType(decision.type())
						&& !nativeConstantDurationResult(decision) && !nativeYearMonthDurationResult(decision)
						&& !decision.expression()
								.filter(e -> e instanceof RuntimeFilterExpression filter
										&& nativeContextFilterResult(filter, decision.type()))
								.isPresent()
						&& !nativeContextPutResult(decision) && !nativeListReplaceResult(decision)
						&& !decision.expression()
								.filter(e -> e instanceof RuntimeConstant c && c.kind() == RuntimeConstantKind.NULL)
								.isPresent()
						&& !decision.expression().map(SparkSqlCapabilityAnalyzer::nativeDate).orElse(false)
						&& !(Set.of(RuntimeTypeKind.DATE_TIME, RuntimeTypeKind.TIME).contains(decision.type().kind())
								&& decision.expression().map(SparkSqlCapabilityAnalyzer::nativeLocalTemporal)
										.orElse(false)))
					reasons.add("Decision result requires lossless FEEL transport");
			}
		}, SparkSqlCapabilityAnalyzer::inspectChildren);
		if (hybrid && temporalKinds.size() > 1 && !yearMonthResult)
			reasons.add("Mixed temporal kinds require FEEL type identity checks before native timestamp comparison");
	}

	private static boolean nativeListReplaceResult(RuntimeDecision decision) {
		RuntimeType result = decision.expression().map(SparkSqlExpressionEmitter::nativeListReplaceType).orElse(null);
		return result != null && (result.kind() == RuntimeTypeKind.NULL || decision.type()
				.kind() == RuntimeTypeKind.LIST
				&& (decision.type().elementType() != null && decision.type().elementType().kind() == RuntimeTypeKind.ANY
						|| decision.type().equals(result)));
	}

	private static boolean nativeContextPutResult(RuntimeDecision decision) {
		RuntimeType result = decision.expression().map(SparkSqlExpressionEmitter::nativeContextPutType).orElse(null);
		return result != null
				&& (result.kind() == RuntimeTypeKind.NULL || decision.type().kind() == RuntimeTypeKind.CONTEXT
						&& (decision.type().fieldLayout().isEmpty() || decision.type().equals(result)));
	}

	private static boolean nativeYearMonthDurationResult(RuntimeDecision decision) {
		if (decision.type() == null || !Set.of(RuntimeTypeKind.DURATION, RuntimeTypeKind.YEARS_MONTHS_DURATION)
				.contains(decision.type().kind()))
			return false;
		RuntimeExpression expression = decision.expression().orElse(null);
		if (SparkSqlExpressionEmitter.nativeYearMonthArithmeticSql(expression) != null)
			return true;
		if (expression instanceof RuntimeInvocationExpression invocation && invocation.function().isPresent()) {
			if (!invocation.namedArguments().isEmpty() && (invocation.namedArguments().size() != 2
					|| !invocation.namedArguments().stream().map(RuntimeNamedArgument::name)
							.collect(java.util.stream.Collectors.toSet()).equals(Set.of("from", "to"))))
				return false;
			expression = new RuntimeFunctionCall(invocation.function().get(),
					invocation.namedArguments().isEmpty()
							? invocation.positionalArguments()
							: SparkSqlExpressionEmitter.reorderNamedArguments(invocation.function().get(),
									invocation.namedArguments()),
					invocation.type());
		}
		if (!(expression instanceof RuntimeFunctionCall call)
				|| !Set.of("years and months duration", "years_and_months_duration")
						.contains(call.function().toLowerCase(Locale.ROOT)))
			return false;
		if (SparkSqlExpressionEmitter.nativeYearMonthNull(call.arguments()))
			return true;
		if (SparkSqlExpressionEmitter.nativeStaticCalendarSql(call.arguments()) != null)
			return true;
		if (call.arguments().size() != 2)
			return false;
		return call.arguments().stream()
				.allMatch(arg -> arg.type() != null && (arg.type().kind() == RuntimeTypeKind.DATE && nativeDate(arg)
						|| arg.type().kind() == RuntimeTypeKind.DATE_TIME && nativeLocalTemporal(arg)));
	}

	private static boolean nativeConstantDurationResult(RuntimeDecision decision) {
		RuntimeExpression expression = decision.expression().orElse(null);
		RuntimeConstant absolute = SparkSqlExpressionEmitter.nativeDurationAbsOperand(expression);
		if (absolute != null) {
			if (absolute.kind() == RuntimeConstantKind.NULL)
				return true;
			expression = absolute;
		}
		if (expression instanceof RuntimePathExpression path)
			expression = SparkSqlExpressionEmitter.nativeTemporalPropertyConstant(path);
		if (expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.NULL
				&& decision.expression().orElse(null) instanceof RuntimePathExpression && decision.type() != null
				&& Set.of(RuntimeTypeKind.DURATION, RuntimeTypeKind.YEARS_MONTHS_DURATION,
						RuntimeTypeKind.DAYS_TIME_DURATION).contains(decision.type().kind()))
			return true;
		if (!(expression instanceof RuntimeConstant constant) || constant.kind() != RuntimeConstantKind.DURATION
				|| decision.type() == null)
			return false;
		Object duration = io.finmsg.dmn.runtime.DmnRuntime.parseDuration(constant.value());
		return switch (decision.type().kind()) {
			case DURATION -> duration != null;
			case YEARS_MONTHS_DURATION -> duration instanceof java.time.Period;
			case DAYS_TIME_DURATION -> duration instanceof java.time.Duration;
			default -> false;
		};
	}

	static boolean nativeFor(RuntimeForExpression loop) {
		if (nativeCartesianFor(loop))
			return true;
		if (loop.iterations().size() != 1 || loop.partialSlot() >= 0
				|| !SparkSqlFeelValueCodec.isNativeType(loop.type()))
			return false;
		var iteration = loop.iterations().getFirst();
		if (iteration.end().isEmpty())
			return iteration.source().type().kind() == RuntimeTypeKind.LIST
					&& SparkSqlFeelValueCodec.isNativeType(iteration.source().type());
		if (!(iteration.source() instanceof RuntimeConstant start) || start.kind() != RuntimeConstantKind.NUMBER
				|| !(iteration.end().get() instanceof RuntimeConstant end) || end.kind() != RuntimeConstantKind.NUMBER)
			return false;
		try {
			long first = new java.math.BigDecimal(start.value()).intValueExact();
			long last = new java.math.BigDecimal(end.value()).intValueExact();
			return Math.abs(last - first) < 10_000;
		} catch (ArithmeticException | NumberFormatException ignored) {
			return false;
		}
	}

	private static boolean nativeCartesianFor(RuntimeForExpression loop) {
		if (loop.iterations().size() < 2 || loop.iterations().size() > 4 || loop.partialSlot() >= 0
				|| loop.type().kind() != RuntimeTypeKind.LIST || !SparkSqlFeelValueCodec.isNativeType(loop.type()))
			return false;
		long size = 1;
		var slots = new HashSet<Integer>();
		var locals = new HashMap<Integer, CartesianBound>();
		for (var iteration : loop.iterations()) {
			if (iteration.localSlot() >= 2048 || !slots.add(iteration.localSlot()))
				return false;
			long count;
			CartesianBound bound;
			if (iteration.end().isPresent()) {
				if (!(iteration.source() instanceof RuntimeConstant start) || start.kind() != RuntimeConstantKind.NUMBER
						|| !(iteration.end().get() instanceof RuntimeConstant end)
						|| end.kind() != RuntimeConstantKind.NUMBER)
					return false;
				try {
					long first = new java.math.BigDecimal(start.value()).intValueExact();
					long last = new java.math.BigDecimal(end.value()).intValueExact();
					count = Math.abs(last - first) + 1;
					bound = new CartesianBound(RuntimeTypeKind.NUMBER, Math.max(Math.abs(first), Math.abs(last)));
				} catch (ArithmeticException | NumberFormatException exception) {
					return false;
				}
			} else {
				if (!(iteration.source() instanceof RuntimeListExpression list)
						|| !SparkSqlFeelValueCodec.isNativeType(list.type())
						|| !Set.of(RuntimeTypeKind.NUMBER, RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN)
								.contains(list.type().elementType().kind())
						|| list.elements().stream().anyMatch(e -> !(e instanceof RuntimeConstant constant)
								|| !Set.of(RuntimeConstantKind.NUMBER, RuntimeConstantKind.STRING,
										RuntimeConstantKind.BOOLEAN).contains(constant.kind())
								|| !constant.type().equals(list.type().elementType())
								|| !nativeSingletonFilterLiteral(constant) || !cartesianConstantMatchesType(constant)))
					return false;
				count = list.elements().size();
				long maximum = 0;
				for (var element : list.elements()) {
					if (element instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.NUMBER) {
						try {
							maximum = Math.max(maximum,
									Math.abs(new java.math.BigDecimal(constant.value()).longValueExact()));
						} catch (ArithmeticException | NumberFormatException exception) {
							return false;
						}
					}
				}
				bound = new CartesianBound(list.type().elementType().kind(), maximum);
			}
			if (count > 2048 || bound.maximum() < 0 || bound.maximum() > Integer.MAX_VALUE)
				return false;
			locals.put(iteration.localSlot(), bound);
			size *= count;
			if (size > 2048)
				return false;
		}
		var result = cartesianResultBound(loop.result(), locals, 0, new int[]{64});
		return result != null && result.kind() == loop.type().elementType().kind()
				&& (result.kind() != RuntimeTypeKind.LIST || loop.result().type().equals(loop.type().elementType()));
	}

	private record CartesianBound(RuntimeTypeKind kind, long maximum) {
	}

	private static CartesianBound cartesianResultBound(RuntimeExpression expression,
			Map<Integer, CartesianBound> locals, int depth, int[] budget) {
		if (depth > 8 || --budget[0] < 0)
			return null;
		if (expression instanceof RuntimeLocalReference local) {
			var bound = local.lexicalDepth() == 0 ? locals.get(local.localSlot()) : null;
			return bound != null && local.type().kind() == bound.kind() ? bound : null;
		}
		if (expression instanceof RuntimeConstant constant) {
			if (constant.kind() == RuntimeConstantKind.NUMBER) {
				if (!nativeSingletonFilterLiteral(constant))
					return null;
				try {
					long value = Math.abs(new java.math.BigDecimal(constant.value()).longValueExact());
					return value >= 0 && value <= Integer.MAX_VALUE
							? new CartesianBound(RuntimeTypeKind.NUMBER, value)
							: null;
				} catch (ArithmeticException | NumberFormatException exception) {
					return null;
				}
			}
			return switch (constant.kind()) {
				case STRING -> constant.value().length() <= 2048 ? new CartesianBound(RuntimeTypeKind.STRING, 0) : null;
				case BOOLEAN -> new CartesianBound(RuntimeTypeKind.BOOLEAN, 0);
				case NULL -> null;
				default -> null;
			};
		}
		if (expression instanceof RuntimeListExpression list && list.elements().size() <= 64
				&& SparkSqlFeelValueCodec.isNativeType(list.type())) {
			for (var element : list.elements()) {
				var bound = cartesianResultBound(element, locals, depth + 1, budget);
				if (bound == null || bound.kind() != list.type().elementType().kind()
						|| bound.kind() == RuntimeTypeKind.LIST && !element.type().equals(list.type().elementType()))
					return null;
			}
			return new CartesianBound(RuntimeTypeKind.LIST, 0);
		}
		if (!(expression instanceof RuntimeBinaryExpression binary))
			return null;
		var left = cartesianResultBound(binary.left(), locals, depth + 1, budget);
		var right = cartesianResultBound(binary.right(), locals, depth + 1, budget);
		if (left == null || right == null || left.kind() != right.kind())
			return null;
		if (left.kind() == RuntimeTypeKind.NUMBER) {
			long maximum = switch (binary.operator()) {
				case ADD, SUBTRACT -> left.maximum() + right.maximum();
				case MULTIPLY -> left.maximum() * right.maximum();
				default -> -1;
			};
			if (maximum >= 0)
				return maximum <= Integer.MAX_VALUE ? new CartesianBound(RuntimeTypeKind.NUMBER, maximum) : null;
			if (Set.of(RuntimeBinaryOperator.EQUAL, RuntimeBinaryOperator.NOT_EQUAL, RuntimeBinaryOperator.LESS,
					RuntimeBinaryOperator.LESS_EQUAL, RuntimeBinaryOperator.GREATER,
					RuntimeBinaryOperator.GREATER_EQUAL).contains(binary.operator()))
				return new CartesianBound(RuntimeTypeKind.BOOLEAN, 0);
		}
		if (left.kind() == RuntimeTypeKind.STRING && binary.operator() == RuntimeBinaryOperator.ADD)
			return new CartesianBound(RuntimeTypeKind.STRING, 0);
		if (Set.of(RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN).contains(left.kind())
				&& Set.of(RuntimeBinaryOperator.EQUAL, RuntimeBinaryOperator.NOT_EQUAL).contains(binary.operator())
				|| left.kind() == RuntimeTypeKind.BOOLEAN
						&& Set.of(RuntimeBinaryOperator.AND, RuntimeBinaryOperator.OR).contains(binary.operator()))
			return new CartesianBound(RuntimeTypeKind.BOOLEAN, 0);
		return null;
	}

	private static boolean cartesianConstantMatchesType(RuntimeConstant constant) {
		return switch (constant.kind()) {
			case NUMBER -> constant.type().kind() == RuntimeTypeKind.NUMBER;
			case BOOLEAN -> constant.type().kind() == RuntimeTypeKind.BOOLEAN;
			case STRING -> constant.type().kind() == RuntimeTypeKind.STRING && constant.value().length() <= 2048;
			default -> false;
		};
	}

	static boolean nativeFilter(RuntimeFilterExpression filter) {
		boolean booleanSelector = filter.filter().type().kind() == RuntimeTypeKind.BOOLEAN;
		if (filter.source() instanceof RuntimeConstant constant
				&& Set.of(RuntimeConstantKind.BOOLEAN, RuntimeConstantKind.STRING, RuntimeConstantKind.NUMBER)
						.contains(constant.kind()))
			return booleanSelector && nativeSingletonFilterLiteral(constant);
		if (filter.source() instanceof RuntimeListExpression list && nativeContextListType(list) != null)
			return booleanSelector;
		return filter.source() instanceof RuntimeListExpression list && SparkSqlFeelValueCodec.isNativeType(list.type())
				&& list.elements().stream()
						.allMatch(element -> element instanceof RuntimeConstant constant
								&& constant.kind() != RuntimeConstantKind.NULL
								|| nativeFilterContext(element, list.type().elementType()))
				&& booleanSelector;
	}

	private static boolean nativeContextFilterResult(RuntimeFilterExpression filter, RuntimeType declared) {
		if (!nativeFilter(filter) || !(filter.source() instanceof RuntimeListExpression list))
			return false;
		RuntimeType inferred = nativeContextListType(list);
		return inferred != null && compatibleCollectionResult(declared, inferred);
	}

	private static boolean compatibleCollectionResult(RuntimeType declared, RuntimeType inferred) {
		return declared != null && (declared.kind() == RuntimeTypeKind.ANY || declared.equals(inferred)
				|| declared.kind() == RuntimeTypeKind.LIST && declared.elementType() != null
						&& declared.elementType().kind() == RuntimeTypeKind.ANY);
	}

	// Null widens a field's values, without changing its name, position or
	// non-null FEEL kind. Different field sets and mixed non-null kinds stay
	// guarded.
	static RuntimeType nativeContextListType(RuntimeListExpression list) {
		if (list.type() == null || list.type().kind() != RuntimeTypeKind.LIST || list.type().elementType() == null
				|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.CONTEXT).contains(list.type().elementType().kind())
				|| list.elements().isEmpty() || list.elements().size() > 64
				|| !(list.elements().getFirst() instanceof RuntimeContextExpression first) || first.entries().isEmpty()
				|| first.entries().size() > 64)
			return null;
		var kinds = new RuntimeTypeKind[first.entries().size()];
		Arrays.fill(kinds, RuntimeTypeKind.NULL);
		var names = new HashSet<String>();
		for (var entry : first.entries()) {
			if (!names.add(entry.name()))
				return null;
		}
		for (var element : list.elements()) {
			if (!(element instanceof RuntimeContextExpression context) || context.entries().size() != kinds.length
					|| context.type().kind() != RuntimeTypeKind.CONTEXT
					|| context.type().fieldLayout().size() != kinds.length)
				return null;
			for (int index = 0; index < kinds.length; index++) {
				var entry = context.entries().get(index);
				var field = context.type().fieldLayout().get(index);
				if (!entry.name().equals(first.entries().get(index).name()) || !field.name().equals(entry.name())
						|| !(entry.expression() instanceof RuntimeConstant constant))
					return null;
				RuntimeTypeKind kind = switch (constant.kind()) {
					case NUMBER -> RuntimeTypeKind.NUMBER;
					case STRING -> RuntimeTypeKind.STRING;
					case BOOLEAN -> RuntimeTypeKind.BOOLEAN;
					case NULL -> RuntimeTypeKind.NULL;
					default -> null;
				};
				if (kind == null || kind != RuntimeTypeKind.NULL
						&& (!constant.type().equals(RuntimeType.scalar(kind)) || !field.type().equals(constant.type())))
					return null;
				if (kind == RuntimeTypeKind.NUMBER) {
					try {
						var number = new java.math.BigDecimal(constant.value()).stripTrailingZeros();
						if (!nativeSingletonFilterLiteral(constant) || number.scale() > 0
								|| (long) number.precision() - number.scale() > 15)
							return null;
					} catch (NumberFormatException exception) {
						return null;
					}
				}
				if (kind != RuntimeTypeKind.NULL) {
					if (kinds[index] != RuntimeTypeKind.NULL && kinds[index] != kind)
						return null;
					kinds[index] = kind;
				}
			}
		}
		var fields = new ArrayList<RuntimeField>();
		for (int index = 0; index < kinds.length; index++)
			fields.add(new RuntimeField(index, first.entries().get(index).name(), RuntimeType.scalar(kinds[index])));
		return RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType.contextFields(fields));
	}

	private static boolean nativeFilterContext(RuntimeExpression expression, RuntimeType elementType) {
		if (!(expression instanceof RuntimeContextExpression context) || !context.type().equals(elementType)
				|| !SparkSqlFeelValueCodec.isNativeType(elementType) || context.entries().isEmpty()
				|| context.entries().size() > 64 || context.entries().size() != elementType.fieldLayout().size())
			return false;
		for (int index = 0; index < context.entries().size(); index++) {
			var entry = context.entries().get(index);
			var field = elementType.fieldLayout().get(index);
			if (!entry.name().equals(field.name()) || !(entry.expression() instanceof RuntimeConstant constant)
					|| !Set.of(RuntimeConstantKind.BOOLEAN, RuntimeConstantKind.STRING, RuntimeConstantKind.NUMBER,
							RuntimeConstantKind.NULL).contains(constant.kind()))
				return false;
			if (constant.kind() == RuntimeConstantKind.NUMBER) {
				try {
					var value = new java.math.BigDecimal(constant.value()).stripTrailingZeros();
					if (!nativeSingletonFilterLiteral(constant) || value.scale() > 0
							|| (long) value.precision() - value.scale() > 15)
						return false;
				} catch (NumberFormatException ignored) {
					return false;
				}
			}
			if (constant.kind() != RuntimeConstantKind.NULL && !constant.type().equals(field.type()))
				return false;
		}
		return true;
	}

	private static boolean nativeSingletonFilterLiteral(RuntimeConstant constant) {
		if (constant.kind() != RuntimeConstantKind.NUMBER)
			return true;
		// Exponent literals are parsed as floating point by Spark, not exact decimals.
		if (constant.value().contains("e") || constant.value().contains("E"))
			return false;
		try {
			var value = new java.math.BigDecimal(constant.value());
			return value.scale() >= 0 && value.scale() <= 38 && Math.max(value.precision(), value.scale()) <= 38;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	static boolean nativeEmptyQuantified(RuntimeQuantifiedExpression quantified) {
		return quantified.bindings().size() == 1
				&& quantified.bindings().getFirst().source() instanceof RuntimeListExpression list
				&& list.elements().isEmpty();
	}

	static boolean nativeQuantified(RuntimeQuantifiedExpression quantified) {
		return quantified.bindings().size() == 1
				&& quantified.bindings().getFirst().source() instanceof RuntimeListExpression list
				&& SparkSqlFeelValueCodec.isNativeType(list.type())
				&& quantified.satisfies().type().kind() == RuntimeTypeKind.BOOLEAN;
	}

	private static boolean inspectChildren(Object node) {
		if (node instanceof RuntimeExpression expression && SparkSqlRoundingProof.sql(expression) != null)
			return false;
		if (node instanceof RuntimeInstanceOfExpression instance && SparkSqlStaticTypeProof.sql(instance) != null)
			return false;
		if (node instanceof RuntimeListExpression list && nativeContextListType(list) != null)
			return false;
		if (node instanceof RuntimeExpression expression
				&& SparkSqlExpressionEmitter.nativeDurationAbsOperand(expression) != null)
			return false;
		if (node instanceof RuntimeInstanceOfExpression instance
				&& SparkSqlExpressionEmitter.nativeTemporalInstanceOfSql(instance) != null)
			return false;
		if (node instanceof RuntimeBetweenExpression between
				&& SparkSqlExpressionEmitter.nativeDurationBetweenSql(between) != null)
			return false;
		if (node instanceof RuntimeExpression expression
				&& SparkSqlExpressionEmitter.nativeListReplaceMatchType(expression) != null)
			return false;
		if (node instanceof RuntimeQuantifiedExpression quantified && nativeEmptyQuantified(quantified))
			return false;
		if (node instanceof RuntimeExpression expression
				&& SparkSqlExpressionEmitter.nativeInvalidExpression(expression))
			return false;
		if (node instanceof RuntimeBinaryExpression binary
				&& SparkSqlExpressionEmitter.nativeStaticContextPredicate(binary) != null)
			return false;
		if (node instanceof RuntimeExpression expression
				&& SparkSqlExpressionEmitter.nativeStaticContextExpression(expression) != null)
			return false;
		if (node instanceof RuntimePathExpression path
				&& SparkSqlExpressionEmitter.nativeTemporalPropertyConstant(path) != null)
			return false;
		if (node instanceof RuntimeInvocationExpression invocation && invocation.function().isPresent()
				&& Set.of("years and months duration", "years_and_months_duration")
						.contains(invocation.function().get().toLowerCase(Locale.ROOT))
				&& invocation.namedArguments().size() == 2
				&& invocation.namedArguments().stream().map(RuntimeNamedArgument::name)
						.collect(java.util.stream.Collectors.toSet()).equals(Set.of("from", "to"))) {
			var args = SparkSqlExpressionEmitter.reorderNamedArguments(invocation.function().get(),
					invocation.namedArguments());
			return !SparkSqlExpressionEmitter.nativeYearMonthNull(args)
					&& SparkSqlExpressionEmitter.nativeStaticCalendarSql(args) == null;
		}
		if (node instanceof RuntimeFunctionCall call && Set.of("years and months duration", "years_and_months_duration")
				.contains(call.function().toLowerCase(Locale.ROOT)))
			return !SparkSqlExpressionEmitter.nativeYearMonthNull(call.arguments())
					&& SparkSqlExpressionEmitter.nativeStaticCalendarSql(call.arguments()) == null;
		if (node instanceof RuntimeInvocationExpression invocation && invocation.namedArguments().isEmpty()
				&& invocation.function().filter(fn -> Set.of("years and months duration", "years_and_months_duration")
						.contains(fn.toLowerCase(Locale.ROOT))).isPresent())
			return !SparkSqlExpressionEmitter.nativeYearMonthNull(invocation.positionalArguments())
					&& SparkSqlExpressionEmitter.nativeStaticCalendarSql(invocation.positionalArguments()) == null;
		if (node instanceof RuntimeExpression expression) {
			RuntimeType updated = SparkSqlExpressionEmitter.nativeContextPutType(expression);
			if (updated != null && updated.kind() == RuntimeTypeKind.NULL)
				return false;
		}
		if (node instanceof RuntimeFunctionCall call && call.function().equalsIgnoreCase("date and time"))
			return !SparkSqlExpressionEmitter.nativeDateTimeNull(call.arguments());
		if (node instanceof RuntimeInvocationExpression invocation && invocation.namedArguments().isEmpty()
				&& invocation.function().filter("date and time"::equalsIgnoreCase).isPresent())
			return !SparkSqlExpressionEmitter.nativeDateTimeNull(invocation.positionalArguments());
		if (node instanceof RuntimeFunctionCall call && call.function().equalsIgnoreCase("is")
				&& SparkSqlExpressionEmitter.nativeStaticIsSql(call.arguments()) != null)
			return false;
		if (node instanceof RuntimeInvocationExpression invocation && invocation.function().isPresent()
				&& invocation.function().get().equalsIgnoreCase("is")
				&& SparkSqlExpressionEmitter.nativeStaticIsSql(invocation.namedArguments().isEmpty()
						? invocation.positionalArguments()
						: SparkSqlExpressionEmitter.reorderNamedArguments("is", invocation.namedArguments())) != null)
			return false;
		if (node instanceof RuntimeInstanceOfExpression expression
				&& SparkSqlExpressionEmitter.nativeDurationInstanceOfSql(expression) != null)
			return false;
		if (node instanceof RuntimeInExpression expression && SparkSqlExpressionEmitter.nativeMembership(expression))
			return false;
		if (node instanceof RuntimeFunctionCall call && call.function().equalsIgnoreCase("time"))
			return !SparkSqlExpressionEmitter.nativeTimeNull(call.arguments());
		if (node instanceof RuntimeInvocationExpression invocation && invocation.namedArguments().isEmpty()
				&& invocation.function().filter("time"::equalsIgnoreCase).isPresent())
			return !SparkSqlExpressionEmitter.nativeTimeNull(invocation.positionalArguments());
		// Invalid matches signatures emit SQL NULL without evaluating their arguments.
		if (node instanceof RuntimeFunctionCall call && call.function().equalsIgnoreCase("matches"))
			return !SparkSqlExpressionEmitter.invalidMatchesArguments(call.arguments());
		if (node instanceof RuntimeInvocationExpression invocation
				&& invocation.function().filter("matches"::equalsIgnoreCase).isPresent())
			return !SparkSqlExpressionEmitter.invalidMatchesArguments(invocation.namedArguments().isEmpty()
					? invocation.positionalArguments()
					: SparkSqlExpressionEmitter.reorderNamedArguments("matches", invocation.namedArguments()));
		return true;
	}

	private static boolean nativeDate(RuntimeExpression expression) {
		try {
			java.time.LocalDate date;
			if (expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.DATE) {
				date = java.time.LocalDate.parse(constant.value());
			} else if (expression instanceof RuntimeFunctionCall call && call.function().equalsIgnoreCase("date")) {
				List<RuntimeExpression> args = call.arguments();
				if (args.size() == 1 && args.get(0) instanceof RuntimeConstant constant
						&& constant.kind() == RuntimeConstantKind.STRING) {
					date = java.time.LocalDate.parse(constant.value());
				} else if (args.size() == 1 && args.get(0).type().kind() != RuntimeTypeKind.LIST
						&& nativeDate(args.get(0))) {
					return true;
				} else if (args.size() == 3 && args.stream().allMatch(arg -> arg instanceof RuntimeConstant constant
						&& constant.kind() == RuntimeConstantKind.NUMBER)) {
					int year = new java.math.BigDecimal(((RuntimeConstant) args.get(0)).value()).intValueExact();
					int month = new java.math.BigDecimal(((RuntimeConstant) args.get(1)).value()).intValueExact();
					int day = new java.math.BigDecimal(((RuntimeConstant) args.get(2)).value()).intValueExact();
					date = java.time.LocalDate.of(year, month, day);
				} else {
					return false;
				}
			} else if (expression instanceof RuntimeInvocationExpression invocation
					&& invocation.function().filter("date"::equalsIgnoreCase).isPresent()) {
				return nativeDate(new RuntimeFunctionCall("date",
						invocation.namedArguments().isEmpty()
								? invocation.positionalArguments()
								: SparkSqlExpressionEmitter.reorderNamedArguments("date", invocation.namedArguments()),
						invocation.type()));
			} else if (expression instanceof RuntimeListExpression list && !list.elements().isEmpty()) {
				return list.elements().stream().allMatch(SparkSqlCapabilityAnalyzer::nativeDate);
			} else {
				return false;
			}
			// Keep historical-calendar and extreme-year cases in lossless evaluation.
			return date.getYear() >= 1583 && date.getYear() <= 9999;
		} catch (java.time.DateTimeException | ArithmeticException ignored) {
			return false;
		}
	}

	private static boolean nativeLocalTemporal(RuntimeExpression expression) {
		try {
			if (expression instanceof RuntimeConstant constant) {
				if (constant.kind() == RuntimeConstantKind.NULL)
					return true;
				if (constant.kind() == RuntimeConstantKind.TIME)
					return java.time.LocalTime.parse(constant.value()).getNano() == 0;
				if (constant.kind() == RuntimeConstantKind.DATE_TIME) {
					var dateTime = java.time.LocalDateTime.parse(constant.value());
					return dateTime.getYear() >= 1583 && dateTime.getYear() <= 9999 && dateTime.getNano() == 0;
				}
			} else if (expression instanceof RuntimeFunctionCall call) {
				String function = call.function().toLowerCase(Locale.ROOT);
				List<RuntimeExpression> args = call.arguments();
				if (function.equals("date and time") && SparkSqlExpressionEmitter.nativeDateTimeNull(args))
					return true;
				if (function.equals("time") && SparkSqlExpressionEmitter.nativeTimeNull(args))
					return true;
				if (args.size() == 1 && args.get(0) instanceof RuntimeConstant constant
						&& constant.kind() == RuntimeConstantKind.STRING) {
					if (function.equals("time"))
						return java.time.LocalTime.parse(constant.value()).getNano() == 0;
					if (function.equals("date and time")) {
						var dateTime = java.time.LocalDateTime.parse(constant.value());
						return dateTime.getYear() >= 1583 && dateTime.getYear() <= 9999 && dateTime.getNano() == 0;
					}
				}
				if (function.equals("time") && args.size() == 3
						&& args.stream().allMatch(arg -> arg instanceof RuntimeConstant constant
								&& constant.kind() == RuntimeConstantKind.NUMBER)) {
					java.time.LocalTime.of(
							new java.math.BigDecimal(((RuntimeConstant) args.get(0)).value()).intValueExact(),
							new java.math.BigDecimal(((RuntimeConstant) args.get(1)).value()).intValueExact(),
							new java.math.BigDecimal(((RuntimeConstant) args.get(2)).value()).intValueExact());
					return true;
				}
			} else if (expression instanceof RuntimeInvocationExpression invocation
					&& invocation.function().isPresent()) {
				String function = invocation.function().get();
				return nativeLocalTemporal(new RuntimeFunctionCall(function, invocation.namedArguments().isEmpty()
						? invocation.positionalArguments()
						: SparkSqlExpressionEmitter.reorderNamedArguments(function, invocation.namedArguments()),
						invocation.type()));
			} else if (expression instanceof RuntimeListExpression list && !list.elements().isEmpty()) {
				return list.elements().stream().allMatch(SparkSqlCapabilityAnalyzer::nativeLocalTemporal);
			}
			return false;
		} catch (java.time.DateTimeException | ArithmeticException ignored) {
			return false;
		}
	}

	private static void inspectStatistics(String function, List<RuntimeExpression> arguments, Set<String> reasons) {
		if (Set.of("median", "mode", "stddev").contains(function)
				&& arguments.stream().anyMatch(arg -> arg.type().kind() == RuntimeTypeKind.ANY
						&& !(arg instanceof RuntimeConstant c && c.kind() == RuntimeConstantKind.NULL)))
			reasons.add("Statistical argument requires runtime numeric type validation");
	}

	private static void inspectRoundingScale(String function, List<RuntimeExpression> arguments, Set<String> reasons) {
		if (Set.of("decimal", "floor", "ceiling", "ceil", "round", "round up", "round_up", "round down", "round_down",
				"round half up", "round_half_up", "round half down", "round_half_down", "round half even",
				"round_half_even").contains(function) && arguments.size() == 2
				&& arguments.get(1) instanceof RuntimeConstant scale && scale.kind() == RuntimeConstantKind.NUMBER) {
			try {
				if (new java.math.BigDecimal(scale.value()).abs().compareTo(java.math.BigDecimal.valueOf(308)) > 0)
					reasons.add(
							"Rounding scale exceeds native decimal/double resource bounds; requires lossless FEEL evaluation");
			} catch (NumberFormatException ignored) {
				// Invalid constants are handled by normal expression validation.
			}
		}
	}

	private static boolean staticTarget(RuntimeExpression target, Set<Integer> staticFunctions) {
		// Preserve the existing native emitter's static function and context-path
		// inlining.
		return target instanceof RuntimeFunctionDefinition
				|| target instanceof RuntimeValueReference ref && staticFunctions.contains(ref.sourceSlot())
				|| target instanceof RuntimePathExpression;
	}

	private static void inspectContextLookup(String fn, List<RuntimeExpression> args, Set<String> reasons) {
		if (Set.of("get value", "get_value").contains(fn) && !SparkSqlExpressionEmitter.nativeGetValue(args))
			reasons.add("Context lookup requires runtime field types");
	}

	private static void inspectBooleanArguments(String fn, List<RuntimeExpression> args, Set<String> reasons) {
		if (!Set.of("not", "all", "any").contains(fn))
			return;
		for (RuntimeExpression arg : args) {
			if (arg.type().kind() == RuntimeTypeKind.ANY
					&& !(arg instanceof RuntimeConstant c && c.kind() == RuntimeConstantKind.NULL))
				reasons.add("Boolean function argument requires runtime type checking");
		}
	}
}

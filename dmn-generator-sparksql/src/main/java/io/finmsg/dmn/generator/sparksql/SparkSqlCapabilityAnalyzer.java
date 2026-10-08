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
		for (RuntimeDecision decision : model.decisions()) {
			if (required.contains(decision.id()))
				inspect(decision, hybridBoundary, reasons, staticFunctions);
		}
		if (hybridBoundary) {
			for (RuntimeInput input : model.inputs()) {
				if (required.contains(input.id()) && !SparkSqlFeelValueCodec.isNativeType(input.type())) {
					reasons.add("Input slot " + input.valueSlot() + " requires lossless FEEL transport");
				}
			}
		}
		return new Capability(new ArrayList<>(reasons), new ArrayList<>(limitations));
	}

	private static final Map<String, String> NON_NATIVE_FUNCTIONS = Map.of("context",
			"Native context construction does not preserve heterogeneous struct values", "context put",
			"Native map updates are incompatible with the context struct representation", "context merge",
			"Native map merging is incompatible with the context struct representation");

	private static void inspect(Object node, boolean hybrid, Set<String> reasons, Set<Integer> staticFunctions) {
		boolean yearMonthResult = node instanceof RuntimeDecision decision && nativeYearMonthDurationResult(decision);
		boolean durationResult = node instanceof RuntimeDecision decision
				&& (nativeConstantDurationResult(decision) || yearMonthResult);
		Set<RuntimeTypeKind> temporalKinds = EnumSet.noneOf(RuntimeTypeKind.class);
		SparkSqlPayloadCodec.walk(node, value -> {
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
				if (NON_NATIVE_FUNCTIONS.containsKey(fn)) {
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
					if (NON_NATIVE_FUNCTIONS.containsKey(fn)) {
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
				if (list.elements().stream().map(RuntimeExpression::type).distinct().count() > 1
						|| list.type().elementType() == null
						|| list.type().elementType().kind() == RuntimeTypeKind.ANY) {
					reasons.add("Heterogeneous or untyped list");
				}
			}
			if (hybrid && value instanceof RuntimeExpression expression) {
				if (expression.type() != null && expression.type().kind() != RuntimeTypeKind.ANY
						&& !(expression instanceof RuntimeRangeExpression)
						&& !(expression instanceof RuntimeConstant c
								&& ("null".equals(c.value()) || c.kind() == RuntimeConstantKind.NULL))
						&& !SparkSqlFeelValueCodec.isNativeType(expression.type()) && !nativeDate(expression)
						&& !nativeLocalTemporal(expression) && !durationResult)
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
						|| expression instanceof RuntimeFunctionDefinition
						|| expression instanceof RuntimeInvocationExpression
						|| expression instanceof RuntimeFunctionCall)) {
					reasons.add("Native semantics not yet verified for " + expression.getClass().getSimpleName());
				}
			}
			if (hybrid && value instanceof RuntimeDecision decision) {
				if (decision.type() != null && decision.type().kind() != RuntimeTypeKind.ANY
						&& !SparkSqlFeelValueCodec.isNativeType(decision.type())
						&& !nativeConstantDurationResult(decision) && !nativeYearMonthDurationResult(decision)
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

	private static boolean nativeYearMonthDurationResult(RuntimeDecision decision) {
		if (decision.type() == null || !Set.of(RuntimeTypeKind.DURATION, RuntimeTypeKind.YEARS_MONTHS_DURATION)
				.contains(decision.type().kind()))
			return false;
		RuntimeExpression expression = decision.expression().orElse(null);
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
						.contains(call.function().toLowerCase(Locale.ROOT))
				|| call.arguments().size() != 2)
			return false;
		return call.arguments().stream()
				.allMatch(arg -> arg.type() != null && (arg.type().kind() == RuntimeTypeKind.DATE && nativeDate(arg)
						|| arg.type().kind() == RuntimeTypeKind.DATE_TIME && nativeLocalTemporal(arg)));
	}

	private static boolean nativeConstantDurationResult(RuntimeDecision decision) {
		if (!(decision.expression().orElse(null) instanceof RuntimeConstant constant)
				|| constant.kind() != RuntimeConstantKind.DURATION || decision.type() == null)
			return false;
		Object duration = io.finmsg.dmn.runtime.DmnRuntime.parseDuration(constant.value());
		return switch (decision.type().kind()) {
			case DURATION -> duration != null;
			case YEARS_MONTHS_DURATION -> duration instanceof java.time.Period;
			case DAYS_TIME_DURATION -> duration instanceof java.time.Duration;
			default -> false;
		};
	}

	private static boolean inspectChildren(Object node) {
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

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

	private static final Map<String, String> NON_NATIVE_FUNCTIONS = Map.of("matches",
			"FEEL regex syntax and flags require conformant evaluation", "context",
			"Native context construction does not preserve heterogeneous struct values", "context put",
			"Native map updates are incompatible with the context struct representation", "context merge",
			"Native map merging is incompatible with the context struct representation", "median",
			"Native median does not preserve even-length and invalid-list semantics", "mode",
			"Native mode does not preserve the list-valued frequency result", "stddev",
			"Native standard deviation does not preserve invalid-list semantics");

	private static void inspect(Object node, boolean hybrid, Set<String> reasons, Set<Integer> staticFunctions) {
		SparkSqlPayloadCodec.walk(node, value -> {
			if (value instanceof RuntimeFunctionCall fc) {
				String fn = fc.function().toLowerCase(Locale.ROOT);
				inspectBooleanArguments(fn, fc.arguments(), reasons);
				inspectContextLookup(fn, fc.arguments(), reasons);
				inspectRoundingScale(fn, fc.arguments(), reasons);
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
						&& !SparkSqlFeelValueCodec.isNativeType(expression.type()))
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
						&& !SparkSqlFeelValueCodec.isNativeType(decision.type()))
					reasons.add("Decision result requires lossless FEEL transport");
			}
		});
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

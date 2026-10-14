package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Bounded beta reduction of statically bound FEEL functions, never value
 * evaluation.
 */
final class SparkSqlStaticFunctionLowerer {
	private record Closure(RuntimeFunctionDefinition function, List<Map<Integer, Object>> frames) {
	}
	private record Builtin(String name) {
	}
	private static final class Unsupported extends RuntimeException {
	}
	private final RuntimeModel model;
	private final Set<Integer> active = new HashSet<>();
	private int budget = 4096;
	private boolean applied;

	private SparkSqlStaticFunctionLowerer(RuntimeModel model) {
		this.model = model;
	}

	static RuntimeModel lower(RuntimeModel model) {
		List<RuntimeDecision> decisions = new ArrayList<>();
		for (RuntimeDecision decision : model.decisions()) {
			RuntimeDecision result = decision;
			if (decision.expression().isPresent() && decision.decisionTable().isEmpty()) {
				var lowerer = new SparkSqlStaticFunctionLowerer(model);
				try {
					RuntimeExpression expression = lowerer.expression(decision.expression().get(),
							List.of(new HashMap<>()), 0);
					if (lowerer.applied) {
						RuntimeExpression resultExpression = (RuntimeExpression) coerce(expression, decision.type());
						Set<Integer> slots = new HashSet<>();
						SparkSqlPayloadCodec.walk(resultExpression, node -> {
							if (node instanceof RuntimeValueReference reference)
								slots.add(reference.sourceSlot());
						});
						List<Integer> dependencies = new ArrayList<>();
						model.inputs().stream().filter(i -> slots.contains(i.valueSlot()))
								.forEach(i -> dependencies.add(i.id()));
						model.decisions().stream().filter(d -> slots.contains(d.resultSlot()))
								.forEach(d -> dependencies.add(d.id()));
						model.businessKnowledgeModels().stream().filter(b -> slots.contains(b.resultSlot()))
								.forEach(b -> dependencies.add(b.id()));
						result = new RuntimeDecision(decision.id(), decision.resultSlot(), decision.type(),
								dependencies, Optional.of(resultExpression), Optional.empty(),
								decision.localSlotCount());
					}
				} catch (Unsupported ignored) {
					// Keep the original complete function graph for the established fallback.
				}
			}
			decisions.add(result);
		}
		return new RuntimeModel(model.inputs(), decisions, model.businessKnowledgeModels(), model.evaluationOrder(),
				model.valueSlotCount());
	}

	private RuntimeExpression expression(RuntimeExpression expression, List<Map<Integer, Object>> frames, int depth) {
		Object value = value(expression, frames, depth);
		if (value instanceof RuntimeExpression scalar)
			return scalar;
		throw new Unsupported();
	}

	private Object value(RuntimeExpression expression, List<Map<Integer, Object>> frames, int depth) {
		if (--budget < 0 || depth > 48)
			throw new Unsupported();
		if (expression instanceof RuntimeFunctionDefinition function) {
			if (function.external() || function.body().isEmpty())
				throw new Unsupported();
			// The runtime treats zero-parameter wrappers around named builtins specially.
			if (function.parameters().isEmpty()
					&& function.body().get() instanceof RuntimeInvocationExpression invocation
					&& invocation.function().isPresent()) {
				String name = invocation.function().get().toLowerCase(Locale.ROOT);
				if (Set.of("abs", "sqrt", "sum").contains(name))
					return new Builtin(name);
				throw new Unsupported();
			}
			return new Closure(function, frames);
		}
		if (expression instanceof RuntimeLocalReference reference) {
			if (reference.lexicalDepth() >= frames.size())
				throw new Unsupported();
			Object bound = frames.get(reference.lexicalDepth()).get(reference.localSlot());
			if (bound == null)
				throw new Unsupported();
			if (bound instanceof RuntimeExpression scalar) {
				SparkSqlPayloadCodec.walk(scalar, node -> {
					if (node instanceof RuntimeExpression && --budget < 0)
						throw new Unsupported();
				});
			}
			return bound;
		}
		if (expression instanceof RuntimeValueReference reference) {
			RuntimeExpression definition = null;
			for (RuntimeBkm bkm : model.businessKnowledgeModels()) {
				if (bkm.resultSlot() == reference.sourceSlot())
					definition = bkm.function().orElse(null);
			}
			for (RuntimeDecision decision : model.decisions()) {
				if (decision.resultSlot() == reference.sourceSlot() && decision.expression().isPresent()
						&& Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.FUNCTION).contains(decision.type().kind())
						&& (decision.expression().get() instanceof RuntimeFunctionDefinition
								|| decision.expression().get() instanceof RuntimeInvocationExpression invocation
										&& invocation.function().isEmpty()
								|| decision.type().kind() == RuntimeTypeKind.FUNCTION
								|| reference.type().kind() == RuntimeTypeKind.FUNCTION))
					definition = decision.expression().get();
			}
			if (definition == null)
				return reference;
			if (!active.add(reference.sourceSlot()))
				throw new Unsupported();
			try {
				Object resolved = value(definition, List.of(new HashMap<>()), depth + 1);
				return resolved instanceof Closure || resolved instanceof Builtin ? resolved : reference;
			} finally {
				active.remove(reference.sourceSlot());
			}
		}
		if (expression instanceof RuntimeInvocationExpression invocation) {
			if (invocation.function().isPresent()) {
				return new RuntimeInvocationExpression(invocation.function(), Optional.empty(), invocation
						.namedArguments().stream()
						.map(a -> new RuntimeNamedArgument(a.name(), expression(a.expression(), frames, depth + 1)))
						.toList(),
						invocation.positionalArguments().stream().map(a -> expression(a, frames, depth + 1)).toList(),
						invocation.type());
			}
			Object target = value(invocation.target().orElseThrow(), frames, depth + 1);
			if (target instanceof Builtin builtin) {
				if (!invocation.namedArguments().isEmpty())
					throw new Unsupported();
				List<RuntimeExpression> arguments = invocation.positionalArguments().stream()
						.map(a -> expression(a, frames, depth + 1)).toList();
				applied = true;
				if (!builtin.name().equals("sum") && arguments.size() != 1)
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null",
							RuntimeType.scalar(RuntimeTypeKind.NULL));
				if (builtin.name().equals("sum")) {
					if (arguments.isEmpty()
							|| !arguments.stream().allMatch(SparkSqlStaticFunctionLowerer::constantNumbers)
							|| arguments.size() > 1
									&& arguments.stream().anyMatch(a -> a instanceof RuntimeListExpression))
						throw new Unsupported();
				} else if (!compatible(arguments.getFirst(), RuntimeType.scalar(RuntimeTypeKind.NUMBER)))
					throw new Unsupported();
				return new RuntimeFunctionCall(builtin.name(), arguments, RuntimeType.scalar(RuntimeTypeKind.NUMBER));
			}
			if (!(target instanceof Closure closure))
				throw new Unsupported();
			var parameters = closure.function().parameters();
			if (!invocation.namedArguments().isEmpty() && !invocation.positionalArguments().isEmpty())
				throw new Unsupported();
			if (parameters.size() != invocation.namedArguments().size() + invocation.positionalArguments().size())
				throw new Unsupported();
			Map<Integer, Object> bindings = new HashMap<>();
			for (int index = 0; index < parameters.size(); index++) {
				RuntimeFunctionParameter parameter = parameters.get(index);
				RuntimeExpression argument = invocation.namedArguments().isEmpty()
						? invocation.positionalArguments().get(index)
						: invocation.namedArguments().stream().filter(a -> a.name().equals(parameter.name()))
								.map(RuntimeNamedArgument::expression).findFirst().orElseThrow(Unsupported::new);
				Object bound = coerce(value(argument, frames, depth + 1), parameter.type());
				bindings.put(parameter.localSlot(), bound);
			}
			List<Map<Integer, Object>> environment = new ArrayList<>();
			environment.add(bindings);
			environment.addAll(closure.frames());
			Object body = value(closure.function().body().orElseThrow(), List.copyOf(environment), depth + 1);
			RuntimeType functionType = closure.function().type();
			RuntimeType returnType = functionType.kind() == RuntimeTypeKind.FUNCTION
					? functionType.returnType()
					: functionType;
			if (returnType != null)
				body = coerce(body, returnType);
			applied = true;
			return body;
		}
		if (expression instanceof RuntimeContextExpression context) {
			Set<String> keys = new HashSet<>();
			context.entries().forEach(entry -> keys.add(entry.name()));
			if (keys.contains("java") || keys.containsAll(Set.of("class", "method signature")))
				throw new Unsupported();
			if (frames.isEmpty())
				throw new Unsupported();
			List<RuntimeContextEntry> entries = new ArrayList<>();
			boolean containsFunction = false;
			for (RuntimeContextEntry entry : context.entries()) {
				Object result = value(entry.expression(), frames, depth + 1);
				if (entry.name().isEmpty() && entry.localSlot() == -1)
					return result;
				if (entry.localSlot() >= 0)
					frames.getFirst().put(entry.localSlot(), result);
				if (result instanceof RuntimeExpression scalar)
					entries.add(new RuntimeContextEntry(entry.name(), entry.localSlot(), scalar));
				else
					containsFunction = true;
			}
			if (containsFunction)
				throw new Unsupported();
			return new RuntimeContextExpression(entries, context.type());
		}
		if (expression instanceof RuntimeConstant)
			return expression;
		if (expression instanceof RuntimeListExpression list) {
			List<RuntimeExpression> elements = list.elements().stream().map(e -> expression(e, frames, depth + 1))
					.toList();
			return new RuntimeListExpression(elements, list.type());
		}
		if (expression instanceof RuntimeBinaryExpression binary)
			return new RuntimeBinaryExpression(binary.operator(), expression(binary.left(), frames, depth + 1),
					expression(binary.right(), frames, depth + 1), binary.type());
		if (expression instanceof RuntimeUnaryExpression unary)
			return new RuntimeUnaryExpression(unary.operator(), expression(unary.operand(), frames, depth + 1),
					unary.type());
		if (expression instanceof RuntimeConditionalExpression conditional)
			return new RuntimeConditionalExpression(expression(conditional.condition(), frames, depth + 1),
					expression(conditional.thenExpression(), frames, depth + 1),
					expression(conditional.elseExpression(), frames, depth + 1), conditional.type());
		if (expression instanceof RuntimeFunctionCall call)
			return new RuntimeFunctionCall(call.function(),
					call.arguments().stream().map(a -> expression(a, frames, depth + 1)).toList(), call.type());
		throw new Unsupported();
	}

	private static Object coerce(Object value, RuntimeType expected) {
		if (compatible(value, expected))
			return value;
		if (value instanceof RuntimeExpression expression) {
			if (expected.kind() == RuntimeTypeKind.LIST && expected.elementType() != null
					&& compatible(expression, expected.elementType()) && SparkSqlFeelValueCodec.isNativeType(expected))
				return new RuntimeConditionalExpression(
						new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, expression,
								new RuntimeConstant(RuntimeConstantKind.NULL, "null",
										RuntimeType.scalar(RuntimeTypeKind.NULL)),
								RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)),
						new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL)),
						new RuntimeListExpression(List.of(expression), expected), expected);
			if (expression instanceof RuntimeListExpression list && list.elements().size() == 1
					&& list.elements().getFirst() instanceof RuntimeConstant constant
					&& constant.kind() != RuntimeConstantKind.NULL && compatible(list.elements().getFirst(), expected))
				return list.elements().getFirst();
		}
		throw new Unsupported();
	}

	private static boolean compatible(Object value, RuntimeType expected) {
		if (expected.kind() == RuntimeTypeKind.ANY)
			return true;
		if (value instanceof Closure || value instanceof Builtin)
			return expected.kind() == RuntimeTypeKind.FUNCTION;
		if (!(value instanceof RuntimeExpression expression))
			return false;
		RuntimeTypeKind actual = expression.type().kind();
		if (expression instanceof RuntimeConstant constant) {
			actual = switch (constant.kind()) {
				case NUMBER -> RuntimeTypeKind.NUMBER;
				case STRING -> RuntimeTypeKind.STRING;
				case BOOLEAN -> RuntimeTypeKind.BOOLEAN;
				case NULL -> RuntimeTypeKind.NULL;
				default -> actual;
			};
		}
		return actual == RuntimeTypeKind.NULL || actual == expected.kind()
				&& (Set.of(RuntimeTypeKind.NUMBER, RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN).contains(actual)
						|| expression.type().equals(expected) && SparkSqlFeelValueCodec.isNativeType(expected));
	}

	private static boolean constantNumbers(RuntimeExpression expression) {
		return expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.NUMBER
				|| expression instanceof RuntimeListExpression list && !list.elements().isEmpty()
						&& list.elements().stream().allMatch(e -> e instanceof RuntimeConstant constant
								&& constant.kind() == RuntimeConstantKind.NUMBER);
	}
}

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
	private record BoundContext(Map<String, Object> fields) {
	}
	private record Builtin(String name) {
	}
	private record LiteralJava(RuntimeFunctionDefinition function) {
	}
	private static final class Unsupported extends RuntimeException {
	}
	private final RuntimeModel model;
	private final Set<Integer> active = new HashSet<>();
	private int budget = 4096;
	private boolean applied;
	private boolean contextFunctions;
	private boolean numericLoopBindings;
	private int loopResults;
	private int quantifiedResults;

	private SparkSqlStaticFunctionLowerer(RuntimeModel model) {
		this.model = model;
	}

	static RuntimeModel lower(RuntimeModel model) {
		List<RuntimeDecision> decisions = new ArrayList<>();
		for (RuntimeDecision decision : model.decisions()) {
			if (SparkSqlTemporalChain.supports(model, decision)
					|| SparkSqlRankedContextLoop.supports(model, decision)) {
				decisions.add(decision);
				continue;
			}
			RuntimeDecision result = decision;
			if (decision.expression().isPresent() && decision.decisionTable().isEmpty()) {
				var lowerer = new SparkSqlStaticFunctionLowerer(model);
				try {
					RuntimeExpression expression = lowerer.expression(decision.expression().get(),
							List.of(new HashMap<>()), 0);
					RuntimeExpression resultExpression = (RuntimeExpression) lowerer.coerce(expression,
							decision.type());
					var candidate = new RuntimeDecision(decision.id(), decision.resultSlot(), decision.type(),
							decision.dependencies(), Optional.of(resultExpression), Optional.empty(),
							decision.localSlotCount());
					if (lowerer.contextFunctions && exactNumbersRequired(resultExpression)
							&& !SparkSqlExactArithmeticInput.supports(model, candidate))
						throw new Unsupported();
					if (lowerer.applied || resultExpression != expression) {
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

	private static boolean exactNumbersRequired(RuntimeExpression expression) {
		boolean[] required = {false};
		SparkSqlPayloadCodec.walk(expression, node -> {
			if (node instanceof RuntimeValueReference r && r.type().kind() == RuntimeTypeKind.NUMBER)
				required[0] = true;
			if (node instanceof RuntimeBinaryExpression b
					&& b.type().kind() != RuntimeTypeKind.STRING && Set
							.of(RuntimeBinaryOperator.ADD, RuntimeBinaryOperator.SUBTRACT,
									RuntimeBinaryOperator.MULTIPLY, RuntimeBinaryOperator.DIVIDE)
							.contains(b.operator()))
				required[0] = true;
		});
		return required[0];
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
		RuntimeExpression calendar = SparkSqlCalendarLiteralProof.result(expression);
		if (calendar != null) {
			applied = true;
			return calendar;
		}
		RuntimeExpression collection = SparkSqlClosedCollectionProof.result(expression);
		if (collection != null) {
			applied = true;
			return collection;
		}
		if (expression instanceof RuntimeFunctionDefinition function) {
			if (SparkSqlJavaLiteralProof.supports(function))
				return new LiteralJava(function);
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
			applied = true;
			if (bound instanceof RuntimeExpression scalar) {
				SparkSqlPayloadCodec.walk(scalar, node -> {
					if (node instanceof RuntimeExpression && --budget < 0)
						throw new Unsupported();
				});
			}
			return bound;
		}
		if (expression instanceof RuntimeValueReference reference) {
			for (RuntimeDecision decision : model.decisions()) {
				if (decision.resultSlot() == reference.sourceSlot() && decision.expression().isPresent()
						&& decision.expression().get() instanceof RuntimeContextExpression
						&& closedContextLiteral(decision.expression().get(), 0, new int[]{2048})) {
					if (!active.add(reference.sourceSlot()))
						throw new Unsupported();
					try {
						Object resolved = coerce(
								value(decision.expression().get(), List.of(new HashMap<>()), depth + 1),
								decision.type());
						applied = true;
						return resolved;
					} finally {
						active.remove(reference.sourceSlot());
					}
				}
			}
			for (var decision : model.decisions()) {
				if (decision.resultSlot() == reference.sourceSlot()
						&& decision.expression().orElse(null) instanceof RuntimeContextExpression context
						&& context.entries().stream()
								.anyMatch(e -> e.expression() instanceof RuntimeFunctionDefinition)) {
					if (!active.add(reference.sourceSlot()))
						throw new Unsupported();
					try {
						return coerce(value(context, List.of(new HashMap<>()), depth + 1), decision.type());
					} finally {
						active.remove(reference.sourceSlot());
					}
				}
			}

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
				if (invocation.namedArguments().isEmpty()) {
					RuntimeExpression sorted = boundedSort(invocation.function().get(),
							invocation.positionalArguments(), frames, depth);
					if (sorted != null)
						return sorted;
					var arguments = invocation.positionalArguments().stream().map(a -> expression(a, frames, depth + 1))
							.toList();
					RuntimeExpression flattened = SparkSqlFlattenLowerer.lower(invocation.function().get(), arguments);
					if (flattened != null) {
						applied = true;
						return flattened;
					}
					RuntimeType inferred = SparkSqlListBuiltinTypes.result(invocation.function().get(), arguments);
					if (inferred != null) {
						applied = true;
						return new RuntimeFunctionCall(invocation.function().get(), arguments, inferred);
					}
				}
				var rewritten = new RuntimeInvocationExpression(invocation.function(), Optional.empty(), invocation
						.namedArguments().stream()
						.map(a -> new RuntimeNamedArgument(a.name(), expression(a.expression(), frames, depth + 1)))
						.toList(),
						invocation.positionalArguments().stream().map(a -> expression(a, frames, depth + 1)).toList(),
						invocation.type());
				RuntimeExpression literal = SparkSqlExpressionEmitter.nativeStaticContextExpression(rewritten);
				if (literal != null) {
					applied = true;
					return literal;
				}
				return rewritten;
			}
			Object target = value(invocation.target().orElseThrow(), frames, depth + 1);
			if (target instanceof LiteralJava java) {
				var call = new RuntimeInvocationExpression(Optional.empty(), Optional.of(java.function()),
						invocation.namedArguments().stream()
								.map(argument -> new RuntimeNamedArgument(argument.name(),
										expression(argument.expression(), frames, depth + 1)))
								.toList(),
						invocation.positionalArguments().stream()
								.map(argument -> expression(argument, frames, depth + 1)).toList(),
						invocation.type());
				RuntimeExpression literal = SparkSqlJavaLiteralProof.result(java.function(), call);
				if (literal == null)
					throw new Unsupported();
				applied = true;
				return literal;
			}
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
				Object argumentValue = value(argument, frames, depth + 1);
				Object bound = coerce(argumentValue, parameter.type());
				if (bound instanceof RuntimeConstant result && result.kind() == RuntimeConstantKind.NULL
						&& !(argumentValue instanceof RuntimeConstant original
								&& original.kind() == RuntimeConstantKind.NULL)) {
					applied = true;
					return result;
				}
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
			var boundFields = new LinkedHashMap<String, Object>();
			boolean duplicateBoundFields = false;
			for (RuntimeContextEntry entry : context.entries()) {
				Object result = value(entry.expression(), frames, depth + 1);
				duplicateBoundFields |= boundFields.put(entry.name(), result) != null;
				if (entry.name().isEmpty() && entry.localSlot() == -1) {
					applied = true;
					return result;
				}
				if (entry.localSlot() >= 0)
					frames.getFirst().put(entry.localSlot(), result);
				if (result instanceof RuntimeExpression scalar)
					entries.add(new RuntimeContextEntry(entry.name(), entry.localSlot(), scalar));
				else
					containsFunction = true;
			}
			if (containsFunction) {
				if (duplicateBoundFields)
					throw new Unsupported();
				if (boundFields.values().stream().anyMatch(v -> !(v instanceof Closure || v instanceof Builtin
						|| v instanceof LiteralJava || v instanceof RuntimeConstant)))
					throw new Unsupported();
				contextFunctions = true;
				return new BoundContext(Collections.unmodifiableMap(boundFields));
			}
			var fields = new ArrayList<RuntimeField>();
			var names = new HashSet<String>();
			for (RuntimeContextEntry entry : entries) {
				RuntimeType fieldType = SparkSqlExpressionEmitter.nativeContextPutType(entry.expression());
				if (entry.expression() instanceof RuntimeConstant constant)
					fieldType = switch (constant.kind()) {
						case NUMBER -> RuntimeType.scalar(RuntimeTypeKind.NUMBER);
						case STRING -> RuntimeType.scalar(RuntimeTypeKind.STRING);
						case BOOLEAN -> RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
						case NULL -> RuntimeType.scalar(RuntimeTypeKind.NULL);
						default -> constant.type();
					};
				if (fieldType == null)
					fieldType = entry.expression().type();
				if (!names.add(entry.name().toLowerCase(Locale.ROOT))
						|| !SparkSqlFeelValueCodec.isNativeType(fieldType)) {
					fields.clear();
					break;
				}
				fields.add(new RuntimeField(fields.size(), entry.name(), fieldType));
			}
			if (!fields.isEmpty()) {
				RuntimeType inferred = RuntimeType.contextFields(fields);
				applied |= !inferred.equals(context.type());
				return new RuntimeContextExpression(entries, inferred);
			}
			return new RuntimeContextExpression(entries, context.type());
		}
		if (expression instanceof RuntimePathExpression path) {
			Object resolved = value(path.source(), frames, depth + 1);
			if (resolved instanceof BoundContext context) {
				applied = true;
				if (path.fieldIndex() >= 0 && path.fieldIndex() < context.fields().size())
					return new ArrayList<>(context.fields().values()).get(path.fieldIndex());
				Object member = context.fields().get(path.member());
				if (member == null)
					throw new Unsupported();
				return member;
			}
			if (!(resolved instanceof RuntimeExpression source))
				throw new Unsupported();
			var rewritten = new RuntimePathExpression(source, path.member(), path.fieldIndex(), path.type());
			var literal = SparkSqlClosedCollectionProof.result(rewritten);
			if (literal == null) {
				if (numericLoopBindings && source instanceof RuntimeLocalReference)
					return rewritten;
				throw new Unsupported();
			}
			applied = true;
			return literal;
		}
		if (expression instanceof RuntimeConstant)
			return expression;
		if (expression instanceof RuntimeQuantifiedExpression quantified) {
			if (quantified.bindings().size() > 4)
				throw new Unsupported();
			var predicates = new ArrayList<RuntimeExpression>();
			unrollQuantified(quantified, 0, frames, depth + 1, predicates);
			boolean every = quantified.quantifier() == RuntimeQuantifier.EVERY;
			RuntimeType bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
			applied = true;
			if (predicates.isEmpty())
				return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, Boolean.toString(every), bool);
			while (predicates.size() > 1) {
				var combined = new ArrayList<RuntimeExpression>();
				for (int index = 0; index < predicates.size(); index += 2)
					combined.add(index + 1 == predicates.size()
							? predicates.get(index)
							: new RuntimeBinaryExpression(every ? RuntimeBinaryOperator.AND : RuntimeBinaryOperator.OR,
									predicates.get(index), predicates.get(index + 1), bool));
				predicates = combined;
			}
			return predicates.getFirst();
		}
		if (expression instanceof RuntimeForExpression loop) {
			RuntimeExpression recurrence = SparkSqlPartialLoopProof.result(loop);
			if (recurrence != null) {
				applied = true;
				return recurrence;
			}
			if (loop.partialSlot() >= 0 || loop.iterations().size() > 4)
				throw new Unsupported();
			if (loop.iterations().size() == 1 && loop.iterations().getFirst().end().isEmpty()
					&& loop.iterations().getFirst().source() instanceof RuntimeValueReference source
					&& source.type().kind() == RuntimeTypeKind.LIST && source.type().elementType() != null) {
				var iteration = loop.iterations().getFirst();
				var frame = new HashMap<Integer, Object>(frames.getFirst());
				frame.put(iteration.localSlot(),
						new RuntimeLocalReference(iteration.localSlot(), source.type().elementType()));
				var environment = new ArrayList<Map<Integer, Object>>(frames);
				environment.set(0, frame);
				boolean previousBindings = numericLoopBindings;
				numericLoopBindings = source.type().elementType().kind() == RuntimeTypeKind.CONTEXT;
				RuntimeExpression body;
				try {
					body = expression(loop.result(), List.copyOf(environment), depth + 1);
				} finally {
					numericLoopBindings = previousBindings;
				}
				var rewritten = new RuntimeForExpression(loop.iterations(), body, -1,
						RuntimeType.element(RuntimeTypeKind.LIST, body.type()));
				var candidate = new RuntimeDecision(0, 0, rewritten.type(), List.of(), Optional.of(rewritten));
				if (!SparkSqlFactorLoopInput.supports(model, candidate)
						&& !SparkSqlNumericLoopInput.supports(model, candidate))
					throw new Unsupported();
				applied = true;
				return rewritten;
			}
			var results = new ArrayList<RuntimeExpression>();
			unroll(loop, 0, frames, depth + 1, results);
			RuntimeType element = results.isEmpty()
					? RuntimeType.scalar(RuntimeTypeKind.NULL)
					: results.getFirst().type();
			if (results.stream().anyMatch(item -> !item.type().equals(element))
					|| !SparkSqlFeelValueCodec.isNativeType(element))
				throw new Unsupported();
			applied = true;
			return new RuntimeListExpression(results, RuntimeType.element(RuntimeTypeKind.LIST, element));
		}
		if (expression instanceof RuntimeListExpression list) {
			List<RuntimeExpression> elements = list.elements().stream().map(e -> expression(e, frames, depth + 1))
					.toList();
			return new RuntimeListExpression(elements, list.type());
		}
		if (expression instanceof RuntimeBinaryExpression binary) {
			var left = expression(binary.left(), frames, depth + 1);
			var right = expression(binary.right(), frames, depth + 1);
			RuntimeType type = binary.type();
			if (binary.operator() == RuntimeBinaryOperator.ADD
					&& Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING).contains(type.kind())
					&& left.type().kind() == RuntimeTypeKind.STRING && right.type().kind() == RuntimeTypeKind.STRING) {
				applied |= type.kind() != RuntimeTypeKind.STRING;
				type = RuntimeType.scalar(RuntimeTypeKind.STRING);
			}
			if (contextFunctions && type.kind() == RuntimeTypeKind.ANY && left.type().kind() == RuntimeTypeKind.NUMBER
					&& right.type().kind() == RuntimeTypeKind.NUMBER && Set
							.of(RuntimeBinaryOperator.ADD, RuntimeBinaryOperator.SUBTRACT,
									RuntimeBinaryOperator.MULTIPLY, RuntimeBinaryOperator.DIVIDE)
							.contains(binary.operator())) {
				type = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
				applied = true;
			}
			return new RuntimeBinaryExpression(binary.operator(), left, right, type);
		}
		if (expression instanceof RuntimeInExpression in) {
			var tests = new ArrayList<RuntimeUnaryTest>();
			for (var test : in.tests().tests()) {
				if (test instanceof RuntimeRangeUnaryTest range)
					tests.add(new RuntimeRangeUnaryTest(
							(RuntimeRangeExpression) expression(range.range(), frames, depth + 1)));
				else if (test instanceof RuntimeExpressionUnaryTest t)
					tests.add(new RuntimeExpressionUnaryTest(expression(t.expression(), frames, depth + 1)));
				else if (test instanceof RuntimeComparisonUnaryTest t)
					tests.add(
							new RuntimeComparisonUnaryTest(t.operator(), expression(t.endpoint(), frames, depth + 1)));
				else
					throw new Unsupported();
			}
			return new RuntimeInExpression(expression(in.value(), frames, depth + 1),
					new RuntimeUnaryTests(in.tests().negated(), in.tests().wildcard(), tests), in.type());
		}
		if (expression instanceof RuntimeRangeExpression range)
			return new RuntimeRangeExpression(range.lower().map(e -> expression(e, frames, depth + 1)),
					range.upper().map(e -> expression(e, frames, depth + 1)), range.lowerBoundary(),
					range.upperBoundary(), range.type());

		if (expression instanceof RuntimeUnaryExpression unary)
			return new RuntimeUnaryExpression(unary.operator(), expression(unary.operand(), frames, depth + 1),
					unary.type());
		if (expression instanceof RuntimeConditionalExpression conditional) {
			var condition = expression(conditional.condition(), frames, depth + 1);
			var yes = expression(conditional.thenExpression(), frames, depth + 1);
			var no = expression(conditional.elseExpression(), frames, depth + 1);
			var type = conditional.type();
			if (contextFunctions && type.kind() == RuntimeTypeKind.ANY) {
				for (var kind : List.of(RuntimeTypeKind.NUMBER, RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN)) {
					var candidate = RuntimeType.scalar(kind);
					if (compatible(yes, candidate) && compatible(no, candidate)) {
						type = candidate;
						applied = true;
						break;
					}
				}
			}
			return new RuntimeConditionalExpression(condition, yes, no, type);
		}

		if (expression instanceof RuntimeFunctionCall call) {
			RuntimeExpression sorted = boundedSort(call.function(), call.arguments(), frames, depth);
			if (sorted != null)
				return sorted;
			var arguments = call.arguments().stream().map(a -> expression(a, frames, depth + 1)).toList();
			RuntimeExpression flattened = SparkSqlFlattenLowerer.lower(call.function(), arguments);
			if (flattened != null) {
				applied = true;
				return flattened;
			}
			RuntimeExpression literal = SparkSqlExpressionEmitter
					.nativeStaticContextExpression(new RuntimeFunctionCall(call.function(), arguments, call.type()));
			if (literal != null) {
				applied = true;
				return literal;
			}
			RuntimeType inferred = SparkSqlListBuiltinTypes.result(call.function(), arguments);
			if (inferred != null)
				applied = true;
			return new RuntimeFunctionCall(call.function(), arguments, inferred == null ? call.type() : inferred);
		}
		throw new Unsupported();
	}

	private RuntimeExpression boundedSort(String name, List<RuntimeExpression> arguments,
			List<Map<Integer, Object>> frames, int depth) {
		if (!name.equalsIgnoreCase("sort") || arguments.size() != 2)
			return null;
		RuntimeExpression source = expression(arguments.getFirst(), frames, depth + 1);
		Object comparator = value(arguments.get(1), frames, depth + 1);
		if (!(comparator instanceof Closure closure) || closure.function().parameters().size() != 2)
			return null;
		var bindings = new HashMap<Integer, Object>();
		for (var parameter : closure.function().parameters())
			bindings.put(parameter.localSlot(), new RuntimeLocalReference(parameter.localSlot(), parameter.type()));
		var environment = new ArrayList<Map<Integer, Object>>();
		environment.add(bindings);
		environment.addAll(closure.frames());
		var body = expression(closure.function().body().orElseThrow(), List.copyOf(environment), depth + 1);
		var function = new RuntimeFunctionDefinition(closure.function().parameters(), Optional.of(body), false,
				closure.function().localSlotCount(), closure.function().type());
		RuntimeExpression result = SparkSqlBoundedSortProof.result(model, source, function);
		if (result != null)
			applied = true;
		return result;
	}

	private void unrollQuantified(RuntimeQuantifiedExpression quantified, int index, List<Map<Integer, Object>> frames,
			int depth, List<RuntimeExpression> predicates) {
		if (depth > 48 || --budget < 0)
			throw new Unsupported();
		if (index == quantified.bindings().size()) {
			if (++quantifiedResults > 128)
				throw new Unsupported();
			RuntimeExpression predicate = expression(quantified.satisfies(), frames, depth + 1);
			RuntimeType bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
			if (predicate instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.NULL) {
				predicates.add(new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", bool));
				return;
			}
			if (predicate.type().kind() != RuntimeTypeKind.BOOLEAN)
				throw new Unsupported();
			predicates.add(new RuntimeConditionalExpression(predicate,
					new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool),
					new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", bool), bool));
			return;
		}
		RuntimeQuantifiedBinding binding = quantified.bindings().get(index);
		RuntimeExpression source = expression(binding.source(), frames, depth + 1);
		if (source instanceof RuntimeValueReference reference) {
			RuntimeDecision decision = model.decisions().stream().filter(d -> d.resultSlot() == reference.sourceSlot())
					.findFirst().orElseThrow(Unsupported::new);
			RuntimeExpression literal = decision.expression().orElseThrow(Unsupported::new);
			RuntimeExpression lowered = expression(literal, List.of(new HashMap<>()), depth + 1);
			if (!closedContextLiteral(lowered, 0, new int[]{512}))
				throw new Unsupported();
			source = (RuntimeExpression) coerce(lowered, decision.type());
		}
		if (!(source instanceof RuntimeListExpression list) || list.elements().size() > 64
				|| list.elements().stream().anyMatch(item -> !closedContextLiteral(item, 0, new int[]{512})))
			throw new Unsupported();

		for (RuntimeExpression item : list.elements()) {
			var bindings = new HashMap<>(frames.getFirst());
			bindings.put(binding.localSlot(), item);
			var environment = new ArrayList<>(frames);
			environment.set(0, bindings);
			unrollQuantified(quantified, index + 1, environment, depth + 1, predicates);
		}
	}

	private void unroll(RuntimeForExpression loop, int index, List<Map<Integer, Object>> frames, int depth,
			List<RuntimeExpression> results) {
		if (depth > 48 || --budget < 0)
			throw new Unsupported();
		if (index == loop.iterations().size()) {
			if (++loopResults > 256)
				throw new Unsupported();
			results.add(typedResult(expression(loop.result(), frames, depth + 1)));
			return;
		}
		RuntimeIteration iteration = loop.iterations().get(index);
		if (iteration.localSlot() >= 2048)
			throw new Unsupported();
		RuntimeExpression source = expression(iteration.source(), frames, depth + 1);
		if (source instanceof RuntimeValueReference reference) {
			RuntimeDecision definition = model.decisions().stream()
					.filter(d -> d.resultSlot() == reference.sourceSlot()).findFirst().orElseThrow(Unsupported::new);
			RuntimeExpression literal = definition.expression().orElseThrow(Unsupported::new);
			if (!closedLiteral(literal, 0, new int[]{512}))
				throw new Unsupported();
			source = (RuntimeExpression) coerce(expression(literal, frames, depth + 1), definition.type());
		}
		List<RuntimeExpression> items;
		if (iteration.end().isPresent()) {
			long first = integer(source, 0);
			long last = integer(expression(iteration.end().get(), frames, depth + 1), 0);
			if (Math.abs(last - first) >= 256)
				throw new Unsupported();
			items = new ArrayList<>();
			long step = first <= last ? 1 : -1;
			for (long number = first;; number += step) {
				items.add(new RuntimeConstant(RuntimeConstantKind.NUMBER, Long.toString(number),
						RuntimeType.scalar(RuntimeTypeKind.NUMBER)));
				if (number == last)
					break;
			}
		} else if (source instanceof RuntimeListExpression list && list.elements().size() <= 256
				&& closedLiteral(list, 0, new int[]{512})) {
			items = list.elements();
		} else {
			throw new Unsupported();
		}
		for (RuntimeExpression item : items) {
			var bindings = new HashMap<>(frames.getFirst());
			bindings.put(iteration.localSlot(), item);
			var environment = new ArrayList<>(frames);
			environment.set(0, bindings);
			unroll(loop, index + 1, environment, depth + 1, results);
		}
	}

	private static boolean closedContextLiteral(RuntimeExpression expression, int depth, int[] budget) {
		if (depth > 16 || --budget[0] < 0)
			return false;
		if (expression instanceof RuntimeContextExpression context)
			return context.entries().stream().allMatch(
					entry -> !entry.name().isEmpty() && closedContextLiteral(entry.expression(), depth + 1, budget));
		if (expression instanceof RuntimeListExpression list)
			return list.elements().stream().allMatch(item -> closedContextLiteral(item, depth + 1, budget));
		return expression instanceof RuntimeConstant constant && constant.value().length() <= 2048
				&& Set.of(RuntimeConstantKind.NUMBER, RuntimeConstantKind.STRING, RuntimeConstantKind.BOOLEAN,
						RuntimeConstantKind.NULL).contains(constant.kind());
	}

	private static boolean closedLiteral(RuntimeExpression expression, int depth, int[] budget) {
		if (depth > 16 || --budget[0] < 0)
			return false;
		if (expression instanceof RuntimeListExpression list)
			return list.elements().stream().allMatch(item -> closedLiteral(item, depth + 1, budget));
		return expression instanceof RuntimeConstant constant && constant.value().length() <= 2048
				&& Set.of(RuntimeConstantKind.NUMBER, RuntimeConstantKind.STRING, RuntimeConstantKind.BOOLEAN)
						.contains(constant.kind());
	}

	private static long integer(RuntimeExpression expression, int depth) {
		if (depth > 8)
			throw new Unsupported();
		try {
			if (expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.NUMBER
					&& constant.value().length() <= 64)
				return new java.math.BigDecimal(constant.value()).intValueExact();
			if (expression instanceof RuntimeUnaryExpression unary) {
				long operand = integer(unary.operand(), depth + 1);
				return switch (unary.operator()) {
					case POSITIVE -> operand;
					case NEGATE -> Math.toIntExact(-operand);
					default -> throw new Unsupported();
				};
			}
			if (expression instanceof RuntimeBinaryExpression binary) {
				long left = integer(binary.left(), depth + 1), right = integer(binary.right(), depth + 1);
				return switch (binary.operator()) {
					case ADD -> Math.toIntExact(left + right);
					case SUBTRACT -> Math.toIntExact(left - right);
					case MULTIPLY -> Math.toIntExact(Math.multiplyExact(left, right));
					default -> throw new Unsupported();
				};
			}
		} catch (ArithmeticException | NumberFormatException ignored) {
			throw new Unsupported();
		}
		throw new Unsupported();
	}

	private static RuntimeExpression typedResult(RuntimeExpression expression) {
		if (expression instanceof RuntimeConstant constant) {
			if (constant.value().length() > 2048
					|| !Set.of(RuntimeConstantKind.NUMBER, RuntimeConstantKind.STRING, RuntimeConstantKind.BOOLEAN)
							.contains(constant.kind()))
				throw new Unsupported();
			if (constant.kind() == RuntimeConstantKind.NUMBER)
				integer(constant, 0);
			RuntimeTypeKind kind = switch (constant.kind()) {
				case NUMBER -> RuntimeTypeKind.NUMBER;
				case STRING -> RuntimeTypeKind.STRING;
				case BOOLEAN -> RuntimeTypeKind.BOOLEAN;
				default -> constant.type().kind();
			};
			return new RuntimeConstant(constant.kind(), constant.value(), RuntimeType.scalar(kind));
		}
		if (expression instanceof RuntimeListExpression list) {
			var elements = list.elements().stream().map(SparkSqlStaticFunctionLowerer::typedResult).toList();
			var element = elements.isEmpty() ? RuntimeType.scalar(RuntimeTypeKind.NULL) : elements.getFirst().type();
			if (elements.stream().anyMatch(item -> !item.type().equals(element)))
				throw new Unsupported();
			return new RuntimeListExpression(elements, RuntimeType.element(RuntimeTypeKind.LIST, element));
		}
		if (expression instanceof RuntimeBinaryExpression binary) {
			var left = typedResult(binary.left());
			var right = typedResult(binary.right());
			if (!left.type().equals(right.type()))
				throw new Unsupported();
			RuntimeTypeKind kind = switch (binary.operator()) {
				case ADD, SUBTRACT, MULTIPLY -> {
					if (binary.operator() == RuntimeBinaryOperator.ADD && left.type().kind() == RuntimeTypeKind.STRING)
						yield RuntimeTypeKind.STRING;
					integer(binary, 0); // Every arithmetic intermediate must fit Spark's integer representation.
					yield RuntimeTypeKind.NUMBER;
				}
				case EQUAL, NOT_EQUAL -> {
					if (!Set.of(RuntimeTypeKind.NUMBER, RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN)
							.contains(left.type().kind()))
						throw new Unsupported();
					yield RuntimeTypeKind.BOOLEAN;
				}
				case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
					if (!Set.of(RuntimeTypeKind.NUMBER, RuntimeTypeKind.STRING).contains(left.type().kind()))
						throw new Unsupported();
					yield RuntimeTypeKind.BOOLEAN;
				}
				case AND, OR -> {
					if (left.type().kind() != RuntimeTypeKind.BOOLEAN)
						throw new Unsupported();
					yield RuntimeTypeKind.BOOLEAN;
				}
				default -> throw new Unsupported();
			};
			return new RuntimeBinaryExpression(binary.operator(), left, right, RuntimeType.scalar(kind));
		}
		if (expression instanceof RuntimeConditionalExpression conditional) {
			var condition = typedResult(conditional.condition());
			var yes = typedResult(conditional.thenExpression());
			var no = typedResult(conditional.elseExpression());
			if (condition.type().kind() != RuntimeTypeKind.BOOLEAN || !yes.type().equals(no.type()))
				throw new Unsupported();
			return new RuntimeConditionalExpression(condition, yes, no, yes.type());
		}
		if (expression instanceof RuntimeUnaryExpression unary) {
			integer(unary, 0);
			return new RuntimeUnaryExpression(unary.operator(), typedResult(unary.operand()),
					RuntimeType.scalar(RuntimeTypeKind.NUMBER));
		}
		throw new Unsupported();
	}

	private Object coerce(Object value, RuntimeType expected) {
		if (value instanceof BoundContext context && expected.kind() == RuntimeTypeKind.CONTEXT) {
			for (var f : expected.fieldLayout())
				if (!context.fields().containsKey(f.name()) || !compatible(context.fields().get(f.name()), f.type()))
					throw new Unsupported();
			return context;
		}
		if (contextFunctions && value instanceof RuntimeContextExpression context
				&& expected.kind() == RuntimeTypeKind.CONTEXT && !context.type().equals(expected)) {
			for (var f : expected.fieldLayout()) {
				var entry = context.entries().stream().filter(e -> e.name().equals(f.name())).findFirst()
						.orElseThrow(Unsupported::new);
				if (!compatible(entry.expression(), f.type()))
					throw new Unsupported();
			}
			return new RuntimeContextExpression(context.entries(), expected);
		}

		if (compatible(value, expected))
			return value;
		if (value instanceof RuntimeExpression expression) {
			if (closedContextLiteral(expression, 0, new int[]{2048})) {
				int[] localSlots = {0};
				SparkSqlPayloadCodec.walk(expression, node -> {
					if (node instanceof RuntimeContextEntry entry) {
						if (entry.localSlot() >= 2048)
							throw new Unsupported();
						localSlots[0] = Math.max(localSlots[0], entry.localSlot() + 1);
					}
				});
				var decision = new RuntimeDecision(0, 0, expected, List.of(), Optional.of(expression), Optional.empty(),
						localSlots[0]);
				Object coerced = new io.finmsg.dmn.runtime.DmnRuntime()
						.evaluate(new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0), 1), Map.of())
						.decisionValue(0);
				RuntimeExpression literal = SparkSqlExpressionEmitter.nativeContextLiteralValue(coerced);
				if (literal != null)
					return literal;
			}
			if (expression instanceof RuntimeListExpression list && expected.kind() == RuntimeTypeKind.LIST
					&& expected.elementType() != null && expected.elementType().kind() == RuntimeTypeKind.ANY)
				return list;
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

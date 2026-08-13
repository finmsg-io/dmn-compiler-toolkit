package io.finmsg.dmn.runtime;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.*;
import java.time.temporal.TemporalAmount;
import java.util.*;

/** Deterministic, side-effect-free interpreter for {@link RuntimeModel}. */
public final class DmnRuntime {
	public DmnEvaluationResult evaluate(RuntimeModel model, Map<Integer, ?> inputs) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(inputs, "inputs");
		Object[] slots = new Object[model.valueSlotCount()];
		for (RuntimeInput input : model.inputs()) {
			if (!inputs.containsKey(input.valueSlot())) {
				throw new DmnEvaluationException("Missing input value for slot " + input.valueSlot());
			}
			slots[input.valueSlot()] = inputs.get(input.valueSlot());
		}
		Map<Integer, RuntimeDecision> decisions = new HashMap<>();
		model.decisions().forEach(value -> decisions.put(value.id(), value));
		Map<Integer, RuntimeBkm> bkms = new HashMap<>();
		model.businessKnowledgeModels().forEach(value -> bkms.put(value.id(), value));
		State state = new State(slots);
		for (int id : model.evaluationOrder()) {
			RuntimeBkm bkm = bkms.get(id);
			if (bkm != null) {
				if (bkm.functionKind() != RuntimeFunctionKind.FEEL || bkm.function().isEmpty()) {
					throw new DmnEvaluationException("External BKM " + id + " is not executable by this runtime");
				}
				slots[bkm.resultSlot()] = state.expression(bkm.function().orElseThrow(), Frame.EMPTY);
				continue;
			}
			RuntimeDecision decision = decisions.get(id);
			if (decision == null)
				throw new DmnEvaluationException("Unknown aggregate ID " + id);
			Frame frame = new Frame(Frame.EMPTY, decision.localSlotCount());
			Object value = decision.expression().map(it -> state.expression(it, frame))
					.orElseGet(() -> decision.decisionTable().map(it -> state.table(it, frame)).orElse(null));
			slots[decision.resultSlot()] = value;
		}
		return new DmnEvaluationResult(model, Arrays.asList(slots.clone()));
	}

	private interface CallableValue {
		Object call(List<Object> positional, Map<String, Object> named);
	}

	private static final class Frame {
		static final Frame EMPTY = new Frame(null, 0);
		final Frame parent;
		final Object[] values;
		Frame(Frame parent, int size) {
			this.parent = parent;
			this.values = new Object[size];
		}
		Object get(int depth, int slot) {
			Frame frame = this;
			for (int index = 0; index < depth; index++) {
				frame = frame.parent;
				if (frame == null)
					throw new DmnEvaluationException("Invalid lexical depth " + depth);
			}
			if (slot >= frame.values.length)
				throw new DmnEvaluationException("Invalid local slot " + slot);
			return frame.values[slot];
		}
		void set(int slot, Object value) {
			if (slot >= values.length)
				throw new DmnEvaluationException("Invalid local slot " + slot);
			values[slot] = value;
		}
	}

	private static final class State {
		private final Object[] slots;
		State(Object[] slots) {
			this.slots = slots;
		}

		Object expression(RuntimeExpression value, Frame frame) {
			return switch (value) {
				case RuntimeConstant it -> constant(it);
				case RuntimeValueReference it -> slots[it.sourceSlot()];
				case RuntimeLocalReference it -> frame.get(it.lexicalDepth(), it.localSlot());
				case RuntimeUnaryExpression it -> unary(it.operator(), expression(it.operand(), frame));
				case RuntimeBinaryExpression it ->
					binary(it.operator(), expression(it.left(), frame), () -> expression(it.right(), frame));
				case RuntimeConditionalExpression it -> truth(expression(it.condition(), frame))
						? expression(it.thenExpression(), frame)
						: expression(it.elseExpression(), frame);
				case RuntimeListExpression it -> it.elements().stream().map(item -> expression(item, frame)).toList();
				case RuntimeFunctionCall it ->
					builtin(it.function(), it.arguments().stream().map(arg -> expression(arg, frame)).toList());
				case RuntimeContextExpression it -> context(it, frame);
				case RuntimePathExpression it -> property(expression(it.source(), frame), it.member(), it.fieldIndex());
				case RuntimeDescendantExpression it ->
					descendants(expression(it.source(), frame), it.member(), it.fieldIndex());
				case RuntimeRangeExpression it -> new RuntimeRangeValue(
						it.lower().map(v -> expression(v, frame)).orElse(null),
						it.upper().map(v -> expression(v, frame)).orElse(null), it.lowerBoundary(), it.upperBoundary());
				case RuntimeBetweenExpression it ->
					compare(expression(it.value(), frame), expression(it.lower(), frame)) >= 0
							&& compare(expression(it.value(), frame), expression(it.upper(), frame)) <= 0;
				case RuntimeInExpression it -> tests(expression(it.value(), frame), it.tests(), frame);
				case RuntimeInstanceOfExpression it -> instanceOf(expression(it.expression(), frame), it.testedType());
				case RuntimeForExpression it -> iterate(it.iterations(), 0, it.result(), frame, new ArrayList<>());
				case RuntimeQuantifiedExpression it -> quantify(it, 0, frame);
				case RuntimeFunctionDefinition it -> function(it, frame);
				case RuntimeInvocationExpression it -> invoke(it, frame);
				case RuntimeRelationExpression it -> relation(it, frame);
				case RuntimeUnaryTestsExpression it -> it.tests();
				case RuntimeDecisionTableReference ignored -> throw new DmnEvaluationException(
						"A decision-table marker cannot be evaluated outside its owning decision");
				case RuntimeFilterExpression it -> filter(it, frame);
			};
		}

		private Object constant(RuntimeConstant value) {
			try {
				return switch (value.kind()) {
					case NULL -> null;
					case BOOLEAN -> Boolean.valueOf(value.value());
					case NUMBER -> new BigDecimal(value.value());
					case STRING -> value.value();
					case DATE -> LocalDate.parse(value.value());
					case TIME -> value.value().matches(".*[Z+-][0-9:]*$")
							? OffsetTime.parse(value.value())
							: LocalTime.parse(value.value());
					case DATE_TIME -> value.value().matches(".*[Z+-][0-9:]*$")
							? OffsetDateTime.parse(value.value())
							: LocalDateTime.parse(value.value());
					case DURATION ->
						value.value().contains("T") ? Duration.parse(value.value()) : Period.parse(value.value());
				};
			} catch (RuntimeException exception) {
				throw new DmnEvaluationException("Invalid " + value.kind() + " constant: " + value.value(), exception);
			}
		}

		private Object unary(RuntimeUnaryOperator operator, Object operand) {
			return switch (operator) {
				case POSITIVE -> operand == null ? null : number(operand);
				case NEGATE -> operand == null ? null : number(operand).negate();
				case NOT -> not(operand);
			};
		}

		private Object binary(RuntimeBinaryOperator operator, Object left, java.util.function.Supplier<Object> right) {
			if (operator == RuntimeBinaryOperator.AND)
				return and(left, right);
			if (operator == RuntimeBinaryOperator.OR)
				return or(left, right);
			Object r = right.get();
			if (left == null || r == null) {
				return switch (operator) {
					case EQUAL -> equal(left, r);
					case NOT_EQUAL -> !equal(left, r);
					case ADD -> (left instanceof String || r instanceof String)
							? String.valueOf(left) + String.valueOf(r)
							: null;
					default -> null;
				};
			}
			return switch (operator) {
				case ADD -> left instanceof String || r instanceof String
						? String.valueOf(left) + r
						: number(left).add(number(r));
				case SUBTRACT -> number(left).subtract(number(r));
				case MULTIPLY -> number(left).multiply(number(r));
				case DIVIDE -> {
					try {
						BigDecimal denom = number(r);
						yield denom.compareTo(BigDecimal.ZERO) == 0
								? null
								: number(left).divide(denom, MathContext.DECIMAL128);
					} catch (ArithmeticException e) {
						yield null;
					}
				}
				case POWER -> BigDecimal.valueOf(Math.pow(number(left).doubleValue(), number(r).doubleValue()));
				case EQUAL -> equal(left, r);
				case NOT_EQUAL -> !equal(left, r);
				case LESS -> {
					Integer c = compare(left, r);
					yield c == null ? null : c < 0;
				}
				case LESS_EQUAL -> {
					Integer c = compare(left, r);
					yield c == null ? null : c <= 0;
				}
				case GREATER -> {
					Integer c = compare(left, r);
					yield c == null ? null : c > 0;
				}
				case GREATER_EQUAL -> {
					Integer c = compare(left, r);
					yield c == null ? null : c >= 0;
				}
				case AND, OR -> throw new AssertionError();
			};
		}

		private Object context(RuntimeContextExpression context, Frame frame) {
			List<Object> values = new ArrayList<>();
			Map<String, Object> named = new LinkedHashMap<>();
			for (RuntimeContextEntry entry : context.entries()) {
				Object result = expression(entry.expression(), frame);
				if (entry.name().isBlank())
					return result;
				frame.set(entry.localSlot(), result);
				values.add(result);
				named.put(entry.name(), result);
			}
			return new RuntimeContextValue(values, named);
		}

		private Object property(Object source, String name, int index) {
			if (source == null)
				return null;
			if (source instanceof List<?> list)
				return list.stream().map(item -> property(item, name, index)).toList();
			if (source instanceof RuntimeContextValue context)
				return index >= 0 ? context.field(index) : context.field(name);
			if (source instanceof Map<?, ?> map)
				return map.get(name);
			throw new DmnEvaluationException(
					"Cannot access member '" + name + "' on " + source.getClass().getSimpleName());
		}

		private List<Object> descendants(Object source, String name, int index) {
			List<Object> result = new ArrayList<>();
			descend(source, name, index, result);
			return List.copyOf(result);
		}
		private void descend(Object source, String name, int index, List<Object> result) {
			if (source instanceof List<?> list) {
				list.forEach(item -> descend(item, name, index, result));
				return;
			}
			if (source instanceof RuntimeContextValue context) {
				Object match = index >= 0 && index < context.fields().size()
						? context.field(index)
						: context.field(name);
				if (match != null)
					result.add(match);
				context.namedFields().values().forEach(item -> descend(item, name, -1, result));
			} else if (source instanceof Map<?, ?> map) {
				if (map.containsKey(name))
					result.add(map.get(name));
				map.values().forEach(item -> descend(item, name, -1, result));
			}
		}

		private Object filter(RuntimeFilterExpression filter, Frame frame) {
			Object source = expression(filter.source(), frame);
			if (source == null)
				return null;
			List<?> list = source instanceof List<?> l ? l : List.of(source);
			List<Object> result = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				Object item = list.get(i);
				Frame child = new Frame(frame, 1);
				child.set(0, item);
				Object selector = expression(filter.filter(), child);
				if (selector instanceof BigDecimal number) {
					int position = number.intValueExact();
					int index = position > 0 ? position - 1 : list.size() + position;
					return index >= 0 && index < list.size() ? list.get(index) : null;
				}
				if (Boolean.TRUE.equals(selector)) {
					result.add(item);
				}
			}
			return List.copyOf(result);
		}

		private List<Object> iterate(List<RuntimeIteration> iterations, int index, RuntimeExpression result,
				Frame frame, List<Object> output) {
			if (index == iterations.size()) {
				output.add(expression(result, frame));
				return output;
			}
			RuntimeIteration iteration = iterations.get(index);
			for (Object item : iterationValues(iteration, frame)) {
				frame.set(iteration.localSlot(), item);
				iterate(iterations, index + 1, result, frame, output);
			}
			return List.copyOf(output);
		}

		private List<Object> iterationValues(RuntimeIteration iteration, Frame frame) {
			Object start = expression(iteration.source(), frame);
			if (iteration.end().isEmpty())
				return list(start);
			int first = number(start).intValueExact();
			int last = number(expression(iteration.end().orElseThrow(), frame)).intValueExact();
			List<Object> result = new ArrayList<>();
			int step = first <= last ? 1 : -1;
			for (int value = first;; value += step) {
				result.add(BigDecimal.valueOf(value));
				if (value == last)
					break;
			}
			return result;
		}

		private boolean quantify(RuntimeQuantifiedExpression value, int index, Frame frame) {
			if (index == value.bindings().size())
				return truth(expression(value.satisfies(), frame));
			RuntimeQuantifiedBinding binding = value.bindings().get(index);
			for (Object item : list(expression(binding.source(), frame))) {
				frame.set(binding.localSlot(), item);
				boolean match = quantify(value, index + 1, frame);
				if (value.quantifier() == RuntimeQuantifier.SOME && match)
					return true;
				if (value.quantifier() == RuntimeQuantifier.EVERY && !match)
					return false;
			}
			return value.quantifier() == RuntimeQuantifier.EVERY;
		}

		private CallableValue function(RuntimeFunctionDefinition definition, Frame closure) {
			if (definition.external())
				return (positional, named) -> {
					throw new DmnEvaluationException("External functions require a host binding");
				};
			return (positional, named) -> {
				Frame call = new Frame(closure, definition.localSlotCount());
				for (int index = 0; index < definition.parameters().size(); index++) {
					RuntimeFunctionParameter parameter = definition.parameters().get(index);
					Object argument = index < positional.size() ? positional.get(index) : named.get(parameter.name());
					call.set(parameter.localSlot(), argument);
				}
				return expression(definition.body().orElseThrow(), call);
			};
		}

		private Object invoke(RuntimeInvocationExpression invocation, Frame frame) {
			List<Object> positional = invocation.positionalArguments().stream().map(it -> expression(it, frame))
					.toList();
			Map<String, Object> named = new LinkedHashMap<>();
			invocation.namedArguments().forEach(it -> named.put(it.name(), expression(it.expression(), frame)));
			if (invocation.function().isPresent()) {
				List<Object> all = new ArrayList<>(positional);
				all.addAll(named.values());
				return builtin(invocation.function().orElseThrow(), all);
			}
			Object target = expression(invocation.target().orElseThrow(), frame);
			if (!(target instanceof CallableValue callable))
				throw new DmnEvaluationException("Invocation target is not a function");
			return callable.call(positional, named);
		}

		private Object relation(RuntimeRelationExpression relation, Frame frame) {
			List<Object> rows = new ArrayList<>();
			for (List<RuntimeExpression> row : relation.rows()) {
				List<Object> fields = row.stream().map(it -> expression(it, frame)).toList();
				Map<String, Object> named = new LinkedHashMap<>();
				for (int index = 0; index < fields.size(); index++)
					named.put(relation.columns().get(index).name(), fields.get(index));
				rows.add(new RuntimeContextValue(fields, named));
			}
			return List.copyOf(rows);
		}

		Object table(RuntimeDecisionTable table, Frame frame) {
			List<Object> inputValues = table.inputs().stream().map(it -> expression(it.expression(), frame)).toList();
			List<List<Object>> matches = new ArrayList<>();
			for (RuntimeDecisionTableRule rule : table.rules()) {
				boolean matchesRule = true;
				for (int index = 0; index < inputValues.size(); index++)
					matchesRule &= tests(inputValues.get(index), rule.inputEntries().get(index), frame);
				if (matchesRule)
					matches.add(rule.outputEntries().stream().map(it -> expression(it, frame)).toList());
			}
			if (matches.isEmpty())
				return defaultOutput(table, frame);
			return switch (table.hitPolicy()) {
				case UNIQUE -> {
					if (matches.size() != 1)
						throw new DmnEvaluationException("UNIQUE table matched " + matches.size() + " rules");
					yield output(matches.getFirst(), table.outputs());
				}
				case FIRST, PRIORITY -> output(matches.getFirst(), table.outputs());
				case ANY -> {
					Object first = output(matches.getFirst(), table.outputs());
					if (matches.stream().map(row -> output(row, table.outputs())).anyMatch(row -> !equal(first, row)))
						throw new DmnEvaluationException("ANY table produced different outputs");
					yield first;
				}
				case RULE_ORDER -> matches.stream().map(row -> output(row, table.outputs())).toList();
				case OUTPUT_ORDER -> sortOutputOrder(matches.stream().map(row -> output(row, table.outputs())).toList(),
						table.outputs());
				case COLLECT ->
					aggregate(matches.stream().map(row -> output(row, table.outputs())).toList(), table.aggregation());
			};
		}

		private Object defaultOutput(RuntimeDecisionTable table, Frame frame) {
			List<Object> values = table.outputs().stream()
					.map(output -> output.defaultValue().map(it -> expression(it, frame)).orElse(null)).toList();
			return output(values, table.outputs());
		}
		private Object output(List<Object> values, List<RuntimeDecisionTableOutput> outputs) {
			if (values.size() == 1)
				return values.getFirst();
			Map<String, Object> named = new LinkedHashMap<>();
			for (int index = 0; index < values.size(); index++)
				named.put(outputs.get(index).name().orElse("output" + (index + 1)), values.get(index));
			return new RuntimeContextValue(values, named);
		}
		private Object aggregate(List<Object> values, Optional<RuntimeAggregation> aggregation) {
			if (aggregation.isEmpty())
				return values;
			return switch (aggregation.orElseThrow()) {
				case COUNT -> BigDecimal.valueOf(values.size());
				case SUM -> values.stream().map(DmnRuntime::number).reduce(BigDecimal.ZERO, BigDecimal::add);
				case MIN -> values.stream().min(DmnRuntime::compare).orElse(null);
				case MAX -> values.stream().max(DmnRuntime::compare).orElse(null);
			};
		}

		private List<Object> sortOutputOrder(List<Object> results, List<RuntimeDecisionTableOutput> outputs) {
			if (outputs.size() == 1 && outputs.get(0).allowedValues().isPresent()) {
				List<Object> domain = extractAllowedValues(outputs.get(0).allowedValues().get());
				if (!domain.isEmpty()) {
					List<Object> sorted = new ArrayList<>(results);
					sorted.sort(Comparator.comparingInt(val -> {
						int idx = domain.indexOf(val);
						return idx < 0 ? Integer.MAX_VALUE : idx;
					}));
					return sorted;
				}
			}
			return results;
		}

		private List<Object> extractAllowedValues(RuntimeUnaryTests tests) {
			List<Object> domain = new ArrayList<>();
			for (RuntimeUnaryTest test : tests.tests()) {
				if (test instanceof RuntimeComparisonUnaryTest comp) {
					Object val = expression(comp.endpoint(), Frame.EMPTY);
					if (val != null)
						domain.add(val);
				} else if (test instanceof RuntimeExpressionUnaryTest expr) {
					Object val = expression(expr.expression(), Frame.EMPTY);
					if (val != null)
						domain.add(val);
				}
			}
			return domain;
		}

		private boolean tests(Object candidate, RuntimeUnaryTests tests, Frame frame) {
			boolean match = tests.wildcard() || tests.tests().stream().anyMatch(test -> test(candidate, test, frame));
			return tests.negated() ? !match : match;
		}
		private boolean test(Object candidate, RuntimeUnaryTest test, Frame frame) {
			return switch (test) {
				case RuntimeComparisonUnaryTest it ->
					comparison(it.operator(), candidate, expression(it.endpoint(), frame));
				case RuntimeRangeUnaryTest it -> contains((RuntimeRangeValue) expression(it.range(), frame), candidate);
				case RuntimeExpressionUnaryTest it -> {
					Object expected = expression(it.expression(), frame);
					yield expected instanceof Boolean bool ? bool : equal(candidate, expected);
				}
			};
		}

		private Object builtin(String name, List<Object> arguments) {
			RuntimeBuiltinOperation operation = RuntimeBuiltinOperation.find(name)
					.orElseThrow(() -> new DmnEvaluationException("Unsupported built-in function '" + name + "'"));
			return switch (operation) {
				case NOT -> not(argument(arguments, 0));
				case STRING -> argument(arguments, 0) == null ? null : String.valueOf(argument(arguments, 0));
				case NUMBER ->
					argument(arguments, 0) == null ? null : new BigDecimal(String.valueOf(argument(arguments, 0)));
				case DATE ->
					argument(arguments, 0) == null ? null : LocalDate.parse(String.valueOf(argument(arguments, 0)));
				case TIME ->
					argument(arguments, 0) == null ? null : LocalTime.parse(String.valueOf(argument(arguments, 0)));
				case DATE_AND_TIME ->
					argument(arguments, 0) == null ? null : LocalDateTime.parse(String.valueOf(argument(arguments, 0)));
				case DURATION -> argument(arguments, 0) == null
						? null
						: DmnRuntime.parseDuration(String.valueOf(argument(arguments, 0)));
				case COUNT -> BigDecimal.valueOf(listArgument(arguments).size());
				case SUM ->
					listArgument(arguments).stream().map(DmnRuntime::number).reduce(BigDecimal.ZERO, BigDecimal::add);
				case MIN -> listArgument(arguments).stream().min(DmnRuntime::compare).orElse(null);
				case MAX -> listArgument(arguments).stream().max(DmnRuntime::compare).orElse(null);
				case ABS -> argument(arguments, 0) == null ? null : number(argument(arguments, 0)).abs();
				case SUBSTRING -> substring(arguments);
				case SUBSTRING_BEFORE -> substringBefore(arguments);
				case SUBSTRING_AFTER -> substringAfter(arguments);
				case STRING_LENGTH -> argument(arguments, 0) == null
						? null
						: BigDecimal.valueOf(String.valueOf(argument(arguments, 0)).length());
				case UPPER_CASE -> argument(arguments, 0) == null
						? null
						: String.valueOf(argument(arguments, 0)).toUpperCase(Locale.ROOT);
				case LOWER_CASE -> argument(arguments, 0) == null
						? null
						: String.valueOf(argument(arguments, 0)).toLowerCase(Locale.ROOT);
				case CONTAINS -> argument(arguments, 0) != null && argument(arguments, 1) != null
						&& String.valueOf(argument(arguments, 0)).contains(String.valueOf(argument(arguments, 1)));
				case STARTS_WITH -> argument(arguments, 0) != null && argument(arguments, 1) != null
						&& String.valueOf(argument(arguments, 0)).startsWith(String.valueOf(argument(arguments, 1)));
				case ENDS_WITH -> argument(arguments, 0) != null && argument(arguments, 1) != null
						&& String.valueOf(argument(arguments, 0)).endsWith(String.valueOf(argument(arguments, 1)));
				case MATCHES -> argument(arguments, 0) != null && argument(arguments, 1) != null
						&& String.valueOf(argument(arguments, 0)).matches(String.valueOf(argument(arguments, 1)));
				case REPLACE -> replace(arguments);
				case SPLIT -> split(arguments);
				case FLOOR -> argument(arguments, 0) == null
						? null
						: number(argument(arguments, 0)).setScale(0, java.math.RoundingMode.FLOOR);
				case CEILING -> argument(arguments, 0) == null
						? null
						: number(argument(arguments, 0)).setScale(0, java.math.RoundingMode.CEILING);
				case DECIMAL -> rounded(arguments, java.math.RoundingMode.HALF_EVEN);
				case ROUND_HALF_UP -> argument(arguments, 0) == null
						? null
						: number(argument(arguments, 0)).setScale(number(argument(arguments, 1)).intValueExact(),
								java.math.RoundingMode.HALF_UP);
				case ROUND_HALF_EVEN -> argument(arguments, 0) == null
						? null
						: number(argument(arguments, 0)).setScale(number(argument(arguments, 1)).intValueExact(),
								java.math.RoundingMode.HALF_EVEN);
				case SUBLIST -> sublist(arguments);
				case CONCATENATE -> concatenate(arguments);
				case DISTINCT_VALUES -> distinctValues(arguments);
				case FLATTEN -> flatten(arguments);
				case REVERSE -> reverse(arguments);
				case INDEX_OF -> indexOf(arguments);
				case SORT -> sort(arguments);
				case LIST_REPLACE -> listReplace(arguments);
				case YEARS_AND_MONTHS_DURATION -> yearsAndMonthsDuration(arguments);
			};
		}

		private Object substring(List<Object> args) {
			if (args.getFirst() == null)
				return null;
			String str = String.valueOf(args.getFirst());
			Integer start = integer(args.get(1));
			if (start == null)
				return null;
			int idx = start > 0 ? start - 1 : str.length() + start;
			if (idx < 0 || idx >= str.length())
				return "";
			if (args.size() > 2 && args.get(2) != null) {
				Integer len = integer(args.get(2));
				if (len == null)
					return null;
				int end = Math.min(str.length(), idx + len);
				return str.substring(idx, end);
			}
			return str.substring(idx);
		}

		private Object rounded(List<Object> args, java.math.RoundingMode mode) {
			if (argument(args, 0) == null)
				return null;
			Integer scale = integer(argument(args, 1));
			return scale == null ? null : number(argument(args, 0)).setScale(scale, mode);
		}

		private Integer integer(Object value) {
			if (value == null)
				return null;
			return number(value).intValue();
		}

		private Object substringBefore(List<Object> args) {
			if (args.get(0) == null || args.get(1) == null)
				return null;
			String str = String.valueOf(args.get(0));
			String sub = String.valueOf(args.get(1));
			int idx = str.indexOf(sub);
			return idx < 0 ? "" : str.substring(0, idx);
		}

		private Object substringAfter(List<Object> args) {
			if (args.get(0) == null || args.get(1) == null)
				return null;
			String str = String.valueOf(args.get(0));
			String sub = String.valueOf(args.get(1));
			int idx = str.indexOf(sub);
			return idx < 0 ? "" : str.substring(idx + sub.length());
		}

		private Object replace(List<Object> args) {
			if (args.get(0) == null || args.get(1) == null || args.get(2) == null)
				return null;
			String input = String.valueOf(args.get(0));
			String pattern = String.valueOf(args.get(1));
			String replacement = String.valueOf(args.get(2));
			return input.replaceAll(pattern, replacement);
		}

		private Object split(List<Object> args) {
			if (args.get(0) == null || args.get(1) == null)
				return null;
			String input = String.valueOf(args.get(0));
			String pattern = String.valueOf(args.get(1));
			return Arrays.asList(input.split(pattern));
		}

		private Object sublist(List<Object> args) {
			List<Object> list = listArgument(args);
			int start = number(args.get(1)).intValueExact();
			int idx = start > 0 ? start - 1 : list.size() + start;
			if (idx < 0 || idx >= list.size())
				return List.of();
			if (args.size() > 2 && args.get(2) != null) {
				int len = number(args.get(2)).intValueExact();
				int end = Math.min(list.size(), idx + len);
				return list.subList(idx, end);
			}
			return list.subList(idx, list.size());
		}

		private Object concatenate(List<Object> args) {
			List<Object> result = new ArrayList<>();
			for (Object arg : args) {
				if (arg instanceof List<?> l)
					result.addAll(l);
				else if (arg != null)
					result.add(arg);
			}
			return List.copyOf(result);
		}

		private Object distinctValues(List<Object> args) {
			List<Object> list = listArgument(args);
			List<Object> result = new ArrayList<>();
			for (Object item : list) {
				if (result.stream().noneMatch(existing -> equal(existing, item))) {
					result.add(item);
				}
			}
			return List.copyOf(result);
		}

		private Object flatten(List<Object> args) {
			List<Object> result = new ArrayList<>();
			flattenInto(args.size() == 1 ? args.getFirst() : args, result);
			return List.copyOf(result);
		}

		private void flattenInto(Object item, List<Object> target) {
			if (item instanceof List<?> list) {
				list.forEach(elem -> flattenInto(elem, target));
			} else if (item != null) {
				target.add(item);
			}
		}

		private Object reverse(List<Object> args) {
			List<Object> list = new ArrayList<>(listArgument(args));
			Collections.reverse(list);
			return List.copyOf(list);
		}

		private Object indexOf(List<Object> args) {
			List<Object> list = listArgument(args);
			Object target = args.get(1);
			List<Object> indices = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				if (equal(list.get(i), target)) {
					indices.add(BigDecimal.valueOf(i + 1));
				}
			}
			return List.copyOf(indices);
		}

		private Object sort(List<Object> args) {
			if (args.isEmpty() || args.getFirst() == null)
				return null;
			List<Object> list = new ArrayList<>(listArgument(args));
			if (args.size() > 1 && args.get(1) instanceof CallableValue fn) {
				list.sort((a, b) -> {
					Object res = fn.call(List.of(a, b), Map.of());
					return Boolean.TRUE.equals(res) ? -1 : (Boolean.FALSE.equals(res) ? 1 : 0);
				});
			} else {
				list.sort((a, b) -> compare(a, b));
			}
			return List.copyOf(list);
		}

		private Object listReplace(List<Object> args) {
			if (args.size() < 3 || args.get(0) == null)
				return null;
			List<Object> list = new ArrayList<>(listArgument(args));
			Object second = args.get(1);
			Object newItem = args.get(2);
			if (second instanceof Number n) {
				int pos = n.intValue();
				int idx = pos > 0 ? pos - 1 : list.size() + pos;
				if (idx >= 0 && idx < list.size()) {
					list.set(idx, newItem);
				}
			} else if (second instanceof CallableValue fn) {
				for (int i = 0; i < list.size(); i++) {
					Object res = fn.call(List.of(list.get(i)), Map.of());
					if (Boolean.TRUE.equals(res)) {
						list.set(i, newItem);
					}
				}
			}
			return List.copyOf(list);
		}

		private Object yearsAndMonthsDuration(List<Object> args) {
			if (args.get(0) == null || args.get(1) == null)
				return null;
			LocalDateTime dt1 = (LocalDateTime) args.get(0);
			LocalDateTime dt2 = (LocalDateTime) args.get(1);
			Period p = Period.between(dt1.toLocalDate(), dt2.toLocalDate());
			return p;
		}

		private List<Object> listArgument(List<Object> arguments) {
			return arguments.size() == 1 && arguments.getFirst() instanceof List<?>
					? list(arguments.getFirst())
					: arguments;
		}
	}

	private static TemporalAmount parseDuration(String value) {
		return value.contains("T") ? Duration.parse(value) : Period.parse(value);
	}
	private static Object argument(List<Object> values, int index) {
		if (index >= values.size())
			throw new DmnEvaluationException("Missing function argument " + index);
		return values.get(index);
	}
	private static Object not(Object operand) {
		if (operand instanceof Boolean b)
			return !b;
		return null;
	}
	private static Object and(Object left, java.util.function.Supplier<Object> rightSupplier) {
		if (Boolean.FALSE.equals(left))
			return Boolean.FALSE;
		Object right = rightSupplier.get();
		if (Boolean.FALSE.equals(right))
			return Boolean.FALSE;
		if (Boolean.TRUE.equals(left) && Boolean.TRUE.equals(right))
			return Boolean.TRUE;
		return null;
	}
	private static Object or(Object left, java.util.function.Supplier<Object> rightSupplier) {
		if (Boolean.TRUE.equals(left))
			return Boolean.TRUE;
		Object right = rightSupplier.get();
		if (Boolean.TRUE.equals(right))
			return Boolean.TRUE;
		if (Boolean.FALSE.equals(left) && Boolean.FALSE.equals(right))
			return Boolean.FALSE;
		return null;
	}
	private static boolean truth(Object value) {
		return Boolean.TRUE.equals(value);
	}
	private static BigDecimal number(Object value) {
		if (value instanceof BigDecimal decimal)
			return decimal;
		if (value instanceof Number number)
			return new BigDecimal(number.toString());
		throw new DmnEvaluationException(
				"Expected number, got " + (value == null ? "null" : value.getClass().getSimpleName()));
	}
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Integer compare(Object left, Object right) {
		if (left == null || right == null)
			return null;
		if (left instanceof Number && right instanceof Number)
			return number(left).compareTo(number(right));
		if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
			try {
				return comparable.compareTo(right);
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}
	private static boolean equal(Object left, Object right) {
		if (left == null && right == null)
			return true;
		if (left == null || right == null)
			return false;
		if (left instanceof Number && right instanceof Number)
			return number(left).compareTo(number(right)) == 0;
		return Objects.equals(left, right);
	}
	private static boolean comparison(RuntimeUnaryTestOperator operator, Object left, Object right) {
		if (operator == RuntimeUnaryTestOperator.EQUAL)
			return equal(left, right);
		if (operator == RuntimeUnaryTestOperator.NOT_EQUAL)
			return !equal(left, right);
		if (left == null || right == null)
			return false;
		return switch (operator) {
			case LESS -> {
				Integer c = compare(left, right);
				yield c != null && c < 0;
			}
			case LESS_EQUAL -> {
				Integer c = compare(left, right);
				yield c != null && c <= 0;
			}
			case GREATER -> {
				Integer c = compare(left, right);
				yield c != null && c > 0;
			}
			case GREATER_EQUAL -> {
				Integer c = compare(left, right);
				yield c != null && c >= 0;
			}
			default -> false;
		};
	}
	private static boolean contains(RuntimeRangeValue range, Object value) {
		if (value == null)
			return false;
		Integer lowC = compare(value, range.lower());
		boolean lower = range.lower() == null || (range.lowerBoundary() == RuntimeRangeBoundary.CLOSED
				? lowC != null && lowC >= 0
				: lowC != null && lowC > 0);
		Integer upC = compare(value, range.upper());
		boolean upper = range.upper() == null || (range.upperBoundary() == RuntimeRangeBoundary.CLOSED
				? upC != null && upC <= 0
				: upC != null && upC < 0);
		return lower && upper;
	}
	private static List<Object> list(Object value) {
		if (value instanceof List<?> list)
			return new ArrayList<>(list);
		return List.of(value);
	}
	private static boolean instanceOf(Object value, RuntimeType type) {
		if (type.kind() == RuntimeTypeKind.ANY)
			return true;
		if (value == null)
			return type.kind() == RuntimeTypeKind.NULL;
		return switch (type.kind()) {
			case BOOLEAN -> value instanceof Boolean;
			case NUMBER -> value instanceof Number;
			case STRING -> value instanceof String;
			case DATE -> value instanceof LocalDate;
			case TIME -> value instanceof LocalTime || value instanceof OffsetTime;
			case DATE_TIME -> value instanceof LocalDateTime || value instanceof OffsetDateTime;
			case DURATION -> value instanceof TemporalAmount;
			case YEARS_MONTHS_DURATION -> value instanceof Period;
			case DAYS_TIME_DURATION -> value instanceof Duration;
			case LIST -> value instanceof List<?>;
			case RANGE -> value instanceof RuntimeRangeValue;
			case CONTEXT -> value instanceof RuntimeContextValue || value instanceof Map<?, ?>;
			case FUNCTION -> value instanceof CallableValue;
			case ANY -> true;
			case NULL -> false;
		};
	}
}

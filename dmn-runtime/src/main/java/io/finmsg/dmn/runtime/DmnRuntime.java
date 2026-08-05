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
      if (decision == null) throw new DmnEvaluationException("Unknown aggregate ID " + id);
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
    Frame(Frame parent, int size) { this.parent = parent; this.values = new Object[size]; }
    Object get(int depth, int slot) {
      Frame frame = this;
      for (int index = 0; index < depth; index++) {
        frame = frame.parent;
        if (frame == null) throw new DmnEvaluationException("Invalid lexical depth " + depth);
      }
      if (slot >= frame.values.length) throw new DmnEvaluationException("Invalid local slot " + slot);
      return frame.values[slot];
    }
    void set(int slot, Object value) {
      if (slot >= values.length) throw new DmnEvaluationException("Invalid local slot " + slot);
      values[slot] = value;
    }
  }

  private static final class State {
    private final Object[] slots;
    State(Object[] slots) { this.slots = slots; }

    Object expression(RuntimeExpression value, Frame frame) {
      return switch (value) {
        case RuntimeConstant it -> constant(it);
        case RuntimeValueReference it -> slots[it.sourceSlot()];
        case RuntimeLocalReference it -> frame.get(it.lexicalDepth(), it.localSlot());
        case RuntimeUnaryExpression it -> unary(it.operator(), expression(it.operand(), frame));
        case RuntimeBinaryExpression it -> binary(it.operator(), expression(it.left(), frame),
            () -> expression(it.right(), frame));
        case RuntimeConditionalExpression it -> truth(expression(it.condition(), frame))
            ? expression(it.thenExpression(), frame) : expression(it.elseExpression(), frame);
        case RuntimeListExpression it -> it.elements().stream().map(item -> expression(item, frame)).toList();
        case RuntimeFunctionCall it -> builtin(it.function(),
            it.arguments().stream().map(arg -> expression(arg, frame)).toList());
        case RuntimeContextExpression it -> context(it, frame);
        case RuntimePathExpression it -> property(expression(it.source(), frame), it.member(), it.fieldIndex());
        case RuntimeDescendantExpression it -> descendants(expression(it.source(), frame), it.member(), it.fieldIndex());
        case RuntimeRangeExpression it -> new RuntimeRangeValue(
            it.lower().map(v -> expression(v, frame)).orElse(null),
            it.upper().map(v -> expression(v, frame)).orElse(null), it.lowerBoundary(), it.upperBoundary());
        case RuntimeBetweenExpression it -> compare(expression(it.value(), frame), expression(it.lower(), frame)) >= 0
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
          case TIME -> value.value().matches(".*[Z+-][0-9:]*$") ? OffsetTime.parse(value.value()) : LocalTime.parse(value.value());
          case DATE_TIME -> value.value().matches(".*[Z+-][0-9:]*$") ? OffsetDateTime.parse(value.value()) : LocalDateTime.parse(value.value());
          case DURATION -> value.value().contains("T") ? Duration.parse(value.value()) : Period.parse(value.value());
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
      if (operator == RuntimeBinaryOperator.AND) return and(left, right);
      if (operator == RuntimeBinaryOperator.OR) return or(left, right);
      Object r = right.get();
      if (left == null || r == null) {
        return switch (operator) {
          case EQUAL -> equal(left, r);
          case NOT_EQUAL -> !equal(left, r);
          case ADD -> (left instanceof String || r instanceof String) ? String.valueOf(left) + String.valueOf(r) : null;
          default -> null;
        };
      }
      return switch (operator) {
        case ADD -> left instanceof String || r instanceof String ? String.valueOf(left) + r : number(left).add(number(r));
        case SUBTRACT -> number(left).subtract(number(r));
        case MULTIPLY -> number(left).multiply(number(r));
        case DIVIDE -> number(left).divide(number(r), MathContext.DECIMAL128);
        case POWER -> BigDecimal.valueOf(Math.pow(number(left).doubleValue(), number(r).doubleValue()));
        case EQUAL -> equal(left, r);
        case NOT_EQUAL -> !equal(left, r);
        case LESS -> compare(left, r) < 0;
        case LESS_EQUAL -> compare(left, r) <= 0;
        case GREATER -> compare(left, r) > 0;
        case GREATER_EQUAL -> compare(left, r) >= 0;
        case AND, OR -> throw new AssertionError();
      };
    }

    private Object context(RuntimeContextExpression context, Frame frame) {
      List<Object> values = new ArrayList<>();
      Map<String, Object> named = new LinkedHashMap<>();
      for (RuntimeContextEntry entry : context.entries()) {
        Object result = expression(entry.expression(), frame);
        if (entry.name().isBlank()) return result;
        frame.set(entry.localSlot(), result);
        values.add(result);
        named.put(entry.name(), result);
      }
      return new RuntimeContextValue(values, named);
    }

    private Object property(Object source, String name, int index) {
      if (source == null) return null;
      if (source instanceof List<?> list) return list.stream().map(item -> property(item, name, index)).toList();
      if (source instanceof RuntimeContextValue context) return index >= 0 ? context.field(index) : context.field(name);
      if (source instanceof Map<?, ?> map) return map.get(name);
      throw new DmnEvaluationException("Cannot access member '" + name + "' on " + source.getClass().getSimpleName());
    }

    private List<Object> descendants(Object source, String name, int index) {
      List<Object> result = new ArrayList<>();
      descend(source, name, index, result);
      return List.copyOf(result);
    }
    private void descend(Object source, String name, int index, List<Object> result) {
      if (source instanceof List<?> list) { list.forEach(item -> descend(item, name, index, result)); return; }
      if (source instanceof RuntimeContextValue context) {
        Object match = index >= 0 && index < context.fields().size() ? context.field(index) : context.field(name);
        if (match != null) result.add(match);
        context.namedFields().values().forEach(item -> descend(item, name, -1, result));
      } else if (source instanceof Map<?, ?> map) {
        if (map.containsKey(name)) result.add(map.get(name));
        map.values().forEach(item -> descend(item, name, -1, result));
      }
    }

    private Object filter(RuntimeFilterExpression filter, Frame frame) {
      Object source = expression(filter.source(), frame);
      if (!(source instanceof List<?> list)) throw new DmnEvaluationException("Filter source is not a list");
      Object selector = expression(filter.filter(), frame);
      if (selector instanceof BigDecimal number) {
        int position = number.intValueExact();
        int index = position > 0 ? position - 1 : list.size() + position;
        return index >= 0 && index < list.size() ? list.get(index) : null;
      }
      if (selector instanceof Boolean keep) return keep ? List.copyOf(list) : List.of();
      throw new DmnEvaluationException("Filter predicate must evaluate to a number or boolean");
    }

    private List<Object> iterate(List<RuntimeIteration> iterations, int index,
        RuntimeExpression result, Frame frame, List<Object> output) {
      if (index == iterations.size()) { output.add(expression(result, frame)); return output; }
      RuntimeIteration iteration = iterations.get(index);
      for (Object item : iterationValues(iteration, frame)) {
        frame.set(iteration.localSlot(), item);
        iterate(iterations, index + 1, result, frame, output);
      }
      return List.copyOf(output);
    }

    private List<Object> iterationValues(RuntimeIteration iteration, Frame frame) {
      Object start = expression(iteration.source(), frame);
      if (iteration.end().isEmpty()) return list(start);
      int first = number(start).intValueExact();
      int last = number(expression(iteration.end().orElseThrow(), frame)).intValueExact();
      List<Object> result = new ArrayList<>();
      int step = first <= last ? 1 : -1;
      for (int value = first; ; value += step) { result.add(BigDecimal.valueOf(value)); if (value == last) break; }
      return result;
    }

    private boolean quantify(RuntimeQuantifiedExpression value, int index, Frame frame) {
      if (index == value.bindings().size()) return truth(expression(value.satisfies(), frame));
      RuntimeQuantifiedBinding binding = value.bindings().get(index);
      for (Object item : list(expression(binding.source(), frame))) {
        frame.set(binding.localSlot(), item);
        boolean match = quantify(value, index + 1, frame);
        if (value.quantifier() == RuntimeQuantifier.SOME && match) return true;
        if (value.quantifier() == RuntimeQuantifier.EVERY && !match) return false;
      }
      return value.quantifier() == RuntimeQuantifier.EVERY;
    }

    private CallableValue function(RuntimeFunctionDefinition definition, Frame closure) {
      if (definition.external()) return (positional, named) -> {
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
      List<Object> positional = invocation.positionalArguments().stream().map(it -> expression(it, frame)).toList();
      Map<String, Object> named = new LinkedHashMap<>();
      invocation.namedArguments().forEach(it -> named.put(it.name(), expression(it.expression(), frame)));
      if (invocation.function().isPresent()) {
        List<Object> all = new ArrayList<>(positional);
        all.addAll(named.values());
        return builtin(invocation.function().orElseThrow(), all);
      }
      Object target = expression(invocation.target().orElseThrow(), frame);
      if (!(target instanceof CallableValue callable)) throw new DmnEvaluationException("Invocation target is not a function");
      return callable.call(positional, named);
    }

    private Object relation(RuntimeRelationExpression relation, Frame frame) {
      List<Object> rows = new ArrayList<>();
      for (List<RuntimeExpression> row : relation.rows()) {
        List<Object> fields = row.stream().map(it -> expression(it, frame)).toList();
        Map<String, Object> named = new LinkedHashMap<>();
        for (int index = 0; index < fields.size(); index++) named.put(relation.columns().get(index).name(), fields.get(index));
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
        if (matchesRule) matches.add(rule.outputEntries().stream().map(it -> expression(it, frame)).toList());
      }
      if (matches.isEmpty()) return defaultOutput(table, frame);
      return switch (table.hitPolicy()) {
        case UNIQUE -> {
          if (matches.size() != 1) throw new DmnEvaluationException("UNIQUE table matched " + matches.size() + " rules");
          yield output(matches.getFirst(), table.outputs());
        }
        case FIRST, PRIORITY -> output(matches.getFirst(), table.outputs());
        case ANY -> {
          Object first = output(matches.getFirst(), table.outputs());
          if (matches.stream().map(row -> output(row, table.outputs())).anyMatch(row -> !equal(first, row)))
            throw new DmnEvaluationException("ANY table produced different outputs");
          yield first;
        }
        case RULE_ORDER, OUTPUT_ORDER -> matches.stream().map(row -> output(row, table.outputs())).toList();
        case COLLECT -> aggregate(matches.stream().map(row -> output(row, table.outputs())).toList(), table.aggregation());
      };
    }

    private Object defaultOutput(RuntimeDecisionTable table, Frame frame) {
      List<Object> values = table.outputs().stream().map(output -> output.defaultValue()
          .map(it -> expression(it, frame)).orElse(null)).toList();
      return output(values, table.outputs());
    }
    private Object output(List<Object> values, List<RuntimeDecisionTableOutput> outputs) {
      if (values.size() == 1) return values.getFirst();
      Map<String, Object> named = new LinkedHashMap<>();
      for (int index = 0; index < values.size(); index++)
        named.put(outputs.get(index).name().orElse("output" + (index + 1)), values.get(index));
      return new RuntimeContextValue(values, named);
    }
    private Object aggregate(List<Object> values, Optional<RuntimeAggregation> aggregation) {
      if (aggregation.isEmpty()) return values;
      return switch (aggregation.orElseThrow()) {
        case COUNT -> BigDecimal.valueOf(values.size());
        case SUM -> values.stream().map(DmnRuntime::number).reduce(BigDecimal.ZERO, BigDecimal::add);
        case MIN -> values.stream().min(DmnRuntime::compare).orElse(null);
        case MAX -> values.stream().max(DmnRuntime::compare).orElse(null);
      };
    }

    private boolean tests(Object candidate, RuntimeUnaryTests tests, Frame frame) {
      boolean match = tests.wildcard() || tests.tests().stream().anyMatch(test -> test(candidate, test, frame));
      return tests.negated() ? !match : match;
    }
    private boolean test(Object candidate, RuntimeUnaryTest test, Frame frame) {
      return switch (test) {
        case RuntimeComparisonUnaryTest it -> comparison(it.operator(), candidate, expression(it.endpoint(), frame));
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
        case NOT -> !truth(argument(arguments, 0));
        case STRING -> String.valueOf(argument(arguments, 0));
        case NUMBER -> new BigDecimal(String.valueOf(argument(arguments, 0)));
        case DATE -> LocalDate.parse(String.valueOf(argument(arguments, 0)));
        case TIME -> LocalTime.parse(String.valueOf(argument(arguments, 0)));
        case DATE_AND_TIME -> LocalDateTime.parse(String.valueOf(argument(arguments, 0)));
        case DURATION -> DmnRuntime.parseDuration(String.valueOf(argument(arguments, 0)));
        case COUNT -> BigDecimal.valueOf(list(argument(arguments, 0)).size());
        case SUM -> listArgument(arguments).stream().map(DmnRuntime::number).reduce(BigDecimal.ZERO, BigDecimal::add);
        case MIN -> listArgument(arguments).stream().min(DmnRuntime::compare).orElse(null);
        case MAX -> listArgument(arguments).stream().max(DmnRuntime::compare).orElse(null);
        case ABS -> number(argument(arguments, 0)).abs();
      };
    }
    private List<Object> listArgument(List<Object> arguments) {
      return arguments.size() == 1 && arguments.getFirst() instanceof List<?> ? list(arguments.getFirst()) : arguments;
    }
  }

  private static TemporalAmount parseDuration(String value) { return value.contains("T") ? Duration.parse(value) : Period.parse(value); }
  private static Object argument(List<Object> values, int index) {
    if (index >= values.size()) throw new DmnEvaluationException("Missing function argument " + index);
    return values.get(index);
  }
  private static Object not(Object operand) {
    if (operand instanceof Boolean b) return !b;
    return null;
  }
  private static Object and(Object left, java.util.function.Supplier<Object> rightSupplier) {
    if (Boolean.FALSE.equals(left)) return Boolean.FALSE;
    Object right = rightSupplier.get();
    if (Boolean.FALSE.equals(right)) return Boolean.FALSE;
    if (Boolean.TRUE.equals(left) && Boolean.TRUE.equals(right)) return Boolean.TRUE;
    return null;
  }
  private static Object or(Object left, java.util.function.Supplier<Object> rightSupplier) {
    if (Boolean.TRUE.equals(left)) return Boolean.TRUE;
    Object right = rightSupplier.get();
    if (Boolean.TRUE.equals(right)) return Boolean.TRUE;
    if (Boolean.FALSE.equals(left) && Boolean.FALSE.equals(right)) return Boolean.FALSE;
    return null;
  }
  private static boolean truth(Object value) {
    return Boolean.TRUE.equals(value);
  }
  private static BigDecimal number(Object value) {
    if (value instanceof BigDecimal decimal) return decimal;
    if (value instanceof Number number) return new BigDecimal(number.toString());
    throw new DmnEvaluationException("Expected number, got " + (value == null ? "null" : value.getClass().getSimpleName()));
  }
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compare(Object left, Object right) {
    if (left == null || right == null) throw new DmnEvaluationException("Cannot compare null values");
    if (left instanceof Number && right instanceof Number) return number(left).compareTo(number(right));
    if (left instanceof Comparable comparable && left.getClass().isInstance(right)) return comparable.compareTo(right);
    throw new DmnEvaluationException("Values are not comparable: " + left + " and " + right);
  }
  private static boolean equal(Object left, Object right) {
    if (left == null && right == null) return true;
    if (left == null || right == null) return false;
    if (left instanceof Number && right instanceof Number) return number(left).compareTo(number(right)) == 0;
    return Objects.equals(left, right);
  }
  private static boolean comparison(RuntimeUnaryTestOperator operator, Object left, Object right) {
    return switch (operator) {
      case EQUAL -> equal(left, right); case NOT_EQUAL -> !equal(left, right);
      case LESS -> compare(left, right) < 0; case LESS_EQUAL -> compare(left, right) <= 0;
      case GREATER -> compare(left, right) > 0; case GREATER_EQUAL -> compare(left, right) >= 0;
    };
  }
  private static boolean contains(RuntimeRangeValue range, Object value) {
    boolean lower = range.lower() == null || (range.lowerBoundary() == RuntimeRangeBoundary.CLOSED
        ? compare(value, range.lower()) >= 0 : compare(value, range.lower()) > 0);
    boolean upper = range.upper() == null || (range.upperBoundary() == RuntimeRangeBoundary.CLOSED
        ? compare(value, range.upper()) <= 0 : compare(value, range.upper()) < 0);
    return lower && upper;
  }
  private static List<Object> list(Object value) {
    if (value instanceof List<?> list) return new ArrayList<>(list);
    return List.of(value);
  }
  private static boolean instanceOf(Object value, RuntimeType type) {
    if (type.kind() == RuntimeTypeKind.ANY) return true;
    if (value == null) return type.kind() == RuntimeTypeKind.NULL;
    return switch (type.kind()) {
      case BOOLEAN -> value instanceof Boolean; case NUMBER -> value instanceof Number;
      case STRING -> value instanceof String; case DATE -> value instanceof LocalDate;
      case TIME -> value instanceof LocalTime || value instanceof OffsetTime;
      case DATE_TIME -> value instanceof LocalDateTime || value instanceof OffsetDateTime;
      case DURATION -> value instanceof TemporalAmount;
      case YEARS_MONTHS_DURATION -> value instanceof Period;
      case DAYS_TIME_DURATION -> value instanceof Duration;
      case LIST -> value instanceof List<?>; case RANGE -> value instanceof RuntimeRangeValue;
      case CONTEXT -> value instanceof RuntimeContextValue || value instanceof Map<?, ?>;
      case FUNCTION -> value instanceof CallableValue; case ANY -> true; case NULL -> false;
    };
  }
}

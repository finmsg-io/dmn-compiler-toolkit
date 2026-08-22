package io.finmsg.dmn.runtime;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.*;
import java.time.temporal.TemporalAmount;
import java.util.*;

/** Deterministic, side-effect-free interpreter for {@link RuntimeModel}. */
public final class DmnRuntime {
	public record NamedZoneTime(LocalTime value, ZoneId zone) {
		@Override
		public String toString() {
			return java.time.format.DateTimeFormatter.ISO_LOCAL_TIME.format(value) + "@" + zone.getId();
		}
	}

	public record NamedZoneDateTime(LocalDateTime value, ZoneId zone) {
		@Override
		public String toString() {
			return java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value) + "@" + zone.getId();
		}
	}

	public DmnEvaluationResult evaluate(RuntimeModel model, Map<Integer, ?> inputs) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(inputs, "inputs");
		Object[] slots = new Object[model.valueSlotCount()];
		for (RuntimeInput input : model.inputs()) {
			if (!inputs.containsKey(input.valueSlot())) {
				throw new DmnEvaluationException("Missing input value for slot " + input.valueSlot());
			}
			slots[input.valueSlot()] = coerce(inputs.get(input.valueSlot()), input.type());
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
					.orElseGet(() -> decision.decisionTable().map(it -> state.table(it, frame)).orElse(slots[decision.resultSlot()]));
			slots[decision.resultSlot()] = coerce(value, decision.type());
		}
		return new DmnEvaluationResult(model, Arrays.asList(slots.clone()));
	}

	public static Object coerce(Object value, RuntimeType targetType) {
		if (value == null || targetType == null || targetType.kind() == RuntimeTypeKind.ANY) {
			return value;
		}
		if (targetType.kind() == RuntimeTypeKind.LIST) {
			RuntimeType elemType = targetType.elementType();
			if (value instanceof List<?> list) {
				if (elemType.kind() == RuntimeTypeKind.ANY) {
					return value;
				}
				List<Object> result = new ArrayList<>();
				for (Object item : list) {
					Object c = coerce(item, elemType);
					if (c == null && item != null)
						return null;
					result.add(c);
				}
				return Collections.unmodifiableList(result);
			}
			Object coerced = coerce(value, targetType.elementType());
			if (coerced == null)
				return null;
			return List.of(coerced);
		}
		if (value instanceof List<?> list) {
			if (list.size() == 1) {
				Object item = list.get(0);
				if (item instanceof List<?>)
					return null;
				return coerce(item, targetType);
			}
			return null;
		}
		if (targetType.kind() == RuntimeTypeKind.CONTEXT && !targetType.fieldLayout().isEmpty()) {
			Map<?, ?> map = value instanceof RuntimeContextValue ctx
					? ctx.namedFields()
					: value instanceof Map<?, ?> m ? m : null;
			if (map == null)
				return null;
			List<RuntimeField> fields = targetType.fieldLayout();
			for (RuntimeField field : fields) {
				if (!map.containsKey(field.name()))
					return null;
				Object fieldVal = map.get(field.name());
				Object coercedField = coerce(fieldVal, field.type());
				if (coercedField == null && fieldVal != null)
					return null;
			}
		}
		return switch (targetType.kind()) {
			case NUMBER -> value instanceof Number ? value : null;
			case STRING -> value instanceof CharSequence ? value : null;
			case BOOLEAN -> value instanceof Boolean ? value : null;
			case DATE -> value instanceof LocalDate ? value : null;
			case TIME -> (value instanceof LocalTime || value instanceof OffsetTime || value instanceof NamedZoneTime)
					? value
					: null;
			case DATE_TIME -> (value instanceof LocalDateTime || value instanceof OffsetDateTime
					|| value instanceof ZonedDateTime || value instanceof NamedZoneDateTime) ? value : null;
			case DURATION -> value instanceof TemporalAmount ? value : null;
			default -> value;
		};
	}

	private interface CallableValue {
		Object call(List<Object> positional, Map<String, Object> named);
		default int parameterCount() {
			return -1;
		}
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
				case RuntimeRangeExpression it -> {
					Object low = null;
					boolean lowerAbsent = it.lower().isEmpty();
					if (!lowerAbsent) {
						low = expression(it.lower().get(), frame);
						if (low == null)
							yield null;
					}
					Object up = null;
					boolean upperAbsent = it.upper().isEmpty();
					if (!upperAbsent) {
						up = expression(it.upper().get(), frame);
						if (up == null)
							yield null;
					}
					if (low != null && up != null) {
						Integer c = compare(low, up);
						if (c == null || c > 0)
							yield null;
					}
					yield new RuntimeRangeValue(low, up, it.lowerBoundary(), it.upperBoundary(), lowerAbsent,
							upperAbsent);
				}
				case RuntimeBetweenExpression it ->
					compare(expression(it.value(), frame), expression(it.lower(), frame)) >= 0
							&& compare(expression(it.value(), frame), expression(it.upper(), frame)) <= 0;
				case RuntimeInExpression it -> {
					Object val = expression(it.value(), frame);
					if (val == null)
						yield null;
					Boolean res = false;
					for (RuntimeUnaryTest t : it.tests().tests()) {
						Object match = test(val, t, frame);
						if (Boolean.TRUE.equals(match))
							yield it.tests().negated() ? false : true;
						if (match == null)
							res = null;
					}
					yield res == null ? null : (it.tests().negated() ? !res : res);
				}
				case RuntimeInstanceOfExpression it -> instanceOf(expression(it.expression(), frame), it.testedType());
				case RuntimeForExpression it -> iterate(it, it.iterations(), 0, frame, new ArrayList<>());
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
					case TIME -> parseTime(value.value());
					case DATE_TIME -> parseDateTime(value.value());
					case DURATION -> parseDuration(value.value());
				};
			} catch (RuntimeException exception) {
				return null;
			}
		}

		private Object unary(RuntimeUnaryOperator operator, Object operand) {
			return switch (operator) {
				case POSITIVE -> operand;
				case NEGATE -> {
					if (operand == null)
						yield null;
					if (operand instanceof Duration dur)
						yield dur.negated();
					if (operand instanceof Period p)
						yield p.negated();
					BigDecimal num = number(operand);
					yield num != null ? num.negate() : null;
				}
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
					case NOT_EQUAL -> {
						Boolean eq = equal(left, r);
						yield eq == null ? null : !eq;
					}
					default -> null;
				};
			}
			return switch (operator) {
				case ADD -> addValues(left, r);
				case SUBTRACT -> subtractValues(left, r);
				case MULTIPLY -> multiplyValues(left, r);
				case DIVIDE -> divideValues(left, r);
				case POWER -> powerValues(left, r);
				case EQUAL -> equal(left, r);
				case NOT_EQUAL -> {
					Boolean eq = equal(left, r);
					yield eq == null ? null : !eq;
				}
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
				if (entry.name().isEmpty() && entry.localSlot() == -1)
					return result;
				if (entry.localSlot() >= 0)
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
				return (index >= 0 && index < context.fields().size()) ? context.field(index) : context.field(name);
			if (source instanceof Map<?, ?> map)
				return map.get(name);
			if (source instanceof LocalDate ld) {
				return switch (name) {
					case "year" -> BigDecimal.valueOf(ld.getYear());
					case "month" -> BigDecimal.valueOf(ld.getMonthValue());
					case "day" -> BigDecimal.valueOf(ld.getDayOfMonth());
					case "weekday" -> BigDecimal.valueOf(ld.getDayOfWeek().getValue());
					default -> null;
				};
			}
			if (source instanceof LocalTime lt) {
				return switch (name) {
					case "hour" -> BigDecimal.valueOf(lt.getHour());
					case "minute" -> BigDecimal.valueOf(lt.getMinute());
					case "second" -> BigDecimal.valueOf(lt.getSecond());
					case "time offset", "timezone" -> null;
					default -> null;
				};
			}
			if (source instanceof OffsetTime ot) {
				return switch (name) {
					case "hour" -> BigDecimal.valueOf(ot.getHour());
					case "minute" -> BigDecimal.valueOf(ot.getMinute());
					case "second" -> BigDecimal.valueOf(ot.getSecond());
					case "time offset" -> Duration.ofSeconds(ot.getOffset().getTotalSeconds());
					case "timezone" -> ot.getOffset().getId();
					default -> null;
				};
			}
			if (source instanceof NamedZoneTime nzt) {
				return switch (name) {
					case "hour" -> BigDecimal.valueOf(nzt.value().getHour());
					case "minute" -> BigDecimal.valueOf(nzt.value().getMinute());
					case "second" -> BigDecimal.valueOf(nzt.value().getSecond());
					case "time offset" -> null;
					case "timezone" -> nzt.zone().getId();
					default -> null;
				};
			}
			if (source instanceof LocalDateTime ldt) {
				return switch (name) {
					case "year" -> BigDecimal.valueOf(ldt.getYear());
					case "month" -> BigDecimal.valueOf(ldt.getMonthValue());
					case "day" -> BigDecimal.valueOf(ldt.getDayOfMonth());
					case "weekday" -> BigDecimal.valueOf(ldt.getDayOfWeek().getValue());
					case "hour" -> BigDecimal.valueOf(ldt.getHour());
					case "minute" -> BigDecimal.valueOf(ldt.getMinute());
					case "second" -> BigDecimal.valueOf(ldt.getSecond());
					case "time offset", "timezone" -> null;
					default -> null;
				};
			}
			if (source instanceof OffsetDateTime odt) {
				return switch (name) {
					case "year" -> BigDecimal.valueOf(odt.getYear());
					case "month" -> BigDecimal.valueOf(odt.getMonthValue());
					case "day" -> BigDecimal.valueOf(odt.getDayOfMonth());
					case "weekday" -> BigDecimal.valueOf(odt.getDayOfWeek().getValue());
					case "hour" -> BigDecimal.valueOf(odt.getHour());
					case "minute" -> BigDecimal.valueOf(odt.getMinute());
					case "second" -> BigDecimal.valueOf(odt.getSecond());
					case "time offset" -> Duration.ofSeconds(odt.getOffset().getTotalSeconds());
					case "timezone" -> odt.getOffset().getId();
					default -> null;
				};
			}
			if (source instanceof ZonedDateTime zdt) {
				return switch (name) {
					case "year" -> BigDecimal.valueOf(zdt.getYear());
					case "month" -> BigDecimal.valueOf(zdt.getMonthValue());
					case "day" -> BigDecimal.valueOf(zdt.getDayOfMonth());
					case "weekday" -> BigDecimal.valueOf(zdt.getDayOfWeek().getValue());
					case "hour" -> BigDecimal.valueOf(zdt.getHour());
					case "minute" -> BigDecimal.valueOf(zdt.getMinute());
					case "second" -> BigDecimal.valueOf(zdt.getSecond());
					case "time offset" -> Duration.ofSeconds(zdt.getOffset().getTotalSeconds());
					case "timezone" -> zdt.getZone().getId();
					default -> null;
				};
			}
			if (source instanceof NamedZoneDateTime nzdt) {
				return switch (name) {
					case "year" -> BigDecimal.valueOf(nzdt.value().getYear());
					case "month" -> BigDecimal.valueOf(nzdt.value().getMonthValue());
					case "day" -> BigDecimal.valueOf(nzdt.value().getDayOfMonth());
					case "weekday" -> BigDecimal.valueOf(nzdt.value().getDayOfWeek().getValue());
					case "hour" -> BigDecimal.valueOf(nzdt.value().getHour());
					case "minute" -> BigDecimal.valueOf(nzdt.value().getMinute());
					case "second" -> BigDecimal.valueOf(nzdt.value().getSecond());
					case "time offset" ->
						Duration.ofSeconds(nzdt.zone().getRules().getOffset(nzdt.value()).getTotalSeconds());
					case "timezone" -> nzdt.zone().getId();
					default -> null;
				};
			}
			if (source instanceof Period p) {
				return switch (name) {
					case "years" -> BigDecimal.valueOf(p.getYears());
					case "months" -> BigDecimal.valueOf(p.getMonths());
					default -> null;
				};
			}
			if (source instanceof Duration dur) {
				return switch (name) {
					case "days" -> BigDecimal.valueOf(dur.toDays());
					case "hours" -> BigDecimal.valueOf(dur.toHoursPart());
					case "minutes" -> BigDecimal.valueOf(dur.toMinutesPart());
					case "seconds" -> BigDecimal.valueOf(dur.toSecondsPart());
					default -> null;
				};
			}
			if (source instanceof RuntimeRangeValue range) {
				return switch (name) {
					case "start" -> range.lower();
					case "end" -> range.upper() != null ? range.upper() : (range.lowerBoundary() == RuntimeRangeBoundary.CLOSED && range.upperBoundary() == RuntimeRangeBoundary.CLOSED ? range.lower() : null);
					case "start included" -> range.lowerBoundary() == RuntimeRangeBoundary.CLOSED;
					case "end included" -> range.upperBoundary() == RuntimeRangeBoundary.CLOSED;
					default -> null;
				};
			}
			return null;
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
				Frame child = new Frame(frame, filter.localSlot() + 1);
				child.set(filter.localSlot(), item);
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

		private List<Object> iterate(RuntimeForExpression forExpr, List<RuntimeIteration> iterations, int index,
				Frame frame, List<Object> output) {
			if (index == iterations.size()) {
				if (forExpr.partialSlot() >= 0) {
					frame.set(forExpr.partialSlot(), List.copyOf(output));
				}
				output.add(expression(forExpr.result(), frame));
				return output;
			}
			RuntimeIteration iteration = iterations.get(index);
			List<Object> values = iterationValues(iteration, frame);
			if (values == null)
				return null;
			for (Object item : values) {
				frame.set(iteration.localSlot(), item);
				if (iterate(forExpr, iterations, index + 1, frame, output) == null)
					return null;
			}
			return List.copyOf(output);
		}

		private List<Object> iterationValues(RuntimeIteration iteration, Frame frame) {
			Object start = expression(iteration.source(), frame);
			if (start == null || start instanceof RuntimeRangeValue)
				return null;
			if (iteration.end().isEmpty()) {
				if (!(start instanceof List<?>))
					return null;
				return list(start);
			}
			Object end = expression(iteration.end().orElseThrow(), frame);
			if (end == null || end instanceof RuntimeRangeValue)
				return null;
			if (start instanceof LocalDate ldStart && end instanceof LocalDate ldEnd) {
				List<Object> result = new ArrayList<>();
				if (ldStart.isBefore(ldEnd) || ldStart.isEqual(ldEnd)) {
					for (LocalDate cur = ldStart; !cur.isAfter(ldEnd); cur = cur.plusDays(1)) {
						result.add(cur);
					}
				} else {
					for (LocalDate cur = ldStart; !cur.isBefore(ldEnd); cur = cur.minusDays(1)) {
						result.add(cur);
					}
				}
				return result;
			}
			BigDecimal nStart = number(start);
			BigDecimal nEnd = number(end);
			if (nStart == null || nEnd == null)
				return null;
			try {
				int first = nStart.intValueExact();
				int last = nEnd.intValueExact();
				List<Object> result = new ArrayList<>();
				int step = first <= last ? 1 : -1;
				for (int value = first;; value += step) {
					result.add(BigDecimal.valueOf(value));
					if (value == last)
						break;
				}
				return result;
			} catch (Exception e) {
				return null;
			}
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
			if (definition.external()) {
				Frame bodyFrame = definition.localSlotCount() > 0
						? new Frame(closure, definition.localSlotCount())
						: closure;
				Object desc = definition.body().isPresent() ? expression(definition.body().get(), bodyFrame) : null;
				return (positional, named) -> {
					List<Object> args = new ArrayList<>(positional);
					args.addAll(named.values());
					return invokeExternalJava(desc, args);
				};
			}
			if (definition.parameters().isEmpty() && definition.body().isPresent()
					&& definition.body().get() instanceof RuntimeInvocationExpression inv
					&& inv.function().isPresent()) {
				return (positional, named) -> {
					List<Object> args = new ArrayList<>(positional);
					args.addAll(named.values());
					List<List<String>> overloads = BUILTIN_PARAM_SPECS.get(inv.function().get());
					if (overloads != null && !isVariadicBuiltin(inv.function().get())
							&& overloads.stream().noneMatch(spec -> spec.size() == args.size()))
						return null;
					return builtin(inv.function().get(), args);
				};
			}
			return new CallableValue() {
				@Override
				public Object call(List<Object> positional, Map<String, Object> named) {
					if (!positional.isEmpty()) {
						if (positional.size() != definition.parameters().size()) {
							return null;
						}
					} else if (!named.isEmpty()) {
						if (named.size() != definition.parameters().size()) {
							return null;
						}
						for (RuntimeFunctionParameter param : definition.parameters()) {
							if (!named.containsKey(param.name())) {
								return null;
							}
						}
					} else if (!definition.parameters().isEmpty()) {
						return null;
					}
					Frame call = new Frame(closure, definition.localSlotCount());
					for (int index = 0; index < definition.parameters().size(); index++) {
						RuntimeFunctionParameter parameter = definition.parameters().get(index);
						Object argument = index < positional.size()
								? positional.get(index)
								: named.get(parameter.name());
						Object coercedArg = coerce(argument, parameter.type());
						if (coercedArg == null && argument != null)
							return null;
						call.set(parameter.localSlot(), coercedArg);
					}
					Object bodyVal = expression(definition.body().orElseThrow(), call);
					if (isExternalJavaDescriptor(bodyVal)) {
						List<Object> args = new ArrayList<>(positional);
						args.addAll(named.values());
						return invokeExternalJava(bodyVal, args);
					}
					RuntimeType returnType = definition.type().kind() == RuntimeTypeKind.FUNCTION
							? definition.type().returnType()
							: definition.type();
					return coerce(bodyVal, returnType);
				}
				@Override
				public int parameterCount() {
					return definition.parameters().size();
				}
			};
		}

		private boolean isVariadicBuiltin(String name) {
			return switch (name) {
				case "sum", "min", "max", "count", "mean", "median", "mode", "stddev", "product", "all", "any" -> true;
				default -> false;
			};
		}

		private Object invoke(RuntimeInvocationExpression invocation, Frame frame) {
			List<Object> positional = invocation.positionalArguments().stream().map(it -> expression(it, frame))
					.toList();
			Map<String, Object> named = new LinkedHashMap<>();
			invocation.namedArguments().forEach(it -> named.put(it.name(), expression(it.expression(), frame)));
			if (invocation.function().isPresent()) {
				String fnName = invocation.function().orElseThrow();
				if (!named.isEmpty()) {
					if (!positional.isEmpty())
						return null;
					return invokeBuiltinNamed(fnName, named);
				}
				return builtin(fnName, positional);
			}
			Object target = expression(invocation.target().orElseThrow(), frame);
			if (target instanceof CallableValue callable) {
				return callable.call(positional, named);
			}
			if (isExternalJavaDescriptor(target)) {
				List<Object> all = new ArrayList<>(positional);
				all.addAll(named.values());
				return invokeExternalJava(target, all);
			}
			return null;
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
				case FIRST -> output(matches.getFirst(), table.outputs());
				case PRIORITY -> {
					List<Object> all = matches.stream().map(row -> output(row, table.outputs())).toList();
					List<Object> sorted = sortOutputOrder(all, table.outputs());
					yield sorted.isEmpty() ? null : sorted.getFirst();
				}
				case ANY -> {
					Object first = output(matches.getFirst(), table.outputs());
					if (matches.stream().map(row -> output(row, table.outputs()))
							.anyMatch(row -> !Boolean.TRUE.equals(equal(first, row))))
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
			List<List<Object>> domains = new ArrayList<>();
			boolean hasAnyDomain = false;
			for (RuntimeDecisionTableOutput out : outputs) {
				if (out.allowedValues().isPresent()) {
					List<Object> domain = extractAllowedValues(out.allowedValues().get());
					domains.add(domain);
					if (!domain.isEmpty())
						hasAnyDomain = true;
				} else {
					domains.add(List.of());
				}
			}
			if (!hasAnyDomain) {
				return results;
			}
			List<Object> sorted = new ArrayList<>(results);
			sorted.sort((a, b) -> {
				for (int i = 0; i < outputs.size(); i++) {
					List<Object> domain = domains.get(i);
					if (domain.isEmpty())
						continue;
					Object valA = (a instanceof RuntimeContextValue ctxA)
							? ctxA.field(i)
							: (outputs.size() == 1 ? a : null);
					Object valB = (b instanceof RuntimeContextValue ctxB)
							? ctxB.field(i)
							: (outputs.size() == 1 ? b : null);
					int idxA = domain.indexOf(valA);
					int idxB = domain.indexOf(valB);
					int posA = idxA < 0 ? Integer.MAX_VALUE : idxA;
					int posB = idxB < 0 ? Integer.MAX_VALUE : idxB;
					if (posA != posB)
						return Integer.compare(posA, posB);
				}
				return 0;
			});
			return sorted;
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
			boolean match = tests.wildcard()
					|| tests.tests().stream().anyMatch(test -> Boolean.TRUE.equals(test(candidate, test, frame)));
			return tests.negated() ? !match : match;
		}
		private Object test(Object candidate, RuntimeUnaryTest test, Frame frame) {
			return switch (test) {
				case RuntimeComparisonUnaryTest it ->
					comparison(it.operator(), candidate, expression(it.endpoint(), frame));
				case RuntimeRangeUnaryTest it -> contains((RuntimeRangeValue) expression(it.range(), frame), candidate);
				case RuntimeExpressionUnaryTest it -> {
					Object expected = expression(it.expression(), frame);
					if (expected instanceof Collection<?> col) {
						boolean match = col.stream().anyMatch(elem -> {
							if (elem instanceof RuntimeRangeValue range) {
								return Boolean.TRUE.equals(contains(range, candidate));
							}
							return Boolean.TRUE.equals(equal(candidate, elem));
						});
						if (match)
							yield true;
					}
					if (expected instanceof RuntimeRangeValue range) {
						yield contains(range, candidate);
					}
					yield Boolean.TRUE.equals(equal(candidate, expected));
				}
			};
		}

		private Object invokeBuiltinNamed(String fnName, Map<String, Object> namedArgs) {
			List<Object> args = bindNamedBuiltinArguments(fnName, namedArgs);
			if (args == null)
				return null;
			return builtin(fnName, args);
		}

		private Object builtin(String name, List<Object> arguments) {
			RuntimeBuiltinOperation operation = RuntimeBuiltinOperation.find(name)
					.orElseThrow(() -> new DmnEvaluationException("Unsupported built-in function '" + name + "'"));
			return switch (operation) {
				case NOT -> not(argument(arguments, 0));
				case IS -> arguments.size() == 2 ? isValues(argument(arguments, 0), argument(arguments, 1)) : null;
				case STRING -> formatFeelString(argument(arguments, 0));
				case NUMBER -> feelNumber(arguments);
				case DATE -> {
					if (arguments.size() == 1) {
						Object a = argument(arguments, 0);
						if (a == null)
							yield null;
						if (a instanceof LocalDate ld)
							yield ld;
						if (a instanceof LocalDateTime ldt)
							yield ldt.toLocalDate();
						if (a instanceof OffsetDateTime odt)
							yield odt.toLocalDate();
						if (a instanceof ZonedDateTime zdt)
							yield zdt.toLocalDate();
						if (a instanceof NamedZoneDateTime nzdt)
							yield nzdt.value().toLocalDate();
						yield parseFeelDate(String.valueOf(a));
					}
					if (arguments.size() == 3) {
						Number y = number(arguments.get(0)), m = number(arguments.get(1)), d = number(arguments.get(2));
						if (y == null || m == null || d == null)
							yield null;
						try {
							yield LocalDate.of(y.intValue(), m.intValue(), d.intValue());
						} catch (Exception e) {
							yield null;
						}
					}
					yield null;
				}
				case TIME -> {
					if (arguments.isEmpty())
						yield null;
					if (arguments.size() == 1) {
						Object a = arguments.get(0);
						if (a == null)
							yield null;
						if (a instanceof LocalTime lt)
							yield lt;
						if (a instanceof OffsetTime ot)
							yield ot;
						if (a instanceof NamedZoneTime nzt)
							yield nzt;
						if (a instanceof LocalDate)
							yield OffsetTime.of(LocalTime.MIDNIGHT, ZoneOffset.UTC);
						if (a instanceof LocalDateTime ldt)
							yield ldt.toLocalTime();
						if (a instanceof OffsetDateTime odt)
							yield odt.toOffsetTime();
						if (a instanceof ZonedDateTime zdt)
							yield zdt.toOffsetDateTime().toOffsetTime();
						if (a instanceof NamedZoneDateTime nzdt)
							yield new NamedZoneTime(nzdt.value().toLocalTime(), nzdt.zone());
						yield parseTime(String.valueOf(a));
					}
					if (arguments.size() < 3)
						yield null;
					try {
						Number hour = number(arguments.get(0));
						Number minute = number(arguments.get(1));
						Number second = number(arguments.get(2));
						if (hour == null || minute == null || second == null)
							yield null;
						int h = hour.intValue(), m = minute.intValue();
						BigDecimal sec = number(arguments.get(2));
						int s = sec.intValue();
						int nano = sec.subtract(BigDecimal.valueOf(s)).movePointRight(9).intValue();
						LocalTime lt = LocalTime.of(h, m, s, nano);
						if (arguments.size() >= 4 && arguments.get(3) != null) {
							Object offset = arguments.get(3);
							if (offset instanceof Duration dur) {
								yield OffsetTime.of(lt, ZoneOffset.ofTotalSeconds((int) dur.getSeconds()));
							}
							if (offset instanceof String zoneStr) {
								try {
									yield OffsetTime.of(lt, ZoneOffset.of(zoneStr));
								} catch (Exception e) {
									yield new NamedZoneTime(lt, ZoneId.of(zoneStr));
								}
							}
						}
						yield lt;
					} catch (Exception e) {
						yield null;
					}
				}
				case DATE_AND_TIME -> {
					yield feelDateAndTime(arguments);
				}
				case DURATION -> argument(arguments, 0) == null
						? null
						: DmnRuntime.parseDuration(String.valueOf(argument(arguments, 0)));
				case COUNT -> BigDecimal.valueOf(listArgument(arguments).size());
				case SUM ->
					listArgument(arguments).stream().map(DmnRuntime::number).reduce(BigDecimal.ZERO, BigDecimal::add);
				case MIN -> listArgument(arguments).stream().min(DmnRuntime::compare).orElse(null);
				case MAX -> listArgument(arguments).stream().max(DmnRuntime::compare).orElse(null);
				case ABS -> feelAbs(arguments);
				case SUBSTRING -> substring(arguments);
				case SUBSTRING_BEFORE -> feelSubstringBefore(arguments);
				case SUBSTRING_AFTER -> feelSubstringAfter(arguments);
				case STRING_LENGTH -> argument(arguments, 0) == null
						? null
						: BigDecimal.valueOf(stringValue(argument(arguments, 0)).codePointCount(0, stringValue(argument(arguments, 0)).length()));
				case UPPER_CASE -> argument(arguments, 0) == null
						? null
						: stringValue(argument(arguments, 0)).toUpperCase(Locale.ROOT);
				case LOWER_CASE -> argument(arguments, 0) == null
						? null
						: stringValue(argument(arguments, 0)).toLowerCase(Locale.ROOT);
				case CONTAINS -> argument(arguments, 0) == null || argument(arguments, 1) == null
						? null
						: stringValue(argument(arguments, 0)).contains(stringValue(argument(arguments, 1)));
				case STARTS_WITH -> argument(arguments, 0) != null && argument(arguments, 1) != null
						&& stringValue(argument(arguments, 0)).startsWith(stringValue(argument(arguments, 1)));
				case ENDS_WITH -> argument(arguments, 0) != null && argument(arguments, 1) != null
						&& stringValue(argument(arguments, 0)).endsWith(stringValue(argument(arguments, 1)));
				case MATCHES -> {
					yield feelMatches(arguments);
				}
				case REPLACE -> feelReplace(arguments);
				case SPLIT -> split(arguments);
				case FLOOR -> rounded(arguments, java.math.RoundingMode.FLOOR);
				case CEILING -> rounded(arguments, java.math.RoundingMode.CEILING);
				case DECIMAL -> rounded(arguments, java.math.RoundingMode.HALF_EVEN);
				case ROUND_HALF_UP -> rounded(arguments, java.math.RoundingMode.HALF_UP);
				case ROUND_HALF_DOWN -> rounded(arguments, java.math.RoundingMode.HALF_DOWN);
				case ROUND_HALF_EVEN -> rounded(arguments, java.math.RoundingMode.HALF_EVEN);
				case SUBLIST -> sublist(arguments);
				case CONCATENATE -> concatenate(arguments);
				case DISTINCT_VALUES -> distinctValues(arguments);
				case FLATTEN -> flatten(arguments);
				case REVERSE -> reverse(arguments);
				case INDEX_OF -> indexOf(arguments);
				case SORT -> sort(arguments);
				case LIST_REPLACE -> listReplace(arguments);
				case YEARS_AND_MONTHS_DURATION -> yearsAndMonthsDuration(arguments);
				case MEAN -> {
					List<Object> list = listArgument(arguments);
					if (list.isEmpty())
						yield null;
					BigDecimal sum = BigDecimal.ZERO;
					for (Object o : list) {
						if (o == null)
							yield null;
						BigDecimal n = number(o);
						if (n == null)
							yield null;
						sum = sum.add(n);
					}
					yield sum.divide(BigDecimal.valueOf(list.size()), MathContext.DECIMAL128);
				}
				case MEDIAN -> {
					List<Object> list = listArgument(arguments);
					if (list.isEmpty())
						yield null;
					List<BigDecimal> nums = new ArrayList<>();
					for (Object o : list) {
						if (o == null)
							yield null;
						BigDecimal n = number(o);
						if (n == null)
							yield null;
						nums.add(n);
					}
					Collections.sort(nums);
					int size = nums.size();
					if (size % 2 == 1)
						yield nums.get(size / 2);
					yield nums.get(size / 2 - 1).add(nums.get(size / 2)).divide(BigDecimal.valueOf(2),
							MathContext.DECIMAL128);
				}
				case MODE -> feelMode(arguments);
				case STDDEV -> {
					List<Object> list = listArgument(arguments);
					if (list.size() < 2)
						yield null;
					BigDecimal sum = BigDecimal.ZERO;
					List<BigDecimal> nums = new ArrayList<>();
					for (Object o : list) {
						if (o == null)
							yield null;
						BigDecimal n = number(o);
						if (n == null)
							yield null;
						sum = sum.add(n);
						nums.add(n);
					}
					BigDecimal mean = sum.divide(BigDecimal.valueOf(list.size()), MathContext.DECIMAL128);
					BigDecimal sqSum = BigDecimal.ZERO;
					for (BigDecimal n : nums) {
						BigDecimal diff = n.subtract(mean);
						sqSum = sqSum.add(diff.multiply(diff));
					}
					BigDecimal variance = sqSum.divide(BigDecimal.valueOf(list.size() - 1), MathContext.DECIMAL128);
					yield BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
				}
				case SQRT -> feelSqrt(arguments);
				case EXP -> feelExp(arguments);
				case LOG -> feelLog(arguments);
				case MODULO -> feelModulo(arguments);
				case EVEN -> feelEven(arguments);
				case ODD -> feelOdd(arguments);
				case PRODUCT -> {
					List<Object> list = listArgument(arguments);
					if (list.isEmpty())
						yield null;
					BigDecimal prod = BigDecimal.ONE;
					for (Object o : list) {
						if (!(o instanceof Number))
							yield null;
						prod = prod.multiply(number(o));
					}
					yield prod;
				}
				case ALL -> feelAll(arguments);
				case ANY -> feelAny(arguments);
				case INSERT_BEFORE -> {
					if (argument(arguments, 0) == null || argument(arguments, 1) == null
							|| argument(arguments, 2) == null)
						yield null;
					List<Object> list = new ArrayList<>((List<?>) argument(arguments, 0));
					int pos = number(argument(arguments, 1)).intValue();
					Object newItem = argument(arguments, 2);
					int idx = pos > 0 ? pos - 1 : list.size() + pos;
					if (idx < 0 || idx > list.size())
						yield null;
					list.add(idx, newItem);
					yield List.copyOf(list);
				}
				case REMOVE -> {
					if (argument(arguments, 0) == null || argument(arguments, 1) == null)
						yield null;
					List<Object> list = new ArrayList<>((List<?>) argument(arguments, 0));
					int pos = number(argument(arguments, 1)).intValue();
					int idx = pos > 0 ? pos - 1 : list.size() + pos;
					if (idx >= 0 && idx < list.size())
						list.remove(idx);
					yield List.copyOf(list);
				}
				case APPEND -> {
					if (argument(arguments, 0) == null)
						yield null;
					if (!(argument(arguments, 0) instanceof List<?> l0))
						yield null;
					List<Object> list = new ArrayList<>(l0);
					for (int i = 1; i < arguments.size(); i++) {
						list.add(arguments.get(i));
					}
					yield List.copyOf(list);
				}
				case UNION -> {
					List<Object> result = new ArrayList<>();
					for (Object arg : arguments) {
						if (arg instanceof List<?> l) {
							for (Object item : l) {
								if (result.stream().noneMatch(existing -> Boolean.TRUE.equals(equal(existing, item))))
									result.add(item);
							}
						} else {
							if (result.stream().noneMatch(existing -> Boolean.TRUE.equals(equal(existing, arg))))
								result.add(arg);
						}
					}
					yield List.copyOf(result);
				}
				case LIST_CONTAINS -> {
					if (argument(arguments, 0) == null)
						yield null;
					List<?> list = (List<?>) argument(arguments, 0);
					Object match = argument(arguments, 1);
					for (Object o : list) {
						if (Boolean.TRUE.equals(equal(o, match)))
							yield true;
					}
					yield false;
				}
				case ROUND_UP -> rounded(arguments, java.math.RoundingMode.UP);
				case ROUND_DOWN -> rounded(arguments, java.math.RoundingMode.DOWN);
				case STRING_JOIN -> feelStringJoin(arguments);
				case DAY_AND_TIME_DURATION -> {
					if (argument(arguments, 0) == null || argument(arguments, 1) == null)
						yield null;
					Object o1 = argument(arguments, 0);
					Object o2 = argument(arguments, 1);
					java.time.temporal.Temporal t1 = null;
					if (o1 instanceof java.time.temporal.Temporal t)
						t1 = t;
					else if (o1 instanceof NamedZoneDateTime nzd)
						t1 = ZonedDateTime.of(nzd.value(), nzd.zone());
					else {
						Object p1 = parseDateTime(String.valueOf(o1));
						if (p1 instanceof java.time.temporal.Temporal t)
							t1 = t;
						else if (p1 instanceof NamedZoneDateTime nzd)
							t1 = ZonedDateTime.of(nzd.value(), nzd.zone());
					}
					java.time.temporal.Temporal t2 = null;
					if (o2 instanceof java.time.temporal.Temporal t)
						t2 = t;
					else if (o2 instanceof NamedZoneDateTime nzd)
						t2 = ZonedDateTime.of(nzd.value(), nzd.zone());
					else {
						Object p2 = parseDateTime(String.valueOf(o2));
						if (p2 instanceof java.time.temporal.Temporal t)
							t2 = t;
						else if (p2 instanceof NamedZoneDateTime nzd)
							t2 = ZonedDateTime.of(nzd.value(), nzd.zone());
					}
					if (t1 == null || t2 == null)
						yield null;
					yield Duration.between(t1, t2);
				}
				case GET_ENTRIES -> {
					if (arguments.size() != 1 || argument(arguments, 0) == null)
						yield null;
					Object arg = argument(arguments, 0);
					Map<?, ?> m = null;
					if (arg instanceof Map<?, ?> map) {
						m = map;
					} else if (arg instanceof RuntimeContextValue ctx) {
						m = ctx.namedFields();
					}
					if (m == null)
						yield null;
					List<Map<String, Object>> result = new ArrayList<>();
					for (Map.Entry<?, ?> e : m.entrySet()) {
						Map<String, Object> entry = new HashMap<>();
						entry.put("key", e.getKey());
						entry.put("value", e.getValue());
						result.add(entry);
					}
					yield result;
				}
				case GET_VALUE -> {
					if (arguments.size() != 2 || argument(arguments, 0) == null || argument(arguments, 1) == null)
						yield null;
					Object arg = argument(arguments, 0);
					Map<?, ?> m = null;
					if (arg instanceof Map<?, ?> map) {
						m = map;
					} else if (arg instanceof RuntimeContextValue ctx) {
						m = ctx.namedFields();
					}
					if (m == null)
						yield null;
					yield m.get(String.valueOf(argument(arguments, 1)));
				}
				case RANGE -> {
					if (arguments.size() != 1 || argument(arguments, 0) == null)
						yield null;
					String s = String.valueOf(argument(arguments, 0)).trim();
					boolean lowClosed = s.startsWith("[");
					boolean upClosed = s.endsWith("]");
					boolean lowOpen = s.startsWith("(") || s.startsWith("]");
					boolean upOpen = s.endsWith(")") || s.endsWith("[");
					if (s.length() >= 2 && (lowClosed || lowOpen) && (upClosed || upOpen)) {
						String inner = s.substring(1, s.length() - 1);
						String[] parts = inner.split("\\.\\.", 2);
						if (parts.length == 2) {
							String p0 = parts[0].trim();
							String p1 = parts[1].trim();
							if (p0.isEmpty() && lowClosed)
								yield null;
							if (p1.isEmpty() && upClosed)
								yield null;
							Object low = p0.isEmpty() ? null : parseLiteralValue(p0);
							Object up = p1.isEmpty() ? null : parseLiteralValue(p1);
							if (!p0.isEmpty() && low == null)
								yield null;
							if (!p1.isEmpty() && up == null)
								yield null;
							if (low == null && up == null)
								yield null;
							if (low != null && up != null) {
								if (low.getClass() != up.getClass() && !(low instanceof Number && up instanceof Number))
									yield null;
								Integer c = compare(low, up);
								if (c == null || c > 0)
									yield null;
							}
							yield new RuntimeRangeValue(low, up,
									lowClosed ? RuntimeRangeBoundary.CLOSED : RuntimeRangeBoundary.OPEN,
									upClosed ? RuntimeRangeBoundary.CLOSED : RuntimeRangeBoundary.OPEN);
						}
					}
					yield null;
				}
				case DAY_OF_YEAR -> {
					if (arguments.size() != 1)
						yield null;
					LocalDate d = toLocalDate(arguments.get(0));
					yield d == null ? null : BigDecimal.valueOf(d.getDayOfYear());
				}
				case DAY_OF_WEEK -> {
					if (arguments.size() != 1)
						yield null;
					LocalDate d = toLocalDate(arguments.get(0));
					yield d == null
							? null
							: d.getDayOfWeek().name().substring(0, 1)
									+ d.getDayOfWeek().name().substring(1).toLowerCase();
				}
				case WEEK_OF_YEAR -> {
					if (arguments.size() != 1)
						yield null;
					LocalDate d = toLocalDate(arguments.get(0));
					yield d == null
							? null
							: BigDecimal.valueOf(d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
				}
				case MONTH_OF_YEAR -> {
					if (arguments.size() != 1)
						yield null;
					LocalDate d = toLocalDate(arguments.get(0));
					yield d == null
							? null
							: d.getMonth().name().substring(0, 1) + d.getMonth().name().substring(1).toLowerCase();
				}
				case CONTEXT -> {
					if (arguments.size() != 1 || arguments.get(0) == null)
						yield null;
					List<?> list = arguments.get(0) instanceof List<?> l ? l : List.of(arguments.get(0));
					Map<String, Object> map = new LinkedHashMap<>();
					for (Object item : list) {
						if (item == null)
							yield null;
						Object keyObj = null;
						Object valObj = null;
						if (item instanceof Map<?, ?> m) {
							if (!m.containsKey("key") || !m.containsKey("value"))
								yield null;
							keyObj = m.get("key");
							valObj = m.get("value");
						} else if (item instanceof RuntimeContextValue rcv) {
							if (!rcv.namedFields().containsKey("key") || !rcv.namedFields().containsKey("value"))
								yield null;
							keyObj = rcv.namedFields().get("key");
							valObj = rcv.namedFields().get("value");
						} else {
							yield null;
						}
						if (!(keyObj instanceof String strKey))
							yield null;
						if (map.containsKey(strKey))
							yield null;
						map.put(strKey, valObj);
					}
					yield map;
				}
				case CONTEXT_PUT -> {
					if (arguments.size() != 3)
						yield null;
					yield contextPut(argument(arguments, 0), argument(arguments, 1), argument(arguments, 2));
				}
				case CONTEXT_MERGE -> {
					if (arguments.size() != 1 || arguments.get(0) == null)
						yield null;
					List<?> list = arguments.get(0) instanceof List<?> l ? l : (arguments.get(0) instanceof Map<?, ?> || arguments.get(0) instanceof RuntimeContextValue ? List.of(arguments.get(0)) : null);
					if (list == null)
						yield null;
					Map<String, Object> merged = new LinkedHashMap<>();
					for (Object item : list) {
						if (item instanceof Map<?, ?> m) {
							for (Map.Entry<?, ?> e : m.entrySet()) {
								if (!(e.getKey() instanceof String))
									yield null;
								merged.put((String) e.getKey(), e.getValue());
							}
						} else if (item instanceof RuntimeContextValue rcv) {
							merged.putAll(rcv.namedFields());
						} else {
							yield null;
						}
					}
					yield merged;
				}
				case PMT, PMT2 -> {
					if (arguments.size() < 3)
						yield null;
					BigDecimal r = number(argument(arguments, 0));
					BigDecimal n = number(argument(arguments, 1));
					BigDecimal p = number(argument(arguments, 2));
					if (r == null || n == null || p == null)
						yield null;
					double rd = r.doubleValue(), nd = n.doubleValue(), pd = p.doubleValue();
					if (rd == 0)
						yield BigDecimal.valueOf(-pd / nd);
					double pmt = (pd * rd) / (1.0 - Math.pow(1.0 + rd, -nd));
					yield BigDecimal.valueOf(pmt);
				}
				case NOW -> arguments.isEmpty() ? ZonedDateTime.now() : null;
				case TODAY -> arguments.isEmpty() ? LocalDate.now() : null;
				case DURING -> intervalDuring(argument(arguments, 0), argument(arguments, 1));
				case BEFORE -> intervalBefore(argument(arguments, 0), argument(arguments, 1));
				case AFTER -> intervalAfter(argument(arguments, 0), argument(arguments, 1));
				case MEETS -> intervalMeets(argument(arguments, 0), argument(arguments, 1));
				case MET_BY -> intervalMetBy(argument(arguments, 0), argument(arguments, 1));
				case OVERLAPS -> intervalOverlaps(argument(arguments, 0), argument(arguments, 1));
				case OVERLAPS_BEFORE -> intervalOverlapsBefore(argument(arguments, 0), argument(arguments, 1));
				case OVERLAPS_AFTER -> intervalOverlapsAfter(argument(arguments, 0), argument(arguments, 1));
				case FINISHES -> intervalFinishes(argument(arguments, 0), argument(arguments, 1));
				case FINISHED_BY -> intervalFinishedBy(argument(arguments, 0), argument(arguments, 1));
				case INCLUDES -> intervalIncludes(argument(arguments, 0), argument(arguments, 1));
				case STARTS -> intervalStarts(argument(arguments, 0), argument(arguments, 1));
				case STARTED_BY -> intervalStartedBy(argument(arguments, 0), argument(arguments, 1));
				case COINCIDES -> intervalCoincides(argument(arguments, 0), argument(arguments, 1));
			};
		}

		private Object substring(List<Object> args) {
			if (args.isEmpty() || args.getFirst() == null || args.size() < 2 || args.get(1) == null)
				return null;
			String str = String.valueOf(args.getFirst());
			Integer start = integer(args.get(1));
			if (start == null)
				return null;
			Integer len = args.size() > 2 && args.get(2) != null ? integer(args.get(2)) : null;
			if (args.size() > 2 && args.get(2) != null && len == null)
				return null;
			return feelSubstring(str, start, len);
		}

		private Object rounded(List<Object> args, java.math.RoundingMode mode) {
			if (args.isEmpty() || args.size() > 2 || !(argument(args, 0) instanceof Number))
				return null;
			BigDecimal n = number(argument(args, 0));
			if (n == null)
				return null;
			int scale = 0;
			if (args.size() > 1) {
				if (!(argument(args, 1) instanceof Number scaleNumber))
					return null;
				BigDecimal scaleDecimal = number(scaleNumber);
				if (scaleDecimal == null)
					return null;
				scale = scaleDecimal.intValue();
			}
			if (scale < -6111 || scale > 6176)
				return null;
			return n.setScale(scale, mode);
		}

		private Integer integer(Object value) {
			if (value == null)
				return null;
			BigDecimal n = number(value);
			return n != null ? n.intValue() : null;
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
			if (args.size() < 3 || args.get(0) == null || args.get(1) == null || args.get(2) == null)
				return null;
			String input = String.valueOf(args.get(0));
			String pattern = String.valueOf(args.get(1));
			String replacement = String.valueOf(args.get(2));
			return input.replaceAll(pattern, replacement);
		}

		private Object split(List<Object> args) {
			if (args.size() < 2 || args.get(0) == null || args.get(1) == null)
				return null;
			String input = String.valueOf(args.get(0));
			String pattern = String.valueOf(args.get(1));
			try {
				String[] parts = input.split(pattern, -1);
				return Arrays.asList((Object[]) parts);
			} catch (Exception e) {
				return null;
			}
		}

		private Object sublist(List<Object> args) {
			if (args.size() < 2 || args.get(0) == null || args.get(1) == null)
				return null;
			List<Object> list = list(args.get(0));
			if (list == null)
				return null;
			BigDecimal sNum = number(args.get(1));
			if (sNum == null)
				return null;
			int start = sNum.intValueExact();
			int idx = start > 0 ? start - 1 : list.size() + start;
			if (idx < 0 || idx >= list.size())
				return List.of();
			if (args.size() > 2 && args.get(2) != null) {
				BigDecimal lNum = number(args.get(2));
				if (lNum == null)
					return null;
				int len = lNum.intValueExact();
				int end = Math.min(list.size(), idx + len);
				return list.subList(idx, end);
			}
			return list.subList(idx, list.size());
		}

		private Object contextPut(Object ctx, Object keyOrKeys, Object val) {
			if (!(ctx instanceof Map<?, ?> || ctx instanceof RuntimeContextValue))
				return null;
			if (keyOrKeys == null)
				return null;
			Map<String, Object> copy = new LinkedHashMap<>();
			if (ctx instanceof Map<?, ?> m) {
				m.forEach((k, v) -> copy.put(String.valueOf(k), v));
			} else if (ctx instanceof RuntimeContextValue rcv) {
				copy.putAll(rcv.namedFields());
			}
			if (keyOrKeys instanceof List<?> keys) {
				if (keys.isEmpty())
					return null;
				for (Object k : keys) {
					if (!(k instanceof String))
						return null;
				}
				if (keys.size() == 1) {
					copy.put((String) keys.get(0), val);
					return copy;
				}
				String k0 = (String) keys.get(0);
				Object sub = copy.get(k0);
				if (sub != null && !(sub instanceof Map<?, ?> || sub instanceof RuntimeContextValue))
					return null;
				Object updatedSub = contextPut(sub != null ? sub : new LinkedHashMap<>(), keys.subList(1, keys.size()), val);
				if (updatedSub == null)
					return null;
				copy.put(k0, updatedSub);
				return copy;
			}
			if (!(keyOrKeys instanceof String strKey))
				return null;
			copy.put(strKey, val);
			return copy;
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
			List<Object> list = list(args.get(0));
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
			List<Object> list = new ArrayList<>(list(args.get(0)));
			Collections.reverse(list);
			return List.copyOf(list);
		}

		private Object indexOf(List<Object> args) {
			List<Object> list = list(args.get(0));
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
			List<Object> list = new ArrayList<>(list(args.get(0)));
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
			List<Object> list = new ArrayList<>(list(args.get(0)));
			Object second = args.get(1);
			Object newItem = args.get(2);
			if (second instanceof Number n) {
				int pos = n.intValue();
				if (pos == 0 || pos < -list.size() || pos > list.size())
					return null;
				int idx = pos > 0 ? pos - 1 : list.size() + pos;
				if (idx >= 0 && idx < list.size()) {
					list.set(idx, newItem);
				}
			} else if (second instanceof CallableValue fn) {
				if (fn.parameterCount() != 2 && fn.parameterCount() != -1) {
					return null;
				}
				for (int i = 0; i < list.size(); i++) {
					Object res = fn.call(List.of(list.get(i), newItem), Map.of());
					if (!(res instanceof Boolean)) {
						return null;
					}
					if (Boolean.TRUE.equals(res)) {
						list.set(i, newItem);
					}
				}
			} else {
				return null;
			}
			return Collections.unmodifiableList(list);
		}

		private Object yearsAndMonthsDuration(List<Object> args) {
			if (args.isEmpty() || args.get(0) == null)
				return null;
			if (args.size() == 1) {
				return DmnRuntime.parseDuration(String.valueOf(args.get(0)));
			}
			if (args.get(1) == null)
				return null;
			LocalDate d1 = toLocalDate(args.get(0));
			LocalDate d2 = toLocalDate(args.get(1));
			if (d1 == null || d2 == null)
				return null;
			Period p = Period.between(d1, d2);
			return Period.of(p.getYears(), p.getMonths(), 0);
		}

		private static LocalDate toLocalDate(Object obj) {
			if (obj == null)
				return null;
			if (obj instanceof LocalDate ld)
				return ld;
			if (obj instanceof LocalDateTime ldt)
				return ldt.toLocalDate();
			if (obj instanceof OffsetDateTime odt)
				return odt.toLocalDate();
			if (obj instanceof ZonedDateTime zdt)
				return zdt.toLocalDate();
			if (obj instanceof NamedZoneDateTime nzdt)
				return nzdt.value().toLocalDate();
			try {
				return LocalDate.parse(String.valueOf(obj));
			} catch (Exception e) {
				return null;
			}
		}

		private List<Object> listArgument(List<Object> arguments) {
			return arguments.size() == 1 && arguments.getFirst() instanceof List<?>
					? list(arguments.getFirst())
					: arguments;
		}
	}

	public static TemporalAmount parseDuration(String value) {
		if (value == null)
			return null;
		try {
			String s = value.trim();
			boolean isYm = s.contains("Y") || (s.contains("M") && !s.contains("T") && !s.contains("D"));
			if (isYm) {
				return Period.parse(s).normalized();
			}
			return Duration.parse(s);
		} catch (Exception e) {
			return null;
		}
	}
	private static Object argument(List<Object> values, int index) {
		if (index >= values.size())
			return null;
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
	public static String formatFeelString(Object value) {
		if (value == null)
			return null;
		if (value instanceof List<?> list) {
			if (list.size() == 1)
				return formatFeelString(list.get(0));
			return null;
		}
		String s = String.valueOf(value);
		if (s.startsWith("+") && s.length() > 1 && Character.isDigit(s.charAt(1))) {
			s = s.substring(1);
		}
		return s;
	}
	private static String stringValue(Object value) {
		return formatFeelString(value);
	}
	private static BigDecimal number(Object value) {
		if (value == null)
			return null;
		if (value instanceof List<?> list) {
			if (list.size() == 1)
				return number(list.get(0));
			return null;
		}
		if (value instanceof BigDecimal decimal)
			return decimal;
		if (value instanceof Number number)
			return new BigDecimal(number.toString());
		return null;
	}
	public static java.time.Instant toInstant(Object dt) {
		if (dt instanceof OffsetDateTime odt)
			return odt.toInstant();
		if (dt instanceof ZonedDateTime zdt)
			return zdt.toInstant();
		if (dt instanceof NamedZoneDateTime nzdt)
			return nzdt.value().atZone(nzdt.zone()).toInstant();
		return null;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static Integer compare(Object left, Object right) {
		if (left == null || right == null)
			return null;
		if (left instanceof Duration dl && right instanceof Duration dr)
			return dl.compareTo(dr);
		if (left instanceof Period pl && right instanceof Period pr)
			return Long.compare(pl.toTotalMonths(), pr.toTotalMonths());
		if (left instanceof Number && right instanceof Number)
			return number(left).compareTo(number(right));
		if (left instanceof String sl && right instanceof String sr)
			return sl.compareTo(sr);
		if (left instanceof LocalDate dl && right instanceof LocalDate dr)
			return dl.compareTo(dr);
		if (left instanceof LocalTime tl && right instanceof LocalTime tr)
			return tl.compareTo(tr);
		if (left instanceof LocalDateTime dtl && right instanceof LocalDateTime dtr)
			return dtl.compareTo(dtr);
		java.time.Instant i1 = toInstant(left);
		java.time.Instant i2 = toInstant(right);
		if (i1 != null && i2 != null)
			return i1.compareTo(i2);
		if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
			try {
				return comparable.compareTo(right);
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}

	public static Boolean equal(Object left, Object right) {
		if (left == null && right == null)
			return true;
		if (left == null || right == null)
			return false;
		if (left instanceof Boolean bl && right instanceof Boolean br)
			return bl.equals(br);
		if (left instanceof Number && right instanceof Number)
			return number(left).compareTo(number(right)) == 0;
		if (left instanceof String sl && right instanceof String sr)
			return sl.equals(sr);
		if (left instanceof LocalDate dl && right instanceof LocalDate dr)
			return dl.equals(dr);
		if (left instanceof LocalTime tl && right instanceof LocalTime tr)
			// FEEL spec: time precision is at most milliseconds; truncate nanoseconds below
			// that
			return tl.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
					.equals(tr.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
		if (left instanceof OffsetTime ot1 && right instanceof OffsetTime ot2)
			return (ot1.toLocalTime().toSecondOfDay()
					- ot1.getOffset().getTotalSeconds()) == (ot2.toLocalTime().toSecondOfDay()
							- ot2.getOffset().getTotalSeconds())
					&& (ot1.toLocalTime().get(java.time.temporal.ChronoField.MILLI_OF_SECOND)) == (ot2.toLocalTime()
							.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));
		if (left instanceof NamedZoneTime nzt1 && right instanceof NamedZoneTime nzt2)
			return nzt1.value().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).equals(
					nzt2.value().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)) && nzt1.zone().equals(nzt2.zone());
		if (left instanceof LocalDateTime dtl && right instanceof LocalDateTime dtr)
			return dtl.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
					.equals(dtr.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
		java.time.Instant i1 = toInstant(left);
		java.time.Instant i2 = toInstant(right);
		if (i1 != null && i2 != null)
			return i1.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
					.equals(i2.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
		if (left instanceof Duration dl && right instanceof Duration dr)
			return dl.compareTo(dr) == 0;
		if (left instanceof Period pl && right instanceof Period pr)
			return pl.toTotalMonths() == pr.toTotalMonths();
		if (left instanceof RuntimeRangeValue r1 && right instanceof RuntimeRangeValue r2) {
			// Absent endpoints (shorthand like (< 10)) are NOT equal to explicit-null
			// endpoints (null..10)
			if (r1.lowerAbsent() != r2.lowerAbsent() || r1.upperAbsent() != r2.upperAbsent())
				return false;
			if (r1.lowerBoundary() != r2.lowerBoundary() || r1.upperBoundary() != r2.upperBoundary())
				return false;
			Boolean lowEq = equal(r1.lower(), r2.lower());
			Boolean upEq = equal(r1.upper(), r2.upper());
			if (lowEq == null || upEq == null)
				return null;
			return lowEq && upEq;
		}
		Map<?, ?> m1 = left instanceof RuntimeContextValue ctx
				? ctx.namedFields()
				: left instanceof Map<?, ?> m ? m : null;
		Map<?, ?> m2 = right instanceof RuntimeContextValue ctx
				? ctx.namedFields()
				: right instanceof Map<?, ?> m ? m : null;
		if (m1 != null && m2 != null) {
			if (m1.size() != m2.size())
				return false;
			for (Map.Entry<?, ?> entry : m1.entrySet()) {
				String key = String.valueOf(entry.getKey());
				if (!m2.containsKey(key))
					return false;
				Boolean vEq = equal(entry.getValue(), m2.get(key));
				if (vEq == null)
					return null;
				if (!vEq)
					return false;
			}
			return true;
		}
		if (left instanceof List<?> l1 && right instanceof List<?> l2) {
			if (l1.size() != l2.size())
				return false;
			for (int i = 0; i < l1.size(); i++) {
				Boolean vEq = equal(l1.get(i), l2.get(i));
				if (vEq == null)
					return null;
				if (!vEq)
					return false;
			}
			return true;
		}
		return null;
	}

	public static Boolean isValues(Object a, Object b) {
		if (a == null && b == null)
			return true;
		if (a == null || b == null)
			return false;
		if (a instanceof Number && b instanceof Number) {
			BigDecimal da = number(a), db = number(b);
			if (da == null || db == null)
				return false;
			return da.compareTo(db) == 0;
		}
		if (a instanceof NamedZoneDateTime nz1 && b instanceof NamedZoneDateTime nz2) {
			return nz1.zone().equals(nz2.zone()) && nz1.value().equals(nz2.value());
		}
		if (a instanceof NamedZoneTime nzt1 && b instanceof NamedZoneTime nzt2) {
			return nzt1.zone().equals(nzt2.zone()) && nzt1.value().equals(nzt2.value());
		}
		if (a instanceof OffsetDateTime odt1 && b instanceof OffsetDateTime odt2) {
			return odt1.getOffset().equals(odt2.getOffset()) && odt1.toLocalDateTime().equals(odt2.toLocalDateTime());
		}
		if (a instanceof OffsetTime ot1 && b instanceof OffsetTime ot2) {
			return ot1.getOffset().equals(ot2.getOffset()) && ot1.toLocalTime().equals(ot2.toLocalTime());
		}
		if (a instanceof Period p1 && b instanceof Period p2) {
			return p1.toTotalMonths() == p2.toTotalMonths();
		}
		if (a instanceof Duration d1 && b instanceof Duration d2) {
			return d1.equals(d2);
		}
		if (a instanceof List<?> l1 && b instanceof List<?> l2) {
			if (l1.size() != l2.size())
				return false;
			for (int i = 0; i < l1.size(); i++) {
				if (!Boolean.TRUE.equals(isValues(l1.get(i), l2.get(i))))
					return false;
			}
			return true;
		}
		if (a instanceof Map<?, ?> m1 && b instanceof Map<?, ?> m2) {
			if (m1.size() != m2.size())
				return false;
			for (Map.Entry<?, ?> entry : m1.entrySet()) {
				if (!m2.containsKey(entry.getKey()))
					return false;
				if (!Boolean.TRUE.equals(isValues(entry.getValue(), m2.get(entry.getKey()))))
					return false;
			}
			return true;
		}
		if (a.getClass() != b.getClass())
			return false;
		return Boolean.TRUE.equals(equal(a, b));
	}

	private static boolean comparison(RuntimeUnaryTestOperator operator, Object left, Object right) {
		if (operator == RuntimeUnaryTestOperator.EQUAL)
			return Boolean.TRUE.equals(equal(left, right));
		if (operator == RuntimeUnaryTestOperator.NOT_EQUAL)
			return Boolean.FALSE.equals(equal(left, right));
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
	public static Boolean contains(RuntimeRangeValue range, Object value) {
		if (value == null || range == null)
			return null;
		if (range.lower() == null && range.upper() == null)
			return null;
		if (range.upperAbsent()) {
			if (range.lowerBoundary() == RuntimeRangeBoundary.CLOSED && range.upperBoundary() == RuntimeRangeBoundary.CLOSED) {
				return equal(value, range.lower());
			}
			if (range.lowerBoundary() == RuntimeRangeBoundary.OPEN && range.upperBoundary() == RuntimeRangeBoundary.OPEN) {
				Boolean eq = equal(value, range.lower());
				return eq == null ? null : !eq;
			}
		}
		if (range.lower() != null) {
			Integer lowC = compare(value, range.lower());
			if (lowC == null)
				return null;
			boolean lower = range.lowerBoundary() == RuntimeRangeBoundary.CLOSED ? lowC >= 0 : lowC > 0;
			if (!lower)
				return false;
		}
		if (range.upper() != null) {
			Integer upC = compare(value, range.upper());
			if (upC == null)
				return null;
			boolean upper = range.upperBoundary() == RuntimeRangeBoundary.CLOSED ? upC <= 0 : upC < 0;
			if (!upper)
				return false;
		}
		return true;
	}
	private static List<Object> list(Object value) {
		if (value instanceof List<?> list)
			return new ArrayList<>(list);
		return List.of(value);
	}
	public static boolean instanceOf(Object value, RuntimeType type) {
		if (value == null)
			return false;
		if (type == null || type.kind() == RuntimeTypeKind.ANY)
			return true;
		return switch (type.kind()) {
			case BOOLEAN -> value instanceof Boolean;
			case NUMBER -> value instanceof Number;
			case STRING -> value instanceof String;
			case DATE -> value instanceof LocalDate;
			case TIME -> value instanceof LocalTime || value instanceof OffsetTime || value instanceof NamedZoneTime;
			case DATE_TIME -> value instanceof LocalDateTime || value instanceof OffsetDateTime
					|| value instanceof ZonedDateTime || value instanceof NamedZoneDateTime;
			case DURATION -> value instanceof TemporalAmount;
			case YEARS_MONTHS_DURATION -> value instanceof Period;
			case DAYS_TIME_DURATION -> value instanceof Duration;
			case LIST -> value instanceof List<?> list
					&& (type.elementType() == null || list.stream().allMatch(e -> instanceOf(e, type.elementType())));
			case RANGE -> value instanceof RuntimeRangeValue range && (type.elementType() == null
					|| ((range.lower() == null || instanceOf(range.lower(), type.elementType()))
							&& (range.upper() == null || instanceOf(range.upper(), type.elementType()))));
			case CONTEXT -> instanceOfContext(value, type);
			case FUNCTION -> value instanceof CallableValue;
			case ANY -> true;
			case NULL -> false;
		};
	}

	private static boolean instanceOfContext(Object value, RuntimeType type) {
		if (value instanceof RuntimeContextValue rcv) {
			if (type.fieldLayout() != null && !type.fieldLayout().isEmpty()) {
				Map<String, Object> fields = rcv.namedFields();
				for (io.finmsg.dmn.ir.RuntimeField f : type.fieldLayout()) {
					if (!fields.containsKey(f.name()))
						return false;
					Object fVal = fields.get(f.name());
					if (fVal != null && !instanceOf(fVal, f.type()))
						return false;
				}
			}
			return true;
		}
		if (value instanceof Map<?, ?> m) {
			if (type.fieldLayout() != null && !type.fieldLayout().isEmpty()) {
				for (io.finmsg.dmn.ir.RuntimeField f : type.fieldLayout()) {
					if (!m.containsKey(f.name()))
						return false;
					Object fVal = m.get(f.name());
					if (fVal != null && !instanceOf(fVal, f.type()))
						return false;
				}
			}
			return true;
		}
		return false;
	}

	private static Period periodFromMonths(long totalMonths) {
		int years = (int) (totalMonths / 12);
		int months = (int) (totalMonths % 12);
		return Period.of(years, months, 0);
	}

	private static Object addValues(Object left, Object right) {
		if (left == null || right == null)
			return null;
		if (left instanceof String sl && right instanceof String sr)
			return sl + sr;
		if (left instanceof Number && right instanceof Number)
			return number(left).add(number(right));
		if (left instanceof LocalDate d && right instanceof Period p)
			return d.plus(p);
		if (left instanceof Period p && right instanceof LocalDate d)
			return d.plus(p);
		if (left instanceof LocalDate d && right instanceof Duration dur)
			return d.atStartOfDay().plus(dur).toLocalDate();
		if (left instanceof Duration dur && right instanceof LocalDate d)
			return d.atStartOfDay().plus(dur).toLocalDate();
		if (left instanceof LocalTime t && right instanceof Duration dur)
			return t.plus(dur);
		if (left instanceof Duration dur && right instanceof LocalTime t)
			return t.plus(dur);
		if (left instanceof OffsetTime ot && right instanceof Duration dur)
			return ot.plus(dur);
		if (left instanceof Duration dur && right instanceof OffsetTime ot)
			return ot.plus(dur);
		if (left instanceof NamedZoneTime nzt && right instanceof Duration dur)
			return new NamedZoneTime(nzt.value().plus(dur), nzt.zone());
		if (left instanceof Duration dur && right instanceof NamedZoneTime nzt)
			return new NamedZoneTime(nzt.value().plus(dur), nzt.zone());
		if (left instanceof LocalDateTime dt && right instanceof Period p)
			return dt.plus(p);
		if (left instanceof Period p && right instanceof LocalDateTime dt)
			return dt.plus(p);
		if (left instanceof LocalDateTime dt && right instanceof Duration dur)
			return dt.plus(dur);
		if (left instanceof Duration dur && right instanceof LocalDateTime dt)
			return dt.plus(dur);
		if (left instanceof OffsetDateTime odt && right instanceof Period p)
			return odt.plus(p);
		if (left instanceof Period p && right instanceof OffsetDateTime odt)
			return odt.plus(p);
		if (left instanceof OffsetDateTime odt && right instanceof Duration dur)
			return odt.plus(dur);
		if (left instanceof Duration dur && right instanceof OffsetDateTime odt)
			return odt.plus(dur);
		if (left instanceof ZonedDateTime zdt && right instanceof Period p)
			return zdt.plus(p);
		if (left instanceof Period p && right instanceof ZonedDateTime zdt)
			return zdt.plus(p);
		if (left instanceof ZonedDateTime zdt && right instanceof Duration dur)
			return zdt.plus(dur);
		if (left instanceof Duration dur && right instanceof ZonedDateTime zdt)
			return zdt.plus(dur);
		if (left instanceof NamedZoneDateTime nzdt && right instanceof Period p)
			return new NamedZoneDateTime(nzdt.value().plus(p), nzdt.zone());
		if (left instanceof Period p && right instanceof NamedZoneDateTime nzdt)
			return new NamedZoneDateTime(nzdt.value().plus(p), nzdt.zone());
		if (left instanceof NamedZoneDateTime nzdt && right instanceof Duration dur)
			return new NamedZoneDateTime(nzdt.value().plus(dur), nzdt.zone());
		if (left instanceof Duration dur && right instanceof NamedZoneDateTime nzdt)
			return new NamedZoneDateTime(nzdt.value().plus(dur), nzdt.zone());
		if (left instanceof Period p1 && right instanceof Period p2)
			return periodFromMonths(p1.toTotalMonths() + p2.toTotalMonths());
		if (left instanceof Duration d1 && right instanceof Duration d2)
			return d1.plus(d2);
		return null;
	}

	private static Object subtractValues(Object left, Object right) {
		if (left == null || right == null)
			return null;
		if (left instanceof Number && right instanceof Number)
			return number(left).subtract(number(right));
		if (left instanceof LocalDate d1 && right instanceof LocalDate d2)
			return Duration.ofDays(java.time.temporal.ChronoUnit.DAYS.between(d2, d1));
		if (left instanceof LocalDate d && right instanceof Period p)
			return d.minus(p);
		if (left instanceof LocalDate d && right instanceof Duration dur)
			return d.atStartOfDay().minus(dur).toLocalDate();
		if (left instanceof LocalTime t1 && right instanceof LocalTime t2)
			return Duration.between(t2, t1);
		if (left instanceof LocalTime t && right instanceof Duration dur)
			return t.minus(dur);
		if (left instanceof OffsetTime ot1 && right instanceof OffsetTime ot2)
			return Duration.between(ot2, ot1);
		if (left instanceof OffsetTime ot && right instanceof Duration dur)
			return ot.minus(dur);
		if (left instanceof NamedZoneTime nzt && right instanceof Duration dur)
			return new NamedZoneTime(nzt.value().minus(dur), nzt.zone());
		if (left instanceof NamedZoneTime nzt1 && right instanceof NamedZoneTime nzt2)
			return Duration.between(nzt2.value(), nzt1.value());
		if (left instanceof LocalDateTime dt1 && right instanceof LocalDateTime dt2)
			return Duration.between(dt2, dt1);
		if (left instanceof LocalDateTime dt && right instanceof Period p)
			return dt.minus(p);
		if (left instanceof LocalDateTime dt && right instanceof Duration dur)
			return dt.minus(dur);
		if (left instanceof OffsetDateTime odt && right instanceof Duration dur)
			return odt.minus(dur);
		if (left instanceof OffsetDateTime odt && right instanceof Period p)
			return odt.minus(p);
		if (left instanceof NamedZoneDateTime nzdt && right instanceof Duration dur)
			return new NamedZoneDateTime(nzdt.value().minus(dur), nzdt.zone());
		if (left instanceof NamedZoneDateTime nzdt && right instanceof Period p)
			return new NamedZoneDateTime(nzdt.value().minus(p), nzdt.zone());
		if (left instanceof ZonedDateTime zdt && right instanceof Duration dur)
			return zdt.minus(dur);
		if (left instanceof ZonedDateTime zdt && right instanceof Period p)
			return zdt.minus(p);
		Instant inst1 = toInstant(left);
		Instant inst2 = toInstant(right);
		if (inst1 != null && inst2 != null)
			return Duration.between(inst2, inst1);
		if (inst1 != null && right instanceof LocalDate d)
			return Duration.between(d.atStartOfDay(ZoneOffset.UTC).toInstant(), inst1);
		if (left instanceof LocalDate d && inst2 != null)
			return Duration.between(inst2, d.atStartOfDay(ZoneOffset.UTC).toInstant());
		if (left instanceof Period p1 && right instanceof Period p2)
			return periodFromMonths(p1.toTotalMonths() - p2.toTotalMonths());
		if (left instanceof Duration d1 && right instanceof Duration d2)
			return d1.minus(d2);
		return null;
	}

	private static Object multiplyValues(Object left, Object right) {
		if (left == null || right == null)
			return null;
		if (left instanceof Number && right instanceof Number)
			return number(left).multiply(number(right));
		if (left instanceof Duration dur && right instanceof Number n)
			return Duration.ofNanos((long) (dur.toNanos() * n.doubleValue()));
		if (left instanceof Number n && right instanceof Duration dur)
			return Duration.ofNanos((long) (dur.toNanos() * n.doubleValue()));
		if (left instanceof Period p && right instanceof Number n)
			return periodFromMonths((long) (p.toTotalMonths() * n.doubleValue()));
		if (left instanceof Number n && right instanceof Period p)
			return periodFromMonths((long) (p.toTotalMonths() * n.doubleValue()));
		return null;
	}

	private static Object divideValues(Object left, Object right) {
		if (left == null || right == null)
			return null;
		if (left instanceof Number && right instanceof Number) {
			BigDecimal denom = number(right);
			if (denom.compareTo(BigDecimal.ZERO) == 0)
				return null;
			return number(left).divide(denom, MathContext.DECIMAL128);
		}
		if (left instanceof Duration dur && right instanceof Number n) {
			if (n.doubleValue() == 0)
				return null;
			return Duration.ofNanos((long) (dur.toNanos() / n.doubleValue()));
		}
		if (left instanceof Duration d1 && right instanceof Duration d2) {
			if (d2.toNanos() == 0)
				return null;
			return BigDecimal.valueOf(d1.toNanos()).divide(BigDecimal.valueOf(d2.toNanos()), MathContext.DECIMAL128);
		}
		if (left instanceof Period p && right instanceof Number n) {
			if (n.doubleValue() == 0)
				return null;
			return periodFromMonths((long) (p.toTotalMonths() / n.doubleValue()));
		}
		if (left instanceof Period p1 && right instanceof Period p2) {
			if (p2.toTotalMonths() == 0)
				return null;
			return BigDecimal.valueOf(p1.toTotalMonths()).divide(BigDecimal.valueOf(p2.toTotalMonths()),
					MathContext.DECIMAL128);
		}
		return null;
	}

	private static Object powerValues(Object left, Object right) {
		if (left == null || right == null)
			return null;
		if (left instanceof Number && right instanceof Number) {
			return BigDecimal.valueOf(Math.pow(number(left).doubleValue(), number(right).doubleValue()));
		}
		return null;
	}

	public static Object parseTime(String value) {
		if (value == null)
			return null;
		try {
			String norm = value;
			if (norm.startsWith("24:")) {
				if (!norm.matches(
						"24:00(?::00(?:\\.0{1,9})?)?(?:Z|[+-]\\d{2}:\\d{2}|@[A-Za-z_]+(?:/[A-Za-z0-9_+\\-]+)*)?"))
					return null;
				norm = "00:" + norm.substring(3);
			}
			int namedZone = norm.indexOf('@');
			return namedZone >= 0
					? new NamedZoneTime(LocalTime.parse(norm.substring(0, namedZone)),
							ZoneId.of(norm.substring(namedZone + 1)))
					: norm.matches(".*[Z+-][0-9:]*$") ? OffsetTime.parse(norm) : LocalTime.parse(norm);
		} catch (Exception e) {
			return null;
		}
	}

	private static final java.time.format.DateTimeFormatter FEEL_DATE_FORMATTER = new java.time.format.DateTimeFormatterBuilder()
			.appendValue(java.time.temporal.ChronoField.YEAR, 1, 10, java.time.format.SignStyle.NORMAL)
			.appendPattern("-MM-dd").toFormatter();

	private static final java.time.format.DateTimeFormatter FEEL_DATE_TIME_FORMATTER = new java.time.format.DateTimeFormatterBuilder()
			.appendValue(java.time.temporal.ChronoField.YEAR, 1, 10, java.time.format.SignStyle.NORMAL)
			.appendPattern("-MM-dd'T'HH:mm[:ss]").optionalStart()
			.appendFraction(java.time.temporal.ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd().optionalStart()
			.appendOffsetId().optionalEnd().toFormatter();

	public static LocalDate parseFeelDate(String value) {
		if (value == null)
			return null;
		try {
			java.util.regex.Matcher matcher = java.util.regex.Pattern
					.compile("(\\d{4}|[1-9]\\d{4,8}|-\\d{4,9})-(\\d{2})-(\\d{2})").matcher(value);
			if (!matcher.matches())
				return null;
			return LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
					Integer.parseInt(matcher.group(3)));
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	public static Object parseDateTime(String value) {
		if (value == null)
			return null;
		try {
			int tIdx = value.indexOf('T');
			if (tIdx < 0) {
				int at = value.indexOf('@');
				if (at >= 0) {
					LocalDate d = parseFeelDate(value.substring(0, at));
					if (d == null)
						return null;
					return new NamedZoneDateTime(LocalDateTime.of(d, LocalTime.MIDNIGHT),
							ZoneId.of(value.substring(at + 1)));
				}
				if (value.endsWith("Z")) {
					LocalDate d = parseFeelDate(value.substring(0, value.length() - 1));
					if (d == null)
						return null;
					return OffsetDateTime.of(d, LocalTime.MIDNIGHT, ZoneOffset.UTC);
				}
				LocalDate d = parseFeelDate(value);
				return d == null ? null : LocalDateTime.of(d, LocalTime.MIDNIGHT);
			}
			LocalDate date = parseFeelDate(value.substring(0, tIdx));
			if (date == null)
				return null;
			String timeText = value.substring(tIdx + 1);
			boolean endOfDay = timeText.startsWith("24:");
			Object time = parseTime(timeText);
			return time == null ? null : combineDateAndTime(endOfDay ? date.plusDays(1) : date, time);
		} catch (Exception e) {
			return null;
		}
	}

	public static Object feelDateAndTime(List<Object> arguments) {
		if (arguments == null || arguments.isEmpty() || arguments.size() > 2)
			return null;
		if (arguments.size() == 1)
			return arguments.get(0) instanceof String value ? parseDateTime(value) : null;
		Object dateArg = arguments.get(0);
		Object timeArg = arguments.get(1);
		LocalDate date = dateArg instanceof LocalDate d
				? d
				: dateArg instanceof LocalDateTime dt
						? dt.toLocalDate()
						: dateArg instanceof OffsetDateTime odt
								? odt.toLocalDate()
								: dateArg instanceof ZonedDateTime zdt
										? zdt.toLocalDate()
										: dateArg instanceof NamedZoneDateTime nzdt
												? nzdt.value().toLocalDate()
												: dateArg instanceof String s ? parseFeelDate(s) : null;
		Object time = timeArg instanceof String s ? parseTime(s) : timeArg;
		return date == null ? null : combineDateAndTime(date, time);
	}

	private static Object combineDateAndTime(LocalDate date, Object time) {
		if (time instanceof NamedZoneTime nzt)
			return new NamedZoneDateTime(LocalDateTime.of(date, nzt.value()), nzt.zone());
		if (time instanceof OffsetTime ot)
			return OffsetDateTime.of(date, ot.toLocalTime(), ot.getOffset());
		if (time instanceof LocalTime lt)
			return LocalDateTime.of(date, lt);
		return null;
	}

	public static boolean intervalBefore(Object a, Object b) {
		if (a == null || b == null)
			return false;
		if (a instanceof RuntimeRangeValue r1 && b instanceof RuntimeRangeValue r2) {
			if (r1.upper() == null || r2.lower() == null)
				return false;
			Integer c = compare(r1.upper(), r2.lower());
			if (c == null)
				return false;
			if (c < 0)
				return true;
			if (c == 0)
				return r1.upperBoundary() == RuntimeRangeBoundary.OPEN
						|| r2.lowerBoundary() == RuntimeRangeBoundary.OPEN;
			return false;
		}
		if (a instanceof RuntimeRangeValue r) {
			if (r.upper() == null)
				return false;
			Integer c = compare(r.upper(), b);
			if (c == null)
				return false;
			if (c < 0)
				return true;
			if (c == 0)
				return r.upperBoundary() == RuntimeRangeBoundary.OPEN;
			return false;
		}
		if (b instanceof RuntimeRangeValue r) {
			if (r.lower() == null)
				return false;
			Integer c = compare(a, r.lower());
			if (c == null)
				return false;
			if (c < 0)
				return true;
			if (c == 0)
				return r.lowerBoundary() == RuntimeRangeBoundary.OPEN;
			return false;
		}
		Integer c = compare(a, b);
		return c != null && c < 0;
	}

	public static boolean intervalAfter(Object a, Object b) {
		return intervalBefore(b, a);
	}

	public static boolean intervalMeets(Object a, Object b) {
		if (!(a instanceof RuntimeRangeValue r1) || !(b instanceof RuntimeRangeValue r2))
			return false;
		if (r1.upper() == null || r2.lower() == null)
			return false;
		return Boolean.TRUE.equals(equal(r1.upper(), r2.lower())) && r1.upperBoundary() == RuntimeRangeBoundary.CLOSED
				&& r2.lowerBoundary() == RuntimeRangeBoundary.CLOSED;
	}

	public static boolean intervalMetBy(Object a, Object b) {
		return intervalMeets(b, a);
	}

	public static boolean intervalStarts(Object a, Object b) {
		if (b instanceof RuntimeRangeValue r2) {
			if (a instanceof RuntimeRangeValue r1) {
				boolean lowerEq = (r1.lower() == null && r2.lower() == null)
						|| (Boolean.TRUE.equals(equal(r1.lower(), r2.lower()))
								&& r1.lowerBoundary() == r2.lowerBoundary());
				if (!lowerEq)
					return false;
				if (r2.upper() == null)
					return true;
				if (r1.upper() == null)
					return false;
				Integer c = compare(r1.upper(), r2.upper());
				if (c == null)
					return false;
				if (c < 0)
					return true;
				if (c == 0)
					return r1.upperBoundary() == RuntimeRangeBoundary.OPEN
							|| r2.upperBoundary() == RuntimeRangeBoundary.CLOSED;
				return false;
			} else {
				if (r2.lower() == null)
					return false;
				return Boolean.TRUE.equals(equal(a, r2.lower())) && r2.lowerBoundary() == RuntimeRangeBoundary.CLOSED;
			}
		}
		return false;
	}

	public static boolean intervalStartedBy(Object a, Object b) {
		return intervalStarts(b, a);
	}

	public static boolean intervalFinishes(Object a, Object b) {
		if (b instanceof RuntimeRangeValue r2) {
			if (a instanceof RuntimeRangeValue r1) {
				boolean upperEq = (r1.upper() == null && r2.upper() == null)
						|| (Boolean.TRUE.equals(equal(r1.upper(), r2.upper()))
								&& r1.upperBoundary() == r2.upperBoundary());
				if (!upperEq)
					return false;
				if (r2.lower() == null)
					return true;
				if (r1.lower() == null)
					return false;
				Integer c = compare(r1.lower(), r2.lower());
				if (c == null)
					return false;
				if (c > 0)
					return true;
				if (c == 0)
					return r1.lowerBoundary() == RuntimeRangeBoundary.OPEN
							|| r2.lowerBoundary() == RuntimeRangeBoundary.CLOSED;
				return false;
			} else {
				if (r2.upper() == null)
					return false;
				return Boolean.TRUE.equals(equal(a, r2.upper())) && r2.upperBoundary() == RuntimeRangeBoundary.CLOSED;
			}
		}
		return false;
	}

	public static boolean intervalFinishedBy(Object a, Object b) {
		return intervalFinishes(b, a);
	}

	public static boolean intervalDuring(Object a, Object b) {
		if (b instanceof RuntimeRangeValue r2) {
			if (a instanceof RuntimeRangeValue r1) {
				boolean lowerOk;
				if (r2.lower() == null)
					lowerOk = true;
				else if (r1.lower() == null)
					lowerOk = false;
				else {
					Integer c = compare(r1.lower(), r2.lower());
					if (c == null)
						return false;
					lowerOk = c > 0 || (c == 0 && (r1.lowerBoundary() == RuntimeRangeBoundary.OPEN
							|| r2.lowerBoundary() == RuntimeRangeBoundary.CLOSED));
				}
				if (!lowerOk)
					return false;
				boolean upperOk;
				if (r2.upper() == null)
					upperOk = true;
				else if (r1.upper() == null)
					upperOk = false;
				else {
					Integer c = compare(r1.upper(), r2.upper());
					if (c == null)
						return false;
					upperOk = c < 0 || (c == 0 && (r1.upperBoundary() == RuntimeRangeBoundary.OPEN
							|| r2.upperBoundary() == RuntimeRangeBoundary.CLOSED));
				}
				return upperOk;
			} else {
				if (r2.lower() != null) {
					Integer c = compare(a, r2.lower());
					if (c == null || c < 0 || (c == 0 && r2.lowerBoundary() == RuntimeRangeBoundary.OPEN))
						return false;
				}
				if (r2.upper() != null) {
					Integer c = compare(a, r2.upper());
					if (c == null || c > 0 || (c == 0 && r2.upperBoundary() == RuntimeRangeBoundary.OPEN))
						return false;
				}
				return true;
			}
		}
		return false;
	}

	public static boolean intervalIncludes(Object a, Object b) {
		return intervalDuring(b, a);
	}

	public static boolean intervalCoincides(Object a, Object b) {
		if (a instanceof RuntimeRangeValue r1 && b instanceof RuntimeRangeValue r2) {
			boolean lowerEq = (r1.lower() == null && r2.lower() == null)
					|| (Boolean.TRUE.equals(equal(r1.lower(), r2.lower())) && r1.lowerBoundary() == r2.lowerBoundary());
			boolean upperEq = (r1.upper() == null && r2.upper() == null)
					|| (Boolean.TRUE.equals(equal(r1.upper(), r2.upper())) && r1.upperBoundary() == r2.upperBoundary());
			return lowerEq && upperEq;
		}
		return Boolean.TRUE.equals(equal(a, b));
	}

	public static boolean intervalOverlapsBefore(Object a, Object b) {
		if (!(a instanceof RuntimeRangeValue r1) || !(b instanceof RuntimeRangeValue r2))
			return false;
		if (r1.lower() != null && r2.lower() != null) {
			Integer c1 = compare(r1.lower(), r2.lower());
			if (c1 == null || c1 > 0 || (c1 == 0 && !(r1.lowerBoundary() == RuntimeRangeBoundary.CLOSED
					&& r2.lowerBoundary() == RuntimeRangeBoundary.OPEN)))
				return false;
		}
		if (r1.upper() == null || r2.lower() == null)
			return false;
		Integer c2 = compare(r1.upper(), r2.lower());
		if (c2 == null || c2 < 0 || (c2 == 0 && (r1.upperBoundary() == RuntimeRangeBoundary.OPEN
				|| r2.lowerBoundary() == RuntimeRangeBoundary.OPEN)))
			return false;
		if (r1.upper() != null && r2.upper() != null) {
			Integer c3 = compare(r1.upper(), r2.upper());
			if (c3 == null || c3 > 0 || (c3 == 0 && r1.upperBoundary() == RuntimeRangeBoundary.CLOSED
					&& r2.upperBoundary() == RuntimeRangeBoundary.OPEN))
				return false;
		}
		return true;
	}

	public static boolean intervalOverlapsAfter(Object a, Object b) {
		return intervalOverlapsBefore(b, a);
	}

	public static boolean intervalOverlaps(Object a, Object b) {
		return intervalOverlapsBefore(a, b) || intervalOverlapsAfter(a, b) || intervalDuring(a, b)
				|| intervalIncludes(a, b) || intervalCoincides(a, b);
	}

	public static boolean isExternalJavaDescriptor(Object desc) {
		if (desc == null)
			return false;
		if (desc instanceof RuntimeContextValue rcv)
			return isExternalJavaDescriptor(rcv.namedFields());
		if (!(desc instanceof Map<?, ?> m))
			return false;
		if (m.containsKey("java")) {
			Object j = m.get("java");
			if (j instanceof Map<?, ?> jm)
				return jm.containsKey("class") && jm.containsKey("method signature");
			if (j instanceof RuntimeContextValue rcv)
				return rcv.namedFields().containsKey("class") && rcv.namedFields().containsKey("method signature");
		}
		return m.containsKey("class") && m.containsKey("method signature");
	}

	public static Object invokeExternalJava(Object desc, List<Object> args) {
		if (desc == null)
			return null;
		Map<?, ?> m;
		if (desc instanceof Map<?, ?> map) {
			m = map;
		} else if (desc instanceof RuntimeContextValue rcv) {
			m = rcv.namedFields();
		} else {
			return null;
		}
		if (m.containsKey("java")) {
			Object j = m.get("java");
			if (j instanceof Map<?, ?> jm)
				m = jm;
			else if (j instanceof RuntimeContextValue rcv)
				m = rcv.namedFields();
		}
		Object clsObj = m.get("class");
		Object sigObj = m.get("method signature");
		if (clsObj == null || sigObj == null)
			return null;
		String className = String.valueOf(clsObj).trim();
		String signature = String.valueOf(sigObj).trim();
		try {
			int pOpen = signature.indexOf('(');
			int pClose = signature.lastIndexOf(')');
			if (pOpen < 0 || pClose < pOpen)
				return null;
			String methodName = signature.substring(0, pOpen).trim();
			String paramsStr = signature.substring(pOpen + 1, pClose).trim();
			String[] paramTypeNames = paramsStr.isEmpty() ? new String[0] : paramsStr.split(",");
			Class<?>[] paramClasses = new Class<?>[paramTypeNames.length];
			for (int i = 0; i < paramTypeNames.length; i++) {
				paramClasses[i] = resolveJavaClass(paramTypeNames[i].trim());
				if (paramClasses[i] == null)
					return null;
			}
			Class<?> targetClass = Class.forName(className);
			java.lang.reflect.Method targetMethod = targetClass.getMethod(methodName, paramClasses);
			Object[] convertedArgs = new Object[paramClasses.length];
			for (int i = 0; i < paramClasses.length; i++) {
				Object arg = i < args.size() ? args.get(i) : null;
				convertedArgs[i] = convertToJavaType(arg, paramClasses[i]);
				// null result for a primitive type signals a coercion failure → FEEL error
				if (convertedArgs[i] == null && paramClasses[i].isPrimitive() && arg != null)
					return null;
			}
			Object result = targetMethod.invoke(null, convertedArgs);
			return convertFromJavaType(result);
		} catch (Exception e) {
			return null;
		}
	}

	private static Class<?> resolveJavaClass(String name) {
		return switch (name) {
			case "int" -> int.class;
			case "long" -> long.class;
			case "double" -> double.class;
			case "float" -> float.class;
			case "boolean" -> boolean.class;
			case "char" -> char.class;
			case "byte" -> byte.class;
			case "short" -> short.class;
			case "java.lang.String", "String", "string" -> String.class;
			case "java.math.BigDecimal", "BigDecimal" -> BigDecimal.class;
			case "java.lang.Object", "Object" -> Object.class;
			case "java.util.List", "List", "list" -> List.class;
			case "java.util.Map", "Map", "context" -> Map.class;
			default -> {
				try {
					yield Class.forName(name);
				} catch (Exception e) {
					yield null;
				}
			}
		};
	}

	private static Object convertToJavaType(Object arg, Class<?> target) {
		if (arg == null) {
			if (target.isPrimitive()) {
				if (target == boolean.class)
					return false;
				if (target == char.class)
					return (char) 0;
				if (target == byte.class)
					return (byte) 0;
				if (target == short.class)
					return (short) 0;
				if (target == int.class)
					return 0;
				if (target == long.class)
					return 0L;
				if (target == float.class)
					return 0.0f;
				if (target == double.class)
					return 0.0d;
			}
			return null;
		}
		if (target == double.class || target == Double.class) {
			return arg instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(arg));
		}
		if (target == int.class || target == Integer.class) {
			return arg instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(arg));
		}
		if (target == long.class || target == Long.class) {
			return arg instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(arg));
		}
		if (target == float.class || target == Float.class) {
			return arg instanceof Number n ? n.floatValue() : Float.parseFloat(String.valueOf(arg));
		}
		if (target == short.class || target == Short.class) {
			return arg instanceof Number n ? n.shortValue() : Short.parseShort(String.valueOf(arg));
		}
		if (target == byte.class || target == Byte.class) {
			return arg instanceof Number n ? n.byteValue() : Byte.parseByte(String.valueOf(arg));
		}
		if (target == char.class || target == Character.class) {
			if (arg instanceof String s && s.length() == 1)
				return s.charAt(0);
			if (arg instanceof Number n)
				return (char) n.intValue();
			// Cannot convert — signal coercion failure with null
			return null;
		}
		if (target == boolean.class || target == Boolean.class) {
			return Boolean.TRUE.equals(arg);
		}
		if (target == String.class) {
			return String.valueOf(arg);
		}
		if (target == BigDecimal.class) {
			return arg instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(arg));
		}
		// varargs: target is an array type (e.g. Object[])
		if (target.isArray()) {
			Class<?> componentType = target.getComponentType();
			if (arg instanceof List<?> list) {
				Object array = java.lang.reflect.Array.newInstance(componentType, list.size());
				for (int i = 0; i < list.size(); i++) {
					java.lang.reflect.Array.set(array, i, convertToJavaType(list.get(i), componentType));
				}
				return array;
			}
			Object array = java.lang.reflect.Array.newInstance(componentType, 1);
			java.lang.reflect.Array.set(array, 0, convertToJavaType(arg, componentType));
			return array;
		}
		return arg;
	}

	private static Object convertFromJavaType(Object result) {
		if (result == null)
			return null;
		if (result instanceof Double d) {
			if (d.isNaN() || d.isInfinite())
				return null;
			return new BigDecimal(Double.toString(d));
		}
		if (result instanceof Float f) {
			if (f.isNaN() || f.isInfinite())
				return null;
			return new BigDecimal(Float.toString(f));
		}
		if (result instanceof Integer i)
			return BigDecimal.valueOf(i);
		if (result instanceof Long l)
			return BigDecimal.valueOf(l);
		if (result instanceof Short s)
			return BigDecimal.valueOf(s);
		if (result instanceof Byte b)
			return BigDecimal.valueOf(b);
		return result;
	}

	public static Object parseLiteralValue(String text) {
		if (text == null || text.isBlank() || "null".equals(text))
			return null;
		if ("true".equalsIgnoreCase(text))
			return Boolean.TRUE;
		if ("false".equalsIgnoreCase(text))
			return Boolean.FALSE;
		if (text.startsWith("@\"") && text.endsWith("\"") && text.length() >= 3) {
			String raw = text.substring(2, text.length() - 1);
			if (raw.length() == 10 && raw.charAt(4) == '-' && raw.charAt(7) == '-') {
				try {
					return LocalDate.parse(raw);
				} catch (Exception ignored) {
				}
			}
			Object dt = parseDateTime(raw);
			if (dt != null)
				return dt;
			Object t = parseTime(raw);
			if (t != null)
				return t;
			Object d = parseDuration(raw);
			if (d != null)
				return d;
			try {
				return LocalDate.parse(raw);
			} catch (Exception ignored) {
			}
			return null;
		} else if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
			return text.substring(1, text.length() - 1);
		}
		if (text.startsWith("time(\"") && text.endsWith("\")")) {
			return parseTime(text.substring(6, text.length() - 2));
		}
		if (text.startsWith("date(\"") && text.endsWith("\")")) {
			try {
				return LocalDate.parse(text.substring(6, text.length() - 2));
			} catch (Exception e) {
				return null;
			}
		}
		if ((text.startsWith("date and time(\"") || text.startsWith("dateTime(\"")) && text.endsWith("\")")) {
			int pIdx = text.indexOf('\"');
			return parseDateTime(text.substring(pIdx + 1, text.length() - 2));
		}
		if (text.startsWith("duration(\"") && text.endsWith("\")")) {
			return parseDuration(text.substring(10, text.length() - 2));
		}
		if (text.startsWith("years and months duration(\"") && text.endsWith("\")")) {
			return parseDuration(text.substring(27, text.length() - 2));
		}
		try {
			return new BigDecimal(text);
		} catch (Exception ignored) {
		}
		if (text.length() == 10 && text.charAt(4) == '-' && text.charAt(7) == '-') {
			try {
				return LocalDate.parse(text);
			} catch (Exception ignored) {
			}
		}
		try {
			Object dt = parseDateTime(text);
			if (dt != null)
				return dt;
		} catch (Exception ignored) {
		}
		try {
			Object t = parseTime(text);
			if (t != null)
				return t;
		} catch (Exception ignored) {
		}
		try {
			Object d = parseDuration(text);
			if (d != null)
				return d;
		} catch (Exception ignored) {
		}
		return null;
	}

	private static final Map<String, List<List<String>>> BUILTIN_PARAM_SPECS = Map.ofEntries(
			Map.entry("range", List.of(List.of("from"))), Map.entry("not", List.of(List.of("negand"))),
			Map.entry("is", List.of(List.of("value1", "value2"))),
			Map.entry("substring",
					List.of(List.of("string", "start position"), List.of("string", "start position", "length"))),
			Map.entry("string length", List.of(List.of("string"))), Map.entry("upper case", List.of(List.of("string"))),
			Map.entry("lower case", List.of(List.of("string"))),
			Map.entry("contains", List.of(List.of("string", "match"))),
			Map.entry("starts with", List.of(List.of("string", "match"))),
			Map.entry("ends with", List.of(List.of("string", "match"))),
			Map.entry("substring before", List.of(List.of("string", "match"))),
			Map.entry("substring after", List.of(List.of("string", "match"))),
			Map.entry("matches", List.of(List.of("input", "pattern"), List.of("input", "pattern", "flags"))),
			Map.entry("replace",
					List.of(List.of("input", "pattern", "replacement"),
							List.of("input", "pattern", "replacement", "flags"))),
			Map.entry("split", List.of(List.of("string", "delimiter"))),
			Map.entry("date", List.of(List.of("from"), List.of("year", "month", "day"))),
			Map.entry("date and time", List.of(List.of("from"), List.of("date", "time"))),
			Map.entry("time",
					List.of(List.of("from"), List.of("hour", "minute", "second"),
							List.of("hour", "minute", "second", "offset"))),
			Map.entry("duration", List.of(List.of("from"))),
			Map.entry("years and months duration", List.of(List.of("from", "to"))),
			Map.entry("day and time duration", List.of(List.of("from", "to"))),
			Map.entry("number", List.of(List.of("from"), List.of("from", "grouping separator", "decimal separator"))),
			Map.entry("decimal", List.of(List.of("n", "scale"))),
			Map.entry("floor", List.of(List.of("n"), List.of("n", "scale"))),
			Map.entry("ceiling", List.of(List.of("n"), List.of("n", "scale"))),
			Map.entry("round up", List.of(List.of("n", "scale"))),
			Map.entry("round down", List.of(List.of("n", "scale"))),
			Map.entry("round half up", List.of(List.of("n", "scale"))),
			Map.entry("round half down", List.of(List.of("n", "scale"))), Map.entry("abs", List.of(List.of("n"))),
			Map.entry("sqrt", List.of(List.of("number"))), Map.entry("log", List.of(List.of("number"))),
			Map.entry("exp", List.of(List.of("number"))), Map.entry("modulo", List.of(List.of("dividend", "divisor"))),
			Map.entry("even", List.of(List.of("number"))), Map.entry("odd", List.of(List.of("number"))),
			Map.entry("list contains", List.of(List.of("list", "element"))),
			Map.entry("list replace",
					List.of(List.of("list", "position", "newItem"), List.of("list", "match", "newItem"))),
			Map.entry("insert before", List.of(List.of("list", "position", "newItem"))),
			Map.entry("remove", List.of(List.of("list", "position"))),
			Map.entry("sublist",
					List.of(List.of("list", "start position"), List.of("list", "start position", "length"))),
			Map.entry("append", List.of(List.of("list", "item"))), Map.entry("union", List.of(List.of("list"))),
			Map.entry("distinct values", List.of(List.of("list"))), Map.entry("flatten", List.of(List.of("list"))),
			Map.entry("context merge", List.of(List.of("contexts"))),
			Map.entry("context put", List.of(List.of("context", "key", "value"), List.of("context", "keys", "value"))),
			Map.entry("context", List.of(List.of("entries"))), Map.entry("get value", List.of(List.of("m", "key"))),
			Map.entry("get entries", List.of(List.of("m"))),
			Map.entry("sort", List.of(List.of("list", "precedes"), List.of("list"))),
			Map.entry("day of year", List.of(List.of("date"))),
			Map.entry("day of week", List.of(List.of("date"))),
			Map.entry("month of year", List.of(List.of("date"))),
			Map.entry("week of year", List.of(List.of("date"))),
			Map.entry("string join",
					List.of(List.of("list"), List.of("list", "delimiter"),
							List.of("list", "delimiter", "prefix", "suffix"))),
			Map.entry("all", List.of(List.of("list"))), Map.entry("any", List.of(List.of("list"))),
			Map.entry("sum", List.of(List.of("list"))), Map.entry("mean", List.of(List.of("list"))),
			Map.entry("median", List.of(List.of("list"))), Map.entry("mode", List.of(List.of("list"))),
			Map.entry("stddev", List.of(List.of("list"))), Map.entry("product", List.of(List.of("list"))),
			Map.entry("min", List.of(List.of("list"))), Map.entry("max", List.of(List.of("list"))),
			Map.entry("count", List.of(List.of("list"))));

	public static Object feelAbs(List<Object> arguments) {
		if (arguments == null || arguments.size() != 1)
			return null;
		Object a = arguments.get(0);
		if (a == null)
			return null;
		if (a instanceof Number n)
			return number(n).abs();
		if (a instanceof Duration d)
			return d.abs();
		if (a instanceof Period p)
			return p.toTotalMonths() < 0 ? p.negated() : p;
		return null;
	}

	public static BigDecimal feelSqrt(List<Object> arguments) {
		if (arguments == null || arguments.size() != 1)
			return null;
		Object a = arguments.get(0);
		if (!(a instanceof Number))
			return null;
		BigDecimal n = number(a);
		if (n == null || n.signum() < 0)
			return null;
		return BigDecimal.valueOf(Math.sqrt(n.doubleValue()));
	}

	public static BigDecimal feelExp(List<Object> arguments) {
		if (arguments == null || arguments.size() != 1)
			return null;
		Object a = arguments.get(0);
		if (!(a instanceof Number))
			return null;
		BigDecimal n = number(a);
		if (n == null)
			return null;
		return BigDecimal.valueOf(Math.exp(n.doubleValue()));
	}

	public static BigDecimal feelLog(List<Object> arguments) {
		if (arguments == null || arguments.size() != 1)
			return null;
		Object a = arguments.get(0);
		if (!(a instanceof Number))
			return null;
		BigDecimal n = number(a);
		if (n == null || n.signum() <= 0)
			return null;
		return BigDecimal.valueOf(Math.log(n.doubleValue()));
	}

	public static Boolean feelEven(List<Object> arguments) {
		if (arguments == null || arguments.size() != 1)
			return null;
		Object a = arguments.get(0);
		if (!(a instanceof Number))
			return null;
		BigDecimal n = number(a);
		if (n == null)
			return null;
		return n.intValue() % 2 == 0;
	}

	public static Boolean feelOdd(List<Object> arguments) {
		if (arguments == null || arguments.size() != 1)
			return null;
		Object a = arguments.get(0);
		if (!(a instanceof Number))
			return null;
		BigDecimal n = number(a);
		if (n == null)
			return null;
		return n.intValue() % 2 != 0;
	}

	public static List<Object> feelMode(List<Object> arguments) {
		if (arguments == null || arguments.isEmpty())
			return null;
		if (arguments.size() == 1 && arguments.get(0) == null)
			return null;
		List<Object> list;
		if (arguments.size() == 1 && arguments.get(0) instanceof List<?> l) {
			list = (List<Object>) l;
		} else {
			list = arguments;
		}
		if (list.isEmpty())
			return List.of();
		Map<Object, Integer> counts = new HashMap<>();
		for (Object o : list) {
			if (o == null)
				return null;
			BigDecimal n = number(o);
			if (n == null)
				return null;
			counts.put(n, counts.getOrDefault(n, 0) + 1);
		}
		int max = Collections.max(counts.values());
		List<Object> result = new ArrayList<>();
		for (Map.Entry<Object, Integer> e : counts.entrySet()) {
			if (e.getValue() == max)
				result.add(e.getKey());
		}
		result.sort(DmnRuntime::compare);
		return result;
	}

	public static BigDecimal feelNumber(List<Object> arguments) {
		if (arguments == null || (arguments.size() != 1 && arguments.size() != 3))
			return null;
		if (arguments.size() == 1) {
			Object a = arguments.get(0);
			if (a == null)
				return null;
			if (a instanceof Number num)
				return number(num);
			if (a instanceof String s) {
				try {
					return new BigDecimal(s.trim());
				} catch (Exception e) {
					return null;
				}
			}
			return null;
		}
		Object fromObj = arguments.get(0);
		Object groupSepObj = arguments.get(1);
		Object decSepObj = arguments.get(2);
		if (!(fromObj instanceof String from))
			return null;
		String groupSep = null;
		if (groupSepObj != null) {
			if (!(groupSepObj instanceof String gs) || (!gs.equals(" ") && !gs.equals(".") && !gs.equals(",")))
				return null;
			groupSep = gs;
		}
		String decSep = null;
		if (decSepObj != null) {
			if (!(decSepObj instanceof String ds) || (!ds.equals(".") && !ds.equals(",")))
				return null;
			decSep = ds;
		}
		if (groupSep != null && decSep != null && groupSep.equals(decSep))
			return null;
		try {
			String s = from.trim();
			if (groupSep != null) {
				s = s.replace(groupSep, "");
			}
			if (decSep != null && !".".equals(decSep)) {
				s = s.replace(decSep, ".");
			}
			return new BigDecimal(s);
		} catch (Exception e) {
			return null;
		}
	}

	public static String feelSubstring(String str, Integer start, Integer len) {
		if (str == null || start == null)
			return null;
		int totalCodePoints = str.codePointCount(0, str.length());
		int cpStart = start > 0 ? start - 1 : totalCodePoints + start;
		if (cpStart < 0 || cpStart >= totalCodePoints)
			return "";
		int charStart = str.offsetByCodePoints(0, cpStart);
		if (len != null) {
			if (len <= 0)
				return "";
			int cpEnd = Math.min(totalCodePoints, cpStart + len);
			int charEnd = str.offsetByCodePoints(0, cpEnd);
			return str.substring(charStart, charEnd);
		}
		return str.substring(charStart);
	}

	public static Boolean feelAll(List<Object> arguments) {
		if (arguments == null || arguments.isEmpty())
			return null;
		if (arguments.size() == 1 && arguments.get(0) == null)
			return null;
		List<Object> list;
		if (arguments.size() == 1 && arguments.get(0) instanceof List<?> l) {
			list = (List<Object>) l;
		} else if (arguments.size() == 1 && !(arguments.get(0) instanceof Boolean)) {
			return null;
		} else {
			list = arguments;
		}
		if (list.isEmpty())
			return true;
		boolean hasNullOrNonBoolean = false;
		for (Object o : list) {
			if (Boolean.FALSE.equals(o))
				return false;
			if (!Boolean.TRUE.equals(o))
				hasNullOrNonBoolean = true;
		}
		return hasNullOrNonBoolean ? null : true;
	}

	public static BigDecimal feelModulo(List<Object> arguments) {
		if (arguments == null || arguments.size() != 2)
			return null;
		Object aObj = arguments.get(0);
		Object bObj = arguments.get(1);
		if (!(aObj instanceof Number) || !(bObj instanceof Number))
			return null;
		BigDecimal a = number(aObj);
		BigDecimal b = number(bObj);
		if (a == null || b == null || b.signum() == 0)
			return null;
		BigDecimal q = a.divide(b, MathContext.DECIMAL128);
		BigDecimal floorQ = q.setScale(0, java.math.RoundingMode.FLOOR);
		return a.subtract(floorQ.multiply(b));
	}

	public static Boolean feelAny(List<Object> arguments) {
		if (arguments == null || arguments.isEmpty())
			return null;
		if (arguments.size() == 1 && arguments.get(0) == null)
			return null;
		List<Object> list;
		if (arguments.size() == 1 && arguments.get(0) instanceof List<?> l) {
			list = (List<Object>) l;
		} else if (arguments.size() == 1 && !(arguments.get(0) instanceof Boolean)) {
			return null;
		} else {
			list = arguments;
		}
		if (list.isEmpty())
			return false;
		boolean hasNullOrNonBoolean = false;
		for (Object o : list) {
			if (Boolean.TRUE.equals(o))
				return true;
			if (!Boolean.FALSE.equals(o))
				hasNullOrNonBoolean = true;
		}
		return hasNullOrNonBoolean ? null : false;
	}

	public static List<Object> bindNamedBuiltinArguments(String fnName, Map<String, Object> namedArgs) {
		if (namedArgs == null || namedArgs.isEmpty())
			return List.of();
		List<List<String>> overloads = BUILTIN_PARAM_SPECS.get(fnName);
		if (overloads == null)
			return null;
		for (List<String> spec : overloads) {
			boolean partialIsInvocation = "is".equals(fnName) && spec.containsAll(namedArgs.keySet());
			if ((spec.size() == namedArgs.size() && namedArgs.keySet().containsAll(spec)) || partialIsInvocation) {
				List<Object> args = new ArrayList<>(spec.size());
				for (String param : spec) {
					args.add(namedArgs.get(param));
				}
				// context put(context, key, value): 'key' must be a string, not a list.
				// If 'key' was the matched param (not 'keys') and its value is a list → null.
				if ("context put".equals(fnName) && spec.contains("key") && !spec.contains("keys")) {
					Object keyVal = namedArgs.get("key");
					if (keyVal instanceof List<?>)
						return null;
				}
				return args;
			}
		}
		return null;
	}

	public static Object feelMatches(List<Object> args) {
		if (args == null || args.size() < 2 || args.size() > 3 || args.get(0) == null || args.get(1) == null)
			return null;
		try {
			java.util.regex.Pattern pattern = compileFeelRegex(String.valueOf(args.get(1)),
					args.size() == 3 && args.get(2) != null ? String.valueOf(args.get(2)) : "");
			return pattern.matcher(String.valueOf(args.get(0))).find();
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	public static Object feelReplace(List<Object> args) {
		if (args == null || args.size() < 3 || args.size() > 4 || args.get(0) == null || args.get(1) == null
				|| args.get(2) == null)
			return null;
		try {
			java.util.regex.Pattern pattern = compileFeelRegex(String.valueOf(args.get(1)),
					args.size() == 4 && args.get(3) != null ? String.valueOf(args.get(3)) : "");
			return pattern.matcher(String.valueOf(args.get(0))).replaceAll(String.valueOf(args.get(2)));
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static java.util.regex.Pattern compileFeelRegex(String source, String flags) {
		int javaFlags = 0;
		boolean freeSpacing = false;
		for (char flag : flags.toCharArray()) {
			javaFlags |= switch (flag) {
				case 'i' -> java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE;
				case 's' -> java.util.regex.Pattern.DOTALL;
				case 'm' -> java.util.regex.Pattern.MULTILINE;
				case 'x' -> 0;
				case 'q' -> java.util.regex.Pattern.LITERAL;
				default -> throw new IllegalArgumentException("Unsupported FEEL regular-expression flag");
			};
			freeSpacing |= flag == 'x';
		}
		String translated = source.replaceAll("\\[([^\\]\\[]+)-\\[([^\\]]+)\\]\\]", "[$1&&[^$2]]");
		if (freeSpacing)
			translated = stripFeelRegexWhitespace(translated);
		translated = translated.replace("\\p{IsBasicLatin}", "\\p{InBasic_Latin}");
		validateBackReferences(translated);
		return java.util.regex.Pattern.compile(translated, javaFlags);
	}

	private static void validateBackReferences(String pattern) {
		int groupCount = 0;
		boolean inClass = false;
		for (int index = 0; index < pattern.length(); index++) {
			char ch = pattern.charAt(index);
			if (ch == '\\' && index + 1 < pattern.length()) {
				index++;
				continue;
			}
			if (ch == '[')
				inClass = true;
			else if (ch == ']')
				inClass = false;
			else if (ch == '(' && !inClass && (index + 1 >= pattern.length() || pattern.charAt(index + 1) != '?'))
				groupCount++;
		}
		inClass = false;
		for (int index = 0; index + 1 < pattern.length(); index++) {
			char ch = pattern.charAt(index);
			if (ch == '[')
				inClass = true;
			else if (ch == ']')
				inClass = false;
			else if (ch == '\\' && !inClass && Character.isDigit(pattern.charAt(index + 1))) {
				int reference = Character.digit(pattern.charAt(index + 1), 10);
				if (reference > groupCount)
					throw new IllegalArgumentException("Invalid FEEL regular-expression backreference");
				index++;
			}
		}
	}

	private static String stripFeelRegexWhitespace(String pattern) {
		StringBuilder result = new StringBuilder();
		boolean inClass = false;
		for (int index = 0; index < pattern.length(); index++) {
			char ch = pattern.charAt(index);
			if (ch == '\\' && index + 1 < pattern.length()) {
				char next = pattern.charAt(++index);
				result.append(Character.isWhitespace(next) ? "\\" : "\\" + next);
				continue;
			}
			if (ch == '[')
				inClass = true;
			else if (ch == ']')
				inClass = false;
			if (!Character.isWhitespace(ch) || inClass)
				result.append(ch);
		}
		return result.toString().replaceAll("(\\\\p\\{[^}]*)\\s+([^}]*})", "$1$2");
	}

	public static String feelSubstringBefore(List<Object> args) {
		if (args == null || args.size() != 2 || args.get(0) == null || args.get(1) == null)
			return null;
		if (!(args.get(0) instanceof String) || !(args.get(1) instanceof String))
			return null;
		String str = (String) args.get(0);
		String sub = (String) args.get(1);
		int idx = str.indexOf(sub);
		return idx < 0 ? "" : str.substring(0, idx);
	}

	public static String feelSubstringAfter(List<Object> args) {
		if (args == null || args.size() != 2 || args.get(0) == null || args.get(1) == null)
			return null;
		if (!(args.get(0) instanceof String) || !(args.get(1) instanceof String))
			return null;
		String str = (String) args.get(0);
		String sub = (String) args.get(1);
		int idx = str.indexOf(sub);
		return idx < 0 ? "" : str.substring(idx + sub.length());
	}

	public static String feelStringJoin(List<Object> arguments) {
		if (arguments == null || arguments.isEmpty() || arguments.size() > 4 || arguments.size() == 3)
			return null;
		Object firstArg = arguments.get(0);
		if (firstArg == null)
			return null;
		List<?> items;
		if (firstArg instanceof List<?> l) {
			items = l;
		} else if (firstArg instanceof String s) {
			items = List.of(s);
		} else {
			return null;
		}
		String delimiter = "";
		if (arguments.size() > 1) {
			Object delimArg = arguments.get(1);
			if (delimArg != null) {
				if (!(delimArg instanceof String))
					return null;
				delimiter = (String) delimArg;
			}
		}
		String prefix = "";
		String suffix = "";
		if (arguments.size() == 4) {
			Object p = arguments.get(2);
			Object s = arguments.get(3);
			if (p != null && !(p instanceof String))
				return null;
			if (s != null && !(s instanceof String))
				return null;
			if (p != null)
				prefix = (String) p;
			if (s != null)
				suffix = (String) s;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(prefix);
		boolean first = true;
		for (Object o : items) {
			if (o == null)
				continue;
			if (!(o instanceof String s))
				return null;
			if (!first)
				sb.append(delimiter);
			sb.append(s);
			first = false;
		}
		sb.append(suffix);
		return sb.toString();
	}
}

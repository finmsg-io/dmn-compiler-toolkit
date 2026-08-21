package io.finmsg.dmn.ir;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds deterministic constant-pool and built-in dispatch metadata without
 * changing lossless IR.
 */
public final class RuntimeIrOptimizer {
	public RuntimeOptimizedModel optimize(RuntimeModel model) {
		Objects.requireNonNull(model, "model");
		Collector collector = new Collector();
		model.decisions().forEach(decision -> {
			decision.expression().ifPresent(collector::expression);
			decision.decisionTable().ifPresent(collector::table);
		});
		model.businessKnowledgeModels().forEach(bkm -> bkm.function().ifPresent(collector::expression));
		return collector.result(model);
	}

	private static final class Collector {
		private final Map<PoolKey, Integer> poolIds = new LinkedHashMap<>();
		private final List<RuntimeConstantPoolEntry> pool = new ArrayList<>();
		private final List<RuntimeConstantUse> uses = new ArrayList<>();
		private final List<RuntimeBuiltinBinding> builtins = new ArrayList<>();
		private int nextOrdinal;

		private RuntimeOptimizedModel result(RuntimeModel model) {
			return new RuntimeOptimizedModel(model, pool, uses, builtins);
		}

		private void table(RuntimeDecisionTable table) {
			table.inputs().forEach(input -> {
				expression(input.expression());
				input.allowedValues().ifPresent(this::tests);
			});
			table.outputs().forEach(output -> {
				output.allowedValues().ifPresent(this::tests);
				output.defaultValue().ifPresent(this::expression);
			});
			table.rules().forEach(rule -> {
				rule.inputEntries().forEach(this::tests);
				rule.outputEntries().forEach(this::expression);
			});
		}

		private void tests(RuntimeUnaryTests tests) {
			tests.tests().forEach(test -> {
				switch (test) {
					case RuntimeComparisonUnaryTest value -> expression(value.endpoint());
					case RuntimeRangeUnaryTest value -> expression(value.range());
					case RuntimeExpressionUnaryTest value -> expression(value.expression());
				}
			});
		}

		private void expression(RuntimeExpression expression) {
			int ordinal = nextOrdinal++;
			switch (expression) {
				case RuntimeConstant constant -> constant(ordinal, constant);
				case RuntimeFunctionCall call -> {
					bindBuiltin(ordinal, call.function());
					call.arguments().forEach(this::expression);
				}
				case RuntimeInvocationExpression invocation -> {
					invocation.function().ifPresent(name -> bindBuiltin(ordinal, name));
					invocation.target().ifPresent(this::expression);
					invocation.namedArguments().forEach(value -> expression(value.expression()));
					invocation.positionalArguments().forEach(this::expression);
				}
				case RuntimeUnaryExpression value -> expression(value.operand());
				case RuntimeBinaryExpression value -> {
					expression(value.left());
					expression(value.right());
				}
				case RuntimeConditionalExpression value -> {
					expression(value.condition());
					expression(value.thenExpression());
					expression(value.elseExpression());
				}
				case RuntimeListExpression value -> value.elements().forEach(this::expression);
				case RuntimeContextExpression value -> value.entries().forEach(entry -> expression(entry.expression()));
				case RuntimePathExpression value -> expression(value.source());
				case RuntimeDescendantExpression value -> expression(value.source());
				case RuntimeRangeExpression value -> {
					value.lower().ifPresent(this::expression);
					value.upper().ifPresent(this::expression);
				}
				case RuntimeFilterExpression value -> {
					expression(value.source());
					expression(value.filter());
				}
				case RuntimeBetweenExpression value -> {
					expression(value.value());
					expression(value.lower());
					expression(value.upper());
				}
				case RuntimeInExpression value -> {
					expression(value.value());
					tests(value.tests());
				}
				case RuntimeInstanceOfExpression value -> expression(value.expression());
				case RuntimeUnaryTestsExpression value -> tests(value.tests());
				case RuntimeForExpression value -> {
					value.iterations().forEach(iteration -> {
						expression(iteration.source());
						iteration.end().ifPresent(this::expression);
					});
					expression(value.result());
				}
				case RuntimeQuantifiedExpression value -> {
					value.bindings().forEach(binding -> expression(binding.source()));
					expression(value.satisfies());
				}
				case RuntimeFunctionDefinition value -> value.body().ifPresent(this::expression);
				case RuntimeRelationExpression value -> value.rows().forEach(row -> row.forEach(this::expression));
				case RuntimeValueReference ignored -> {
				}
				case RuntimeDecisionTableReference ignored -> {
				}
				case RuntimeLocalReference ignored -> {
				}
			}
		}

		private void constant(int ordinal, RuntimeConstant constant) {
			RuntimeCanonicalValue value = canonical(constant);
			PoolKey key = new PoolKey(constant.kind(), value, constant.type());
			int id = poolIds.computeIfAbsent(key, ignored -> {
				int next = pool.size();
				pool.add(new RuntimeConstantPoolEntry(next, constant.kind(), value, constant.type()));
				return next;
			});
			uses.add(new RuntimeConstantUse(ordinal, id));
		}

		private void bindBuiltin(int ordinal, String name) {
			RuntimeBuiltinOperation.find(name)
					.ifPresent(value -> builtins.add(new RuntimeBuiltinBinding(ordinal, value)));
		}
	}

	private static RuntimeCanonicalValue canonical(RuntimeConstant constant) {
		String value = constant.value();
		try {
			return switch (constant.kind()) {
				case NULL -> new RuntimeCanonicalValue.NullValue();
				case BOOLEAN -> new RuntimeCanonicalValue.BooleanValue(parseBoolean(value));
				case NUMBER -> new RuntimeCanonicalValue.NumberValue(new BigDecimal(value).stripTrailingZeros());
				case STRING -> new RuntimeCanonicalValue.StringValue(value);
				case DATE -> new RuntimeCanonicalValue.DateValue(LocalDate.parse(value));
				case TIME -> parseTime(value);
				case DATE_TIME -> parseDateTime(value);
				case DURATION -> new RuntimeCanonicalValue.DurationValue(parseDuration(value));
			};
		} catch (Exception exception) {
			return new RuntimeCanonicalValue.StringValue(constant.kind() + ":" + value);
		}
	}

	private static boolean parseBoolean(String value) {
		if (!value.equals("true") && !value.equals("false")) {
			throw new IllegalArgumentException("Invalid BOOLEAN runtime constant '" + value + "'.");
		}
		return Boolean.parseBoolean(value);
	}

	private static RuntimeCanonicalValue parseTime(String rawValue) {
		String value = rawValue.replace("24:00:00", "00:00:00").replace("24:00", "00:00");
		int namedZone = value.indexOf('@');
		if (namedZone >= 0) {
			String zone = value.substring(namedZone + 1);
			ZoneId.of(zone);
			return new RuntimeCanonicalValue.TimeValue(LocalTime.parse(value.substring(0, namedZone)),
					Optional.of(zone));
		}
		try {
			OffsetTime parsed = OffsetTime.parse(value);
			return new RuntimeCanonicalValue.TimeValue(parsed.toLocalTime(),
					Optional.of(parsed.getOffset().toString()));
		} catch (DateTimeParseException ignored) {
			return new RuntimeCanonicalValue.TimeValue(LocalTime.parse(value), Optional.empty());
		}
	}

	private static RuntimeCanonicalValue parseDateTime(String rawValue) {
		String value = rawValue;
		int tIdx = value.indexOf('T');
		if (tIdx > 0 && (value.contains("T24:00:00") || value.contains("T24:00"))) {
			String datePart = value.substring(0, tIdx);
			String rest = value.substring(tIdx + 1).replace("24:00:00", "00:00:00").replace("24:00", "00:00");
			LocalDate d = LocalDate.parse(datePart).plusDays(1);
			value = d.toString() + "T" + rest;
		}
		int namedZone = value.indexOf('@');
		if (namedZone >= 0) {
			String zone = value.substring(namedZone + 1);
			ZoneId.of(zone);
			return new RuntimeCanonicalValue.DateTimeValue(LocalDateTime.parse(value.substring(0, namedZone)),
					Optional.of(zone));
		}
		try {
			OffsetDateTime parsed = OffsetDateTime.parse(value);
			return new RuntimeCanonicalValue.DateTimeValue(parsed.toLocalDateTime(),
					Optional.of(parsed.getOffset().toString()));
		} catch (DateTimeParseException ignored) {
			return new RuntimeCanonicalValue.DateTimeValue(LocalDateTime.parse(value), Optional.empty());
		}
	}

	private static TemporalAmount parseDuration(String value) {
		return value.contains("T") ? Duration.parse(value) : Period.parse(value);
	}

	private record PoolKey(RuntimeConstantKind kind, RuntimeCanonicalValue value, RuntimeType type) {
	}
}

package io.finmsg.dmn.generator.sparksql;

import static org.assertj.core.api.Assertions.*;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.*;
import org.apache.spark.sql.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * These tests must execute Spark: a failed Spark setup is a failure, never a
 * skipped assertion.
 */
class SparkSqlHybridIntegrationTest {
	private static SparkSession spark;
	@org.junit.jupiter.api.io.TempDir
	java.nio.file.Path generatedDirectory;

	@BeforeAll
	static void start() {
		spark = SparkSession.builder().master("local[2]").appName("DMN hybrid bridge")
				.config("spark.ui.enabled", "false").config("spark.sql.shuffle.partitions", "2").getOrCreate();
	}
	@AfterAll
	static void stop() {
		if (spark != null)
			spark.stop();
	}

	@Test
	void nativeMembershipPreservesDurationPrecisionAndTypeIdentity() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var durationType = RuntimeType.scalar(RuntimeTypeKind.DURATION);
		for (String[] sample : List.of(new String[]{"PT0.000000001S", "PT0.000000001S", "true"},
				new String[]{"PT0.000000001S", "PT0.000000002S", "false"},
				new String[]{"-PT0.000000001S", "PT0.000000001S", "false"}, new String[]{"P1Y", "P12M", "true"},
				new String[]{"P12M", "PT12S", "false"},
				new String[]{"PT9223372036854775807S", "PT9223372036854775807S", "true"})) {
			var value = new RuntimeConstant(RuntimeConstantKind.DURATION, sample[0], durationType);
			var endpoint = new RuntimeConstant(RuntimeConstantKind.DURATION, sample[1], durationType);
			var list = new RuntimeListExpression(List.of(endpoint, SparkSqlHybridTest.string(sample[0])),
					RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY));
			var expression = new RuntimeInExpression(value,
					new RuntimeUnaryTests(false, false, List.of(new RuntimeExpressionUnaryTest(list))), bool);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0))
					.isEqualTo(Boolean.valueOf(sample[2]));
		}
		var lower = new RuntimeConstant(RuntimeConstantKind.DURATION, "PT0S", durationType);
		var upper = new RuntimeConstant(RuntimeConstantKind.DURATION, "PT0.000000002S", durationType);
		var range = new RuntimeRangeExpression(Optional.of(lower), Optional.of(upper), RuntimeRangeBoundary.CLOSED,
				RuntimeRangeBoundary.OPEN, RuntimeType.element(RuntimeTypeKind.RANGE, durationType));
		for (String[] sample : List.of(new String[]{"PT0.000000001S", "true"},
				new String[]{"PT0.000000002S", "false"})) {
			var value = new RuntimeConstant(RuntimeConstantKind.DURATION, sample[0], durationType);
			var expression = new RuntimeInExpression(value,
					new RuntimeUnaryTests(false, false, List.of(new RuntimeRangeUnaryTest(range))), bool);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0))
					.isEqualTo(Boolean.valueOf(sample[1]));
		}
	}

	@Test
	void heterogeneousMembershipUsesDynamicRowsWithoutCoercingTypes() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var nullValue = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		var list = new RuntimeListExpression(
				List.of(SparkSqlHybridTest.string("1"), SparkSqlHybridTest.number("2"), nullValue),
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY));
		var expression = new RuntimeInExpression(
				new RuntimeValueReference(0, RuntimeType.scalar(RuntimeTypeKind.STRING)),
				new RuntimeUnaryTests(false, false, List.of(new RuntimeExpressionUnaryTest(list))), bool);
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, RuntimeType.scalar(RuntimeTypeKind.STRING))),
				List.of(new RuntimeDecision(10, 1, bool, List.of(5), Optional.of(expression))), List.of(), List.of(10),
				2);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.udfs()).isEmpty();
		for (Object value : Arrays.asList("2", "1", null)) {
			Map<Integer, Object> input = new HashMap<>();
			input.put(0, value);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, input)), generated.inputSchema())
					.createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0))
					.isEqualTo(!"2".equals(value));
		}
		var sharedModel = new RuntimeModel(model.inputs(),
				List.of(model.decisions().getFirst(),
						new RuntimeDecision(20, 2, list.type(), List.of(), Optional.of(list))),
				List.of(), List.of(10, 20), 3);
		assertThat(SparkSqlHybridTest.generate(sharedModel).capabilities().get(20).nativeSql()).isFalse();
	}

	@Test
	void yearMonthDurationUsesWholeCalendarMonthsWithoutUdfs() {
		var resultType = RuntimeType.scalar(RuntimeTypeKind.YEARS_MONTHS_DURATION);
		for (String[] sample : List.of(new String[]{"2021-01-31", "2021-02-28"},
				new String[]{"2021-02-28", "2021-01-31"}, new String[]{"2020-01-31", "2020-02-29"},
				new String[]{"2021-01-30", "2021-03-31"}, new String[]{"2021-03-31", "2021-01-30"},
				new String[]{"2011-12-22", "2013-08-24"}, new String[]{"2013-08-24", "2011-12-22"},
				new String[]{"2017-12-31T13:00:00", "2017-12-31T12:00:00"},
				new String[]{"2017-12-31", "2018-01-31T12:00:00"}, new String[]{"2018-01-31T12:00:00", "2017-12-31"})) {
			var fromType = RuntimeType
					.scalar(sample[0].contains("T") ? RuntimeTypeKind.DATE_TIME : RuntimeTypeKind.DATE);
			var toType = RuntimeType.scalar(sample[1].contains("T") ? RuntimeTypeKind.DATE_TIME : RuntimeTypeKind.DATE);
			RuntimeExpression from = new RuntimeConstant(
					sample[0].contains("T") ? RuntimeConstantKind.DATE_TIME : RuntimeConstantKind.DATE, sample[0],
					fromType);
			RuntimeExpression to = new RuntimeConstant(
					sample[1].contains("T") ? RuntimeConstantKind.DATE_TIME : RuntimeConstantKind.DATE, sample[1],
					toType);
			var call = new RuntimeFunctionCall("years and months duration", List.of(from, to), resultType);
			var named = new RuntimeInvocationExpression(Optional.of("years and months duration"), Optional.empty(),
					List.of(new RuntimeNamedArgument("to", to), new RuntimeNamedArgument("from", from)), List.of(),
					resultType);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, resultType, List.of(), Optional.of(call)),
							new RuntimeDecision(20, 1, resultType, List.of(), Optional.of(named))),
					List.of(), List.of(10, 20), 2);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			var period = java.time.Period.between(java.time.LocalDate.parse(sample[0].substring(0, 10)),
					java.time.LocalDate.parse(sample[1].substring(0, 10)));
			var expected = java.time.Period.of(period.getYears(), period.getMonths(), 0);
			for (int slot : List.of(0, 1))
				assertThat(SparkSqlFeelValueCodec.fromSpark(
						spark.sql(generated.sqlFiles().get("Decision_" + slot + ".sql")).head().get(0), resultType))
						.isEqualTo(expected);
		}
		var dateType = RuntimeType.scalar(RuntimeTypeKind.DATE);
		var historical = new RuntimeConstant(RuntimeConstantKind.DATE, "1500-01-01", dateType);
		var current = new RuntimeConstant(RuntimeConstantKind.DATE, "2021-01-01", dateType);
		var unsupported = new RuntimeFunctionCall("years and months duration", List.of(historical, current),
				resultType);
		var model = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, resultType, List.of(), Optional.of(unsupported))), List.of(),
				List.of(10), 1);
		assertThat(SparkSqlHybridTest.generate(model).capabilities().get(10).nativeSql()).isFalse();
	}

	@Test
	void constantDurationResultsPreservePrecisionWithoutUdfs() {
		for (RuntimeTypeKind kind : List.of(RuntimeTypeKind.DURATION, RuntimeTypeKind.YEARS_MONTHS_DURATION,
				RuntimeTypeKind.DAYS_TIME_DURATION)) {
			var type = RuntimeType.scalar(kind);
			List<String> samples = kind == RuntimeTypeKind.YEARS_MONTHS_DURATION
					? List.of("P13M", "-P2Y3M", "P2147483647M")
					: List.of("PT0.000000001S", "-PT1000M0.999999999S", "PT9223372036854775807S");
			for (String sample : samples) {
				var constant = new RuntimeConstant(RuntimeConstantKind.DURATION, sample, type);
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, type, List.of(), Optional.of(constant))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				Object raw = spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0);
				assertThat(raw).isEqualTo(sample);
				assertThat(SparkSqlFeelValueCodec.fromSpark(raw, type))
						.isEqualTo(io.finmsg.dmn.runtime.DmnRuntime.parseDuration(sample));
			}
		}
		var type = RuntimeType.scalar(RuntimeTypeKind.DAYS_TIME_DURATION);
		assertThatThrownBy(() -> SparkSqlFeelValueCodec.fromSpark("P1Y", type))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SparkSqlFeelValueCodec.fromSpark("invalid", type))
				.isInstanceOf(IllegalArgumentException.class);
		var input = java.time.Duration.ofNanos(1);
		assertThat(SparkSqlFeelValueCodec.fromSpark(SparkSqlFeelValueCodec.encode(input), type)).isEqualTo(input);
		for (String invalid : List.of("invalid", "P1Y")) {
			var constant = new RuntimeConstant(RuntimeConstantKind.DURATION, invalid, type);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, type, List.of(), Optional.of(constant))), List.of(), List.of(10),
					1);
			assertThat(SparkSqlHybridTest.generate(model).capabilities().get(10).nativeSql()).isFalse();
		}
	}

	@Test
	void foldedNullResultsDoNotRequireLosslessTransport() {
		var constant = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		for (RuntimeTypeKind kind : List.of(RuntimeTypeKind.DATE, RuntimeTypeKind.DURATION,
				RuntimeTypeKind.YEARS_MONTHS_DURATION, RuntimeTypeKind.DAYS_TIME_DURATION, RuntimeTypeKind.CONTEXT)) {
			var type = RuntimeType.scalar(kind);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, type, List.of(), Optional.of(constant))), List.of(), List.of(10),
					1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0), type)).isNull();
		}
	}

	@Test
	void nativeTimeResultsPreserveLocalIdentityWithoutUdfs() {
		var timeType = RuntimeType.scalar(RuntimeTypeKind.TIME);
		var expression = new RuntimeFunctionCall("time", List.of(SparkSqlHybridTest.string("10:30:08")), timeType);
		var model = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, timeType, List.of(), Optional.of(expression))), List.of(),
				List.of(10), 1);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.udfs()).isEmpty();
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		for (String zone : List.of("UTC", "Europe/Zurich")) {
			spark.conf().set("spark.sql.session.timeZone", zone);
			Row row = spark.sql(generated.sqlFiles().get("Decision_0.sql")).head();
			assertThat(SparkSqlFeelValueCodec.fromSpark(row.get(0), timeType))
					.isEqualTo(java.time.LocalTime.parse("10:30:08"));
		}
		assertThat(SparkSqlFeelValueCodec
				.fromSpark(SparkSqlFeelValueCodec.encode(java.time.OffsetTime.parse("10:30:08+02:00")), timeType))
				.isEqualTo(java.time.OffsetTime.parse("10:30:08+02:00"));
		assertThatThrownBy(
				() -> SparkSqlFeelValueCodec.fromSpark(java.time.LocalDateTime.parse("2021-03-28T10:30:08"), timeType))
				.isInstanceOf(IllegalArgumentException.class);
		spark.conf().set("spark.sql.session.timeZone", "UTC");
	}

	@Test
	void nullTimeArgumentsDoNotEvaluateUnsupportedOffsets() {
		var timeType = RuntimeType.scalar(RuntimeTypeKind.TIME);
		var durationType = RuntimeType.scalar(RuntimeTypeKind.DAYS_TIME_DURATION);
		var offset = new RuntimeFunctionCall("duration", List.of(SparkSqlHybridTest.string("P0D")), durationType);
		var nullValue = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		var expression = new RuntimeFunctionCall("time",
				List.of(nullValue, SparkSqlHybridTest.number("11"), SparkSqlHybridTest.number("45"), offset), timeType);
		var model = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, timeType, List.of(), Optional.of(expression))), List.of(),
				List.of(10), 1);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.udfs()).isEmpty();
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)).isNull();
		var folded = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, timeType, List.of(), Optional.of(nullValue))), List.of(),
				List.of(10), 1);
		var foldedSql = SparkSqlHybridTest.generate(folded);
		assertThat(foldedSql.udfs()).isEmpty();
		assertThat(spark.sql(foldedSql.sqlFiles().get("Decision_0.sql")).head().get(0)).isNull();
	}

	@Test
	void evaluatesMixedListOnExecutors() {
		var model = SparkSqlHybridTest.mixedModel();
		var generated = SparkSqlHybridTest.generate(model);
		generated.registerUdfs(spark);
		spark.range(2).createOrReplaceTempView("hybrid_inputs");
		var rows = spark.sql(generated.sqlFiles().get("Decision_0.sql")).collectAsList();
		assertThat(rows).hasSize(2);
		for (Row row : rows)
			assertThat(SparkSqlFeelValueCodec.fromSpark(row.get(0)))
					.isEqualTo(Arrays.asList(BigDecimal.ONE, "1", true, null));
	}

	@Test
	void matchesStaticFeelPatternsAgainstDynamicInputWithoutUdfs() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		for (String[] sample : List.of(new String[]{"k", "i", "\u212A", "true"},
				new String[]{"[A-Z-[OI]]", "i", "O", "false"}, new String[]{"x[y-z]", "qi", "X[y-Z]", "true"},
				new String[]{"he ll o[ ]worl d", "x", "hello world", "true"},
				new String[]{"\\p{ I s B a s i c L a t i n }+", "x", "hello world", "true"},
				new String[]{"a [ ] b", "x", "ab", "false"}, new String[]{"(.)\\3", "", "h", "null"},
				new String[]{"k", "p", "k", "null"})) {
			var expression = new RuntimeFunctionCall("matches",
					List.of(new RuntimeValueReference(0, SparkSqlHybridTest.STRING),
							SparkSqlHybridTest.string(sample[0]), SparkSqlHybridTest.string(sample[1])),
					bool);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, SparkSqlHybridTest.STRING)),
					List.of(new RuntimeDecision(10, 1, bool, List.of(5), Optional.of(expression))), List.of(),
					List.of(10), 2);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, Map.of(0, sample[2]))),
					generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
			assertThat(actual).isEqualTo(sample[3].equals("null") ? null : Boolean.valueOf(sample[3]));
		}
	}

	@Test
	void invalidMatchesArgumentsDoNotSuppressSharedListFallbackChecks() {
		var listType = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY);
		var emptyList = new RuntimeListExpression(List.of(), listType);
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		for (List<RuntimeExpression> args : List.<List<RuntimeExpression>>of(
				List.of(SparkSqlHybridTest.string("input"), emptyList),
				List.of(SparkSqlHybridTest.string("input"), SparkSqlHybridTest.string("pattern"), emptyList),
				List.of(SparkSqlHybridTest.string("input"), SparkSqlHybridTest.string("pattern"),
						SparkSqlHybridTest.string(""), emptyList))) {
			var invalid = new RuntimeFunctionCall("matches", args, bool);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(invalid)),
							new RuntimeDecision(20, 1, listType, List.of(), Optional.of(emptyList))),
					List.of(), List.of(10, 20), 2);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.capabilities().get(20).nativeSql()).isFalse();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)).isNull();
		}
	}

	@Test
	void evaluatesLocalTemporalValuesWithoutSessionTimezoneConversion() {
		var timeType = RuntimeType.scalar(RuntimeTypeKind.TIME);
		var dateTimeType = RuntimeType.scalar(RuntimeTypeKind.DATE_TIME);
		var booleanType = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var literalTime = new RuntimeConstant(RuntimeConstantKind.TIME, "10:30:08", timeType);
		var constructedTime = new RuntimeFunctionCall("time", List.of(SparkSqlHybridTest.string("10:30:08")), timeType);
		var equality = new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, literalTime, constructedTime,
				booleanType);
		var dateTime = new RuntimeFunctionCall("date and time",
				List.of(SparkSqlHybridTest.string("2021-03-28T02:30:00")), dateTimeType);
		var model = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, booleanType, List.of(), Optional.of(equality)),
						new RuntimeDecision(20, 1, dateTimeType, List.of(), Optional.of(dateTime))),
				List.of(), List.of(10, 20), 2);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.udfs()).isEmpty();
		String originalTimezone = spark.conf().get("spark.sql.session.timeZone");
		try {
			for (String timezone : List.of("UTC", "Europe/Zurich")) {
				spark.conf().set("spark.sql.session.timeZone", timezone);
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
						.isEqualTo(true);
				assertThat(SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0)))
						.isEqualTo(java.time.LocalDateTime.of(2021, 3, 28, 2, 30));
			}
		} finally {
			spark.conf().set("spark.sql.session.timeZone", originalTimezone);
		}
		for (RuntimeExpression value : List.of(literalTime, dateTime)) {
			var invalidAbs = new RuntimeFunctionCall("abs", List.of(value), SparkSqlHybridTest.NUMBER);
			var invalidAbsModel = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.NUMBER, List.of(), Optional.of(invalidAbs))),
					List.of(), List.of(10), 1);
			var invalidAbsGenerated = SparkSqlHybridTest.generate(invalidAbsModel);
			assertThat(invalidAbsGenerated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(invalidAbsGenerated.sqlFiles().get("Decision_0.sql")).head().get(0)).isNull();
		}
		for (String value : List.of("2021-03-28T02:30:00Z", "2021-03-28T02:30:00.000000001", "1500-01-01T00:00:00")) {
			var unverified = new RuntimeFunctionCall("date and time", List.of(SparkSqlHybridTest.string(value)),
					dateTimeType);
			var fallbackModel = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, dateTimeType, List.of(), Optional.of(unverified))), List.of(),
					List.of(10), 1);
			assertThat(SparkSqlHybridTest.generate(fallbackModel).capabilities().get(10).nativeSql()).isFalse();
		}
		var midnight = new RuntimeConstant(RuntimeConstantKind.TIME, "00:00:00", timeType);
		var epochDateTime = new RuntimeConstant(RuntimeConstantKind.DATE_TIME, "1970-01-01T00:00:00", dateTimeType);
		var mixedEquality = new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, midnight, epochDateTime,
				booleanType);
		var mixedModel = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, booleanType, List.of(), Optional.of(mixedEquality))), List.of(),
				List.of(10), 1);
		var mixedGenerated = SparkSqlHybridTest.generate(mixedModel);
		assertThat(mixedGenerated.capabilities().get(10).nativeSql()).isFalse();
		mixedGenerated.registerUdfs(spark);
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		assertThat(SparkSqlFeelValueCodec
				.fromSpark(spark.sql(mixedGenerated.sqlFiles().get("Decision_0.sql")).head().get(0))).isNull();
	}

	@Test
	void evaluatesCapturedValuesPerRowAndPreservesPrecision() {
		var model = SparkSqlHybridTest.closureModel();
		var generated = SparkSqlHybridTest.generate(model);
		generated.registerUdfs(spark);
		var inputs = List.of(new BigDecimal("123456789012345678901234567890.123456789"),
				new BigDecimal("-0.000000000000000000000001"));
		var rows = inputs.stream().map(n -> SparkSqlFeelValueCodec.inputRow(model, Map.of(0, n))).toList();
		spark.createDataFrame(rows, generated.inputSchema()).repartition(2).createOrReplaceTempView("hybrid_inputs");
		var results = spark.sql(generated.sqlFiles().get("AllDecisions.sql")).collectAsList().stream()
				.map(row -> SparkSqlFeelValueCodec.fromSpark(row.get(0))).toList();
		assertThat(results)
				.containsExactlyInAnyOrderElementsOf(inputs.stream().map(n -> n.add(new BigDecimal("2"))).toList());
	}

	@Test
	void evaluatesNumericArithmeticAndDecisionTableNatively() {
		var number = SparkSqlHybridTest.NUMBER;
		var input = new RuntimeInput(5, 0, number);
		var arithmetic = new RuntimeDecision(10, 1, number, List.of(5),
				Optional.of(new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, new RuntimeValueReference(0, number),
						SparkSqlHybridTest.number("2"), number)));
		var arithmeticModel = new RuntimeModel(List.of(input), List.of(arithmetic), List.of(), List.of(10), 2);
		var arithmeticGenerated = SparkSqlHybridTest.generate(arithmeticModel);
		assertThat(arithmeticGenerated.udfs()).isEmpty();
		assertThat(arithmeticGenerated.capabilities().get(10).nativeSql()).isTrue();
		spark.createDataFrame(
				List.of(SparkSqlFeelValueCodec.inputRow(arithmeticModel, Map.of(0, new BigDecimal("40")))),
				arithmeticGenerated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
		Object arithmeticResult = spark.sql(arithmeticGenerated.sqlFiles().get("Decision_1.sql")).head().get(0);
		assertThat(SparkSqlFeelValueCodec.fromSpark(arithmeticResult)).isEqualTo(new BigDecimal("42"));

		var tableModel = SparkSqlHybridTest.numericTableModel();
		var tableGenerated = SparkSqlHybridTest.generate(tableModel);
		assertThat(tableGenerated.udfs()).isEmpty();
		assertThat(tableGenerated.capabilities().get(10).nativeSql()).isTrue();
		spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(tableModel, Map.of(0, new BigDecimal("40")))),
				tableGenerated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
		Object tableResult = spark.sql(tableGenerated.sqlFiles().get("Decision_1.sql")).head().get(0);
		assertThat(SparkSqlFeelValueCodec.fromSpark(tableResult)).isEqualTo(new BigDecimal("42"));
	}

	@Test
	void addsSelectedContextResultsWithUnknownBkmReturnTypes() {
		for (boolean strings : List.of(true, false)) {
			var any = SparkSqlHybridTest.ANY;
			var resultType = strings ? SparkSqlHybridTest.STRING : SparkSqlHybridTest.NUMBER;
			RuntimeExpression value = strings ? SparkSqlHybridTest.string("A") : SparkSqlHybridTest.number("21");
			var context = new RuntimeContextExpression(
					List.of(new RuntimeContextEntry("mapping", 0, value),
							new RuntimeContextEntry("__result__", 1, new RuntimeLocalReference(0, any))),
					RuntimeType.context(List.of(any, any)));
			var selected = new RuntimePathExpression(context, "__result__", any);
			var functionType = RuntimeType.function(List.of(), any);
			var function = new RuntimeFunctionDefinition(List.of(), Optional.of(selected), false, 2, functionType);
			var bkm = new RuntimeBkm(20, 0, functionType, List.of(), RuntimeFunctionKind.FEEL, Optional.of(function));
			var invocation = new RuntimeInvocationExpression(Optional.empty(),
					Optional.of(new RuntimeValueReference(0, functionType)), List.of(), List.of(), any);
			var sum = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, invocation, invocation, any);
			var decision = new RuntimeDecision(10, 1, resultType, List.of(20), Optional.of(sum));
			var model = new RuntimeModel(List.of(), List.of(decision), List.of(bkm), List.of(20, 10), 2);
			String sql = SparkSqlExpressionEmitter.emitWithBkms(sum, Map.of(0, bkm), slot -> "encode");
			Object actual = SparkSqlFeelValueCodec.fromSpark(spark.sql("SELECT " + sql).head().get(0));
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10);
			assertThat(actual).isEqualTo(expected).isEqualTo(strings ? "AA" : new BigDecimal("42"));
		}
	}

	@Test
	void addsUnknownTypedStringLengthNumerically() {
		var any = SparkSqlHybridTest.ANY;
		var length = new RuntimeFunctionCall("string length", List.of(SparkSqlHybridTest.string("abcd")), any);
		var sum = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, length,
				new RuntimeConstant(RuntimeConstantKind.NUMBER, "38", any), any);
		String sql = SparkSqlExpressionEmitter.emit(sum, slot -> null);
		assertThat(SparkSqlFeelValueCodec.fromSpark(spark.sql("SELECT " + sql).head().get(0)))
				.isEqualTo(new BigDecimal("42"));
	}

	@Test
	void stringConversionPreservesFeelNumbersAndStringInputs() {
		var expression = new RuntimeFunctionCall("string",
				List.of(new RuntimeValueReference(0, SparkSqlHybridTest.ANY)), SparkSqlHybridTest.STRING);
		String sql = SparkSqlExpressionEmitter.emit(expression, slot -> "value");
		var cases = new LinkedHashMap<String, Object>();
		cases.put("cast(65 as double)", new BigDecimal("65"));
		cases.put("cast(-12 as double)", new BigDecimal("-12"));
		cases.put("cast(-0.0 as double)", BigDecimal.ZERO);
		cases.put("cast(1.25 as double)", new BigDecimal("1.25"));
		cases.put("cast(1.2300 as decimal(10,4))", new BigDecimal("1.2300"));
		cases.put("'1.0'", "1.0");
		cases.put("true", true);
		for (var test : cases.entrySet()) {
			Object actual = spark.sql("SELECT " + sql + " FROM (SELECT " + test.getKey() + " AS value)").head().get(0);
			assertThat(actual).isEqualTo(io.finmsg.dmn.runtime.DmnRuntime.formatFeelString(test.getValue()));
		}
		assertThat(spark.sql("SELECT " + sql + " FROM (SELECT cast(NULL as double) AS value)").head().get(0)).isNull();
	}

	@Test
	void moduloPreservesDecimalValuesAndRejectsInvalidNamedArguments() {
		for (String dividend : List.of("10.1", "-10.1")) {
			for (String divisor : List.of("4.5", "-4.5")) {
				var expression = new RuntimeFunctionCall("modulo",
						List.of(SparkSqlHybridTest.number(dividend), SparkSqlHybridTest.number(divisor)),
						SparkSqlHybridTest.NUMBER);
				String sql = SparkSqlExpressionEmitter.emit(expression, slot -> null);
				var decision = new RuntimeDecision(10, 0, SparkSqlHybridTest.NUMBER, List.of(),
						Optional.of(expression));
				var model = new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(10), 1);
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10);
				assertThat((BigDecimal) SparkSqlFeelValueCodec.fromSpark(spark.sql("SELECT " + sql).head().get(0)))
						.isEqualByComparingTo((BigDecimal) expected);
			}
		}
		var invalid = new RuntimeInvocationExpression(Optional.of("modulo"), Optional.empty(),
				List.of(new RuntimeNamedArgument("dividend", SparkSqlHybridTest.number("10")),
						new RuntimeNamedArgument("foo", SparkSqlHybridTest.number("4"))),
				List.of(), SparkSqlHybridTest.NUMBER);
		assertThat(spark.sql("SELECT " + SparkSqlExpressionEmitter.emit(invalid, slot -> null)).head().get(0)).isNull();
		var reordered = new RuntimeInvocationExpression(Optional.of("modulo"), Optional.empty(),
				List.of(new RuntimeNamedArgument("divisor", SparkSqlHybridTest.number("4")),
						new RuntimeNamedArgument("dividend", SparkSqlHybridTest.number("10"))),
				List.of(), SparkSqlHybridTest.NUMBER);
		assertThat((BigDecimal) SparkSqlFeelValueCodec.fromSpark(
				spark.sql("SELECT " + SparkSqlExpressionEmitter.emit(reordered, slot -> null)).head().get(0)))
				.isEqualByComparingTo("2");
	}

	@Test
	void roundsNativeDecimalsWithFeelTieSemantics() {
		for (String name : List.of("decimal", "round up", "round down", "round half up", "round half down",
				"round half even")) {
			for (String value : List.of("2.5", "3.5", "-2.5", "-3.5", "1.126", "-1.126")) {
				var scale = SparkSqlHybridTest.number(value.contains("126") ? "2" : "0");
				var expression = new RuntimeFunctionCall(name, List.of(SparkSqlHybridTest.number(value), scale),
						SparkSqlHybridTest.NUMBER);
				var decision = new RuntimeDecision(10, 0, SparkSqlHybridTest.NUMBER, List.of(),
						Optional.of(expression));
				var model = new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(10), 1);
				var expected = (BigDecimal) new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
						.decisionValue(10);
				String sql = SparkSqlExpressionEmitter.emit(expression, slot -> null);
				assertThat((BigDecimal) SparkSqlFeelValueCodec.fromSpark(spark.sql("SELECT " + sql).head().get(0)))
						.as("%s(%s)", name, value).isEqualByComparingTo(expected);
			}
		}
		var invalid = new RuntimeFunctionCall("decimal",
				List.of(SparkSqlHybridTest.number("2.5"), SparkSqlHybridTest.string("bad scale")),
				SparkSqlHybridTest.NUMBER);
		assertThat(spark.sql("SELECT " + SparkSqlExpressionEmitter.emit(invalid, slot -> null)).head().get(0)).isNull();
	}

	@Test
	void extremeRoundingScalesRetainConformantFallback() {
		for (String scale : List.of("6176", "6177", "-6112")) {
			var expression = new RuntimeFunctionCall("round up",
					List.of(SparkSqlHybridTest.number("5.5"), SparkSqlHybridTest.number(scale)),
					SparkSqlHybridTest.NUMBER);
			var decision = new RuntimeDecision(10, 0, SparkSqlHybridTest.NUMBER, List.of(), Optional.of(expression));
			var model = new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isFalse();
			assertThat(generated.capabilities().get(10).reasons())
					.anyMatch(reason -> reason.contains("Rounding scale"));
			generated.registerUdfs(spark);
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0));
			if (scale.equals("6176"))
				assertThat((BigDecimal) actual).isEqualByComparingTo("5.5");
			else
				assertThat(actual).isNull();
		}
	}

	@Test
	void preservesTemporalAndContextValuesThroughSpark() {
		var type = SparkSqlHybridTest.ANY;
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, type)),
				List.of(new RuntimeDecision(10, 1, type, List.of(5), Optional.of(new RuntimeValueReference(0, type)))),
				List.of(), List.of(10), 2);
		var generated = SparkSqlHybridTest.generate(model);
		generated.registerUdfs(spark);
		var value = Map.of("time", java.time.OffsetTime.parse("12:00:00.123456789+02:00"), "duration",
				java.time.Period.ofMonths(15), "items", Arrays.asList(BigDecimal.ONE, "1", null));
		spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, Map.of(0, value))),
				generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
		Object result = spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0);
		assertThat(SparkSqlFeelValueCodec.fromSpark(result)).isEqualTo(value);
	}

	@Test
	void generatedRunnerCompilesAndRegistersItsOwnUdfs() throws Exception {
		RuntimeModel model = SparkSqlHybridTest.closureModel();
		var options = new DmnSparkSqlGeneratorOptions("generated", "HybridRunner", "runner_inputs", true, true,
				Map.of());
		var generated = new DmnSparkSqlGenerator().generate(SparkSqlHybridTest.optimized(model), options);
		var source = generatedDirectory.resolve("generated/HybridRunner.java");
		java.nio.file.Files.createDirectories(source.getParent());
		java.nio.file.Files.writeString(source, generated.javaSources().get("generated.HybridRunner"));
		int exit = javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath",
				System.getProperty("java.class.path"), "-d", generatedDirectory.toString(), source.toString());
		assertThat(exit).isZero();
		try (var loader = new java.net.URLClassLoader(new java.net.URL[]{generatedDirectory.toUri().toURL()},
				getClass().getClassLoader())) {
			var runner = loader.loadClass("generated.HybridRunner");
			var frame = spark.createDataFrame(
					List.of(SparkSqlFeelValueCodec.inputRow(model, Map.of(0, new BigDecimal("40")))),
					generated.inputSchema());
			@SuppressWarnings("unchecked")
			Dataset<Row> results = (Dataset<Row>) runner
					.getMethod("evaluate", SparkSession.class, Dataset.class, String.class)
					.invoke(null, spark, frame, "Decision_2");
			assertThat(SparkSqlFeelValueCodec.fromSpark(results.head().get(0))).isEqualTo(new BigDecimal("42"));
		}
	}
}

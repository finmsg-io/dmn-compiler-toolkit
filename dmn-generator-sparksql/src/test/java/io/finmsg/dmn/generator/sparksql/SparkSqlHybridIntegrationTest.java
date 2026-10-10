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
					.isEqualTo(value == null ? null : !"2".equals(value));
		}
		var sharedModel = new RuntimeModel(model.inputs(),
				List.of(model.decisions().getFirst(),
						new RuntimeDecision(20, 2, list.type(), List.of(), Optional.of(list))),
				List.of(), List.of(10, 20), 3);
		assertThat(SparkSqlHybridTest.generate(sharedModel).capabilities().get(20).nativeSql()).isFalse();
	}

	@Test
	void literalMembershipRetainsArbitraryDecimalPrecisionAndNullSemantics() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		for (String[] sample : List.of(
				new String[]{"99999999999999999999999999999999999999", "0.00000000000000000000000000000000000001"},
				new String[]{"0.10000000000000000000000000000000000001", "0.1"},
				new String[]{"123456789012345678901234567890123456789", "123456789012345678901234567890123456789"},
				new String[]{"1E-50", "1E-50"})) {
			var candidate = SparkSqlHybridTest.number(sample[0]);
			var endpoint = SparkSqlHybridTest.number(sample[1]);
			for (RuntimeUnaryTest test : List.of(new RuntimeExpressionUnaryTest(endpoint),
					new RuntimeComparisonUnaryTest(RuntimeUnaryTestOperator.LESS, endpoint))) {
				var expression = new RuntimeInExpression(candidate, new RuntimeUnaryTests(false, false, List.of(test)),
						bool);
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0))
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
		var nullValue = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		for (RuntimeExpression candidate : List.of(nullValue, SparkSqlHybridTest.number("5"))) {
			for (RuntimeExpression rhs : List.of(nullValue, new RuntimeListExpression(List.of(nullValue),
					RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY)))) {
				var expression = new RuntimeInExpression(candidate,
						new RuntimeUnaryTests(false, false, List.of(new RuntimeExpressionUnaryTest(rhs))), bool);
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0))
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
	}

	@Test
	void compiledYearMonthArithmeticExecutesNatively() {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="months" name="months" namespace="urn:months">
				  <decision id="result" name="result"><variable name="result"/>
				    <literalExpression><text>@"P1Y" + @"P2M"</text></literalExpression>
				  </decision>
				</definitions>
				""";
		var source = new io.finmsg.dmn.compiler.DmnSource(io.finmsg.dmn.compiler.DmnSourceId.of("memory:months"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var compilation = new io.finmsg.dmn.compiler.DmnCompiler().compile(source,
				new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(List.of()));
		assertThat(compilation.isSuccess()).isTrue();
		var optimized = compilation.optimizedRuntimeModel().orElseThrow();
		var generated = SparkSqlHybridTest.generate(optimized.model());
		assertThat(generated.udfs()).as("IR %s; capabilities %s", optimized.model(), generated.capabilities())
				.isEmpty();
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		var decision = optimized.model().decisions().getFirst();
		assertThat(SparkSqlFeelValueCodec.fromSpark(
				spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0),
				decision.type())).isEqualTo(java.time.Period.of(1, 2, 0));
	}

	@Test
	void yearMonthArithmeticPreservesScalingAndWholeMonthTruncation() {
		var duration = RuntimeType.scalar(RuntimeTypeKind.DURATION);
		var months = new RuntimeConstant(RuntimeConstantKind.DURATION, "P1Y11M", duration);
		var other = new RuntimeConstant(RuntimeConstantKind.DURATION, "-P2M", duration);
		var factor = SparkSqlHybridTest.number("-2.5");
		for (RuntimeBinaryExpression expression : List.of(
				new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, months, other, duration),
				new RuntimeBinaryExpression(RuntimeBinaryOperator.SUBTRACT, months, other, duration),
				new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, months, factor, duration),
				new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, factor, months, duration),
				new RuntimeBinaryExpression(RuntimeBinaryOperator.DIVIDE, months, factor, duration),
				new RuntimeBinaryExpression(RuntimeBinaryOperator.DIVIDE, months, SparkSqlHybridTest.number("0"),
						duration))) {
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, duration, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0), duration))
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
		}
		var overflow = new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, months,
				SparkSqlHybridTest.number("1E30"), duration);
		assertThat(SparkSqlExpressionEmitter.nativeYearMonthArithmeticSql(overflow)).isNull();
	}

	@Test
	void durationInstanceOfKeepsSubtypeIdentityWithoutLosslessTransport() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		for (String value : List.of("P1Y", "P0M", "PT0S", "PT0.000000001S", "-PT1S")) {
			var literal = new RuntimeConstant(RuntimeConstantKind.DURATION, value,
					RuntimeType.scalar(RuntimeTypeKind.DURATION));
			for (RuntimeTypeKind kind : List.of(RuntimeTypeKind.ANY, RuntimeTypeKind.DURATION,
					RuntimeTypeKind.YEARS_MONTHS_DURATION, RuntimeTypeKind.DAYS_TIME_DURATION, RuntimeTypeKind.NUMBER,
					RuntimeTypeKind.STRING, RuntimeTypeKind.NULL)) {
				var expression = new RuntimeInstanceOfExpression(literal, RuntimeType.scalar(kind), bool);
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0))
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
	}

	@Test
	void staticIsPreservesTemporalRepresentationIdentity() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		for (String name : List.of("value1", "value2")) {
			var invocation = new RuntimeInvocationExpression(Optional.of("is"), Optional.empty(),
					List.of(new RuntimeNamedArgument(name, new RuntimeConstant(RuntimeConstantKind.TIME,
							"12:00:00+01:00", RuntimeType.scalar(RuntimeTypeKind.TIME)))),
					List.of(), bool);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(invocation))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)).isEqualTo(false);
		}
		for (String[] sample : List.of(new String[]{"TIME", "12:00:00+01:00", "11:00:00Z", "false"},
				new String[]{"TIME", "12:00:00+01:00", "12:00:00+01:00", "true"},
				new String[]{"TIME", "12:00:00.000000001", "12:00:00.000000002", "false"},
				new String[]{"DATE_TIME", "2021-01-01T12:00:00+01:00", "2021-01-01T11:00:00Z", "false"},
				new String[]{"DURATION", "P1Y", "P12M", "true"}, new String[]{"DURATION", "P0M", "PT0S", "false"})) {
			var kind = RuntimeConstantKind.valueOf(sample[0]);
			var type = RuntimeType.scalar(RuntimeTypeKind.valueOf(sample[0]));
			var expression = new RuntimeFunctionCall("is",
					List.of(new RuntimeConstant(kind, sample[1], type), new RuntimeConstant(kind, sample[2], type)),
					bool);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0))
					.as("is(%s, %s), kind %s", sample[1], sample[2], kind).isEqualTo(Boolean.valueOf(sample[3]));
		}
	}

	@Test
	void singleNumericForLoopExecutesOnDynamicRows() {
		var listType = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER);
		var body = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD,
				new RuntimeLocalReference(0, SparkSqlHybridTest.NUMBER), SparkSqlHybridTest.number("1"),
				SparkSqlHybridTest.NUMBER);
		var loop = new RuntimeForExpression(
				List.of(new RuntimeIteration(0, new RuntimeValueReference(0, listType), Optional.empty())), body, -1,
				listType);
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, listType)),
				List.of(new RuntimeDecision(10, 1, listType, List.of(5), Optional.of(loop), Optional.empty(), 1)),
				List.of(), List.of(10), 2);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.udfs()).isEmpty();
		for (Object values : Arrays.asList(List.of(new BigDecimal("1"), new BigDecimal("2")), List.of(), null)) {
			Map<Integer, Object> inputs = new HashMap<>();
			inputs.put(0, values);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs)), generated.inputSchema())
					.createOrReplaceTempView("hybrid_inputs");
			var actual = SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
			assertThat(actual).isEqualTo(values == null
					? null
					: ((List<?>) values).isEmpty() ? List.of() : List.of(new BigDecimal("2"), new BigDecimal("3")));
		}
		for (String[] range : List.of(new String[]{"2", "4"}, new String[]{"4", "2"}, new String[]{"1", "1"})) {
			var ranged = new RuntimeForExpression(
					List.of(new RuntimeIteration(0, SparkSqlHybridTest.number(range[0]),
							Optional.of(SparkSqlHybridTest.number(range[1])))),
					new RuntimeLocalReference(0, SparkSqlHybridTest.NUMBER), -1, listType);
			var rangedModel = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, listType, List.of(), Optional.of(ranged), Optional.empty(), 1)),
					List.of(), List.of(10), 1);
			var rangeSql = SparkSqlHybridTest.generate(rangedModel);
			assertThat(rangeSql.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(rangeSql.sqlFiles().get("Decision_0.sql")).head().get(0))).isEqualTo(
							new io.finmsg.dmn.runtime.DmnRuntime().evaluate(rangedModel, Map.of()).decisionValue(10));
		}
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
		assertThat(SparkSqlHybridTest.generate(model).capabilities().get(10).nativeSql()).isTrue();
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
	void constantTemporalResultsPreserveFractionsZonesAndExtremeYears() {
		for (var kind : List.of(RuntimeTypeKind.TIME, RuntimeTypeKind.DATE_TIME)) {
			var type = RuntimeType.scalar(kind);
			var constantKind = kind == RuntimeTypeKind.TIME ? RuntimeConstantKind.TIME : RuntimeConstantKind.DATE_TIME;
			var samples = kind == RuntimeTypeKind.TIME
					? List.of("10:30:08.000000001", "10:30:08.999999999+02:00", "10:30:08@Europe/Zurich")
					: List.of("2021-03-28T02:30:08.000000001", "2021-03-28T02:30:08.999999999+02:00",
							"2021-03-28T02:30:08@Europe/Zurich", "0001-01-01T00:00:00", "999999999-12-31T23:59:59");
			for (String sample : samples) {
				var constant = new RuntimeConstant(constantKind, sample, type);
				var decision = new RuntimeDecision(10, 0, type, List.of(), Optional.of(constant));
				var model = new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				Object expected = kind == RuntimeTypeKind.TIME
						? io.finmsg.dmn.runtime.DmnRuntime.parseTime(sample)
						: io.finmsg.dmn.runtime.DmnRuntime.parseDateTime(sample);
				assertThat(expected).isNotNull();
				for (String zone : List.of("UTC", "Europe/Zurich")) {
					spark.conf().set("spark.sql.session.timeZone", zone);
					spark.range(1).createOrReplaceTempView("hybrid_inputs");
					Object raw = spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0);
					assertThat(raw).isEqualTo(sample);
					assertThat(SparkSqlFeelValueCodec.fromSpark(raw, type)).isEqualTo(expected);
				}
			}
			assertThatThrownBy(() -> SparkSqlFeelValueCodec.fromSpark("invalid", type))
					.isInstanceOf(IllegalArgumentException.class);
			for (String invalid : List.of("invalid", "+999999999-12-31T23:59:59")) {
				var constant = new RuntimeConstant(constantKind, invalid, type);
				var decision = new RuntimeDecision(10, 0, type, List.of(), Optional.of(constant));
				assertThat(SparkSqlHybridTest
						.generate(new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(10), 1))
						.capabilities().get(10).nativeSql()).isFalse();
			}
		}
		spark.conf().set("spark.sql.session.timeZone", "UTC");
	}

	@Test
	void constantTemporalResultPromotionDoesNotEnableTemporalConsumers() {
		var type = RuntimeType.scalar(RuntimeTypeKind.TIME);
		var constant = new RuntimeConstant(RuntimeConstantKind.TIME, "10:30:08.000000001", type);
		var result = new RuntimeDecision(10, 0, type, List.of(), Optional.of(constant));
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var comparison = new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, new RuntimeValueReference(0, type),
				constant, bool);
		var consumer = new RuntimeDecision(20, 1, bool, List.of(10), Optional.of(comparison));
		var model = new RuntimeModel(List.of(), List.of(result, consumer), List.of(), List.of(10, 20), 2);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		assertThat(generated.capabilities().get(20).nativeSql()).isFalse();
		generated.registerUdfs(spark);
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		assertThat(
				SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0)))
				.isEqualTo(true);
	}

	@Test
	void invalidTemporalConstructorListsEmitNullWithoutEvaluatingUnusedValues() {
		var listType = RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType.scalar(RuntimeTypeKind.ANY));
		var list = new RuntimeListExpression(
				List.of(SparkSqlHybridTest.string("not a time"), new RuntimeConstant(RuntimeConstantKind.DURATION,
						"PT0.000000001S", RuntimeType.scalar(RuntimeTypeKind.DURATION))),
				listType);
		for (String function : List.of("time", "date and time")) {
			var type = RuntimeType.scalar(function.equals("time") ? RuntimeTypeKind.TIME : RuntimeTypeKind.DATE_TIME);
			for (List<RuntimeExpression> args : List.of(List.<RuntimeExpression>of(list),
					List.<RuntimeExpression>of())) {
				var call = new RuntimeFunctionCall(function, args, type);
				var result = new RuntimeDecision(10, 0, type, List.of(), Optional.of(call));
				var shared = new RuntimeDecision(20, 1, listType, List.of(), Optional.of(list));
				var generated = SparkSqlHybridTest
						.generate(new RuntimeModel(List.of(), List.of(result, shared), List.of(), List.of(10, 20), 2));
				assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
				assertThat(generated.capabilities().get(20).nativeSql()).isFalse();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)).isNull();
			}
		}
	}

	@Test
	void nativeContextPutRebuildsHeterogeneousDynamicStructs() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var sourceType = RuntimeType.contextFields(
				List.of(new RuntimeField(0, "name", SparkSqlHybridTest.STRING), new RuntimeField(1, "enabled", bool)));
		var resultType = RuntimeType.scalar(RuntimeTypeKind.CONTEXT);
		for (String key : List.of("name", "added", "a`b", "")) {
			var call = new RuntimeFunctionCall("context put", List.of(new RuntimeValueReference(0, sourceType),
					SparkSqlHybridTest.string(key), new RuntimeValueReference(1, bool)), resultType);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, sourceType), new RuntimeInput(6, 1, bool)),
					List.of(new RuntimeDecision(10, 2, resultType, List.of(5, 6), Optional.of(call))), List.of(),
					List.of(10), 3);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			assertThat(generated.sqlFiles().get("Decision_2.sql")).doesNotContain("map_concat");
			for (Object source : Arrays.asList(Map.of("name", "alpha", "enabled", false), null)) {
				var inputs = new HashMap<Integer, Object>();
				inputs.put(0, source);
				inputs.put(1, true);
				spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs)), generated.inputSchema())
						.createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_2.sql")).head().get(0));
				assertThat(actual)
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10));
			}
		}
	}

	@Test
	void nativeContextPutKeepsDynamicKeysAndCaseCollisionsOnFallback() {
		var contextType = RuntimeType.contextFields(List.of(new RuntimeField(0, "a", SparkSqlHybridTest.STRING)));
		var resultType = RuntimeType.scalar(RuntimeTypeKind.CONTEXT);
		var dynamic = new RuntimeFunctionCall("context put",
				List.of(new RuntimeValueReference(0, contextType),
						new RuntimeValueReference(1, SparkSqlHybridTest.STRING), SparkSqlHybridTest.string("new")),
				resultType);
		var collision = new RuntimeFunctionCall("context put", List.of(new RuntimeValueReference(0, contextType),
				SparkSqlHybridTest.string("A"), SparkSqlHybridTest.string("new")), resultType);
		var constrained = new RuntimeFunctionCall("context put", List.of(new RuntimeValueReference(0, contextType),
				SparkSqlHybridTest.string("added"), SparkSqlHybridTest.string("new")), resultType);
		for (RuntimeExpression expression : List.of(dynamic, collision, constrained)) {
			var model = new RuntimeModel(
					List.of(new RuntimeInput(5, 0, contextType), new RuntimeInput(6, 1, SparkSqlHybridTest.STRING)),
					List.of(new RuntimeDecision(10, 2, expression == constrained ? contextType : resultType,
							List.of(5, 6), Optional.of(expression))),
					List.of(), List.of(10), 3);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isFalse();
			generated.registerUdfs(spark);
			var inputs = Map.<Integer, Object>of(0, Map.of("a", "old"), 1, "dynamic");
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs)), generated.inputSchema())
					.createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_2.sql")).head().get(0)))
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10));
		}
	}

	@Test
	void nativeContextPutStaticPathsPreserveMissingAndNullNestedFields() {
		var contextType = RuntimeType.scalar(RuntimeTypeKind.CONTEXT);
		var absent = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		var nested = new RuntimeContextExpression(
				List.of(new RuntimeContextEntry("a", -1, SparkSqlHybridTest.number("1"))), RuntimeType
						.contextFields(List.of(new RuntimeField(0, "a", RuntimeType.scalar(RuntimeTypeKind.NUMBER)))));
		for (RuntimeExpression child : List.of(nested, absent, SparkSqlHybridTest.number("1"))) {
			var source = new RuntimeContextExpression(List.of(new RuntimeContextEntry("y", -1, child)),
					RuntimeType.contextFields(List.of(new RuntimeField(0, "y", child.type()))));
			for (String leaf : List.of("a", "missing")) {
				var keys = new RuntimeListExpression(
						List.of(SparkSqlHybridTest.string("y"), SparkSqlHybridTest.string(leaf)),
						RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.STRING));
				var expression = new RuntimeFunctionCall("context put",
						List.of(source, keys, SparkSqlHybridTest.string("new")), contextType);
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, contextType, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
		var source = new RuntimeContextExpression(List.of(), contextType);
		for (var elements : List.of(List.<RuntimeExpression>of(), List.<RuntimeExpression>of(absent),
				List.<RuntimeExpression>of(SparkSqlHybridTest.string("y"), absent))) {
			var keys = new RuntimeListExpression(elements,
					RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType.scalar(RuntimeTypeKind.ANY)));
			var call = new RuntimeFunctionCall("context put", List.of(source, keys, SparkSqlHybridTest.number("1")),
					contextType);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, contextType, List.of(), Optional.of(call))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)).isNull();
		}
	}

	@Test
	void nativeContextPathUpdateDoesNotShadowOuterReplacementBindings() {
		var number = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
		var nestedType = RuntimeType.contextFields(List.of(new RuntimeField(0, "a", number)));
		var nested = new RuntimeContextExpression(
				List.of(new RuntimeContextEntry("a", -1, SparkSqlHybridTest.number("1"))), nestedType);
		var source = new RuntimeContextExpression(List.of(new RuntimeContextEntry("y", -1, nested)),
				RuntimeType.contextFields(List.of(new RuntimeField(0, "y", nestedType))));
		var keys = new RuntimeListExpression(List.of(SparkSqlHybridTest.string("y"), SparkSqlHybridTest.string("b")),
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.STRING));
		var updatedNested = RuntimeType.contextFields(
				List.of(new RuntimeField(0, "a", number), new RuntimeField(1, "b", SparkSqlHybridTest.STRING)));
		var resultType = RuntimeType.contextFields(List.of(new RuntimeField(0, "y", updatedNested)));
		var call = new RuntimeFunctionCall("context put",
				List.of(source, keys, new RuntimeLocalReference(0, SparkSqlHybridTest.STRING)), resultType);
		var outer = new RuntimeContextExpression(
				List.of(new RuntimeContextEntry("replacement", 0, SparkSqlHybridTest.string("outer")),
						new RuntimeContextEntry("", -1, call)),
				resultType);
		var decision = new RuntimeDecision(10, 0, resultType, List.of(), Optional.of(outer), Optional.empty(), 1);
		var model = new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(10), 1);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.udfs()).isEmpty();
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		assertThat(
				SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
				.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
	}

	@Test
	void nativeContextPutValidatesNullArityAndNamedArguments() {
		var contextType = RuntimeType.scalar(RuntimeTypeKind.CONTEXT);
		var empty = new RuntimeContextExpression(List.of(), contextType);
		var absent = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		var name = SparkSqlHybridTest.string("a");
		var value = SparkSqlHybridTest.string("value");
		var expressions = List.<RuntimeExpression>of(
				new RuntimeFunctionCall("context put", List.of(empty, name, value), contextType),
				new RuntimeFunctionCall("context put", List.of(empty, name, absent), contextType),
				new RuntimeFunctionCall("context put", List.of(empty, absent, value), contextType),
				new RuntimeFunctionCall("context put", List.of(absent, name, value), contextType),
				new RuntimeFunctionCall("context put", List.of(empty, name), contextType),
				new RuntimeFunctionCall("context put", List.of(empty, name, value, value), contextType),
				new RuntimeInvocationExpression(Optional.of("context put"), Optional.empty(),
						List.of(new RuntimeNamedArgument("value", value), new RuntimeNamedArgument("key", name),
								new RuntimeNamedArgument("context", empty)),
						List.of(), contextType),
				new RuntimeInvocationExpression(
						Optional.of("context put"), Optional.empty(), List.of(new RuntimeNamedArgument("value", value),
								new RuntimeNamedArgument("ky", name), new RuntimeNamedArgument("context", empty)),
						List.of(), contextType));
		for (RuntimeExpression expression : expressions) {
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, contextType, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
		}
	}

	@Test
	void constantDateResultsPreserveHistoricalAndExtremeYears() {
		var date = RuntimeType.scalar(RuntimeTypeKind.DATE);
		for (String sample : List.of("0001-01-01", "1582-10-04", "1582-10-15", "-0001-01-01", "-999999999-01-01",
				"+999999999-12-31")) {
			var constant = new RuntimeConstant(RuntimeConstantKind.DATE, sample, date);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, date, List.of(), Optional.of(constant))), List.of(), List.of(10),
					1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			for (String zone : List.of("UTC", "Europe/Zurich")) {
				spark.conf().set("spark.sql.session.timeZone", zone);
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				Object raw = spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0);
				assertThat(raw).isEqualTo(sample);
				assertThat(SparkSqlFeelValueCodec.fromSpark(raw, date))
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
		assertThatThrownBy(() -> SparkSqlFeelValueCodec.fromSpark("invalid", date))
				.isInstanceOf(IllegalArgumentException.class);
		var invalid = new RuntimeConstant(RuntimeConstantKind.DATE, "invalid", date);
		var model = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, date, List.of(), Optional.of(invalid))), List.of(), List.of(10), 1);
		assertThat(SparkSqlHybridTest.generate(model).capabilities().get(10).nativeSql()).isFalse();
		spark.conf().set("spark.sql.session.timeZone", "UTC");
	}

	@Test
	void nullCalendarDurationCallsDoNotEvaluateUnusedTemporalOperands() {
		var duration = RuntimeType.scalar(RuntimeTypeKind.YEARS_MONTHS_DURATION);
		var absent = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		var temporal = new RuntimeConstant(RuntimeConstantKind.DATE_TIME, "2017-08-25T15:20:59.123456789@Europe/Paris",
				RuntimeType.scalar(RuntimeTypeKind.DATE_TIME));
		for (var args : List.of(List.<RuntimeExpression>of(), List.<RuntimeExpression>of(absent),
				List.<RuntimeExpression>of(absent, temporal), List.<RuntimeExpression>of(temporal, absent))) {
			var expression = new RuntimeFunctionCall("years and months duration", args, duration);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, duration, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)).isNull();
			assertThat(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10)).isNull();
		}
	}

	@Test
	void staticCalendarDurationsPreserveLocalDatesAcrossZonesAndExtremeYears() {
		var type = RuntimeType.scalar(RuntimeTypeKind.YEARS_MONTHS_DURATION);
		var dateTime = RuntimeType.scalar(RuntimeTypeKind.DATE_TIME);
		try {
			for (String zone : List.of("UTC", "Europe/Zurich")) {
				spark.conf().set("spark.sql.session.timeZone", zone);
				for (String[] pair : List.of(new String[]{"-2016-01-30T09:05:00", "-2017-02-28T02:02:02"},
						new String[]{"2017-01-31T23:59:59.999999999-12:00", "2017-02-28T00:00:00.000000001+14:00"},
						new String[]{"2017-03-26T02:30:00@Europe/Paris", "2018-03-26T02:30:00@Etc/UTC"},
						new String[]{"-999999999-01-01T00:00:00", "999999999-12-31T23:59:59"})) {
					RuntimeExpression from = new RuntimeConstant(RuntimeConstantKind.DATE_TIME, pair[0], dateTime);
					RuntimeExpression to = new RuntimeConstant(RuntimeConstantKind.DATE_TIME, pair[1], dateTime);
					for (RuntimeExpression expression : List.<RuntimeExpression>of(
							new RuntimeFunctionCall("years and months duration", List.of(from, to), type),
							new RuntimeInvocationExpression(Optional.of("years and months duration"), Optional.empty(),
									List.of(new RuntimeNamedArgument("to", to), new RuntimeNamedArgument("from", from)),
									List.of(), type))) {
						var model = new RuntimeModel(List.of(),
								List.of(new RuntimeDecision(10, 0, type, List.of(), Optional.of(expression))),
								List.of(), List.of(10), 1);
						var generated = SparkSqlHybridTest.generate(model);
						assertThat(generated.udfs()).isEmpty();
						spark.range(1).createOrReplaceTempView("hybrid_inputs");
						Object actual = SparkSqlFeelValueCodec
								.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0), type);
						assertThat(actual).isEqualTo(
								new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
					}
				}
			}
		} finally {
			spark.conf().set("spark.sql.session.timeZone", "UTC");
		}
	}

	@Test
	void invalidCalendarConstructorsAndListsReturnNativeNull() {
		var type = RuntimeType.scalar(RuntimeTypeKind.YEARS_MONTHS_DURATION);
		RuntimeExpression emptyDate = new RuntimeFunctionCall("date", List.of(SparkSqlHybridTest.string("")),
				RuntimeType.scalar(RuntimeTypeKind.DATE));
		RuntimeExpression emptyList = new RuntimeListExpression(List.of(),
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY));
		for (RuntimeExpression operand : List.of(emptyDate, emptyList)) {
			var expression = new RuntimeFunctionCall("years and months duration", List.of(operand, operand), type);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, type, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)).isNull();
			assertThat(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10)).isNull();
		}
	}

	@Test
	void literalTemporalPropertiesPreserveDurationPartsAndZoneIdentity() {
		try {
			for (String zone : List.of("UTC", "Europe/Zurich")) {
				spark.conf().set("spark.sql.session.timeZone", zone);
				for (String[] sample : List.of(
						new String[]{"DURATION", "-PT25H61M0.123456789S", "days", "hours", "minutes", "seconds",
								"years"},
						new String[]{"DURATION", "P13M", "years", "months", "days"},
						new String[]{"TIME", "10:30:00.123456789+05:30", "hour", "time offset", "timezone"},
						new String[]{"DATE_TIME", "2017-03-26T02:30:00@Europe/Paris", "hour", "time offset",
								"timezone"},
						new String[]{"DATE_TIME", "2018-12-10T10:30:00.000000001", "time offset", "timezone"})) {
					var kind = RuntimeConstantKind.valueOf(sample[0]);
					var source = new RuntimeConstant(kind, sample[1],
							RuntimeType.scalar(RuntimeTypeKind.valueOf(sample[0])));
					for (int index = 2; index < sample.length; index++) {
						String member = sample[index];
						var type = RuntimeType.scalar(member.equals("time offset")
								? RuntimeTypeKind.DAYS_TIME_DURATION
								: member.equals("timezone") ? RuntimeTypeKind.STRING : RuntimeTypeKind.NUMBER);
						var path = new RuntimePathExpression(source, member, type);
						var model = new RuntimeModel(List.of(),
								List.of(new RuntimeDecision(10, 0, type, List.of(), Optional.of(path))), List.of(),
								List.of(10), 1);
						var generated = SparkSqlHybridTest.generate(model);
						assertThat(generated.udfs()).isEmpty();
						spark.range(1).createOrReplaceTempView("hybrid_inputs");
						Object actual = SparkSqlFeelValueCodec
								.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0), type);
						assertThat(actual).isEqualTo(
								new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
					}
				}
			}
		} finally {
			spark.conf().set("spark.sql.session.timeZone", "UTC");
		}
	}

	@Test
	void literalContextConstructionAndMergePreserveHeterogeneousValuesAndKeyRules() {
		var context = RuntimeType.scalar(RuntimeTypeKind.CONTEXT);
		var list = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY);
		var a = nativeContextFixture("key", SparkSqlHybridTest.string("a"), "value", SparkSqlHybridTest.number("1"));
		var b = nativeContextFixture("key", SparkSqlHybridTest.string("b"), "value",
				new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)));
		var entries = new RuntimeListExpression(List.of(a, b), list);
		var mergeInput = new RuntimeListExpression(List.of(nativeContextFixture("a", SparkSqlHybridTest.number("1")),
				nativeContextFixture("b", SparkSqlHybridTest.string("text"), "a", SparkSqlHybridTest.number("2"))),
				list);
		for (RuntimeExpression expression : List
				.<RuntimeExpression>of(new RuntimeFunctionCall("context", List.of(entries), context),
						new RuntimeFunctionCall("context", List.of(a), context),
						new RuntimeFunctionCall("context", List.of(new RuntimeListExpression(List.of(a, a), list)),
								context),
						new RuntimeFunctionCall("context merge", List.of(mergeInput), context),
						new RuntimeFunctionCall("context merge", List.of(nativeContextFixture("a", a)), context),
						new RuntimeInvocationExpression(Optional.of("context"), Optional.empty(),
								List.of(new RuntimeNamedArgument("entries", entries)), List.of(), context),
						new RuntimeInvocationExpression(Optional.of("context merge"), Optional.empty(),
								List.of(new RuntimeNamedArgument("contexts", mergeInput)), List.of(), context))) {
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, context, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0), context);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10);
			assertThat(actual).isEqualTo(expected);
			if (actual instanceof Map<?, ?> map)
				assertThat(new ArrayList<>(map.keySet())).isEqualTo(new ArrayList<>(((Map<?, ?>) expected).keySet()));
		}
	}

	@Test
	void literalContextNullAndEmptyEqualityUseNativeSql() {
		var context = RuntimeType.scalar(RuntimeTypeKind.CONTEXT);
		var empty = new RuntimeListExpression(List.of(),
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY));
		for (String name : List.of("context", "context merge")) {
			RuntimeExpression constructed = new RuntimeFunctionCall(name, List.of(empty), context);
			for (RuntimeExpression expression : List.<RuntimeExpression>of(
					new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, constructed, nativeContextFixture(),
							RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)),
					new RuntimeFunctionCall(name, List.of(), context),
					new RuntimeFunctionCall(name, List.of(SparkSqlHybridTest.string("wrong")), context),
					new RuntimeInvocationExpression(Optional.of(name), Optional.empty(),
							List.of(new RuntimeNamedArgument("wrong", empty)), List.of(), context))) {
				var type = expression.type();
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, type, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0), type))
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
	}

	@Test
	void literalContextPromotionRetainsAmbiguousLayoutsAndPrecisionFallback() {
		var context = RuntimeType.scalar(RuntimeTypeKind.CONTEXT);
		var list = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY);
		for (RuntimeExpression source : List.<RuntimeExpression>of(
				new RuntimeListExpression(List.of(nativeContextFixture("a", SparkSqlHybridTest.number("1")),
						nativeContextFixture("A", SparkSqlHybridTest.number("2"))), list),
				nativeContextFixture("a", SparkSqlHybridTest.number("1E-50")),
				nativeContextFixture("a", SparkSqlHybridTest.number("0E-100")),
				nativeContextFixture("a", SparkSqlHybridTest.number("1E+2147483647")))) {
			var expression = new RuntimeFunctionCall("context merge", List.of(source), context);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, context, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			assertThat(SparkSqlHybridTest.generate(model).capabilities().get(10).nativeSql()).isFalse();
		}
	}

	private static RuntimeContextExpression nativeContextFixture(Object... pairs) {
		var entries = new ArrayList<RuntimeContextEntry>();
		var fields = new ArrayList<RuntimeField>();
		for (int index = 0; index < pairs.length; index += 2) {
			String name = (String) pairs[index];
			RuntimeExpression value = (RuntimeExpression) pairs[index + 1];
			entries.add(new RuntimeContextEntry(name, -1, value));
			fields.add(new RuntimeField(fields.size(), name, value.type()));
		}
		return new RuntimeContextExpression(entries, RuntimeType.contextFields(fields));
	}

	@Test
	void literalPropertyPromotionDoesNotEnableDynamicTemporalInputs() {
		var time = RuntimeType.scalar(RuntimeTypeKind.TIME);
		var path = new RuntimePathExpression(new RuntimeValueReference(0, time), "timezone", SparkSqlHybridTest.STRING);
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, time)),
				List.of(new RuntimeDecision(10, 1, SparkSqlHybridTest.STRING, List.of(5), Optional.of(path))),
				List.of(), List.of(10), 2);
		assertThat(SparkSqlHybridTest.generate(model).capabilities().get(10).nativeSql()).isFalse();
		var constant = new RuntimeConstant(RuntimeConstantKind.TIME, "10:30:00+05:30", time);
		var offset = new RuntimePathExpression(constant, "time offset", SparkSqlHybridTest.ANY);
		var unknownResult = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(offset))), List.of(),
				List.of(10), 1);
		var generated = SparkSqlHybridTest.generate(unknownResult);
		assertThat(generated.capabilities().get(10).nativeSql()).isFalse();
		generated.registerUdfs(spark);
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		assertThat(
				SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
				.isEqualTo(java.time.Duration.ofMinutes(330));
		var local = new RuntimeConstant(RuntimeConstantKind.TIME, "10:30:00", time);
		var absentOffset = new RuntimePathExpression(local, "time offset",
				RuntimeType.scalar(RuntimeTypeKind.DAYS_TIME_DURATION));
		var nullModel = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(absentOffset))),
				List.of(), List.of(10), 1);
		var nullGenerated = SparkSqlHybridTest.generate(nullModel);
		assertThat(nullGenerated.udfs()).isEmpty();
		assertThat(spark.sql(nullGenerated.sqlFiles().get("Decision_0.sql")).head().get(0)).isNull();
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
	void positionalListReplacementInfersNativeResultsAndPreservesNullAndFractionalPositions() {
		var listType = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER);
		var resultType = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY);
		var source = new RuntimeListExpression(
				List.of(SparkSqlHybridTest.number("1"), SparkSqlHybridTest.number("2"), SparkSqlHybridTest.number("3")),
				listType);
		var nil = new RuntimeConstant(RuntimeConstantKind.NULL, "null", SparkSqlHybridTest.ANY);
		var expressions = new ArrayList<RuntimeExpression>();
		for (RuntimeExpression position : List.of(SparkSqlHybridTest.number("2"), SparkSqlHybridTest.number("-1"),
				SparkSqlHybridTest.number("0"), SparkSqlHybridTest.number("4"), SparkSqlHybridTest.number("-4"),
				SparkSqlHybridTest.number("2.5"), SparkSqlHybridTest.number("-1.5"), SparkSqlHybridTest.string("2"),
				nil)) {
			for (RuntimeExpression replacement : List.of(SparkSqlHybridTest.number("4"), nil))
				expressions.add(
						new RuntimeFunctionCall("list replace", List.of(source, position, replacement), resultType));
		}
		expressions.add(new RuntimeFunctionCall("list replace",
				List.of(nil, SparkSqlHybridTest.number("1"), SparkSqlHybridTest.number("4")), resultType));
		expressions.add(new RuntimeFunctionCall("list replace",
				List.of(SparkSqlHybridTest.number("1"), SparkSqlHybridTest.number("1"), SparkSqlHybridTest.number("5")),
				resultType));
		expressions.add(new RuntimeInvocationExpression(Optional.of("list replace"), Optional.empty(),
				List.of(new RuntimeNamedArgument("newItem", SparkSqlHybridTest.number("4")),
						new RuntimeNamedArgument("position", SparkSqlHybridTest.number("2")),
						new RuntimeNamedArgument("list", source)),
				List.of(), resultType));
		for (var expression : expressions) {
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, resultType, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.sql("SELECT 1").createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0));
			assertThat(actual).as("%s", expression).isEqualTo(expected);
		}
		var unsafe = new RuntimeFunctionCall("list replace",
				List.of(source, SparkSqlHybridTest.number("1.9999999999999999999"), SparkSqlHybridTest.number("4")),
				resultType);
		assertThat(SparkSqlExpressionEmitter.nativeListReplaceType(unsafe)).isNull();
	}

	@Test
	void invalidOperationsExecuteNativelyWithoutCoercingUnsupportedOperands() {
		var function = new RuntimeFunctionDefinition(List.of(), Optional.of(SparkSqlHybridTest.number("1")), false,
				RuntimeType.function(List.of(), SparkSqlHybridTest.NUMBER));
		var expressions = new ArrayList<RuntimeExpression>();
		for (var operator : List.of(RuntimeBinaryOperator.ADD, RuntimeBinaryOperator.SUBTRACT,
				RuntimeBinaryOperator.MULTIPLY, RuntimeBinaryOperator.DIVIDE, RuntimeBinaryOperator.POWER)) {
			expressions.add(new RuntimeBinaryExpression(operator, function, SparkSqlHybridTest.string("x"),
					SparkSqlHybridTest.ANY));
			expressions.add(new RuntimeBinaryExpression(operator, SparkSqlHybridTest.number("2"), function,
					SparkSqlHybridTest.ANY));
		}
		for (String duration : List.of("P4D", "P4Y")) {
			var argument = new RuntimeConstant(RuntimeConstantKind.DURATION, duration,
					RuntimeType.scalar(RuntimeTypeKind.DURATION));
			for (String name : List.of("sqrt", "exp", "log", "even", "odd", "modulo"))
				expressions.add(new RuntimeFunctionCall(name,
						name.equals("modulo") ? List.of(argument, SparkSqlHybridTest.number("4")) : List.of(argument),
						SparkSqlHybridTest.ANY));
		}
		expressions.add(new RuntimeInvocationExpression(Optional.empty(), Optional.of(SparkSqlHybridTest.string("abs")),
				List.of(), List.of(SparkSqlHybridTest.number("-1")), SparkSqlHybridTest.ANY));
		expressions.add(new RuntimeFunctionCall("date", List.of(), RuntimeType.scalar(RuntimeTypeKind.DATE)));
		for (var expression : expressions) {
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, expression.type(), List.of(), Optional.of(expression))),
					List.of(), List.of(10), 1);
			assertThat(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10))
					.as("runtime %s", expression).isNull();
			assertThat(new SparkSqlCapabilityAnalyzer().analyze(model, 10, true).nativeSql()).isTrue();
			assertThat(spark.sql("SELECT " + SparkSqlExpressionEmitter.emit(expression, slot -> null)).head().get(0))
					.isNull();
		}
	}

	@Test
	void typedFiltersAndQuantifiersPreserveFalseNullAndEmptyPredicates() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var listType = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER);
		var source = new RuntimeListExpression(
				List.of(SparkSqlHybridTest.number("1"), SparkSqlHybridTest.number("2"), SparkSqlHybridTest.number("3")),
				listType);
		var empty = new RuntimeListExpression(List.of(),
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY));
		var comparison = new RuntimeBinaryExpression(RuntimeBinaryOperator.GREATER,
				new RuntimeLocalReference(0, SparkSqlHybridTest.NUMBER), SparkSqlHybridTest.number("1"), bool);
		var expressions = new ArrayList<RuntimeExpression>();
		for (RuntimeExpression predicate : List.of(comparison,
				new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool),
				new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", bool),
				new RuntimeConstant(RuntimeConstantKind.NULL, "null", bool))) {
			expressions.add(new RuntimeFilterExpression(source, 0, predicate, listType));
			for (var quantifier : RuntimeQuantifier.values()) {
				expressions.add(new RuntimeQuantifiedExpression(quantifier,
						List.of(new RuntimeQuantifiedBinding(0, source)), predicate, bool));
				expressions.add(new RuntimeQuantifiedExpression(quantifier,
						List.of(new RuntimeQuantifiedBinding(0, empty)), predicate, bool));
			}
		}
		for (var expression : expressions) {
			var model = new RuntimeModel(List.of(), List.of(new RuntimeDecision(10, 0, expression.type(), List.of(),
					Optional.of(expression), Optional.empty(), 1)), List.of(), List.of(10), 1);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10);
			assertThat(new SparkSqlCapabilityAnalyzer().analyze(model, 10, true).nativeSql()).as("%s", expression)
					.isTrue();
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql("SELECT " + SparkSqlExpressionEmitter.emit(expression, slot -> null)).head().get(0));
			assertThat(actual).as("%s", expression).isEqualTo(expected);
		}
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

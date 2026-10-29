package io.finmsg.dmn.generator.sparksql;

import static org.assertj.core.api.Assertions.*;

import io.finmsg.dmn.ir.*;
import java.util.*;
import org.apache.spark.sql.*;
import org.junit.jupiter.api.*;

class SparkSqlTemporalChainIntegrationTest {
	private static SparkSession spark;
	private static SparkSqlProcessingAccounting processing;
	@BeforeAll
	static void start() {
		spark = SparkSession.builder().master("local[2]").appName("Exact temporal chains")
				.config("spark.ui.enabled", "false").config("spark.sql.shuffle.partitions", "2").getOrCreate();
		processing = SparkSqlProcessingAccounting.attach(spark);
	}
	@AfterAll
	static void stop() throws Exception {
		try {
			processing.report(spark, "SparkSqlTemporalChainIntegrationTest");
		} finally {
			spark.stop();
		}
	}
	@Test
	void dateTimeConstructorsRejectIncompatibleOperandKinds() {
		var string = RuntimeType.scalar(RuntimeTypeKind.STRING);
		var date = RuntimeType.scalar(RuntimeTypeKind.DATE);
		var dt = RuntimeType.scalar(RuntimeTypeKind.DATE_TIME);
		var source = new RuntimeValueReference(0, string);
		var parsedDate = new RuntimeFunctionCall("date", List.of(source), date);
		var parsedDateTime = new RuntimeFunctionCall("date and time", List.of(source), dt);
		for (var args : List.of(List.<RuntimeExpression>of(parsedDate),
				List.<RuntimeExpression>of(parsedDate, parsedDateTime))) {
			var call = new RuntimeFunctionCall("date and time", args, dt);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, string)),
					List.of(new RuntimeDecision(10, 1, dt, List.of(5), Optional.of(call))), List.of(), List.of(10), 2);
			assertGenerated(model, Map.of(0, "2024-02-29"), 10, 1);
		}
	}
	@Test
	void generatedDurationsRetainPeriodIdentityAndSignedNanoseconds() {
		var string = RuntimeType.scalar(RuntimeTypeKind.STRING);
		var duration = RuntimeType.scalar(RuntimeTypeKind.DURATION);
		var call = new RuntimeFunctionCall("duration", List.of(new RuntimeValueReference(0, string)), duration);
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, string)),
				List.of(new RuntimeDecision(10, 1, duration, List.of(5), Optional.of(call))), List.of(), List.of(10),
				2);
		for (String value : List.of("P13DT2H14S", "PT-0.000000001S", "P13M", "P1Y2M3D", "nonsense"))
			assertGenerated(model, Map.of(0, value), 10, 1);
	}
	@Test
	void numericTimeConstructorPreservesExactFractionAndDurationOffset() {
		var number = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
		var duration = RuntimeType.scalar(RuntimeTypeKind.DAYS_TIME_DURATION);
		var time = RuntimeType.scalar(RuntimeTypeKind.TIME);
		var call = new RuntimeFunctionCall("time",
				List.of(new RuntimeValueReference(0, number), new RuntimeValueReference(1, number),
						new RuntimeValueReference(2, number), new RuntimeValueReference(3, duration)),
				time);
		var model = new RuntimeModel(
				List.of(new RuntimeInput(5, 0, number), new RuntimeInput(6, 1, number), new RuntimeInput(7, 2, number),
						new RuntimeInput(8, 3, duration)),
				List.of(new RuntimeDecision(10, 4, time, List.of(5, 6, 7, 8), Optional.of(call))), List.of(),
				List.of(10), 5);
		for (String seconds : List.of("1.3", "1.123456789", "-0.1"))
			assertGenerated(model,
					Map.of(0, 12, 1, 59, 2, new java.math.BigDecimal(seconds), 3, java.time.Duration.ofHours(-1)), 10,
					4);
	}
	private void assertGenerated(RuntimeModel model, Map<Integer, ?> values, int decisionId, int slot) {
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(decisionId).nativeSql()).as("%s", generated.capabilities()).isTrue();
		Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, new HashMap<Integer, Object>(values))
				.decisionValue(decisionId);
		spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, values, generated.inputSchema())),
				generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
		var row = spark.sql(generated.sqlFiles().get("Decision_" + slot + ".sql")).head();
		assertThat(SparkSqlFeelValueCodec.fromSpark(row.get(0))).isEqualTo(expected);
	}
	@Test
	void generatedDateTimeChainPreservesNanosecondsAndOffsets() {
		var string = RuntimeType.scalar(RuntimeTypeKind.STRING);
		var dateTime = RuntimeType.scalar(RuntimeTypeKind.DATE_TIME);
		var duration = RuntimeType.scalar(RuntimeTypeKind.DURATION);
		var decisions = List.of(
				new RuntimeDecision(10, 2, dateTime, List.of(5),
						Optional.of(new RuntimeFunctionCall("date and time",
								List.of(new RuntimeValueReference(0, string)), dateTime))),
				new RuntimeDecision(11, 3, dateTime, List.of(6),
						Optional.of(new RuntimeFunctionCall("date and time",
								List.of(new RuntimeValueReference(1, string)), dateTime))),
				new RuntimeDecision(12, 4, duration, List.of(10, 11),
						Optional.of(new RuntimeBinaryExpression(RuntimeBinaryOperator.SUBTRACT,
								new RuntimeValueReference(2, dateTime), new RuntimeValueReference(3, dateTime),
								duration))));
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, string), new RuntimeInput(6, 1, string)), decisions,
				List.of(), List.of(10, 11, 12), 5);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(12).nativeSql()).as("%s", generated.capabilities()).isTrue();
		for (var sample : List.of(List.of("2016-12-24T23:59:00.123456789-08:00", "2015-12-24T00:00:01.000000001-01:00"),
				List.of("2024-03-31T03:30@Europe/Zurich", "2024-03-31T01:30@Europe/Zurich"),
				List.of("2050-10-30T02:30@Europe/Zurich", "2050-10-30T00:30Z"))) {
			var values = Map.<Integer, Object>of(0, sample.get(0), 1, sample.get(1));
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, values).decisionValue(12);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, values, generated.inputSchema())),
					generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			var row = spark.sql(generated.sqlFiles().get("Decision_4.sql")).head();
			assertThat(SparkSqlFeelValueCodec.fromSpark(row.get(0))).isEqualTo(expected);
		}
	}
	@Test
	void exactDateComponentsAndInvalidDates() {
		for (String text : new String[]{"2024-02-29", "0000-01-01", "-0001-12-31", "999999999-12-31"}) {
			var expected = java.time.LocalDate.parse(text.startsWith("999999999") ? "+" + text : text);
			var row = spark.sql("SELECT v.t, v.day FROM (SELECT "
					+ SparkSqlTemporalFunctions.date(SparkSqlTemporalFunctions.q(text)) + " AS v)").head();
			assertThat(row.getString(0)).isEqualTo(expected.toString());
			assertThat(row.getLong(1)).isEqualTo(expected.toEpochDay());
		}
		var invalid = spark.sql("SELECT v.k FROM (SELECT " + SparkSqlTemporalFunctions.date("'2023-02-29'") + " AS v)")
				.head();
		assertThat(invalid.getString(0)).isEqualTo("NULL");
	}
	@Test
	void jdkTemporalFactoriesUseNativeSparkBuiltins() {
		var row = spark.sql("SELECT try_reflect('java.time.LocalDate','of',1999,11,22), "
				+ "try_reflect('java.time.LocalTime','of',12,59,1,123456789), "
				+ "try_reflect('java.time.OffsetTime','parse','00:00:01.123456789-01:00'), "
				+ "try_reflect('java.time.Duration','parse','P13DT2H14S'), "
				+ "try_reflect('java.time.LocalDate','of',2023,2,29)").head();
		assertThat(row.getString(0)).isEqualTo("1999-11-22");
		assertThat(row.getString(1)).isEqualTo("12:59:01.123456789");
		assertThat(row.getString(2)).isEqualTo("00:00:01.123456789-01:00");
		assertThat(row.getString(3)).isEqualTo("PT314H14S");
		assertThat(row.isNullAt(4)).isTrue();
	}
}

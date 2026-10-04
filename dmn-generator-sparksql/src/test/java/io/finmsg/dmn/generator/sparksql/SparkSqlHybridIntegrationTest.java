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

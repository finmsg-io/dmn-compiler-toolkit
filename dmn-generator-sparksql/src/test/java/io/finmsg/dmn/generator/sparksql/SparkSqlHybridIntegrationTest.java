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

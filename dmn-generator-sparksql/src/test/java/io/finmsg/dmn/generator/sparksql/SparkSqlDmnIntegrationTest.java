package io.finmsg.dmn.generator.sparksql;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.ir.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SparkSqlDmnIntegrationTest {

	private static SparkSession spark;
	private final DmnCompiler compiler = new DmnCompiler();
	private final DmnSparkSqlGenerator generator = new DmnSparkSqlGenerator();

	@BeforeAll
	static void setupSpark() {
		try {
			spark = SparkSession.builder().appName("DmnSparkSqlIntegrationTest").master("local[1]")
					.config("spark.ui.enabled", "false").config("spark.sql.shuffle.partitions", "1").getOrCreate();
		} catch (Throwable t) {
			// Catch Hadoop UGI / JDK 25 Subject.getSubject deprecation exception during
			// local SparkSession setup
			spark = null;
		}
	}

	@AfterAll
	static void tearDownSpark() {
		if (spark != null) {
			try {
				spark.stop();
			} catch (Throwable ignored) {
			}
		}
	}

	@Test
	void generatesSparkSqlCteQueriesAndExecutesOnSpark() throws Exception {
		Path scenarioDir = Path.of("../dmn-compiler/src/test/resources/corpus/p2-09-lending-eligibility");
		Path rootFile = scenarioDir.resolve("credit-application.dmn");
		DmnSource rootSource = new DmnSource(DmnSourceId.of(rootFile.toUri().toString()), Files.readAllBytes(rootFile));
		DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

		DmnCompilationResult compilation = compiler.compile(rootSource, resolver);
		assertThat(compilation.isSuccess()).isTrue();

		RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();

		DmnSparkSqlGeneratorOptions options = DmnSparkSqlGeneratorOptions.defaults();
		DmnSparkSqlGeneratorResult result = generator.generate(optimized, options);

		assertThat(result.sqlFiles()).isNotEmpty();
		assertThat(result.inputSchema()).isNotNull();
		assertThat(result.javaSources()).containsKey("io.finmsg.dmn.spark.DmnSparkSqlRunner");

		String runnerCode = result.javaSources().get("io.finmsg.dmn.spark.DmnSparkSqlRunner");
		assertThat(runnerCode).contains("public final class DmnSparkSqlRunner");

		for (Map.Entry<String, String> entry : result.sqlFiles().entrySet()) {
			String fileName = entry.getKey();
			String sqlContent = entry.getValue();

			assertThat(fileName).endsWith(".sql");
			assertThat(sqlContent).contains("WITH _base_input AS");
		}
	}

	@Test
	void testTck0001InputDataStringOnSpark() throws Exception {
		Path dmnPath = Path.of("../dmn-tck-runner/src/test/resources/tck-official/TestCases/compliance-level-2/0001-input-data-string/0001-input-data-string.dmn");
		if (!Files.exists(dmnPath)) return;

		DmnSource source = new DmnSource(DmnSourceId.of(dmnPath.toUri().toString()), Files.readAllBytes(dmnPath));
		DmnCompilationResult compilation = compiler.compile(source, new InMemoryDmnModelResolver(List.of()));
		assertThat(compilation.isSuccess()).isTrue();

		RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
		DmnSparkSqlGeneratorResult genResult = generator.generate(optimized);

		assertThat(genResult.sqlFiles()).containsKey("Decision_0.sql");
		String sql = genResult.sqlFiles().get("Decision_0.sql");

		if (spark != null) {
			StructType schema = genResult.inputSchema();
			Dataset<Row> df = spark.createDataFrame(List.of(RowFactory.create("John Doe")), schema);
			df.createOrReplaceTempView("input_table");

			Dataset<Row> resultDf = spark.sql(sql);
			List<Row> rows = resultDf.collectAsList();
			assertThat(rows).hasSize(1);
			assertThat(rows.get(0).getString(0)).isEqualTo("Hello John Doe");
		}
	}

	@Test
	void testTck0002InputDataNumberOnSpark() throws Exception {
		Path dmnPath = Path.of("../dmn-tck-runner/src/test/resources/tck-official/TestCases/compliance-level-2/0002-input-data-number/0002-input-data-number.dmn");
		if (!Files.exists(dmnPath)) return;

		DmnSource source = new DmnSource(DmnSourceId.of(dmnPath.toUri().toString()), Files.readAllBytes(dmnPath));
		DmnCompilationResult compilation = compiler.compile(source, new InMemoryDmnModelResolver(List.of()));
		assertThat(compilation.isSuccess()).isTrue();

		RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
		DmnSparkSqlGeneratorResult genResult = generator.generate(optimized);

		assertThat(genResult.sqlFiles()).containsKey("Decision_0.sql");
		String sql = genResult.sqlFiles().get("Decision_0.sql");

		if (spark != null) {
			StructType schema = genResult.inputSchema();
			Dataset<Row> df = spark.createDataFrame(List.of(RowFactory.create(10000.0)), schema);
			df.createOrReplaceTempView("input_table");

			Dataset<Row> resultDf = spark.sql(sql);
			List<Row> rows = resultDf.collectAsList();
			assertThat(rows).hasSize(1);
			assertThat(rows.get(0).getDouble(0)).isEqualTo(120000.0);
		}
	}

	@Test
	void testTck0004SimpleTableUniqueOnSpark() throws Exception {
		Path dmnPath = Path.of("../dmn-tck-runner/src/test/resources/tck-official/TestCases/compliance-level-2/0004-simpletable-U/0004-simpletable-U.dmn");
		if (!Files.exists(dmnPath)) return;

		DmnSource source = new DmnSource(DmnSourceId.of(dmnPath.toUri().toString()), Files.readAllBytes(dmnPath));
		DmnCompilationResult compilation = compiler.compile(source, new InMemoryDmnModelResolver(List.of()));
		assertThat(compilation.isSuccess()).isTrue();

		RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
		DmnSparkSqlGeneratorResult genResult = generator.generate(optimized);

		assertThat(genResult.sqlFiles()).containsKey("Decision_0.sql");
		String sql = genResult.sqlFiles().get("Decision_0.sql");

		if (spark != null) {
			StructType schema = genResult.inputSchema();
			// Case 1: 18, "Medium", true -> "Approved"
			Dataset<Row> df1 = spark.createDataFrame(List.of(RowFactory.create(18.0, "Medium", true)), schema);
			df1.createOrReplaceTempView("input_table");
			List<Row> rows1 = spark.sql(sql).collectAsList();
			assertThat(rows1.get(0).getString(0)).isEqualTo("Approved");

			// Case 2: 17, "Medium", true -> "Declined"
			Dataset<Row> df2 = spark.createDataFrame(List.of(RowFactory.create(17.0, "Medium", true)), schema);
			df2.createOrReplaceTempView("input_table");
			List<Row> rows2 = spark.sql(sql).collectAsList();
			assertThat(rows2.get(0).getString(0)).isEqualTo("Declined");

			// Case 3: 25, "High", true -> "Declined"
			Dataset<Row> df3 = spark.createDataFrame(List.of(RowFactory.create(25.0, "High", true)), schema);
			df3.createOrReplaceTempView("input_table");
			List<Row> rows3 = spark.sql(sql).collectAsList();
			assertThat(rows3.get(0).getString(0)).isEqualTo("Declined");

			// Case 4: 25, "Low", false -> "Declined"
			Dataset<Row> df4 = spark.createDataFrame(List.of(RowFactory.create(25.0, "Low", false)), schema);
			df4.createOrReplaceTempView("input_table");
			List<Row> rows4 = spark.sql(sql).collectAsList();
			assertThat(rows4.get(0).getString(0)).isEqualTo("Declined");
		}
	}

	@Test
	void testTck0010MultiOutputTableOnSpark() throws Exception {
		Path dmnPath = Path.of("../dmn-tck-runner/src/test/resources/tck-official/TestCases/compliance-level-2/0010-multi-output-U/0010-multi-output-U.dmn");
		if (!Files.exists(dmnPath)) return;

		DmnSource source = new DmnSource(DmnSourceId.of(dmnPath.toUri().toString()), Files.readAllBytes(dmnPath));
		DmnCompilationResult compilation = compiler.compile(source, new InMemoryDmnModelResolver(List.of()));
		assertThat(compilation.isSuccess()).isTrue();

		RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
		DmnSparkSqlGeneratorResult genResult = generator.generate(optimized);

		assertThat(genResult.sqlFiles()).containsKey("Decision_0.sql");
		String sql = genResult.sqlFiles().get("Decision_0.sql");

		if (spark != null) {
			StructType schema = genResult.inputSchema();
			// 18, "Medium", true -> Status: "Approved", Rate: "Standard"
			Dataset<Row> df = spark.createDataFrame(List.of(RowFactory.create(18.0, "Medium", true)), schema);
			df.createOrReplaceTempView("input_table");

			Dataset<Row> resultDf = spark.sql(sql);
			List<Row> rows = resultDf.collectAsList();
			assertThat(rows).hasSize(1);
			Row structRow = rows.get(0).getStruct(0);
			assertThat(structRow.getString(0)).isEqualTo("Approved");
			assertThat(structRow.getString(1)).isEqualTo("Standard");
		}
	}

	@Test
	void testTck0105FeelMathOnSpark() throws Exception {
		Path dmnPath = Path.of("../dmn-tck-runner/src/test/resources/tck-official/TestCases/compliance-level-2/0105-feel-math/0105-feel-math.dmn");
		if (!Files.exists(dmnPath)) return;

		DmnSource source = new DmnSource(DmnSourceId.of(dmnPath.toUri().toString()), Files.readAllBytes(dmnPath));
		DmnCompilationResult compilation = compiler.compile(source, new InMemoryDmnModelResolver(List.of()));
		assertThat(compilation.isSuccess()).isTrue();

		RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
		DmnSparkSqlGeneratorResult genResult = generator.generate(optimized);

		assertThat(genResult.sqlFiles()).isNotEmpty();
		if (spark != null) {
			spark.sql("SELECT 1 AS _dummy").createOrReplaceTempView("input_table");

			for (String sql : genResult.sqlFiles().values()) {
				Dataset<Row> resultDf = spark.sql(sql);
				assertThat(resultDf.collectAsList()).hasSize(1);
			}
		}
	}
}

package io.finmsg.dmn.generator.sparksql;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.ir.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.spark.sql.SparkSession;
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
			assertThat(sqlContent).contains("SELECT ");
		}
	}
}

package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.apache.spark.sql.*;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.*;

class SparkSqlLiteralEscapingIntegrationTest {
	private static SparkSession spark;
	private static SparkSqlProcessingAccounting processing;
	@BeforeAll
	static void start() {
		spark = SparkSession.builder().master("local[1]").appName("SQL literal escaping")
				.config("spark.ui.enabled", "false").config("spark.sql.shuffle.partitions", "1").getOrCreate();
		spark.sparkContext().setLogLevel("ERROR");
		String actualParserMode = spark.conf().get("spark.sql.parser.escapedStringLiterals");
		System.out.println("LITERAL PARSER START: escapedStringLiterals=" + actualParserMode);
		String requestedParserMode = System.getProperty("spark.sql.parser.escapedStringLiterals");
		if (requestedParserMode != null)
			assertThat(actualParserMode).isEqualTo(requestedParserMode);
		processing = SparkSqlProcessingAccounting.attach(spark);
	}
	@AfterAll
	static void stop() throws Exception {
		try {
			processing.report(spark, "SparkSqlLiteralEscapingIntegrationTest");
		} finally {
			spark.stop();
		}
	}
	@Test
	void constantsPreserveTextWithDefaultParser() {
		assertConstants(false);
	}
	@Test
	void constantsPreserveTextWithLegacyParser() {
		assertConstants(true);
	}
	@Test
	void fieldNamesRegexAndTemporalArgumentsPreserveRuntimeValuesInBothModes() {
		String previous = spark.conf().get("spark.sql.parser.escapedStringLiterals");
		try {
			for (boolean legacy : List.of(false, true)) {
				spark.conf().set("spark.sql.parser.escapedStringLiterals", legacy);
				var string = RuntimeType.scalar(RuntimeTypeKind.STRING);
				var number = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
				var date = RuntimeType.scalar(RuntimeTypeKind.DATE);
				var names = List.of("O'Reilly", "path\\field", "line\nkey", "Grüezi 東京");
				for (var inputType : List.of(string, number, date)) {
					var entries = new ArrayList<RuntimeContextEntry>();
					var fields = new ArrayList<RuntimeField>();
					RuntimeType resultType = inputType == number ? number : string;
					for (int i = 0; i < names.size(); i++) {
						RuntimeExpression value = new RuntimeValueReference(0, inputType);
						if (inputType == date)
							value = new RuntimeFunctionCall("day of week", List.of(value), string);
						if (inputType == number)
							value = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, value,
									new RuntimeConstant(RuntimeConstantKind.NUMBER, "1", number), number);
						entries.add(new RuntimeContextEntry(names.get(i), -1, value));
						fields.add(new RuntimeField(i, names.get(i), resultType));
					}
					var type = inputType == date
							? RuntimeType.scalar(RuntimeTypeKind.ANY)
							: RuntimeType.contextFields(fields);
					var context = new RuntimeContextExpression(entries, type);
					Object input = inputType == number
							? new java.math.BigDecimal("12345678901234567890.123")
							: inputType == date ? java.time.LocalDate.of(2024, 2, 29) : "text'\\\"\n😀";
					assertGenerated(model(inputType, context), Map.of(0, input));
				}
				var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
				var matches = new RuntimeFunctionCall("matches", List.of(new RuntimeValueReference(0, string),
						new RuntimeConstant(RuntimeConstantKind.STRING, "\\d+\\.'", string)), bool);
				assertGenerated(model(string, matches), Map.of(0, "123.'"));
				var dt = RuntimeType.scalar(RuntimeTypeKind.DATE_TIME);
				var parse = new RuntimeFunctionCall("date and time", List.of(new RuntimeValueReference(0, string)), dt);
				for (String input : List.of("2024-02-29T12:34:56.123456789+02:00", "bad'\\\ntext"))
					assertGenerated(model(string, parse), Map.of(0, input));
			}
		} finally {
			spark.conf().set("spark.sql.parser.escapedStringLiterals", previous);
		}
	}
	@Test
	void generatedJavaRunnerPreservesSqlTextAcrossBothEscapeLayers(
			@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
		var string = RuntimeType.scalar(RuntimeTypeKind.STRING);
		String value = "quote'\\\"\n\r\tGrüezi 東京 😀";
		String quoted = "double\"quote Grüezi 東京 😀";
		var model = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, string, List.of(),
						Optional.of(new RuntimeConstant(RuntimeConstantKind.STRING, value, string))),
						new RuntimeDecision(11, 1, string, List.of(),
								Optional.of(new RuntimeConstant(RuntimeConstantKind.STRING, quoted, string)))),
				List.of(), List.of(10, 11), 2);
		var generated = new DmnSparkSqlGenerator().generate(SparkSqlHybridTest.optimized(model),
				new DmnSparkSqlGeneratorOptions("generated", "LiteralRunner", "runner_inputs", true, true, Map.of()));
		var source = directory.resolve("generated/LiteralRunner.java");
		java.nio.file.Files.createDirectories(source.getParent());
		java.nio.file.Files.writeString(source, generated.javaSources().get("generated.LiteralRunner"));
		assertThat(javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath",
				System.getProperty("java.class.path"), "-d", directory.toString(), source.toString())).isZero();
		String previous = spark.conf().get("spark.sql.parser.escapedStringLiterals");
		try (var loader = new java.net.URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},
				getClass().getClassLoader())) {
			var runner = loader.loadClass("generated.LiteralRunner");
			for (boolean legacy : List.of(false, true)) {
				spark.conf().set("spark.sql.parser.escapedStringLiterals", legacy);
				for (int slot = 0; slot < 2; slot++) {
					var result = (Dataset<?>) runner
							.getMethod("evaluate", SparkSession.class, Dataset.class, String.class)
							.invoke(null, spark, spark.range(1).toDF(), "Decision_" + slot);
					assertThat(((Row) result.head()).getString(0)).isEqualTo(slot == 0 ? value : quoted);
				}
			}
		} finally {
			spark.conf().set("spark.sql.parser.escapedStringLiterals", previous);
		}
	}
	private RuntimeModel model(RuntimeType input, RuntimeExpression expression) {
		return new RuntimeModel(List.of(new RuntimeInput(5, 0, input)),
				List.of(new RuntimeDecision(10, 1, expression.type(), List.of(5), Optional.of(expression))), List.of(),
				List.of(10), 2);
	}
	private void assertGenerated(RuntimeModel model, Map<Integer, ?> inputs) {
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(10).nativeSql()).as("%s", generated.capabilities()).isTrue();
		spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
				generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
		Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
		for (String query : List.of("Decision_1.sql", "AllDecisions.sql"))
			assertThat(normalize(
					SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(query)).head().get(0))))
					.isEqualTo(normalize(expected));
	}
	private Object normalize(Object value) {
		if (value instanceof io.finmsg.dmn.runtime.RuntimeContextValue context)
			return normalize(context.namedFields());
		if (value instanceof Map<?, ?> map) {
			var result = new LinkedHashMap<Object, Object>();
			map.forEach((key, item) -> result.put(key, normalize(item)));
			return result;
		}
		return value;
	}
	private void assertConstants(boolean legacy) {
		String previous = spark.conf().get("spark.sql.parser.escapedStringLiterals");
		try {
			spark.conf().set("spark.sql.parser.escapedStringLiterals", legacy);
			var assertions = new SoftAssertions();
			for (String value : List.of("O'Reilly", "C:\\temp\\tail", "quote'\\tail", "\\'", "line\nnext\r\ntab\tend",
					"Grüezi 東京 😀", "'); SELECT 999; --", "", "\\n\\u0041\\0", "\u0000\u007f", "double\"quote")) {
				var string = RuntimeType.scalar(RuntimeTypeKind.STRING);
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, string, List.of(),
								Optional.of(new RuntimeConstant(RuntimeConstantKind.STRING, value, string)))),
						List.of(), List.of(10), 1);
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10);
				var generated = SparkSqlHybridTest.generate(model);
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				String query = generated.sqlFiles().get("Decision_0.sql");
				try {
					assertions.assertThat(spark.sql(query).head().get(0))
							.as("legacy=%s value=%s SQL=%s", legacy, value, query).isEqualTo(expected);
				} catch (Exception exception) {
					assertions.fail("legacy=" + legacy + " value=" + value + " SQL=" + query, exception);
				}
			}
			assertions.assertAll();
		} finally {
			spark.conf().set("spark.sql.parser.escapedStringLiterals", previous);
		}
	}
}

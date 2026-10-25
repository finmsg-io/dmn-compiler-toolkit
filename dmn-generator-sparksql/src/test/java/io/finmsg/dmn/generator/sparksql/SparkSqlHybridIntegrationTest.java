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
	private static SparkSqlProcessingAccounting processing;
	@org.junit.jupiter.api.io.TempDir
	java.nio.file.Path generatedDirectory;

	@BeforeAll
	static void start() {
		spark = SparkSession.builder().master("local[2]").appName("DMN hybrid bridge")
				.config("spark.ui.enabled", "false").config("spark.sql.shuffle.partitions", "2").getOrCreate();
		processing = SparkSqlProcessingAccounting.attach(spark);
	}
	@AfterAll
	static void stop() throws java.util.concurrent.TimeoutException {
		if (spark != null) {
			try {
				processing.report(spark, "SparkSqlHybridIntegrationTest");
			} finally {
				spark.stop();
			}
		}
	}

	private static Map<String, Object> joinRow(String key, Object number, String textName, Object text) {
		var row = new HashMap<String, Object>();
		row.put(key, number);
		row.put(textName, text);
		return row;
	}
	private static RuntimeModel indexedJoinFixture() {
		var number = SparkSqlHybridTest.NUMBER;
		var string = SparkSqlHybridTest.STRING;
		var any = SparkSqlHybridTest.ANY;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var departments = RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType
				.contextFields(List.of(new RuntimeField(0, "number", number), new RuntimeField(1, "manager", string))));
		var employees = RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType
				.contextFields(List.of(new RuntimeField(0, "name", string), new RuntimeField(1, "dept", number))));
		var employeeFilter = new RuntimeFilterExpression(new RuntimeValueReference(1, employees), 1,
				new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL,
						new RuntimePathExpression(new RuntimeLocalReference(1, any), "name", string),
						new RuntimeValueReference(2, string), bool),
				employees);
		var selectedKey = new RuntimeFilterExpression(
				new RuntimePathExpression(employeeFilter, "dept", RuntimeType.element(RuntimeTypeKind.LIST, number)), 2,
				SparkSqlHybridTest.number("1"), any);
		var departmentFilter = new RuntimeFilterExpression(new RuntimeValueReference(0, departments), 0,
				new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL,
						new RuntimePathExpression(new RuntimeLocalReference(0, any), "number", number), selectedKey,
						bool),
				departments);
		var result = new RuntimeFilterExpression(new RuntimePathExpression(departmentFilter, "manager",
				RuntimeType.element(RuntimeTypeKind.LIST, string)), 3, SparkSqlHybridTest.number("1"), any);
		return new RuntimeModel(
				List.of(new RuntimeInput(5, 0, departments), new RuntimeInput(6, 1, employees),
						new RuntimeInput(7, 2, string)),
				List.of(new RuntimeDecision(20, 3, string, List.of(5, 6, 7), Optional.of(result), Optional.empty(), 4)),
				List.of(), List.of(20), 4);
	}
	private static RuntimeModel exactProductFixture() {
		var number = SparkSqlHybridTest.NUMBER;
		var list = RuntimeType.element(RuntimeTypeKind.LIST, number);
		var loop = new RuntimeForExpression(
				List.of(new RuntimeIteration(0, new RuntimeValueReference(0, list), Optional.empty()),
						new RuntimeIteration(1, new RuntimeValueReference(1, list), Optional.empty())),
				new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, new RuntimeLocalReference(0, number),
						new RuntimeLocalReference(1, number), number),
				-1, list);
		return new RuntimeModel(List.of(new RuntimeInput(5, 0, list), new RuntimeInput(6, 1, list)),
				List.of(new RuntimeDecision(10, 2, list, List.of(5, 6), Optional.of(loop), Optional.empty(), 2)),
				List.of(), List.of(10), 3);
	}
	private static RuntimeModel scalarAffordabilityFixture() {
		var n = SparkSqlHybridTest.NUMBER;
		var str = SparkSqlHybridTest.STRING;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var monthly = RuntimeType.contextFields(List.of(new RuntimeField(0, "income", n),
				new RuntimeField(1, "expenses", n), new RuntimeField(2, "repayments", n)));
		var inputType = RuntimeType.contextFields(List.of(new RuntimeField(0, "monthly", monthly),
				new RuntimeField(1, "risk", str), new RuntimeField(2, "required", n),
				new RuntimeField(3, "active", bool), new RuntimeField(4, "poison", n)));
		var params = List.of(new RuntimeFunctionParameter("income", 0, n),
				new RuntimeFunctionParameter("expenses", 1, n), new RuntimeFunctionParameter("repayments", 2, n),
				new RuntimeFunctionParameter("risk", 3, str), new RuntimeFunctionParameter("required", 4, n),
				new RuntimeFunctionParameter("active", 5, bool), new RuntimeFunctionParameter("poison", 6, n));
		var risk = new RuntimeLocalReference(3, str);
		var high = new RuntimeInExpression(risk,
				new RuntimeUnaryTests(false, false,
						List.of(new RuntimeExpressionUnaryTest(SparkSqlHybridTest.string("HIGH")),
								new RuntimeExpressionUnaryTest(SparkSqlHybridTest.string("DECLINE")))),
				bool);
		var low = new RuntimeInExpression(risk,
				new RuntimeUnaryTests(false, false,
						List.of(new RuntimeExpressionUnaryTest(SparkSqlHybridTest.string("LOW")),
								new RuntimeExpressionUnaryTest(SparkSqlHybridTest.string("VERY LOW")))),
				bool);
		var factor = new RuntimeConditionalExpression(high, SparkSqlHybridTest.number("0.6"),
				new RuntimeConditionalExpression(low, SparkSqlHybridTest.number("0.8"),
						new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL)),
						n),
				n);
		var disposable = new RuntimeBinaryExpression(RuntimeBinaryOperator.SUBTRACT, new RuntimeLocalReference(0, n),
				new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, new RuntimeLocalReference(1, n),
						new RuntimeLocalReference(2, n), n),
				n);
		var guard = new RuntimeConditionalExpression(
				new RuntimeLocalReference(5, bool), new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD,
						new RuntimeLocalReference(6, n), SparkSqlHybridTest.number("1"), n),
				SparkSqlHybridTest.number("0"), n);
		var predicate = new RuntimeBinaryExpression(
				RuntimeBinaryOperator.GREATER, new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY,
						new RuntimeLocalReference(8, n), new RuntimeLocalReference(9, n), n),
				new RuntimeLocalReference(4, n), bool);
		var ctx = new RuntimeContextExpression(List.of(new RuntimeContextEntry("unused", 7, guard),
				new RuntimeContextEntry("disposable", 8, disposable), new RuntimeContextEntry("factor", 9, factor),
				new RuntimeContextEntry("affordable", 10, predicate),
				new RuntimeContextEntry("__result__", 11, new RuntimeLocalReference(10, bool))),
				RuntimeType.scalar(RuntimeTypeKind.CONTEXT));
		var fn = new RuntimeFunctionDefinition(params, Optional.of(new RuntimePathExpression(ctx, "__result__", bool)),
				false, 12, RuntimeType.function(params.stream().map(RuntimeFunctionParameter::type).toList(), bool));
		var source = new RuntimeValueReference(0, inputType);
		var m = new RuntimePathExpression(source, "monthly", monthly);
		var args = List.of(new RuntimeNamedArgument("poison", new RuntimePathExpression(source, "poison", n)),
				new RuntimeNamedArgument("required", new RuntimePathExpression(source, "required", n)),
				new RuntimeNamedArgument("risk", new RuntimePathExpression(source, "risk", str)),
				new RuntimeNamedArgument("active", new RuntimePathExpression(source, "active", bool)),
				new RuntimeNamedArgument("income", new RuntimePathExpression(m, "income", n)),
				new RuntimeNamedArgument("expenses", new RuntimePathExpression(m, "expenses", n)),
				new RuntimeNamedArgument("repayments", new RuntimePathExpression(m, "repayments", n)));
		var call = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimeValueReference(1, SparkSqlHybridTest.ANY)), args, List.of(), bool);
		return new RuntimeModel(List.of(new RuntimeInput(5, 0, inputType)),
				List.of(new RuntimeDecision(10, 2, bool, List.of(5, 7), Optional.of(call))), List.of(new RuntimeBkm(7,
						1, SparkSqlHybridTest.ANY, List.of(), RuntimeFunctionKind.FEEL, Optional.of(fn))),
				List.of(7, 10), 3);
	}
	private static Map<String, Object> affordabilityInput(Object income, Object expenses, Object repayments,
			Object risk, Object required, Object active, Object poison) {
		var monthly = new HashMap<String, Object>();
		monthly.put("income", income);
		monthly.put("expenses", expenses);
		monthly.put("repayments", repayments);
		var input = new HashMap<String, Object>();
		input.put("monthly", monthly);
		input.put("risk", risk);
		input.put("required", required);
		input.put("active", active);
		input.put("poison", poison);
		return input;
	}
	@Test
	void scalarContextsPreserveExactAffordabilityAndLazyBranches() {
		var model = scalarAffordabilityFixture();
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(10).nativeSql()).as("%s", generated.capabilities()).isTrue();
		var zero = BigDecimal.ZERO;
		var cases = Arrays.asList(
				new Object[]{new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("20"), "LOW",
						new BigDecimal("50"), false},
				new Object[]{new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("20"), "HIGH",
						new BigDecimal("50"), false},
				new Object[]{new BigDecimal("9007199254740993"), new BigDecimal("9007199254740992"), zero, "LOW",
						new BigDecimal("0.75"), false},
				new Object[]{new BigDecimal("20.00000000000000000000000000000000000001"), new BigDecimal("20"), zero,
						"LOW", zero, false},
				new Object[]{BigDecimal.ONE, zero, zero, "UNKNOWN", zero, false},
				new Object[]{null, zero, zero, "LOW", zero, false},
				new Object[]{BigDecimal.ONE, zero, zero, null, zero, false},
				new Object[]{BigDecimal.ONE, zero, zero, "LOW", zero, null});
		for (var c : cases) {
			var inputs = Map.<Integer, Object>of(0, affordabilityInput(c[0], c[1], c[2], c[3], c[4], c[5], Double.NaN));
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThat(SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0)))
						.as("%s", inputs).isEqualTo(expected);
		}
	}
	@Test
	void scalarContextsEvaluateUnusedEntriesAndBothArithmeticOperands() {
		var model = scalarAffordabilityFixture();
		var generated = SparkSqlHybridTest.generate(model);
		for (var input : List.of(
				affordabilityInput(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, "UNKNOWN", BigDecimal.TEN, true,
						Double.NaN),
				affordabilityInput(null, Double.NaN, BigDecimal.ZERO, "UNKNOWN", BigDecimal.TEN, false, Double.NaN))) {
			var inputs = Map.<Integer, Object>of(0, input);
			assertThatThrownBy(() -> new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs))
					.isInstanceOf(RuntimeException.class);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).repartition(1).createOrReplaceTempView("hybrid_inputs");
			for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThatThrownBy(() -> spark.sql(generated.sqlFiles().get(file)).head())
						.isInstanceOf(Exception.class);
		}
	}

	@Test
	void scalarContextStringEqualityRetainsUtf16InputIdentity() {
		var str = SparkSqlHybridTest.STRING;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var equal = new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, new RuntimeLocalReference(0, str),
				SparkSqlHybridTest.string("?"), bool);
		var ctx = new RuntimeContextExpression(List.of(new RuntimeContextEntry("__result__", 1, equal)),
				RuntimeType.scalar(RuntimeTypeKind.CONTEXT));
		var fn = new RuntimeFunctionDefinition(List.of(new RuntimeFunctionParameter("value", 0, str)),
				Optional.of(new RuntimePathExpression(ctx, "__result__", bool)), false, 2,
				RuntimeType.function(List.of(str), bool));
		var call = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimeValueReference(1, SparkSqlHybridTest.ANY)), List.of(),
				List.of(new RuntimeValueReference(0, str)), bool);
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, str)),
				List.of(new RuntimeDecision(10, 2, bool, List.of(5, 7), Optional.of(call))), List.of(new RuntimeBkm(7,
						1, SparkSqlHybridTest.ANY, List.of(), RuntimeFunctionKind.FEEL, Optional.of(fn))),
				List.of(7, 10), 3);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		for (String value : Arrays.asList("?", String.valueOf((char) 0xD800), new String(Character.toChars(0x1F600)),
				"", null)) {
			var inputs = new HashMap<Integer, Object>();
			inputs.put(0, value);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThat(SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0)))
						.isEqualTo(expected);
		}
	}

	@Test
	void scalarInTestsEvaluateCandidatesForWildcardAndEmptyTests() {
		var n = SparkSqlHybridTest.NUMBER;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		for (boolean wildcard : List.of(true, false)) {
			var candidate = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, new RuntimeLocalReference(0, n),
					SparkSqlHybridTest.number("1"), n);
			var test = new RuntimeInExpression(candidate, new RuntimeUnaryTests(false, wildcard, List.of()), bool);
			var ctx = new RuntimeContextExpression(List.of(new RuntimeContextEntry("__result__", 1, test)),
					RuntimeType.scalar(RuntimeTypeKind.CONTEXT));
			var fn = new RuntimeFunctionDefinition(List.of(new RuntimeFunctionParameter("value", 0, n)),
					Optional.of(new RuntimePathExpression(ctx, "__result__", bool)), false, 2,
					RuntimeType.function(List.of(n), bool));
			var call = new RuntimeInvocationExpression(Optional.empty(),
					Optional.of(new RuntimeValueReference(1, SparkSqlHybridTest.ANY)), List.of(),
					List.of(new RuntimeValueReference(0, n)), bool);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, n)),
					List.of(new RuntimeDecision(10, 2, bool, List.of(5, 7), Optional.of(call))),
					List.of(new RuntimeBkm(7, 1, SparkSqlHybridTest.ANY, List.of(), RuntimeFunctionKind.FEEL,
							Optional.of(fn))),
					List.of(7, 10), 3);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			for (Object value : Arrays.asList(BigDecimal.ONE, null, Double.NaN)) {
				var inputs = new HashMap<Integer, Object>();
				inputs.put(0, value);
				spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
						generated.inputSchema()).repartition(1).createOrReplaceTempView("hybrid_inputs");
				if (value == null || value instanceof BigDecimal) {
					Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
					for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
						assertThat(SparkSqlFeelValueCodec
								.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0)))
								.isEqualTo(expected);
				} else {
					assertThatThrownBy(() -> new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs))
							.isInstanceOf(RuntimeException.class);
					for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
						assertThatThrownBy(() -> spark.sql(generated.sqlFiles().get(file)).head())
								.isInstanceOf(Exception.class);
				}
			}
		}
	}

	private static RuntimeModel numericScalarContextFixture(boolean named, String field) {
		var n = SparkSqlHybridTest.NUMBER;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var params = List.of(new RuntimeFunctionParameter("amount", 0, n),
				new RuntimeFunctionParameter("enabled", 1, bool), new RuntimeFunctionParameter("unused", 2, n));
		var arithmetic = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD,
				new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, new RuntimeLocalReference(0, n),
						SparkSqlHybridTest.number("1.25"), n),
				SparkSqlHybridTest.number("0.0000000000000000000000000000000000000001"), n);
		var result = new RuntimeConditionalExpression(new RuntimeLocalReference(1, bool), arithmetic,
				new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL)), n);
		var unused = new RuntimeConditionalExpression(new RuntimeLocalReference(1, bool),
				SparkSqlHybridTest.number("0"), new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD,
						new RuntimeLocalReference(2, n), SparkSqlHybridTest.number("1"), n),
				n);
		var ctx = new RuntimeContextExpression(
				List.of(new RuntimeContextEntry("check", 3, unused), new RuntimeContextEntry(field, 4, result)),
				RuntimeType.scalar(RuntimeTypeKind.CONTEXT));
		var fn = new RuntimeFunctionDefinition(params, Optional.of(new RuntimePathExpression(ctx, field, n)), false, 5,
				RuntimeType.function(params.stream().map(RuntimeFunctionParameter::type).toList(), n));
		var args = List.<RuntimeExpression>of(new RuntimeValueReference(0, n), new RuntimeValueReference(1, bool),
				new RuntimeValueReference(2, n));
		var call = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimeValueReference(3, SparkSqlHybridTest.ANY)),
				named
						? List.of(new RuntimeNamedArgument("unused", args.get(2)),
								new RuntimeNamedArgument("enabled", args.get(1)),
								new RuntimeNamedArgument("amount", args.get(0)))
						: List.of(),
				named ? List.of() : args, n);
		return new RuntimeModel(
				List.of(new RuntimeInput(5, 0, n), new RuntimeInput(6, 1, bool), new RuntimeInput(7, 2, n)),
				List.of(new RuntimeDecision(10, 4, n, List.of(5, 6, 7, 8), Optional.of(call))),
				List.of(new RuntimeBkm(8, 3, SparkSqlHybridTest.ANY, List.of(), RuntimeFunctionKind.FEEL,
						Optional.of(fn))),
				List.of(8, 10), 5);
	}
	@Test
	void numericScalarContextsReturnExactNumbersAcrossBindingsAndNullBranches() {
		for (boolean named : List.of(false, true)) {
			var model = numericScalarContextFixture(named, named ? "renamed result" : "metric");
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).as("%s", generated.capabilities()).isTrue();
			for (Object[] sample : List.of(new Object[]{new BigDecimal("9007199254740993"), true, Double.NaN},
					new Object[]{new BigDecimal("-20.00000000000000000000000000000000000001"), true, Double.NaN},
					new Object[]{null, true, Double.NaN}, new Object[]{Double.NaN, false, BigDecimal.ZERO},
					new Object[]{Double.NaN, null, BigDecimal.ZERO})) {
				var inputs = new HashMap<Integer, Object>();
				inputs.put(0, sample[0]);
				inputs.put(1, sample[1]);
				inputs.put(2, sample[2]);
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
				spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
						generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				for (var file : List.of("Decision_4.sql", "AllDecisions.sql")) {
					Object actual = SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0));
					if (expected == null)
						assertThat(actual).isNull();
					else
						assertThat((BigDecimal) actual).isEqualByComparingTo((BigDecimal) expected);
				}
			}
		}
	}
	@Test
	void numericScalarContextsRetainUnusedEntryErrorsAndConsumerBoundary() {
		var model = numericScalarContextFixture(true, "metric");
		var generated = SparkSqlHybridTest.generate(model);
		var inputs = Map.<Integer, Object>of(0, BigDecimal.ONE, 1, false, 2, Double.NaN);
		assertThatThrownBy(() -> new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs))
				.isInstanceOf(RuntimeException.class);
		spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
				generated.inputSchema()).repartition(1).createOrReplaceTempView("hybrid_inputs");
		for (var file : List.of("Decision_4.sql", "AllDecisions.sql"))
			assertThatThrownBy(() -> spark.sql(generated.sqlFiles().get(file)).head()).isInstanceOf(Exception.class);
		var decisions = new ArrayList<>(model.decisions());
		decisions.add(new RuntimeDecision(11, 5, SparkSqlHybridTest.NUMBER, List.of(10),
				Optional.of(new RuntimeFunctionCall("abs",
						List.of(new RuntimeValueReference(4, SparkSqlHybridTest.NUMBER)), SparkSqlHybridTest.NUMBER))));
		var consumer = new RuntimeModel(model.inputs(), decisions, model.businessKnowledgeModels(), List.of(8, 10, 11),
				6);
		var consumerSql = SparkSqlHybridTest.generate(consumer);
		assertThat(consumerSql.capabilities().get(10).nativeSql()).isTrue();
		assertThat(consumerSql.capabilities().get(11).nativeSql()).isFalse();
		assertThat(consumerSql.capabilities().get(11).reasons())
				.contains("Exact scalar numeric dependency requires a lossless consumer plan");
		consumerSql.registerUdfs(spark);
		for (var value : Arrays.asList(new BigDecimal("9007199254740993"),
				new BigDecimal("-20.00000000000000000000000000000000000001"), null)) {
			var valid = new HashMap<Integer, Object>();
			valid.put(0, value);
			valid.put(1, true);
			valid.put(2, Double.NaN);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(consumer, valid, consumerSql.inputSchema())),
					consumerSql.inputSchema()).repartition(2).createOrReplaceTempView("hybrid_inputs");
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(consumer, valid).decisionValue(11);
			for (var file : List.of("Decision_5.sql", "AllDecisions.sql")) {
				var row = spark.sql(consumerSql.sqlFiles().get(file)).head();
				int index = file.equals("AllDecisions.sql") ? 1 : 0;
				if (expected == null)
					assertThat(row.isNullAt(index)).isTrue();
				else
					assertThat((BigDecimal) SparkSqlFeelValueCodec.fromSpark(row.get(index)))
							.isEqualByComparingTo((BigDecimal) expected);
			}
		}
		var invalid = Map.<Integer, Object>of(0, Double.NaN, 1, true, 2, BigDecimal.ZERO);
		assertThatThrownBy(() -> new io.finmsg.dmn.runtime.DmnRuntime().evaluate(consumer, invalid))
				.isInstanceOf(RuntimeException.class);
		spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(consumer, invalid, consumerSql.inputSchema())),
				consumerSql.inputSchema()).repartition(2).createOrReplaceTempView("hybrid_inputs");
		assertThatThrownBy(() -> spark.sql(consumerSql.sqlFiles().get("Decision_5.sql")).head())
				.isInstanceOf(Exception.class);

	}

	@Test
	void fallbackTransportPreservesNumericListsAndNestedInputFields() {
		var n = SparkSqlHybridTest.NUMBER;
		var list = RuntimeType.element(RuntimeTypeKind.LIST, n);
		for (boolean nested : List.of(false, true)) {
			var inputType = nested
					? RuntimeType.contextFields(List.of(new RuntimeField(0, "amounts", list),
							new RuntimeField(1, "label", SparkSqlHybridTest.STRING)))
					: list;
			RuntimeExpression source = new RuntimeValueReference(0, inputType);
			if (nested)
				source = new RuntimePathExpression(source, "amounts", list);
			var loop = new RuntimeForExpression(List.of(new RuntimeIteration(0, source, Optional.empty())),
					new RuntimeLocalReference(0, n), 1, list);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, inputType)),
					List.of(new RuntimeDecision(10, 1, list, List.of(5), Optional.of(loop), Optional.empty(), 2)),
					List.of(), List.of(10), 2);
			var options = DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true,
					Map.of(0, "__dmn_fallback_input_0", 1, "renamed output"));
			var generated = new DmnSparkSqlGenerator().generate(SparkSqlHybridTest.optimized(model), options);
			assertThat(generated.capabilities().get(10).nativeSql()).isFalse();
			assertThat(generated.inputSchema().fieldNames()).contains("__dmn_fallback_input_0_");
			generated.registerUdfs(spark);
			for (var values : List.of(
					List.of(new BigDecimal("9007199254740993"),
							new BigDecimal("0.0000000000000000000000000000000000000001"), new BigDecimal("-1.2300")),
					List.<BigDecimal>of())) {
				var inputs = Map.<Integer, Object>of(0, nested ? Map.of("amounts", values, "label", "x") : values);
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
				spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
						generated.inputSchema()).repartition(2).createOrReplaceTempView("hybrid_inputs");
				for (var file : List.of("Decision_1.sql", "AllDecisions.sql"))
					assertThat(
							SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0)))
							.isEqualTo(expected);
			}
		}
	}

	private static RuntimeModel rankedContextFixture(int size, boolean descending, String scoreName) {
		var n = SparkSqlHybridTest.NUMBER;
		var str = SparkSqlHybridTest.STRING;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var sourceType = RuntimeType
				.contextFields(List.of(new RuntimeField(0, "label", str), new RuntimeField(1, "fee", n)));
		var outputType = RuntimeType
				.contextFields(List.of(new RuntimeField(0, "label", str), new RuntimeField(1, scoreName, n)));
		var sourceList = RuntimeType.element(RuntimeTypeKind.LIST, sourceType);
		var outputList = RuntimeType.element(RuntimeTypeKind.LIST, outputType);
		var rows = new ArrayList<List<RuntimeExpression>>();
		for (int i = 0; i < size; i++)
			rows.add(List.of(SparkSqlHybridTest.string("row '" + i + " " + new String(Character.toChars(0x1F600))),
					SparkSqlHybridTest.number(
							i % 3 == 0 ? "0.0000000000000000000000000000000000000001" : Integer.toString(i % 3))));
		var relation = new RuntimeRelationExpression(
				List.of(new RuntimeRelationColumn("label", str), new RuntimeRelationColumn("fee", n)), rows,
				sourceList);
		var helper = new RuntimeFunctionDefinition(List.of(new RuntimeFunctionParameter("base", 0, n)),
				Optional.of(new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, new RuntimeLocalReference(0, n),
						SparkSqlHybridTest.number("1.25"), n)),
				false, 1, RuntimeType.function(List.of(n), n));
		var score = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimeValueReference(3, SparkSqlHybridTest.ANY)), List.of(),
				List.of(new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, new RuntimeLocalReference(1, n),
						new RuntimePathExpression(new RuntimeLocalReference(0, sourceType), "fee", n), n)),
				n);
		var body = new RuntimeContextExpression(List.of(
				new RuntimeContextEntry("label", 2,
						new RuntimePathExpression(new RuntimeLocalReference(0, sourceType), "label", str)),
				new RuntimeContextEntry(scoreName, 3, score)), outputType);
		var fn = new RuntimeFunctionDefinition(
				List.of(new RuntimeFunctionParameter("record", 0, sourceType),
						new RuntimeFunctionParameter("amount", 1, n)),
				Optional.of(body), false, 4, RuntimeType.function(List.of(sourceType, n), outputType));
		var call = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimeValueReference(2, SparkSqlHybridTest.ANY)),
				descending
						? List.of(new RuntimeNamedArgument("amount", new RuntimeValueReference(0, n)),
								new RuntimeNamedArgument("record", new RuntimeLocalReference(0, sourceType)))
						: List.of(),
				descending
						? List.of()
						: List.of(new RuntimeLocalReference(0, sourceType), new RuntimeValueReference(0, n)),
				outputType);
		var loop = new RuntimeForExpression(
				List.of(new RuntimeIteration(0, new RuntimeValueReference(1, sourceList), Optional.empty())), call, -1,
				outputList);
		var cmp = new RuntimeFunctionDefinition(
				List.of(new RuntimeFunctionParameter("a", 0, SparkSqlHybridTest.ANY),
						new RuntimeFunctionParameter("b", 1, SparkSqlHybridTest.ANY)),
				Optional.of(new RuntimeBinaryExpression(
						descending ? RuntimeBinaryOperator.GREATER : RuntimeBinaryOperator.LESS,
						new RuntimePathExpression(new RuntimeLocalReference(0, SparkSqlHybridTest.ANY), scoreName,
								SparkSqlHybridTest.ANY),
						new RuntimePathExpression(new RuntimeLocalReference(1, SparkSqlHybridTest.ANY), scoreName,
								SparkSqlHybridTest.ANY),
						bool)),
				false, 2, RuntimeType.function(List.of(SparkSqlHybridTest.ANY, SparkSqlHybridTest.ANY), bool));
		var sort = new RuntimeInvocationExpression(Optional.of("sort"), Optional.empty(), List.of(),
				List.of(new RuntimeLocalReference(1, outputList), cmp), outputList);
		var root = new RuntimeContextExpression(
				List.of(new RuntimeContextEntry("original", 1, loop), new RuntimeContextEntry("ranked", 2, sort)),
				RuntimeType.contextFields(List.of(new RuntimeField(0, "original", outputList),
						new RuntimeField(1, "ranked", outputList))));
		return new RuntimeModel(List.of(new RuntimeInput(5, 0, n)),
				List.of(new RuntimeDecision(10, 1, sourceList, List.of(), Optional.of(relation)),
						new RuntimeDecision(30, 4, root.type(), List.of(5, 10, 20), Optional.of(root), Optional.empty(),
								3)),
				List.of(new RuntimeBkm(20, 2, SparkSqlHybridTest.ANY, List.of(21), RuntimeFunctionKind.FEEL,
						Optional.of(fn)),
						new RuntimeBkm(21, 3, SparkSqlHybridTest.ANY, List.of(), RuntimeFunctionKind.FEEL,
								Optional.of(helper))),
				List.of(10, 21, 20, 30), 5);
	}
	@Test
	void rankedContextLoopsPreserveExactMetricsStableTiesAndVariations() {
		for (int size : List.of(0, 1, 5))
			for (boolean descending : List.of(false, true)) {
				var model = rankedContextFixture(size, descending, descending ? "renamed score" : "score");
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(30).nativeSql()).as("%s", generated.capabilities()).isTrue();
				for (String udf : generated.udfs().keySet())
					assertThat(generated.sqlFiles().get("Decision_4.sql")).doesNotContain(udf + "(");
				generated.registerUdfs(spark);
				for (Object amount : Arrays.asList(new BigDecimal("9007199254740993.0000000000000000000001"), null)) {
					var inputs = new HashMap<Integer, Object>();
					inputs.put(0, amount);
					Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(30);
					spark.createDataFrame(
							List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
							generated.inputSchema()).repartition(2).createOrReplaceTempView("hybrid_inputs");
					for (var file : List.of("Decision_4.sql", "AllDecisions.sql")) {
						var row = spark.sql(generated.sqlFiles().get(file)).head();
						Object actual = SparkSqlFeelValueCodec
								.fromSpark(row.get(file.equals("AllDecisions.sql") ? 1 : 0));
						assertThat(actual).usingRecursiveComparison()
								.withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(expected);
					}
				}
			}
	}

	@Test
	void rankedContextPlansRetainFallbackForCoercingComparatorsAndAliasedSlots() {
		var model = rankedContextFixture(3, false, "score");
		var decision = model.decisions().getLast();
		var context = (RuntimeContextExpression) decision.expression().orElseThrow();
		var sort = (RuntimeInvocationExpression) context.entries().getLast().expression();
		var fn = (RuntimeFunctionDefinition) sort.positionalArguments().getLast();
		for (boolean alias : List.of(false, true)) {
			var params = List.of(new RuntimeFunctionParameter("a", 0, SparkSqlHybridTest.NUMBER),
					fn.parameters().getLast());
			var badFn = new RuntimeFunctionDefinition(params, fn.body(), false, 2,
					RuntimeType.function(params.stream().map(RuntimeFunctionParameter::type).toList(),
							RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)));
			var replacement = alias
					? sort
					: new RuntimeInvocationExpression(sort.function(), sort.target(), List.of(),
							List.of(sort.positionalArguments().getFirst(), badFn), sort.type());
			var root = new RuntimeContextExpression(List.of(context.entries().getFirst(),
					new RuntimeContextEntry("ranked", alias ? 1 : 2, replacement)), context.type());
			var changed = new RuntimeDecision(decision.id(), decision.resultSlot(), decision.type(),
					decision.dependencies(), Optional.of(root), Optional.empty(), decision.localSlotCount());
			var variant = new RuntimeModel(model.inputs(), List.of(model.decisions().getFirst(), changed),
					model.businessKnowledgeModels(), model.evaluationOrder(), model.valueSlotCount());
			assertThat(SparkSqlRankedContextLoop.supports(variant, changed)).isFalse();
			assertThat(SparkSqlHybridTest.generate(variant).capabilities().get(decision.id()).nativeSql()).isFalse();
		}
		var consumer = new RuntimeDecision(40, 5, SparkSqlHybridTest.ANY, List.of(30),
				Optional.of(new RuntimeValueReference(4, decision.type())));
		var variant = new RuntimeModel(model.inputs(), List.of(model.decisions().getFirst(), decision, consumer),
				model.businessKnowledgeModels(), List.of(10, 21, 20, 30, 40), 6);
		var generated = SparkSqlHybridTest.generate(variant);
		assertThat(generated.capabilities().get(30).nativeSql()).isTrue();
		assertThat(generated.capabilities().get(40).nativeSql()).isFalse();
		assertThat(generated.capabilities().get(40).reasons())
				.contains("Structured ranking dependency requires a lossless consumer plan");
	}

	private static RuntimeModel numericContextLoopFixture(RuntimeType context, RuntimeExpression body) {
		var n = SparkSqlHybridTest.NUMBER;
		var list = RuntimeType.element(RuntimeTypeKind.LIST, context);
		var result = RuntimeType.element(RuntimeTypeKind.LIST, n);
		var fn = new RuntimeFunctionDefinition(List.of(new RuntimeFunctionParameter("item", 0, context)),
				Optional.of(body), false, 1, RuntimeType.function(List.of(context), n));
		var loop = new RuntimeForExpression(
				List.of(new RuntimeIteration(0, new RuntimeValueReference(0, list), Optional.empty())),
				new RuntimeInvocationExpression(Optional.empty(),
						Optional.of(new RuntimeValueReference(1, SparkSqlHybridTest.ANY)), List.of(),
						List.of(new RuntimeLocalReference(0, context)), SparkSqlHybridTest.ANY),
				-1, RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY));
		return new RuntimeModel(List.of(new RuntimeInput(5, 0, list)),
				List.of(new RuntimeDecision(10, 2, result, List.of(5, 7), Optional.of(loop), Optional.empty(), 1)),
				List.of(new RuntimeBkm(7, 1, SparkSqlHybridTest.ANY, List.of(), RuntimeFunctionKind.FEEL,
						Optional.of(fn))),
				List.of(7, 10), 3);
	}
	private static RuntimeModel paymentLoopFixture() {
		var n = SparkSqlHybridTest.NUMBER;
		var context = RuntimeType.contextFields(List.of(new RuntimeField(0, "amount", n),
				new RuntimeField(1, "rate", n), new RuntimeField(2, "term", n)));
		var item = new RuntimeLocalReference(0, context);
		var amount = new RuntimePathExpression(item, "amount", n);
		var rate = new RuntimePathExpression(item, "rate", n);
		var term = new RuntimePathExpression(item, "term", n);
		var twelve = SparkSqlHybridTest.number("12");
		var one = SparkSqlHybridTest.number("1");
		var monthly = new RuntimeBinaryExpression(RuntimeBinaryOperator.DIVIDE, rate, twelve, n);
		var numerator = new RuntimeBinaryExpression(RuntimeBinaryOperator.DIVIDE,
				new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, amount, rate, n), twelve, n);
		var power = new RuntimeBinaryExpression(RuntimeBinaryOperator.POWER,
				new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, one, monthly, n),
				new RuntimeUnaryExpression(RuntimeUnaryOperator.NEGATE, term, n), n);
		return numericContextLoopFixture(context, new RuntimeBinaryExpression(RuntimeBinaryOperator.DIVIDE, numerator,
				new RuntimeBinaryExpression(RuntimeBinaryOperator.SUBTRACT, one, power, n), n));
	}
	@Test
	void nativeNumericContextLoopsPreservePaymentArithmeticAndInputOrder() {
		var model = paymentLoopFixture();
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(10).nativeSql()).as("%s", generated.capabilities()).isTrue();
		var loan1 = Map.<String, Object>of("amount", new BigDecimal("200000"), "rate", new BigDecimal(".041"), "term",
				new BigDecimal("360"));
		var loan2 = Map.<String, Object>of("amount", new BigDecimal("20000"), "rate", new BigDecimal(".049"), "term",
				new BigDecimal("60"));
		for (Object value : Arrays.asList(List.of(loan1, loan2), List.of(loan2, loan1), List.of(), null)) {
			var inputs = new HashMap<Integer, Object>();
			inputs.put(0, value);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThat(SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0)))
						.as("%s", value).isEqualTo(expected);
		}
	}
	private static RuntimeModel powerLoopFixture() {
		var n = SparkSqlHybridTest.NUMBER;
		var context = RuntimeType
				.contextFields(List.of(new RuntimeField(0, "base", n), new RuntimeField(1, "exponent", n)));
		var item = new RuntimeLocalReference(0, context);
		return numericContextLoopFixture(context, new RuntimeBinaryExpression(RuntimeBinaryOperator.POWER,
				new RuntimePathExpression(item, "base", n), new RuntimePathExpression(item, "exponent", n), n));
	}
	@Test
	void nativeNumericContextLoopPowerMatchesJavaMathDecimalConversion() {
		var model = powerLoopFixture();
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		var rows = new ArrayList<Map<String, Object>>();
		for (var pair : List.of(new String[]{"2", "-3"}, new String[]{"1.0000000000000000001", "123"},
				new String[]{"-2", "3"}, new String[]{"0", "2"}, new String[]{"1E-80", "2"}, new String[]{"1E80", "2"},
				new String[]{"2.5", "0.7"}, new String[]{"-1E-300", "3"}, new String[]{"0", "0"}))
			rows.add(Map.of("base", new BigDecimal(pair[0]), "exponent", new BigDecimal(pair[1])));
		var inputs = Map.<Integer, Object>of(0, rows);
		Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
		spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
				generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
		for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
			assertThat(SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0)))
					.isEqualTo(expected);
	}

	@Test
	void nativeNumericContextLoopsPreservePowerAndNullResultErrors() {
		var power = powerLoopFixture();
		var generated = SparkSqlHybridTest.generate(power);
		var invalidRows = new ArrayList<Map<String, Object>>();
		for (var pair : List.of(new Object[]{new BigDecimal("1E200"), new BigDecimal("2")},
				new Object[]{new BigDecimal("-1"), new BigDecimal("0.5")},
				new Object[]{BigDecimal.ZERO, new BigDecimal("-1")}, new Object[]{Double.NaN, BigDecimal.ONE},
				new Object[]{BigDecimal.ONE, Double.NaN}))
			invalidRows.add(Map.of("base", pair[0], "exponent", pair[1]));
		for (var row : invalidRows) {
			var inputs = Map.<Integer, Object>of(0, List.of(row));
			assertThatThrownBy(() -> new io.finmsg.dmn.runtime.DmnRuntime().evaluate(power, inputs))
					.isInstanceOf(RuntimeException.class);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(power, inputs, generated.inputSchema())),
					generated.inputSchema()).repartition(1).createOrReplaceTempView("hybrid_inputs");
			for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThatThrownBy(() -> spark.sql(generated.sqlFiles().get(file)).head())
						.isInstanceOf(Exception.class);
		}
		var payment = paymentLoopFixture();
		var paymentSql = SparkSqlHybridTest.generate(payment);
		for (Object row : Arrays
				.asList(Map.of("amount", BigDecimal.ONE, "rate", BigDecimal.ZERO, "term", BigDecimal.ONE), null)) {
			var inputs = Map.<Integer, Object>of(0, Collections.singletonList(row));
			assertThatThrownBy(() -> new io.finmsg.dmn.runtime.DmnRuntime().evaluate(payment, inputs))
					.isInstanceOf(RuntimeException.class);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(payment, inputs, paymentSql.inputSchema())),
					paymentSql.inputSchema()).repartition(1).createOrReplaceTempView("hybrid_inputs");
			for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThatThrownBy(() -> spark.sql(paymentSql.sqlFiles().get(file)).head())
						.isInstanceOf(Exception.class);
		}
		for (Object row : List.of(Map.of("amount", BigDecimal.ONE, "term", BigDecimal.ONE))) {
			var inputs = Map.<Integer, Object>of(0, Collections.singletonList(row));
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(payment, inputs).decisionValue(10);
			assertThat(expected).isNull();
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(payment, inputs, paymentSql.inputSchema())),
					paymentSql.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThat(SparkSqlFeelValueCodec.fromSpark(spark.sql(paymentSql.sqlFiles().get(file)).head().get(0)))
						.isEqualTo(expected);
		}

	}

	private static RuntimeModel factorLoopFixture() {
		return factorLoopFixture("0");
	}
	private static RuntimeModel factorLoopFixture(String roundingScale) {
		var n = SparkSqlHybridTest.NUMBER;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var list = RuntimeType.element(RuntimeTypeKind.LIST, n);
		var resultType = RuntimeType.element(RuntimeTypeKind.LIST, bool);
		var quotient = new RuntimeBinaryExpression(RuntimeBinaryOperator.DIVIDE, new RuntimeLocalReference(0, n),
				new RuntimeLocalReference(1, n), n);
		var rounded = new RuntimeInvocationExpression(Optional.of("decimal"), Optional.empty(), List.of(),
				List.of(quotient, SparkSqlHybridTest.number(roundingScale)), n);
		var body = new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, quotient, rounded, bool);
		var fn = new RuntimeFunctionDefinition(
				List.of(new RuntimeFunctionParameter("value", 0, n), new RuntimeFunctionParameter("factor", 1, n)),
				Optional.of(body), false, 2, RuntimeType.function(List.of(n, n), bool));
		var loop = new RuntimeForExpression(
				List.of(new RuntimeIteration(0, new RuntimeValueReference(1, list), Optional.empty())),
				new RuntimeInvocationExpression(Optional.empty(),
						Optional.of(new RuntimeValueReference(2, SparkSqlHybridTest.ANY)), List.of(),
						List.of(new RuntimeValueReference(0, n), new RuntimeLocalReference(0, n)),
						SparkSqlHybridTest.ANY),
				-1, RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY));
		return new RuntimeModel(List.of(new RuntimeInput(5, 0, n), new RuntimeInput(6, 1, list)),
				List.of(new RuntimeDecision(10, 3, resultType, List.of(5, 6, 7), Optional.of(loop), Optional.empty(),
						1)),
				List.of(new RuntimeBkm(7, 2, SparkSqlHybridTest.ANY, List.of(), RuntimeFunctionKind.FEEL,
						Optional.of(fn))),
				List.of(7, 10), 4);
	}
	@Test
	void nativeFactorLoopsUseRoundedQuotientsAndPreserveListOrder() {
		var model = factorLoopFixture();
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(10).nativeSql()).as("%s", generated.capabilities()).isTrue();
		var cases = Arrays.asList(
				new Object[]{new BigDecimal("12"),
						Arrays.asList(new BigDecimal("2"), new BigDecimal("5"), BigDecimal.ZERO, null,
								new BigDecimal("-3"))},
				new Object[]{new BigDecimal("0.3"), List.of(new BigDecimal("0.1"), new BigDecimal("0.2"))},
				new Object[]{new BigDecimal("10000000000000000000000000000000001"),
						List.of(new BigDecimal("2"), new BigDecimal("3"))},
				new Object[]{null, Arrays.asList(BigDecimal.ONE, null, BigDecimal.ZERO)},
				new Object[]{Double.NaN, List.of()}, new Object[]{Double.NaN, List.of(BigDecimal.ZERO)},
				new Object[]{BigDecimal.ONE, null});
		for (var pair : cases) {
			var inputs = new HashMap<Integer, Object>();
			inputs.put(0, pair[0]);
			inputs.put(1, pair[1]);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			for (var file : List.of("Decision_3.sql", "AllDecisions.sql"))
				assertThat(SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0)))
						.as("%s via %s", inputs, file).isEqualTo(expected);
		}
	}

	@Test
	void factorLoopValidationPreservesFailuresAndRejectsOtherRoundScales() {
		var model = factorLoopFixture();
		var generated = SparkSqlHybridTest.generate(model);
		var minimum = new BigDecimal(java.math.BigInteger.ONE, Integer.MIN_VALUE);
		var maximum = new BigDecimal(java.math.BigInteger.ONE, Integer.MAX_VALUE);
		for (var pair : List.of(new Object[]{Double.NaN, BigDecimal.ONE}, new Object[]{BigDecimal.ONE, Double.NaN},
				new Object[]{minimum, BigDecimal.ONE}, new Object[]{maximum, BigDecimal.ONE})) {
			var inputs = Map.<Integer, Object>of(0, pair[0], 1, List.of(pair[1]));
			assertThatThrownBy(() -> new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs))
					.isInstanceOf(RuntimeException.class);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).repartition(1).createOrReplaceTempView("hybrid_inputs");
			for (var file : List.of("Decision_3.sql", "AllDecisions.sql"))
				assertThatThrownBy(() -> spark.sql(generated.sqlFiles().get(file)).head())
						.isInstanceOf(Exception.class);
		}
		var other = factorLoopFixture("1");
		var fallback = SparkSqlHybridTest.generate(other);
		assertThat(fallback.capabilities().get(10).nativeSql()).isFalse();
		fallback.registerUdfs(spark);
		var inputs = Map.<Integer, Object>of(0, new BigDecimal("1"), 1,
				List.of(new BigDecimal("2"), new BigDecimal("3")));
		spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(other, inputs, fallback.inputSchema())),
				fallback.inputSchema()).createOrReplaceTempView("hybrid_inputs");
		Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(other, inputs).decisionValue(10);
		for (var file : List.of("Decision_3.sql", "AllDecisions.sql"))
			assertThat(SparkSqlFeelValueCodec.fromSpark(spark.sql(fallback.sqlFiles().get(file)).head().get(0)))
					.isEqualTo(expected);
	}

	private static RuntimeModel exactDivisionFixture() {
		var n = SparkSqlHybridTest.NUMBER;
		var type = RuntimeType.contextFields(List.of(new RuntimeField(0, "quotient", n)));
		var divide = new RuntimeBinaryExpression(RuntimeBinaryOperator.DIVIDE, new RuntimeValueReference(0, n),
				new RuntimeValueReference(1, n), n);
		var expression = new RuntimeContextExpression(List.of(new RuntimeContextEntry("quotient", 0, divide)), type);
		return new RuntimeModel(List.of(new RuntimeInput(5, 0, n), new RuntimeInput(6, 1, n)),
				List.of(new RuntimeDecision(10, 2, type, List.of(5, 6), Optional.of(expression), Optional.empty(), 1)),
				List.of(), List.of(10), 3);
	}
	@Test
	void exactDivisionMatchesDecimal128RoundingScaleAndNulls() {
		var model = exactDivisionFixture();
		var type = model.decisions().getFirst().type();
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		String[][] pairs = {{"10", "5"}, {"10.00", "5"}, {"1", "3"}, {"2", "3"}, {"-1", "7"}, {"1", "-7"}, {"-1", "-7"},
				{"0.00", "3.0000"}, {"1", "0.00"}, {null, "1"}, {"1", null}, {null, null}, {"1E80", "3E-80"},
				{"1E-80", "3E80"}, {"9007199254740993", "9007199254740992"},
				{"12345678901234567890123456789012345", "1"}, {"12345678901234567890123456789012355", "1"},
				{"99999999999999999999999999999999995", "1"}, {"1.0000000000000000000000000000000000000000", "1"},
				{"1", "8"}, {"1000", "2"}, {"1000", "0.02"}, {"1", "9999"},
				{"10000000000000000000000000000000005", "10"}};
		for (String[] pair : pairs) {
			Map<Integer, Object> inputs = new HashMap<>();
			inputs.put(0, pair[0] == null ? null : new BigDecimal(pair[0]));
			inputs.put(1, pair[1] == null ? null : new BigDecimal(pair[1]));
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
			for (String file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThat(
						SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0), type))
						.as("%s / %s via %s", pair[0], pair[1], file).isEqualTo(expected);
		}
	}

	@Test
	void exactDivisionRetainsInvalidNumberOrderAndScaleOverflow() {
		var model = exactDivisionFixture();
		var type = model.decisions().getFirst().type();
		var generated = SparkSqlHybridTest.generate(model);
		var maximum = new BigDecimal(java.math.BigInteger.ONE, Integer.MAX_VALUE);
		var minimum = new BigDecimal(java.math.BigInteger.ONE, Integer.MIN_VALUE);
		var valid = Arrays.asList(new Object[]{Double.NaN, BigDecimal.ZERO}, new Object[]{null, Double.NaN},
				new Object[]{Double.NaN, null},
				new Object[]{new BigDecimal(java.math.BigInteger.ZERO, Integer.MAX_VALUE), minimum},
				new Object[]{new BigDecimal(java.math.BigInteger.ZERO, Integer.MIN_VALUE), maximum},
				new Object[]{minimum, BigDecimal.ONE});
		for (var pair : valid) {
			Map<Integer, Object> inputs = new HashMap<>();
			inputs.put(0, pair[0]);
			inputs.put(1, pair[1]);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).repartition(1).createOrReplaceTempView("hybrid_inputs");
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
			for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThat(
						SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0), type))
						.as("%s", inputs).isEqualTo(expected);
		}
		for (var pair : List.of(new Object[]{Double.NaN, BigDecimal.ONE}, new Object[]{BigDecimal.ONE, Double.NaN},
				new Object[]{maximum, minimum}, new Object[]{minimum, maximum},
				new Object[]{maximum, BigDecimal.ONE})) {
			var inputs = Map.<Integer, Object>of(0, pair[0], 1, pair[1]);
			assertThatThrownBy(() -> new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs))
					.isInstanceOf(RuntimeException.class);
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).repartition(1).createOrReplaceTempView("hybrid_inputs");
			for (var file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThatThrownBy(() -> spark.sql(generated.sqlFiles().get(file)).head())
						.isInstanceOf(Exception.class);
		}
	}

	@Test
	void exactNumericContextsPreserveAdditionSubtractionAndRangeBranches() {
		var n = SparkSqlHybridTest.NUMBER;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var a = new RuntimeValueReference(0, n);
		var b = new RuntimeValueReference(1, n);
		var sum = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, a, b, n);
		var difference = new RuntimeBinaryExpression(RuntimeBinaryOperator.SUBTRACT, a, b, n);
		var product = new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, a, b, n);
		var range = new RuntimeRangeExpression(Optional.of(SparkSqlHybridTest.number("-1")),
				Optional.of(SparkSqlHybridTest.number("1")), RuntimeRangeBoundary.CLOSED, RuntimeRangeBoundary.CLOSED,
				RuntimeType.scalar(RuntimeTypeKind.ANY));
		var inside = new RuntimeInExpression(difference,
				new RuntimeUnaryTests(false, false, List.of(new RuntimeRangeUnaryTest(range))), bool);
		var chosen = new RuntimeConditionalExpression(inside, SparkSqlHybridTest.number("1"),
				SparkSqlHybridTest.number("0"), n);
		var type = RuntimeType.contextFields(List.of(new RuntimeField(0, "product", n),
				new RuntimeField(1, "difference", n), new RuntimeField(2, "sum", n), new RuntimeField(3, "chosen", n)));
		var expression = new RuntimeContextExpression(
				List.of(new RuntimeContextEntry("sum", 0, sum), new RuntimeContextEntry("difference", 1, difference),
						new RuntimeContextEntry("product", 2, product), new RuntimeContextEntry("chosen", 3, chosen)),
				type);
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, n), new RuntimeInput(6, 1, n)),
				List.of(new RuntimeDecision(10, 2, type, List.of(5, 6), Optional.of(expression), Optional.empty(), 4)),
				List.of(), List.of(10), 3);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		var cases = Arrays.asList(new Object[]{new BigDecimal("9007199254740993"), new BigDecimal("9007199254740992")},
				new Object[]{new BigDecimal("20.00000000000000000000000000000000000001"), new BigDecimal("20")},
				new Object[]{new BigDecimal("-1.01"), BigDecimal.ONE},
				new Object[]{new BigDecimal("-1"), new BigDecimal("1.01")},
				new Object[]{new BigDecimal("-2.00"), new BigDecimal("-3.000")},
				new Object[]{new BigDecimal("0.00"), new BigDecimal("0E-4")},
				new Object[]{new BigDecimal("1E40"), new BigDecimal("1E-40")}, new Object[]{null, BigDecimal.ONE},
				new Object[]{BigDecimal.ONE, null}, new Object[]{null, null});
		for (var pair : cases) {
			Map<Integer, Object> inputs = new HashMap<>();
			inputs.put(0, pair[0]);
			inputs.put(1, pair[1]);
			var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
			spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
			for (String file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThat(
						SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0), type))
						.as("%s", inputs).isEqualTo(expected);
		}
	}
	private static RuntimeModel numericFunctionContextFixture(boolean producer) {
		var n = SparkSqlHybridTest.NUMBER;
		var any = SparkSqlHybridTest.ANY;
		var definitions = new ArrayList<RuntimeContextEntry>();
		for (var op : List.of(RuntimeBinaryOperator.ADD, RuntimeBinaryOperator.SUBTRACT,
				RuntimeBinaryOperator.MULTIPLY)) {
			var parameter = op == RuntimeBinaryOperator.MULTIPLY ? n : any;
			var fn = new RuntimeFunctionDefinition(
					List.of(new RuntimeFunctionParameter("a", 0, parameter),
							new RuntimeFunctionParameter("b", 1, parameter)),
					Optional.of(new RuntimeBinaryExpression(op, new RuntimeLocalReference(0, parameter),
							new RuntimeLocalReference(1, parameter), parameter)),
					false, 2, RuntimeType.function(List.of(parameter, parameter), parameter));
			definitions.add(new RuntimeContextEntry(op.name(), definitions.size(), fn));
		}
		var libraryType = RuntimeType.contextFields(List.of(new RuntimeField(0, "ADD", any),
				new RuntimeField(1, "SUBTRACT", any), new RuntimeField(2, "MULTIPLY", any)));
		var library = new RuntimeContextExpression(definitions, libraryType);
		RuntimeExpression source = producer ? new RuntimeValueReference(2, libraryType) : library;
		var a = new RuntimeValueReference(0, n);
		var b = new RuntimeValueReference(1, n);
		var sum = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimePathExpression(source, "ADD", any)), List.of(), List.of(a, b), any);
		var nested = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimePathExpression(source, "MULTIPLY", any)), List.of(), List.of(sum, sum), any);
		var reversed = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimePathExpression(source, "SUBTRACT", any)),
				List.of(new RuntimeNamedArgument("b", a), new RuntimeNamedArgument("a", b)), List.of(), any);
		var scaled = new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, SparkSqlHybridTest.number("6.283184"),
				sum, n);
		var type = RuntimeType.contextFields(List.of(new RuntimeField(0, "scaled", n), new RuntimeField(1, "nested", n),
				new RuntimeField(2, "difference", n)));
		int start = producer ? 0 : 3;
		var result = new RuntimeContextExpression(List.of(new RuntimeContextEntry("nested", start, nested),
				new RuntimeContextEntry("difference", start + 1, reversed),
				new RuntimeContextEntry("scaled", start + 2, scaled)), type);
		var decisions = new ArrayList<RuntimeDecision>();
		if (producer)
			decisions
					.add(new RuntimeDecision(10, 2, libraryType, List.of(), Optional.of(library), Optional.empty(), 3));
		decisions.add(new RuntimeDecision(20, producer ? 3 : 2, type, producer ? List.of(5, 6, 10) : List.of(5, 6),
				Optional.of(result), Optional.empty(), start + 3));
		return new RuntimeModel(List.of(new RuntimeInput(5, 0, n), new RuntimeInput(6, 1, n)), decisions, List.of(),
				producer ? List.of(10, 20) : List.of(20), producer ? 4 : 3);
	}
	@Test
	void numericFunctionContextsInlineNamedAndNestedCallsWithExactResults() {
		for (boolean producer : List.of(false, true)) {
			var model = numericFunctionContextFixture(producer);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(20).nativeSql()).isTrue();
			generated.registerUdfs(spark);
			for (var pair : Arrays.asList(new Object[]{new BigDecimal("10"), new BigDecimal("5")},
					new Object[]{new BigDecimal("9007199254740993"), new BigDecimal("9007199254740992")},
					new Object[]{new BigDecimal("-1.01"), new BigDecimal("2.00")},
					new Object[]{null, BigDecimal.ONE})) {
				Map<Integer, Object> inputs = new HashMap<>();
				inputs.put(0, pair[0]);
				inputs.put(1, pair[1]);
				var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
				spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(20);
				var files = producer ? List.of("Decision_3.sql") : List.of("Decision_2.sql", "AllDecisions.sql");
				for (var file : files)
					assertThat(
							SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0)))
							.as("producer=%s input=%s", producer, inputs).isEqualTo(expected);
			}
		}
	}

	@Test
	void exactProductLoopsRetainNullInvalidNumberAndScaleOverflowErrors() {
		var model = exactProductFixture();
		var generated = SparkSqlHybridTest.generate(model);
		var maximum = new BigDecimal(java.math.BigInteger.ONE, Integer.MAX_VALUE);
		var minimum = new BigDecimal(java.math.BigInteger.ONE, Integer.MIN_VALUE);
		var cases = List.of(new Object[]{List.of(maximum), List.of(new BigDecimal("0.1"))},
				new Object[]{List.of(maximum), List.of(new BigDecimal("0.0"))},
				new Object[]{List.of(minimum), List.of(new BigDecimal("1E1"))},
				new Object[]{Arrays.asList((Object) null), List.of(BigDecimal.ONE)},
				new Object[]{List.of(Double.NaN), List.of(BigDecimal.ONE)});
		for (var pair : cases) {
			Map<Integer, Object> inputs = Map.of(0, pair[0], 1, pair[1]);
			assertThatThrownBy(() -> new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs))
					.isInstanceOf(RuntimeException.class);
			var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
			spark.createDataFrame(List.of(row), generated.inputSchema()).repartition(1)
					.createOrReplaceTempView("hybrid_inputs");
			for (String file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThatThrownBy(() -> spark.sql(generated.sqlFiles().get(file)).collectAsList())
						.hasMessageContaining("FEEL product");
		}
	}

	@Test
	void exactProductLoopsPreserveDecimalPrecisionScalesAndEmptySourceOrder() {
		var model = exactProductFixture();
		var list = model.decisions().getFirst().type();
		var generated = new DmnSparkSqlGenerator().generate(SparkSqlHybridTest.optimized(model),
				DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true,
						Map.of(0, "__dmn_product_10", 1, "right", 2, "result")));
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		assertThat(generated.udfs()).isEmpty();
		assertThat(generated.inputSchema().fields()[2].name()).isEqualTo("__dmn_product_10_");
		assertThat(SparkSqlFeelValueCodec.decode(java.util.HexFormat.of().parseHex("444D4E010400000004312E3233")))
				.isEqualTo(new BigDecimal("1.23"));
		for (int scale : List.of(Integer.MIN_VALUE, Integer.MAX_VALUE))
			for (var coefficient : List.of(java.math.BigInteger.ZERO, java.math.BigInteger.TEN)) {
				var number = new BigDecimal(coefficient, scale);
				assertThat(SparkSqlFeelValueCodec.decode(SparkSqlFeelValueCodec.encode(number))).isEqualTo(number);
			}
		var cases = Arrays.asList(
				new Object[]{List.of(new BigDecimal("123456789012345678901234567890123456789"),
						new BigDecimal("-1.00000000000000000001"), new BigDecimal("0.00"), new BigDecimal("1E400")),
						List.of(new BigDecimal("987654321098765432109876543210987654321"),
								new BigDecimal("-0.000000000000000000002"), BigDecimal.ZERO, new BigDecimal("1E-400"))},
				new Object[]{List.of(new BigDecimal(java.math.BigInteger.TEN, Integer.MIN_VALUE)),
						List.of(BigDecimal.ONE)},
				new Object[]{List.of(new BigDecimal(java.math.BigInteger.TEN, Integer.MAX_VALUE)),
						List.of(BigDecimal.ONE)},
				new Object[]{List.of(new BigDecimal(java.math.BigInteger.ZERO, Integer.MAX_VALUE)),
						List.of(new BigDecimal("0.1"))},
				new Object[]{List.of(new BigDecimal(java.math.BigInteger.ZERO, Integer.MIN_VALUE)),
						List.of(new BigDecimal("1E1"))},
				new Object[]{List.of(), null}, new Object[]{null, List.of()},
				new Object[]{List.of(BigDecimal.ONE), null}, new Object[]{List.of(Double.NaN), List.of()},
				new Object[]{List.of(), List.of(Double.NaN)});
		for (var pair : cases) {
			Map<Integer, Object> inputs = new HashMap<>();
			inputs.put(0, pair[0]);
			inputs.put(1, pair[1]);
			var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
			assertThat(row.get(0)).isEqualTo(SparkSqlFeelValueCodec.inputRow(model, inputs).get(0));
			spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
			for (String file : List.of("Decision_2.sql", "AllDecisions.sql"))
				assertThat(
						SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0), list))
						.as("%s", inputs).isEqualTo(expected);
		}
	}

	@Test
	void sortComparatorReturnCoercionAndAliasedBindingsRetainFallbackSemantics() {
		var number = SparkSqlHybridTest.NUMBER;
		var any = SparkSqlHybridTest.ANY;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var list = RuntimeType.element(RuntimeTypeKind.LIST, number);
		for (int mode : List.of(0, 1, 2)) {
			var returned = mode == 0
					? SparkSqlHybridTest.STRING
					: mode == 1 ? RuntimeType.element(RuntimeTypeKind.LIST, bool) : bool;
			int second = mode == 2 ? 0 : 1;
			var fn = new RuntimeFunctionDefinition(
					List.of(new RuntimeFunctionParameter("x", 0, any), new RuntimeFunctionParameter("y", second, any)),
					Optional.of(new RuntimeBinaryExpression(RuntimeBinaryOperator.LESS,
							new RuntimeLocalReference(0, any), new RuntimeLocalReference(second, any), bool)),
					false, 2, RuntimeType.function(List.of(any, any), returned));
			var expression = new RuntimeFunctionCall("sort", List.of(new RuntimeValueReference(0, list), fn), list);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, list)),
					List.of(new RuntimeDecision(10, 1, list, List.of(5), Optional.of(expression), Optional.empty(), 0)),
					List.of(), List.of(10), 2);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isFalse();
			generated.registerUdfs(spark);
			Map<Integer, Object> inputs = Map.of(0, List.of(3.0, 1.0, 2.0));
			var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
			spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			Object expected = SparkSqlFeelValueCodec
					.fromSpark(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10));
			for (String file : List.of("Decision_1.sql", "AllDecisions.sql"))
				assertThat(
						SparkSqlFeelValueCodec.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0), list))
						.usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
						.isEqualTo(expected);
		}
	}

	@Test
	void exactSortsPreserveNumericPayloadsAndStableContextRows() {
		var number = SparkSqlHybridTest.NUMBER;
		var string = SparkSqlHybridTest.STRING;
		var any = SparkSqlHybridTest.ANY;
		var context = RuntimeType
				.contextFields(List.of(new RuntimeField(0, "key", number), new RuntimeField(1, "label", string)));
		for (var element : List.of(number, string, context))
			for (var op : List.of(RuntimeBinaryOperator.LESS, RuntimeBinaryOperator.GREATER)) {
				var list = RuntimeType.element(RuntimeTypeKind.LIST, element);
				RuntimeExpression left = new RuntimeLocalReference(0, any), right = new RuntimeLocalReference(1, any);
				if (element.equals(context)) {
					left = new RuntimePathExpression(left, "key", number);
					right = new RuntimePathExpression(right, "key", number);
				}
				var fn = new RuntimeFunctionDefinition(
						List.of(new RuntimeFunctionParameter("x", 0, any), new RuntimeFunctionParameter("y", 1, any)),
						Optional.of(new RuntimeBinaryExpression(op, left, right,
								RuntimeType.scalar(RuntimeTypeKind.BOOLEAN))),
						false, 2, RuntimeType.function(List.of(any, any), RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)));
				var expression = new RuntimeFunctionCall("sort", List.of(new RuntimeValueReference(0, list), fn), list);
				var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, list)), List
						.of(new RuntimeDecision(10, 1, list, List.of(5), Optional.of(expression), Optional.empty(), 0)),
						List.of(), List.of(10), 2);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				var numbers = List.of(new BigDecimal("9007199254740993"), new BigDecimal("9007199254740992"),
						new BigDecimal("-1.01"), new BigDecimal("-1"), new BigDecimal("1.00"), BigDecimal.ONE,
						new BigDecimal("1E-400"), new BigDecimal("1E400"));
				List<?> values = element.equals(number)
						? numbers
						: element.equals(string)
								? List.of("z", "a", "😀", "\uE000", "a")
								: java.util.stream.IntStream.range(0, numbers.size())
										.mapToObj(i -> joinRow("key", numbers.get(i), "label", "row" + i)).toList();
				if (element.equals(string))
					values = List.of("z", new StringBuilder("a"), "😀", "\uE000", "a");
				for (Object source : Arrays.asList(values, List.of(), null)) {
					Map<Integer, Object> inputs = new HashMap<>();
					inputs.put(0, source);
					var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
					spark.createDataFrame(List.of(row), generated.inputSchema())
							.createOrReplaceTempView("hybrid_inputs");
					Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model,
							element.equals(string) && source instanceof List<?> strings
									? Map.of(0, strings.stream().map(Object::toString).toList())
									: inputs)
							.decisionValue(10);
					for (String file : List.of("Decision_1.sql", "AllDecisions.sql"))
						assertThat(SparkSqlFeelValueCodec
								.fromSpark(spark.sql(generated.sqlFiles().get(file)).head().get(0), list))
								.as("%s %s", element, op).isEqualTo(expected);
				}
			}
	}

	@Test
	void indexedJoinsPreserveExactKeysFirstMatchesAndNullSources() {
		var model = indexedJoinFixture();
		var generated = new DmnSparkSqlGenerator().generate(SparkSqlHybridTest.optimized(model),
				DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true,
						Map.of(0, "__dmn_join_20", 1, "employees", 2, "name", 3, "result")));
		assertThat(generated.capabilities().get(20).nativeSql()).isTrue();
		assertThat(generated.udfs()).isEmpty();
		assertThat(generated.inputSchema().fields()[3].name()).isEqualTo("__dmn_join_20_");
		var exact = new BigDecimal("9007199254740993");
		var close = exact.add(new BigDecimal("1E-38"));
		var departments = List.of(joinRow("number", close, "manager", "rounded collision"),
				joinRow("number", exact, "manager", "first"), joinRow("number", exact, "manager", "second"));
		var employees = List.of(joinRow("dept", exact, "name", "Name"), joinRow("dept", close, "name", "Name"));
		var cases = new ArrayList<Object[]>();
		cases.add(new Object[]{departments, employees, "Name"});
		cases.add(new Object[]{departments, employees, "Absent"});
		cases.add(new Object[]{departments, List.of(), "Name"});
		cases.add(new Object[]{departments, null, "Name"});
		cases.add(new Object[]{List.of(), employees, "Name"});
		cases.add(new Object[]{null, employees, "Name"});
		cases.add(new Object[]{List.of(joinRow("number", null, "manager", "null key")), null, "Name"});
		cases.add(new Object[]{List.of(joinRow("number", null, "manager", "null key")), List.of(), "Name"});
		cases.add(new Object[]{List.of(joinRow("number", null, "manager", "null key")),
				List.of(joinRow("dept", null, "name", null)), null});
		cases.add(
				new Object[]{departments, List.of(joinRow("dept", exact, "name", new StringBuilder("Name"))), "Name"});
		cases.add(new Object[]{departments, employees, new StringBuilder("Name")});
		cases.add(new Object[]{List.of(joinRow("number", new BigDecimal("1.000"), "manager", null)),
				List.of(joinRow("dept", BigDecimal.ONE, "name", "Name")), "Name"});
		cases.add(new Object[]{departments,
				List.of(joinRow("dept", exact, "name", "Name"), joinRow("dept", Double.NaN, "name", "Other")), "Name"});
		cases.add(new Object[]{List.of(joinRow("number", Double.NaN, "manager", "unused")), null, "Name"});
		cases.add(new Object[]{List.of(), Arrays.asList((Object) null), null});
		for (int scale : List.of(Integer.MIN_VALUE, Integer.MAX_VALUE)) {
			var value = new BigDecimal(java.math.BigInteger.TEN, scale);
			cases.add(new Object[]{List.of(joinRow("number", value, "manager", "extreme")),
					List.of(joinRow("dept", value, "name", "Name")), "Name"});
		}
		for (var pair : cases) {
			Map<Integer, Object> inputs = new HashMap<>();
			inputs.put(0, pair[0]);
			inputs.put(1, pair[1]);
			inputs.put(2, pair[2]);
			var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
			assertThat(row.get(0)).isEqualTo(SparkSqlFeelValueCodec.inputRow(model, inputs).get(0));
			spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(20);
			assertThat(spark.sql(generated.sqlFiles().get("Decision_3.sql")).head().get(0)).as("inputs=%s", inputs)
					.isEqualTo(expected);
			assertThat(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(0)).isEqualTo(expected);
		}
	}

	@Test
	void indexedJoinsRetainErrorsAfterEarlierMatchesAndForNullMatchedRows() {
		var model = indexedJoinFixture();
		var generated = SparkSqlHybridTest.generate(model);
		var one = BigDecimal.ONE;
		var cases = List.of(
				new Object[]{
						List.of(joinRow("number", one, "manager", "first"),
								joinRow("number", Double.NaN, "manager", "invalid later")),
						List.of(joinRow("dept", one, "name", "Name")), "Name"},
				new Object[]{List.of(joinRow("number", null, "manager", "null key")), Arrays.asList((Object) null),
						null},
				new Object[]{Arrays.asList((Object) null), null, "Name"});
		for (var pair : cases) {
			Map<Integer, Object> inputs = new HashMap<>();
			inputs.put(0, pair[0]);
			inputs.put(1, pair[1]);
			inputs.put(2, pair[2]);
			assertThatThrownBy(() -> new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs))
					.isInstanceOf(RuntimeException.class);
			var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
			spark.createDataFrame(List.of(row), generated.inputSchema()).repartition(1)
					.createOrReplaceTempView("hybrid_inputs");
			for (String file : List.of("Decision_3.sql", "AllDecisions.sql"))
				assertThatThrownBy(() -> spark.sql(generated.sqlFiles().get(file)).collectAsList())
						.hasMessageContaining("FEEL join");
		}
	}

	@Test
	void calendarContextsPreserveBoundedShiftsAndZoneTransitions() {
		var any = SparkSqlHybridTest.ANY;
		var input = new RuntimeValueReference(0, any);
		for (String function : List.of("day of week", "month of year")) {
			var entries = new ArrayList<RuntimeContextEntry>();
			var direct = new RuntimeInvocationExpression(Optional.of(function), Optional.empty(), List.of(),
					List.of(input), SparkSqlHybridTest.STRING);
			entries.add(new RuntimeContextEntry("direct", 0, direct));
			for (int shift : List.of(-12, -1, 0, 1, 11, 12)) {
				String duration = function.equals("day of week") ? "P" + shift + "D" : "P" + shift + "M";
				var addition = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, input, new RuntimeConstant(
						RuntimeConstantKind.DURATION, duration, RuntimeType.scalar(RuntimeTypeKind.DURATION)), any);
				entries.add(new RuntimeContextEntry("shift" + shift, entries.size(),
						new RuntimeInvocationExpression(Optional.of(function), Optional.empty(), List.of(),
								List.of(addition), SparkSqlHybridTest.STRING)));
			}
			var expression = new RuntimeContextExpression(entries, any);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, any)), List.of(new RuntimeDecision(10, 1, any,
					List.of(5), Optional.of(expression), Optional.empty(), entries.size())), List.of(), List.of(10), 2);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			for (Object value : Arrays.asList(java.time.LocalDate.of(2020, 2, 29),
					java.time.ZonedDateTime.of(2021, 1, 31, 23, 59, 59, 999999999, java.time.ZoneId.of("UTC")),
					java.time.ZonedDateTime.of(999999998, 6, 15, 12, 0, 0, 0, java.time.ZoneId.of("Europe/Zurich")),
					java.time.LocalDate.of(0, 1, 31), java.time.LocalDate.of(-999999998, 6, 15),
					java.time.LocalDate.of(999999998, 6, 15),
					java.time.LocalDateTime.parse("2021-01-31T23:59:59.999999999"),
					java.time.OffsetDateTime.parse("2021-01-31T23:59:59.999999999-05:00"),
					java.time.ZonedDateTime.of(2024, 3, 30, 23, 30, 0, 123456789, java.time.ZoneId.of("Europe/Zurich")),
					java.time.ZonedDateTime.of(2024, 10, 26, 0, 30, 0, 123456789, java.time.ZoneId.of("Europe/Zurich")),
					java.time.ZonedDateTime.of(1994, 1, 31, 12, 0, 0, 0, java.time.ZoneId.of("Pacific/Kiritimati")),
					new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime(
							java.time.LocalDateTime.of(2024, 3, 30, 23, 30), java.time.ZoneId.of("Europe/Zurich")),
					"2021-01-31", "bad", 42, java.time.LocalTime.NOON, null)) {
				Map<Integer, Object> inputs = new HashMap<>();
				inputs.put(0, value);
				var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
				assertThat((byte[]) row.get(0))
						.isEqualTo((byte[]) SparkSqlFeelValueCodec.inputRow(model, inputs).get(0));
				spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
				assertThat(SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0), any))
						.as("%s input=%s", function, value).isEqualTo(expected);
				assertThat(SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(0), any))
						.isEqualTo(expected);
			}
		}
	}

	@Test
	void temporalTableComponentsPreserveOffsetsPrecisionDurationKindsAndNulls() {
		var string = SparkSqlHybridTest.STRING;
		var samples = new LinkedHashMap<RuntimeTypeKind, List<Object[]>>();
		samples.put(RuntimeTypeKind.DATE,
				Arrays.asList(new Object[]{java.time.LocalDate.MIN, java.time.LocalDate.MAX},
						new Object[]{java.time.LocalDate.of(0, 1, 1), java.time.LocalDate.of(0, 1, 1)},
						new Object[]{null, null}, new Object[]{java.time.LocalDate.of(2020, 1, 1), null},
						new Object[]{java.time.LocalDate.of(2020, 1, 1), List.of(java.time.LocalDate.of(2020, 1, 1))}));
		samples.put(RuntimeTypeKind.TIME,
				Arrays.asList(new Object[]{java.time.LocalTime.of(12, 0, 0, 1), java.time.LocalTime.of(12, 0, 0, 1)},
						new Object[]{java.time.LocalTime.of(12, 0, 0, 1000001), java.time.LocalTime.of(12, 0, 0, 1)},
						new Object[]{java.time.OffsetTime.parse("12:00:00.000000001+01:00"),
								java.time.OffsetTime.parse("12:00:00.000000001+02:00")},
						new Object[]{
								new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime(java.time.LocalTime.NOON,
										java.time.ZoneId.of("Europe/Zurich")),
								new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime(java.time.LocalTime.NOON,
										java.time.ZoneId.of("Europe/Zurich"))},
						new Object[]{java.time.LocalTime.NOON, java.time.OffsetTime.parse("12:00Z")},
						new Object[]{null, null}));
		samples.put(RuntimeTypeKind.DATE_TIME, Arrays.asList(
				new Object[]{java.time.LocalDateTime.MIN, java.time.LocalDateTime.MAX},
				new Object[]{java.time.OffsetDateTime.parse("2020-01-01T00:00:00.123456789+02:00"),
						java.time.OffsetDateTime.parse("2019-12-31T22:00:00.123456789Z")},
				new Object[]{
						new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime(
								java.time.LocalDateTime.of(2024, 3, 31, 2, 30), java.time.ZoneId.of("Europe/Zurich")),
						java.time.OffsetDateTime.parse("2024-03-31T03:30:00+02:00")},
				new Object[]{null, null}));
		samples.put(RuntimeTypeKind.DURATION,
				Arrays.asList(
						new Object[]{java.time.Duration.ofSeconds(Long.MAX_VALUE, 999999999),
								java.time.Duration.ofSeconds(Long.MAX_VALUE)},
						new Object[]{java.time.Duration.ofNanos(-1), java.time.Duration.ZERO},
						new Object[]{java.time.Period.of(1, 0, 3), java.time.Period.of(0, 12, 4)},
						new Object[]{java.time.Duration.ZERO, java.time.Period.ZERO}, new Object[]{null, null}));
		samples.put(RuntimeTypeKind.DAYS_TIME_DURATION,
				Arrays.asList(new Object[]{java.time.Duration.ofNanos(-1), java.time.Duration.ZERO},
						new Object[]{java.time.Period.ZERO, java.time.Duration.ZERO}, new Object[]{null, null}));
		samples.put(RuntimeTypeKind.YEARS_MONTHS_DURATION,
				Arrays.asList(new Object[]{java.time.Period.ofYears(1), java.time.Period.ofMonths(12)},
						new Object[]{java.time.Duration.ZERO, java.time.Period.ZERO}, new Object[]{null, null}));
		samples.put(RuntimeTypeKind.NUMBER,
				Arrays.asList(new Object[]{new BigDecimal("9007199254740993"), new BigDecimal("9007199254740992")},
						new Object[]{new BigDecimal("-1.01"), new BigDecimal("-1")},
						new Object[]{new BigDecimal("-1"), new BigDecimal("-1.01")},
						new Object[]{new BigDecimal("20.00000000000000000000000000000000000001"), new BigDecimal("20")},
						new Object[]{new BigDecimal("1.00"), BigDecimal.ONE},
						new Object[]{BigDecimal.ZERO, new BigDecimal("-0.00")}, new Object[]{null, null}));
		samples.put(RuntimeTypeKind.STRING, Arrays.asList(new Object[]{"hello", "hello"},
				new Object[]{"hello", "world"}, new Object[]{"😀", "\uE000"}, new Object[]{null, null}));
		samples.put(RuntimeTypeKind.BOOLEAN,
				Arrays.asList(new Object[]{true, true}, new Object[]{true, false}, new Object[]{null, null}));

		for (var entry : samples.entrySet()) {
			var temporal = RuntimeType.scalar(entry.getKey());
			var context = RuntimeType.contextFields(List.of(new RuntimeField(0, "value", temporal)));
			var left = new RuntimeValueReference(0, temporal);
			var right = new RuntimePathExpression(new RuntimeValueReference(1, context), "value", temporal);
			var rules = new ArrayList<RuntimeDecisionTableRule>();
			for (var op : (entry.getKey() == RuntimeTypeKind.STRING || entry.getKey() == RuntimeTypeKind.BOOLEAN
					? List.of(RuntimeUnaryTestOperator.EQUAL, RuntimeUnaryTestOperator.NOT_EQUAL)
					: List.of(RuntimeUnaryTestOperator.EQUAL, RuntimeUnaryTestOperator.LESS,
							RuntimeUnaryTestOperator.GREATER))) {
				RuntimeUnaryTest test = op == RuntimeUnaryTestOperator.EQUAL || op == RuntimeUnaryTestOperator.NOT_EQUAL
						? new RuntimeExpressionUnaryTest(right)
						: new RuntimeComparisonUnaryTest(op, right);
				rules.add(new RuntimeDecisionTableRule(rules.size(),
						List.of(new RuntimeUnaryTests(op == RuntimeUnaryTestOperator.NOT_EQUAL, false, List.of(test))),
						List.of(SparkSqlHybridTest.string(op.name())), List.of()));
			}
			var table = new RuntimeDecisionTable(RuntimeHitPolicy.UNIQUE, Optional.empty(),
					List.of(new RuntimeDecisionTableInput(left, Optional.empty(), temporal)),
					List.of(new RuntimeDecisionTableOutput(Optional.empty(), string, Optional.empty(),
							Optional.empty())),
					rules, 0);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, temporal), new RuntimeInput(6, 1, context)),
					List.of(new RuntimeDecision(10, 2, string, List.of(5, 6), Optional.empty(), Optional.of(table), 0)),
					List.of(), List.of(10), 3);
			var generated = new DmnSparkSqlGenerator().generate(SparkSqlHybridTest.optimized(model),
					DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true,
							Map.of(0, "__dmn_temporal_table_10", 1, "context", 2, "result")));
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			if (generated.inputSchema().fields().length > 2)
				assertThat(generated.inputSchema().fields()[2].name()).isEqualTo("__dmn_temporal_table_10_");
			for (var pair : entry.getValue()) {
				var contextValue = new HashMap<String, Object>();
				contextValue.put("value", pair[1]);
				Map<Integer, Object> inputs = new HashMap<>();
				inputs.put(0, pair[0]);
				inputs.put(1, contextValue);
				var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
				assertThat(row.get(1)).isEqualTo(SparkSqlFeelValueCodec.inputRow(model, inputs).get(1));
				spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				var expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
				assertThat(spark.sql(generated.sqlFiles().get("Decision_2.sql")).head().get(0))
						.as("%s %s", entry.getKey(), Arrays.toString(pair)).isEqualTo(expected);
				assertThat(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(0)).isEqualTo(expected);
			}
		}
	}

	@Test
	void numericQuantifiersPreserveDecimalOrderingNullsAndEarlyExit() {
		var number = SparkSqlHybridTest.NUMBER;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var context = RuntimeType.contextFields(List.of(new RuntimeField(0, "price", number)));
		var list = RuntimeType.element(RuntimeTypeKind.LIST, context);
		for (String literal : List.of("20", "-20")) {
			var bound = new BigDecimal(literal);
			for (var quantifier : RuntimeQuantifier.values()) {
				for (var operator : List.of(RuntimeBinaryOperator.EQUAL, RuntimeBinaryOperator.NOT_EQUAL,
						RuntimeBinaryOperator.LESS, RuntimeBinaryOperator.LESS_EQUAL, RuntimeBinaryOperator.GREATER,
						RuntimeBinaryOperator.GREATER_EQUAL)) {
					boolean reversed = operator == RuntimeBinaryOperator.LESS
							|| operator == RuntimeBinaryOperator.GREATER_EQUAL;
					var member = new RuntimePathExpression(new RuntimeLocalReference(0, SparkSqlHybridTest.ANY),
							"price", number);
					var fixed = SparkSqlHybridTest.number(literal);
					var predicate = new RuntimeBinaryExpression(operator, reversed ? fixed : member,
							reversed ? member : fixed, bool);
					var expression = new RuntimeQuantifiedExpression(quantifier,
							List.of(new RuntimeQuantifiedBinding(0, new RuntimeValueReference(0, list))), predicate,
							bool);
					var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, list)), List.of(
							new RuntimeDecision(10, 1, bool, List.of(5), Optional.of(expression), Optional.empty(), 1)),
							List.of(), List.of(10), 2);
					var generated = SparkSqlHybridTest.generate(model);
					assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
					assertThat(generated.udfs()).isEmpty();
					var sources = new ArrayList<Object>();
					for (Object value : Arrays.asList(bound, bound.add(new BigDecimal("1E-38")),
							bound.subtract(new BigDecimal("1E-38")), BigDecimal.ZERO,
							new BigDecimal(java.math.BigInteger.TEN, Integer.MIN_VALUE),
							new BigDecimal(java.math.BigInteger.TEN, Integer.MAX_VALUE), null)) {
						var row = new HashMap<String, Object>();
						row.put("price", value);
						sources.add(List.of(row));
					}
					sources.add(List.of());
					for (var candidate : List.of(bound.subtract(BigDecimal.ONE), bound, bound.add(BigDecimal.ONE))) {
						Object singleton = List.of(Map.of("price", candidate));
						boolean outcome = (Boolean) new io.finmsg.dmn.runtime.DmnRuntime()
								.evaluate(model, Map.of(0, singleton)).decisionValue(10);
						if (outcome == (quantifier == RuntimeQuantifier.SOME)) {
							sources.add(List.of(Map.of("price", candidate), Map.of("price", Double.NaN)));
							break;
						}
					}
					for (Object source : sources) {
						Map<Integer, Object> inputs = new HashMap<>();
						inputs.put(0, source);
						var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
						spark.createDataFrame(List.of(row), generated.inputSchema())
								.createOrReplaceTempView("hybrid_inputs");
						Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs)
								.decisionValue(10);
						assertThat(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0))
								.as("%s %s %s input=%s", literal, quantifier, operator, source).isEqualTo(expected);
					}
					assertThat(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(0))
							.isEqualTo(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
				}
			}
		}
	}

	@Test
	void literalRelationsLowerForBoundedContextQuantifiers() {
		var number = SparkSqlHybridTest.NUMBER;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var context = RuntimeType.contextFields(List.of(new RuntimeField(0, "price", number)));
		var list = RuntimeType.element(RuntimeTypeKind.LIST, context);
		for (var quantifier : RuntimeQuantifier.values()) {
			var relation = new RuntimeRelationExpression(List.of(new RuntimeRelationColumn("price", number)),
					List.of(List.of(SparkSqlHybridTest.number("25")), List.of(SparkSqlHybridTest.number("1.5"))), list);
			var predicate = new RuntimeBinaryExpression(RuntimeBinaryOperator.GREATER,
					new RuntimePathExpression(new RuntimeLocalReference(0, SparkSqlHybridTest.ANY), "price", number),
					SparkSqlHybridTest.number("10"), bool);
			var quantified = new RuntimeQuantifiedExpression(quantifier,
					List.of(new RuntimeQuantifiedBinding(0, new RuntimeValueReference(0, list))), predicate, bool);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, list, List.of(), Optional.of(relation)), new RuntimeDecision(11,
							1, bool, List.of(10), Optional.of(quantified), Optional.empty(), 1)),
					List.of(), List.of(10, 11), 2);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.capabilities().get(11).nativeSql()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(11);
			assertThat(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0)).isEqualTo(expected);
			assertThat(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(1)).isEqualTo(expected);
		}
	}

	@Test
	void numericContextFiltersUseExactDecimalKeysBeforeStringProjection() {
		var string = SparkSqlHybridTest.STRING;
		var number = SparkSqlHybridTest.NUMBER;
		var context = RuntimeType
				.contextFields(List.of(new RuntimeField(0, "dept", number), new RuntimeField(1, "name", string)));
		var sourceType = RuntimeType.element(RuntimeTypeKind.LIST, context);
		var names = RuntimeType.element(RuntimeTypeKind.LIST, string);
		for (var resultType : List.of(SparkSqlHybridTest.ANY, names)) {
			for (String literal : List.of("20", "9007199254740993", "1E+400")) {
				var local = new RuntimeLocalReference(0, SparkSqlHybridTest.ANY);
				var equal = new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL,
						new RuntimePathExpression(local, "dept", number), SparkSqlHybridTest.number(literal),
						RuntimeType.scalar(RuntimeTypeKind.BOOLEAN));
				var filter = new RuntimeFilterExpression(new RuntimeValueReference(0, sourceType), 0, equal,
						sourceType);
				var expression = new RuntimePathExpression(filter, "name", names);
				var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, sourceType)),
						List.of(new RuntimeDecision(10, 1, resultType, List.of(5), Optional.of(expression),
								Optional.empty(), 1)),
						List.of(), List.of(10), 2);
				var generated = new DmnSparkSqlGenerator().generate(SparkSqlHybridTest.optimized(model),
						DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true,
								Map.of(0, "__dmn_numeric_filter_10", 1, "names")));
				assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				assertThat(generated.inputSchema().fields()[1].name()).isEqualTo("__dmn_numeric_filter_10_");
				var exact = new BigDecimal(literal);
				var withNullName = new HashMap<String, Object>();
				withNullName.put("dept", exact);
				withNullName.put("name", null);
				var populated = Arrays.asList(Map.of("dept", exact, "name", "O'Reilly\\tail"),
						Map.of("dept", exact.add(new BigDecimal("0.00000000000000000000000000000000000001")), "name",
								"nearby"),
						Map.of("dept", exact.setScale(Math.max(2, exact.scale())), "name", "scaled"), withNullName,
						null, Map.of("dept", exact.subtract(BigDecimal.ONE), "name", "other"),
						Map.of("dept", new BigDecimal(java.math.BigInteger.TEN, Integer.MIN_VALUE), "name",
								"extreme large"),
						Map.of("dept", new BigDecimal(java.math.BigInteger.TEN, Integer.MAX_VALUE), "name",
								"extreme small"));
				for (Object source : Arrays.asList(populated, List.of(), null,
						List.of(Map.of("dept", "invalid", "name", "bad")))) {
					Map<Integer, Object> inputs = new HashMap<>();
					inputs.put(0, source);
					var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
					assertThat(row.get(0)).isEqualTo(SparkSqlFeelValueCodec.inputRow(model, inputs).get(0));
					spark.createDataFrame(List.of(row), generated.inputSchema())
							.createOrReplaceTempView("hybrid_inputs");
					Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
					assertThat(SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0), resultType))
							.as("literal=%s result=%s source=%s", literal, resultType, source).isEqualTo(expected);
					assertThat(SparkSqlFeelValueCodec.fromSpark(
							spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(0), resultType))
							.isEqualTo(expected);
				}
			}
		}
	}

	@Test
	void inlinedStringFunctionsPreserveNamedBindingAndDeclaredResultCoercion() {
		var string = SparkSqlHybridTest.STRING;
		var any = SparkSqlHybridTest.ANY;
		for (boolean named : List.of(false, true)) {
			var entries = new ArrayList<RuntimeContextEntry>();
			var calls = new ArrayList<RuntimeExpression>();
			for (boolean typed : List.of(true, false)) {
				var parameterType = typed ? string : any;
				var parameters = List.of(new RuntimeFunctionParameter("a", 0, parameterType),
						new RuntimeFunctionParameter("b", 1, parameterType));
				var body = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD,
						new RuntimeLocalReference(0, parameterType), new RuntimeLocalReference(1, parameterType),
						parameterType);
				var function = new RuntimeFunctionDefinition(parameters, Optional.of(body), false,
						RuntimeType.function(List.of(parameterType, parameterType), parameterType));
				int slot = typed ? 2 : 3;
				entries.add(new RuntimeContextEntry(typed ? "boxed" : "literal", slot, function));
				var a = new RuntimeValueReference(0, string);
				var b = new RuntimeValueReference(1, string);
				calls.add(new RuntimeInvocationExpression(Optional.empty(),
						Optional.of(new RuntimeLocalReference(slot, any)),
						named ? List.of(new RuntimeNamedArgument("b", a), new RuntimeNamedArgument("a", b)) : List.of(),
						named ? List.of() : List.of(a, b), parameterType));
			}
			entries.add(new RuntimeContextEntry("", -1,
					new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, calls.get(0), calls.get(1), any)));
			var expression = new RuntimeContextExpression(entries, string);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, string), new RuntimeInput(6, 1, string)),
					List.of(new RuntimeDecision(10, 2, string, List.of(5, 6), Optional.of(expression), Optional.empty(),
							4)),
					List.of(), List.of(10), 3);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			for (Object first : Arrays.asList("alpha", "O'Reilly\\tail", "", null, BigDecimal.ONE,
					List.of("singleton"))) {
				for (Object second : Arrays.asList("beta", null)) {
					Map<Integer, Object> inputs = new HashMap<>();
					inputs.put(0, first);
					inputs.put(1, second);
					var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
					spark.createDataFrame(List.of(row), generated.inputSchema())
							.createOrReplaceTempView("hybrid_inputs");
					Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
					assertThat(spark.sql(generated.sqlFiles().get("Decision_2.sql")).head().get(0))
							.as("named=%s first=%s second=%s", named, first, second).isEqualTo(expected);
					assertThat(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(0))
							.isEqualTo(expected);
				}
			}
		}
	}

	@Test
	void missingBootstrapJavaClassesPreserveNullWithoutBroadeningMethodWhitelist() {
		for (String className : List.of("java.lang.Foo", "java.lang.DmnCompilerToolkitAbsentClass")) {
			var external = literalJavaFixture(className, "valueOf(double)", 1);
			var boxed = new RuntimeFunctionDefinition(external.parameters(), external.body(), false,
					external.localSlotCount(), external.type());
			for (var function : List.of(external, boxed)) {
				for (var args : List.of(List.<RuntimeExpression>of(SparkSqlHybridTest.number("123")),
						List.<RuntimeExpression>of(SparkSqlHybridTest.string("invalid number")),
						List.<RuntimeExpression>of())) {
					var call = new RuntimeInvocationExpression(Optional.empty(), Optional.of(function), List.of(), args,
							SparkSqlHybridTest.ANY);
					var model = new RuntimeModel(List.of(),
							List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(call))),
							List.of(), List.of(10), 1);
					var generated = generateApprovedJava(model);
					assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
					assertThat(generated.udfs()).isEmpty();
					assertThat(new io.finmsg.dmn.runtime.DmnRuntime(JAVA_TEST_POLICY).evaluate(model, Map.of())
							.decisionValue(10)).isNull();
					spark.range(1).createOrReplaceTempView("hybrid_inputs");
					assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)).isNull();
					assertThat(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(0)).isNull();
				}
			}
		}
		for (String className : List.of("java.lang.System", "java.lang.Runtime", "example.Untrusted",
				"java.util.DmnAbsentClass"))
			assertThat(SparkSqlJavaLiteralProof.supports(literalJavaFixture(className, "valueOf(double)", 1)))
					.isFalse();
	}

	@Test
	void boundedSortSpecializationPreservesCapturedAndBkmComparatorOrdering() {
		var string = SparkSqlHybridTest.STRING;
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var listType = RuntimeType.element(RuntimeTypeKind.LIST, string);
		var comparatorType = RuntimeType.function(List.of(string, string), bool);
		var parameters = List.of(new RuntimeFunctionParameter("a", 0, string),
				new RuntimeFunctionParameter("b", 1, string));
		for (boolean captured : List.of(false, true)) {
			for (boolean invocation : List.of(false, true)) {
				RuntimeExpression target = captured
						? new RuntimeValueReference(2, string)
						: SparkSqlHybridTest.string("a");
				var predicate = new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL,
						new RuntimeLocalReference(0, string), target, bool);
				var body = new RuntimeConditionalExpression(predicate,
						new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool),
						new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", bool), bool);
				var comparator = new RuntimeFunctionDefinition(parameters, Optional.of(body), false, comparatorType);
				var comparatorBkm = new RuntimeBkm(10, 0, comparatorType, List.of(), RuntimeFunctionKind.FEEL,
						Optional.of(comparator));
				var source = new RuntimeListExpression(List.of(SparkSqlHybridTest.string("a"),
						SparkSqlHybridTest.string("z"), SparkSqlHybridTest.string("a"), SparkSqlHybridTest.string("z")),
						listType);
				RuntimeExpression function = captured
						? new RuntimeLocalReference(0, comparatorType)
						: new RuntimeValueReference(0, comparatorType);
				RuntimeExpression sort = invocation
						? new RuntimeInvocationExpression(Optional.of("sort"), Optional.empty(), List.of(),
								List.of(source, function), listType)
						: new RuntimeFunctionCall("sort", List.of(source, function), listType);
				var wrapperType = RuntimeType.function(List.of(comparatorType), listType);
				var wrapper = new RuntimeFunctionDefinition(
						List.of(new RuntimeFunctionParameter("fn", 0, comparatorType)), Optional.of(sort), false,
						wrapperType);
				var wrapperBkm = new RuntimeBkm(20, 1, wrapperType, List.of(), RuntimeFunctionKind.FEEL,
						Optional.of(wrapper));
				RuntimeExpression expression = captured
						? new RuntimeInvocationExpression(Optional.empty(),
								Optional.of(new RuntimeValueReference(1, wrapperType)), List.of(), List.of(comparator),
								listType)
						: sort;
				var decision = new RuntimeDecision(30, 3, SparkSqlHybridTest.ANY,
						captured ? List.of(5, 20) : List.of(10), Optional.of(expression));
				var model = new RuntimeModel(List.of(new RuntimeInput(5, 2, string)), List.of(decision),
						List.of(comparatorBkm, wrapperBkm), List.of(10, 20, 30), 4);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(30).nativeSql()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				for (Object value : Arrays.asList("a", "z", "neither", "", null, BigDecimal.ONE, List.of("a"),
						List.of())) {
					Map<Integer, Object> inputs = new HashMap<>();
					inputs.put(2, value);
					var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
					spark.createDataFrame(List.of(row), generated.inputSchema())
							.createOrReplaceTempView("hybrid_inputs");
					Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(30);
					assertThat(SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get("Decision_3.sql")).head().get(0), listType))
							.as("captured=%s invocation=%s input=%s", captured, invocation, value).isEqualTo(expected);
					assertThat(SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(0), listType))
							.isEqualTo(expected);
				}
			}
		}
	}

	@Test
	void serviceConcatenationUsesTypedDecisionAndOpaqueParameterProjection() {
		var string = SparkSqlHybridTest.STRING;
		var any = SparkSqlHybridTest.ANY;
		var global = new RuntimeDecision(10, 1, string, List.of(5), Optional.of(new RuntimeValueReference(0, string)));
		var type = RuntimeType.function(List.of(any), any);
		var body = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, new RuntimeValueReference(1, string),
				new RuntimeLocalReference(0, any), any);
		var function = new RuntimeFunctionDefinition(List.of(new RuntimeFunctionParameter("evaluation", 0, any)),
				Optional.of(body), false, type);
		var service = new RuntimeBkm(20, 2, type, List.of(10), RuntimeFunctionKind.FEEL, Optional.of(function));
		var original = new RuntimeModel(List.of(new RuntimeInput(5, 0, string)), List.of(global), List.of(service),
				List.of(10, 20), 3);
		var plan = SparkSqlInvocationPlan.create(original, 20);
		var model = plan.model();
		int parameter = plan.parameterNames().keySet().iterator().next();
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(plan.decisionId()).nativeSql()).isTrue();
		assertThat(generated.udfs()).isEmpty();
		for (Object prefix : Arrays.asList("grade: ", null, BigDecimal.ONE)) {
			for (Object value : Arrays.asList("good", "", "O'Reilly\\tail", null, BigDecimal.ONE, true,
					List.of("singleton"), Map.of("x", "y"), java.time.LocalDate.of(2026, 10, 20))) {
				Map<Integer, Object> inputs = new HashMap<>();
				inputs.put(0, prefix);
				inputs.put(parameter, value);
				var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
				assertThat((byte[]) row.get(1))
						.isEqualTo((byte[]) SparkSqlFeelValueCodec.inputRow(model, inputs).get(1));
				spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs)
						.decisionValue(plan.decisionId());
				assertThat(spark.sql(generated.sqlFiles().get("Decision_" + plan.resultSlot() + ".sql")).head().get(0))
						.as("prefix=%s parameter=%s", prefix, value).isEqualTo(expected);
				assertThat(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(1)).isEqualTo(expected);
			}
		}
	}

	@Test
	void dateTablesPreserveExactOrderingAndNullNoMatch() {
		var date = RuntimeType.scalar(RuntimeTypeKind.DATE);
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var source = new RuntimeValueReference(0, date);
		var values = Arrays.asList(java.time.LocalDate.MIN, java.time.LocalDate.of(-1, 12, 31),
				java.time.LocalDate.of(0, 1, 1), java.time.LocalDate.of(2016, 10, 1), java.time.LocalDate.MAX,
				List.of(java.time.LocalDate.of(2018, 1, 1)), List.of(), "2018-01-01", null);
		for (boolean dynamic : List.of(false, true)) {
			for (var op : List.of(RuntimeUnaryTestOperator.GREATER, RuntimeUnaryTestOperator.LESS,
					RuntimeUnaryTestOperator.GREATER_EQUAL, RuntimeUnaryTestOperator.LESS_EQUAL)) {
				RuntimeExpression endpoint = dynamic
						? new RuntimeValueReference(1, date)
						: new RuntimeFunctionCall("date", List.of(SparkSqlHybridTest.string("2016-10-01")), date);
				var opposite = switch (op) {
					case GREATER -> RuntimeUnaryTestOperator.LESS_EQUAL;
					case LESS -> RuntimeUnaryTestOperator.GREATER_EQUAL;
					case GREATER_EQUAL -> RuntimeUnaryTestOperator.LESS;
					default -> RuntimeUnaryTestOperator.GREATER;
				};
				var rules = new ArrayList<RuntimeDecisionTableRule>();
				for (var testOp : List.of(op, opposite)) {
					var test = new RuntimeUnaryTests(false, false,
							List.of(new RuntimeComparisonUnaryTest(testOp, endpoint)));
					rules.add(new RuntimeDecisionTableRule(rules.size(), List.of(test), List.of(
							new RuntimeConstant(RuntimeConstantKind.BOOLEAN, rules.isEmpty() ? "true" : "false", bool)),
							List.of()));
				}
				var table = new RuntimeDecisionTable(RuntimeHitPolicy.UNIQUE, Optional.empty(),
						List.of(new RuntimeDecisionTableInput(source, Optional.empty(), date)),
						List.of(new RuntimeDecisionTableOutput(Optional.empty(), bool, Optional.empty(),
								Optional.empty())),
						rules, 0);
				var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, date), new RuntimeInput(6, 1, date)),
						List.of(new RuntimeDecision(10, 2, bool, dynamic ? List.of(5, 6) : List.of(5), Optional.empty(),
								Optional.of(table), 0)),
						List.of(), List.of(10), 3);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				for (int i = 0; i < values.size(); i++) {
					for (Object other : Arrays.asList(java.time.LocalDate.of(2016, 10, 1), null)) {
						Map<Integer, Object> inputs = new HashMap<>();
						inputs.put(0, values.get(i));
						inputs.put(1, other);
						var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
						spark.createDataFrame(List.of(row), generated.inputSchema())
								.createOrReplaceTempView("hybrid_inputs");
						var expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
						assertThat(spark.sql(generated.sqlFiles().get("Decision_2.sql")).head().get(0))
								.as("dynamic=%s op=%s input=%s other=%s", dynamic, op, values.get(i), other)
								.isEqualTo(expected);
						if (i == 0)
							assertThat(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(0))
									.isEqualTo(expected);
					}
				}
			}
		}
	}

	@Test
	void dynamicListPositionsPreserveExactIntConversionAndBounds() {
		var strings = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.STRING);
		for (boolean nested : List.of(false, true)) {
			var list = nested ? RuntimeType.element(RuntimeTypeKind.LIST, strings) : strings;
			var source = new RuntimeValueReference(0, list);
			var position = new RuntimeValueReference(1, SparkSqlHybridTest.NUMBER);
			var item = new RuntimeValueReference(2, list.elementType());
			for (String name : List.of("remove", "insert before")) {
				List<RuntimeExpression> args = name.equals("remove")
						? List.of(source, position)
						: List.of(source, position, item);
				var expression = new RuntimeInvocationExpression(Optional.of(name), Optional.empty(), List.of(), args,
						SparkSqlHybridTest.ANY);
				var model = new RuntimeModel(
						List.of(new RuntimeInput(5, 0, list), new RuntimeInput(6, 1, SparkSqlHybridTest.NUMBER),
								new RuntimeInput(7, 2, list.elementType())),
						List.of(new RuntimeDecision(10, 3, list, List.of(5, 6, 7), Optional.of(expression))), List.of(),
						List.of(10), 4);
				var generated = new DmnSparkSqlGenerator().generate(
						new RuntimeOptimizedModel(model, List.of(), List.of(), List.of()),
						DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true,
								Map.of(0, "values", 1, "__dmn_list_position_1", 2, "newItem", 3, "result")));
				assertThat(generated.capabilities().get(10).nativeSql()).as("%s %s", name, nested).isTrue();
				assertThat(generated.udfs()).isEmpty();
				assertThat(Arrays.stream(generated.inputSchema().fields()).map(f -> f.name()))
						.contains("__dmn_list_position_1_");
				Object populated = nested ? List.of(List.of("a", "b"), List.of("c")) : List.of("a", "b", "c");
				for (Object values : Arrays.asList(populated, List.of(), null)) {
					for (Object pos : Arrays.asList(new BigDecimal("1"), new BigDecimal("-1"), BigDecimal.ZERO,
							new BigDecimal("4"), new BigDecimal("-9"), new BigDecimal("1.999999999999999999999999"),
							new BigDecimal("0.999999999999999999999999"), new BigDecimal("2147483648"),
							new BigDecimal("9007199254740993"), new BigDecimal("4294967297"), null)) {
						var inputs = new HashMap<Integer, Object>();
						inputs.put(0, values);
						inputs.put(1, pos);
						inputs.put(2, nested ? List.of("x", "y") : "x");
						spark.createDataFrame(
								List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
								generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
						Object actual = SparkSqlFeelValueCodec
								.fromSpark(spark.sql(generated.sqlFiles().get("Decision_3.sql")).head().get(0));
						assertThat(actual).as("%s nested=%s values=%s pos=%s", name, nested, values, pos).isEqualTo(
								new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10));
					}
				}
				var inputs = new HashMap<Integer, Object>();
				inputs.put(0, null);
				inputs.put(1, Double.NaN);
				inputs.put(2, nested ? List.of("x") : "x");
				spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
						generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				assertThat(spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head().get(0)).isNull();
				assertThat(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10)).isNull();
			}
		}
	}

	@Test
	void typedStringSelectionsPreserveSingletonCoercionAndCaseFormatting() {
		var strings = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.STRING);
		var ref = new RuntimeValueReference(0, strings);
		var local = new RuntimeLocalReference(0, SparkSqlHybridTest.STRING);
		var equal = new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, local, SparkSqlHybridTest.string("Bob"),
				RuntimeType.scalar(RuntimeTypeKind.BOOLEAN));
		var filter = new RuntimeFilterExpression(ref, 0, equal, strings);
		for (RuntimeType type : List.of(SparkSqlHybridTest.ANY, SparkSqlHybridTest.STRING, strings,
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY))) {
			for (RuntimeExpression expression : List.of(filter,
					new RuntimeFunctionCall("sublist",
							List.of(ref, SparkSqlHybridTest.number("2"), SparkSqlHybridTest.number("1")), strings),
					new RuntimeFunctionCall("sublist",
							List.of(ref, SparkSqlHybridTest.number("-2"), SparkSqlHybridTest.number("2")), strings))) {
				var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, strings)), List
						.of(new RuntimeDecision(10, 1, type, List.of(5), Optional.of(expression), Optional.empty(), 1)),
						List.of(), List.of(10), 2);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).as("%s %s", type, expression).isTrue();
				assertThat(generated.udfs()).isEmpty();
				for (Object value : Arrays.asList(List.of("Bob", "Alice"), List.of(), List.of("Bob", "Bob"),
						Arrays.asList(null, "Bob"), null)) {
					var inputs = new HashMap<Integer, Object>();
					inputs.put(0, value);
					spark.createDataFrame(
							List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
							generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
					Object actual = SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
					assertThat(actual).as("%s %s %s", type, expression, value).isEqualTo(
							new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10));
				}
			}
		}
		for (String match : List.of("Bob", "+1", "ß")) {
			var predicate = new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, local,
					SparkSqlHybridTest.string(match), RuntimeType.scalar(RuntimeTypeKind.BOOLEAN));
			var selection = new RuntimeFilterExpression(ref, 0, predicate, strings);
			for (String casing : List.of("upper case", "lower case")) {
				var expression = new RuntimeFunctionCall(casing, List.of(selection), SparkSqlHybridTest.STRING);
				var model = new RuntimeModel(
						List.of(new RuntimeInput(5, 0, strings)), List.of(new RuntimeDecision(10, 1,
								SparkSqlHybridTest.STRING, List.of(5), Optional.of(expression), Optional.empty(), 1)),
						List.of(), List.of(10), 2);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				for (Object value : Arrays.asList(List.of(match), Arrays.asList(null, match), null)) {
					var inputs = new HashMap<Integer, Object>();
					inputs.put(0, value);
					spark.createDataFrame(
							List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
							generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
					Object actual = SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
					assertThat(actual).as("%s %s %s", casing, match, value).isEqualTo(
							new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10));
				}
			}
		}
	}

	@Test
	void nestedTextBuiltinsPreserveShapeAndNullSources() {
		var strings = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.STRING);
		var nested = RuntimeType.element(RuntimeTypeKind.LIST, strings);
		var source = new RuntimeValueReference(0, nested);
		var item = new RuntimeValueReference(1, strings);
		var singleton = new RuntimeListExpression(List.of(SparkSqlHybridTest.string("tail")), strings);
		for (String name : List.of("append", "concatenate", "flatten")) {
			List<RuntimeExpression> args = name.equals("flatten")
					? List.of(source)
					: name.equals("append")
							? List.of(source, item)
							: List.of(source, new RuntimeListExpression(List.of(singleton), nested));
			var expression = new RuntimeInvocationExpression(Optional.of(name), Optional.empty(), List.of(), args,
					SparkSqlHybridTest.ANY);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, nested), new RuntimeInput(6, 1, strings)),
					List.of(new RuntimeDecision(10, 2, SparkSqlHybridTest.ANY, List.of(5, 6), Optional.of(expression))),
					List.of(), List.of(10), 3);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).as(name).isTrue();
			assertThat(generated.udfs()).isEmpty();
			for (Object value : Arrays.asList(List.of(List.of("a", "b"), List.of("c")), List.of(), List.of(List.of()),
					List.of(Arrays.asList("a", null)), null)) {
				var inputs = new HashMap<Integer, Object>();
				inputs.put(0, value);
				inputs.put(1, List.of("x", "y"));
				spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
						generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_2.sql")).head().get(0));
				assertThat(actual).as("%s %s", name, value)
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10));
			}
		}
	}

	@Test
	void dynamicLookupKeysPreserveCanonicalValuesAndFallbackPayloads() {
		var ref = new RuntimeValueReference(0, SparkSqlHybridTest.ANY);
		var entries = new ArrayList<RuntimeContextEntry>();
		for (String key : List.of("a", "A", "12", "12.00", "true", "[a]", "{a=1}", "O'Reilly\\tail"))
			entries.add(new RuntimeContextEntry(key, -1, SparkSqlHybridTest.string("found:" + key)));
		entries.add(new RuntimeContextEntry("nil", -1,
				new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL))));
		var context = new RuntimeContextExpression(entries, SparkSqlHybridTest.ANY);
		var positional = List.<RuntimeExpression>of(context, ref);
		var named = List.of(new RuntimeNamedArgument("key", ref), new RuntimeNamedArgument("m", context));
		for (RuntimeExpression lookup : List.of(
				new RuntimeFunctionCall("get value", positional, SparkSqlHybridTest.STRING),
				new RuntimeInvocationExpression(Optional.of("get value"), Optional.empty(), List.of(), positional,
						SparkSqlHybridTest.STRING),
				new RuntimeInvocationExpression(Optional.of("get value"), Optional.empty(), named, List.of(),
						SparkSqlHybridTest.STRING))) {
			var concat = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, SparkSqlHybridTest.string("prefix:"),
					ref, SparkSqlHybridTest.ANY);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, SparkSqlHybridTest.ANY)),
					List.of(new RuntimeDecision(10, 1, SparkSqlHybridTest.STRING, List.of(5), Optional.of(lookup)),
							new RuntimeDecision(11, 2, SparkSqlHybridTest.ANY, List.of(5), Optional.of(concat)),
							new RuntimeDecision(12, 3, SparkSqlHybridTest.ANY, List.of(5), Optional.of(ref))),
					List.of(), List.of(10, 11, 12), 4);
			var generated = new DmnSparkSqlGenerator().generate(
					new RuntimeOptimizedModel(model, List.of(), List.of(), List.of()),
					DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true,
							Map.of(0, "__dmn_lookup_key_0", 1, "found", 2, "concatenated", 3, "original")));
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.capabilities().get(11).nativeSql()).isTrue();
			assertThat(generated.capabilities().get(12).nativeSql()).isFalse();
			assertThat(Arrays.stream(generated.inputSchema().fields()).map(f -> f.name()))
					.contains("__dmn_lookup_key_0_");
			generated.registerUdfs(spark);
			for (Object value : Arrays.asList("a", "A", "missing", new BigDecimal("12"), new BigDecimal("12.00"), true,
					List.of("a"), Map.of("a", BigDecimal.ONE),
					java.time.OffsetDateTime.parse("2020-12-31T23:59:59.123456789-03:30"), null, "nil",
					"O'Reilly\\tail")) {
				var inputs = new HashMap<Integer, Object>();
				inputs.put(0, value);
				Row row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
				assertThat((byte[]) row.get(0))
						.isEqualTo((byte[]) SparkSqlFeelValueCodec.inputRow(model, inputs).get(0));
				spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				var expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs);
				Object actual = SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
				assertThat(actual).as("lookup %s", value).isEqualTo(expected.decisionValue(10));
				Row all = spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head();
				for (int index = 0; index < 3; index++)
					assertThat(SparkSqlFeelValueCodec.fromSpark(all.get(index))).as("column %s, input %s", index, value)
							.isEqualTo(expected.decisionValue(10 + index));
			}
			var duplicate = new RuntimeInvocationExpression(Optional.of("get value"), Optional.empty(),
					List.of(new RuntimeNamedArgument("m", context), new RuntimeNamedArgument("m", ref)), List.of(),
					SparkSqlHybridTest.STRING);
			assertThat(SparkSqlLookupKeyInput.inputSlot(model,
					new RuntimeDecision(13, 3, SparkSqlHybridTest.STRING, List.of(5), Optional.of(duplicate))))
					.isNull();
		}
	}

	@Test
	void literalCalendarListsPreserveIsoWeeksAndConstructorBinding() {
		for (boolean optimized : List.of(false, true)) {
			for (String sample : List.of(
					"[week of year(date(2005, 1, 1)), week of year(date(2005, 1, 3)), week of year(date(2005, 1, 9))]",
					"[day of year(date(2000, 2, 29)), day of year(date(1900, 3, 1))]",
					"day of week(date: date(0, 1, 1))",
					"month of year(date and time(\"2020-12-31T23:59:59.123456789-03:30\"))",
					"week of year(date: date(\"+999999999-12-31\"))", "week of year(value: date(2005, 1, 1))",
					"day of week(date(2005, 2, 30))", "week of year(null)", "month of year(\"foo\")")) {
				var model = compiledFilterModel(sample, optimized);
				var decision = model.decisions().getFirst();
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(decision.id()).nativeSql()).as(sample).isTrue();
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec.fromSpark(spark
						.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
						.decisionValue(decision.id());
				if (expected instanceof BigDecimal number)
					assertThat((BigDecimal) actual).as(sample).isEqualByComparingTo(number);
				else
					assertThat(actual).as(sample).isEqualTo(expected);
			}
		}
		assertThat(SparkSqlCalendarLiteralProof.result(new RuntimeFunctionCall("week of year",
				List.of(new RuntimeValueReference(0, SparkSqlHybridTest.ANY)), SparkSqlHybridTest.ANY))).isNull();
	}

	@Test
	void terminalDateLoopsPreserveBoundsOrderAndLeapDays() {
		for (String sample : List.of("for i in @\"1980-01-01\"..@\"1980-01-03\" return i",
				"for i in @\"1980-01-03\"..@\"1980-01-01\" return i",
				"for i in @\"2020-02-28\"..@\"2020-03-01\" return i",
				"for i in @\"0000-01-01\"..@\"0000-01-01\" return i",
				"for i in @\"-999999999-01-01\"..@\"-999999999-01-02\" return i",
				"for i in @\"+999999999-12-31\"..@\"+999999999-12-30\" return i")) {
			var model = compiledFilterModel(sample, true);
			var decision = model.decisions().getFirst();
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql()).as(sample).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			assertThat(actual).as(sample).isEqualTo(
					new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(decision.id()));
		}
		for (String sample : List.of("for i in @\"2020-01-01\"..@\"2021-01-01\" return i",
				"for i in @\"2020-01-01\"..@\"2020-01-02\" return 1",
				"for i in @\"+999999999-12-30\"..@\"+999999999-12-31\" return i",
				"for i in @\"-999999999-01-02\"..@\"-999999999-01-01\" return i")) {
			var model = compiledFilterModel(sample, true);
			assertThat(SparkSqlDateLoopProof.value(model.decisions().getFirst())).as(sample).isNull();
		}
	}

	@Test
	void closedPathEqualityPreservesNestedListsAndMissingKeys() {
		for (String sample : List.of(
				"[{a: {b: 1}}, {a: {b: [2.1, 2.2]}}, {a: {b: 3}}].a.b = [{b: 1}, {b: [2.1, 2.2]}, {b: 3}].b",
				"[{b: 1}, {b: [2.1, 2.2]}, {b: 3}].b = [1, [2.1, 2.2], 3]",
				"[{b: 1}, {b: [2.1, 2.2]}].b = [1, 2.1, 2.2]", "[{b: 1}, {b: [2.1, 2.2]}].b != [1, 2.1, 2.2]",
				"[{b: null}, {}].b = [null, null]", "{a: null} = {}", "null = null")) {
			var model = compiledFilterModel(sample, true);
			var decision = model.decisions().getFirst();
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql()).as(sample).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			assertThat(actual).as(sample).isEqualTo(
					new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(decision.id()));
		}
		var ref = new RuntimeValueReference(0, SparkSqlHybridTest.ANY);
		assertThat(SparkSqlClosedCollectionProof.result(new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, ref,
				ref, RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)))).isNull();
	}

	@Test
	void closedContextCollectionsPreserveMissingKeysNullsAndMembership() {
		for (String sample : List.of("[{x: 1}, {y: 2}][y > 1]", "[{x: 1}, {y: 2}].y",
				"{a: \"foo\"} in [{b: \"bar\"}, {a: \"foo\"}]", "{c: \"baz\"} in [{b: \"bar\"}, {a: \"foo\"}]",
				"{a: null} in [{}]", "{} in [{a: null}]", "{a: null} in [{a: null}]", "[{x: 1}, {y: 2}].z",
				"[{x: 1}, {y: 2}][y > 99]", "[{x: 1}, {y: 2}][1]", "[{x: 1}, {y: 2}][-1]", "[{x: 1}, {y: 2}][3]")) {
			var model = compiledFilterModel(sample, true);
			var decision = model.decisions().getFirst();
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql()).as(sample).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			assertThat(actual).as(sample).isEqualTo(
					new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(decision.id()));
		}
		for (String sample : List.of("[{a: null}, {}][true]",
				"[{x: 99999999999999999999999999999999999999}, {x: 0.00000000000000000000000000000000000001}].x")) {
			var model = compiledFilterModel(sample, true);
			var decision = model.decisions().getFirst();
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql()).as(sample).isFalse();
			generated.registerUdfs(spark);
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			assertThat(actual).as(sample).isEqualTo(
					new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(decision.id()));
		}
		var oversized = new RuntimeListExpression(java.util.Collections.nCopies(65, SparkSqlHybridTest.number("1")),
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER));
		assertThat(
				SparkSqlClosedCollectionProof.result(new RuntimePathExpression(oversized, "x", SparkSqlHybridTest.ANY)))
				.isNull();
		assertThat(SparkSqlClosedCollectionProof.result(new RuntimePathExpression(
				new RuntimeValueReference(0, SparkSqlHybridTest.ANY), "x", SparkSqlHybridTest.ANY))).isNull();
	}

	@Test
	void dateComparisonsUseExactFieldsAcrossBoundsNullsAndSingletonCoercion() {
		var date = RuntimeType.scalar(RuntimeTypeKind.DATE);
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var reference = new RuntimeValueReference(0, date);
		var endpoint = new RuntimeConstant(RuntimeConstantKind.DATE, "2017-01-01", date);
		var text = new RuntimeValueReference(1, SparkSqlHybridTest.STRING);
		var before = new RuntimeFunctionCall("substring before", List.of(text, SparkSqlHybridTest.string(" ")),
				SparkSqlHybridTest.STRING);
		var after = new RuntimeFunctionCall("substring after", List.of(text, SparkSqlHybridTest.string(" ")),
				SparkSqlHybridTest.STRING);
		var values = Arrays.asList(java.time.LocalDate.MIN, java.time.LocalDate.of(0, 1, 1),
				java.time.LocalDate.of(2016, 12, 31), java.time.LocalDate.of(2017, 1, 1), java.time.LocalDate.MAX,
				List.of(java.time.LocalDate.of(2018, 1, 1)), List.of(), "2018-01-01", true,
				java.time.OffsetDateTime.parse("2017-01-01T00:00:00.123456789+14:00"), null);
		for (RuntimeBinaryOperator op : List.of(RuntimeBinaryOperator.EQUAL, RuntimeBinaryOperator.NOT_EQUAL,
				RuntimeBinaryOperator.LESS, RuntimeBinaryOperator.LESS_EQUAL, RuntimeBinaryOperator.GREATER,
				RuntimeBinaryOperator.GREATER_EQUAL)) {
			for (boolean reversed : List.of(false, true)) {
				RuntimeExpression fixed = reversed
						? new RuntimeFunctionCall("date", List.of(SparkSqlHybridTest.string("2017-01-01")), date)
						: endpoint;
				var comparison = new RuntimeBinaryExpression(op, reversed ? fixed : reference,
						reversed ? reference : fixed, bool);
				var conditional = new RuntimeConditionalExpression(comparison, before, after,
						SparkSqlHybridTest.STRING);
				var model = new RuntimeModel(
						List.of(new RuntimeInput(5, 0, date), new RuntimeInput(6, 1, SparkSqlHybridTest.STRING)),
						List.of(new RuntimeDecision(10, 2, bool, List.of(5), Optional.of(comparison)),
								new RuntimeDecision(11, 3, SparkSqlHybridTest.STRING, List.of(5, 6),
										Optional.of(conditional))),
						List.of(), List.of(10, 11), 4);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
				assertThat(generated.capabilities().get(11).nativeSql()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				for (Object value : values) {
					Map<Integer, Object> inputs = new HashMap<>();
					inputs.put(0, value);
					inputs.put(1, "alpha beta");
					var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
					assertThat((byte[]) row.get(0))
							.isEqualTo((byte[]) SparkSqlFeelValueCodec.inputRow(model, inputs).get(0));
					spark.createDataFrame(List.of(row), generated.inputSchema())
							.createOrReplaceTempView("hybrid_inputs");
					var expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs);
					for (var decision : model.decisions())
						assertThat(spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql"))
								.head().get(0)).as("%s reversed=%s value=%s", op, reversed, value)
								.isEqualTo(expected.decisionValue(decision.id()));
				}
			}
		}
	}

	@Test
	void conditionalScoreListsPreserveEmptyBranchesAndDecimalBounds() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var list = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY);
		var empty = new RuntimeListExpression(List.of(), list);
		for (RuntimeExpression second : List.of(SparkSqlHybridTest.number("2"), SparkSqlHybridTest.string("two"),
				SparkSqlHybridTest.number("0.00000000000000000000000000000000000001"))) {
			RuntimeExpression first = second instanceof RuntimeConstant c && c.value().startsWith("0.")
					? SparkSqlHybridTest.number("99999999999999999999999999999999999999")
					: SparkSqlHybridTest.number("1");
			var a = new RuntimeConditionalExpression(new RuntimeValueReference(0, bool),
					new RuntimeListExpression(List.of(first), list), empty, SparkSqlHybridTest.ANY);
			var b = new RuntimeConditionalExpression(new RuntimeValueReference(1, bool),
					new RuntimeListExpression(List.of(second), list), empty, SparkSqlHybridTest.ANY);
			var expression = new RuntimeFunctionCall("flatten", List.of(new RuntimeListExpression(List.of(a, b), list)),
					list);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, bool), new RuntimeInput(6, 1, bool)),
					List.of(new RuntimeDecision(10, 2, SparkSqlHybridTest.ANY, List.of(5, 6), Optional.of(expression))),
					List.of(), List.of(10), 3);
			var generated = SparkSqlHybridTest.generate(model);
			boolean nativeExpected = second.equals(SparkSqlHybridTest.number("2"));
			assertThat(generated.capabilities().get(10).nativeSql()).isEqualTo(nativeExpected);
			if (!nativeExpected)
				continue;
			for (Boolean left : Arrays.asList(true, false, null)) {
				for (Boolean right : Arrays.asList(true, false, null)) {
					Map<Integer, Object> inputs = new HashMap<>();
					inputs.put(0, left);
					inputs.put(1, right);
					var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
					spark.createDataFrame(List.of(row), generated.inputSchema())
							.createOrReplaceTempView("hybrid_inputs");
					Object actual = SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get("Decision_2.sql")).head().get(0));
					assertThat(actual).usingRecursiveComparison()
							.withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(
									new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10));
				}
			}
		}
	}

	@Test
	void inlineNestedInputComponentsRetainNativeLayoutsAndNullSemantics() {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="nested" name="nested" namespace="urn:nested">
				  <itemDefinition id="shape" name="ApplicantType">
				    <itemComponent name="Monthly">
				      <itemComponent name="Income"><typeRef>number</typeRef></itemComponent>
				      <itemComponent name="Expenses"><typeRef>number</typeRef></itemComponent>
				    </itemComponent>
				  </itemDefinition>
				  <inputData id="in" name="Applicant"><variable name="Applicant" typeRef="ApplicantType"/></inputData>
				  <decision id="out" name="Affordable"><variable name="Affordable" typeRef="boolean"/>
				    <informationRequirement><requiredInput href="#in"/></informationRequirement>
				    <literalExpression><text>Applicant.Monthly.Income &gt; Applicant.Monthly.Expenses</text></literalExpression>
				  </decision>
				</definitions>
				""";
		var source = new io.finmsg.dmn.compiler.DmnSource(io.finmsg.dmn.compiler.DmnSourceId.of("memory:nested"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var compilation = new io.finmsg.dmn.compiler.DmnCompiler().compile(source,
				new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(List.of()),
				io.finmsg.dmn.compiler.DmnCompilerOptions.optimized());
		assertThat(compilation.isSuccess()).isTrue();
		var model = compilation.optimizedRuntimeModel().orElseThrow().model();
		assertThat(model.inputs().getFirst().type().fieldLayout().getFirst().type().kind())
				.isEqualTo(RuntimeTypeKind.CONTEXT);
		var decision = model.decisions().getFirst();
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(decision.id()).nativeSql()).isTrue();
		assertThat(generated.udfs()).isEmpty();
		for (Object monthly : Arrays.asList(Map.of("Income", new BigDecimal("20"), "Expenses", BigDecimal.TEN),
				Map.of("Income", new BigDecimal("5"), "Expenses", BigDecimal.TEN), Map.of("Expenses", BigDecimal.TEN),
				null)) {
			Map<String, Object> applicant = new HashMap<>();
			applicant.put("Monthly", monthly);
			var inputs = Map.<Integer, Object>of(model.inputs().getFirst().valueSlot(), applicant);
			var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
			spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			Object actual = spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head()
					.get(0);
			assertThat(actual).isEqualTo(
					new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(decision.id()));
		}
	}

	@Test
	void stringProjectionsPreserveOpaqueInputsAndWrongTypeNulls() {
		var reference = new RuntimeValueReference(0, SparkSqlHybridTest.ANY);
		var concat = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, SparkSqlHybridTest.string("foo "),
				reference, SparkSqlHybridTest.ANY);
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, SparkSqlHybridTest.ANY)),
				List.of(new RuntimeDecision(10, 1, SparkSqlHybridTest.ANY, List.of(5), Optional.of(concat)),
						new RuntimeDecision(11, 2, SparkSqlHybridTest.ANY, List.of(5), Optional.of(reference))),
				List.of(), List.of(10, 11), 3);
		var generated = new DmnSparkSqlGenerator().generate(SparkSqlHybridTest.optimized(model),
				DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true,
						Map.of(0, "__dmn_string_0", 1, "joined", 2, "original")));
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		assertThat(generated.capabilities().get(11).nativeSql()).isFalse();
		assertThat(generated.inputSchema().fields()[1].name()).isEqualTo("__dmn_string_0_");
		generated.registerUdfs(spark);
		for (Object value : Arrays.asList("baz", "O'Reilly\\tail", "", null, new BigDecimal("1234"), true, List.of("x"),
				java.time.LocalDate.of(2026, 10, 19))) {
			Map<Integer, Object> inputs = new HashMap<>();
			inputs.put(0, value);
			var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
			assertThat((byte[]) row.get(0)).isEqualTo((byte[]) SparkSqlFeelValueCodec.inputRow(model, inputs).get(0));
			spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			var expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs);
			Object actual = spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0);
			assertThat(actual).isEqualTo(expected.decisionValue(10));
			Row all = spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head();
			assertThat(SparkSqlFeelValueCodec.fromSpark(all.get(0))).isEqualTo(expected.decisionValue(10));
			assertThat(SparkSqlFeelValueCodec.fromSpark(all.get(1))).isEqualTo(value);
		}
		var ambiguous = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, reference, reference,
				SparkSqlHybridTest.ANY);
		var decision = new RuntimeDecision(12, 1, SparkSqlHybridTest.ANY, List.of(5), Optional.of(ambiguous));
		assertThat(SparkSqlStringInput.inputSlots(model, decision)).isEmpty();
	}

	@Test
	void boxedJavaDescriptorsPreserveArityNamedBindingAndCoercion() {
		var fixture = literalJavaFixture("java.lang.Math", "max(double, double)", 2);
		var parameters = List.of(new RuntimeFunctionParameter("left", 0, SparkSqlHybridTest.NUMBER),
				new RuntimeFunctionParameter("right", 1, SparkSqlHybridTest.NUMBER));
		var function = new RuntimeFunctionDefinition(parameters, fixture.body(), false, RuntimeType
				.function(List.of(SparkSqlHybridTest.NUMBER, SparkSqlHybridTest.NUMBER), SparkSqlHybridTest.ANY));
		var calls = new ArrayList<RuntimeInvocationExpression>();
		for (List<RuntimeExpression> arguments : List.<List<RuntimeExpression>>of(
				List.of(SparkSqlHybridTest.number("123"), SparkSqlHybridTest.number("456")),
				List.of(SparkSqlHybridTest.number("123")),
				List.of(SparkSqlHybridTest.string("wrong"), SparkSqlHybridTest.number("456"))))
			calls.add(new RuntimeInvocationExpression(Optional.empty(), Optional.of(function), List.of(), arguments,
					SparkSqlHybridTest.ANY));
		for (String firstName : List.of("right", "unknown"))
			calls.add(new RuntimeInvocationExpression(Optional.empty(), Optional.of(function),
					List.of(new RuntimeNamedArgument(firstName, SparkSqlHybridTest.number("456")),
							new RuntimeNamedArgument("left", SparkSqlHybridTest.number("123"))),
					List.of(), SparkSqlHybridTest.ANY));
		for (RuntimeInvocationExpression call : calls) {
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(call))),
					List.of(), List.of(10), 1);
			var generated = generateApprovedJava(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime(JAVA_TEST_POLICY).evaluate(model, Map.of())
							.decisionValue(10));
		}
	}

	@Test
	void closedPartialLoopsPreserveSharedRecurrencePrecision() {
		for (String sample : List.of("for i in 0..4 return if i = 0 then 1 else i * partial[-1]",
				"for i in 1..6 return if i = 1 then 1 else partial[-1] / 3",
				"for i in 4..1 return if i = 4 then 0.1 else partial[-1] + 0.2",
				"for i in [1, 3, 5] return if i = 1 then 2 else partial[-1] * i")) {
			var model = compiledFilterModel(sample, true);
			var decision = model.decisions().getFirst();
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql()).as(sample).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			assertThat(actual).as(sample).usingRecursiveComparison()
					.withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
							.decisionValue(decision.id()));
		}
		for (String sample : List.of("for i in 0..64 return if i = 0 then 1 else partial[-1] + 1",
				"for i in 0..4 return if i = 0 then 2 else partial[-1] * partial[-1]",
				"for i in 0..40 return if i = 0 then 1 else partial[-1] * 1000")) {
			var model = compiledFilterModel(sample, true);
			assertThat(SparkSqlHybridTest.generate(model).capabilities().get(model.decisions().getFirst().id())
					.nativeSql()).as(sample).isFalse();
		}
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
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, input, generated.inputSchema())),
					generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
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
	void durationBetweenPreservesSubtypesNanosecondsAndInclusiveBounds() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var duration = RuntimeType.scalar(RuntimeTypeKind.DURATION);
		for (String[] sample : List.of(new String[]{"P1D", "P2D", "P4D"}, new String[]{"P2D", "P2D", "P4D"},
				new String[]{"P3D", "P2D", "P4D"}, new String[]{"P4D", "P2D", "P4D"}, new String[]{"P5D", "P2D", "P4D"},
				new String[]{"PT0.000000002S", "PT0.000000001S", "PT0.000000003S"},
				new String[]{"-PT0.000000001S", "-PT0.000000002S", "PT0S"},
				new String[]{"PT9223372036854775806S", "PT9223372036854775805S", "PT9223372036854775807S"},
				new String[]{"P1Y", "P2Y", "P4Y"}, new String[]{"P2Y", "P2Y", "P4Y"}, new String[]{"P3Y", "P2Y", "P4Y"},
				new String[]{"P4Y", "P2Y", "P4Y"}, new String[]{"P5Y", "P2Y", "P4Y"},
				new String[]{"-P1M", "-P2M", "P0M"}, new String[]{"P1Y", "P2Y", "P0M"})) {
			for (boolean constructors : List.of(false, true)) {
				var operands = new ArrayList<RuntimeExpression>();
				for (String text : sample)
					operands.add(constructors
							? new RuntimeFunctionCall("duration", List.of(SparkSqlHybridTest.string(text)), duration)
							: new RuntimeConstant(RuntimeConstantKind.DURATION, text, duration));
				var expression = new RuntimeBetweenExpression(operands.get(0), operands.get(1), operands.get(2), bool);
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0))
						.as("%s", Arrays.toString(sample))
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
		var day = new RuntimeConstant(RuntimeConstantKind.DURATION, "P1D", duration);
		var month = new RuntimeConstant(RuntimeConstantKind.DURATION, "P1M", duration);
		assertThat(
				SparkSqlExpressionEmitter.nativeDurationBetweenSql(new RuntimeBetweenExpression(day, month, day, bool)))
				.isNull();
		assertThat(SparkSqlExpressionEmitter.nativeDurationBetweenSql(
				new RuntimeBetweenExpression(new RuntimeValueReference(0, duration), day, day, bool))).isNull();
	}

	@Test
	void temporalTypeChecksPreserveOffsetsNamedZonesAndRangeDurationSubtypes() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var date = RuntimeType.scalar(RuntimeTypeKind.DATE);
		var time = RuntimeType.scalar(RuntimeTypeKind.TIME);
		var dateTime = RuntimeType.scalar(RuntimeTypeKind.DATE_TIME);
		var duration = RuntimeType.scalar(RuntimeTypeKind.DURATION);
		var operands = List.<RuntimeExpression>of(
				new RuntimeConstant(RuntimeConstantKind.TIME, "10:30:11.123456789+11:00", time),
				new RuntimeConstant(RuntimeConstantKind.TIME, "10:30:11@Australia/Melbourne", time),
				new RuntimeConstant(RuntimeConstantKind.DATE_TIME, "2018-12-08T10:30:11.123456789+11:00", dateTime),
				new RuntimeConstant(RuntimeConstantKind.DATE_TIME, "2018-12-08T10:30:11@Australia/Melbourne", dateTime),
				new RuntimeConstant(RuntimeConstantKind.DATE, "-2018-12-06", date),
				new RuntimeFunctionCall("now", List.of(), dateTime), new RuntimeFunctionCall("today", List.of(), date),
				new RuntimeFunctionCall("range", List.of(SparkSqlHybridTest.string("[@\"P1D\"..@\"P2D\"]")),
						RuntimeType.element(RuntimeTypeKind.RANGE, duration)),
				new RuntimeFunctionCall("range", List.of(SparkSqlHybridTest.string("[@\"P1Y\"..@\"P2Y\"]")),
						RuntimeType.element(RuntimeTypeKind.RANGE, duration)));
		for (RuntimeExpression operand : operands) {
			for (RuntimeType target : List.of(SparkSqlHybridTest.ANY, date, time, dateTime,
					RuntimeType.element(RuntimeTypeKind.RANGE, RuntimeType.scalar(RuntimeTypeKind.DAYS_TIME_DURATION)),
					RuntimeType.element(RuntimeTypeKind.RANGE,
							RuntimeType.scalar(RuntimeTypeKind.YEARS_MONTHS_DURATION)))) {
				var expression = new RuntimeInstanceOfExpression(operand, target, bool);
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0))
						.as("%s instance of %s", operand, target)
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
		assertThat(SparkSqlExpressionEmitter.nativeTemporalInstanceOfSql(
				new RuntimeInstanceOfExpression(new RuntimeValueReference(0, time), time, bool))).isNull();
		assertThat(SparkSqlExpressionEmitter.nativeTemporalInstanceOfSql(new RuntimeInstanceOfExpression(
				new RuntimeFunctionCall("now", List.of(SparkSqlHybridTest.number("1")), dateTime), dateTime, bool)))
				.isNull();
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
	void durationAbsPreservesSubtypeNanosecondsAndNormalizedSigns() {
		var duration = RuntimeType.scalar(RuntimeTypeKind.DURATION);
		for (String text : List.of("-P1D", "P1D", "-PT0.000000001S", "PT0S", "-PT1.999999999S",
				"PT9223372036854775807S", "-P1Y", "P1Y", "P0M", "P1Y-13M", "-P1Y2M")) {
			for (int form = 0; form < 3; form++) {
				RuntimeExpression operand = form == 0
						? new RuntimeConstant(RuntimeConstantKind.DURATION, text, duration)
						: new RuntimeFunctionCall("duration", List.of(SparkSqlHybridTest.string(text)), duration);
				RuntimeExpression expression = form == 2
						? new RuntimeInvocationExpression(Optional.of("abs"), Optional.empty(),
								List.of(new RuntimeNamedArgument("n", operand)), List.of(), duration)
						: new RuntimeFunctionCall("abs", List.of(operand), duration);
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, duration, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0), duration))
						.as("abs(%s), form %s", text, form)
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
		assertThat(SparkSqlExpressionEmitter.nativeDurationAbsOperand(
				new RuntimeFunctionCall("abs", List.of(new RuntimeValueReference(0, duration)), duration))).isNull();
		assertThat(SparkSqlExpressionEmitter.nativeDurationAbsOperand(new RuntimeFunctionCall("abs",
				List.of(new RuntimeConstant(RuntimeConstantKind.DURATION, "PT-9223372036854775808S", duration)),
				duration))).isNull();
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
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
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
				spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
						generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
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
			assertThat(generated.capabilities().get(10).nativeSql()).isEqualTo(expression == constrained);
			generated.registerUdfs(spark);
			var inputs = Map.<Integer, Object>of(0, Map.of("a", "old"), 1, "dynamic");
			spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
					generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
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
	void literalListBuiltinsPreserveNullsValidationAndArgumentBinding() {
		var any = SparkSqlHybridTest.ANY;
		var list = RuntimeType.element(RuntimeTypeKind.LIST, any);
		var nil = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		var yes = new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", RuntimeType.scalar(RuntimeTypeKind.BOOLEAN));
		var no = new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", RuntimeType.scalar(RuntimeTypeKind.BOOLEAN));
		for (String name : List.of("all", "any", "string join", "product")) {
			var expressions = new ArrayList<RuntimeExpression>();
			for (List<RuntimeExpression> elements : List.<List<RuntimeExpression>>of(List.of(), List.of(nil),
					List.of(yes, nil, no), List.of(yes, SparkSqlHybridTest.number("123"), no),
					List.of(SparkSqlHybridTest.string("a"), nil, SparkSqlHybridTest.string("c")),
					List.of(SparkSqlHybridTest.number("2"), SparkSqlHybridTest.number("3")),
					List.of(SparkSqlHybridTest.number("2"), nil))) {
				var source = new RuntimeListExpression(elements, list);
				expressions.add(new RuntimeFunctionCall(name, List.of(source), any));
				expressions.add(new RuntimeInvocationExpression(Optional.of(name), Optional.empty(),
						List.of(new RuntimeNamedArgument("list", source)), List.of(), any));
			}
			expressions.add(new RuntimeFunctionCall(name, List.of(), any));
			expressions.add(new RuntimeFunctionCall(name, List.of(yes, nil, no), any));
			expressions.add(new RuntimeInvocationExpression(Optional.of(name), Optional.empty(),
					List.of(new RuntimeNamedArgument("wrong", yes)), List.of(), any));
			expressions.add(new RuntimeInvocationExpression(Optional.of(name), Optional.empty(),
					List.of(new RuntimeNamedArgument("list", yes), new RuntimeNamedArgument("list", no)), List.of(),
					any));
			expressions.add(new RuntimeInvocationExpression(Optional.of(name), Optional.empty(),
					List.of(new RuntimeNamedArgument("list", yes)), List.of(yes), any));
			if (name.equals("string join")) {
				var source = new RuntimeListExpression(
						List.of(SparkSqlHybridTest.string("a"), nil, SparkSqlHybridTest.string("c")), list);
				expressions.add(new RuntimeFunctionCall(name, List.of(source, SparkSqlHybridTest.string(" / ")), any));
				expressions.add(new RuntimeFunctionCall(name,
						List.of(source, nil, SparkSqlHybridTest.string("["), SparkSqlHybridTest.string("]")), any));
			}
			for (RuntimeExpression expression : expressions) {
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, any, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0), any);
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10);
				if (actual instanceof Number && expected instanceof Number)
					assertThat(new BigDecimal(actual.toString()))
							.isEqualByComparingTo(new BigDecimal(expected.toString()));
				else
					assertThat(actual).isEqualTo(expected);
			}
		}
		var dynamic = new RuntimeFunctionCall("all", List.of(new RuntimeValueReference(0, any)), any);
		assertThat(SparkSqlLiteralBuiltinProof.result(dynamic)).isNull();
		var oversized = new RuntimeFunctionCall("all",
				List.of(new RuntimeListExpression(Collections.nCopies(513, yes), list)), any);
		assertThat(SparkSqlLiteralBuiltinProof.result(oversized)).isNull();
		var precision = new RuntimeFunctionCall("product", List.of(SparkSqlHybridTest.number("1E-50")), any);
		assertThat(SparkSqlLiteralBuiltinProof.result(precision)).isNull();
		var overflow = new RuntimeFunctionCall("product",
				List.of(SparkSqlHybridTest.number("99999999999999999999999999999999999999"),
						SparkSqlHybridTest.number("9")),
				any);
		assertThat(SparkSqlLiteralBuiltinProof.result(overflow)).isNull();
		var scalar = new RuntimeFunctionCall("all", List.of(yes), any);
		var incompatible = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, list, List.of(), Optional.of(scalar))), List.of(), List.of(10), 1);
		assertThat(SparkSqlHybridTest.generate(incompatible).capabilities().get(10).nativeSql()).isFalse();
	}

	@Test
	void literalGetEntriesPreservesValidationAndSharedResultTyping() {
		var any = SparkSqlHybridTest.ANY;
		var list = RuntimeType.element(RuntimeTypeKind.LIST, any);
		var source = nativeContextFixture("first", SparkSqlHybridTest.string("alpha"), "second",
				SparkSqlHybridTest.string("beta"));
		var nil = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		var expressions = new ArrayList<RuntimeExpression>();
		for (RuntimeExpression input : List.of(source, nativeContextFixture(), nil, SparkSqlHybridTest.number("123"),
				new RuntimeListExpression(List.of(SparkSqlHybridTest.number("1")), list),
				nativeContextFixture("nested", nativeContextFixture("a", SparkSqlHybridTest.number("2")))))
			expressions.add(new RuntimeFunctionCall("get entries", List.of(input), list));
		expressions.add(new RuntimeFunctionCall("get entries", List.of(), list));
		expressions.add(new RuntimeFunctionCall("get entries", List.of(source, source), list));
		for (List<RuntimeNamedArgument> arguments : List.of(List.of(new RuntimeNamedArgument("m", source)),
				List.of(new RuntimeNamedArgument("map", source)),
				List.of(new RuntimeNamedArgument("m", nil), new RuntimeNamedArgument("m", source))))
			expressions.add(new RuntimeInvocationExpression(Optional.of("get entries"), Optional.empty(), arguments,
					List.of(), list));
		expressions.add(new RuntimeInvocationExpression(Optional.of("get entries"), Optional.empty(),
				List.of(new RuntimeNamedArgument("m", source)), List.of(source), list));
		for (RuntimeType declared : List.of(any, list)) {
			for (RuntimeExpression expression : expressions) {
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, declared, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0), declared);
				assertThat(actual)
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
		// Different value kinds cannot share a Spark array element layout.
		var heterogeneous = new RuntimeFunctionCall("get entries", List.of(nativeContextFixture("number",
				SparkSqlHybridTest.number("1"), "text", SparkSqlHybridTest.string("one"))), list);
		var model = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, list, List.of(), Optional.of(heterogeneous))), List.of(),
				List.of(10), 1);
		assertThat(SparkSqlHybridTest.generate(model).capabilities().get(10).nativeSql()).isFalse();
	}

	@Test
	void nestedLiteralContextBuiltinsPreserveSharedBindingAndPathValidation() {
		for (String expression : List.of("context put({}, \"\", 1)", "context put(context: {}, key: \"\", value: true)",
				"get entries(context put({a: 1}, \"b\", 2))",
				"context put({x:1, y:{a:0}}, [\"y\",\"a\",\"b\",\"c\"], 2)",
				"context put(context put({a:1}, \"b\", true), \"a\", 3)",
				"context merge([context put({}, \"a\", 1), context put({}, \"b\", \"two\")])",
				"get entries(context merge([{a:1}, {b:2}]))", "context put({a:{b:1}}, [\"a\",\"b\"], null)",
				"context put(context: {}, key: \"a\", value: 1)",
				"context put(context: {a:{b:1}}, keys: [\"a\",\"b\"], value: 2)",
				"context put(context: {}, wrong: \"a\", value: 1)")) {
			var model = compiledFilterModel(expression);
			var generated = SparkSqlHybridTest.generate(model);
			var decision = model.decisions().getFirst();
			assertThat(generated.capabilities().get(decision.id()).nativeSql())
					.as("%s: %s", expression, generated.capabilities()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			var runtimeModel = compiledFilterModel(expression, true);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(runtimeModel, Map.of())
					.decisionValue(runtimeModel.decisions().getFirst().id());
			assertThat(actual).as(expression).isEqualTo(expected);
		}
		for (boolean nested : List.of(false, true)) {
			var source = nested
					? nativeContextFixture("a", nativeContextFixture("b", SparkSqlHybridTest.number("1")))
					: nativeContextFixture("a", SparkSqlHybridTest.number("1"));
			RuntimeExpression key = nested
					? new RuntimeListExpression(List.of(SparkSqlHybridTest.string("a"), SparkSqlHybridTest.string("b")),
							RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.STRING))
					: SparkSqlHybridTest.string("a");
			var result = new RuntimeFunctionCall("context put",
					List.of(new RuntimeLocalReference(0, source.type()), key, SparkSqlHybridTest.number("2")),
					SparkSqlHybridTest.ANY);
			var boxed = new RuntimeContextExpression(
					List.of(new RuntimeContextEntry("original", 0, source), new RuntimeContextEntry("", -1, result)),
					SparkSqlHybridTest.ANY);
			var model = new RuntimeModel(List.of(), List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY,
					List.of(), Optional.of(boxed), Optional.empty(), 1)), List.of(), List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
		}
	}

	@Test
	void flattenPreservesDynamicNullsOrderAndHomogeneousLeafKinds() {
		for (RuntimeType element : List.of(SparkSqlHybridTest.STRING, SparkSqlHybridTest.NUMBER,
				RuntimeType.scalar(RuntimeTypeKind.BOOLEAN))) {
			var listType = RuntimeType.element(RuntimeTypeKind.LIST, element);
			Object first = element.kind() == RuntimeTypeKind.STRING
					? "a"
					: element.kind() == RuntimeTypeKind.NUMBER ? new BigDecimal("1") : true;
			Object second = element.kind() == RuntimeTypeKind.STRING
					? "b"
					: element.kind() == RuntimeTypeKind.NUMBER ? new BigDecimal("2") : false;
			var constant = new RuntimeConstant(element.kind() == RuntimeTypeKind.STRING
					? RuntimeConstantKind.STRING
					: element.kind() == RuntimeTypeKind.NUMBER
							? RuntimeConstantKind.NUMBER
							: RuntimeConstantKind.BOOLEAN,
					first.toString(), element);
			var anyList = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY);
			var mixed = new RuntimeListExpression(
					List.of(new RuntimeValueReference(0, listType), new RuntimeValueReference(1, element),
							new RuntimeListExpression(List.of(constant, new RuntimeConstant(RuntimeConstantKind.NULL,
									"null", RuntimeType.scalar(RuntimeTypeKind.NULL))), anyList)),
					anyList);
			for (RuntimeType declared : List.of(SparkSqlHybridTest.ANY, listType)) {
				var call = new RuntimeFunctionCall("flatten", List.of(mixed), anyList);
				var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, listType), new RuntimeInput(6, 1, element)),
						List.of(new RuntimeDecision(10, 2, declared, List.of(5, 6), Optional.of(call))), List.of(),
						List.of(10), 3);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).as("%s", generated.capabilities()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				for (Object values : Arrays.asList(List.of(first, second), Arrays.asList(first, null, second),
						List.of(), null)) {
					var inputs = new HashMap<Integer, Object>();
					inputs.put(0, values);
					inputs.put(1, values == null ? null : second);
					spark.createDataFrame(
							List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
							generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
					Object actual = SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get("Decision_2.sql")).head().get(0));
					assertThat(actual).isEqualTo(
							new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10));
				}
			}
		}
		for (String expression : List.of("flatten([1, \"two\"])",
				"flatten([[99999999999999999999999999999999999999], [0.00000000000000000000000000000000000001]])")) {
			var model = compiledFilterModel(expression, true);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(model.decisions().getFirst().id()).nativeSql()).isFalse();
			generated.registerUdfs(spark);
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			var decision = model.decisions().getFirst();
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			assertThat(actual).isEqualTo(
					new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(decision.id()));
		}
	}

	@Test
	void contextFunctionLibrariesRetainLosslessNumericFallback() {
		for (String arguments : List.of("a,b", "b:b,a:a")) {
			String xml = """
					<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="library" name="library" namespace="urn:library">
					  <inputData id="a" name="a"><variable name="a" typeRef="number"/></inputData>
					  <inputData id="b" name="b"><variable name="b" typeRef="number"/></inputData>
					  <decision id="functions" name="functions"><variable name="functions"/>
					    <context>
					      <contextEntry><variable name="add"/><literalExpression><text>function(a,b) a+b</text></literalExpression></contextEntry>
					      <contextEntry><variable name="divide"/><literalExpression><text>function(a,b) if b = 0 then null else a/b</text></literalExpression></contextEntry>
					      <contextEntry><variable name="captured"/><literalExpression><text>function(a,b) add(a,b)</text></literalExpression></contextEntry>
					    </context>
					  </decision>
					  <decision id="result" name="result"><variable name="result"/>
					    <informationRequirement><requiredInput href="#a"/></informationRequirement>
					    <informationRequirement><requiredInput href="#b"/></informationRequirement>
					    <informationRequirement><requiredDecision href="#functions"/></informationRequirement>
					    <literalExpression><text>{sum: functions.add(%s), quotient: functions.divide(%s), captured: functions.captured(%s)}</text></literalExpression>
					  </decision>
					</definitions>
					"""
					.formatted(arguments, arguments, arguments);
			var source = new io.finmsg.dmn.compiler.DmnSource(io.finmsg.dmn.compiler.DmnSourceId.of("memory:library"),
					xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			var compilation = new io.finmsg.dmn.compiler.DmnCompiler().compile(source,
					new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(List.of()),
					io.finmsg.dmn.compiler.DmnCompilerOptions.optimized());
			assertThat(compilation.isSuccess()).isTrue();
			var model = compilation.optimizedRuntimeModel().orElseThrow().model();
			var decision = model.decisions().getLast();
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql()).as("%s", generated.capabilities())
					.isFalse();
			String sql = generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql");
			generated.registerUdfs(spark);
			for (Object[] values : List.of(new Object[]{new BigDecimal("2"), new BigDecimal("3")},
					new Object[]{new BigDecimal("0.1"), new BigDecimal("0.2")},
					new Object[]{new BigDecimal("4"), BigDecimal.ZERO},
					new Object[]{new BigDecimal("-10"), new BigDecimal("2")},
					new Object[]{null, new BigDecimal("2")})) {
				var inputs = new HashMap<Integer, Object>();
				inputs.put(model.inputs().get(0).valueSlot(), values[0]);
				inputs.put(model.inputs().get(1).valueSlot(), values[1]);
				spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
						generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec.fromSpark(spark.sql(sql).head().get(0));
				assertThat(actual).isEqualTo(
						new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(decision.id()));
			}
		}
	}

	@Test
	void boundedQuantifiersPreserveNullTruthNestedBindingsAndCapture() {
		for (String sample : List.of("every x in [1,2,3] satisfies x > 0", "some x in [1,null,3] satisfies x > 2",
				"every x in [1,null,3] satisfies x > 0", "some x in [] satisfies x > 0",
				"every x in [] satisfies x > 0", "some x in [1,2], y in [3,4] satisfies x+y = 6",
				"every x in [1,2], y in [3,4] satisfies x+y > 0",
				"(function(n:number) every x in [1,2,3] satisfies x > n)(2)",
				"(function(n:number) some x in [1,2] satisfies (function(y:number) y>n)(x))(1)",
				"every x in [true,null] satisfies x", "some x in [true,null] satisfies x",
				"some x in [null] satisfies x = true", "every x in [null] satisfies x = true")) {
			var model = compiledFilterModel(sample, true);
			var decision = model.decisions().getFirst();
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql())
					.as("%s: %s", sample, generated.capabilities()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			assertThat(actual).as(sample).isEqualTo(
					new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(decision.id()));
		}
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var listType = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER);
		for (RuntimeQuantifier quantifier : RuntimeQuantifier.values()) {
			var values = new RuntimeListExpression(List.of(SparkSqlHybridTest.number("0"),
					SparkSqlHybridTest.number("1"), SparkSqlHybridTest.number("2")), listType);
			var source = new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(values));
			var expression = new RuntimeQuantifiedExpression(quantifier,
					List.of(new RuntimeQuantifiedBinding(0, new RuntimeValueReference(0, listType))),
					new RuntimeFunctionCall("even", List.of(new RuntimeLocalReference(0, SparkSqlHybridTest.NUMBER)),
							bool),
					bool);
			var model = new RuntimeModel(List.of(), List.of(source,
					new RuntimeDecision(11, 1, bool, List.of(10), Optional.of(expression), Optional.empty(), 1)),
					List.of(), List.of(10, 11), 2);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(11).nativeSql()).as("%s", generated.capabilities()).isTrue();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
			assertThat(actual)
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(11));
		}
	}

	@Test
	void pureLiteralJavaCallsPreserveSharedConversionsAndFallbackGuards() {
		var nil = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		var samples = List.<Object[]>of(
				new Object[]{"java.lang.Math", "max(double, double)",
						List.of(SparkSqlHybridTest.number("123.45"), SparkSqlHybridTest.number("456.78")), true},
				new Object[]{"java.lang.Math", "max(double, double)", List.of(nil, SparkSqlHybridTest.number("456")),
						true},
				new Object[]{"java.lang.Math", "cos(double)", List.of(SparkSqlHybridTest.number("123")), true},
				new Object[]{"java.lang.Math", "foo(double)", List.of(SparkSqlHybridTest.number("123")), true},
				new Object[]{"java.lang.Math", "max(java.lang.String, java.lang.String)",
						List.of(SparkSqlHybridTest.string("123"), SparkSqlHybridTest.string("456")), true},
				new Object[]{"java.lang.Math", "max(int,int)",
						List.of(SparkSqlHybridTest.number("123"), SparkSqlHybridTest.number("456")), true},
				new Object[]{"java.lang.Math", "max(float,float)",
						List.of(SparkSqlHybridTest.number("123.46"), SparkSqlHybridTest.number("456.78")), true},
				new Object[]{"java.lang.Short", "valueOf(short)", List.of(SparkSqlHybridTest.number("123")), true},
				new Object[]{"java.lang.Byte", "valueOf(byte)", List.of(SparkSqlHybridTest.number("3")), true},
				new Object[]{"java.lang.String", "valueOf(char)", List.of(SparkSqlHybridTest.string("a")), true},
				new Object[]{"java.lang.String", "valueOf(char)", List.of(SparkSqlHybridTest.string("abc")), true},
				new Object[]{"java.lang.Integer", "valueOf(java.lang.String)",
						List.of(SparkSqlHybridTest.string("123")), true},
				new Object[]{"java.lang.Float", "valueOf(java.lang.String)",
						List.of(SparkSqlHybridTest.string("1234.56")), true},
				new Object[]{"java.lang.Double", "valueOf(java.lang.String)",
						List.of(SparkSqlHybridTest.string("1234.56")), true},
				new Object[]{"java.lang.String", "format(java.lang.String, [Ljava.lang.Object;)",
						List.of(SparkSqlHybridTest.string("foo %s"), SparkSqlHybridTest.string("bar")), true},
				new Object[]{"java.lang.String", "format (java.lang.String, [Ljava.lang.Object;)",
						List.of(SparkSqlHybridTest.string("%f"), SparkSqlHybridTest.number("1.25")), false},
				new Object[]{"java.lang.Math", "pow(double,double)",
						List.of(SparkSqlHybridTest.number("10"), SparkSqlHybridTest.number("100")), false});
		for (Object[] sample : samples) {
			@SuppressWarnings("unchecked")
			List<RuntimeExpression> arguments = (List<RuntimeExpression>) sample[2];
			var function = literalJavaFixture((String) sample[0], (String) sample[1], arguments.size());
			var call = new RuntimeInvocationExpression(Optional.empty(), Optional.of(function), List.of(), arguments,
					SparkSqlHybridTest.ANY);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(call))),
					List.of(), List.of(10), 1);
			var generated = generateApprovedJava(model);
			assertThat(generated.capabilities().get(10).nativeSql())
					.as("%s.%s: %s", sample[0], sample[1], generated.capabilities()).isEqualTo(sample[3]);
			generated.registerUdfs(spark);
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0));
			assertThat(actual).isEqualTo(
					new io.finmsg.dmn.runtime.DmnRuntime(JAVA_TEST_POLICY).evaluate(model, Map.of()).decisionValue(10));
		}
		var formatFunction = literalJavaFixture("java.lang.String", "format(java.lang.String, [Ljava.lang.Object;)", 2);
		for (RuntimeInvocationExpression call : List.of(
				new RuntimeInvocationExpression(Optional.empty(), Optional.of(formatFunction),
						List.of(new RuntimeNamedArgument("format", SparkSqlHybridTest.string("foo %s")),
								new RuntimeNamedArgument("item", SparkSqlHybridTest.string("bar"))),
						List.of(), SparkSqlHybridTest.ANY),
				new RuntimeInvocationExpression(Optional.empty(), Optional.of(formatFunction),
						List.of(new RuntimeNamedArgument("item", SparkSqlHybridTest.string("bar")),
								new RuntimeNamedArgument("format", SparkSqlHybridTest.string("foo %s"))),
						List.of(), SparkSqlHybridTest.ANY),
				new RuntimeInvocationExpression(Optional.empty(), Optional.of(formatFunction),
						List.of(new RuntimeNamedArgument("item", SparkSqlHybridTest.string("bar"))),
						List.of(SparkSqlHybridTest.string("foo %s")), SparkSqlHybridTest.ANY))) {
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(call))),
					List.of(), List.of(10), 1);
			var generated = generateApprovedJava(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime(JAVA_TEST_POLICY).evaluate(model, Map.of())
							.decisionValue(10));
		}
		var dynamic = new RuntimeValueReference(0, SparkSqlHybridTest.NUMBER);
		var dynamicCall = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(literalJavaFixture("java.lang.Math", "max(double,double)", 2)), List.of(),
				List.of(dynamic, SparkSqlHybridTest.number("456")), SparkSqlHybridTest.ANY);
		var dynamicModel = new RuntimeModel(List.of(new RuntimeInput(5, 0, SparkSqlHybridTest.NUMBER)),
				List.of(new RuntimeDecision(10, 1, SparkSqlHybridTest.ANY, List.of(5), Optional.of(dynamicCall))),
				List.of(), List.of(10), 2);
		var dynamicGenerated = generateApprovedJava(dynamicModel);
		assertThat(dynamicGenerated.capabilities().get(10).nativeSql()).isFalse();
		dynamicGenerated.registerUdfs(spark);
		for (String value : List.of("123", "900")) {
			var inputs = Map.<Integer, Object>of(0, new BigDecimal(value));
			spark.createDataFrame(
					List.of(SparkSqlFeelValueCodec.inputRow(dynamicModel, inputs, dynamicGenerated.inputSchema())),
					dynamicGenerated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(dynamicGenerated.sqlFiles().get("Decision_1.sql")).head().get(0)))
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime(JAVA_TEST_POLICY).evaluate(dynamicModel, inputs)
							.decisionValue(10));
		}
		var surrogateCall = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(literalJavaFixture("java.lang.String", "valueOf(char)", 1)), List.of(),
				List.of(SparkSqlHybridTest.number("55296")), SparkSqlHybridTest.ANY);
		var surrogateModel = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(surrogateCall))),
				List.of(), List.of(10), 1);
		assertThat(generateApprovedJava(surrogateModel).capabilities().get(10).nativeSql()).isFalse();
		for (String[] signature : List.of(new String[]{"java.lang.Math", "random()"},
				new String[]{"example.Untrusted", "valueOf(double)"})) {
			var function = literalJavaFixture(signature[0], signature[1], 0);
			assertThat(SparkSqlJavaLiteralProof.supports(function)).isFalse();
		}
	}

	private static final io.finmsg.dmn.runtime.ExternalJavaPolicy JAVA_TEST_POLICY = io.finmsg.dmn.runtime.ExternalJavaPolicy
			.allowOnly(java.util.Set.of(
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Math", "cos(double)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Math", "foo(double)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Foo", "valueOf(double)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.DmnCompilerToolkitAbsentClass",
							"valueOf(double)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Math",
							"max(java.lang.String, java.lang.String)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Math", "max(double,double)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Math", "max(double, double)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Math", "max(int,int)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Math", "max(float,float)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Math", "pow(double,double)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Short", "valueOf(short)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Byte", "valueOf(byte)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.String", "valueOf(char)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Integer",
							"valueOf(java.lang.String)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Float", "valueOf(java.lang.String)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Double",
							"valueOf(java.lang.String)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.String",
							"format(java.lang.String, [Ljava.lang.Object;)"),
					new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.String",
							"format (java.lang.String, [Ljava.lang.Object;)")));
	private static DmnSparkSqlGeneratorResult generateApprovedJava(RuntimeModel model) {
		return new DmnSparkSqlGenerator().generate(new RuntimeOptimizedModel(model, List.of(), List.of(), List.of()),
				DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true, Map.of())
						.withExternalJavaPolicy(JAVA_TEST_POLICY));
	}
	private static RuntimeFunctionDefinition literalJavaFixture(String className, String signature, int arity) {
		var parameters = new ArrayList<RuntimeFunctionParameter>();
		for (int index = 0; index < arity; index++)
			parameters.add(new RuntimeFunctionParameter("arg" + index, index, SparkSqlHybridTest.ANY));
		return new RuntimeFunctionDefinition(parameters,
				Optional.of(nativeContextFixture("class", SparkSqlHybridTest.string(className), "method signature",
						SparkSqlHybridTest.string(signature))),
				true, RuntimeType.function(parameters.stream().map(RuntimeFunctionParameter::type).toList(),
						SparkSqlHybridTest.ANY));
	}

	@Test
	void typedListBuiltinsPreserveDynamicValuesAndPositionBounds() {
		for (RuntimeType element : List.of(SparkSqlHybridTest.STRING, SparkSqlHybridTest.NUMBER,
				RuntimeType.scalar(RuntimeTypeKind.BOOLEAN))) {
			var listType = RuntimeType.element(RuntimeTypeKind.LIST, element);
			var unknownList = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY);
			var source = new RuntimeValueReference(0, listType);
			var item = new RuntimeValueReference(1, element);
			var other = new RuntimeValueReference(2, listType);
			var calls = new ArrayList<RuntimeFunctionCall>();
			for (String name : List.of("reverse", "distinct values"))
				calls.add(new RuntimeFunctionCall(name, List.of(source), unknownList));
			for (String name : List.of("union", "concatenate"))
				calls.add(new RuntimeFunctionCall(name, List.of(source, other), unknownList));
			calls.add(new RuntimeFunctionCall("append", List.of(source, item, item), unknownList));
			for (String position : List.of("1", "-1", "9", "-9", "0")) {
				if (!position.equals("0"))
					calls.add(new RuntimeFunctionCall("sublist",
							List.of(source, SparkSqlHybridTest.number(position), SparkSqlHybridTest.number("2")),
							unknownList));
				calls.add(new RuntimeFunctionCall("remove", List.of(source, SparkSqlHybridTest.number(position)),
						unknownList));
				calls.add(new RuntimeFunctionCall("insert before",
						List.of(source, SparkSqlHybridTest.number(position), item), unknownList));
			}
			Object first = element.kind() == RuntimeTypeKind.STRING
					? "a"
					: element.kind() == RuntimeTypeKind.NUMBER ? new BigDecimal("1") : true;
			Object second = element.kind() == RuntimeTypeKind.STRING
					? "b"
					: element.kind() == RuntimeTypeKind.NUMBER ? new BigDecimal("2") : false;
			for (RuntimeFunctionCall call : calls) {
				var model = new RuntimeModel(
						List.of(new RuntimeInput(5, 0, listType), new RuntimeInput(6, 1, element),
								new RuntimeInput(7, 2, listType)),
						List.of(new RuntimeDecision(10, 3, listType, List.of(5, 6, 7), Optional.of(call))), List.of(),
						List.of(10), 4);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).as("%s: %s", call, generated.capabilities())
						.isTrue();
				assertThat(generated.udfs()).isEmpty();
				for (List<?> values : List.of(List.of(first, second, first), List.of())) {
					Map<Integer, Object> inputs = Map.of(0, values, 1, second, 2, List.of(second));
					spark.createDataFrame(
							List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
							generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
					Object actual = SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get("Decision_3.sql")).head().get(0));
					Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
					assertThat(actual).as("%s on %s", call, values).isEqualTo(expected);
				}
			}
		}
	}

	@Test
	void literalDeclaredCoercionUsesSharedRulesAndStopsInvalidParameterCalls() {
		for (String expression : List.of("[1, 2, \"bad\"]", "[10]", "[[10]]", "[\"bad\"]", "{a: 1}",
				"(function(x: number) 99)(\"bad\")", "(function(x: number) x)([10])",
				"(function(x: number) x)([\"bad\"])", "context put({a:1}, \"b\", 2)")) {
			var original = compiledFilterModel(expression, true);
			var result = original.decisions().getFirst();
			for (RuntimeType declared : List.of(SparkSqlHybridTest.NUMBER,
					RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER))) {
				var model = new RuntimeModel(original.inputs(),
						List.of(new RuntimeDecision(result.id(), result.resultSlot(), declared, result.dependencies(),
								result.expression(), result.decisionTable(), result.localSlotCount())),
						original.businessKnowledgeModels(), original.evaluationOrder(), original.valueSlotCount());
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(result.id()).nativeSql())
						.as("%s as %s: %s", expression, declared, generated.capabilities()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec.fromSpark(
						spark.sql(generated.sqlFiles().get("Decision_" + result.resultSlot() + ".sql")).head().get(0));
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
						.decisionValue(result.id());
				assertThat(actual).as("%s as %s", expression, declared).isEqualTo(expected);
			}
		}
	}

	@Test
	void declaredContextFieldsValidateWithoutDroppingExtraFields() {
		for (String expression : List.of("{name: \"foo\", surname: \"bar\", age: 10}",
				"context put({name: \"foo\", age: 10}, \"surname\", \"bar\")")) {
			var original = compiledFilterModel(expression, true);
			var decision = original.decisions().getFirst();
			for (RuntimeType declared : List.of(
					RuntimeType.contextFields(List.of(new RuntimeField(0, "name", SparkSqlHybridTest.STRING),
							new RuntimeField(1, "age", SparkSqlHybridTest.NUMBER))),
					RuntimeType.contextFields(List.of(new RuntimeField(0, "name", SparkSqlHybridTest.STRING))),
					RuntimeType.contextFields(List.of(new RuntimeField(0, "age", SparkSqlHybridTest.STRING))),
					RuntimeType.contextFields(List.of(new RuntimeField(0, "missing", SparkSqlHybridTest.STRING))))) {
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(decision.id(), decision.resultSlot(), declared,
								decision.dependencies(), decision.expression(), decision.decisionTable(),
								decision.localSlotCount())),
						original.businessKnowledgeModels(), original.evaluationOrder(), original.valueSlotCount());
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(decision.id()).nativeSql())
						.as("%s as %s: %s", expression, declared, generated.capabilities()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec.fromSpark(spark
						.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
				assertThat(actual).isEqualTo(
						new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(decision.id()));
			}
		}
	}

	@Test
	void literalContextInvocationProofPreservesDuplicateAndMixedArgumentBinding() {
		var source = nativeContextFixture();
		var arguments = List.of(new RuntimeNamedArgument("context", source),
				new RuntimeNamedArgument("key", SparkSqlHybridTest.string("a")),
				new RuntimeNamedArgument("value", SparkSqlHybridTest.number("1")),
				new RuntimeNamedArgument("value", SparkSqlHybridTest.number("2")));
		for (List<RuntimeExpression> positional : List.<List<RuntimeExpression>>of(List.of(), List.of(source))) {
			var invocation = new RuntimeInvocationExpression(Optional.of("context put"), Optional.empty(), arguments,
					positional, SparkSqlHybridTest.ANY);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(invocation))),
					List.of(), List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
		}
	}

	@Test
	void literalStatisticsValidateNullAndHeterogeneousListsBeforeSparkCoercion() {
		var any = SparkSqlHybridTest.ANY;
		var nil = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
		for (String name : List.of("median", "mode", "stddev")) {
			var expressions = new ArrayList<RuntimeExpression>();
			for (RuntimeExpression invalid : List.of(nil, SparkSqlHybridTest.string("not a number"))) {
				var list = new RuntimeListExpression(
						List.of(SparkSqlHybridTest.number("1"), invalid, SparkSqlHybridTest.number("2")),
						RuntimeType.element(RuntimeTypeKind.LIST, any));
				expressions.add(new RuntimeFunctionCall(name, List.of(list), any));
				expressions.add(new RuntimeInvocationExpression(Optional.of(name), Optional.empty(),
						List.of(new RuntimeNamedArgument("list", list)), List.of(), any));
			}
			var numbers = new RuntimeListExpression(
					List.of(SparkSqlHybridTest.number("2"), SparkSqlHybridTest.number("4")),
					RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER));
			expressions.add(new RuntimeFunctionCall(name, List.of(numbers), any));
			for (RuntimeExpression expression : expressions) {
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, any, List.of(), Optional.of(expression))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0));
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10);
				if (actual instanceof Number && expected instanceof Number)
					assertThat(new BigDecimal(actual.toString()))
							.isEqualByComparingTo(new BigDecimal(expected.toString()));
				else
					assertThat(actual).isEqualTo(expected);
			}
		}
	}

	@Test
	void closedContextDependenciesAndNamedBoxedFieldsKeepNativeLayouts() {
		for (boolean nested : List.of(false, true)) {
			var any = SparkSqlHybridTest.ANY;
			var literal = new RuntimeConstant(RuntimeConstantKind.NUMBER, "1", any);
			var original = nested
					? nativeContextFixture("a", nativeContextFixture("b", literal))
					: nativeContextFixture("a", literal);
			RuntimeExpression key = nested
					? new RuntimeListExpression(List.of(SparkSqlHybridTest.string("a"), SparkSqlHybridTest.string("b")),
							RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.STRING))
					: SparkSqlHybridTest.string("a");
			var dependency = new RuntimeDecision(5, 0, any, List.of(), Optional.of(original));
			var reference = new RuntimeValueReference(0, any);
			var direct = new RuntimeFunctionCall("context put", List.of(reference, key, SparkSqlHybridTest.number("2")),
					any);
			var copied = new RuntimeFunctionCall("context put",
					List.of(new RuntimeLocalReference(0, any), key, SparkSqlHybridTest.number("2")), any);
			var boxed = new RuntimeContextExpression(List.of(new RuntimeContextEntry("original", 0, reference),
					new RuntimeContextEntry("copied", 1, copied)), any);
			for (RuntimeExpression result : List.of(direct, boxed)) {
				var model = new RuntimeModel(List.of(),
						List.of(dependency,
								new RuntimeDecision(10, 1, any, List.of(5), Optional.of(result), Optional.empty(), 2)),
						List.of(), List.of(5, 10), 2);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.capabilities().get(10).nativeSql()).as("%s", generated.capabilities()).isTrue();
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0)))
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
	}

	@Test
	void literalModeListsPreserveDecimalValuesAndDeclaredCollectionCoercion() {
		var any = SparkSqlHybridTest.ANY;
		var list = RuntimeType.element(RuntimeTypeKind.LIST, any);
		for (List<RuntimeExpression> values : List.<List<RuntimeExpression>>of(List.of(),
				List.of(SparkSqlHybridTest.number("2.5"), SparkSqlHybridTest.number("1"),
						SparkSqlHybridTest.number("2.5"), SparkSqlHybridTest.number("3")))) {
			var mode = new RuntimeFunctionCall("mode", List.of(new RuntimeListExpression(values, list)), list);
			for (RuntimeType declared : List.of(any, list)) {
				var model = new RuntimeModel(List.of(),
						List.of(new RuntimeDecision(10, 0, declared, List.of(), Optional.of(mode))), List.of(),
						List.of(10), 1);
				var generated = SparkSqlHybridTest.generate(model);
				assertThat(generated.udfs()).isEmpty();
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				assertThat(SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
						.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
			}
		}
		var mode = new RuntimeFunctionCall("mode",
				List.of(new RuntimeListExpression(List.of(SparkSqlHybridTest.number("1")), list)), list);
		var incompatible = new RuntimeModel(List.of(), List.of(new RuntimeDecision(10, 0,
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.STRING), List.of(), Optional.of(mode))),
				List.of(), List.of(10), 1);
		assertThat(SparkSqlHybridTest.generate(incompatible).capabilities().get(10).nativeSql()).isFalse();
	}

	@Test
	void numericArraysRetainLosslessFallbackWhenCombinedDecimalWidthExceedsSparkBounds() {
		var any = SparkSqlHybridTest.ANY;
		var large = SparkSqlHybridTest.number("99999999999999999999999999999999999999");
		var small = SparkSqlHybridTest.number("0.00000000000000000000000000000000000001");
		var numbers = new RuntimeListExpression(List.of(large, small),
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER));
		var context = nativeContextFixture("large", large, "small", small);
		var nested = new RuntimeListExpression(List.of(nativeContextFixture("items", numbers)),
				RuntimeType.element(RuntimeTypeKind.LIST, any));
		for (RuntimeExpression expression : List.<RuntimeExpression>of(
				new RuntimeFunctionCall("mode", List.of(numbers), any),
				new RuntimeFunctionCall("get entries", List.of(context), any),
				new RuntimeFunctionCall("context merge", List.of(nested), any))) {
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, any, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isFalse();
			generated.registerUdfs(spark);
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0)))
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
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
			for (RuntimeExpression expression : List.<RuntimeExpression>of(constructed,
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
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		assertThat(generated.udfs()).isEmpty();
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
		var sharedList = new RuntimeListExpression(
				List.of(SparkSqlHybridTest.number("1"), SparkSqlHybridTest.string("one")), listType);
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		for (List<RuntimeExpression> args : List.<List<RuntimeExpression>>of(
				List.of(SparkSqlHybridTest.string("input"), sharedList),
				List.of(SparkSqlHybridTest.string("input"), SparkSqlHybridTest.string("pattern"), sharedList),
				List.of(SparkSqlHybridTest.string("input"), SparkSqlHybridTest.string("pattern"),
						SparkSqlHybridTest.string(""), sharedList))) {
			var invalid = new RuntimeFunctionCall("matches", args, bool);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, bool, List.of(), Optional.of(invalid)),
							new RuntimeDecision(20, 1, listType, List.of(), Optional.of(sharedList))),
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
		var rows = inputs.stream()
				.map(n -> SparkSqlFeelValueCodec.inputRow(model, Map.of(0, n), generated.inputSchema())).toList();
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
		spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(arithmeticModel, Map.of(0, new BigDecimal("40")),
				arithmeticGenerated.inputSchema())), arithmeticGenerated.inputSchema())
				.createOrReplaceTempView("hybrid_inputs");
		Object arithmeticResult = spark.sql(arithmeticGenerated.sqlFiles().get("Decision_1.sql")).head().get(0);
		assertThat(SparkSqlFeelValueCodec.fromSpark(arithmeticResult)).isEqualTo(new BigDecimal("42"));

		var tableModel = SparkSqlHybridTest.numericTableModel();
		var tableGenerated = SparkSqlHybridTest.generate(tableModel);
		assertThat(tableGenerated.udfs()).isEmpty();
		assertThat(tableGenerated.capabilities().get(10).nativeSql()).isTrue();
		spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(tableModel, Map.of(0, new BigDecimal("40")),
				tableGenerated.inputSchema())), tableGenerated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
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
	void listReplacementCallbacksPreserveWholeResultValidationAndLexicalBindings() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var listType = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER);
		var resultType = RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY);
		var source = new RuntimeListExpression(
				List.of(SparkSqlHybridTest.number("1"), SparkSqlHybridTest.number("2"), SparkSqlHybridTest.number("3")),
				listType);
		var empty = new RuntimeListExpression(List.of(), listType);
		var parameters = List.of(new RuntimeFunctionParameter("item", 0, SparkSqlHybridTest.ANY),
				new RuntimeFunctionParameter("newItem", 1, SparkSqlHybridTest.ANY));
		var item = new RuntimeLocalReference(0, SparkSqlHybridTest.ANY);
		var replacement = new RuntimeLocalReference(1, SparkSqlHybridTest.ANY);
		var nil = new RuntimeConstant(RuntimeConstantKind.NULL, "null", SparkSqlHybridTest.ANY);
		var bodies = List.<RuntimeExpression>of(
				new RuntimeBinaryExpression(RuntimeBinaryOperator.LESS, item, replacement, bool),
				new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, item, SparkSqlHybridTest.number("2"), bool),
				new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool),
				new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", bool), nil, item,
				new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", SparkSqlHybridTest.ANY),
				new RuntimeConditionalExpression(
						new RuntimeBinaryExpression(RuntimeBinaryOperator.LESS, item, replacement, bool), nil,
						new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool), SparkSqlHybridTest.ANY));
		for (RuntimeExpression body : bodies) {
			var function = new RuntimeFunctionDefinition(parameters, Optional.of(body), false, RuntimeType.function(
					parameters.stream().map(RuntimeFunctionParameter::type).toList(), SparkSqlHybridTest.ANY));
			for (RuntimeExpression list : List.of(source, empty)) {
				var positional = new RuntimeFunctionCall("list replace",
						List.of(list, function, SparkSqlHybridTest.number("2")), resultType);
				var named = new RuntimeInvocationExpression(Optional.of("list replace"), Optional.empty(),
						List.of(new RuntimeNamedArgument("newItem", SparkSqlHybridTest.number("2")),
								new RuntimeNamedArgument("match", function), new RuntimeNamedArgument("list", list)),
						List.of(), resultType);
				for (RuntimeExpression expression : List.of(positional, named)) {
					var model = new RuntimeModel(List.of(),
							List.of(new RuntimeDecision(10, 0, resultType, List.of(), Optional.of(expression))),
							List.of(), List.of(10), 1);
					Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
							.decisionValue(10);
					var generated = SparkSqlHybridTest.generate(model);
					assertThat(generated.capabilities().get(10).nativeSql()).as("%s", expression).isTrue();
					assertThat(generated.udfs()).isEmpty();
					assertThat(SparkSqlHybridTest.generate(model).sqlFiles()).isEqualTo(generated.sqlFiles());
					spark.sql("SELECT 1").createOrReplaceTempView("hybrid_inputs");
					Object actual = SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0));
					assertThat(actual).as("%s", expression).isEqualTo(expected);
				}
			}
		}
		var captured = new RuntimeFunctionDefinition(parameters, Optional.of(new RuntimeLocalReference(1, 0, bool)),
				false,
				RuntimeType.function(List.of(SparkSqlHybridTest.ANY, SparkSqlHybridTest.ANY), SparkSqlHybridTest.ANY));
		var valid = new RuntimeFunctionDefinition(parameters,
				Optional.of(new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool)), false,
				RuntimeType.function(List.of(SparkSqlHybridTest.ANY, SparkSqlHybridTest.ANY), bool));
		var equalMatch = new RuntimeFunctionDefinition(parameters,
				Optional.of(new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, item, replacement, bool)), false,
				RuntimeType.function(List.of(SparkSqlHybridTest.ANY, SparkSqlHybridTest.ANY), bool));
		for (var values : List.of(List.of(SparkSqlHybridTest.string("a"), SparkSqlHybridTest.string("b")),
				List.of(new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", bool),
						new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool)))) {
			var expression = new RuntimeFunctionCall("list replace",
					List.of(new RuntimeListExpression(new ArrayList<>(values),
							RuntimeType.element(RuntimeTypeKind.LIST, values.getFirst().type())), equalMatch,
							values.get(1)),
					resultType);
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, resultType, List.of(), Optional.of(expression))), List.of(),
					List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			Object actual = SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0));
			assertThat(actual)
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
		}
		for (var expression : List.of(
				new RuntimeFunctionCall(
						"list replace", List.of(source, captured, SparkSqlHybridTest.number("2")), resultType),
				new RuntimeFunctionCall("list replace", List.of(source, valid, nil), resultType),
				new RuntimeFunctionCall("list replace",
						List.of(source, valid,
								new RuntimeConstant(RuntimeConstantKind.NULL, "null", SparkSqlHybridTest.NUMBER)),
						resultType),
				new RuntimeFunctionCall("list replace",
						List.of(new RuntimeListExpression(List.of(nil), listType), valid,
								SparkSqlHybridTest.number("2")),
						resultType),
				new RuntimeInvocationExpression(
						Optional.of("list replace"), Optional.empty(), List.of(new RuntimeNamedArgument("list", source),
								new RuntimeNamedArgument("list", source), new RuntimeNamedArgument("match", valid)),
						List.of(), resultType)))
			assertThat(SparkSqlExpressionEmitter.nativeListReplaceMatchType(expression)).isNull();
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
		for (int arity : List.of(0, 1, 3)) {
			var parameters = new ArrayList<RuntimeFunctionParameter>();
			for (int slot = 0; slot < arity; slot++)
				parameters.add(new RuntimeFunctionParameter("p" + slot, slot, SparkSqlHybridTest.ANY));
			var invalidMatch = new RuntimeFunctionDefinition(parameters, Optional.of(SparkSqlHybridTest.number("1")),
					false, RuntimeType.function(parameters.stream().map(RuntimeFunctionParameter::type).toList(),
							SparkSqlHybridTest.NUMBER));
			for (var items : List.of(List.<RuntimeExpression>of(),
					List.<RuntimeExpression>of(SparkSqlHybridTest.number("1"))))
				expressions.add(new RuntimeFunctionCall("list replace",
						List.of(new RuntimeListExpression(items,
								RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER)), invalidMatch,
								SparkSqlHybridTest.number("4")),
						SparkSqlHybridTest.ANY));
		}
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
	void scalarBooleanFiltersPreserveSingletonCoercionAndDeclaredResults() {
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		for (RuntimeExpression source : List.of(SparkSqlHybridTest.number("100"),
				SparkSqlHybridTest.number("123456789012345.125"), SparkSqlHybridTest.string("foo"),
				new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool))) {
			var resultType = RuntimeType.element(RuntimeTypeKind.LIST, source.type());
			for (RuntimeExpression predicate : List.of(new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool),
					new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", bool),
					new RuntimeConstant(RuntimeConstantKind.NULL, "null", bool), new RuntimeBinaryExpression(
							RuntimeBinaryOperator.EQUAL, new RuntimeLocalReference(0, source.type()), source, bool))) {
				var expression = new RuntimeFilterExpression(source, 0, predicate, resultType);
				for (var declaredType : List.of(resultType, source.type(), SparkSqlHybridTest.ANY)) {
					var model = new RuntimeModel(List.of(), List.of(new RuntimeDecision(10, 0, declaredType, List.of(),
							Optional.of(expression), Optional.empty(), 1)), List.of(), List.of(10), 1);
					Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
							.decisionValue(10);
					var generated = SparkSqlHybridTest.generate(model);
					assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
					assertThat(generated.udfs()).isEmpty();
					spark.sql("SELECT 1").createOrReplaceTempView("hybrid_inputs");
					Object actual = SparkSqlFeelValueCodec
							.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0));
					assertThat(actual).as("%s", expression).isEqualTo(expected);
				}
			}
		}
		for (String unsafe : List.of("1e100", "0.000000000000000000000000000000000000001",
				"123456789012345678901234567890123456789")) {
			var filter = new RuntimeFilterExpression(SparkSqlHybridTest.number(unsafe), 0,
					new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool),
					RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER));
			assertThat(SparkSqlCapabilityAnalyzer.nativeFilter(filter)).as(unsafe).isFalse();
		}
	}

	@Test
	void terminalEmptyListsPreserveUntypedResultsWithoutPromotingConsumers() {
		var expression = new RuntimeListExpression(List.of(),
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.ANY));
		for (var resultType : List.of(SparkSqlHybridTest.ANY, expression.type(),
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER))) {
			var decision = new RuntimeDecision(10, 0, resultType, List.of(), Optional.of(expression));
			var model = new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.sql("SELECT 1").createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0));
			assertThat(actual)
					.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10));
		}
		var scalar = new RuntimeDecision(10, 0, SparkSqlHybridTest.NUMBER, List.of(), Optional.of(expression));
		assertThat(SparkSqlCapabilityAnalyzer.nativeEmptyListResult(scalar)).isFalse();
		var empty = new RuntimeDecision(10, 0, expression.type(), List.of(), Optional.of(expression));
		var consumer = new RuntimeDecision(11, 1, expression.type(), List.of(10),
				Optional.of(new RuntimeValueReference(0, expression.type())));
		var dependent = new RuntimeModel(List.of(), List.of(empty, consumer), List.of(), List.of(10, 11), 2);
		assertThat(new SparkSqlCapabilityAnalyzer().analyze(dependent, 11, true).nativeSql()).isFalse();
	}

	@Test
	void calendarProjectionsSurviveAllDecisionsAndPreserveFallbackPayloads()
			throws java.util.concurrent.TimeoutException {
		var before = processing.snapshot(spark);
		var input = new RuntimeInput(5, 0, SparkSqlHybridTest.ANY);
		var reference = new RuntimeValueReference(0, SparkSqlHybridTest.ANY);
		var month = new RuntimeDecision(10, 1, SparkSqlHybridTest.ANY, List.of(5),
				Optional.of(new RuntimeFunctionCall("month of year", List.of(reference), SparkSqlHybridTest.ANY)));
		var week = new RuntimeDecision(11, 2, SparkSqlHybridTest.ANY, List.of(5),
				Optional.of(new RuntimeInvocationExpression(Optional.of("week of year"), Optional.empty(),
						List.of(new RuntimeNamedArgument("date", reference)), List.of(), SparkSqlHybridTest.ANY)));
		var identity = new RuntimeDecision(12, 3, SparkSqlHybridTest.ANY, List.of(5), Optional.of(reference));
		var model = new RuntimeModel(List.of(input), List.of(month, week, identity), List.of(), List.of(10, 11, 12), 4);
		var options = DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true,
				Map.of(0, "__dmn_calendar_0", 1, "month", 2, "week", 3, "original"));
		var generated = new DmnSparkSqlGenerator()
				.generate(new RuntimeOptimizedModel(model, List.of(), List.of(), List.of()), options);
		assertThat(generated.inputSchema().fields()[1].name()).isEqualTo("__dmn_calendar_0_");
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		assertThat(generated.capabilities().get(11).nativeSql()).isTrue();
		assertThat(generated.capabilities().get(12).nativeSql()).isFalse();
		Object value = io.finmsg.dmn.runtime.DmnRuntime.parseDateTime("2020-12-31T23:59:59.999999999@Europe/Paris");
		var inputs = Map.of(0, value);
		var row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
		assertThat((byte[]) row.get(0)).isEqualTo((byte[]) SparkSqlFeelValueCodec.inputRow(model, inputs).get(0));
		spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
		generated.registerUdfs(spark);
		var expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs);
		for (var decision : List.of(month, week)) {
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			if (actual instanceof BigDecimal decimal)
				assertThat(decimal).isEqualByComparingTo((BigDecimal) expected.decisionValue(decision.id()));
			else
				assertThat(actual).isEqualTo(expected.decisionValue(decision.id()));
		}
		Row all = spark.sql(generated.sqlFiles().get("AllDecisions.sql")).head();
		assertThat(SparkSqlFeelValueCodec.fromSpark(all.get(0))).isEqualTo(expected.decisionValue(10));
		assertThat((BigDecimal) SparkSqlFeelValueCodec.fromSpark(all.get(1)))
				.isEqualByComparingTo((BigDecimal) expected.decisionValue(11));
		assertThat(SparkSqlFeelValueCodec.fromSpark(all.get(2))).isEqualTo(value);
		var after = processing.snapshot(spark);
		assertThat(after.nativeActions() - before.nativeActions()).isEqualTo(2);
		assertThat(after.fallbackActions() - before.fallbackActions()).isEqualTo(1);
		assertThat(after.unknownActions() - before.unknownActions()).isZero();
		assertThat(after.failedActions() - before.failedActions()).isZero();
	}

	@Test
	void anyCalendarInputsPreserveLocalDateAndOpaqueTemporalIdentity() {
		var values = new ArrayList<Object>(List.of(java.time.LocalDate.of(2000, 2, 29),
				java.time.LocalDate.of(1900, 3, 1), java.time.LocalDate.of(2003, 12, 29),
				java.time.LocalDate.of(2010, 1, 1), java.time.LocalDate.of(1582, 10, 4),
				java.time.LocalDate.of(0, 1, 1), java.time.LocalDate.of(-400, 12, 31), java.time.LocalDate.MAX,
				java.time.LocalDate.MIN, java.time.OffsetDateTime.parse("2020-01-01T00:00:00.123456789+14:00"),
				java.time.OffsetDateTime.parse("2020-12-31T23:59:59.999999999-03:30"),
				io.finmsg.dmn.runtime.DmnRuntime.parseDateTime("2021-03-28T01:59:59.999999999@Europe/Paris"),
				"2020-01-01", BigDecimal.ONE, true, java.time.Duration.ofNanos(1), java.time.Period.ZERO,
				java.time.LocalTime.NOON, List.of(java.time.LocalDate.of(2020, 1, 1))));
		values.add(null);
		for (String name : List.of("day of year", "day of week", "month of year", "week of year")) {
			var expression = new RuntimeFunctionCall(name,
					List.of(new RuntimeValueReference(0, SparkSqlHybridTest.ANY)), SparkSqlHybridTest.ANY);
			var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, SparkSqlHybridTest.ANY)),
					List.of(new RuntimeDecision(10, 1, SparkSqlHybridTest.ANY, List.of(5), Optional.of(expression))),
					List.of(), List.of(10), 2);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).as(name).isTrue();
			assertThat(generated.udfs()).isEmpty();
			assertThat(generated.inputSchema().size()).isEqualTo(2);
			for (Object value : values) {
				var inputs = new HashMap<Integer, Object>();
				inputs.put(0, value);
				Row row = SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema());
				assertThat(SparkSqlFeelValueCodec.fromSpark(row.get(0))).as("payload %s", value).isEqualTo(value);
				spark.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
				if (expected instanceof BigDecimal decimal)
					assertThat((BigDecimal) actual).as("%s(%s)", name, value).isEqualByComparingTo(decimal);
				else
					assertThat(actual).as("%s(%s)", name, value).isEqualTo(expected);
			}
		}
	}

	@Test
	void invalidLiteralIterationEndpointsReturnNativeNullWithoutTemporalCoercion() {
		var expressions = List.of("for i in \"a\"..\"z\" return i",
				"for i in @\"1980-01-03T00:00:00\"..@\"1980-01-01T00:00:00\" return i",
				"for i in @\"2020-01-01T23:59:59.123456789+14:00\"..@\"2020-01-02T00:00:00-03:30\" return i",
				"for i in @\"00:00:00\"..@\"00:00:00\" return i",
				"for i in @\"12:00:00.123456789@Europe/Paris\"..@\"13:00:00@Europe/Paris\" return i",
				"for i in @\"P1D\"..@\"P2D\" return i", "for i in @\"P0M\"..@\"P1Y\" return i",
				"for i in 1.5..2.5 return i", "for i in 2147483648..2147483649 return i", "for i in null..1 return i",
				"for i in 1..null return i", "for i in @\"2020-01-01\"..1 return i", "for i in 1 return i",
				"for i in \"1\"..\"2\" return i");
		for (String expression : expressions) {
			var model = compiledFilterModel(expression);
			var lowered = compiledFilterModel(expression, true);
			assertThat(new io.finmsg.dmn.runtime.DmnRuntime().evaluate(lowered, Map.of())
					.decisionValue(lowered.decisions().getFirst().id())).as(expression).isNull();
			var decision = model.decisions().getFirst();
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql())
					.as("%s; IR %s; routes %s", expression, model, generated.capabilities()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0))
					.as(expression).isNull();
		}
		var dateLoop = compiledFilterModel("for i in @\"1980-01-01\"..@\"1980-01-03\" return i");
		assertThat(SparkSqlExpressionEmitter
				.nativeInvalidExpression(dateLoop.decisions().getFirst().expression().orElseThrow())).isFalse();
	}

	@Test
	void boundedLoopLoweringPreservesDependentBindingsAndComputedRanges() {
		for (String expression : List.of("for i in [] return i", "for i in -1..1 return i", "for i in 1..-1 return i",
				"for i in 1+1..1+3 return i", "for x in [[1,2],[3,4]], y in x return y",
				"for x in [[1,2],[3,4]] return for y in x return y", "for i in [1,2], j in 1..i return i+j",
				"for i in [1,2], j in [3,4] return [i,j]",
				"for i in [\"saturday\",\"sunday\"] return if i = \"sunday\" then true else false",
				"for i in [1..2] return i", "for i in [\"a\"..\"z\"] return i",
				"for i in [@\"00:00:00.123456789+14:00\"..@\"01:00:00-03:30\"] return i",
				"for i in [@\"P1D\"..@\"P2D\"] return i")) {
			var model = compiledFilterModel(expression);
			var generated = SparkSqlHybridTest.generate(model);
			var decision = model.decisions().getFirst();
			assertThat(generated.capabilities().get(decision.id()).nativeSql())
					.as("%s: %s", expression, generated.capabilities()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			var lowered = compiledFilterModel(expression, true);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(lowered, Map.of())
					.decisionValue(lowered.decisions().getFirst().id());
			assertThat(actual).as(expression).isEqualTo(expected);
		}
		for (String expression : List.of("for i in 1..100, j in 1..100 return i+j",
				"for i in [50000], j in [50000] return i*j", "for i in [2147483647], j in [1] return i+j",
				"for i in [0.1], j in [0.2] return i+j", "for i in [1], j in [2] return null")) {
			var model = compiledFilterModel(expression);
			assertThat(SparkSqlHybridTest.generate(model).capabilities().get(model.decisions().getFirst().id())
					.nativeSql()).as(expression).isFalse();
		}
	}

	@Test
	void boundedCartesianLoopsPreserveBindingOrderAndNestedResults() {
		var expressions = List.of("for i in [1,2,3], j in [4,5] return i + j",
				"for i in [1,2], j in [4,5], k in [7,8] return i + j + k", "for i in [1,2], j in [4,5] return [i,j]",
				"for i in 1..3, j in 3..1 return i * 10 + j", "for i in 1.0..2.0, j in [4,5] return i + j",
				"for i in [true,false], j in [true,false] return i and j",
				"for i in [\"a\",\"b\"], j in [\"c\",\"d\"] return i + j");
		for (String expression : expressions) {
			var model = compiledFilterModel(expression);
			var decision = model.decisions().getFirst();
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql())
					.as("%s; IR %s; routes %s", expression, model, generated.capabilities()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			var lowered = compiledFilterModel(expression, true);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(lowered, Map.of())
					.decisionValue(lowered.decisions().getFirst().id());
			assertThat(actual).as(expression).isEqualTo(expected);
		}
		for (String expression : List.of("for i in 1..100, j in 1..100 return i+j",
				"for i in [1,2], j in 1..i return i+j", "for i in [1,2], j in [1,null] return i+j",
				"for i in [2147483647], j in [1] return i+j", "for i in [50000], j in [50000] return i*j",
				"for i in [0.1], j in [0.2] return i+j", "for i in [1], j in [2] return null")) {
			var model = compiledFilterModel(expression);
			assertThat(new SparkSqlCapabilityAnalyzer().analyze(model, model.decisions().getFirst().id(), true)
					.nativeSql()).as(expression).isFalse();
		}
		var scalar = compiledFilterModel("for i in [1,2], j in [3,4] return i+j", false, "number");
		assertThat(
				new SparkSqlCapabilityAnalyzer().analyze(scalar, scalar.decisions().getFirst().id(), true).nativeSql())
				.isFalse();
	}

	@Test
	void compiledContextFiltersPreserveFieldBindingsAndQuotedKeys() {
		var expressions = List.of("[{a: 1}, {a: 2}, {a: 3}][item.a >= 2]", "[{a: 1}, {a: 2}, {a: 3}][a >= 2]",
				"[{item: 1}, {item: 2}, {item: 3}][item >= 2]", "[{x: 1, y: 2}, {x: 2, y: 3}][x = 1]",
				"[{x: 1}, {x: 2}][true]", "[{x: 1}, {x: 2}][false]", "[{x: 1}, {x: 2}][x > null]",
				"[{x: 1}, {x: 2}][y > 1]", "[{full name: \"one\"}, {full name: \"two\"}][full name = \"two\"]",
				"[{x: true}, {x: false}][x]", "[{x: 999999999999999}, {x: 2}][x > 2]",
				"[{x: 1, y: 2}, {x: null, y: 3}][x < 2]", "[{x: null, y: 3}, {x: 1, y: 2}][x < 2]",
				"[{x: 1}, {x: null}][true]", "[{x: null}, {x: null}][true]", "[{x: true}, {x: null}, {x: false}][x]",
				"[{x: null}, {x: \"two\"}][x = \"two\"]",
				"[{full name: null}, {full name: \"two\"}][full name = \"two\"]");
		for (String expression : expressions) {
			var model = compiledFilterModel(expression);
			var decision = model.decisions().getFirst();
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql())
					.as("%s: %s", expression, generated.capabilities()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
					.decisionValue(decision.id());
			assertThat(actual).as(expression).isEqualTo(expected);
		}
		System.out.printf("CONTEXT FILTER PARITY: %d native (100.00%%), 0 executed fallback (0.00%%)%n",
				expressions.size());
		for (RuntimeTypeKind declared : List.of(RuntimeTypeKind.NUMBER, RuntimeTypeKind.STRING)) {
			var compiled = compiledFilterModel("[{x: 1}, {x: null}][true]");
			var original = compiled.decisions().getFirst();
			var decision = new RuntimeDecision(original.id(), original.resultSlot(), RuntimeType.scalar(declared),
					original.dependencies(), original.expression(), original.decisionTable(),
					original.localSlotCount());
			var model = new RuntimeModel(compiled.inputs(), List.of(decision), compiled.businessKnowledgeModels(),
					compiled.evaluationOrder(), compiled.valueSlotCount());
			assertThat(new SparkSqlCapabilityAnalyzer().analyze(model, model.decisions().getFirst().id(), true)
					.nativeSql()).as("nullable context filter declared %s", declared).isFalse();
		}
		for (String expression : List.of("[{x: 1}, {y: 2}][y > 1]", "[{x: 1}, {x: \"two\"}][true]",
				"[{x: 1, y: 2}, {y: 3, x: null}][true]", "[{x: 1.1234567890123456789}, {x: 2}][true]",
				"[{x: 1000000000000000}, {x: 2}][true]")) {
			var model = compiledFilterModel(expression);
			assertThat(new SparkSqlCapabilityAnalyzer().analyze(model, model.decisions().getFirst().id(), true)
					.nativeSql()).as(expression).isFalse();
		}
	}

	private static RuntimeModel compiledFilterModel(String expression) {
		return compiledFilterModel(expression, false);
	}

	private static RuntimeModel compiledFilterModel(String expression, boolean lowered) {
		return compiledFilterModel(expression, lowered, "");
	}

	private static RuntimeModel compiledFilterModel(String expression, boolean lowered, String declaredType) {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="filters" name="filters" namespace="urn:filters">
				  <decision id="result" name="result"><variable name="result"%s/>
				    <literalExpression><text>%s</text></literalExpression>
				  </decision>
				</definitions>
				"""
				.formatted(declaredType.isEmpty() ? "" : " typeRef=\"" + declaredType + "\"",
						expression.replace("&", "&amp;").replace("<", "&lt;"));
		var source = new io.finmsg.dmn.compiler.DmnSource(io.finmsg.dmn.compiler.DmnSourceId.of("memory:filters"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var compilation = new io.finmsg.dmn.compiler.DmnCompiler().compile(source,
				new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(List.of()),
				lowered
						? io.finmsg.dmn.compiler.DmnCompilerOptions.lowered()
						: io.finmsg.dmn.compiler.DmnCompilerOptions.optimized());
		assertThat(compilation.isSuccess()).as(expression).isTrue();
		return compilation.optimizedRuntimeModel().orElseThrow().model();
	}

	@Test
	void staticClosuresPreserveCaptureShadowingAndNamedBinding() {
		for (String sample : List.of("(function(a: number) a + 1)(2)",
				"(function(a: number) function(b: number) a * b)(10)(3)",
				"(function(a: number) function(a: number) a + 1)(100)(2)",
				"(function(a: number) function(b: number) function(c: number) a * b * c)(2)(3)(4)",
				"(function(a: number, b: number) a - b)(b: 2, a: 9)")) {
			var model = compiledFilterModel(sample, true);
			var decision = model.decisions().getFirst();
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
					.decisionValue(decision.id());
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql())
					.as("%s: %s", sample, generated.capabilities()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			assertThat(new BigDecimal(actual.toString())).as(sample)
					.isEqualByComparingTo(new BigDecimal(expected.toString()));
		}
	}

	@Test
	void extremeRoundingScalesPreserveExactLiteralsAndValidation() {
		for (String alias : List.of("round", "ceil", "round_up"))
			assertThat(SparkSqlRoundingProof.sql(new RuntimeFunctionCall(alias,
					List.of(SparkSqlHybridTest.number("5.5"), SparkSqlHybridTest.number("6176")),
					SparkSqlHybridTest.NUMBER))).isNull();
		for (String function : List.of("floor", "ceiling", "decimal", "round up", "round down", "round half up",
				"round half down", "round half even")) {
			for (String arguments : List.of("5.5, 6176", "5.5, (6176 + 1)", "5.5, (-6111 - 1)", "-5.5, 6176",
					"5.5, (2 * 3)", "5.5, -6111")) {
				String sample = function + "(" + arguments + ")";
				var model = compiledFilterModel(sample, true);
				var decision = model.decisions().getFirst();
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
						.decisionValue(decision.id());
				var generated = SparkSqlHybridTest.generate(model);
				if (arguments.equals("5.5, -6111"))
					assertThat(generated.udfs()).as(sample).isNotEmpty();
				else
					assertThat(generated.udfs()).as("%s: %s", sample, generated.capabilities()).isEmpty();
				generated.registerUdfs(spark);
				spark.range(1).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec.fromSpark(spark
						.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
				if (expected == null)
					assertThat(actual).as(sample).isNull();
				else
					assertThat(new BigDecimal(actual.toString())).as(sample)
							.isEqualByComparingTo(new BigDecimal(expected.toString()));
			}
		}
		var number = SparkSqlHybridTest.NUMBER;
		assertThat(SparkSqlRoundingProof.sql(new RuntimeFunctionCall("round up",
				List.of(SparkSqlHybridTest.number("5.5"), SparkSqlHybridTest.number("1.1")), number))).isNull();
		assertThat(SparkSqlRoundingProof.sql(new RuntimeFunctionCall("round up",
				List.of(SparkSqlHybridTest.number("5.5"), SparkSqlHybridTest.number("4294967296")), number))).isNull();
		assertThat(SparkSqlRoundingProof.sql(new RuntimeFunctionCall("round up",
				List.of(new RuntimeValueReference(0, number), SparkSqlHybridTest.number("6176")), number))).isNull();
	}

	@Test
	void literalTypeProofsAvoidClosureAndHeterogeneousListMaterialization() {
		List<String> samples = new ArrayList<>();
		for (String tested : List.of("Any", "number", "string", "boolean", "date", "time", "date and time", "list<Any>",
				"years and months duration", "days and time duration", "context<a: string>"))
			samples.add("(function() 1 / 0) instance of " + tested);
		samples.addAll(List.of("[1, \"2\", date(\"2018-12-08\")] instance of list<Any>",
				"[1, \"2\", date(\"2018-12-08\")] instance of list<number>", "[] instance of list<number>",
				"[null] instance of list<Any>", "{a: null} instance of context<a: string>",
				"{a: \"x\"} instance of context<a: string, b: number>", "[function() 1] instance of list<Any>",
				"12345678901234567890123456789012345678901234567890 instance of number"));
		for (String sample : samples) {
			var model = compiledFilterModel(sample, true);
			var decision = model.decisions().getFirst();
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
					.decisionValue(decision.id());
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).as("%s: %s", sample, generated.capabilities()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			assertThat(spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0))
					.as(sample).isEqualTo(expected);
		}
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var functionType = RuntimeType.function(List.of(), SparkSqlHybridTest.NUMBER);
		var function = new RuntimeFunctionDefinition(List.of(), Optional.of(SparkSqlHybridTest.number("1")), false,
				functionType);
		assertThat(SparkSqlStaticTypeProof.sql(new RuntimeInstanceOfExpression(function, functionType, bool))).isNull();
		assertThat(SparkSqlStaticTypeProof
				.sql(new RuntimeInstanceOfExpression(new RuntimeValueReference(0, SparkSqlHybridTest.ANY), bool, bool)))
				.isNull();
		var invalidDate = new RuntimeConstant(RuntimeConstantKind.DATE, "2024-02-30",
				RuntimeType.scalar(RuntimeTypeKind.DATE));
		assertThat(
				SparkSqlStaticTypeProof.sql(new RuntimeInstanceOfExpression(invalidDate, SparkSqlHybridTest.ANY, bool)))
				.isNull();
		var hugeList = new RuntimeListExpression(Collections.nCopies(5000, SparkSqlHybridTest.number("1")),
				RuntimeType.element(RuntimeTypeKind.LIST, SparkSqlHybridTest.NUMBER));
		assertThat(SparkSqlStaticTypeProof.sql(new RuntimeInstanceOfExpression(hugeList, SparkSqlHybridTest.ANY, bool)))
				.isNull();
	}

	@Test
	void trustedImplicitJavaDescriptorsUseNativeLiterals() {
		var nondeterministic = compiledFilterModel(
				"(function() {class: \"java.lang.Math\", method signature: \"random()\"})()", true);
		assertThat(generateApprovedJava(nondeterministic).capabilities()
				.get(nondeterministic.decisions().getFirst().id()).nativeSql()).isFalse();
		var model = compiledFilterModel(
				"(function(d1: number, d2: number) {class: \"java.lang.Math\", method signature: \"max(double,double)\"})(123,456)",
				true);
		var decision = model.decisions().getFirst();
		Object expected = new io.finmsg.dmn.runtime.DmnRuntime(JAVA_TEST_POLICY).evaluate(model, Map.of())
				.decisionValue(decision.id());
		assertThat(new BigDecimal(expected.toString())).isEqualByComparingTo("456");
		var generated = generateApprovedJava(model);
		assertThat(generated.capabilities().get(decision.id()).nativeSql()).isTrue();
		assertThat(generated.udfs()).isEmpty();
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		Object actual = SparkSqlFeelValueCodec.fromSpark(
				spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
		assertThat(new BigDecimal(actual.toString())).isEqualByComparingTo("456");
	}

	@Test
	void defaultDenialSurvivesGenerationAndDoesNotReuseApprovedFoldedResults() {
		var model = compiledFilterModel(
				"(function(x) external {java:{class: \"java.lang.Math\", method signature: \"cos(double)\"}})(0)",
				true);
		int id = model.decisions().getFirst().id();
		var approved = generateApprovedJava(model);
		assertThat(approved.capabilities().get(id).nativeSql()).isTrue();
		var denied = SparkSqlHybridTest.generate(model);
		assertThat(denied.capabilities().get(id).nativeSql()).isFalse();
		assertThat(denied.udfs()).isNotEmpty();
		assertThatThrownBy(
				() -> denied.udfs().values().iterator().next().evaluate(org.apache.spark.sql.RowFactory.create()))
				.isInstanceOf(io.finmsg.dmn.runtime.ExternalJavaDeniedException.class);
		denied.registerUdfs(spark);
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		assertThatThrownBy(() -> spark.sql(denied.sqlFiles().get("AllDecisions.sql")).collectAsList())
				.hasStackTraceContaining("EXTERNAL_JAVA_DENIED:");
		approved.registerUdfs(spark);
		assertThat(
				SparkSqlFeelValueCodec.fromSpark(spark.sql(approved.sqlFiles().get("AllDecisions.sql")).head().get(0)))
				.isEqualTo(new io.finmsg.dmn.runtime.DmnRuntime(JAVA_TEST_POLICY).evaluate(model, Map.of())
						.decisionValue(id));
	}

	@Test
	void staticFunctionArgumentsBindVerifiedNumericBuiltins() {
		var unsupported = compiledFilterModel("(function(f) f([1], [2]))(sum)", true);
		assertThat(SparkSqlHybridTest.generate(unsupported).udfs()).isNotEmpty();
		for (String sample : List.of("(function(f) f(-5))(abs)", "(function(f) f(25))(sqrt)",
				"(function(f) f(10, 2))(sqrt)", "(function(f) f([1,2,3]))(sum)", "(function(f) f(1,2))(sum)")) {
			var model = compiledFilterModel(sample, true);
			var decision = model.decisions().getFirst();
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
					.decisionValue(decision.id());
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).as("%s: %s", sample, generated.capabilities()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec.fromSpark(
					spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head().get(0));
			if (expected == null)
				assertThat(actual).isNull();
			else
				assertThat(new BigDecimal(actual.toString())).as(sample)
						.isEqualByComparingTo(new BigDecimal(expected.toString()));
		}
	}

	@Test
	void boxedClosuresInlineAcrossAnyTypedIntermediateDecisions() {
		var number = SparkSqlHybridTest.NUMBER;
		var innerType = RuntimeType.function(List.of(number), number);
		var inner = new RuntimeFunctionDefinition(List.of(new RuntimeFunctionParameter("a", 0, number)),
				Optional.of(new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY,
						new RuntimeLocalReference(1, 1, number), new RuntimeLocalReference(0, 0, number), number)),
				false, innerType);
		var context = new RuntimeContextExpression(
				List.of(new RuntimeContextEntry("saved", 1, new RuntimeLocalReference(0, 0, number)),
						new RuntimeContextEntry("", -1, inner)),
				innerType);
		var outer = new RuntimeFunctionDefinition(List.of(new RuntimeFunctionParameter("a", 0, number)),
				Optional.of(context), false, 2, RuntimeType.function(List.of(number), innerType));
		var partial = new RuntimeInvocationExpression(Optional.empty(), Optional.of(outer), List.of(),
				List.of(SparkSqlHybridTest.number("10")), innerType);
		var invoke = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimeValueReference(0, SparkSqlHybridTest.ANY)), List.of(),
				List.of(SparkSqlHybridTest.number("3")), number);
		var model = new RuntimeModel(List.of(),
				List.of(new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(partial)),
						new RuntimeDecision(11, 1, number, List.of(10), Optional.of(invoke))),
				List.of(), List.of(10, 11), 2);
		Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(11);
		var generated = SparkSqlHybridTest.generate(model);
		assertThat(generated.capabilities().get(11).nativeSql()).as(generated.capabilities().toString()).isTrue();
		assertThat(generated.sqlFiles().get("Decision_1.sql")).doesNotContain("dmn_");
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		Object actual = spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0);
		assertThat(new BigDecimal(actual.toString())).isEqualByComparingTo(new BigDecimal(expected.toString()))
				.isEqualByComparingTo("30");
	}

	@Test
	void staticFunctionListCoercionPreservesNullableScalarInputsAndDeclaredResults() {
		var number = SparkSqlHybridTest.NUMBER;
		var list = RuntimeType.element(RuntimeTypeKind.LIST, number);
		for (boolean listParameter : List.of(true, false)) {
			RuntimeType parameterType = listParameter ? list : number;
			RuntimeExpression body = new RuntimeLocalReference(0, 0, parameterType);
			var function = new RuntimeFunctionDefinition(
					List.of(new RuntimeFunctionParameter("value", 0, parameterType)), Optional.of(body), false,
					RuntimeType.function(List.of(parameterType), parameterType));
			var invoke = new RuntimeInvocationExpression(Optional.empty(), Optional.of(function), List.of(),
					List.of(new RuntimeValueReference(0, number)), parameterType);
			var model = new RuntimeModel(List.of(new RuntimeInput(1, 0, number)),
					List.of(new RuntimeDecision(10, 1, list, List.of(1), Optional.of(invoke))), List.of(), List.of(10),
					2);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.udfs()).as(generated.capabilities().toString()).isEmpty();
			for (BigDecimal value : Arrays.asList(new BigDecimal("2.5"), new BigDecimal("-5"), null)) {
				Map<Integer, Object> inputs = new HashMap<>();
				inputs.put(0, value);
				Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, inputs).decisionValue(10);
				spark.createDataFrame(List.of(SparkSqlFeelValueCodec.inputRow(model, inputs, generated.inputSchema())),
						generated.inputSchema()).createOrReplaceTempView("hybrid_inputs");
				Object actual = SparkSqlFeelValueCodec
						.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
				if (expected == null)
					assertThat(actual).isNull();
				else {
					assertThat(actual).isInstanceOf(List.class);
					assertThat((List<?>) actual).hasSize(1);
					assertThat(new BigDecimal(((List<?>) actual).getFirst().toString())).isEqualByComparingTo(value);
				}
			}
		}
	}

	@Test
	void staticFunctionGuardsPreserveInvalidCallsAndBoundExpansion() {
		var number = SparkSqlHybridTest.NUMBER;
		var functionType = RuntimeType.function(List.of(number), number);
		var parameter = new RuntimeFunctionParameter("a", 0, number);
		var local = new RuntimeLocalReference(0, 0, number);
		var function = new RuntimeFunctionDefinition(List.of(parameter), Optional.of(
				new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, local, SparkSqlHybridTest.number("1"), number)),
				false, functionType);
		List<RuntimeExpression> calls = new ArrayList<>();
		for (List<RuntimeExpression> args : List.of(List.<RuntimeExpression>of(),
				List.<RuntimeExpression>of(SparkSqlHybridTest.string("wrong type")),
				List.<RuntimeExpression>of(SparkSqlHybridTest.number("1"), SparkSqlHybridTest.number("2")))) {
			calls.add(
					new RuntimeInvocationExpression(Optional.empty(), Optional.of(function), List.of(), args, number));
		}
		calls.add(new RuntimeInvocationExpression(Optional.empty(), Optional.of(function),
				List.of(new RuntimeNamedArgument("wrong name", SparkSqlHybridTest.number("1"))), List.of(), number));
		var twice = new RuntimeFunctionDefinition(List.of(parameter),
				Optional.of(new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, local, local, number)), false,
				functionType);
		RuntimeExpression expanding = SparkSqlHybridTest.number("1");
		for (int index = 0; index < 16; index++)
			expanding = new RuntimeInvocationExpression(Optional.empty(), Optional.of(twice), List.of(),
					List.of(expanding), number);
		calls.add(expanding);
		var ignoresArgument = new RuntimeFunctionDefinition(List.of(parameter),
				Optional.of(SparkSqlHybridTest.number("1")), false, functionType);
		var nullList = new RuntimeListExpression(List
				.of(new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL))),
				RuntimeType.element(RuntimeTypeKind.LIST, number));
		calls.add(new RuntimeInvocationExpression(Optional.empty(), Optional.of(ignoresArgument), List.of(),
				List.of(nullList), number));
		for (RuntimeExpression call : calls) {
			var model = new RuntimeModel(List.of(),
					List.of(new RuntimeDecision(10, 0, number, List.of(), Optional.of(call))), List.of(), List.of(10),
					1);
			var lowered = SparkSqlStaticFunctionLowerer.lower(model);
			if (!lowered.equals(model)) {
				assertThat(lowered.decisions().getFirst().expression().orElseThrow()).isInstanceOfSatisfying(
						RuntimeConstant.class,
						constant -> assertThat(constant.kind()).isEqualTo(RuntimeConstantKind.NULL));
			}
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of()).decisionValue(10);
			var generated = SparkSqlHybridTest.generate(model);
			generated.registerUdfs(spark);
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object actual = SparkSqlFeelValueCodec
					.fromSpark(spark.sql(generated.sqlFiles().get("Decision_0.sql")).head().get(0));
			if (expected == null)
				assertThat(actual).isNull();
			else
				assertThat(new BigDecimal(actual.toString())).isEqualByComparingTo(new BigDecimal(expected.toString()));
		}
	}

	@Test
	void compiledDurationAbsAnyResultsKeepLosslessIdentity() throws java.util.concurrent.TimeoutException {
		var before = processing.snapshot(spark);
		var samples = List.of("abs(duration(\"-P1D\"))", "abs(duration(\"P1D\"))", "abs(duration(\"-P1Y\"))",
				"abs(duration(\"P1Y\"))", "abs(duration(\"P0M\"))", "abs(duration(\"PT0S\"))",
				"abs(duration(\"-PT0.000000001S\"))", "abs(duration(\"-PT1.999999999S\"))",
				"abs(n: duration(\"-P1Y2M\"))", "abs(n: duration(\"-PT0.000000001S\"))");
		for (String sample : samples) {
			var model = compiledFilterModel(sample);
			var decision = model.decisions().getFirst();
			assertThat(decision.type().kind()).as(sample).isEqualTo(RuntimeTypeKind.ANY);
			var lowered = compiledFilterModel(sample, true);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(lowered, Map.of())
					.decisionValue(lowered.decisions().getFirst().id());
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql())
					.as("%s: %s", sample, generated.capabilities()).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object raw = spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head()
					.get(0);
			assertThat(raw).as(sample).isInstanceOf(byte[].class);
			assertThat(SparkSqlFeelValueCodec.fromSpark(raw, decision.type())).as(sample).isEqualTo(expected)
					.isInstanceOf(expected.getClass());
		}
		System.out.printf("COMPILED DURATION ABS PARITY: %d native (100.00%%), 0 executed fallback (0.00%%)%n",
				samples.size());
		var durationType = RuntimeType.scalar(RuntimeTypeKind.DURATION);
		var overflow = new RuntimeFunctionCall("abs",
				List.of(new RuntimeConstant(RuntimeConstantKind.DURATION, "PT-9223372036854775808S", durationType)),
				SparkSqlHybridTest.ANY);
		assertThat(SparkSqlExpressionEmitter.nativeTerminalTemporalValue(
				new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(overflow)))).isNull();
		var dynamic = new RuntimeFunctionCall("abs", List.of(new RuntimeValueReference(0, durationType)),
				SparkSqlHybridTest.ANY);
		var dynamicModel = new RuntimeModel(List.of(new RuntimeInput(5, 0, durationType)),
				List.of(new RuntimeDecision(10, 1, SparkSqlHybridTest.ANY, List.of(5), Optional.of(dynamic))),
				List.of(), List.of(10), 2);
		assertThat(new SparkSqlCapabilityAnalyzer().analyze(dynamicModel, 10, true).nativeSql()).isFalse();
		var compiled = compiledFilterModel("abs(duration(\"-PT0.000000001S\"))");
		var terminal = compiled.decisions().getFirst();
		var consumer = new RuntimeDecision(terminal.id() + 1, 1, SparkSqlHybridTest.ANY, List.of(terminal.id()),
				Optional.of(new RuntimeValueReference(terminal.resultSlot(), SparkSqlHybridTest.ANY)));
		var dependent = new RuntimeModel(List.of(), List.of(terminal, consumer), List.of(),
				List.of(terminal.id(), consumer.id()), 2);
		var generated = SparkSqlHybridTest.generate(dependent);
		assertThat(generated.capabilities().get(terminal.id()).nativeSql()).isTrue();
		assertThat(generated.capabilities().get(consumer.id()).nativeSql()).isFalse();
		generated.registerUdfs(spark);
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		Object actual = SparkSqlFeelValueCodec
				.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
		assertThat(actual).isEqualTo(java.time.Duration.ofNanos(1));
		System.out.println("DURATION ABS DEPENDENCY PARITY: 0 native (0.00%), 1 executed fallback (100.00%)");
		var after = processing.snapshot(spark);
		assertThat(after.nativeActions() - before.nativeActions()).isEqualTo(samples.size());
		assertThat(after.fallbackActions() - before.fallbackActions()).isEqualTo(1);
		assertThat(after.unknownActions() - before.unknownActions()).isZero();
		assertThat(after.failedActions() - before.failedActions()).isZero();
	}

	@Test
	void terminalTemporalAnyResultsPreservePrecisionOffsetsAndDurationIdentity() {
		var samples = List.of("@\"2020-01-01T23:59:59.123456789+14:00\"",
				"@\"2020-01-01T23:59:59.123456789@Europe/Paris\"", "@\"23:59:59.123456789+14:00\"",
				"@\"23:59:59.123456789\"", "@\"P0M\"", "@\"PT0S\"", "@\"PT0.000000001S\"", "@\"-PT0.000000001S\"",
				"@\"PT9223372036854775807S\"", "@\"P12M\"", "@\"2020-01-01T23:59:59.123456789+14:00\".time offset",
				"@\"23:59:59.123456789-03:30\".time offset");
		for (String sample : samples) {
			var compiled = compiledFilterModel(sample);
			var original = compiled.decisions().getFirst();
			var decision = new RuntimeDecision(original.id(), original.resultSlot(), SparkSqlHybridTest.ANY,
					original.dependencies(), original.expression(), original.decisionTable(),
					original.localSlotCount());
			var model = new RuntimeModel(compiled.inputs(), List.of(decision), compiled.businessKnowledgeModels(),
					compiled.evaluationOrder(), compiled.valueSlotCount());
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(model, Map.of())
					.decisionValue(decision.id());
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql()).as(sample).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object raw = spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head()
					.get(0);
			assertThat(raw).as(sample).isInstanceOf(byte[].class);
			Object actual = SparkSqlFeelValueCodec.fromSpark(raw, decision.type());
			assertThat(actual).as(sample).isEqualTo(expected).isInstanceOf(expected.getClass());
		}
		System.out.printf("TEMPORAL ANY PARITY: %d native (100.00%%), 0 executed fallback (0.00%%)%n", samples.size());
		var duration = new RuntimeConstant(RuntimeConstantKind.DURATION, "PT0.000000001S", SparkSqlHybridTest.ANY);
		var terminal = new RuntimeDecision(10, 0, SparkSqlHybridTest.ANY, List.of(), Optional.of(duration));
		var consumer = new RuntimeDecision(11, 1, SparkSqlHybridTest.ANY, List.of(10),
				Optional.of(new RuntimeValueReference(0, SparkSqlHybridTest.ANY)));
		var dependent = new RuntimeModel(List.of(), List.of(terminal, consumer), List.of(), List.of(10, 11), 2);
		var generated = SparkSqlHybridTest.generate(dependent);
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		assertThat(generated.capabilities().get(11).nativeSql()).isFalse();
		generated.registerUdfs(spark);
		spark.range(1).createOrReplaceTempView("hybrid_inputs");
		Object actual = SparkSqlFeelValueCodec
				.fromSpark(spark.sql(generated.sqlFiles().get("Decision_1.sql")).head().get(0));
		assertThat(actual).isEqualTo(java.time.Duration.ofNanos(1));
		System.out.println("TEMPORAL DEPENDENCY PARITY: 0 native (0.00%), 1 executed fallback (100.00%)");
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
	void literalTemporalArithmeticFoldingPreservesOffsetsNanosecondsAndSubtypes() {
		var samples = List.of("@\"2021-01-12T10:10:10+11:00\" + @\"P1DT1H\"",
				"@\"2021-01-12T10:10:10+11:00\" - @\"PT0.000000001S\"",
				"@\"2021-03-28T01:59:59.999999999@Europe/Paris\" + @\"PT0.000000001S\"",
				"@\"2021-10-31T01:59:59.999999999@Europe/Paris\" + @\"PT1H\"",
				"@\"2020-01-31T10:10:10+11:00\" + @\"P1M\"", "@\"P1M\" + @\"2020-01-31T10:10:10+11:00\"",
				"@\"P1DT1H\" + @\"2021-01-12T10:10:10+11:00\"", "@\"10:15:00+11:00\" + @\"P1D\"",
				"@\"23:59:59.999999999-03:30\" + @\"PT0.000000001S\"", "@\"00:00:00+14:00\" - @\"PT0.000000001S\"",
				"@\"10:10:10+11:00\" - @\"PT1H\"", "@\"P1Y\" + @\"P2M\"", "@\"P1Y\" - @\"P12M\"",
				"@\"PT0.000000001S\" + @\"PT0.000000002S\"", "@\"PT1S\" - @\"PT0.000000001S\"",
				"@\"PT0.000000001S\" * 2", "@\"PT0.000000002S\" / 2", "@\"P1Y\" * 0", "@\"PT1S\" * 0");
		for (String sample : samples) {
			var compiled = compiledFilterModel(sample);
			var original = compiled.decisions().getFirst();
			var decision = new RuntimeDecision(original.id(), original.resultSlot(), SparkSqlHybridTest.ANY,
					original.dependencies(), original.expression(), original.decisionTable(),
					original.localSlotCount());
			var model = new RuntimeModel(compiled.inputs(), List.of(decision), compiled.businessKnowledgeModels(),
					compiled.evaluationOrder(), compiled.valueSlotCount());
			var lowered = compiledFilterModel(sample, true);
			Object expected = new io.finmsg.dmn.runtime.DmnRuntime().evaluate(lowered, Map.of())
					.decisionValue(lowered.decisions().getFirst().id());
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(decision.id()).nativeSql()).as(sample).isTrue();
			assertThat(generated.udfs()).isEmpty();
			spark.range(1).createOrReplaceTempView("hybrid_inputs");
			Object raw = spark.sql(generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql")).head()
					.get(0);
			assertThat(raw).as(sample).isInstanceOf(byte[].class);
			assertThat(SparkSqlFeelValueCodec.fromSpark(raw, decision.type())).as(sample).isEqualTo(expected)
					.isInstanceOf(expected.getClass());
		}
		System.out.printf("TEMPORAL ARITHMETIC PARITY: %d native (100.00%%), 0 executed fallback (0.00%%)%n",
				samples.size());
		var durationType = RuntimeType.scalar(RuntimeTypeKind.DURATION);
		var dynamic = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, new RuntimeValueReference(0, durationType),
				new RuntimeConstant(RuntimeConstantKind.DURATION, "PT0.000000001S", durationType), durationType);
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, durationType)),
				List.of(new RuntimeDecision(10, 1, SparkSqlHybridTest.ANY, List.of(5), Optional.of(dynamic))),
				List.of(), List.of(10), 2);
		assertThat(new SparkSqlCapabilityAnalyzer().analyze(model, 10, true).nativeSql()).isFalse();
	}

	@Test
	void extremeRoundingScalesPreserveNativeValidation() {
		for (String scale : List.of("6176", "6177", "-6112")) {
			var expression = new RuntimeFunctionCall("round up",
					List.of(SparkSqlHybridTest.number("5.5"), SparkSqlHybridTest.number(scale)),
					SparkSqlHybridTest.NUMBER);
			var decision = new RuntimeDecision(10, 0, SparkSqlHybridTest.NUMBER, List.of(), Optional.of(expression));
			var model = new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(10), 1);
			var generated = SparkSqlHybridTest.generate(model);
			assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
			assertThat(generated.udfs()).isEmpty();
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
		spark.createDataFrame(
				List.of(SparkSqlFeelValueCodec.inputRow(model, Map.of(0, value), generated.inputSchema())),
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
			var frame = spark.createDataFrame(List.of(
					SparkSqlFeelValueCodec.inputRow(model, Map.of(0, new BigDecimal("40")), generated.inputSchema())),
					generated.inputSchema());
			@SuppressWarnings("unchecked")
			Dataset<Row> results = (Dataset<Row>) runner
					.getMethod("evaluate", SparkSession.class, Dataset.class, String.class)
					.invoke(null, spark, frame, "Decision_2");
			assertThat(SparkSqlFeelValueCodec.fromSpark(results.head().get(0))).isEqualTo(new BigDecimal("42"));
		}
	}
}

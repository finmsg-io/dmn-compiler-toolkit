package io.finmsg.dmn.generator.sparksql;

import static org.assertj.core.api.Assertions.*;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import io.finmsg.dmn.runtime.RuntimeContextValue;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.junit.jupiter.api.Test;

class SparkSqlHybridTest {
	@Test
	void directInvocationUsesSuppliedParametersInsteadOfGlobalDecisions() {
		var functionType = RuntimeType.function(List.of(STRING), STRING);
		var function = new RuntimeFunctionDefinition(List.of(new RuntimeFunctionParameter("input decision", 0, STRING)),
				Optional.of(new RuntimeLocalReference(0, STRING)), false, functionType);
		var global = new RuntimeDecision(10, 0, STRING, List.of(), Optional.of(string("global")));
		var service = new RuntimeBkm(20, 1, functionType, List.of(10), RuntimeFunctionKind.FEEL, Optional.of(function));
		var model = new RuntimeModel(List.of(), List.of(global), List.of(service), List.of(10, 20), 2);
		var plan = SparkSqlInvocationPlan.create(model, 20);
		int parameterSlot = plan.parameterNames().keySet().iterator().next();
		var udf = new SparkSqlDecisionUdf(plan.model(), plan.decisionId());
		assertThat(udf.evaluate(SparkSqlFeelValueCodec.inputRow(plan.model(), Map.of(parameterSlot, "supplied"))))
				.isEqualTo("supplied");
		assertThat(udf.evaluate(SparkSqlFeelValueCodec.inputRow(plan.model(), Map.of(parameterSlot, BigDecimal.ONE))))
				.isNull();
		var missing = new HashMap<Integer, Object>();
		missing.put(parameterSlot, null);
		assertThat(udf.evaluate(SparkSqlFeelValueCodec.inputRow(plan.model(), missing))).isNull();
	}
	static final RuntimeType NUMBER = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
	static final RuntimeType STRING = RuntimeType.scalar(RuntimeTypeKind.STRING);
	static final RuntimeType ANY = RuntimeType.scalar(RuntimeTypeKind.ANY);

	static RuntimeConstant number(String value) {
		return new RuntimeConstant(RuntimeConstantKind.NUMBER, value, NUMBER);
	}
	static RuntimeConstant string(String value) {
		return new RuntimeConstant(RuntimeConstantKind.STRING, value, STRING);
	}
	static RuntimeOptimizedModel optimized(RuntimeModel model) {
		return new RuntimeOptimizedModel(model, List.of(), List.of(), List.of());
	}
	static DmnSparkSqlGeneratorResult generate(RuntimeModel model) {
		return new DmnSparkSqlGenerator().generate(optimized(model),
				DmnSparkSqlGeneratorOptions.of("hybrid_inputs", true, Map.of()));
	}
	static RuntimeModel mixedModel() {
		RuntimeType listType = RuntimeType.element(RuntimeTypeKind.LIST, ANY);
		var list = new RuntimeListExpression(List.of(number("1"), string("1"),
				new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)),
				new RuntimeConstant(RuntimeConstantKind.NULL, "null", ANY)), listType);
		return new RuntimeModel(List.of(), List.of(new RuntimeDecision(10, 0, listType, List.of(), Optional.of(list))),
				List.of(), List.of(10), 1);
	}
	static RuntimeModel closureModel() {
		RuntimeType functionType = RuntimeType.function(List.of(ANY), ANY);
		var function = new RuntimeFunctionDefinition(List.of(new RuntimeFunctionParameter("x", 0, ANY)),
				Optional.of(new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, new RuntimeLocalReference(0, ANY),
						new RuntimeValueReference(0, ANY), ANY)),
				false, functionType);
		var bkm = new RuntimeBkm(20, 1, functionType, List.of(10), RuntimeFunctionKind.FEEL, Optional.of(function));
		var invocation = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimeValueReference(1, functionType)), List.of(), List.of(number("2")), ANY);
		var decision = new RuntimeDecision(30, 2, ANY, List.of(20), Optional.of(invocation));
		return new RuntimeModel(List.of(new RuntimeInput(10, 0, ANY)), List.of(decision), List.of(bkm), List.of(20, 30),
				3);
	}

	@Test
	void roundTripsExactNestedFeelValues() {
		var context = new LinkedHashMap<String, Object>();
		context.put("number", new BigDecimal("1234567890123456789012345678901234567890.12345678901234567890"));
		context.put("list", Arrays.asList(BigDecimal.ONE, "1", true, null, List.of()));
		context.put("date", LocalDate.of(2024, 2, 29));
		context.put("time", OffsetTime.parse("12:00:00.123456789+02:00"));
		context.put("localTime", LocalTime.parse("12:00:00.123456789"));
		context.put("dateTime", OffsetDateTime.parse("2024-02-29T12:00:00.123456789+02:00"));
		context.put("localDateTime", LocalDateTime.parse("2024-02-29T12:00:00.123456789"));
		context.put("zoned", ZonedDateTime.parse("2024-02-29T12:00:00+01:00[Europe/Zurich]"));
		context.put("duration", Duration.parse("PT0.123456789S"));
		context.put("period", Period.ofMonths(-15));
		context.put("namedTime", new DmnRuntime.NamedZoneTime(LocalTime.NOON, ZoneId.of("Europe/Zurich")));
		context.put("namedDateTime",
				new DmnRuntime.NamedZoneDateTime(LocalDateTime.of(2024, 2, 29, 12, 0), ZoneId.of("Europe/Zurich")));
		context.put("null", null);
		assertThat(SparkSqlFeelValueCodec.decode(SparkSqlFeelValueCodec.encode(context))).isEqualTo(context);
		var indexed = new RuntimeContextValue(new ArrayList<>(context.values()), context);
		assertThat(SparkSqlFeelValueCodec.decode(SparkSqlFeelValueCodec.encode(indexed))).isEqualTo(indexed);
	}

	@Test
	void preservesMixedTypesAndNullMembers() {
		var udf = new SparkSqlDecisionUdf(mixedModel(), 10);
		Object value = SparkSqlFeelValueCodec.fromSpark(udf.call(RowFactory.create()));
		assertThat(value).isEqualTo(Arrays.asList(BigDecimal.ONE, "1", true, null));
	}

	@Test
	void executesCapturedInputAfterSparkSerialization() throws Exception {
		var udf = new SparkSqlDecisionUdf(closureModel(), 30);
		var bytes = new ByteArrayOutputStream();
		try (var out = new ObjectOutputStream(bytes)) {
			out.writeObject(udf);
		}
		SparkSqlDecisionUdf restored;
		try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			restored = (SparkSqlDecisionUdf) in.readObject();
		}
		var input = new BigDecimal("12345678901234567890.123456789");
		assertThat(restored.evaluate(SparkSqlFeelValueCodec.inputRow(closureModel(), Map.of(0, input))))
				.isEqualTo(input.add(new BigDecimal("2")));
	}

	@Test
	void excludesUnrelatedExternalBkmFromEvaluation() {
		RuntimeType functionType = RuntimeType.function(List.of(), ANY);
		var bkm = new RuntimeBkm(100, 0, functionType, List.of());
		var decision = new RuntimeDecision(200, 1, ANY, List.of(), Optional.of(number("42")));
		var model = new RuntimeModel(List.of(), List.of(decision), List.of(bkm), List.of(100, 200), 2);
		assertThat(new SparkSqlDecisionUdf(model, 200).evaluate(RowFactory.create())).isEqualTo(new BigDecimal("42"));
		assertThat(generate(model).udfs()).isEmpty();
	}

	@Test
	void routesNumericDecisionTableNatively() {
		var generated = generate(numericTableModel());

		assertThat(generated.udfs()).isEmpty();
		assertThat(generated.capabilities().get(10).nativeSql()).isTrue();
		assertThat(generated.sqlFiles().get("Decision_1.sql")).contains("_cte_1");
	}

	static RuntimeModel numericTableModel() {
		var input = new RuntimeDecisionTableInput(new RuntimeValueReference(0, NUMBER), Optional.empty(), NUMBER);
		var output = new RuntimeDecisionTableOutput(Optional.of("result"), NUMBER, Optional.empty(), Optional.empty());
		var anyInput = new RuntimeUnaryTests(false, true, List.of());
		var rule = new RuntimeDecisionTableRule(0, List.of(anyInput), List.of(number("42")), List.of());
		var table = new RuntimeDecisionTable(RuntimeHitPolicy.FIRST, Optional.empty(), List.of(input), List.of(output),
				List.of(rule), 0);
		var decision = new RuntimeDecision(10, 1, NUMBER, List.of(), Optional.empty(), Optional.of(table), 0);
		return new RuntimeModel(List.of(new RuntimeInput(5, 0, NUMBER)), List.of(decision), List.of(), List.of(10), 2);
	}

	@Test
	void routesUnsafeDecisionsAndTheirConsumersStatically() {
		RuntimeModel model = mixedModel();
		var consumer = new RuntimeDecision(99, 1, model.decisions().getFirst().type(), List.of(),
				Optional.of(new RuntimeValueReference(0, model.decisions().getFirst().type())));
		model = new RuntimeModel(List.of(), List.of(model.decisions().getFirst(), consumer), List.of(), List.of(10, 99),
				2);
		var generated = generate(model);
		assertThat(generated.udfs()).hasSize(2);
		assertThat(generated.capabilities().get(99).nativeSql()).isFalse();
		assertThat(generated.sqlFiles().get("Decision_1.sql")).contains("dmn_").doesNotContain("_cte_0");
		assertThatThrownBy(() -> new DmnSparkSqlGenerator().generate(optimized(mixedModel())))
				.isInstanceOf(UnsupportedRelationalSqlException.class).hasMessageContaining("Heterogeneous");
	}

	@Test
	void leavesSimpleStringProjectionNative() {
		var model = new RuntimeModel(List.of(new RuntimeInput(5, 0, STRING)), List
				.of(new RuntimeDecision(10, 1, STRING, List.of(5), Optional.of(new RuntimeValueReference(0, STRING)))),
				List.of(), List.of(10), 2);
		assertThat(generate(model).udfs()).isEmpty();
		assertThat(generate(model).capabilities().get(10).nativeSql()).isTrue();
	}

	@Test
	void failsPlanningForRuntimeUnsupportedBkm() {
		RuntimeType functionType = RuntimeType.function(List.of(), NUMBER);
		var bkm = new RuntimeBkm(5, 0, functionType, List.of());
		var decision = new RuntimeDecision(10, 1, NUMBER, List.of(5), Optional.of(number("1")));
		var model = new RuntimeModel(List.of(), List.of(decision), List.of(bkm), List.of(5, 10), 2);
		assertThatThrownBy(() -> generate(model)).isInstanceOf(UnsupportedRelationalSqlException.class)
				.hasMessageContaining("not supported by DmnRuntime");
	}

	@Test
	void rejectsInvalidPayloadAndClosureExports() {
		assertThatThrownBy(() -> SparkSqlFeelValueCodec.decode(new byte[]{1, 2, 3}))
				.isInstanceOf(IllegalArgumentException.class);
	}

}

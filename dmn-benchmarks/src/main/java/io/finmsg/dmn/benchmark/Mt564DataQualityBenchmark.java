package io.finmsg.dmn.benchmark;

import io.finmsg.dmn.benchmark.provider.ReferenceModelRegistry;
import io.finmsg.dmn.benchmark.provider.ReferenceModelRegistry.CompiledModelHolder;
import io.finmsg.dmn.compiler.DmnCompiledModel;
import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Reference benchmark for the normalized SWIFT MT564 data-quality showcase. */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(3)
public class Mt564DataQualityBenchmark {

	@Param({"validBaseline", "singleViolation", "multipleViolations", "largeStructure"})
	public String scenario;

	private CompiledModelHolder holder;
	private RuntimeModel runtimeModel;
	private DmnRuntime runtime;
	private int qualityReportSlot;
	private List<Map<String, Object>> rawPayloads;
	private List<Map<Integer, Object>> interpreterPayloads;
	private List<Object[]> generatedPayloads;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		holder = ReferenceModelRegistry.loadBundleFromClasspath("models/data-quality", "swift-mt564-dqm");
		runtimeModel = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model();
		runtime = new DmnRuntime();
		qualityReportSlot = holder.decisionSlotMapping().get("QualityReport");
		rawPayloads = fixtures(scenario, 64);
		interpreterPayloads = rawPayloads.stream()
				.map(payload -> ReferenceModelRegistry.buildInterpreterSlotMap(holder, payload)).toList();
		generatedPayloads = rawPayloads.stream().map(payload -> ReferenceModelRegistry.buildInputSlots(holder, payload))
				.toList();
	}

	@Benchmark
	public Object interpreterCore_Mt564(BenchmarkCursor cursor) {
		return evaluateInterpreterAt(cursor.next(interpreterPayloads.size())).value(qualityReportSlot);
	}

	@Benchmark
	public Object generatedDirect_Mt564(BenchmarkCursor cursor) {
		return evaluateGeneratedAt(cursor.next(generatedPayloads.size()))[qualityReportSlot];
	}

	@Benchmark
	public Object interpreterEndToEnd_Mt564(BenchmarkCursor cursor) {
		Map<String, Object> payload = rawPayloads.get(cursor.next(rawPayloads.size()));
		Object report = runtime.evaluate(runtimeModel, ReferenceModelRegistry.buildInterpreterSlotMap(holder, payload))
				.value(qualityReportSlot);
		return adapt(report);
	}

	@Benchmark
	public Object generatedEndToEnd_Mt564(BenchmarkCursor cursor) {
		Map<String, Object> payload = rawPayloads.get(cursor.next(rawPayloads.size()));
		Object report = holder
				.evaluateDirect(ReferenceModelRegistry.buildInputSlots(holder, payload))[qualityReportSlot];
		return adapt(report);
	}

	DmnEvaluationResult evaluateInterpreterAt(int payloadIndex) {
		return runtime.evaluate(runtimeModel, interpreterPayloads.get(payloadIndex));
	}

	Object[] evaluateGeneratedAt(int payloadIndex) {
		return holder.evaluateDirect(generatedPayloads.get(payloadIndex));
	}

	int qualityReportSlot() {
		return qualityReportSlot;
	}

	static List<Map<String, Object>> fixtures(String scenario, int count) {
		List<Map<String, Object>> fixtures = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			String reference = "BENCH-" + String.format("%05d", index);
			Map<String, Object> sequence = switch (scenario) {
				case "validBaseline" -> Map.of("semeRef", reference, "msgFunction", "NEWM", "caEvent", "ACTV");
				case "singleViolation" ->
					Map.of("semeRef", reference, "msgFunction", "INVALID_FUNC", "caEvent", "ACTV");
				case "multipleViolations" ->
					Map.of("semeRef", "", "msgFunction", "INVALID_FUNC", "caEvent", "BAD_EVENT");
				case "largeStructure" -> largeSequence(reference);
				default -> throw new IllegalArgumentException("Unknown MT564 benchmark scenario: " + scenario);
			};
			fixtures.add(Map.of("Message", Map.of("mt_std", Map.of("seq_A", sequence))));
		}
		return List.copyOf(fixtures);
	}

	private static Map<String, Object> largeSequence(String reference) {
		List<Map<String, Object>> options = java.util.stream.IntStream.range(0, 128)
				.mapToObj(index -> Map.<String, Object>of("optionNumber", index, "indicator", "BENCHMARK_ONLY"))
				.toList();
		// Current DQ rules read the three scalar fields. Until DQ-002 introduces
		// collection validation, this structure measures consumer mapping/adaptation;
		// reports must not claim traversal scaling.
		return Map.of("semeRef", reference, "msgFunction", "NEWM", "caEvent", "ACTV", "options", options);
	}

	private static Object adapt(Object report) {
		return DmnCompiledModel.toNativeValue(DmnCompiledModel.toProtobufValue(report));
	}
}

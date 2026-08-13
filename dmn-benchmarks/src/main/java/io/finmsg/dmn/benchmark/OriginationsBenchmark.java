package io.finmsg.dmn.benchmark;

import io.finmsg.dmn.benchmark.data.BenchmarkDataGenerator;
import io.finmsg.dmn.benchmark.provider.ReferenceModelRegistry;
import io.finmsg.dmn.benchmark.provider.ReferenceModelRegistry.CompiledModelHolder;
import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(3)
public class OriginationsBenchmark {

	private CompiledModelHolder holder;
	private RuntimeModel runtimeModel;
	private DmnRuntime runtime;
	private List<Map<Integer, Object>> interpreterPayloads;
	private List<Object[]> slotPayloads;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		holder = ReferenceModelRegistry.loadFromClasspath("models/originations.dmn");
		runtimeModel = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model();
		runtime = new DmnRuntime();
		List<Map<String, Object>> payloads = new BenchmarkDataGenerator(1207L).generateOriginationPayloads(1000);
		interpreterPayloads = payloads.stream().map(p -> ReferenceModelRegistry.buildInterpreterSlotMap(holder, p))
				.toList();
		slotPayloads = payloads.stream().map(p -> ReferenceModelRegistry.buildInputSlots(holder, p)).toList();
	}

	@Benchmark
	public DmnEvaluationResult interpreterCore_Originations(BenchmarkCursor cursor) {
		return evaluateInterpreterAt(cursor.next(interpreterPayloads.size()));
	}

	@Benchmark
	public Object generatedDirect_Originations(BenchmarkCursor cursor) {
		return evaluateGeneratedAt(cursor.next(slotPayloads.size()));
	}

	@Benchmark
	public Object generatedAdapter_Originations(BenchmarkCursor cursor) throws Exception {
		return holder.evaluateAdapter(slotPayloads.get(cursor.next(slotPayloads.size())));
	}

	DmnEvaluationResult evaluateInterpreterAt(int payloadIndex) {
		return runtime.evaluate(runtimeModel, interpreterPayloads.get(payloadIndex));
	}

	Object evaluateGeneratedAt(int payloadIndex) {
		return holder.evaluateDirect(slotPayloads.get(payloadIndex));
	}
}

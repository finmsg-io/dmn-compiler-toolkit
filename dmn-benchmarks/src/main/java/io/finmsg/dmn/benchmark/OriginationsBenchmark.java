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

@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class OriginationsBenchmark {

	private CompiledModelHolder holder;
	private RuntimeModel runtimeModel;
	private DmnRuntime runtime;
	private List<Map<Integer, Object>> interpreterPayloads;
	private List<Object[]> slotPayloads;
	private int index;

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
	public DmnEvaluationResult interpreter_Originations() {
		return evaluateInterpreterAt((index++) % interpreterPayloads.size());
	}

	@Benchmark
	public Object generatedJava_Originations() throws Exception {
		return evaluateGeneratedAt((index++) % slotPayloads.size());
	}

	DmnEvaluationResult evaluateInterpreterAt(int payloadIndex) {
		return runtime.evaluate(runtimeModel, interpreterPayloads.get(payloadIndex));
	}

	Object evaluateGeneratedAt(int payloadIndex) throws Exception {
		return holder.evaluateMethod().invoke(holder.generatedEngineInstance(), (Object) slotPayloads.get(payloadIndex));
	}
}

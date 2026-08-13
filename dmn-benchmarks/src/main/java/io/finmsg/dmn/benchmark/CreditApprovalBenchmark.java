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
public class CreditApprovalBenchmark {

	private CompiledModelHolder holder;
	private RuntimeModel runtimeModel;
	private DmnRuntime runtime;
	private List<Map<Integer, Object>> interpreterPayloads;
	private List<Object[]> slotPayloads;
	private List<Map<String, Object>> rawPayloads;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		holder = ReferenceModelRegistry.loadFromClasspath("models/credit-approval.dmn");
		runtimeModel = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model();
		runtime = new DmnRuntime();

		BenchmarkDataGenerator generator = new BenchmarkDataGenerator(202L);
		rawPayloads = generator.generateCreditPayloads(1000);
		interpreterPayloads = rawPayloads.stream().map(p -> ReferenceModelRegistry.buildInterpreterSlotMap(holder, p))
				.toList();
		slotPayloads = rawPayloads.stream().map(p -> ReferenceModelRegistry.buildInputSlots(holder, p)).toList();
	}

	@Benchmark
	public DmnEvaluationResult interpreterCore_CreditApproval(BenchmarkCursor cursor) {
		int idx = cursor.next(interpreterPayloads.size());
		Map<Integer, Object> inputs = interpreterPayloads.get(idx);
		return runtime.evaluate(runtimeModel, inputs);
	}

	@Benchmark
	public Object generatedDirect_CreditApproval(BenchmarkCursor cursor) {
		int idx = cursor.next(slotPayloads.size());
		Object[] slots = slotPayloads.get(idx);
		return holder.evaluateDirect(slots);
	}

	@Benchmark
	public Object generatedAdapter_CreditApproval(BenchmarkCursor cursor) throws Exception {
		return holder.evaluateAdapter(slotPayloads.get(cursor.next(slotPayloads.size())));
	}

	@Benchmark
	public Object generatedEndToEnd_CreditApproval(BenchmarkCursor cursor) {
		return holder.evaluateDirect(
				ReferenceModelRegistry.buildInputSlots(holder, rawPayloads.get(cursor.next(rawPayloads.size()))));
	}
}

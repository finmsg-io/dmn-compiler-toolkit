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
@Fork(1)
public class CreditApprovalBenchmark {

	private CompiledModelHolder holder;
	private RuntimeModel runtimeModel;
	private DmnRuntime runtime;
	private List<Map<Integer, Object>> interpreterPayloads;
	private List<Object[]> slotPayloads;
	private int index;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		holder = ReferenceModelRegistry.loadFromClasspath("models/credit-approval.dmn");
		runtimeModel = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model();
		runtime = new DmnRuntime();

		BenchmarkDataGenerator generator = new BenchmarkDataGenerator(202L);
		List<Map<String, Object>> rawPayloads = generator.generateCreditPayloads(1000);
		interpreterPayloads = rawPayloads.stream().map(p -> ReferenceModelRegistry.buildInterpreterSlotMap(holder, p))
				.toList();
		slotPayloads = rawPayloads.stream().map(p -> ReferenceModelRegistry.buildInputSlots(holder, p)).toList();
		index = 0;
	}

	@Benchmark
	public DmnEvaluationResult interpreter_CreditApproval() {
		int idx = (index++) % interpreterPayloads.size();
		Map<Integer, Object> inputs = interpreterPayloads.get(idx);
		return runtime.evaluate(runtimeModel, inputs);
	}

	@Benchmark
	public Object generatedJava_CreditApproval() throws Exception {
		int idx = (index++) % slotPayloads.size();
		Object[] slots = slotPayloads.get(idx);
		return holder.evaluateMethod().invoke(holder.generatedEngineInstance(), (Object) slots);
	}
}

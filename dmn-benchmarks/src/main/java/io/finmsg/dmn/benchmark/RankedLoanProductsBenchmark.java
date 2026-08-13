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
public class RankedLoanProductsBenchmark {
	private CompiledModelHolder holder;
	private RuntimeModel runtimeModel;
	private DmnRuntime runtime;
	private List<Map<String, Object>> rawPayloads;
	private List<Map<Integer, Object>> interpreterPayloads;
	private List<Object[]> slotPayloads;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		holder = ReferenceModelRegistry.loadFromClasspath("models/ranked-loan-products.dmn");
		runtimeModel = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model();
		runtime = new DmnRuntime();
		rawPayloads = new BenchmarkDataGenerator(2903L).generateLoanProductPayloads(1000);
		interpreterPayloads = rawPayloads.stream().map(p -> ReferenceModelRegistry.buildInterpreterSlotMap(holder, p))
				.toList();
		slotPayloads = rawPayloads.stream().map(p -> ReferenceModelRegistry.buildInputSlots(holder, p)).toList();
	}

	@Benchmark
	public DmnEvaluationResult interpreterCore_RankedLoanProducts(BenchmarkCursor cursor) {
		return evaluateInterpreterAt(cursor.next(interpreterPayloads.size()));
	}

	@Benchmark
	public Object generatedDirect_RankedLoanProducts(BenchmarkCursor cursor) {
		return evaluateGeneratedAt(cursor.next(slotPayloads.size()));
	}

	@Benchmark
	public Object generatedAdapter_RankedLoanProducts(BenchmarkCursor cursor) throws Exception {
		return holder.evaluateAdapter(slotPayloads.get(cursor.next(slotPayloads.size())));
	}

	@Benchmark
	public Object generatedEndToEnd_RankedLoanProducts(BenchmarkCursor cursor) {
		return holder.evaluateDirect(
				ReferenceModelRegistry.buildInputSlots(holder, rawPayloads.get(cursor.next(rawPayloads.size()))));
	}

	DmnEvaluationResult evaluateInterpreterAt(int payloadIndex) {
		return runtime.evaluate(runtimeModel, interpreterPayloads.get(payloadIndex));
	}

	Object evaluateGeneratedAt(int payloadIndex) {
		return holder.evaluateDirect(slotPayloads.get(payloadIndex));
	}
}

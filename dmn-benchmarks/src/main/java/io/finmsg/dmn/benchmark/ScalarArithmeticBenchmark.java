package io.finmsg.dmn.benchmark;

import io.finmsg.dmn.benchmark.provider.ReferenceModelRegistry;
import io.finmsg.dmn.benchmark.provider.ReferenceModelRegistry.CompiledModelHolder;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;

import org.openjdk.jmh.annotations.*;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(3)
public class ScalarArithmeticBenchmark {

	private CompiledModelHolder holder;
	private RuntimeModel runtimeModel;
	private DmnRuntime runtime;
	private Map<Integer, Object> interpreterInputMap;
	private Object[] inputSlots;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		String dmnXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/"
				             id="scalar-arithmetic" name="Scalar arithmetic" namespace="urn:finmsg:benchmark:scalar">
				  <inputData id="input-age" name="age">
				    <variable id="input-age-var" name="age" typeRef="number"/>
				  </inputData>
				  <decision id="decision-next-age" name="nextAge">
				    <variable id="decision-next-age-var" name="nextAge" typeRef="number"/>
				    <informationRequirement><requiredInput href="#input-age"/></informationRequirement>
				    <literalExpression><text>age + 1</text></literalExpression>
				  </decision>
				</definitions>
				""";

		DmnSource source = new DmnSource(new DmnSourceId(URI.create("urn:scalar-arithmetic.dmn")),
				dmnXml.getBytes(StandardCharsets.UTF_8));
		holder = ReferenceModelRegistry.compile("scalar-arithmetic", source);
		runtimeModel = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model();
		runtime = new DmnRuntime();

		Map<String, Object> rawInputs = Map.of("age", BigDecimal.valueOf(25));
		interpreterInputMap = ReferenceModelRegistry.buildInterpreterSlotMap(holder, rawInputs);
		inputSlots = ReferenceModelRegistry.buildInputSlots(holder, rawInputs);
	}

	@Benchmark
	public DmnEvaluationResult interpreterCore_ScalarArithmetic() {
		return runtime.evaluate(runtimeModel, interpreterInputMap);
	}

	@Benchmark
	public Object generatedDirect_ScalarArithmetic() {
		return holder.evaluateDirect(inputSlots);
	}

	@Benchmark
	public Object generatedAdapter_ScalarArithmetic() throws Exception {
		return holder.evaluateAdapter(inputSlots);
	}

	@Benchmark
	public Object invocationControl_ScalarArithmetic() {
		return inputSlots;
	}
}

package io.finmsg.dmn.benchmark;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.frontend.xml.dmn.DmnXmlReader;
import io.finmsg.dmn.ir.RuntimeIrLowerer;
import io.finmsg.dmn.ir.RuntimeIrOptimizer;
import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.semantic.analysis.DmnSemanticPipelineResult;
import org.openjdk.jmh.annotations.*;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class CompilerPhaseBenchmark {
	private byte[] sourceBytes;
	private DmnSource source;
	private DmnModelLoadResult loadedModel;
	private List<DmnSemanticPipelineResult> semanticAnalyses;
	private RuntimeModel loweredModel;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream("models/originations.dmn")) {
			if (input == null) {
				throw new IllegalStateException("Originations benchmark model not found");
			}
			sourceBytes = input.readAllBytes();
		}
		source = new DmnSource(new DmnSourceId(URI.create("urn:benchmark:originations")), sourceBytes);
		loadedModel = new DmnModelLoader().load(source, new InMemoryDmnModelResolver(List.of()));
		DmnModelSetSemanticResult semantic = new DmnModelSetSemanticAnalyzer().analyze(loadedModel);
		semanticAnalyses = semantic.models().stream().map(model -> new DmnSemanticPipelineResult(model.model(),
				model.compilationOrder(), List.of(), model.bindings())).toList();
		loweredModel = new RuntimeIrLowerer().lowerModelSet(semanticAnalyses);
	}

	@Benchmark
	public Definitions xmlParsing() {
		return new DmnXmlReader().read(sourceBytes);
	}

	@Benchmark
	public DmnModelSetSemanticResult semanticAnalysis() {
		return new DmnModelSetSemanticAnalyzer().analyze(loadedModel);
	}

	@Benchmark
	public RuntimeModel runtimeIrLowering() {
		return new RuntimeIrLowerer().lowerModelSet(semanticAnalyses);
	}

	@Benchmark
	public Object runtimeIrOptimization() {
		return new RuntimeIrOptimizer().optimize(loweredModel);
	}

	@Benchmark
	public DmnCompilationResult fullCompilation() {
		return new DmnCompiler().compile(source);
	}
}

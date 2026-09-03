package io.finmsg.dmn.compiler;

import io.finmsg.dmn.ir.RuntimeIrLowerer;
import io.finmsg.dmn.ir.RuntimeIrLoweringException;
import io.finmsg.dmn.ir.RuntimeIrOptimizer;
import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import io.finmsg.dmn.optimizer.DmnOptimizer;
import io.finmsg.dmn.semantic.analysis.DmnSemanticPipelineResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Public one-call orchestration facade for the implemented DMN compiler
 * pipeline.
 */
public final class DmnCompiler {
	private final DmnModelLoader loader;
	private final DmnModelSetSemanticAnalyzer semanticAnalyzer;
	private final RuntimeIrLowerer lowerer;
	private final DmnOptimizer modelOptimizer;
	private final RuntimeIrOptimizer optimizer;

	public DmnCompiler() {
		this(new DmnModelLoader(), new DmnModelSetSemanticAnalyzer(), new RuntimeIrLowerer(), new DmnOptimizer(),
				new RuntimeIrOptimizer());
	}

	DmnCompiler(DmnModelLoader loader, DmnModelSetSemanticAnalyzer semanticAnalyzer, RuntimeIrLowerer lowerer,
			DmnOptimizer modelOptimizer, RuntimeIrOptimizer optimizer) {
		this.loader = Objects.requireNonNull(loader, "loader");
		this.semanticAnalyzer = Objects.requireNonNull(semanticAnalyzer, "semanticAnalyzer");
		this.lowerer = Objects.requireNonNull(lowerer, "lowerer");
		this.modelOptimizer = Objects.requireNonNull(modelOptimizer, "modelOptimizer");
		this.optimizer = Objects.requireNonNull(optimizer, "optimizer");
	}

	public DmnCompilationResult compile(DmnSource root) {
		return compile(root, new InMemoryDmnModelResolver(List.of()), DmnCompilerOptions.defaults());
	}

	public DmnCompilationResult compile(DmnSource root, DmnModelResolver resolver) {
		return compile(root, resolver, DmnCompilerOptions.defaults());
	}

	public DmnCompilationResult compile(DmnSource root, DmnModelResolver resolver, DmnCompilerOptions options) {
		Objects.requireNonNull(root, "root");
		Objects.requireNonNull(resolver, "resolver");
		Objects.requireNonNull(options, "options");
		DmnModelLoadResult loaded = loader.load(root, resolver, options.modelLoadOptions());
		DmnModelSetSemanticResult semantic = semanticAnalyzer.analyze(loaded);
		if (semantic.hasErrors()) {
			return result(loaded, semantic, Optional.empty(), semantic.diagnostics());
		}

		List<DmnSemanticPipelineResult> analyses = semantic.models().stream()
				.map(model -> new DmnSemanticPipelineResult(model.model(), model.compilationOrder(), List.of(),
						model.bindings()))
				.toList();
		try {
			RuntimeModel lowered = lowerer.lowerModelSet(analyses);
			RuntimeModel selected = options.runtimeModelMode() == RuntimeModelMode.OPTIMIZED
					? modelOptimizer.optimize(lowered)
					: lowered;
			RuntimeOptimizedModel optimized = optimizer.optimize(selected);
			return result(loaded, semantic, Optional.of(optimized), semantic.diagnostics());
		} catch (RuntimeIrLoweringException exception) {
			List<DmnCompilerDiagnostic> diagnostics = new ArrayList<>(semantic.diagnostics());
			diagnostics.add(loweringDiagnostic(semantic, exception));
			return result(loaded, semantic, Optional.empty(), diagnostics);
		}
	}

	private static DmnCompilationResult result(DmnModelLoadResult loaded, DmnModelSetSemanticResult semantic,
			Optional<RuntimeOptimizedModel> optimized, List<DmnCompilerDiagnostic> diagnostics) {
		return new DmnCompilationResult(loaded.rootId(), loaded, semantic, optimized, diagnostics);
	}

	private static DmnCompilerDiagnostic loweringDiagnostic(DmnModelSetSemanticResult semantic,
			RuntimeIrLoweringException exception) {
		Optional<DmnModelIdentity> identity = semantic.model(semantic.rootId()).map(DmnSemanticModel::identity);
		return new DmnCompilerDiagnostic(DmnDiagnosticSeverity.ERROR, DmnCompilerPhase.RUNTIME_IR_LOWERING,
				DmnDiagnosticCodes.RUNTIME_IR_LOWERING, exception.getMessage(),
				new DmnDiagnosticOrigin(semantic.rootId(), identity, Optional.empty()), Optional.empty(), List.of(),
				List.of());
	}
}

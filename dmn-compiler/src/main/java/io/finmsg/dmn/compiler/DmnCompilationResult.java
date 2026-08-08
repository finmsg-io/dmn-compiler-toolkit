package io.finmsg.dmn.compiler;

import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable evidence returned by one complete compiler-facade invocation. */
public record DmnCompilationResult(DmnSourceId rootId, DmnModelLoadResult loadedModels,
		DmnModelSetSemanticResult semanticResult, Optional<RuntimeOptimizedModel> optimizedRuntimeModel,
		List<DmnCompilerDiagnostic> diagnostics) {

	public DmnCompilationResult {
		Objects.requireNonNull(rootId, "rootId");
		Objects.requireNonNull(loadedModels, "loadedModels");
		Objects.requireNonNull(semanticResult, "semanticResult");
		Objects.requireNonNull(optimizedRuntimeModel, "optimizedRuntimeModel");
		Objects.requireNonNull(diagnostics, "diagnostics");
		if (!rootId.equals(loadedModels.rootId()) || !rootId.equals(semanticResult.rootId())) {
			throw new IllegalArgumentException("Compilation stage results must share the root identity");
		}
		diagnostics = diagnostics.stream().map(diagnostic -> Objects.requireNonNull(diagnostic, "diagnostic"))
				.sorted(DmnCompilerDiagnostic.ORDER).toList();
		if (optimizedRuntimeModel.isPresent()
				&& diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DmnDiagnosticSeverity.ERROR)) {
			throw new IllegalArgumentException("Runtime IR must be absent when compilation has errors");
		}
	}

	public boolean isSuccess() {
		return optimizedRuntimeModel.isPresent() && !hasErrors();
	}

	public boolean hasErrors() {
		return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DmnDiagnosticSeverity.ERROR);
	}

	public Optional<DmnCompiledModel> compiledModel() {
		if (!isSuccess() || optimizedRuntimeModel.isEmpty()) {
			return Optional.empty();
		}
		RuntimeOptimizedModel optimized = optimizedRuntimeModel.get();
		DmnSemanticModel rootSemantic = semanticResult.model(rootId).orElse(null);
		String namespace = rootSemantic != null ? rootSemantic.identity().namespace() : "";
		String modelName = rootSemantic != null ? rootSemantic.identity().name() : "";

		Map<String, Integer> inputSlots = new java.util.LinkedHashMap<>();
		Map<String, Integer> decisionSlots = new java.util.LinkedHashMap<>();
		Map<String, io.finmsg.dmn.ir.RuntimeType> inputTypes = new java.util.LinkedHashMap<>();
		Map<String, io.finmsg.dmn.ir.RuntimeType> decisionTypes = new java.util.LinkedHashMap<>();

		Map<Integer, io.finmsg.dmn.ir.RuntimeInput> inputsBySlot = new java.util.HashMap<>();
		optimized.model().inputs().forEach(in -> inputsBySlot.put(in.valueSlot(), in));
		Map<Integer, io.finmsg.dmn.ir.RuntimeDecision> decisionsBySlot = new java.util.HashMap<>();
		optimized.model().decisions().forEach(dec -> decisionsBySlot.put(dec.resultSlot(), dec));

		int[] runtimeId = {0};
		semanticResult.models().forEach(sem -> {
			io.finmsg.dmn.model.Definitions defs = sem.model();
			for (io.finmsg.dmn.model.DrgElement elem : defs.getDrgElementsList()) {
				switch (elem.getElementCase()) {
					case INPUT_DATA -> {
						int currentId = runtimeId[0]++;
						String name = elem.getInputData().getVariable().getNode().getName();
						if (name.isBlank())
							name = elem.getInputData().getNode().getName();
						inputSlots.put(name, currentId);
						io.finmsg.dmn.ir.RuntimeInput in = inputsBySlot.get(currentId);
						if (in != null)
							inputTypes.put(name, in.type());
					}
					case DECISION -> {
						int currentId = runtimeId[0]++;
						String name = elem.getDecision().getVariable().getNode().getName();
						if (name.isBlank())
							name = elem.getDecision().getNode().getName();
						decisionSlots.put(name, currentId);
						io.finmsg.dmn.ir.RuntimeDecision dec = decisionsBySlot.get(currentId);
						if (dec != null)
							decisionTypes.put(name, dec.type());
					}
					case BUSINESS_KNOWLEDGE_MODEL -> {
						runtimeId[0]++;
					}
					default -> {
					}
				}
			}
		});

		return Optional.of(new DmnCompiledModel(namespace, modelName, optimized, inputSlots, decisionSlots, inputTypes,
				decisionTypes));
	}
}

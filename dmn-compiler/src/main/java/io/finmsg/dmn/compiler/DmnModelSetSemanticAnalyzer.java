package io.finmsg.dmn.compiler;

import io.finmsg.dmn.feel.parser.DmnFeelDiagnostic;
import io.finmsg.dmn.feel.parser.DmnFeelParseResult;
import io.finmsg.dmn.feel.parser.DmnFeelParser;
import io.finmsg.dmn.feel.parser.FeelDiagnostic;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.semantic.analysis.DmnSemanticDiagnostic;
import io.finmsg.dmn.semantic.analysis.DmnSemanticPipeline;
import io.finmsg.dmn.semantic.analysis.DmnSemanticPipelineResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Parses FEEL and performs source-aware semantic analysis for a complete loaded
 * model graph.
 */
public final class DmnModelSetSemanticAnalyzer {
	private final DmnFeelParser feelParser;

	public DmnModelSetSemanticAnalyzer() {
		this(new DmnFeelParser());
	}

	DmnModelSetSemanticAnalyzer(DmnFeelParser feelParser) {
		this.feelParser = Objects.requireNonNull(feelParser, "feelParser");
	}

	public DmnModelSetSemanticResult analyze(DmnModelLoadResult loadedModels) {
		Objects.requireNonNull(loadedModels, "loadedModels");
		List<DmnCompilerDiagnostic> diagnostics = new ArrayList<>(loadedModels.diagnostics());
		if (loadedModels.hasErrors()) {
			return result(loadedModels, List.of(), diagnostics);
		}

		Map<DmnSourceId, Definitions> parsedModels = new LinkedHashMap<>();
		for (LoadedDmnModel loaded : loadedModels.models()) {
			DmnFeelParseResult parsed = feelParser.parseWithDiagnostics(loaded.model());
			parsedModels.put(loaded.id(), parsed.model());
			addFeelDiagnostics(diagnostics, loaded, parsed.diagnostics());
		}
		if (hasErrors(diagnostics)) {
			return result(loadedModels, List.of(), diagnostics);
		}

		List<DmnSemanticModel> semanticModels = new ArrayList<>();
		for (Map.Entry<DmnSourceId, Definitions> entry : parsedModels.entrySet()) {
			List<Definitions> availableModels = parsedModels.entrySet().stream()
					.filter(candidate -> !candidate.getKey().equals(entry.getKey())).map(Map.Entry::getValue).toList();
			DmnSemanticPipelineResult semantic = new DmnSemanticPipeline().analyze(entry.getValue(), availableModels);
			DmnModelIdentity identity = identity(entry.getValue());
			semanticModels.add(new DmnSemanticModel(entry.getKey(), identity, semantic.model(),
					semantic.compilationOrder(), semantic.bindings()));
			addSemanticDiagnostics(diagnostics, entry.getKey(), identity, semantic.diagnostics());
		}
		return result(loadedModels, semanticModels, diagnostics);
	}

	private static DmnModelSetSemanticResult result(DmnModelLoadResult loadedModels, List<DmnSemanticModel> models,
			List<DmnCompilerDiagnostic> diagnostics) {
		return new DmnModelSetSemanticResult(loadedModels.rootId(), models, loadedModels.importEdges(), diagnostics);
	}

	private static void addFeelDiagnostics(List<DmnCompilerDiagnostic> target, LoadedDmnModel loaded,
			List<DmnFeelDiagnostic> source) {
		DmnDiagnosticOrigin origin = new DmnDiagnosticOrigin(loaded.id(), Optional.of(identity(loaded.model())),
				Optional.empty());
		for (DmnFeelDiagnostic diagnostic : source) {
			for (FeelDiagnostic detail : diagnostic.diagnostics()) {
				String message = diagnostic.path() + " at " + detail.line() + ":" + detail.charPositionInLine() + ": "
						+ detail.message();
				target.add(new DmnCompilerDiagnostic(DmnDiagnosticSeverity.ERROR, DmnCompilerPhase.FEEL_PARSING,
						DmnDiagnosticCodes.FEEL_SYNTAX, message, origin, Optional.empty(), List.of(), List.of()));
			}
		}
	}

	private static void addSemanticDiagnostics(List<DmnCompilerDiagnostic> target, DmnSourceId sourceId,
			DmnModelIdentity identity, List<DmnSemanticDiagnostic> source) {
		DmnDiagnosticOrigin origin = new DmnDiagnosticOrigin(sourceId, Optional.of(identity), Optional.empty());
		for (DmnSemanticDiagnostic diagnostic : source) {
			target.add(new DmnCompilerDiagnostic(DmnDiagnosticSeverity.ERROR, DmnCompilerPhase.SEMANTIC_ANALYSIS,
					diagnostic.code(), diagnostic.path() + ": " + diagnostic.message(), origin, Optional.empty(),
					List.of(), List.of()));
		}
	}

	private static DmnModelIdentity identity(Definitions model) {
		return new DmnModelIdentity(model.getNamespace(), model.getNode().getName());
	}

	private static boolean hasErrors(List<DmnCompilerDiagnostic> diagnostics) {
		return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DmnDiagnosticSeverity.ERROR);
	}
}

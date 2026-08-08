package io.finmsg.dmn.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.ElementReference;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.FeelText;
import io.finmsg.dmn.model.Import;
import io.finmsg.dmn.model.InformationRequirement;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.semantic.analysis.DmnSymbolBinding;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DmnModelSetSemanticAnalyzerTest {

	private final DmnModelSetSemanticAnalyzer analyzer = new DmnModelSetSemanticAnalyzer();

	@Test
	void analyzesOneRootIntoAnImmutableSourceAddressableResult() {
		LoadedDmnModel root = loaded("root.dmn", definitions("urn:root", "Root"));

		DmnModelSetSemanticResult result = analyzer.analyze(loadResult(root, List.of(root), List.of()));

		assertThat(result.isValid()).isTrue();
		assertThat(result.models()).singleElement().satisfies(model -> {
			assertThat(model.sourceId()).isEqualTo(root.id());
			assertThat(model.identity()).isEqualTo(new DmnModelIdentity("urn:root", "Root"));
		});
		assertThat(result.model(root.id())).isPresent();
		assertThatThrownBy(() -> result.models().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> result.models().getFirst().bindings().clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void analyzesLinkedModelsInStableOrderWithPerModelCompilationEvidence() {
		Decision baseDecision = decision("base-decision", "Base");
		LoadedDmnModel base = loaded("base.dmn", definitions("urn:base", "Base model").toBuilder()
				.addDrgElements(DrgElement.newBuilder().setDecision(baseDecision)).build());
		Decision rootDecision = decision("root-decision", "Root").toBuilder()
				.addInformationRequirements(InformationRequirement.newBuilder()
						.setDecision(ElementReference.newBuilder().setHref("urn:base#base-decision")))
				.build();
		Definitions rootDefinitions = definitions("urn:root", "Root model").toBuilder()
				.addImports(Import.newBuilder().setNamespace("urn:base").setName("base").setLocationUri("base.dmn"))
				.addDrgElements(DrgElement.newBuilder().setDecision(rootDecision)).build();
		LoadedDmnModel root = loaded("root.dmn", rootDefinitions);
		DmnImportEdge edge = edge(root, base, 0, "base.dmn", "urn:base", "base");

		DmnModelSetSemanticResult result = analyzer.analyze(loadResult(root, List.of(root, base), List.of(edge)));

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.models()).extracting(DmnSemanticModel::sourceId).containsExactly(base.id(), root.id());
		DmnSemanticModel analyzedRoot = result.model(root.id()).orElseThrow();
		assertThat(analyzedRoot.compilationOrder()).extracting(DmnModelSetSemanticAnalyzerTest::elementName)
				.containsExactly("Base", "Root");
		assertThat(analyzedRoot.bindings()).extracting(DmnSymbolBinding::symbolId).contains("base-decision");
		assertThat(result.importEdges()).containsExactly(edge);

		DmnModelSetSemanticResult reversed = analyzer.analyze(loadResult(root, List.of(base, root), List.of(edge)));
		assertThat(reversed).isEqualTo(result);
	}

	@Test
	void preservesLoadErrorsAndStopsBeforeFeelParsing() {
		LoadedDmnModel root = loaded("root.dmn", definitions("urn:root", "Root"));
		DmnImportRequest request = new DmnImportRequest(root.id(), "missing.dmn", "urn:missing", "missing");
		DmnCompilerDiagnostic loadError = new DmnCompilerDiagnostic(DmnDiagnosticSeverity.ERROR,
				DmnCompilerPhase.SOURCE_RESOLUTION, DmnDiagnosticCodes.IMPORT_MISSING, "missing",
				new DmnDiagnosticOrigin(root.id(), Optional.of(new DmnModelIdentity("urn:root", "Root")),
						Optional.of(0)),
				Optional.of(request), List.of(), List.of());
		DmnModelLoadResult loaded = new DmnModelLoadResult(root.id(), List.of(root), List.of(), List.of(loadError));

		DmnModelSetSemanticResult result = analyzer.analyze(loaded);

		assertThat(result.models()).isEmpty();
		assertThat(result.diagnostics()).containsExactly(loadError);
		assertThat(result.hasErrors()).isTrue();
	}

	@Test
	void reportsFeelErrorsWithSourceIdentityAndStopsBeforeSemantics() {
		Decision invalid = decision("invalid", "Invalid").toBuilder().setLogic(DecisionLogic.newBuilder()
				.setLiteralExpression(Feel.newBuilder().setText(FeelText.newBuilder().setText("1 +")))).build();
		LoadedDmnModel root = loaded("root.dmn", definitions("urn:root", "Root").toBuilder()
				.addDrgElements(DrgElement.newBuilder().setDecision(invalid)).build());

		DmnModelSetSemanticResult result = analyzer.analyze(loadResult(root, List.of(root), List.of()));

		assertThat(result.models()).isEmpty();
		assertThat(result.diagnostics()).isNotEmpty().allSatisfy(diagnostic -> {
			assertThat(diagnostic.code()).isEqualTo(DmnDiagnosticCodes.FEEL_SYNTAX);
			assertThat(diagnostic.phase()).isEqualTo(DmnCompilerPhase.FEEL_PARSING);
			assertThat(diagnostic.origin().sourceId()).isEqualTo(root.id());
			assertThat(diagnostic.origin().modelIdentity()).contains(new DmnModelIdentity("urn:root", "Root"));
		});
	}

	@Test
	void adaptsSemanticDiagnosticsWithTheirOriginatingSourceAndNativeCode() {
		Definitions invalid = definitions("urn:root", "Root").toBuilder()
				.addImports(Import.newBuilder().setNamespace("urn:missing")).build();
		LoadedDmnModel root = loaded("root.dmn", invalid);

		DmnModelSetSemanticResult result = analyzer.analyze(loadResult(root, List.of(root), List.of()));

		assertThat(result.models()).singleElement();
		assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
			assertThat(diagnostic.code()).isEqualTo("UNKNOWN_IMPORT");
			assertThat(diagnostic.phase()).isEqualTo(DmnCompilerPhase.SEMANTIC_ANALYSIS);
			assertThat(diagnostic.severity()).isEqualTo(DmnDiagnosticSeverity.ERROR);
			assertThat(diagnostic.origin().sourceId()).isEqualTo(root.id());
			assertThat(diagnostic.origin().modelIdentity()).contains(new DmnModelIdentity("urn:root", "Root"));
		});
		assertThat(result.hasErrors()).isTrue();
	}

	private static DmnModelLoadResult loadResult(LoadedDmnModel root, List<LoadedDmnModel> models,
			List<DmnImportEdge> edges) {
		return new DmnModelLoadResult(root.id(), models, edges, List.of());
	}

	private static LoadedDmnModel loaded(String fileName, Definitions definitions) {
		DmnSource source = new DmnSource(DmnSourceId.of("memory:/models/" + fileName),
				fileName.getBytes(StandardCharsets.UTF_8));
		return new LoadedDmnModel(source, definitions);
	}

	private static Definitions definitions(String namespace, String name) {
		return Definitions.newBuilder().setNode(Node.newBuilder().setId(name + "-id").setName(name))
				.setNamespace(namespace).build();
	}

	private static Decision decision(String id, String name) {
		return Decision.newBuilder().setNode(Node.newBuilder().setId(id).setName(name)).build();
	}

	private static DmnImportEdge edge(LoadedDmnModel importer, LoadedDmnModel imported, int index, String location,
			String namespace, String name) {
		DmnImportRequest request = new DmnImportRequest(importer.id(), location, namespace, name);
		return new DmnImportEdge(importer.id(), index, request, imported.id());
	}

	private static String elementName(DrgElement element) {
		return element.hasDecision() ? element.getDecision().getNode().getName() : "";
	}
}

package io.finmsg.dmn.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DmnCompilerTest {

	private final DmnCompiler compiler = new DmnCompiler();

	@Test
	void compilesOneSourceThroughOptimizedRuntimeIrWithEquivalentOverloads() {
		DmnSource root = source("root.dmn", model("urn:root", "Root", """
				<decision id="decision" name="Decision">
				  <variable id="variable" name="Decision" typeRef="number"/>
				  <literalExpression><text>1 + 2</text></literalExpression>
				</decision>
				"""));

		DmnCompilationResult convenient = compiler.compile(root);
		DmnCompilationResult explicit = compiler.compile(root, new InMemoryDmnModelResolver(List.of()));

		assertThat(explicit).isEqualTo(convenient);
		assertThat(convenient.isSuccess()).isTrue();
		assertThat(convenient.optimizedRuntimeModel()).isPresent();
		assertThat(convenient.optimizedRuntimeModel().orElseThrow().model().decisions()).hasSize(1);
		assertThat(convenient.rootId()).isEqualTo(root.id());
		assertThat(convenient.loadedModels().rootId()).isEqualTo(root.id());
		assertThat(convenient.semanticResult().rootId()).isEqualTo(root.id());
		assertThatThrownBy(() -> convenient.diagnostics().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void compilesImportedModelsIntoOneLinkedRuntimeModel() {
		DmnSource root = source("root.dmn",
				model("urn:root", "Root", "<import namespace=\"urn:base\" locationURI=\"base.dmn\"/>"));
		DmnSource base = source("base.dmn", model("urn:base", "Base", """
				<decision id="base-decision" name="Base decision">
				  <variable id="base-variable" name="Base decision" typeRef="number"/>
				  <literalExpression><text>42</text></literalExpression>
				</decision>
				"""));

		DmnCompilationResult result = compiler.compile(root, new InMemoryDmnModelResolver(List.of(base)));

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.loadedModels().models()).hasSize(2);
		assertThat(result.semanticResult().models()).hasSize(2);
		assertThat(result.optimizedRuntimeModel().orElseThrow().model().decisions()).hasSize(1);
	}

	@Test
	void resolverRegistrationOrderDoesNotChangeCompilationResult() {
		DmnSource root = source("root.dmn",
				model("urn:root", "Root", "<import namespace=\"urn:a\" locationURI=\"a.dmn\"/>"
						+ "<import namespace=\"urn:b\" locationURI=\"b.dmn\"/>"));
		DmnSource first = source("a.dmn", model("urn:a", "A", ""));
		DmnSource second = source("b.dmn", model("urn:b", "B", ""));
		List<DmnSource> forward = List.of(first, second);
		List<DmnSource> reverse = new ArrayList<>(forward);
		Collections.reverse(reverse);

		DmnCompilationResult firstResult = compiler.compile(root, new InMemoryDmnModelResolver(forward));
		DmnCompilationResult secondResult = compiler.compile(root, new InMemoryDmnModelResolver(reverse));

		assertThat(secondResult).isEqualTo(firstResult);
	}

	@Test
	void loadAndFeelErrorsStopRuntimeIrCreation() {
		DmnSource missingImport = source("missing-root.dmn", model("urn:missing-root", "Missing root",
				"<import namespace=\"urn:missing\" locationURI=\"missing.dmn\"/>"));
		DmnCompilationResult loadFailure = compiler.compile(missingImport);

		assertThat(loadFailure.optimizedRuntimeModel()).isEmpty();
		assertThat(loadFailure.diagnostics()).extracting(DmnCompilerDiagnostic::code)
				.containsExactly(DmnDiagnosticCodes.IMPORT_MISSING);

		DmnSource invalidFeel = source("feel-root.dmn", model("urn:feel-root", "FEEL root", """
				<decision id="invalid" name="Invalid">
				  <variable id="invalid-variable" name="Invalid" typeRef="number"/>
				  <literalExpression><text>1 +</text></literalExpression>
				</decision>
				"""));
		DmnCompilationResult feelFailure = compiler.compile(invalidFeel);

		assertThat(feelFailure.optimizedRuntimeModel()).isEmpty();
		assertThat(feelFailure.diagnostics()).isNotEmpty()
				.allSatisfy(diagnostic -> assertThat(diagnostic.phase()).isEqualTo(DmnCompilerPhase.FEEL_PARSING));
	}

	@Test
	void convertsExpectedLoweringFailureIntoCompilerDiagnostic() {
		DmnSource root = source("root.dmn", model("urn:root", "Root", """
				<decision id="decision" name="Decision">
				  <variable id="variable" name="Decision" typeRef="number"/>
				  <invocation/>
				</decision>
				"""));

		DmnCompilationResult result = compiler.compile(root);

		assertThat(result.optimizedRuntimeModel()).isEmpty();
		assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
			assertThat(diagnostic.code()).isEqualTo(DmnDiagnosticCodes.RUNTIME_IR_LOWERING);
			assertThat(diagnostic.phase()).isEqualTo(DmnCompilerPhase.RUNTIME_IR_LOWERING);
			assertThat(diagnostic.origin().sourceId()).isEqualTo(root.id());
		});
	}

	@Test
	void passesCompilerLoadLimitsToTheLoader() {
		DmnSource root = source("root.dmn",
				model("urn:root", "Root", "<import namespace=\"urn:base\" locationURI=\"base.dmn\"/>"));
		DmnSource base = source("base.dmn", model("urn:base", "Base", ""));
		DmnCompilerOptions options = new DmnCompilerOptions(new DmnModelLoadOptions(1, 10));

		assertThatThrownBy(() -> compiler.compile(root, new InMemoryDmnModelResolver(List.of(base)), options))
				.isInstanceOf(DmnModelLoadException.class).hasMessage("DMN model graph exceeds maxSources=1");
	}

	private static DmnSource source(String fileName, String xml) {
		return new DmnSource(DmnSourceId.of("memory:/models/" + fileName), xml.getBytes(StandardCharsets.UTF_8));
	}

	private static String model(String namespace, String name, String content) {
		return "<definitions xmlns=\"https://www.omg.org/spec/DMN/20230324/MODEL/\"" + " id=\"" + name + "-id\" name=\""
				+ name + "\" namespace=\"" + namespace + "\">" + content + "</definitions>";
	}
}

package io.finmsg.dmn.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DmnMultiFileCorpusTest {

  private final DmnCompiler compiler = new DmnCompiler();
  private final DmnRuntime runtime = new DmnRuntime();
  private final Path corpusRoot = Paths.get("src/test/resources/corpus");

  @Test
  void p2_1_singleImport() throws IOException {
    Path scenarioDir = corpusRoot.resolve("p2-01-single-import");
    Path rootFile = scenarioDir.resolve("root.dmn");
    DmnSource rootSource = readSource(rootFile);
    DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

    DmnCompilationResult compilation = compiler.compile(rootSource, resolver);

    assertThat(compilation.isSuccess()).isTrue();
    assertThat(compilation.loadedModels().models()).hasSize(2);

    RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
    DmnEvaluationResult evaluation = runtime.evaluate(optimized.model(), Map.of());

    assertThat(evaluation.slotValues()).isNotEmpty();
  }

  @Test
  void p2_2_threeLevelTransitiveImport() throws IOException {
    Path scenarioDir = corpusRoot.resolve("p2-02-transitive");
    Path rootFile = scenarioDir.resolve("level-a.dmn");
    DmnSource rootSource = readSource(rootFile);
    DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

    DmnCompilationResult compilation = compiler.compile(rootSource, resolver);

    assertThat(compilation.isSuccess()).isTrue();
    assertThat(compilation.loadedModels().models()).hasSize(3);

    RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
    DmnEvaluationResult evaluation = runtime.evaluate(optimized.model(), Map.of());

    assertThat(evaluation.slotValues()).isNotEmpty();
  }

  @Test
  void p2_3_diamondImport() throws IOException {
    Path scenarioDir = corpusRoot.resolve("p2-03-diamond");
    Path rootFile = scenarioDir.resolve("top.dmn");
    DmnSource rootSource = readSource(rootFile);
    DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

    DmnCompilationResult compilation = compiler.compile(rootSource, resolver);

    assertThat(compilation.isSuccess()).isTrue();
    assertThat(compilation.loadedModels().models()).hasSize(4);

    RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
    DmnEvaluationResult evaluation = runtime.evaluate(optimized.model(), Map.of());

    assertThat(evaluation.slotValues()).isNotEmpty();
  }

  @Test
  void p2_4_importedBkmInvocation() throws IOException {
    Path scenarioDir = corpusRoot.resolve("p2-04-imported-bkm");
    Path rootFile = scenarioDir.resolve("caller.dmn");
    DmnSource rootSource = readSource(rootFile);
    DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

    DmnCompilationResult compilation = compiler.compile(rootSource, resolver);

    assertThat(compilation.diagnostics())
        .withFailMessage("Compilation failed with diagnostics: %s", compilation.diagnostics())
        .isEmpty();
    assertThat(compilation.isSuccess()).isTrue();
    assertThat(compilation.loadedModels().models()).hasSize(2);

    RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
    DmnEvaluationResult evaluation = runtime.evaluate(optimized.model(), Map.of());

    assertThat(evaluation.slotValues()).isNotEmpty();
  }

  @Test
  void p2_5_importedItemDefinition() throws IOException {
    Path scenarioDir = corpusRoot.resolve("p2-05-imported-itemdef");
    Path rootFile = scenarioDir.resolve("main.dmn");
    DmnSource rootSource = readSource(rootFile);
    DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

    DmnCompilationResult compilation = compiler.compile(rootSource, resolver);

    assertThat(compilation.isSuccess()).isTrue();
    assertThat(compilation.loadedModels().models()).hasSize(2);
  }

  @Test
  void p2_6_sameModelNameInDistinctNamespaces() throws IOException {
    Path scenarioDir = corpusRoot.resolve("p2-06-same-name-diff-ns");
    Path rootFile = scenarioDir.resolve("main.dmn");
    DmnSource rootSource = readSource(rootFile);
    DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

    DmnCompilationResult compilation = compiler.compile(rootSource, resolver);

    assertThat(compilation.isSuccess()).isTrue();
    assertThat(compilation.loadedModels().models()).hasSize(3);

    RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
    DmnEvaluationResult evaluation = runtime.evaluate(optimized.model(), Map.of());

    assertThat(evaluation.slotValues()).isNotEmpty();
  }

  @Test
  void p2_7_missingImportDiagnostic() throws IOException {
    Path scenarioDir = corpusRoot.resolve("p2-07-invalid-imports");
    Path rootFile = scenarioDir.resolve("missing-import.dmn");
    DmnSource rootSource = readSource(rootFile);
    DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

    DmnCompilationResult compilation = compiler.compile(rootSource, resolver);

    assertThat(compilation.isSuccess()).isFalse();
    assertThat(compilation.diagnostics())
        .extracting(DmnCompilerDiagnostic::code)
        .contains(DmnDiagnosticCodes.IMPORT_MISSING);
  }

  @Test
  void p2_8_crossModelDependencyCycle() throws IOException {
    Path scenarioDir = corpusRoot.resolve("p2-08-cycle");
    Path rootFile = scenarioDir.resolve("cycle-a.dmn");
    DmnSource rootSource = readSource(rootFile);
    DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

    DmnCompilationResult compilation = compiler.compile(rootSource, resolver);

    assertThat(compilation.isSuccess()).isFalse();
    assertThat(compilation.diagnostics())
        .extracting(DmnCompilerDiagnostic::code)
        .contains(DmnDiagnosticCodes.IMPORT_CYCLE);
  }

  @Test
  void p2_9_lendingEligibilityRealBusinessModel() throws IOException {
    Path scenarioDir = corpusRoot.resolve("p2-09-lending-eligibility");
    Path rootFile = scenarioDir.resolve("credit-application.dmn");
    DmnSource rootSource = readSource(rootFile);
    DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

    DmnCompilationResult compilation = compiler.compile(rootSource, resolver);

    assertThat(compilation.diagnostics())
        .withFailMessage("Compilation failed with diagnostics: %s", compilation.diagnostics())
        .isEmpty();
    assertThat(compilation.isSuccess()).isTrue();
    assertThat(compilation.loadedModels().models()).hasSize(3);

    RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();
    assertThat(optimized.model().decisions()).hasSize(3);
  }

  private static DmnSource readSource(Path path) throws IOException {
    return new DmnSource(new DmnSourceId(path.toAbsolutePath().toUri()), Files.readAllBytes(path));
  }
}

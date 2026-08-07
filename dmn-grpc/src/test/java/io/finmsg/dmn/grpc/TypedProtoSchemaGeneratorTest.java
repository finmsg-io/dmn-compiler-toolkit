package io.finmsg.dmn.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.ir.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TypedProtoSchemaGeneratorTest {

  private final DmnCompiler compiler = new DmnCompiler();
  private final DmnTypedGrpcGenerator generator = new DmnTypedGrpcGenerator();

  @Test
  void generatesTypedProtoSchemaFromDmnModel() throws Exception {
    Path scenarioDir = Path.of("../dmn-compiler/src/test/resources/corpus/p2-09-lending-eligibility");
    Path rootFile = scenarioDir.resolve("credit-application.dmn");
    DmnSource rootSource = new DmnSource(DmnSourceId.of(rootFile.toUri().toString()), Files.readAllBytes(rootFile));
    DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

    DmnCompilationResult compilation = compiler.compile(rootSource, resolver);
    assertThat(compilation.isSuccess()).isTrue();

    RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();

    TypedGrpcGeneratorOptions options = TypedGrpcGeneratorOptions.defaults();
    TypedGrpcGeneratorResult result = generator.generate(optimized, options);

    assertThat(result.protoFileName()).endsWith(".proto");
    assertThat(result.protoContent()).contains("syntax = \"proto3\";");
    assertThat(result.protoContent()).contains("package finmsg.dmn.typed;");
    assertThat(result.protoContent()).contains("message InputData");
    assertThat(result.protoContent()).contains("service DmnTypedDecisionService");
  }
}

package io.finmsg.dmn.models.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiledModel;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnModelLoadResult;
import io.finmsg.dmn.compiler.DmnModelLoader;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.model.EvaluationResponse;
import io.finmsg.dmn.model.Value;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class DmnStreamBundleTest {

  private final DmnCompiler compiler = new DmnCompiler();
  private final DmnModelLoader loader = new DmnModelLoader();

  @Test
  void streamsFromClasspathMultiFileDmnDirectory() {
    DmnStreamBundle bundle = DmnStreamBundle.fromClasspath("models/loan-approval");

    assertThat(bundle.sources()).hasSize(3);
    assertThat(bundle.sources().keySet()).containsExactlyInAnyOrder(
        "main-loan.dmn", "credit-score.dmn", "applicant-risk.dmn");

    DmnSource root = bundle.findRootSource().orElseThrow();
    assertThat(root.id().toString()).contains("main-loan.dmn");

    DmnCompilationResult result = bundle.compile(compiler);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.loadedModels().models()).hasSize(3);

    DmnCompiledModel compiledModel = result.compiledModel().orElseThrow();
    EvaluationResponse response = compiledModel.evaluateInputs(Map.of());
    assertThat(response.getDecisionResultsMap()).containsKey("LoanApprovalStatus");
    Value value = response.getDecisionResultsMap().get("LoanApprovalStatus");
    assertThat(value.getStringValue()).isEqualTo("APPROVED");
  }

  @Test
  void streamsOrderFulfillmentMultiFileDmnFromClasspath() {
    DmnStreamBundle bundle = DmnStreamBundle.fromClasspath("models/order-fulfillment");

    assertThat(bundle.sources()).hasSize(3);

    DmnCompilationResult result = bundle.compile(compiler);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.loadedModels().models()).hasSize(3);

    DmnCompiledModel compiledModel = result.compiledModel().orElseThrow();
    EvaluationResponse response = compiledModel.evaluateInputs(Map.of());
    assertThat(response.getDecisionResultsMap()).containsKey("CanFulfillOrder");
    Value value = response.getDecisionResultsMap().get("CanFulfillOrder");
    assertThat(value.getBooleanValue()).isTrue();
  }

  @Test
  void streamsDiscountCalculationMultiFileDmnFromClasspath() {
    DmnStreamBundle bundle = DmnStreamBundle.fromClasspath("models/discount-calculation");

    assertThat(bundle.sources()).hasSize(2);

    DmnCompilationResult result = bundle.compile(compiler);
    assertThat(result.isSuccess()).isTrue();

    DmnCompiledModel compiledModel = result.compiledModel().orElseThrow();
    EvaluationResponse response = compiledModel.evaluateInputs(Map.of());
    assertThat(response.getDecisionResultsMap()).containsKey("FinalDiscountRate");
    Value value = response.getDecisionResultsMap().get("FinalDiscountRate");
    assertThat(value.getNumberValue()).isEqualTo("0.20");
  }

  @Test
  void streamsOriginationsMultiFileDmnFromClasspath() {
    DmnStreamBundle bundle = DmnStreamBundle.fromClasspath("models/originations");

    assertThat(bundle.sources()).hasSize(2);
    assertThat(bundle.sources().keySet()).containsExactlyInAnyOrder(
        "Chapter 12 Example.dmn", "Financial.dmn");

    DmnSource root = bundle.findRootSource().orElseThrow();
    assertThat(root.id().toString()).contains("Chapter");

    DmnCompilationResult result = bundle.compile(compiler);
    assertThat(result.loadedModels().models()).hasSize(2);
    assertThat(result.semanticResult().models()).hasSize(2);
  }

  @Test
  void streamsRankedLoanProductsMultiFileDmnFromClasspath() {
    DmnStreamBundle bundle = DmnStreamBundle.fromClasspath("models/ranked-loan-products");

    assertThat(bundle.sources()).hasSize(2);
    assertThat(bundle.sources().keySet()).containsExactlyInAnyOrder(
        "Loan info.dmn", "Recommended Loan Products.dmn");

    DmnSource root = bundle.findRootSource().orElseThrow();
    assertThat(root.id().toString()).contains("Recommended");

    DmnCompilationResult result = bundle.compile(compiler);
    assertThat(result.loadedModels().models()).hasSize(2);
    assertThat(result.semanticResult().models()).hasSize(2);
  }

  @Test
  void streamsFromZipArchiveWithoutUnpackingToDisk() throws IOException {
    byte[] zipBytes = createTestZipArchive();

    DmnStreamBundle bundle = DmnStreamBundle.fromZip(zipBytes);

    assertThat(bundle.sources()).hasSize(2);
    assertThat(bundle.sources().keySet()).containsExactlyInAnyOrder("root.dmn", "base.dmn");

    DmnModelLoadResult loadResult = bundle.load(loader, "root.dmn");
    assertThat(loadResult.models()).hasSize(2);

    DmnCompilationResult compResult = bundle.compile(compiler, "root.dmn");
    assertThat(compResult.isSuccess()).isTrue();

    DmnCompiledModel compiledModel = compResult.compiledModel().orElseThrow();
    EvaluationResponse response = compiledModel.evaluateInputs(Map.of());
    assertThat(response.getDecisionResultsMap()).containsKey("RootDecision");
    Value value = response.getDecisionResultsMap().get("RootDecision");
    assertThat(value.getStringValue()).isEqualTo("APPROVED");
  }

  @Test
  void streamsFromMapOfInputStreams() {
    String baseXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" namespace="urn:base" id="base-id" name="Base">
          <decision id="baseDec" name="BaseDec">
            <variable id="v1" name="BaseDec" typeRef="number"/>
            <literalExpression><text>100</text></literalExpression>
          </decision>
        </definitions>
        """;

    String rootXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" namespace="urn:root" id="root-id" name="Root">
          <import id="imp" name="BaseMod" namespace="urn:base" locationURI="base.dmn" importType="https://www.omg.org/spec/DMN/20230324/MODEL/"/>
          <decision id="rootDec" name="RootDec">
            <variable id="v2" name="RootDec" typeRef="number"/>
            <literalExpression><text>150</text></literalExpression>
          </decision>
        </definitions>
        """;

    Map<String, InputStream> streams = Map.of(
        "root.dmn", new ByteArrayInputStream(rootXml.getBytes(StandardCharsets.UTF_8)),
        "base.dmn", new ByteArrayInputStream(baseXml.getBytes(StandardCharsets.UTF_8))
    );

    DmnStreamBundle bundle = DmnStreamBundle.fromStreams(streams);
    DmnCompilationResult result = bundle.compile(compiler);
    assertThat(result.isSuccess()).isTrue();

    DmnCompiledModel compiledModel = result.compiledModel().orElseThrow();
    EvaluationResponse response = compiledModel.evaluateInputs(Map.of());
    assertThat(response.getDecisionResultsMap()).containsKey("RootDec");
    Value value = response.getDecisionResultsMap().get("RootDec");
    assertThat(value.getNumberValue()).isEqualTo("150");
  }

  private static byte[] createTestZipArchive() throws IOException {
    String baseDmn = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" namespace="urn:base" id="b1" name="Base">
          <decision id="d1" name="BaseValue">
            <variable id="v1" name="BaseValue" typeRef="number"/>
            <literalExpression><text>42</text></literalExpression>
          </decision>
        </definitions>
        """;

    String rootDmn = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" namespace="urn:root" id="r1" name="Root">
          <import id="i1" name="BaseMod" namespace="urn:base" locationURI="base.dmn" importType="https://www.omg.org/spec/DMN/20230324/MODEL/"/>
          <decision id="d2" name="RootDecision">
            <variable id="v2" name="RootDecision" typeRef="string"/>
            <literalExpression><text>"APPROVED"</text></literalExpression>
          </decision>
        </definitions>
        """;

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      zos.putNextEntry(new ZipEntry("base.dmn"));
      zos.write(baseDmn.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("root.dmn"));
      zos.write(rootDmn.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return baos.toByteArray();
  }
}

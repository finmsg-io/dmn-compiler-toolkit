package io.finmsg.dmn.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.finmsg.dmn.feel.parser.DmnFeelParser;
import io.finmsg.dmn.frontend.xml.dmn.DmnXmlReader;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.semantic.analysis.DmnSemanticPipeline;
import io.finmsg.dmn.semantic.analysis.DmnSemanticPipelineResult;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RuntimeIrPipelineIntegrationTest {

  @Test
  void linksTwoXmlModelsIntoOneNamespaceFreeRuntimeModel() {
    String baseXml = """
        <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/"
            id="base" name="Base" namespace="urn:base">
          <itemDefinition id="applicant-type" name="Applicant">
            <itemComponent id="score-field" name="score" typeRef="number"/>
          </itemDefinition>
          <inputData id="external-input" name="ExternalInput">
            <variable id="external-variable" name="ExternalInput" typeRef="Applicant"/>
          </inputData>
        </definitions>
        """;
    String rootXml = """
        <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/"
            xmlns:base="urn:base" id="root" name="Root" namespace="urn:root">
          <import id="base-import" name="base" namespace="urn:base" importType="DMN"/>
          <decision id="root-decision" name="Result">
            <variable id="root-variable" name="Result" typeRef="number"/>
            <informationRequirement>
              <requiredInput href="urn:base#external-input"/>
            </informationRequirement>
            <literalExpression><text>ExternalInput.score</text></literalExpression>
          </decision>
        </definitions>
        """;

    DmnXmlReader reader = new DmnXmlReader();
    DmnFeelParser parser = new DmnFeelParser();
    Definitions base = parser.parse(reader.read(baseXml.getBytes(StandardCharsets.UTF_8)));
    Definitions root = parser.parse(reader.read(rootXml.getBytes(StandardCharsets.UTF_8)));
    DmnSemanticPipeline pipeline = new DmnSemanticPipeline();
    DmnSemanticPipelineResult baseAnalysis = pipeline.analyze(base);
    DmnSemanticPipelineResult rootAnalysis = pipeline.analyze(root, java.util.List.of(base));
    assertThat(baseAnalysis.diagnostics()).isEmpty();
    assertThat(rootAnalysis.diagnostics()).isEmpty();

    RuntimeModel runtime = new RuntimeIrLowerer().lowerModelSet(
        java.util.List.of(baseAnalysis, rootAnalysis));

    assertThat(runtime.valueSlotCount()).isEqualTo(2);
    assertThat(runtime.inputs()).singleElement().satisfies(input -> {
      assertThat(input.id()).isZero();
      assertThat(input.type().fieldLayout()).singleElement().satisfies(field -> {
        assertThat(field.index()).isZero();
        assertThat(field.name()).isEqualTo("score");
      });
    });
    assertThat(runtime.decisions()).singleElement().satisfies(decision -> {
      assertThat(decision.id()).isEqualTo(1);
      assertThat(decision.dependencies()).containsExactly(0);
      RuntimePathExpression path =
          (RuntimePathExpression) decision.expression().orElseThrow();
      assertThat(path.fieldIndex()).isZero();
      assertThat(path.source()).isEqualTo(new RuntimeValueReference(
          0, runtime.inputs().getFirst().type()));
    });
    assertThat(runtime.evaluationOrder()).containsExactly(1);
  }

  @Test
  void lowersExecutableBkmBodyFromXmlThroughEveryCompilerPhase() {
    String xml = """
        <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/"
            namespace="https://example.com/functions">
          <businessKnowledgeModel id="bkm-1" name="Double">
            <variable id="result-1" name="Double" typeRef="number"/>
            <encapsulatedLogic>
              <functionDefinition kind="FEEL">
                <formalParameter id="parameter-1" name="value" typeRef="number"/>
                <literalExpression><text>value * 2</text></literalExpression>
              </functionDefinition>
            </encapsulatedLogic>
          </businessKnowledgeModel>
        </definitions>
        """;

    Definitions xmlModel = new DmnXmlReader().read(xml.getBytes(StandardCharsets.UTF_8));
    Definitions feelModel = new DmnFeelParser().parse(xmlModel);
    DmnSemanticPipelineResult semantic = new DmnSemanticPipeline().analyze(feelModel);
    assertThat(semantic.diagnostics()).isEmpty();

    RuntimeModel runtime = new RuntimeIrLowerer().lower(semantic);

    assertThat(runtime.businessKnowledgeModels()).singleElement().satisfies(bkm -> {
      assertThat(bkm.functionKind()).isEqualTo(RuntimeFunctionKind.FEEL);
      RuntimeFunctionDefinition function = bkm.function().orElseThrow();
      assertThat(function.parameters()).singleElement().satisfies(parameter -> {
        assertThat(parameter.name()).isEqualTo("value");
        assertThat(parameter.localSlot()).isZero();
      });
      assertThat(function.localSlotCount()).isEqualTo(1);
      RuntimeBinaryExpression body =
          (RuntimeBinaryExpression) function.body().orElseThrow();
      assertThat(body.left()).isEqualTo(new RuntimeLocalReference(
          0, RuntimeType.scalar(RuntimeTypeKind.NUMBER)));
      assertThat(body.right()).isInstanceOf(RuntimeConstant.class);
    });
  }

  @Test
  void lowersTrafficViolationFromXmlThroughEveryCompilerPhase() {
    Definitions xmlModel;
    try (InputStream input = getClass().getResourceAsStream("/TrafficViolation.dmn")) {
      assertThat(input).as("TrafficViolation.dmn test resource").isNotNull();
      xmlModel = new DmnXmlReader().read(input);
    } catch (Exception exception) {
      throw new AssertionError("Could not read TrafficViolation.dmn", exception);
    }

    Definitions feelModel = new DmnFeelParser().parse(xmlModel);
    DmnSemanticPipelineResult semantic = new DmnSemanticPipeline().analyze(feelModel);
    assertThat(semantic.diagnostics()).isEmpty();

    RuntimeModel runtime = new RuntimeIrLowerer().lower(semantic);

    assertThat(runtime.inputs()).hasSize(2);
    assertThat(runtime.decisions()).hasSize(2);
    assertThat(runtime.businessKnowledgeModels()).isEmpty();
    assertThat(runtime.valueSlotCount()).isEqualTo(4);
    assertThat(runtime.evaluationOrder()).containsExactly(1, 3);

    RuntimeInput violation = runtime.inputs().getFirst();
    assertThat(violation.type().fieldLayout())
        .extracting(RuntimeField::index, RuntimeField::name)
        .containsExactly(
            tuple(0, "Code"), tuple(1, "Date"), tuple(2, "Type"),
            tuple(3, "SpeedLimit"), tuple(4, "ActualSpeed"));

    RuntimeDecision fine = runtime.decisions().getFirst();
    assertThat(fine.dependencies()).containsExactly(0);
    RuntimeDecisionTable table = fine.decisionTable().orElseThrow();
    assertThat(table.inputs()).hasSize(2);
    assertThat(table.outputs()).hasSize(2);
    assertThat(table.rules()).hasSize(4);
    RuntimePathExpression violationType =
        (RuntimePathExpression) table.inputs().getFirst().expression();
    assertThat(violationType.member()).isEqualTo("Type");
    assertThat(violationType.fieldIndex()).isEqualTo(2);
    RuntimeBinaryExpression speedDifference =
        (RuntimeBinaryExpression) table.inputs().get(1).expression();
    RuntimePathExpression actualSpeed = (RuntimePathExpression) speedDifference.left();
    RuntimePathExpression speedLimit = (RuntimePathExpression) speedDifference.right();
    assertThat(actualSpeed.member()).isEqualTo("ActualSpeed");
    assertThat(actualSpeed.fieldIndex()).isEqualTo(4);
    assertThat(speedLimit.member()).isEqualTo("SpeedLimit");
    assertThat(speedLimit.fieldIndex()).isEqualTo(3);

    RuntimeDecision suspension = runtime.decisions().get(1);
    assertThat(suspension.dependencies()).containsExactly(2, 1);
    RuntimeContextExpression context =
        (RuntimeContextExpression) suspension.expression().orElseThrow();
    assertThat(context.entries()).extracting(RuntimeContextEntry::name)
        .containsExactly("TotalPoints", "");
    assertThat(context.entries()).extracting(RuntimeContextEntry::localSlot)
        .containsExactly(0, -1);
    RuntimeBinaryExpression totalPoints =
        (RuntimeBinaryExpression) context.entries().getFirst().expression();
    assertThat(((RuntimePathExpression) totalPoints.left()).fieldIndex()).isEqualTo(4);
    assertThat(((RuntimePathExpression) totalPoints.right()).fieldIndex()).isEqualTo(1);
    assertThat(context.entries().get(1).expression())
        .isInstanceOf(RuntimeConditionalExpression.class);
  }
}

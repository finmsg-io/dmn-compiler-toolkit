package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.DmnFeelParser;
import io.finmsg.dmn.feel.parser.FeelParserFacade;
import io.finmsg.dmn.frontend.xml.dmn.DmnXmlReader;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.ElementReference;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.InformationRequirement;
import io.finmsg.dmn.model.Node;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class DmnSemanticPipelineTest {

  private final DmnSemanticPipeline pipeline = new DmnSemanticPipeline();
  private final FeelParserFacade feelParser = new FeelParserFacade();

  @Test
  void analyzesTrafficViolationEndToEnd() {
    Definitions semanticModel;
    try (InputStream input = getClass().getResourceAsStream("/TrafficViolation.dmn")) {
      assertThat(input).isNotNull();
      semanticModel = new DmnXmlReader().read(input);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
    Definitions parsedModel = new DmnFeelParser().parse(semanticModel);

    DmnSemanticPipelineResult result = pipeline.analyze(parsedModel);

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.model()).isNotEqualTo(parsedModel);
    assertThat(result.compilationOrder()).extracting(DmnSemanticPipelineTest::name)
        .containsExactly("Fine", "Should the driver be suspended?");
    Decision fine = decision(result.model(), "Fine");
    assertThat(fine.getLogic().getDecisionTable().getInputs(1)
        .getInputExpression().getParsed().getAst().getInferredType().getBuiltin())
        .isEqualTo(BuiltinType.BUILTIN_TYPE_NUMBER);
  }

  @Test
  void combinesDependencyDiagnosticsAndSuppressesCyclicOrder() {
    Decision first = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("a").setName("A"))
        .addInformationRequirements(requirement("b"))
        .addInformationRequirements(requirement("b"))
        .build();
    Decision second = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("b").setName("B"))
        .addInformationRequirements(requirement("a"))
        .build();
    Definitions model = Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setDecision(first))
        .addDrgElements(DrgElement.newBuilder().setDecision(second))
        .build();

    DmnSemanticPipelineResult result = pipeline.analyze(model);

    assertThat(result.compilationOrder()).isEmpty();
    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("DUPLICATE_REQUIREMENT", "CYCLIC_DEPENDENCY");
  }

  @Test
  void removesDuplicateDiagnosticsReportedByMultiplePasses() {
    Decision decision = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("decision").setName("Decision"))
        .setLogic(DecisionLogic.newBuilder().setLiteralExpression(
            Feel.newBuilder().setParsed(feelParser.parseExpressionAst("Missing"))))
        .build();
    Definitions model = Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setDecision(decision))
        .build();

    assertThat(pipeline.analyze(model).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("UNKNOWN_NAME");
  }

  private static InformationRequirement requirement(String id) {
    return InformationRequirement.newBuilder().setDecision(
        ElementReference.newBuilder().setHref("#" + id)).build();
  }

  private static Decision decision(Definitions definitions, String name) {
    return definitions.getDrgElementsList().stream()
        .filter(DrgElement::hasDecision)
        .map(DrgElement::getDecision)
        .filter(decision -> decision.getNode().getName().equals(name))
        .findFirst().orElseThrow();
  }

  private static String name(DrgElement element) {
    return element.hasDecision()
        ? element.getDecision().getNode().getName()
        : element.getBusinessKnowledgeModel().getNode().getName();
  }
}

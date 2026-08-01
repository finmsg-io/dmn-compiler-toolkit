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
import io.finmsg.dmn.model.InformationItem;
import io.finmsg.dmn.model.InputData;
import io.finmsg.dmn.model.Import;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.NamedTypeReference;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.TypeReference;
import java.io.InputStream;
import java.util.List;
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

  @Test
  void linksImportedReferencesNamesAndTypesByNamespace() {
    TypeReference applicantType = TypeReference.newBuilder().setNamed(
        NamedTypeReference.newBuilder().setName("Applicant").setNamespace("urn:base"))
        .build();
    Definitions imported = Definitions.newBuilder()
        .setNamespace("urn:base")
        .addItemDefinitions(ItemDefinition.newBuilder()
            .setNode(Node.newBuilder().setId("applicant-type").setName("Applicant")))
        .addDrgElements(DrgElement.newBuilder().setInputData(InputData.newBuilder()
            .setNode(Node.newBuilder().setId("external-input").setName("ExternalInput"))
            .setVariable(InformationItem.newBuilder().setType(applicantType))))
        .build();
    Decision decision = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("decision").setName("Decision"))
        .setVariable(InformationItem.newBuilder().setType(applicantType))
        .addInformationRequirements(InformationRequirement.newBuilder().setInput(
            ElementReference.newBuilder().setHref("urn:base#external-input")))
        .setLogic(DecisionLogic.newBuilder().setLiteralExpression(
            Feel.newBuilder().setParsed(feelParser.parseExpressionAst("ExternalInput"))))
        .build();
    Definitions root = Definitions.newBuilder()
        .setNamespace("urn:root")
        .addImports(Import.newBuilder().setNamespace("urn:base").setName("base"))
        .addDrgElements(DrgElement.newBuilder().setDecision(decision))
        .build();

    DmnSemanticPipelineResult result = pipeline.analyze(root, List.of(imported));

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.bindings()).filteredOn(binding ->
        binding.targetNamespace().equals("urn:base"))
        .extracting(DmnSymbolBinding::symbolId)
        .contains("applicant-type", "external-input");
  }

  @Test
  void reportsUnavailableImportedNamespace() {
    Definitions root = Definitions.newBuilder()
        .setNamespace("urn:root")
        .addImports(Import.newBuilder().setNamespace("urn:missing"))
        .build();

    assertThat(pipeline.analyze(root, List.of()).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("UNKNOWN_IMPORT");
  }

  @Test
  void ordersDependenciesAcrossImportedModels() {
    Decision baseDecision = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("base-decision").setName("Base"))
        .build();
    Definitions imported = Definitions.newBuilder()
        .setNamespace("urn:base")
        .addDrgElements(DrgElement.newBuilder().setDecision(baseDecision))
        .build();
    Decision rootDecision = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("root-decision").setName("Root"))
        .addInformationRequirements(InformationRequirement.newBuilder().setDecision(
            ElementReference.newBuilder().setHref("urn:base#base-decision")))
        .build();
    Definitions root = Definitions.newBuilder()
        .setNamespace("urn:root")
        .addImports(Import.newBuilder().setNamespace("urn:base"))
        .addDrgElements(DrgElement.newBuilder().setDecision(rootDecision))
        .build();

    DmnSemanticPipelineResult result = pipeline.analyze(root, List.of(imported));

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.compilationOrder()).extracting(DmnSemanticPipelineTest::name)
        .containsExactly("Base", "Root");
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

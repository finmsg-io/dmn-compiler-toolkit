package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.FeelParserFacade;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.ElementReference;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.InformationItem;
import io.finmsg.dmn.model.InformationRequirement;
import io.finmsg.dmn.model.InputData;
import io.finmsg.dmn.model.ItemComponent;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.NamedTypeReference;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.TypeReference;
import org.junit.jupiter.api.Test;

class DmnSemanticAnalyzerTest {

  private final FeelParserFacade feelParser = new FeelParserFacade();
  private final DmnSemanticAnalyzer analyzer = new DmnSemanticAnalyzer();

  @Test
  void reportsInvalidUnavailableAndUnknownNames() {
    ItemDefinition inputType = ItemDefinition.newBuilder()
        .setNode(Node.newBuilder().setName("tInput"))
        .addComponents(ItemComponent.newBuilder()
            .setNode(Node.newBuilder().setName("Known")))
        .build();

    InputData input = InputData.newBuilder()
        .setNode(Node.newBuilder().setId("input-id").setName("Input"))
        .setVariable(InformationItem.newBuilder().setType(namedType("tInput")))
        .build();

    Decision hidden = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("hidden-id").setName("Hidden"))
        .build();

    Decision target = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("target-id").setName("Target"))
        .addInformationRequirements(InformationRequirement.newBuilder()
            .setInput(ElementReference.newBuilder().setHref("#input-id")))
        .setLogic(DecisionLogic.newBuilder().setLiteralExpression(parsedFeel(
            "Input.Unknown + Hidden + Missing")))
        .build();

    Definitions model = Definitions.newBuilder()
        .addItemDefinitions(inputType)
        .addDrgElements(DrgElement.newBuilder().setInputData(input))
        .addDrgElements(DrgElement.newBuilder().setDecision(hidden))
        .addDrgElements(DrgElement.newBuilder().setDecision(target))
        .build();

    DmnSemanticAnalysisResult result = analyzer.analyze(model);

    assertThat(result.model()).isSameAs(model);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("INVALID_PROPERTY", "UNAVAILABLE_NAME", "UNKNOWN_NAME");
    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::message)
        .containsExactly(
            "Type 'tInput' has no property 'Unknown'.",
            "Name 'Hidden' exists but is not available through a requirement.",
            "Unknown name 'Missing'.");
  }

  @Test
  void reportsDuplicateGlobalNames() {
    Definitions model = Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setInputData(InputData.newBuilder()
            .setNode(Node.newBuilder().setId("first").setName("Duplicate"))))
        .addDrgElements(DrgElement.newBuilder().setDecision(Decision.newBuilder()
            .setNode(Node.newBuilder().setId("second").setName("Duplicate"))))
        .build();

    assertThat(analyzer.analyze(model).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("DUPLICATE_NAME");
  }

  private Feel parsedFeel(String source) {
    return Feel.newBuilder().setParsed(feelParser.parseExpressionAst(source)).build();
  }

  private static TypeReference namedType(String name) {
    return TypeReference.newBuilder()
        .setNamed(NamedTypeReference.newBuilder().setName(name))
        .build();
  }
}

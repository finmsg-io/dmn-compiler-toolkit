package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.FeelParserFacade;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.DecisionService;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.ElementReference;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.InformationItem;
import io.finmsg.dmn.model.InformationRequirement;
import io.finmsg.dmn.model.InputData;
import io.finmsg.dmn.model.Invocation;
import io.finmsg.dmn.model.ItemComponent;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.Binding;
import io.finmsg.dmn.model.BusinessKnowledgeModel;
import io.finmsg.dmn.model.FunctionDefinition;
import io.finmsg.dmn.model.KnowledgeRequirement;
import io.finmsg.dmn.model.KnowledgeSource;
import io.finmsg.dmn.model.AuthorityRequirement;
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

  @Test
  void validatesItemDefinitionTypeGraph() {
    ItemDefinition invalid = ItemDefinition.newBuilder()
        .setNode(Node.newBuilder().setName("Invalid"))
        .setType(namedType("Missing"))
        .addComponents(ItemComponent.newBuilder()
            .setNode(Node.newBuilder().setName("value"))
            .setType(namedType("number")))
        .addComponents(ItemComponent.newBuilder()
            .setNode(Node.newBuilder().setName("value"))
            .setType(namedType("string")))
        .build();
    ItemDefinition first = ItemDefinition.newBuilder()
        .setNode(Node.newBuilder().setName("First"))
        .setType(namedType("Second"))
        .build();
    ItemDefinition second = ItemDefinition.newBuilder()
        .setNode(Node.newBuilder().setName("Second"))
        .setType(namedType("First"))
        .build();

    DmnSemanticAnalysisResult result = analyzer.analyze(Definitions.newBuilder()
        .addItemDefinitions(invalid)
        .addItemDefinitions(first)
        .addItemDefinitions(second)
        .build());

    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("UNKNOWN_TYPE", "DUPLICATE_COMPONENT", "CYCLIC_TYPE_DEFINITION");
    assertThat(result.diagnostics().get(2).message())
        .isEqualTo("Cyclic item-definition types: First -> Second -> First.");
  }

  @Test
  void reportsDuplicateDrgElementIds() {
    Definitions model = Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setInputData(InputData.newBuilder()
            .setNode(Node.newBuilder().setId("duplicate-id").setName("First"))))
        .addDrgElements(DrgElement.newBuilder().setDecision(Decision.newBuilder()
            .setNode(Node.newBuilder().setId("duplicate-id").setName("Second"))))
        .build();

    assertThat(analyzer.analyze(model).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("DUPLICATE_ID");
  }

  @Test
  void validatesRequirementAndDecisionServiceReferences() {
    InputData input = InputData.newBuilder()
        .setNode(Node.newBuilder().setId("input-id").setName("Input"))
        .build();
    Decision dependency = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("decision-id").setName("Dependency"))
        .build();
    Decision target = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("target-id").setName("Target"))
        .addInformationRequirements(InformationRequirement.newBuilder()
            .setInput(reference("#decision-id")))
        .addKnowledgeRequirements(KnowledgeRequirement.newBuilder()
            .setRequiredKnowledge(reference("#missing-bkm")))
        .addAuthorityRequirements(AuthorityRequirement.newBuilder()
            .setRequiredAuthority(reference("#input-id")))
        .build();
    KnowledgeSource authority = KnowledgeSource.newBuilder()
        .setNode(Node.newBuilder().setId("authority-id").setName("Authority"))
        .build();
    DecisionService service = DecisionService.newBuilder()
        .setNode(Node.newBuilder().setId("service-id").setName("Service"))
        .addOutputDecisions(reference("#input-id"))
        .addInputData(reference("#missing-input"))
        .build();

    Definitions model = Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setInputData(input))
        .addDrgElements(DrgElement.newBuilder().setDecision(dependency))
        .addDrgElements(DrgElement.newBuilder().setDecision(target))
        .addDrgElements(DrgElement.newBuilder().setKnowledgeSource(authority))
        .addDrgElements(DrgElement.newBuilder().setDecisionService(service))
        .build();

    assertThat(analyzer.analyze(model).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly(
            "INVALID_REFERENCE_KIND",
            "UNKNOWN_REFERENCE",
            "INVALID_REFERENCE_KIND",
            "INVALID_REFERENCE_KIND",
            "UNKNOWN_REFERENCE");
  }

  @Test
  void requiresDecisionServiceOutputDecision() {
    DecisionService service = DecisionService.newBuilder()
        .setNode(Node.newBuilder().setName("Empty service"))
        .build();

    assertThat(analyzer.analyze(Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setDecisionService(service))
        .build()).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("MISSING_OUTPUT_DECISION");
  }

  @Test
  void rejectsDuplicateAndConflictingDecisionServiceReferences() {
    Decision decision = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("decision-id").setName("Decision"))
        .build();
    DecisionService service = DecisionService.newBuilder()
        .setNode(Node.newBuilder().setName("Service"))
        .addOutputDecisions(reference("#decision-id"))
        .addOutputDecisions(reference("#decision-id"))
        .addEncapsulatedDecisions(reference("#decision-id"))
        .build();

    DmnSemanticAnalysisResult result = analyzer.analyze(Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setDecision(decision))
        .addDrgElements(DrgElement.newBuilder().setDecisionService(service))
        .build());

    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly(
            "DUPLICATE_DECISION_SERVICE_REFERENCE",
            "CONFLICTING_DECISION_SERVICE_ROLE");
  }

  @Test
  void validatesInvocationBindingsAgainstBkmParameters() {
    BusinessKnowledgeModel bkm = BusinessKnowledgeModel.newBuilder()
        .setNode(Node.newBuilder().setId("bkm-id").setName("Calculator"))
        .setFunction(FunctionDefinition.newBuilder()
            .addFormalParameters(parameter("x"))
            .addFormalParameters(parameter("y")))
        .build();
    Invocation invocation = Invocation.newBuilder()
        .setExpression(parsedFeel("Calculator"))
        .addBindings(binding("x", "1"))
        .addBindings(binding("x", "2"))
        .addBindings(binding("z", "3"))
        .build();
    Decision target = Decision.newBuilder()
        .setNode(Node.newBuilder().setId("target-id").setName("Target"))
        .addKnowledgeRequirements(KnowledgeRequirement.newBuilder()
            .setRequiredKnowledge(reference("#bkm-id")))
        .setLogic(DecisionLogic.newBuilder().setInvocation(invocation))
        .build();
    Definitions model = Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setBusinessKnowledgeModel(bkm))
        .addDrgElements(DrgElement.newBuilder().setDecision(target))
        .build();

    assertThat(analyzer.analyze(model).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly(
            "DUPLICATE_INVOCATION_BINDING",
            "UNKNOWN_INVOCATION_PARAMETER",
            "MISSING_INVOCATION_BINDING");
  }

  private Feel parsedFeel(String source) {
    return Feel.newBuilder().setParsed(feelParser.parseExpressionAst(source)).build();
  }

  private static TypeReference namedType(String name) {
    return TypeReference.newBuilder()
        .setNamed(NamedTypeReference.newBuilder().setName(name))
        .build();
  }

  private static ElementReference reference(String href) {
    return ElementReference.newBuilder().setHref(href).build();
  }

  private static InformationItem parameter(String name) {
    return InformationItem.newBuilder().setNode(Node.newBuilder().setName(name)).build();
  }

  private Binding binding(String parameter, String expression) {
    return Binding.newBuilder()
        .setParameter(parameter)
        .setExpression(parsedFeel(expression))
        .build();
  }
}

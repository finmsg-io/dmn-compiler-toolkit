package io.finmsg.dmn.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.finmsg.dmn.model.*;
import io.finmsg.dmn.semantic.analysis.DmnSemanticDiagnostic;
import io.finmsg.dmn.semantic.analysis.DmnSemanticPipelineResult;
import io.finmsg.dmn.semantic.analysis.DmnSymbolBinding;
import io.finmsg.dmn.semantic.analysis.DmnSymbolKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeIrLowererTest {
  private final RuntimeIrLowerer lowerer = new RuntimeIrLowerer();

  @Test
  void assignsDeterministicIdsSlotsTypesDependenciesAndOrder() {
    ItemDefinition person = ItemDefinition.newBuilder()
        .setNode(Node.newBuilder().setName("Person"))
        .addComponents(ItemComponent.newBuilder()
            .setNode(Node.newBuilder().setName("age"))
            .setType(builtin(BuiltinType.BUILTIN_TYPE_NUMBER)))
        .addComponents(ItemComponent.newBuilder()
            .setNode(Node.newBuilder().setName("tags"))
            .setType(builtin(BuiltinType.BUILTIN_TYPE_STRING)).setIsCollection(true))
        .build();
    DrgElement input = DrgElement.newBuilder().setInputData(InputData.newBuilder()
        .setNode(Node.newBuilder().setId("input").setName("Applicant"))
        .setVariable(InformationItem.newBuilder().setType(TypeReference.newBuilder()
            .setNamed(NamedTypeReference.newBuilder().setName("Person"))))).build();
    DrgElement bkm = DrgElement.newBuilder().setBusinessKnowledgeModel(
        BusinessKnowledgeModel.newBuilder()
            .setNode(Node.newBuilder().setId("bkm").setName("Rule"))
            .setVariable(InformationItem.newBuilder().setType(TypeReference.newBuilder()
                .setFunction(FunctionTypeReference.newBuilder()
                    .addParameterType(builtin(BuiltinType.BUILTIN_TYPE_NUMBER))
                    .setReturnType(builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN)))))).build();
    DrgElement decision = DrgElement.newBuilder().setDecision(Decision.newBuilder()
        .setNode(Node.newBuilder().setId("decision").setName("Result"))
        .setVariable(InformationItem.newBuilder()
            .setType(builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN)))
        .addInformationRequirements(InformationRequirement.newBuilder().setInput(ref("#input")))
        .addKnowledgeRequirements(KnowledgeRequirement.newBuilder()
            .setRequiredKnowledge(ref("#bkm")))).build();
    Definitions model = Definitions.newBuilder().addItemDefinitions(person)
        .addDrgElements(input).addDrgElements(bkm).addDrgElements(decision).build();

    RuntimeModel result = lowerer.lower(
        new DmnSemanticPipelineResult(model, List.of(bkm, decision), List.of()));

    assertThat(result.valueSlotCount()).isEqualTo(3);
    assertThat(result.inputs()).singleElement().satisfies(value -> {
      assertThat(value.id()).isZero();
      assertThat(value.valueSlot()).isZero();
      assertThat(value.type().kind()).isEqualTo(RuntimeTypeKind.CONTEXT);
      assertThat(value.type().fields()).extracting(RuntimeType::kind)
          .containsExactly(RuntimeTypeKind.NUMBER, RuntimeTypeKind.LIST);
    });
    assertThat(result.businessKnowledgeModels()).singleElement()
        .satisfies(value -> assertThat(value.id()).isEqualTo(1));
    assertThat(result.decisions()).singleElement().satisfies(value -> {
      assertThat(value.id()).isEqualTo(2);
      assertThat(value.dependencies()).containsExactly(0, 1);
    });
    assertThat(result.evaluationOrder()).containsExactly(1, 2);
  }

  @Test
  void rejectsSemanticResultsWithDiagnostics() {
    DmnSemanticDiagnostic diagnostic = new DmnSemanticDiagnostic(
        "UNKNOWN_NAME", "definitions/decision[Test]", "Unknown name.",
        SourceLocation.getDefaultInstance());
    DmnSemanticPipelineResult analysis = new DmnSemanticPipelineResult(
        Definitions.getDefaultInstance(), List.of(), List.of(diagnostic));

    assertThatThrownBy(() -> lowerer.lower(analysis))
        .isInstanceOf(RuntimeIrLoweringException.class)
        .hasMessageContaining("1 diagnostic");
  }

  @Test
  void lowersTypedConstantDecisionExpression() {
    TypeReference number = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
    DrgElement decision = decisionWithExpression(
        "constant", "Constant", number,
        Expression.newBuilder()
            .setLiteral(LiteralExpression.newBuilder()
                .setKind(LiteralKind.LITERAL_KIND_NUMBER).setValue("42"))
            .setInferredType(number)
            .build());
    Definitions model = Definitions.newBuilder().addDrgElements(decision).build();

    RuntimeDecision lowered = lowerer.lower(
        new DmnSemanticPipelineResult(model, List.of(decision), List.of()))
        .decisions().getFirst();

    assertThat(lowered.expression()).containsInstanceOf(RuntimeConstant.class);
    RuntimeConstant constant = (RuntimeConstant) lowered.expression().orElseThrow();
    assertThat(constant.kind()).isEqualTo(RuntimeConstantKind.NUMBER);
    assertThat(constant.value()).isEqualTo("42");
    assertThat(constant.type().kind()).isEqualTo(RuntimeTypeKind.NUMBER);
  }

  @Test
  void lowersBoundNameToValueSlotReference() {
    TypeReference number = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
    DrgElement input = DrgElement.newBuilder().setInputData(InputData.newBuilder()
        .setNode(Node.newBuilder().setId("input-id").setName("Input"))
        .setVariable(InformationItem.newBuilder().setType(number))).build();
    Expression name = Expression.newBuilder()
        .setName(NameExpression.newBuilder().setName("Input"))
        .setInferredType(number)
        .build();
    Decision decisionValue = decisionWithExpression(
        "decision-id", "Result", number, name).getDecision().toBuilder()
        .addInformationRequirements(
            InformationRequirement.newBuilder().setInput(ref("#input-id")))
        .build();
    DrgElement decision = DrgElement.newBuilder().setDecision(decisionValue).build();
    Definitions model = Definitions.newBuilder()
        .addDrgElements(input).addDrgElements(decision).build();
    DmnSymbolBinding binding = new DmnSymbolBinding(
        "definitions/decision[Result]/logic/literalExpression",
        "definitions/inputData[Input]", "Input", "input-id",
        DmnSymbolKind.INPUT_DATA, number);

    RuntimeDecision lowered = lowerer.lower(new DmnSemanticPipelineResult(
        model, List.of(decision), List.of(), List.of(binding))).decisions().getFirst();

    assertThat(lowered.expression()).containsInstanceOf(RuntimeValueReference.class);
    RuntimeValueReference reference =
        (RuntimeValueReference) lowered.expression().orElseThrow();
    assertThat(reference.sourceSlot()).isZero();
    assertThat(reference.type().kind()).isEqualTo(RuntimeTypeKind.NUMBER);
  }

  private static DrgElement decisionWithExpression(
      String id, String name, TypeReference type, Expression expression) {
    return DrgElement.newBuilder().setDecision(Decision.newBuilder()
        .setNode(Node.newBuilder().setId(id).setName(name))
        .setVariable(InformationItem.newBuilder().setType(type))
        .setLogic(DecisionLogic.newBuilder().setLiteralExpression(
            Feel.newBuilder().setParsed(
                FeelParsed.newBuilder().setAst(expression))))).build();
  }

  private static ElementReference ref(String href) {
    return ElementReference.newBuilder().setHref(href).build();
  }

  private static TypeReference builtin(BuiltinType type) {
    return TypeReference.newBuilder().setBuiltin(type).build();
  }
}

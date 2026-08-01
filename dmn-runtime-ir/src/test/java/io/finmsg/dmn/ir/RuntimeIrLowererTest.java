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

  @Test
  void lowersNestedUnaryAndBinaryExpressions() {
    TypeReference number = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
    DrgElement input = DrgElement.newBuilder().setInputData(InputData.newBuilder()
        .setNode(Node.newBuilder().setId("input-id").setName("Input"))
        .setVariable(InformationItem.newBuilder().setType(number))).build();
    Expression left = Expression.newBuilder()
        .setName(NameExpression.newBuilder().setName("Input"))
        .setInferredType(number).build();
    Expression literal = Expression.newBuilder()
        .setLiteral(LiteralExpression.newBuilder()
            .setKind(LiteralKind.LITERAL_KIND_NUMBER).setValue("2"))
        .setInferredType(number).build();
    Expression right = Expression.newBuilder()
        .setUnary(UnaryExpression.newBuilder()
            .setOperator(UnaryOperator.UNARY_OPERATOR_MINUS).setExpression(literal))
        .setInferredType(number).build();
    Expression sum = Expression.newBuilder()
        .setBinary(BinaryExpression.newBuilder()
            .setOperator(BinaryOperator.BINARY_OPERATOR_ADD)
            .setLeft(left).setRight(right))
        .setInferredType(number).build();
    Decision value = decisionWithExpression("decision-id", "Result", number, sum)
        .getDecision().toBuilder()
        .addInformationRequirements(
            InformationRequirement.newBuilder().setInput(ref("#input-id")))
        .build();
    DrgElement decision = DrgElement.newBuilder().setDecision(value).build();
    Definitions model = Definitions.newBuilder()
        .addDrgElements(input).addDrgElements(decision).build();
    DmnSymbolBinding binding = new DmnSymbolBinding(
        "definitions/decision[Result]/logic/literalExpression/left",
        "definitions/inputData[Input]", "Input", "input-id",
        DmnSymbolKind.INPUT_DATA, number);

    RuntimeExpression lowered = lowerer.lower(new DmnSemanticPipelineResult(
        model, List.of(decision), List.of(), List.of(binding)))
        .decisions().getFirst().expression().orElseThrow();

    assertThat(lowered).isInstanceOf(RuntimeBinaryExpression.class);
    RuntimeBinaryExpression binary = (RuntimeBinaryExpression) lowered;
    assertThat(binary.operator()).isEqualTo(RuntimeBinaryOperator.ADD);
    assertThat(binary.left()).isInstanceOf(RuntimeValueReference.class);
    assertThat(binary.right()).isInstanceOf(RuntimeUnaryExpression.class);
    RuntimeUnaryExpression unary = (RuntimeUnaryExpression) binary.right();
    assertThat(unary.operator()).isEqualTo(RuntimeUnaryOperator.NEGATE);
    assertThat(unary.operand()).isInstanceOf(RuntimeConstant.class);
    assertThat(binary.type().kind()).isEqualTo(RuntimeTypeKind.NUMBER);
  }

  @Test
  void lowersConditionalListAndFunctionCallExpressions() {
    TypeReference number = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
    TypeReference booleanType = builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN);
    TypeReference numberList = TypeReference.newBuilder()
        .setList(ListTypeReference.newBuilder().setElementType(number)).build();
    Expression condition = literal(
        LiteralKind.LITERAL_KIND_BOOLEAN, "true", booleanType);
    Expression call = Expression.newBuilder()
        .setFunctionCall(FunctionCall.newBuilder()
            .setFunction("abs")
            .addArguments(literal(LiteralKind.LITERAL_KIND_NUMBER, "-2", number)))
        .setInferredType(number).build();
    Expression conditional = Expression.newBuilder()
        .setIfExpression(IfExpression.newBuilder()
            .setCondition(condition)
            .setThenExpression(call)
            .setElseExpression(literal(LiteralKind.LITERAL_KIND_NUMBER, "0", number)))
        .setInferredType(number).build();
    Expression list = Expression.newBuilder()
        .setList(ListExpression.newBuilder()
            .addElements(conditional)
            .addElements(literal(LiteralKind.LITERAL_KIND_NUMBER, "3", number)))
        .setInferredType(numberList).build();
    DrgElement decision = decisionWithExpression(
        "decision-id", "Result", numberList, list);
    Definitions model = Definitions.newBuilder().addDrgElements(decision).build();

    RuntimeExpression lowered = lowerer.lower(
        new DmnSemanticPipelineResult(model, List.of(decision), List.of()))
        .decisions().getFirst().expression().orElseThrow();

    assertThat(lowered).isInstanceOf(RuntimeListExpression.class);
    RuntimeListExpression runtimeList = (RuntimeListExpression) lowered;
    assertThat(runtimeList.type().kind()).isEqualTo(RuntimeTypeKind.LIST);
    assertThat(runtimeList.elements()).hasSize(2);
    assertThat(runtimeList.elements().getFirst())
        .isInstanceOf(RuntimeConditionalExpression.class);
    RuntimeConditionalExpression runtimeConditional =
        (RuntimeConditionalExpression) runtimeList.elements().getFirst();
    assertThat(runtimeConditional.condition()).isInstanceOf(RuntimeConstant.class);
    assertThat(runtimeConditional.thenExpression()).isInstanceOf(RuntimeFunctionCall.class);
    RuntimeFunctionCall runtimeCall =
        (RuntimeFunctionCall) runtimeConditional.thenExpression();
    assertThat(runtimeCall.function()).isEqualTo("abs");
    assertThat(runtimeCall.arguments()).singleElement()
        .isInstanceOf(RuntimeConstant.class);
    assertThat(runtimeConditional.elseExpression()).isInstanceOf(RuntimeConstant.class);
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

  private static Expression literal(
      LiteralKind kind, String value, TypeReference type) {
    return Expression.newBuilder()
        .setLiteral(LiteralExpression.newBuilder().setKind(kind).setValue(value))
        .setInferredType(type)
        .build();
  }

  private static ElementReference ref(String href) {
    return ElementReference.newBuilder().setHref(href).build();
  }

  private static TypeReference builtin(BuiltinType type) {
    return TypeReference.newBuilder().setBuiltin(type).build();
  }
}

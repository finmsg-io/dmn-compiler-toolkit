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

  @Test
  void lowersContextLocalBindingsAndPathAccess() {
    TypeReference number = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
    TypeReference contextType = TypeReference.newBuilder()
        .setContext(ContextTypeReference.newBuilder()
            .addEntries(ContextEntryTypeReference.newBuilder().setName("a").setType(number))
            .addEntries(ContextEntryTypeReference.newBuilder().setName("b").setType(number)))
        .build();
    Expression localName = Expression.newBuilder()
        .setName(NameExpression.newBuilder().setName("a"))
        .setInferredType(number).build();
    Expression sum = Expression.newBuilder()
        .setBinary(BinaryExpression.newBuilder()
            .setOperator(BinaryOperator.BINARY_OPERATOR_ADD)
            .setLeft(localName)
            .setRight(literal(LiteralKind.LITERAL_KIND_NUMBER, "1", number)))
        .setInferredType(number).build();
    Expression context = Expression.newBuilder()
        .setContext(ContextExpression.newBuilder()
            .addEntries(ContextEntry.newBuilder().setName("a")
                .setExpression(literal(LiteralKind.LITERAL_KIND_NUMBER, "1", number)))
            .addEntries(ContextEntry.newBuilder().setName("b").setExpression(sum)))
        .setInferredType(contextType).build();
    Expression path = Expression.newBuilder()
        .setPath(PathExpression.newBuilder().setSource(context).setMember("b"))
        .setInferredType(number).build();
    DrgElement decision = decisionWithExpression(
        "decision-id", "Result", number, path);
    Definitions model = Definitions.newBuilder().addDrgElements(decision).build();
    String root = "definitions/decision[Result]/logic/literalExpression";
    DmnSymbolBinding localBinding = new DmnSymbolBinding(
        root + "/source/entry[1]/left", root + "/source/entry[0]", "a", "",
        DmnSymbolKind.LOCAL_VARIABLE, number);

    RuntimeExpression lowered = lowerer.lower(new DmnSemanticPipelineResult(
        model, List.of(decision), List.of(), List.of(localBinding)))
        .decisions().getFirst().expression().orElseThrow();

    assertThat(lowered).isInstanceOf(RuntimePathExpression.class);
    RuntimePathExpression runtimePath = (RuntimePathExpression) lowered;
    assertThat(runtimePath.member()).isEqualTo("b");
    assertThat(runtimePath.type().kind()).isEqualTo(RuntimeTypeKind.NUMBER);
    assertThat(runtimePath.source()).isInstanceOf(RuntimeContextExpression.class);
    RuntimeContextExpression runtimeContext =
        (RuntimeContextExpression) runtimePath.source();
    assertThat(runtimeContext.entries()).extracting(RuntimeContextEntry::name)
        .containsExactly("a", "b");
    assertThat(runtimeContext.entries()).extracting(RuntimeContextEntry::localSlot)
        .containsExactly(0, 1);
    RuntimeBinaryExpression second =
        (RuntimeBinaryExpression) runtimeContext.entries().get(1).expression();
    assertThat(second.left()).isEqualTo(new RuntimeLocalReference(
        0, RuntimeType.scalar(RuntimeTypeKind.NUMBER)));
  }

  @Test
  void lowersRangesFiltersAndTestExpressions() {
    TypeReference any = builtin(BuiltinType.BUILTIN_TYPE_ANY);
    TypeReference number = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
    TypeReference booleanType = builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN);
    TypeReference numberList = TypeReference.newBuilder()
        .setList(ListTypeReference.newBuilder().setElementType(number)).build();
    TypeReference anyList = TypeReference.newBuilder()
        .setList(ListTypeReference.newBuilder().setElementType(any)).build();
    TypeReference numberRange = TypeReference.newBuilder()
        .setRange(RangeTypeReference.newBuilder().setElementType(number)).build();
    Expression range = Expression.newBuilder().setRange(RangeExpression.newBuilder()
            .setLower(literal(LiteralKind.LITERAL_KIND_NUMBER, "1", number))
            .setUpper(literal(LiteralKind.LITERAL_KIND_NUMBER, "10", number))
            .setLowerBoundary(RangeBoundary.RANGE_BOUNDARY_CLOSED)
            .setUpperBoundary(RangeBoundary.RANGE_BOUNDARY_OPEN))
        .setInferredType(numberRange).build();
    Expression filter = Expression.newBuilder().setFilter(FilterExpression.newBuilder()
            .setSource(Expression.newBuilder().setList(ListExpression.newBuilder()
                    .addElements(literal(LiteralKind.LITERAL_KIND_NUMBER, "1", number)))
                .setInferredType(numberList))
            .setFilter(literal(LiteralKind.LITERAL_KIND_BOOLEAN, "true", booleanType)))
        .setInferredType(numberList).build();
    Expression between = Expression.newBuilder().setBetween(BetweenExpression.newBuilder()
            .setValue(literal(LiteralKind.LITERAL_KIND_NUMBER, "5", number))
            .setLower(literal(LiteralKind.LITERAL_KIND_NUMBER, "1", number))
            .setUpper(literal(LiteralKind.LITERAL_KIND_NUMBER, "10", number)))
        .setInferredType(booleanType).build();
    RangeExpression unaryRange = RangeExpression.newBuilder()
        .setLower(literal(LiteralKind.LITERAL_KIND_NUMBER, "20", number))
        .setUpper(literal(LiteralKind.LITERAL_KIND_NUMBER, "30", number))
        .setLowerBoundary(RangeBoundary.RANGE_BOUNDARY_CLOSED)
        .setUpperBoundary(RangeBoundary.RANGE_BOUNDARY_CLOSED).build();
    Expression in = Expression.newBuilder().setIn(InExpression.newBuilder()
            .setValue(literal(LiteralKind.LITERAL_KIND_NUMBER, "5", number))
            .setTests(UnaryTestsExpression.newBuilder()
                .addTests(PositiveUnaryTest.newBuilder().setComparison(
                    ComparisonUnaryTest.newBuilder()
                        .setOperator(UnaryTestOperator.UNARY_TEST_OPERATOR_LESS)
                        .setEndpoint(literal(
                            LiteralKind.LITERAL_KIND_NUMBER, "10", number))))
                .addTests(PositiveUnaryTest.newBuilder().setRange(unaryRange))))
        .setInferredType(booleanType).build();
    Expression instanceOf = Expression.newBuilder()
        .setInstanceOf(InstanceOfExpression.newBuilder()
            .setExpression(literal(LiteralKind.LITERAL_KIND_NUMBER, "5", number))
            .setType(FeelType.newBuilder().setQualifiedName("number")))
        .setInferredType(booleanType).build();
    Expression standaloneTests = Expression.newBuilder()
        .setUnaryTests(UnaryTestsExpression.newBuilder().setWildcard(true))
        .setInferredType(booleanType).build();
    Expression expressions = Expression.newBuilder().setList(ListExpression.newBuilder()
            .addElements(range).addElements(filter).addElements(between)
            .addElements(in).addElements(instanceOf).addElements(standaloneTests))
        .setInferredType(anyList).build();
    DrgElement decision = decisionWithExpression(
        "decision-id", "Result", anyList, expressions);

    RuntimeListExpression lowered = (RuntimeListExpression) lowerer.lower(
        new DmnSemanticPipelineResult(
            Definitions.newBuilder().addDrgElements(decision).build(),
            List.of(decision), List.of()))
        .decisions().getFirst().expression().orElseThrow();

    RuntimeRangeExpression runtimeRange = (RuntimeRangeExpression) lowered.elements().get(0);
    assertThat(runtimeRange.lower()).isPresent();
    assertThat(runtimeRange.upper()).isPresent();
    assertThat(runtimeRange.lowerBoundary()).isEqualTo(RuntimeRangeBoundary.CLOSED);
    assertThat(runtimeRange.upperBoundary()).isEqualTo(RuntimeRangeBoundary.OPEN);
    assertThat(lowered.elements().get(1)).isInstanceOf(RuntimeFilterExpression.class);
    assertThat(lowered.elements().get(2)).isInstanceOf(RuntimeBetweenExpression.class);
    RuntimeInExpression runtimeIn = (RuntimeInExpression) lowered.elements().get(3);
    assertThat(runtimeIn.tests().tests()).hasSize(2);
    assertThat(runtimeIn.tests().tests().get(0))
        .isInstanceOf(RuntimeComparisonUnaryTest.class);
    assertThat(runtimeIn.tests().tests().get(1)).isInstanceOf(RuntimeRangeUnaryTest.class);
    RuntimeInstanceOfExpression runtimeInstance =
        (RuntimeInstanceOfExpression) lowered.elements().get(4);
    assertThat(runtimeInstance.testedType().kind()).isEqualTo(RuntimeTypeKind.NUMBER);
    RuntimeUnaryTestsExpression runtimeTests =
        (RuntimeUnaryTestsExpression) lowered.elements().get(5);
    assertThat(runtimeTests.tests().wildcard()).isTrue();
  }

  @Test
  void lowersIterationQuantificationAndFunctionDefinitions() {
    TypeReference any = builtin(BuiltinType.BUILTIN_TYPE_ANY);
    TypeReference number = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
    TypeReference booleanType = builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN);
    TypeReference numberList = TypeReference.newBuilder()
        .setList(ListTypeReference.newBuilder().setElementType(number)).build();
    TypeReference functionType = TypeReference.newBuilder()
        .setFunction(FunctionTypeReference.newBuilder()
            .addParameterType(number).setReturnType(number)).build();
    TypeReference anyList = TypeReference.newBuilder()
        .setList(ListTypeReference.newBuilder().setElementType(any)).build();
    Expression iterationName = name("x", number);
    Expression forExpression = Expression.newBuilder()
        .setForExpression(ForExpression.newBuilder()
            .addIterations(IterationContext.newBuilder().setVariable("x")
                .setStart(literal(LiteralKind.LITERAL_KIND_NUMBER, "1", number))
                .setEnd(literal(LiteralKind.LITERAL_KIND_NUMBER, "3", number)))
            .setReturnExpression(iterationName))
        .setInferredType(numberList).build();
    Expression quantifiedName = name("y", number);
    Expression satisfies = Expression.newBuilder()
        .setBinary(BinaryExpression.newBuilder()
            .setOperator(BinaryOperator.BINARY_OPERATOR_GREATER)
            .setLeft(quantifiedName)
            .setRight(literal(LiteralKind.LITERAL_KIND_NUMBER, "0", number)))
        .setInferredType(booleanType).build();
    Expression quantified = Expression.newBuilder()
        .setQuantified(QuantifiedExpression.newBuilder()
            .setQuantifier(Quantifier.QUANTIFIER_EVERY)
            .addBindings(IterationBinding.newBuilder().setVariable("y")
                .setIn(Expression.newBuilder().setList(ListExpression.newBuilder()
                        .addElements(literal(LiteralKind.LITERAL_KIND_NUMBER, "1", number)))
                    .setInferredType(numberList)))
            .setSatisfies(satisfies))
        .setInferredType(booleanType).build();
    Expression function = Expression.newBuilder()
        .setFunctionDefinition(FunctionDefinitionExpression.newBuilder()
            .addParameters(FormalParameter.newBuilder().setName("value")
                .setType(FeelType.newBuilder().setQualifiedName("number")))
            .setBody(name("value", number)))
        .setInferredType(functionType).build();
    Expression expression = Expression.newBuilder()
        .setList(ListExpression.newBuilder()
            .addElements(forExpression).addElements(quantified).addElements(function))
        .setInferredType(anyList).build();
    DrgElement decision = decisionWithExpression(
        "decision-id", "Result", anyList, expression);
    String root = "definitions/decision[Result]/logic/literalExpression";
    List<DmnSymbolBinding> bindings = List.of(
        new DmnSymbolBinding(root + "/element[0]/return",
            root + "/element[0]/iteration[0]", "x", "",
            DmnSymbolKind.LOCAL_VARIABLE, number),
        new DmnSymbolBinding(root + "/element[1]/satisfies/left",
            root + "/element[1]/binding[0]", "y", "",
            DmnSymbolKind.LOCAL_VARIABLE, number),
        new DmnSymbolBinding(root + "/element[2]/body",
            root + "/element[2]/parameter[0]", "value", "",
            DmnSymbolKind.PARAMETER, number));

    RuntimeListExpression lowered = (RuntimeListExpression) lowerer.lower(
        new DmnSemanticPipelineResult(
            Definitions.newBuilder().addDrgElements(decision).build(),
            List.of(decision), List.of(), bindings))
        .decisions().getFirst().expression().orElseThrow();

    RuntimeForExpression runtimeFor = (RuntimeForExpression) lowered.elements().get(0);
    assertThat(runtimeFor.iterations()).singleElement().satisfies(iteration -> {
      assertThat(iteration.localSlot()).isZero();
      assertThat(iteration.end()).isPresent();
    });
    assertThat(runtimeFor.result()).isInstanceOf(RuntimeLocalReference.class);
    RuntimeQuantifiedExpression runtimeQuantified =
        (RuntimeQuantifiedExpression) lowered.elements().get(1);
    assertThat(runtimeQuantified.quantifier()).isEqualTo(RuntimeQuantifier.EVERY);
    assertThat(runtimeQuantified.bindings()).singleElement()
        .extracting(RuntimeQuantifiedBinding::localSlot).isEqualTo(1);
    RuntimeFunctionDefinition runtimeFunction =
        (RuntimeFunctionDefinition) lowered.elements().get(2);
    assertThat(runtimeFunction.parameters()).singleElement().satisfies(parameter -> {
      assertThat(parameter.name()).isEqualTo("value");
      assertThat(parameter.localSlot()).isEqualTo(2);
      assertThat(parameter.type().kind()).isEqualTo(RuntimeTypeKind.NUMBER);
    });
    assertThat(runtimeFunction.body()).containsInstanceOf(RuntimeLocalReference.class);
  }

  @Test
  void lowersStaticAndDynamicInvocationsAndDescendantAccess() {
    TypeReference any = builtin(BuiltinType.BUILTIN_TYPE_ANY);
    TypeReference number = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
    TypeReference functionType = TypeReference.newBuilder()
        .setFunction(FunctionTypeReference.newBuilder()
            .addParameterType(number).setReturnType(number)).build();
    TypeReference contextType = TypeReference.newBuilder()
        .setContext(ContextTypeReference.newBuilder()
            .addEntries(ContextEntryTypeReference.newBuilder().setName("amount").setType(number)))
        .build();
    TypeReference anyList = TypeReference.newBuilder()
        .setList(ListTypeReference.newBuilder().setElementType(any)).build();
    DrgElement functionInput = DrgElement.newBuilder().setInputData(InputData.newBuilder()
        .setNode(Node.newBuilder().setId("function-id").setName("Calculator"))
        .setVariable(InformationItem.newBuilder().setType(functionType))).build();
    Expression dynamicInvocation = Expression.newBuilder()
        .setInvocation(InvocationExpression.newBuilder()
            .setTarget(name("Calculator", functionType))
            .addPositionalArguments(literal(
                LiteralKind.LITERAL_KIND_NUMBER, "2", number)))
        .setInferredType(number).build();
    Expression staticInvocation = Expression.newBuilder()
        .setInvocation(InvocationExpression.newBuilder()
            .setTarget(Expression.newBuilder()
                .setName(NameExpression.newBuilder().setName("abs")))
            .addArguments(NamedArgument.newBuilder().setName("n")
                .setExpression(literal(LiteralKind.LITERAL_KIND_NUMBER, "-2", number))))
        .setInferredType(number).build();
    Expression context = Expression.newBuilder()
        .setContext(ContextExpression.newBuilder().addEntries(ContextEntry.newBuilder()
            .setName("amount")
            .setExpression(literal(LiteralKind.LITERAL_KIND_NUMBER, "3", number))))
        .setInferredType(contextType).build();
    Expression descendant = Expression.newBuilder()
        .setDescendant(DescendantExpression.newBuilder()
            .setSource(context).setMember("amount"))
        .setInferredType(number).build();
    Expression expression = Expression.newBuilder()
        .setList(ListExpression.newBuilder().addElements(dynamicInvocation)
            .addElements(staticInvocation).addElements(descendant))
        .setInferredType(anyList).build();
    Decision decisionValue = decisionWithExpression(
        "decision-id", "Result", anyList, expression).getDecision().toBuilder()
        .addInformationRequirements(
            InformationRequirement.newBuilder().setInput(ref("#function-id")))
        .build();
    DrgElement decision = DrgElement.newBuilder().setDecision(decisionValue).build();
    String root = "definitions/decision[Result]/logic/literalExpression";
    DmnSymbolBinding targetBinding = new DmnSymbolBinding(
        root + "/element[0]/target", "definitions/inputData[Calculator]",
        "Calculator", "function-id", DmnSymbolKind.INPUT_DATA, functionType);

    RuntimeListExpression lowered = (RuntimeListExpression) lowerer.lower(
        new DmnSemanticPipelineResult(Definitions.newBuilder()
            .addDrgElements(functionInput).addDrgElements(decision).build(),
            List.of(decision), List.of(), List.of(targetBinding)))
        .decisions().getFirst().expression().orElseThrow();

    RuntimeInvocationExpression dynamic =
        (RuntimeInvocationExpression) lowered.elements().get(0);
    assertThat(dynamic.function()).isEmpty();
    assertThat(dynamic.target()).containsInstanceOf(RuntimeValueReference.class);
    assertThat(dynamic.positionalArguments()).singleElement()
        .isInstanceOf(RuntimeConstant.class);
    RuntimeInvocationExpression named =
        (RuntimeInvocationExpression) lowered.elements().get(1);
    assertThat(named.function()).contains("abs");
    assertThat(named.target()).isEmpty();
    assertThat(named.namedArguments()).singleElement()
        .extracting(RuntimeNamedArgument::name).isEqualTo("n");
    RuntimeDescendantExpression runtimeDescendant =
        (RuntimeDescendantExpression) lowered.elements().get(2);
    assertThat(runtimeDescendant.member()).isEqualTo("amount");
    assertThat(runtimeDescendant.source()).isInstanceOf(RuntimeContextExpression.class);
  }

  @Test
  void lowersBoxedDecisionTablesAndTableReferences() {
    TypeReference number = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
    UnaryTestsExpression wildcard = UnaryTestsExpression.newBuilder().setWildcard(true).build();
    DecisionTable table = DecisionTable.newBuilder()
        .setNode(Node.newBuilder().setId("table-id"))
        .setHitPolicy(HitPolicySpec.newBuilder()
            .setPolicy(HitPolicy.HIT_POLICY_COLLECT)
            .setAggregation(Aggregation.AGGREGATION_SUM))
        .addInputs(InputClause.newBuilder()
            .setType(number)
            .setInputExpression(Feel.newBuilder().setParsed(FeelParsed.newBuilder()
                .setAst(literal(LiteralKind.LITERAL_KIND_NUMBER, "1", number)))))
        .addOutputs(OutputClause.newBuilder()
            .setNode(Node.newBuilder().setName("score"))
            .setType(number)
            .setDefaultOutputEntry(ExpressionNode.newBuilder().setParsed(
                ExpressionParsed.newBuilder().setFeel(FeelParsed.newBuilder()
                    .setAst(literal(LiteralKind.LITERAL_KIND_NUMBER, "0", number))))))
        .addAnnotations(AnnotationClause.newBuilder()
            .setNode(Node.newBuilder().setName("note")))
        .addRules(DecisionRule.newBuilder().setRuleIndex(0)
            .addInputEntries(UnaryTest.newBuilder().setParsed(
                UnaryTestParsed.newBuilder().setTests(wildcard)))
            .addOutputEntries(Feel.newBuilder().setParsed(FeelParsed.newBuilder()
                .setAst(literal(LiteralKind.LITERAL_KIND_NUMBER, "10", number))))
            .addAnnotationEntries(RuleAnnotation.newBuilder().setText("matched")))
        .build();
    DrgElement owner = DrgElement.newBuilder().setDecision(Decision.newBuilder()
        .setNode(Node.newBuilder().setId("owner-id").setName("Table owner"))
        .setVariable(InformationItem.newBuilder().setType(number))
        .setLogic(DecisionLogic.newBuilder().setDecisionTable(table))).build();
    Expression reference = Expression.newBuilder()
        .setDecisionTable(DecisionTableExpression.newBuilder().setDecisionTableId("table-id"))
        .setInferredType(number).build();
    DrgElement consumer = decisionWithExpression(
        "consumer-id", "Consumer", number, reference);
    Definitions model = Definitions.newBuilder()
        .addDrgElements(owner).addDrgElements(consumer).build();

    RuntimeModel lowered = lowerer.lower(new DmnSemanticPipelineResult(
        model, List.of(owner, consumer), List.of()));

    RuntimeDecision runtimeOwner = lowered.decisions().get(0);
    assertThat(runtimeOwner.expression()).isEmpty();
    assertThat(runtimeOwner.decisionTable()).isPresent();
    RuntimeDecisionTable runtimeTable = runtimeOwner.decisionTable().orElseThrow();
    assertThat(runtimeTable.hitPolicy()).isEqualTo(RuntimeHitPolicy.COLLECT);
    assertThat(runtimeTable.aggregation()).contains(RuntimeAggregation.SUM);
    assertThat(runtimeTable.inputs()).singleElement()
        .extracting(input -> input.type().kind()).isEqualTo(RuntimeTypeKind.NUMBER);
    assertThat(runtimeTable.outputs()).singleElement().satisfies(output -> {
      assertThat(output.name()).contains("score");
      assertThat(output.defaultValue()).containsInstanceOf(RuntimeConstant.class);
    });
    assertThat(runtimeTable.rules()).singleElement().satisfies(rule -> {
      assertThat(rule.inputEntries()).singleElement()
          .satisfies(tests -> assertThat(tests.wildcard()).isTrue());
      assertThat(rule.outputEntries()).singleElement()
          .isInstanceOf(RuntimeConstant.class);
      assertThat(rule.annotations()).containsExactly("matched");
    });
    RuntimeExpression runtimeReference = lowered.decisions().get(1)
        .expression().orElseThrow();
    assertThat(runtimeReference).isEqualTo(new RuntimeDecisionTableReference(
        runtimeOwner.resultSlot(), RuntimeType.scalar(RuntimeTypeKind.NUMBER)));
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

  private static Expression name(String value, TypeReference type) {
    return Expression.newBuilder().setName(NameExpression.newBuilder().setName(value))
        .setInferredType(type).build();
  }

  private static ElementReference ref(String href) {
    return ElementReference.newBuilder().setHref(href).build();
  }

  private static TypeReference builtin(BuiltinType type) {
    return TypeReference.newBuilder().setBuiltin(type).build();
  }
}

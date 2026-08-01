package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.FeelParserFacade;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.Aggregation;
import io.finmsg.dmn.model.Binding;
import io.finmsg.dmn.model.BoxedExpression;
import io.finmsg.dmn.model.BoxedExpressionParsed;
import io.finmsg.dmn.model.BusinessKnowledgeModel;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.DecisionRule;
import io.finmsg.dmn.model.DecisionTable;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.FeelParsed;
import io.finmsg.dmn.model.HitPolicy;
import io.finmsg.dmn.model.HitPolicySpec;
import io.finmsg.dmn.model.InformationItem;
import io.finmsg.dmn.model.InputClause;
import io.finmsg.dmn.model.ItemComponent;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.OutputClause;
import io.finmsg.dmn.model.RelationColumnParsed;
import io.finmsg.dmn.model.RelationParsed;
import io.finmsg.dmn.model.RelationRowParsed;
import io.finmsg.dmn.model.Expression;
import io.finmsg.dmn.model.ExpressionNode;
import io.finmsg.dmn.model.ExpressionParsed;
import io.finmsg.dmn.model.ElementReference;
import io.finmsg.dmn.model.FunctionDefinition;
import io.finmsg.dmn.model.Invocation;
import io.finmsg.dmn.model.KnowledgeRequirement;
import io.finmsg.dmn.model.ListTypeReference;
import io.finmsg.dmn.model.TypeReference;
import io.finmsg.dmn.model.TypeConstraint;
import io.finmsg.dmn.model.UnaryTest;
import org.junit.jupiter.api.Test;

class DmnTypeAnalyzerTest {

  private final FeelParserFacade parser = new FeelParserFacade();
  private final DmnTypeAnalyzer analyzer = new DmnTypeAnalyzer();

  @Test
  void validatesDeclaredDecisionTypeAgainstLiteralExpression() {
    Definitions model = Definitions.newBuilder()
        .addDrgElements(decision("Valid", builtin(BuiltinType.BUILTIN_TYPE_NUMBER), "1"))
        .addDrgElements(decision("Invalid", builtin(BuiltinType.BUILTIN_TYPE_NUMBER), "\"text\""))
        .build();

    DmnSemanticAnalysisResult result = analyzer.analyze(model);

    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("DECISION_TYPE_MISMATCH");
    assertThat(result.diagnostics().getFirst().path())
        .isEqualTo("definitions/decision[Invalid]/variable");
  }

  @Test
  void doesNotReportMismatchForUnspecifiedDecisionType() {
    Definitions model = Definitions.newBuilder()
        .addDrgElements(decision("Untyped", TypeReference.getDefaultInstance(), "1"))
        .build();

    assertThat(analyzer.analyze(model).diagnostics()).isEmpty();
  }

  @Test
  void validatesDeclaredDecisionTypeAgainstDecisionTableOutput() {
    DecisionTable table = DecisionTable.newBuilder()
        .setHitPolicy(HitPolicySpec.newBuilder().setPolicy(HitPolicy.HIT_POLICY_UNIQUE))
        .addOutputs(OutputClause.newBuilder()
            .setNode(Node.newBuilder().setName("result"))
            .setType(builtin(BuiltinType.BUILTIN_TYPE_STRING)))
        .build();
    Decision decision = Decision.newBuilder()
        .setNode(Node.newBuilder().setName("Table"))
        .setVariable(InformationItem.newBuilder()
            .setType(builtin(BuiltinType.BUILTIN_TYPE_NUMBER)))
        .setLogic(DecisionLogic.newBuilder().setDecisionTable(table))
        .build();
    Definitions model = Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setDecision(decision))
        .build();

    assertThat(analyzer.analyze(model).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("DECISION_TYPE_MISMATCH");
  }

  @Test
  void validatesDecisionTableStructureAndHitPolicy() {
    DecisionTable table = DecisionTable.newBuilder()
        .setHitPolicy(HitPolicySpec.newBuilder()
            .setPolicy(HitPolicy.HIT_POLICY_UNIQUE)
            .setAggregation(Aggregation.AGGREGATION_SUM))
        .addInputs(InputClause.getDefaultInstance())
        .addOutputs(OutputClause.newBuilder().setNode(Node.newBuilder().setName("result")))
        .addOutputs(OutputClause.newBuilder().setNode(Node.newBuilder().setName("result")))
        .addRules(DecisionRule.newBuilder()
            .addOutputEntries(parsedFeel("1")))
        .build();

    DmnSemanticAnalysisResult result = analyzer.analyze(modelWithTable("Invalid table", table));

    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly(
            "DUPLICATE_OUTPUT_NAME",
            "INVALID_INPUT_ENTRY_COUNT",
            "INVALID_OUTPUT_ENTRY_COUNT",
            "INVALID_HIT_POLICY_AGGREGATION");
  }

  @Test
  void validatesRuleAllowedValueAndDefaultOutputTypes() {
    TypeReference number = builtin(BuiltinType.BUILTIN_TYPE_NUMBER);
    DecisionTable table = DecisionTable.newBuilder()
        .setHitPolicy(HitPolicySpec.newBuilder().setPolicy(HitPolicy.HIT_POLICY_UNIQUE))
        .addInputs(InputClause.newBuilder()
            .setType(number)
            .setInputExpression(parsedFeel("1"))
            .setInputValues(parsedUnaryTests("\"invalid\"")))
        .addOutputs(OutputClause.newBuilder()
            .setNode(Node.newBuilder().setName("result"))
            .setType(number)
            .setOutputValues(parsedUnaryTests("\"invalid\""))
            .setDefaultOutputEntry(parsedExpressionNode("\"invalid\"")))
        .addRules(DecisionRule.newBuilder()
            .addInputEntries(UnaryTest.newBuilder()
                .setParsed(parser.parseUnaryTestsAst("\"invalid\"")))
            .addOutputEntries(parsedFeel("\"invalid\"")))
        .build();

    DmnSemanticAnalysisResult result = analyzer.analyze(modelWithTable("Typed table", table));

    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly(
            "UNARY_TEST_TYPE_MISMATCH",
            "RULE_OUTPUT_TYPE_MISMATCH",
            "UNARY_TEST_TYPE_MISMATCH",
            "UNARY_TEST_TYPE_MISMATCH",
            "DEFAULT_OUTPUT_TYPE_MISMATCH");
  }

  @Test
  void validatesCollectAggregationOutputType() {
    DecisionTable table = DecisionTable.newBuilder()
        .setHitPolicy(HitPolicySpec.newBuilder()
            .setPolicy(HitPolicy.HIT_POLICY_COLLECT)
            .setAggregation(Aggregation.AGGREGATION_SUM))
        .addOutputs(OutputClause.newBuilder()
            .setNode(Node.newBuilder().setName("result"))
            .setType(builtin(BuiltinType.BUILTIN_TYPE_STRING)))
        .build();

    assertThat(analyzer.analyze(modelWithTable("Collect", table)).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("INVALID_COLLECT_AGGREGATION_TYPE");
  }

  @Test
  void requiresOrderedOutputValuesForPriorityHitPolicy() {
    DecisionTable table = DecisionTable.newBuilder()
        .setHitPolicy(HitPolicySpec.newBuilder().setPolicy(HitPolicy.HIT_POLICY_PRIORITY))
        .addOutputs(OutputClause.newBuilder()
            .setNode(Node.newBuilder().setName("result"))
            .setType(builtin(BuiltinType.BUILTIN_TYPE_STRING)))
        .build();

    assertThat(analyzer.analyze(modelWithTable("Priority", table)).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("MISSING_OUTPUT_VALUES");
  }

  @Test
  void validatesBkmReturnType() {
    BusinessKnowledgeModel bkm = bkm("Calculator", builtin(BuiltinType.BUILTIN_TYPE_NUMBER),
        "\"not a number\"");

    assertThat(analyzer.analyze(Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setBusinessKnowledgeModel(bkm))
        .build()).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("BKM_RETURN_TYPE_MISMATCH");
  }

  @Test
  void validatesInvocationArgumentTypesAgainstBkmParameters() {
    BusinessKnowledgeModel bkm = bkm("Calculator", builtin(BuiltinType.BUILTIN_TYPE_NUMBER),
        "x + 1").toBuilder()
        .setFunction(FunctionDefinition.newBuilder()
            .addFormalParameters(InformationItem.newBuilder()
                .setNode(Node.newBuilder().setName("x"))
                .setType(builtin(BuiltinType.BUILTIN_TYPE_NUMBER)))
            .setLogic(parsedFeel("x + 1")))
        .build();
    Invocation invocation = Invocation.newBuilder()
        .setExpression(parsedFeel("Calculator"))
        .addBindings(Binding.newBuilder()
            .setParameter("x")
            .setExpression(parsedFeel("\"wrong\"")))
        .build();
    Decision decision = Decision.newBuilder()
        .setNode(Node.newBuilder().setName("Result"))
        .setVariable(InformationItem.newBuilder()
            .setType(builtin(BuiltinType.BUILTIN_TYPE_NUMBER)))
        .addKnowledgeRequirements(KnowledgeRequirement.newBuilder()
            .setRequiredKnowledge(ElementReference.newBuilder().setHref("#bkm-id")))
        .setLogic(DecisionLogic.newBuilder().setInvocation(invocation))
        .build();

    DmnSemanticAnalysisResult result = analyzer.analyze(Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setBusinessKnowledgeModel(bkm))
        .addDrgElements(DrgElement.newBuilder().setDecision(decision))
        .build());

    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("INVOCATION_ARGUMENT_TYPE_MISMATCH");
  }

  @Test
  void typesAndValidatesItemDefinitionConstraints() {
    ItemDefinition item = ItemDefinition.newBuilder()
        .setNode(Node.newBuilder().setName("Limits"))
        .setType(builtin(BuiltinType.BUILTIN_TYPE_NUMBER))
        .setIsCollection(true)
        .setConstraint(parsedConstraint("< 10"))
        .addComponents(ItemComponent.newBuilder()
            .setNode(Node.newBuilder().setName("label"))
            .setType(builtin(BuiltinType.BUILTIN_TYPE_STRING))
            .setConstraint(parsedConstraint("1")))
        .build();

    DmnSemanticAnalysisResult result = analyzer.analyze(Definitions.newBuilder()
        .addItemDefinitions(item)
        .build());

    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("TYPE_CONSTRAINT_TYPE_MISMATCH");
    assertThat(result.model().getItemDefinitions(0).getConstraint().getParsed()
        .getTests().getTests(0).getComparison().getEndpoint().hasInferredType()).isTrue();
  }

  @Test
  void typesAndValidatesBoxedRelations() {
    RelationParsed relation = RelationParsed.newBuilder()
        .addColumns(RelationColumnParsed.newBuilder()
            .setVariable(InformationItem.newBuilder()
                .setNode(Node.newBuilder().setName("amount"))
                .setType(builtin(BuiltinType.BUILTIN_TYPE_NUMBER))))
        .addRows(RelationRowParsed.newBuilder()
            .addExpressions(parsedExpression("\"wrong\""))
            .addExpressions(parsedExpression("2")))
        .build();
    Decision decision = Decision.newBuilder()
        .setNode(Node.newBuilder().setName("Relation"))
        .setLogic(DecisionLogic.newBuilder().setBoxedExpression(
            BoxedExpression.newBuilder().setParsed(
                BoxedExpressionParsed.newBuilder().setRelation(relation))))
        .build();

    DmnSemanticAnalysisResult result = analyzer.analyze(Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setDecision(decision))
        .build());

    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("RELATION_ROW_WIDTH_MISMATCH", "RELATION_CELL_TYPE_MISMATCH");
    assertThat(result.model().getDrgElements(0).getDecision().getLogic().getBoxedExpression()
        .getParsed().getRelation().getRows(0).getExpressions(1).getFeel().getAst()
        .hasInferredType()).isTrue();
  }

  @Test
  void infersRelationAsListOfRowContexts() {
    RelationParsed relation = RelationParsed.newBuilder()
        .addColumns(RelationColumnParsed.newBuilder()
            .setVariable(InformationItem.newBuilder()
                .setNode(Node.newBuilder().setName("amount"))
                .setType(builtin(BuiltinType.BUILTIN_TYPE_NUMBER))))
        .addRows(RelationRowParsed.newBuilder()
            .addExpressions(parsedExpression("2")))
        .build();
    TypeReference declared = TypeReference.newBuilder()
        .setList(ListTypeReference.newBuilder()
            .setElementType(builtin(BuiltinType.BUILTIN_TYPE_NUMBER)))
        .build();
    Decision decision = Decision.newBuilder()
        .setNode(Node.newBuilder().setName("Relation"))
        .setVariable(InformationItem.newBuilder().setType(declared))
        .setLogic(DecisionLogic.newBuilder().setBoxedExpression(
            BoxedExpression.newBuilder().setParsed(
                BoxedExpressionParsed.newBuilder().setRelation(relation))))
        .build();

    assertThat(analyzer.analyze(Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setDecision(decision))
        .build()).diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("DECISION_TYPE_MISMATCH");
  }

  private DrgElement decision(String name, TypeReference declaredType, String expression) {
    Decision decision = Decision.newBuilder()
        .setNode(Node.newBuilder().setName(name))
        .setVariable(InformationItem.newBuilder().setType(declaredType))
        .setLogic(DecisionLogic.newBuilder().setLiteralExpression(
            Feel.newBuilder().setParsed(parser.parseExpressionAst(expression))))
        .build();
    return DrgElement.newBuilder().setDecision(decision).build();
  }

  private Definitions modelWithTable(String name, DecisionTable table) {
    Decision decision = Decision.newBuilder()
        .setNode(Node.newBuilder().setName(name))
        .setVariable(InformationItem.newBuilder()
            .setType(TypeReference.getDefaultInstance()))
        .setLogic(DecisionLogic.newBuilder().setDecisionTable(table))
        .build();
    return Definitions.newBuilder()
        .addDrgElements(DrgElement.newBuilder().setDecision(decision))
        .build();
  }

  private Feel parsedFeel(String source) {
    return Feel.newBuilder().setParsed(parser.parseExpressionAst(source)).build();
  }

  private Feel parsedUnaryTests(String source) {
    return Feel.newBuilder().setParsed(FeelParsed.newBuilder().setAst(
        Expression.newBuilder().setUnaryTests(parser.parseUnaryTestsAst(source).getTests())))
        .build();
  }

  private ExpressionNode parsedExpressionNode(String source) {
    return ExpressionNode.newBuilder().setParsed(ExpressionParsed.newBuilder()
        .setFeel(parser.parseExpressionAst(source))).build();
  }

  private ExpressionParsed parsedExpression(String source) {
    return ExpressionParsed.newBuilder().setFeel(parser.parseExpressionAst(source)).build();
  }

  private BusinessKnowledgeModel bkm(String name, TypeReference returnType, String expression) {
    return BusinessKnowledgeModel.newBuilder()
        .setNode(Node.newBuilder().setId("bkm-id").setName(name))
        .setVariable(InformationItem.newBuilder().setType(returnType))
        .setFunction(FunctionDefinition.newBuilder().setLogic(parsedFeel(expression)))
        .build();
  }

  private TypeConstraint parsedConstraint(String source) {
    return TypeConstraint.newBuilder().setParsed(parser.parseUnaryTestsAst(source)).build();
  }

  private static TypeReference builtin(BuiltinType type) {
    return TypeReference.newBuilder().setBuiltin(type).build();
  }
}

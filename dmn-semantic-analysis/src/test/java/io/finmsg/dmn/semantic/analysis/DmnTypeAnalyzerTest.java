package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.FeelParserFacade;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.Aggregation;
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
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.OutputClause;
import io.finmsg.dmn.model.Expression;
import io.finmsg.dmn.model.ExpressionNode;
import io.finmsg.dmn.model.ExpressionParsed;
import io.finmsg.dmn.model.TypeReference;
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

  private static TypeReference builtin(BuiltinType type) {
    return TypeReference.newBuilder().setBuiltin(type).build();
  }
}

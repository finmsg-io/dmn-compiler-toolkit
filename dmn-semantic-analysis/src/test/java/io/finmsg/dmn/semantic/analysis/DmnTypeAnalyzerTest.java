package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.FeelParserFacade;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.DecisionTable;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.HitPolicy;
import io.finmsg.dmn.model.HitPolicySpec;
import io.finmsg.dmn.model.InformationItem;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.OutputClause;
import io.finmsg.dmn.model.TypeReference;
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

  private DrgElement decision(String name, TypeReference declaredType, String expression) {
    Decision decision = Decision.newBuilder()
        .setNode(Node.newBuilder().setName(name))
        .setVariable(InformationItem.newBuilder().setType(declaredType))
        .setLogic(DecisionLogic.newBuilder().setLiteralExpression(
            Feel.newBuilder().setParsed(parser.parseExpressionAst(expression))))
        .build();
    return DrgElement.newBuilder().setDecision(decision).build();
  }

  private static TypeReference builtin(BuiltinType type) {
    return TypeReference.newBuilder().setBuiltin(type).build();
  }
}

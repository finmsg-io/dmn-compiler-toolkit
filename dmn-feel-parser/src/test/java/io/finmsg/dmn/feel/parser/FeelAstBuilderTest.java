package io.finmsg.dmn.feel.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.model.BinaryOperator;
import io.finmsg.dmn.model.Quantifier;
import io.finmsg.dmn.model.RangeBoundary;
import io.finmsg.dmn.model.UnaryTestOperator;
import org.junit.jupiter.api.Test;

class FeelAstBuilderTest {

  private final FeelParserFacade parser = new FeelParserFacade();

  @Test
  void buildsBinaryExpressionWithPrecedence() {
    var ast = parser.parseExpressionAst("1 + 2 * 3").getAst();

    assertThat(ast.getBinary().getOperator()).isEqualTo(BinaryOperator.BINARY_OPERATOR_ADD);
    assertThat(ast.getBinary().getRight().getBinary().getOperator())
        .isEqualTo(BinaryOperator.BINARY_OPERATOR_MULTIPLY);
  }

  @Test
  void buildsUnboundedAndMixedRanges() {
    var unbounded = parser.parseExpressionAst("[..10]").getAst().getRange();
    var mixed = parser.parseExpressionAst("[20..30)").getAst().getRange();

    assertThat(unbounded.hasLower()).isFalse();
    assertThat(unbounded.hasUpper()).isTrue();
    assertThat(mixed.getLowerBoundary()).isEqualTo(RangeBoundary.RANGE_BOUNDARY_CLOSED);
    assertThat(mixed.getUpperBoundary()).isEqualTo(RangeBoundary.RANGE_BOUNDARY_OPEN);
  }

  @Test
  void buildsUnaryTests() {
    var tests = parser.parseUnaryTestsAst("not(< 10, [20..30])").getTests();

    assertThat(tests.getNegated()).isTrue();
    assertThat(tests.getTestsCount()).isEqualTo(2);
    assertThat(tests.getTests(0).getComparison().getOperator())
        .isEqualTo(UnaryTestOperator.UNARY_TEST_OPERATOR_LESS);
    assertThat(tests.getTests(1).hasRange()).isTrue();
  }

  @Test
  void buildsNotAsInvocationInExpressionContext() {
    var invocation = parser.parseExpressionAst("not(true)").getAst().getInvocation();
    var invalidArity = parser.parseExpressionAst("not(true, false)").getAst().getInvocation();

    assertThat(invocation.getTarget().getName().getName()).isEqualTo("not");
    assertThat(invocation.getPositionalArgumentsCount()).isEqualTo(1);
    assertThat(invocation.getPositionalArguments(0).getLiteral().getValue()).isEqualTo("true");
    assertThat(invalidArity.getTarget().getName().getName()).isEqualTo("not");
    assertThat(invalidArity.getPositionalArgumentsCount()).isEqualTo(2);
  }

  @Test
  void keepsNotAsNegatedTestsWhenUnaryTestSyntaxIsUsedInExpressionContext() {
    var expression = parser.parseExpressionAst("not(< 10, [20..30])").getAst();

    assertThat(expression.hasUnaryTests()).isTrue();
    assertThat(expression.getUnaryTests().getNegated()).isTrue();
    assertThat(expression.getUnaryTests().getTestsCount()).isEqualTo(2);
  }

  @Test
  void buildsPostfixAndDescendantExpressions() {
    var path = parser.parseExpressionAst("orders[1].amount").getAst();
    var descendant = parser.parseExpressionAst("applicant...age").getAst();

    assertThat(path.hasPath()).isTrue();
    assertThat(path.getPath().getSource().hasFilter()).isTrue();
    assertThat(descendant.hasDescendant()).isTrue();
    assertThat(descendant.getDescendant().getMember()).isEqualTo("age");
  }

  @Test
  void buildsMultipleIterationAndQuantifiedBindings() {
    var forExpression =
        parser.parseExpressionAst("for x in [1], y in [2] return x + y")
            .getAst()
            .getForExpression();
    var quantified =
        parser.parseExpressionAst(
                "some x in [1, 2], y in [3, 4] satisfies x < y")
            .getAst()
            .getQuantified();

    assertThat(forExpression.getIterationsCount()).isEqualTo(2);
    assertThat(quantified.getQuantifier()).isEqualTo(Quantifier.QUANTIFIER_SOME);
    assertThat(quantified.getBindingsCount()).isEqualTo(2);
  }

  @Test
  void buildsTypedFunctionDefinition() {
    var function =
        parser.parseExpressionAst("function(x: number) external x + 1")
            .getAst()
            .getFunctionDefinition();

    assertThat(function.getExternal()).isTrue();
    assertThat(function.getParameters(0).getName()).isEqualTo("x");
    assertThat(function.getParameters(0).getType().getQualifiedName()).isEqualTo("number");
  }
}

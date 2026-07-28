package io.finmsg.dmn.feel.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeelExpressionStructureTest {

    private final FeelParserFacade parser = new FeelParserFacade();

    @Test
    void multiplicationHasHigherPrecedenceThanAddition() {
        var root = parser.requireExpression("1 + 2 * 3");
        var additive = additive(root);

        assertThat(additive.multiplicativeExpression()).hasSize(2);
        assertThat(additive.multiplicativeExpression(0).exponentiationExpression()).hasSize(1);
        assertThat(additive.multiplicativeExpression(1).exponentiationExpression()).hasSize(2);
    }

    @Test
    void feelNegationHasHigherPrecedenceThanExponentiation() {
        var root = parser.requireExpression("-4 ** 2");
        var exponentiation = additive(root)
                .multiplicativeExpression(0)
                .exponentiationExpression(0);

        assertThat(exponentiation.arithmeticNegation()).hasSize(2);
        assertThat(exponentiation.arithmeticNegation(0).MINUS()).hasSize(1);
        assertThat(exponentiation.arithmeticNegation(1).MINUS()).isEmpty();
    }

    @Test
    void additiveOperatorsAreRepresentedAsOneLeftAssociativeSequence() {
        var root = parser.requireExpression("10 - 3 + 2");
        var additive = additive(root);

        assertThat(additive.multiplicativeExpression()).hasSize(3);
        assertThat(additive.MINUS()).hasSize(1);
        assertThat(additive.PLUS()).hasSize(1);
    }

    @Test
    void postfixOperationsRemainInSourceOrder() {
        var root = parser.requireExpression("service(1).result[2]");
        var postfix = additive(root)
                .multiplicativeExpression(0)
                .exponentiationExpression(0)
                .arithmeticNegation(0)
                .instanceOfExpression()
                .postfixExpression();

        assertThat(postfix.postfixPart()).hasSize(3);
        assertThat(postfix.postfixPart(0).parameters()).isNotNull();
        assertThat(postfix.postfixPart(1).DOT()).isNotNull();
        assertThat(postfix.postfixPart(2).LBRACKET()).isNotNull();
    }

    private static FeelParser.AdditiveExpressionContext additive(
            FeelParser.ExpressionRootContext root) {
        return root.expression()
                .textualExpression()
                .disjunction()
                .conjunction(0)
                .comparison(0)
                .additiveExpression();
    }
}

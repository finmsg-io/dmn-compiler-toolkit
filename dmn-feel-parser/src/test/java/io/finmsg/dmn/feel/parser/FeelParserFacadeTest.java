package io.finmsg.dmn.feel.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FeelParserFacadeTest {

	private final FeelParserFacade parser = new FeelParserFacade();

	@Test
	void parsesArithmeticWithFeelPrecedence() {
		var result = parser.parseExpression("-4 ** 2 + 3 * 5");

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.tree()).isNotNull();
	}

	@Test
	void parsesPathFilterAndInvocationChain() {
		var result = parser.parseExpression("orders[1].amount");

		assertThat(result.diagnostics()).isEmpty();
	}

	@Test
	void parsesIfExpression() {
		var result = parser.parseExpression("if TotalPoints >= 20 then \"Yes\" else \"No\"");

		assertThat(result.diagnostics()).isEmpty();
	}

	@Test
	void parsesListAndContext() {
		assertThat(parser.parseExpression("[1, 2, 3]").diagnostics()).isEmpty();
		assertThat(parser.parseExpression("{amount: 100, valid: true}").diagnostics()).isEmpty();
	}

	@Test
	void parsesUnaryTests() {
		var result = parser.parseUnaryTests("not(< 10, [20..30])");

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.tree()).isNotNull();
	}

	@Test
	void rejectsIncompleteExpressionInStrictMode() {
		assertThatThrownBy(() -> parser.requireExpression("1 +")).isInstanceOf(FeelParseException.class);
	}
}

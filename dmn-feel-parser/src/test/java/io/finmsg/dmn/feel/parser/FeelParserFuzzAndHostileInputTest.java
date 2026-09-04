package io.finmsg.dmn.feel.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Fuzzing and adversarial hostile-input resilience tests for FEEL Parser
 * (P14.11 / Gate 4).
 */
class FeelParserFuzzAndHostileInputTest {

	private final FeelParserFacade parser = new FeelParserFacade();

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "\t\n\r", "1 + ", "+ 1", "* 5", "then else", "if true then",
			"if true then 1 else", "for in return", "some in satisfies", "every in satisfies", "[1, 2,", "(1 + 2",
			"1 + 2) +", "[1..", "not(", "function()", "undefined_foo_bar_123", "123.456.789", "???", "###", "@@@",
			"1 + + 2"})
	void handlesMalformedInputsGracefully(String malformedInput) {
		assertThatCode(() -> {
			FeelParseResult result = parser.parseExpression(malformedInput);
			assertThat(result).isNotNull();
		}).doesNotThrowAnyException();
	}

	@Test
	void handlesDeeplyNestedParenthesesWithoutCrash() {
		int depth = 200;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			sb.append("(");
		}
		sb.append("42");
		for (int i = 0; i < depth; i++) {
			sb.append(")");
		}

		assertThatCode(() -> {
			FeelParseResult result = parser.parseExpression(sb.toString());
			assertThat(result).isNotNull();
		}).doesNotThrowAnyException();
	}

	@Test
	void handlesDeeplyNestedIfThenElseWithoutCrash() {
		int depth = 100;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			sb.append("if true then ");
		}
		sb.append("1");
		for (int i = 0; i < depth; i++) {
			sb.append(" else 0");
		}

		assertThatCode(() -> {
			FeelParseResult result = parser.parseExpression(sb.toString());
			assertThat(result).isNotNull();
		}).doesNotThrowAnyException();
	}

	@Test
	void handlesOversizedLiterals() {
		String hugeNumber = "9".repeat(5000);
		assertThatCode(() -> {
			FeelParseResult result = parser.parseExpression(hugeNumber);
			assertThat(result).isNotNull();
		}).doesNotThrowAnyException();
	}

	@Test
	void randomFuzzTestingDoesNotThrowUnhandledExceptions() {
		Random random = new Random(42);
		String[] tokens = {"if", "then", "else", "for", "in", "return", "some", "every", "satisfies", "+", "-", "*",
				"/", "**", "<", "<=", ">", ">=", "=", "!=", "(", ")", "[", "]", "{", "}", ":", ",", ".", "..", "true",
				"false", "null", "123", "\"str\"", "foo", "bar", "!", "@", "#", "$", "%", "^", "&"};

		for (int iteration = 0; iteration < 100; iteration++) {
			int length = 1 + random.nextInt(15);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < length; i++) {
				sb.append(tokens[random.nextInt(tokens.length)]).append(" ");
			}
			String fuzzed = sb.toString();

			assertThatCode(() -> {
				FeelParseResult result = parser.parseExpression(fuzzed);
				assertThat(result).isNotNull();
			}).doesNotThrowAnyException();
		}
	}
}

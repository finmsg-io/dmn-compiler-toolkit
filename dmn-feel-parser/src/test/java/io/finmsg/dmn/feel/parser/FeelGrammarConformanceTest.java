package io.finmsg.dmn.feel.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

final class FeelGrammarConformanceTest {

    @Test
    void parsesNegatedUnaryTestsAsExpression() {
        assertExpression("not(< 10, [20..30])");
    }

    @Test
    void parsesNegatedUnaryTestsAtUnaryTestsEntryPoint() {
        assertUnaryTests("not(< 10, [20..30])");
    }

    @Test
    void parsesDescendantExpression() {
        assertExpression("applicant...age");
    }

    @Test
    void parsesUnboundedLowerRange() {
        assertExpression("[..10]");
    }

    @Test
    void parsesUnboundedUpperRange() {
        assertExpression("[1..)");
    }

    @Test
    void parsesClosedInterval() {
        assertExpression("[20..30]");
    }

    @Test
    void parsesOpenInterval() {
        assertExpression("(20..30)");
    }

    @Test
    void parsesMixedInterval() {
        assertExpression("[20..30)");
    }

    @Test
    void parsesUnaryTestList() {
        assertUnaryTests("< 10, [20..30], 50");
    }

    @Test
    void parsesDashUnaryTest() {
        assertUnaryTests("-");
    }

    @Test
    void parsesArithmeticPrecedence() {
        assertExpression("1 + 2 * 3");
    }

    @Test
    void parsesFeelNegationBeforeExponentiation() {
        assertExpression("-4 ** 2");
    }

    @Test
    void parsesPostfixChain() {
        assertExpression("customer.orders[1].amount");
    }

    @Test
    void parsesIfExpression() {
        assertExpression("if score >= 10 then \"high\" else \"low\"");
    }

    @Test
    void parsesForExpression() {
        assertExpression("for x in [1, 2, 3] return x * 2");
    }

    @Test
    void parsesQuantifiedExpression() {
        assertExpression("some x in [1, 2, 3] satisfies x > 2");
    }

    @Test
    void parsesContext() {
        assertExpression("{name: \"Ada\", age: 42}");
    }

    @Test
    void parsesFunctionDefinition() {
        assertExpression("function(x: number) x + 1");
    }

    @Test
    void parsesAtLiteral() {
        assertExpression("@\"2026-07-27\"");
    }

    @Test
    void parsesTypes() {
        assertType("number");
        assertType("list<number>");
        assertType("range<number>");
        assertType("context<name: string, age: number>");
        assertType("function<number, number> -> number");
    }

    @Test
    void rejectsEmptyRange() {
        assertInvalidExpression("[..]");
    }

    @Test
    void rejectsMissingRangeEndDelimiter() {
        assertInvalidExpression("[1..");
    }

    @Test
    void rejectsRangeWithoutOpeningDelimiter() {
        assertInvalidExpression("..10]");
    }

    @Test
    void rejectsThreeDotRangeOperator() {
        assertInvalidExpression("[1...10]");
    }

    private static void assertExpression(String feel) {
        assertParses(feel, FeelParser::expressionRoot);
    }

    private static void assertUnaryTests(String feel) {
        assertParses(feel, FeelParser::unaryTestsRoot);
    }

    private static void assertType(String feel) {
        assertParses(feel, FeelParser::typeRoot);
    }

    private static void assertParses(
            String feel,
            Function<FeelParser, ? extends ParseTree> entryPoint) {

        ParseResult result = parse(feel, entryPoint);

        assertTrue(
                result.errors().isEmpty(),
                () -> "Expected valid FEEL:\n"
                        + feel
                        + "\nErrors:\n"
                        + String.join("\n", result.errors())
                        + "\nTokens:\n"
                        + result.tokens());
    }

    private static void assertInvalidExpression(String feel) {
        ParseResult result = parse(feel, FeelParser::expressionRoot);

        assertFalse(
                result.errors().isEmpty(),
                () -> "Expected invalid FEEL but parsing succeeded:\n"
                        + feel
                        + "\nTokens:\n"
                        + result.tokens());
    }

    private static ParseResult parse(
            String feel,
            Function<FeelParser, ? extends ParseTree> entryPoint) {

        CollectingErrorListener errors = new CollectingErrorListener();

        FeelLexer lexer = new FeelLexer(CharStreams.fromString(feel));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        FeelParser parser = new FeelParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        entryPoint.apply(parser);

        assertEquals(
                Token.EOF,
                tokens.get(tokens.size() - 1).getType(),
                "Token stream must end with EOF");

        return new ParseResult(List.copyOf(errors.messages), renderTokens(tokens));
    }

    private static String renderTokens(CommonTokenStream tokens) {
        StringBuilder result = new StringBuilder();

        for (Token token : tokens.getTokens()) {
            String symbolicName =
                    token.getType() == Token.EOF
                            ? "EOF"
                            : FeelLexer.VOCABULARY.getSymbolicName(token.getType());

            result
                    .append(symbolicName)
                    .append("('")
                    .append(token.getText().replace("\n", "\\n").replace("\r", "\\r"))
                    .append("') ")
                    .append("channel=")
                    .append(token.getChannel())
                    .append('\n');
        }

        return result.toString();
    }

    private record ParseResult(List<String> errors, String tokens) {}

    private static final class CollectingErrorListener extends BaseErrorListener {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String message,
                RecognitionException exception) {

            messages.add(
                    "line "
                            + line
                            + ":"
                            + charPositionInLine
                            + " "
                            + message);
        }
    }
}

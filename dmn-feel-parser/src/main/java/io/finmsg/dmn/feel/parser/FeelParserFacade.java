package io.finmsg.dmn.feel.parser;

import io.finmsg.dmn.model.FeelParsed;
import io.finmsg.dmn.model.UnaryTestParsed;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

/** Public entry point for parsing the FEEL syntactic forms used by DMN. */
public final class FeelParserFacade {

	public FeelParseResult<FeelParser.ExpressionRootContext> parseExpression(String source) {
		return parse(source, FeelParser::expressionRoot);
	}

	public FeelParseResult<FeelParser.UnaryTestsRootContext> parseUnaryTests(String source) {
		return parse(source, FeelParser::unaryTestsRoot);
	}

	public FeelParseResult<FeelParser.TextualExpressionsRootContext> parseTextualExpressions(String source) {
		return parse(source, FeelParser::textualExpressionsRoot);
	}

	public FeelParseResult<FeelParser.TypeRootContext> parseType(String source) {
		return parse(source, FeelParser::typeRoot);
	}

	public FeelParser.ExpressionRootContext requireExpression(String source) {
		FeelParseResult<FeelParser.ExpressionRootContext> result = parseExpression(source);
		if (!result.isValid()) {
			throw new FeelParseException(source, result.diagnostics());
		}
		return result.tree();
	}

	public FeelParser.UnaryTestsRootContext requireUnaryTests(String source) {
		FeelParseResult<FeelParser.UnaryTestsRootContext> result = parseUnaryTests(source);
		if (!result.isValid()) {
			throw new FeelParseException(source, result.diagnostics());
		}
		return result.tree();
	}

	public FeelParsed parseExpressionAst(String source) {
		return new FeelAstBuilder().build(requireExpression(source));
	}

	public UnaryTestParsed parseUnaryTestsAst(String source) {
		return new FeelAstBuilder().build(requireUnaryTests(source));
	}

	private static <T extends ParserRuleContext> FeelParseResult<T> parse(String source,
			Function<FeelParser, T> startRule) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(startRule, "startRule");

		FeelErrorListener lexerErrors = new FeelErrorListener();
		FeelLexer lexer = new FeelLexer(CharStreams.fromString(source));
		lexer.removeErrorListeners();
		lexer.addErrorListener(lexerErrors);

		CommonTokenStream tokens = new CommonTokenStream(lexer);
		FeelErrorListener parserErrors = new FeelErrorListener();
		FeelParser parser = new FeelParser(tokens);
		parser.removeErrorListeners();
		parser.addErrorListener(parserErrors);

		T tree = startRule.apply(parser);

		List<FeelDiagnostic> diagnostics = new ArrayList<>();
		diagnostics.addAll(lexerErrors.diagnostics());
		diagnostics.addAll(parserErrors.diagnostics());
		return new FeelParseResult<>(tree, diagnostics);
	}
}

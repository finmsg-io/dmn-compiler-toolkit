// Generated from io/finmsg/dmn/feel/parser/FeelParser.g4 by ANTLR 4.13.2
package io.finmsg.dmn.feel.parser;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link FeelParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface FeelParserVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link FeelParser#expressionRoot}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpressionRoot(FeelParser.ExpressionRootContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#unaryTestsRoot}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryTestsRoot(FeelParser.UnaryTestsRootContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#textualExpressionsRoot}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTextualExpressionsRoot(FeelParser.TextualExpressionsRootContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#typeRoot}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeRoot(FeelParser.TypeRootContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(FeelParser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#textualExpressions}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTextualExpressions(FeelParser.TextualExpressionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#textualExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTextualExpression(FeelParser.TextualExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#forExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForExpression(FeelParser.ForExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#iterationContext}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIterationContext(FeelParser.IterationContextContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#ifExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfExpression(FeelParser.IfExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#quantifiedExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQuantifiedExpression(FeelParser.QuantifiedExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#iterationBinding}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIterationBinding(FeelParser.IterationBindingContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#disjunction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDisjunction(FeelParser.DisjunctionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#conjunction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConjunction(FeelParser.ConjunctionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#comparison}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparison(FeelParser.ComparisonContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#comparisonSuffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonSuffix(FeelParser.ComparisonSuffixContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#comparisonOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonOperator(FeelParser.ComparisonOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#additiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditiveExpression(FeelParser.AdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#multiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplicativeExpression(FeelParser.MultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#exponentiationExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExponentiationExpression(FeelParser.ExponentiationExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#arithmeticNegation}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithmeticNegation(FeelParser.ArithmeticNegationContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#instanceOfExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInstanceOfExpression(FeelParser.InstanceOfExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#postfixExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPostfixExpression(FeelParser.PostfixExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#postfixPart}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPostfixPart(FeelParser.PostfixPartContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#primaryExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimaryExpression(FeelParser.PrimaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#parameters}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameters(FeelParser.ParametersContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#namedParameters}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNamedParameters(FeelParser.NamedParametersContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#namedParameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNamedParameter(FeelParser.NamedParameterContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#positionalParameters}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPositionalParameters(FeelParser.PositionalParametersContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#functionDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionDefinition(FeelParser.FunctionDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#formalParameters}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFormalParameters(FeelParser.FormalParametersContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#formalParameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFormalParameter(FeelParser.FormalParameterContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#parameterName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameterName(FeelParser.ParameterNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#list}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitList(FeelParser.ListContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#context}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContext(FeelParser.ContextContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#contextEntry}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContextEntry(FeelParser.ContextEntryContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#key}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitKey(FeelParser.KeyContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#keySegment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitKeySegment(FeelParser.KeySegmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#unaryTests}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryTests(FeelParser.UnaryTestsContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#negatedUnaryTests}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNegatedUnaryTests(FeelParser.NegatedUnaryTestsContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#positiveUnaryTests}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPositiveUnaryTests(FeelParser.PositiveUnaryTestsContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#positiveUnaryTest}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPositiveUnaryTest(FeelParser.PositiveUnaryTestContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#simplePositiveUnaryTest}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSimplePositiveUnaryTest(FeelParser.SimplePositiveUnaryTestContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#interval}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInterval(FeelParser.IntervalContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#intervalStart}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntervalStart(FeelParser.IntervalStartContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#intervalEnd}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntervalEnd(FeelParser.IntervalEndContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#openIntervalStart}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOpenIntervalStart(FeelParser.OpenIntervalStartContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#closedIntervalStart}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClosedIntervalStart(FeelParser.ClosedIntervalStartContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#openIntervalEnd}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOpenIntervalEnd(FeelParser.OpenIntervalEndContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#closedIntervalEnd}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClosedIntervalEnd(FeelParser.ClosedIntervalEndContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#rangeLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRangeLiteral(FeelParser.RangeLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#endpoint}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEndpoint(FeelParser.EndpointContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral(FeelParser.LiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#atLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtLiteral(FeelParser.AtLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#name}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitName(FeelParser.NameContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#nameSegment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNameSegment(FeelParser.NameSegmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#qualifiedName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQualifiedName(FeelParser.QualifiedNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(FeelParser.TypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#contextTypeEntry}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContextTypeEntry(FeelParser.ContextTypeEntryContext ctx);
	/**
	 * Visit a parse tree produced by {@link FeelParser#typeList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeList(FeelParser.TypeListContext ctx);
}
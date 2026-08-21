package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.FeelParserFacade;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.Expression;
import io.finmsg.dmn.model.DecisionTableExpression;
import io.finmsg.dmn.model.ForExpression;
import io.finmsg.dmn.model.FunctionCall;
import io.finmsg.dmn.model.ItemComponent;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.NamedTypeReference;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.QuantifiedExpression;
import io.finmsg.dmn.model.Quantifier;
import io.finmsg.dmn.model.TypeReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeelTypeAnalyzerTest {

	private final FeelParserFacade parser = new FeelParserFacade();
	private final FeelTypeAnalyzer analyzer = new FeelTypeAnalyzer();

	@Test
	void infersLiteralTypes() {
		assertLiteralType("10", BuiltinType.BUILTIN_TYPE_NUMBER);
		assertLiteralType("\"text\"", BuiltinType.BUILTIN_TYPE_STRING);
		assertLiteralType("true", BuiltinType.BUILTIN_TYPE_BOOLEAN);
		assertLiteralType("null", BuiltinType.BUILTIN_TYPE_NULL);
		assertLiteralType("@\"2026-08-01\"", BuiltinType.BUILTIN_TYPE_DATE);
		assertLiteralType("@\"12:30:00\"", BuiltinType.BUILTIN_TYPE_TIME);
		assertLiteralType("@\"2026-08-01T12:30:00\"", BuiltinType.BUILTIN_TYPE_DATE_AND_TIME);
		assertLiteralType("@\"P2D\"", BuiltinType.BUILTIN_TYPE_DURATION);
	}

	@Test
	void infersTrafficViolationSpeedDifferenceAsNumber() {
		Expression expression = parse("Violation.ActualSpeed - Violation.SpeedLimit");
		FeelTypeEnvironment environment = new FeelTypeEnvironment(Map.of("Violation", named("tViolation")),
				Map.of("tViolation",
						itemDefinition("tViolation", component("ActualSpeed", builtin(BuiltinType.BUILTIN_TYPE_NUMBER)),
								component("SpeedLimit", builtin(BuiltinType.BUILTIN_TYPE_NUMBER)))));

		FeelTypeAnalysisResult result = analyzer.analyze(expression, environment);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.expression()).isNotSameAs(expression);
		assertThat(expression.hasInferredType()).isFalse();
		assertThat(result.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
		assertThat(result.expression().getBinary().getLeft().getInferredType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
		assertThat(result.expression().getBinary().getRight().getInferredType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	@Test
	void infersTrafficViolationSuspensionExpression() {
		Expression expression = parse("if TotalPoints >= 20 then \"Yes\" else \"No\"");
		FeelTypeEnvironment environment = new FeelTypeEnvironment(
				Map.of("TotalPoints", builtin(BuiltinType.BUILTIN_TYPE_NUMBER)), Map.of());

		FeelTypeAnalysisResult result = analyzer.analyze(expression, environment);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_STRING));
		assertThat(result.expression().getIfExpression().getCondition().getInferredType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN));
		assertThat(result.expression().getIfExpression().getThenExpression().getInferredType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_STRING));
	}

	@Test
	void infersTrafficViolationTotalPointsAsNumber() {
		Expression expression = parse("Driver.Points + Fine.Points");
		FeelTypeEnvironment environment = new FeelTypeEnvironment(
				Map.of("Driver", named("tDriver"), "Fine", named("tFine")),
				Map.of("tDriver",
						itemDefinition("tDriver", component("Points", builtin(BuiltinType.BUILTIN_TYPE_NUMBER))),
						"tFine",
						itemDefinition("tFine", component("Points", builtin(BuiltinType.BUILTIN_TYPE_NUMBER)))));

		FeelTypeAnalysisResult result = analyzer.analyze(expression, environment);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	@Test
	void reportsInvalidArithmeticOperands() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("\"abc\" - 10"), FeelTypeEnvironment.empty(),
				"decision[Test]/logic", nullLocation());

		assertThat(result.isSuccess()).isFalse();
		assertThat(result.diagnostics()).extracting(DmnSemanticDiagnostic::code)
				.containsExactly("INVALID_OPERAND_TYPES");
		assertThat(result.diagnostics().getFirst().path()).isEqualTo("decision[Test]/logic");
		assertThat(result.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_ANY));
	}

	@Test
	void acceptsNullPropagationInArithmeticAndLogicalExpressions() {
		FeelTypeAnalysisResult arithmetic = analyzer.analyze(parse("1 + null"), FeelTypeEnvironment.empty());
		FeelTypeAnalysisResult logical = analyzer.analyze(parse("true and null"), FeelTypeEnvironment.empty());

		assertThat(arithmetic.diagnostics()).isEmpty();
		assertThat(arithmetic.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NULL));
		assertThat(logical.diagnostics()).isEmpty();
		assertThat(logical.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN));
	}

	@Test
	void infersTemporalAndDurationArithmetic() {
		FeelTypeAnalysisResult datePlusDuration = analyzer.analyze(parse("@\"2026-08-01\" + @\"P1D\""),
				FeelTypeEnvironment.empty());
		FeelTypeAnalysisResult dateDifference = analyzer.analyze(parse("@\"2026-08-02\" - @\"2026-08-01\""),
				FeelTypeEnvironment.empty());
		FeelTypeAnalysisResult scaledDuration = analyzer.analyze(parse("@\"P1D\" * 2"), FeelTypeEnvironment.empty());

		assertThat(datePlusDuration.diagnostics()).isEmpty();
		assertThat(datePlusDuration.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_DATE));
		assertThat(dateDifference.diagnostics()).isEmpty();
		assertThat(dateDifference.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_DURATION));
		assertThat(scaledDuration.diagnostics()).isEmpty();
		assertThat(scaledDuration.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_DURATION));
	}

	@Test
	void reportsUnknownInstanceOfType() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("1 instance of MissingType"),
				FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).extracting(DmnSemanticDiagnostic::code).containsExactly("UNKNOWN_TYPE");
	}

	@Test
	void resolvesDecisionTableExpressionContractsById() {
		Expression reference = Expression.newBuilder()
				.setDecisionTable(DecisionTableExpression.newBuilder().setDecisionTableId("table-id")).build();
		FeelTypeEnvironment environment = new FeelTypeEnvironment(Map.of(), Map.of(),
				Map.of("table-id", List.of(builtin(BuiltinType.BUILTIN_TYPE_NUMBER))));

		FeelTypeAnalysisResult resolved = analyzer.analyze(reference, environment);
		FeelTypeAnalysisResult unknown = analyzer.analyze(
				reference.toBuilder()
						.setDecisionTable(DecisionTableExpression.newBuilder().setDecisionTableId("missing")).build(),
				environment);

		assertThat(resolved.diagnostics()).isEmpty();
		assertThat(resolved.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
		assertThat(unknown.diagnostics()).extracting(DmnSemanticDiagnostic::code)
				.containsExactly("UNKNOWN_DECISION_TABLE_REFERENCE");
	}

	@Test
	void supportsLegacySingleBindingLoopRepresentations() {
		Expression legacyFor = Expression.newBuilder().setForExpression(
				ForExpression.newBuilder().setVariable("x").setIn(parse("[1, 2]")).setReturnExpression(parse("x + 1")))
				.build();
		Expression legacyQuantified = Expression.newBuilder()
				.setQuantified(QuantifiedExpression.newBuilder().setQuantifier(Quantifier.QUANTIFIER_SOME)
						.setVariable("x").setIn(parse("[1, 2]")).setSatisfies(parse("x > 1")))
				.build();

		FeelTypeAnalysisResult forResult = analyzer.analyze(legacyFor, FeelTypeEnvironment.empty());
		FeelTypeAnalysisResult quantifiedResult = analyzer.analyze(legacyQuantified, FeelTypeEnvironment.empty());

		assertThat(forResult.diagnostics()).isEmpty();
		assertThat(forResult.expression().getInferredType()).isEqualTo(listOf(BuiltinType.BUILTIN_TYPE_NUMBER));
		assertThat(quantifiedResult.diagnostics()).isEmpty();
		assertThat(quantifiedResult.expression().getInferredType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN));
	}

	@Test
	void reportsNonBooleanIfConditionAndIncompatibleBranches() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("if 1 then \"yes\" else 0"),
				FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).extracting(DmnSemanticDiagnostic::code).containsExactly("INVALID_OPERAND_TYPE",
				"INCOMPATIBLE_BRANCH_TYPES");
		assertThat(result.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_ANY));
	}

	@Test
	void resolvesBuiltInFunctionCalls() {
		assertFunctionType(functionCall("not", parse("true")), BuiltinType.BUILTIN_TYPE_BOOLEAN);
		assertFunctionType("string(10)", BuiltinType.BUILTIN_TYPE_STRING);
		assertFunctionType("count([1, 2, 3])", BuiltinType.BUILTIN_TYPE_NUMBER);
		assertFunctionType("sum([1, 2, 3])", BuiltinType.BUILTIN_TYPE_NUMBER);
		assertFunctionType("date(2026, 8, 1)", BuiltinType.BUILTIN_TYPE_DATE);
		assertFunctionType("duration(\"P2D\")", BuiltinType.BUILTIN_TYPE_DURATION);
	}

	@Test
	void resolvesAndValidatesParsedNotFunctionCalls() {
		assertThat(parse("not(true)").hasInvocation()).isTrue();
		assertFunctionType("not(true)", BuiltinType.BUILTIN_TYPE_BOOLEAN);
		assertFunctionType("not(true, false)", BuiltinType.BUILTIN_TYPE_ANY);
		assertFunctionType("not(1)", BuiltinType.BUILTIN_TYPE_ANY);
	}

	@Test
	void reportsFunctionCallErrors() {
		assertFunctionError(functionCall("unknownFn", parse("1")), "UNKNOWN_FUNCTION");
	}

	@Test
	void resolvesForVariablesAndSequentialIterations() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("for x in [1, 2], y in [x + 1] return y * 2"),
				FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getInferredType()).isEqualTo(listOf(BuiltinType.BUILTIN_TYPE_NUMBER));
		assertThat(result.expression().getForExpression().getReturnExpression().getInferredType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	@Test
	void resolvesQuantifiedVariables() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("some x in [1, 2], y in [x + 1] satisfies y > x"),
				FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN));
	}

	@Test
	void exposesEarlierContextEntriesToLaterEntries() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("{a: 1, b: a + 1}"), FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getContext().getEntries(1).getExpression().getInferredType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	@Test
	void resolvesTypedFunctionParameters() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("function(x: number) x + 1"),
				FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getFunctionDefinition().getBody().getInferredType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
		assertThat(result.expression().getInferredType().getFunction().getParameterType(0))
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
		assertThat(result.expression().getInferredType().getFunction().getReturnType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	@Test
	void doesNotLeakNestedVariables() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("[for x in [1] return x, x]"),
				FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).extracting(DmnSemanticDiagnostic::code).containsExactly("UNKNOWN_NAME");
	}

	@Test
	void infersRangeElementTypes() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("[1..10]"), FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getInferredType().getRange().getElementType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	@Test
	void infersStructuralContextTypes() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("{amount: 100, valid: true}"),
				FeelTypeEnvironment.empty());
		FeelTypeAnalysisResult property = analyzer.analyze(parse("({amount: 100}).amount"),
				FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getInferredType().getContext().getEntries(0).getName()).isEqualTo("amount");
		assertThat(result.expression().getInferredType().getContext().getEntries(0).getType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
		assertThat(result.expression().getInferredType().getContext().getEntries(1).getType())
				.isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN));
		assertThat(property.diagnostics()).isEmpty();
		assertThat(property.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	@Test
	void infersIndexAndPredicateFilterTypes() {
		FeelTypeAnalysisResult index = analyzer.analyze(parse("[1, 2][1]"), FeelTypeEnvironment.empty());
		FeelTypeAnalysisResult predicate = analyzer.analyze(parse("[1, 2][item > 1]"), FeelTypeEnvironment.empty());

		assertThat(index.diagnostics()).isEmpty();
		assertThat(index.expression().getInferredType()).isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
		assertThat(predicate.diagnostics()).isEmpty();
		assertThat(predicate.expression().getInferredType()).isEqualTo(listOf(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	@Test
	void projectsPropertiesAcrossLists() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("[{amount: 10}, {amount: 20}].amount"),
				FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getInferredType()).isEqualTo(listOf(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	@Test
	void exposesStructuredElementMembersInsideFilterPredicates() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("[{age: 17}, {age: 20}][age >= 18]"),
				FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getInferredType().hasList()).isTrue();
	}

	@Test
	void rejectsNonOrderableRangeAndBetweenOperands() {
		FeelTypeAnalysisResult range = analyzer.analyze(parse("[true..false]"), FeelTypeEnvironment.empty());
		FeelTypeAnalysisResult between = analyzer.analyze(parse("true between false and true"),
				FeelTypeEnvironment.empty());

		assertThat(range.diagnostics()).extracting(DmnSemanticDiagnostic::code)
				.containsExactly("INVALID_RANGE_ENDPOINT");
		assertThat(between.diagnostics()).extracting(DmnSemanticDiagnostic::code)
				.containsExactly("INVALID_BETWEEN_OPERAND");
	}

	@Test
	void validatesInExpressionUnaryTestsAgainstSubjectType() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("\"value\" in (< 10)"), FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).extracting(DmnSemanticDiagnostic::code)
				.containsExactly("INCOMPATIBLE_UNARY_TEST");
	}

	@Test
	void infersRangeIterationVariables() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("for x in [1..3] return x + 1"),
				FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getInferredType()).isEqualTo(listOf(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	@Test
	void preservesConcreteListTypeAcrossNullElements() {
		FeelTypeAnalysisResult result = analyzer.analyze(parse("[1, null, 2]"), FeelTypeEnvironment.empty());

		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getInferredType()).isEqualTo(listOf(BuiltinType.BUILTIN_TYPE_NUMBER));
	}

	private Expression parse(String source) {
		return parser.parseExpressionAst(source).getAst();
	}

	private void assertLiteralType(String source, BuiltinType expected) {
		FeelTypeAnalysisResult result = analyzer.analyze(parse(source), FeelTypeEnvironment.empty());
		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getInferredType()).isEqualTo(builtin(expected));
	}

	private void assertFunctionType(String source, BuiltinType expected) {
		assertFunctionType(parse(source), expected);
	}

	private void assertFunctionType(Expression expression, BuiltinType expected) {
		FeelTypeAnalysisResult result = analyzer.analyze(expression, FeelTypeEnvironment.empty());
		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.expression().getInferredType()).isEqualTo(builtin(expected));
	}

	private void assertFunctionError(Expression expression, String code) {
		FeelTypeAnalysisResult result = analyzer.analyze(expression, FeelTypeEnvironment.empty());
		assertThat(result.diagnostics()).extracting(DmnSemanticDiagnostic::code).containsExactly(code);
	}

	private static Expression functionCall(String name, Expression... arguments) {
		return Expression.newBuilder()
				.setFunctionCall(
						FunctionCall.newBuilder().setFunction(name).addAllArguments(java.util.List.of(arguments)))
				.build();
	}

	private static ItemDefinition itemDefinition(String name, ItemComponent... components) {
		return ItemDefinition.newBuilder().setNode(Node.newBuilder().setName(name))
				.addAllComponents(java.util.List.of(components)).build();
	}

	private static ItemComponent component(String name, TypeReference type) {
		return ItemComponent.newBuilder().setNode(Node.newBuilder().setName(name)).setType(type).build();
	}

	private static TypeReference named(String name) {
		return TypeReference.newBuilder().setNamed(NamedTypeReference.newBuilder().setName(name)).build();
	}

	private static TypeReference builtin(BuiltinType type) {
		return TypeReference.newBuilder().setBuiltin(type).build();
	}

	private static TypeReference listOf(BuiltinType type) {
		return TypeReference.newBuilder()
				.setList(io.finmsg.dmn.model.ListTypeReference.newBuilder().setElementType(builtin(type))).build();
	}

	private static io.finmsg.dmn.model.SourceLocation nullLocation() {
		return io.finmsg.dmn.model.SourceLocation.getDefaultInstance();
	}
}

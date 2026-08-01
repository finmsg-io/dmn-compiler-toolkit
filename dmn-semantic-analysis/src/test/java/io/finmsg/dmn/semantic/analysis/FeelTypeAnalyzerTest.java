package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.FeelParserFacade;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.Expression;
import io.finmsg.dmn.model.FunctionCall;
import io.finmsg.dmn.model.ItemComponent;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.NamedTypeReference;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.TypeReference;
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
    FeelTypeEnvironment environment = new FeelTypeEnvironment(
        Map.of("Violation", named("tViolation")),
        Map.of("tViolation", itemDefinition("tViolation",
            component("ActualSpeed", builtin(BuiltinType.BUILTIN_TYPE_NUMBER)),
            component("SpeedLimit", builtin(BuiltinType.BUILTIN_TYPE_NUMBER)))));

    FeelTypeAnalysisResult result = analyzer.analyze(expression, environment);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.expression()).isNotSameAs(expression);
    assertThat(expression.hasInferredType()).isFalse();
    assertThat(result.expression().getInferredType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
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
    assertThat(result.expression().getInferredType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_STRING));
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
        Map.of(
            "tDriver", itemDefinition("tDriver",
                component("Points", builtin(BuiltinType.BUILTIN_TYPE_NUMBER))),
            "tFine", itemDefinition("tFine",
                component("Points", builtin(BuiltinType.BUILTIN_TYPE_NUMBER)))));

    FeelTypeAnalysisResult result = analyzer.analyze(expression, environment);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.expression().getInferredType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
  }

  @Test
  void reportsInvalidArithmeticOperands() {
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("\"abc\" - 10"), FeelTypeEnvironment.empty(), "decision[Test]/logic", nullLocation());

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("INVALID_OPERAND_TYPES");
    assertThat(result.diagnostics().getFirst().path()).isEqualTo("decision[Test]/logic");
    assertThat(result.expression().getInferredType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_ANY));
  }

  @Test
  void reportsNonBooleanIfConditionAndIncompatibleBranches() {
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("if 1 then \"yes\" else 0"), FeelTypeEnvironment.empty());

    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("INVALID_OPERAND_TYPE", "INCOMPATIBLE_BRANCH_TYPES");
    assertThat(result.expression().getInferredType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_ANY));
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
    assertFunctionError(parse("not(true, false)"), "INVALID_ARGUMENT_COUNT");
    assertFunctionError(parse("not(1)"), "INVALID_ARGUMENT_TYPE");
  }

  @Test
  void reportsFunctionCallErrors() {
    assertFunctionError(functionCall("unknownFn", parse("1")), "UNKNOWN_FUNCTION");
    assertFunctionError(functionCall("not", parse("true"), parse("false")),
        "INVALID_ARGUMENT_COUNT");
    assertFunctionError(functionCall("not", parse("1")), "INVALID_ARGUMENT_TYPE");
    assertFunctionError(functionCall("sum", parse("[\"a\"]")), "INVALID_ARGUMENT_TYPE");
  }

  @Test
  void resolvesForVariablesAndSequentialIterations() {
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("for x in [1, 2], y in [x + 1] return y * 2"),
        FeelTypeEnvironment.empty());

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.expression().getInferredType())
        .isEqualTo(listOf(BuiltinType.BUILTIN_TYPE_NUMBER));
    assertThat(result.expression().getForExpression().getReturnExpression().getInferredType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
  }

  @Test
  void resolvesQuantifiedVariables() {
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("some x in [1, 2], y in [x + 1] satisfies y > x"),
        FeelTypeEnvironment.empty());

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.expression().getInferredType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN));
  }

  @Test
  void exposesEarlierContextEntriesToLaterEntries() {
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("{a: 1, b: a + 1}"), FeelTypeEnvironment.empty());

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.expression().getContext().getEntries(1)
        .getExpression().getInferredType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
  }

  @Test
  void resolvesTypedFunctionParameters() {
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("function(x: number) x + 1"), FeelTypeEnvironment.empty());

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
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("[for x in [1] return x, x]"), FeelTypeEnvironment.empty());

    assertThat(result.diagnostics())
        .extracting(DmnSemanticDiagnostic::code)
        .containsExactly("UNKNOWN_NAME");
  }

  @Test
  void infersRangeElementTypes() {
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("[1..10]"), FeelTypeEnvironment.empty());

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.expression().getInferredType().getRange().getElementType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
  }

  @Test
  void infersStructuralContextTypes() {
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("{amount: 100, valid: true}"), FeelTypeEnvironment.empty());
    FeelTypeAnalysisResult property = analyzer.analyze(
        parse("({amount: 100}).amount"), FeelTypeEnvironment.empty());

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.expression().getInferredType().getContext().getEntries(0).getName())
        .isEqualTo("amount");
    assertThat(result.expression().getInferredType().getContext().getEntries(0).getType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
    assertThat(result.expression().getInferredType().getContext().getEntries(1).getType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_BOOLEAN));
    assertThat(property.diagnostics()).isEmpty();
    assertThat(property.expression().getInferredType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
  }

  @Test
  void infersIndexAndPredicateFilterTypes() {
    FeelTypeAnalysisResult index = analyzer.analyze(
        parse("[1, 2][1]"), FeelTypeEnvironment.empty());
    FeelTypeAnalysisResult predicate = analyzer.analyze(
        parse("[1, 2][item > 1]"), FeelTypeEnvironment.empty());

    assertThat(index.diagnostics()).isEmpty();
    assertThat(index.expression().getInferredType())
        .isEqualTo(builtin(BuiltinType.BUILTIN_TYPE_NUMBER));
    assertThat(predicate.diagnostics()).isEmpty();
    assertThat(predicate.expression().getInferredType())
        .isEqualTo(listOf(BuiltinType.BUILTIN_TYPE_NUMBER));
  }

  @Test
  void infersRangeIterationVariables() {
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("for x in [1..3] return x + 1"), FeelTypeEnvironment.empty());

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.expression().getInferredType())
        .isEqualTo(listOf(BuiltinType.BUILTIN_TYPE_NUMBER));
  }

  @Test
  void preservesConcreteListTypeAcrossNullElements() {
    FeelTypeAnalysisResult result = analyzer.analyze(
        parse("[1, null, 2]"), FeelTypeEnvironment.empty());

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.expression().getInferredType())
        .isEqualTo(listOf(BuiltinType.BUILTIN_TYPE_NUMBER));
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
        .setFunctionCall(FunctionCall.newBuilder()
            .setFunction(name)
            .addAllArguments(java.util.List.of(arguments)))
        .build();
  }

  private static ItemDefinition itemDefinition(String name, ItemComponent... components) {
    return ItemDefinition.newBuilder()
        .setNode(Node.newBuilder().setName(name))
        .addAllComponents(java.util.List.of(components))
        .build();
  }

  private static ItemComponent component(String name, TypeReference type) {
    return ItemComponent.newBuilder()
        .setNode(Node.newBuilder().setName(name))
        .setType(type)
        .build();
  }

  private static TypeReference named(String name) {
    return TypeReference.newBuilder()
        .setNamed(NamedTypeReference.newBuilder().setName(name))
        .build();
  }

  private static TypeReference builtin(BuiltinType type) {
    return TypeReference.newBuilder().setBuiltin(type).build();
  }

  private static TypeReference listOf(BuiltinType type) {
    return TypeReference.newBuilder()
        .setList(io.finmsg.dmn.model.ListTypeReference.newBuilder()
            .setElementType(builtin(type)))
        .build();
  }

  private static io.finmsg.dmn.model.SourceLocation nullLocation() {
    return io.finmsg.dmn.model.SourceLocation.getDefaultInstance();
  }
}

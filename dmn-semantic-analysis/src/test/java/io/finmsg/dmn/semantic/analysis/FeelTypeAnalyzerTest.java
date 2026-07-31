package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.FeelParserFacade;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.Expression;
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

  private Expression parse(String source) {
    return parser.parseExpressionAst(source).getAst();
  }

  private void assertLiteralType(String source, BuiltinType expected) {
    FeelTypeAnalysisResult result = analyzer.analyze(parse(source), FeelTypeEnvironment.empty());
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.expression().getInferredType()).isEqualTo(builtin(expected));
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

  private static io.finmsg.dmn.model.SourceLocation nullLocation() {
    return io.finmsg.dmn.model.SourceLocation.getDefaultInstance();
  }
}

package io.finmsg.dmn.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DmnRuntimeTest {
  private static final RuntimeType NUMBER = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
  private static final RuntimeType BOOLEAN = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);

  @Test
  void evaluatesInputsAndDecisionsInDependencyOrder() {
    RuntimeInput input = new RuntimeInput(1, 0, NUMBER);
    RuntimeDecision first = new RuntimeDecision(2, 1, NUMBER, List.of(1), Optional.of(
        new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY,
            new RuntimeValueReference(0, NUMBER), number("2"), NUMBER)));
    RuntimeDecision second = new RuntimeDecision(3, 2, NUMBER, List.of(2), Optional.of(
        new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD,
            new RuntimeValueReference(1, NUMBER), number("1"), NUMBER)));
    RuntimeModel model = new RuntimeModel(List.of(input), List.of(first, second), List.of(),
        List.of(2, 3), 3);

    DmnEvaluationResult result = new DmnRuntime().evaluate(model, Map.of(0, new BigDecimal("20")));

    assertThat((BigDecimal) result.decisionValue(3)).isEqualByComparingTo(new BigDecimal("41"));
  }

  @Test
  void invokesBkmClosureUsingPersistedParameterSlots() {
    RuntimeFunctionDefinition function = new RuntimeFunctionDefinition(
        List.of(new RuntimeFunctionParameter("x", 0, NUMBER)),
        Optional.of(new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD,
            new RuntimeLocalReference(0, NUMBER), number("10"), NUMBER)), false, 1,
        RuntimeType.function(List.of(NUMBER), NUMBER));
    RuntimeBkm bkm = new RuntimeBkm(1, 0, function.type(), List.of(), RuntimeFunctionKind.FEEL,
        Optional.of(function));
    RuntimeInvocationExpression invocation = new RuntimeInvocationExpression(Optional.empty(),
        Optional.of(new RuntimeValueReference(0, function.type())), List.of(), List.of(number("5")), NUMBER);
    RuntimeDecision decision = new RuntimeDecision(2, 1, NUMBER, List.of(1), Optional.of(invocation));
    RuntimeModel model = new RuntimeModel(List.of(), List.of(decision), List.of(bkm), List.of(1, 2), 2);

    assertThat((BigDecimal) new DmnRuntime().evaluate(model, Map.of()).decisionValue(2))
        .isEqualByComparingTo(new BigDecimal("15"));
  }

  @Test
  void executesUniqueDecisionTableAndUnaryTests() {
    RuntimeDecisionTable table = new RuntimeDecisionTable(RuntimeHitPolicy.UNIQUE, Optional.empty(),
        List.of(new RuntimeDecisionTableInput(new RuntimeValueReference(0, NUMBER), Optional.empty(), NUMBER)),
        List.of(new RuntimeDecisionTableOutput(Optional.of("result"), NUMBER, Optional.empty(), Optional.empty())),
        List.of(
            new RuntimeDecisionTableRule(0, List.of(comparison(RuntimeUnaryTestOperator.LESS, "18")),
                List.of(number("0")), List.of()),
            new RuntimeDecisionTableRule(1, List.of(comparison(RuntimeUnaryTestOperator.GREATER_EQUAL, "18")),
                List.of(number("1")), List.of())), 0);
    RuntimeModel model = new RuntimeModel(List.of(new RuntimeInput(1, 0, NUMBER)),
        List.of(new RuntimeDecision(2, 1, NUMBER, List.of(1), Optional.empty(), Optional.of(table), 0)),
        List.of(), List.of(2), 2);

    assertThat((BigDecimal) new DmnRuntime().evaluate(model, Map.of(0, new BigDecimal("21"))).decisionValue(2))
        .isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void evaluatesSequentialContextsAndIndexedPaths() {
    RuntimeType contextType = RuntimeType.contextFields(List.of(
        new RuntimeField(0, "base", NUMBER), new RuntimeField(1, "total", NUMBER)));
    RuntimeContextExpression context = new RuntimeContextExpression(List.of(
        new RuntimeContextEntry("base", 0, number("4")),
        new RuntimeContextEntry("total", 1, new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD,
            new RuntimeLocalReference(0, NUMBER), number("3"), NUMBER))), contextType);
    RuntimePathExpression path = new RuntimePathExpression(context, "total", 1, NUMBER);
    RuntimeDecision decision = new RuntimeDecision(1, 0, NUMBER, List.of(), Optional.of(path),
        Optional.empty(), 2);

    assertThat((BigDecimal) new DmnRuntime().evaluate(
        new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(1), 1), Map.of()).decisionValue(1))
        .isEqualByComparingTo(new BigDecimal("7"));
  }

  @Test
  void rejectsMissingInputsAndExternalFunctionsExplicitly() {
    RuntimeModel missing = new RuntimeModel(List.of(new RuntimeInput(1, 0, NUMBER)), List.of(),
        List.of(), List.of(), 1);
    assertThatThrownBy(() -> new DmnRuntime().evaluate(missing, Map.of()))
        .isInstanceOf(DmnEvaluationException.class).hasMessageContaining("Missing input");

    RuntimeFunctionDefinition external = new RuntimeFunctionDefinition(List.of(), Optional.empty(), true, 0,
        RuntimeType.function(List.of(), NUMBER));
    RuntimeBkm bkm = new RuntimeBkm(1, 0, external.type(), List.of(), RuntimeFunctionKind.JAVA,
        Optional.of(external));
    RuntimeModel model = new RuntimeModel(List.of(), List.of(), List.of(bkm), List.of(1), 1);
    assertThatThrownBy(() -> new DmnRuntime().evaluate(model, Map.of()))
        .isInstanceOf(DmnEvaluationException.class).hasMessageContaining("External BKM");
  }

  @Test
  void evaluatesThreeValuedLogicAndNullPropagation() {
    RuntimeConstant nullConst = new RuntimeConstant(RuntimeConstantKind.NULL, "null", RuntimeType.scalar(RuntimeTypeKind.NULL));
    RuntimeConstant trueConst = new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", BOOLEAN);
    RuntimeConstant falseConst = new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", BOOLEAN);

    // false AND null -> false
    RuntimeDecision d1 = new RuntimeDecision(1, 0, BOOLEAN, List.of(), Optional.of(
        new RuntimeBinaryExpression(RuntimeBinaryOperator.AND, falseConst, nullConst, BOOLEAN)));
    // true AND null -> null
    RuntimeDecision d2 = new RuntimeDecision(2, 1, BOOLEAN, List.of(), Optional.of(
        new RuntimeBinaryExpression(RuntimeBinaryOperator.AND, trueConst, nullConst, BOOLEAN)));
    // true OR null -> true
    RuntimeDecision d3 = new RuntimeDecision(3, 2, BOOLEAN, List.of(), Optional.of(
        new RuntimeBinaryExpression(RuntimeBinaryOperator.OR, trueConst, nullConst, BOOLEAN)));
    // NOT(null) -> null
    RuntimeDecision d4 = new RuntimeDecision(4, 3, BOOLEAN, List.of(), Optional.of(
        new RuntimeUnaryExpression(RuntimeUnaryOperator.NOT, nullConst, BOOLEAN)));
    // 5 + null -> null
    RuntimeDecision d5 = new RuntimeDecision(5, 4, NUMBER, List.of(), Optional.of(
        new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, number("5"), nullConst, NUMBER)));
    // null = null -> true
    RuntimeDecision d6 = new RuntimeDecision(6, 5, BOOLEAN, List.of(), Optional.of(
        new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, nullConst, nullConst, BOOLEAN)));
    // 5 = null -> false
    RuntimeDecision d7 = new RuntimeDecision(7, 6, BOOLEAN, List.of(), Optional.of(
        new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, number("5"), nullConst, BOOLEAN)));

    RuntimeModel model = new RuntimeModel(List.of(), List.of(d1, d2, d3, d4, d5, d6, d7), List.of(),
        List.of(1, 2, 3, 4, 5, 6, 7), 7);
    DmnEvaluationResult result = new DmnRuntime().evaluate(model, Map.of());

    assertThat(result.decisionValue(1)).isEqualTo(Boolean.FALSE);
    assertThat(result.decisionValue(2)).isNull();
    assertThat(result.decisionValue(3)).isEqualTo(Boolean.TRUE);
    assertThat(result.decisionValue(4)).isNull();
    assertThat(result.decisionValue(5)).isNull();
    assertThat(result.decisionValue(6)).isEqualTo(Boolean.TRUE);
    assertThat(result.decisionValue(7)).isEqualTo(Boolean.FALSE);
  }

  @Test
  void executesCollectDecisionTableWithSumAggregation() {
    RuntimeDecisionTable table = new RuntimeDecisionTable(RuntimeHitPolicy.COLLECT, Optional.of(RuntimeAggregation.SUM),
        List.of(new RuntimeDecisionTableInput(new RuntimeValueReference(0, NUMBER), Optional.empty(), NUMBER)),
        List.of(new RuntimeDecisionTableOutput(Optional.of("score"), NUMBER, Optional.empty(), Optional.empty())),
        List.of(
            new RuntimeDecisionTableRule(0, List.of(comparison(RuntimeUnaryTestOperator.GREATER, "10")),
                List.of(number("100")), List.of()),
            new RuntimeDecisionTableRule(1, List.of(comparison(RuntimeUnaryTestOperator.GREATER, "20")),
                List.of(number("200")), List.of())), 0);
    RuntimeModel model = new RuntimeModel(List.of(new RuntimeInput(1, 0, NUMBER)),
        List.of(new RuntimeDecision(2, 1, NUMBER, List.of(1), Optional.empty(), Optional.of(table), 0)),
        List.of(), List.of(2), 2);

    DmnEvaluationResult result = new DmnRuntime().evaluate(model, Map.of(0, new BigDecimal("25")));
    assertThat((BigDecimal) result.decisionValue(2)).isEqualByComparingTo(new BigDecimal("300"));
  }

  private static RuntimeConstant number(String value) {
    return new RuntimeConstant(RuntimeConstantKind.NUMBER, value, NUMBER);
  }

  private static RuntimeUnaryTests comparison(RuntimeUnaryTestOperator operator, String endpoint) {
    return new RuntimeUnaryTests(false, false,
        List.of(new RuntimeComparisonUnaryTest(operator, number(endpoint))));
  }
}

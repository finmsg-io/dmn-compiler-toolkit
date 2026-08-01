package io.finmsg.dmn.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeIrOptimizerTest {
  private static final RuntimeType NUMBER = RuntimeType.scalar(RuntimeTypeKind.NUMBER);

  @Test
  void canonicalizesAndDeduplicatesTypedConstants() {
    RuntimeExpression expression = new RuntimeListExpression(List.of(
        constant(RuntimeConstantKind.NUMBER, "1.00", NUMBER),
        constant(RuntimeConstantKind.NUMBER, "1.0", NUMBER),
        constant(RuntimeConstantKind.DATE, "2026-08-01",
            RuntimeType.scalar(RuntimeTypeKind.DATE))),
        RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType.scalar(RuntimeTypeKind.ANY)));

    RuntimeOptimizedModel optimized = new RuntimeIrOptimizer().optimize(model(expression));

    assertThat(optimized.constantPool()).hasSize(2);
    assertThat(optimized.constantPool().get(0).value()).isEqualTo(
        new RuntimeCanonicalValue.NumberValue(BigDecimal.ONE));
    assertThat(optimized.constantPool().get(1).value()).isEqualTo(
        new RuntimeCanonicalValue.DateValue(LocalDate.of(2026, 8, 1)));
    assertThat(optimized.constantUses()).extracting(RuntimeConstantUse::constantPoolId)
        .containsExactly(0, 0, 1);
  }

  @Test
  void assignsStableIdsOnlyToRecognizedStaticBuiltins() {
    RuntimeExpression expression = new RuntimeListExpression(List.of(
        new RuntimeFunctionCall("abs", List.of(constant(
            RuntimeConstantKind.NUMBER, "-2", NUMBER)), NUMBER),
        new RuntimeInvocationExpression(Optional.of("sum"), Optional.empty(), List.of(),
            List.of(), NUMBER),
        new RuntimeFunctionCall("custom", List.of(), NUMBER)),
        RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType.scalar(RuntimeTypeKind.ANY)));

    RuntimeOptimizedModel optimized = new RuntimeIrOptimizer().optimize(model(expression));

    assertThat(optimized.builtinBindings()).extracting(
        binding -> binding.operation().id(),
        binding -> binding.operation().feelName())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(11, "abs"),
            org.assertj.core.groups.Tuple.tuple(8, "sum"));
  }

  @Test
  void rejectsMalformedTypedConstantsDuringOptimization() {
    assertThatThrownBy(() -> new RuntimeIrOptimizer().optimize(model(
        constant(RuntimeConstantKind.BOOLEAN, "TRUE", RuntimeType.scalar(
            RuntimeTypeKind.BOOLEAN)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BOOLEAN");
  }

  private static RuntimeConstant constant(
      RuntimeConstantKind kind, String value, RuntimeType type) {
    return new RuntimeConstant(kind, value, type);
  }

  private static RuntimeModel model(RuntimeExpression expression) {
    RuntimeDecision decision = new RuntimeDecision(0, 0, expression.type(), List.of(),
        Optional.of(expression), Optional.empty(), 0);
    return new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0), 1);
  }
}

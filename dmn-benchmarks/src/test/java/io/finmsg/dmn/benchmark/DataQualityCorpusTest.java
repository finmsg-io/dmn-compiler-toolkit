package io.finmsg.dmn.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.benchmark.provider.ReferenceModelRegistry;
import io.finmsg.dmn.ir.RuntimeDecision;
import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataQualityCorpusTest {

  @Test
  @DisplayName("Field Validation DMN: Valid email, IBAN, and SSN pass validation")
  void testFieldValidationValidPayload() throws Exception {
    ReferenceModelRegistry.CompiledModelHolder holder =
        ReferenceModelRegistry.loadFromClasspath("models/dq-field-validation.dmn");

    RuntimeModel model = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model();
    Map<Integer, Object> inputs = ReferenceModelRegistry.buildInterpreterSlotMap(holder, Map.of(
        "email", "john.doe@example.com",
        "iban", "DE89370400440532013000",
        "ssn", "123-45-6789"
    ));

    DmnEvaluationResult evalResult = new DmnRuntime().evaluate(model, inputs);

    assertThat(evalResult.decisionValue(findDecisionId(model, 1))).isEqualTo(true);
    assertThat(evalResult.decisionValue(findDecisionId(model, 2))).isEqualTo(true);
    assertThat(evalResult.decisionValue(findDecisionId(model, 3))).isEqualTo(true);
  }

  @Test
  @DisplayName("Field Validation DMN: Invalid email and IBAN fail validation")
  void testFieldValidationInvalidPayload() throws Exception {
    ReferenceModelRegistry.CompiledModelHolder holder =
        ReferenceModelRegistry.loadFromClasspath("models/dq-field-validation.dmn");

    RuntimeModel model = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model();
    Map<String, Object> inputParams = new HashMap<>();
    inputParams.put("email", null);
    inputParams.put("iban", null);
    inputParams.put("ssn", "123-45-6789");
    Map<Integer, Object> inputs = ReferenceModelRegistry.buildInterpreterSlotMap(holder, inputParams);

    DmnEvaluationResult evalResult = new DmnRuntime().evaluate(model, inputs);

    assertThat(evalResult.decisionValue(findDecisionId(model, 1))).isEqualTo(false);
    assertThat(evalResult.decisionValue(findDecisionId(model, 2))).isEqualTo(false);
    assertThat(evalResult.decisionValue(findDecisionId(model, 3))).isEqualTo(true);
  }

  @Test
  @DisplayName("Cross-Field Consistency DMN: Invoice total matching")
  void testCrossFieldConsistencyInvoiceMatching() throws Exception {
    ReferenceModelRegistry.CompiledModelHolder holder =
        ReferenceModelRegistry.loadFromClasspath("models/dq-cross-field-consistency.dmn");

    RuntimeModel model = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model();
    Map<Integer, Object> inputs = ReferenceModelRegistry.buildInterpreterSlotMap(holder, Map.of(
        "subtotal", new BigDecimal("100.00"),
        "tax", new BigDecimal("20.00"),
        "total", new BigDecimal("120.00")
    ));

    DmnEvaluationResult evalResult = new DmnRuntime().evaluate(model, inputs);

    assertThat(evalResult.decisionValue(findDecisionId(model, 1))).isEqualTo(true);
  }

  @Test
  @DisplayName("Data Quality Scoring DMN: Aggregate DQI score and PASS status")
  void testDataQualityScoringPass() throws Exception {
    ReferenceModelRegistry.CompiledModelHolder holder =
        ReferenceModelRegistry.loadFromClasspath("models/dq-scoring.dmn");

    RuntimeModel model = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model();
    Map<Integer, Object> inputs = ReferenceModelRegistry.buildInterpreterSlotMap(holder, Map.of(
        "fieldValid", true,
        "consistencyValid", true
    ));

    DmnEvaluationResult evalResult = new DmnRuntime().evaluate(model, inputs);

    assertThat(evalResult.decisionValue(findDecisionId(model, 1))).isEqualTo(new BigDecimal("100"));
    assertThat(evalResult.decisionValue(findDecisionId(model, 2))).isEqualTo("PASS");
  }

  private int findDecisionId(RuntimeModel model, int decisionIndex) {
    return model.decisions().get(decisionIndex - 1).id();
  }
}

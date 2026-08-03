package io.finmsg.dmn.tck;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnSemanticModel;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Temporary name-to-slot adapter from TCK cases to the compiler and interpreter. */
public final class DmnToolkitTckEngine {
  public TckExecutionResult execute(Path modelPath, TckTestCase testCase) throws IOException {
    DmnSource source = new DmnSource(
        new DmnSourceId(modelPath.toAbsolutePath().normalize().toUri()), Files.readAllBytes(modelPath));
    DmnCompilationResult compilation = new DmnCompiler().compile(source);
    if (!compilation.isSuccess()) {
      throw new TckExecutionException("Compilation failed for TCK case " + testCase.id() + ": "
          + compilation.diagnostics());
    }

    RuntimeNames names = runtimeNames(compilation);
    Map<Integer, Object> inputs = new LinkedHashMap<>();
    testCase.inputs().forEach((name, value) -> inputs.put(
        required(names.inputSlots(), name, "input", testCase.id()), value.runtimeValue()));
    DmnEvaluationResult evaluation = new DmnRuntime().evaluate(
        compilation.optimizedRuntimeModel().orElseThrow().model(), inputs);

    Map<String, Object> actual = new LinkedHashMap<>();
    testCase.expectedResults().keySet().forEach(name -> {
      int slot = required(names.decisionSlots(), name, "decision", testCase.id());
      actual.put(name, evaluation.value(slot));
    });
    return new TckExecutionResult(testCase.id(), actual);
  }

  private static RuntimeNames runtimeNames(DmnCompilationResult compilation) {
    Map<String, Integer> inputs = new LinkedHashMap<>();
    Map<String, Integer> decisions = new LinkedHashMap<>();
    int slot = 0;
    for (DmnSemanticModel model : compilation.semanticResult().models()) {
      for (DrgElement element : model.model().getDrgElementsList()) {
        if (element.hasInputData()) {
          putUnique(inputs, element.getInputData().getNode().getName(), slot++);
        } else if (element.hasDecision()) {
          putUnique(decisions, element.getDecision().getNode().getName(), slot++);
        } else if (element.hasBusinessKnowledgeModel()) {
          slot++;
        }
      }
    }
    return new RuntimeNames(inputs, decisions);
  }

  private static void putUnique(Map<String, Integer> values, String name, int slot) {
    if (values.putIfAbsent(name, slot) != null) {
      throw new TckExecutionException("Ambiguous DMN name '" + name + "' in compiled model set");
    }
  }

  private static int required(Map<String, Integer> values, String name, String kind, String caseId) {
    Integer slot = values.get(name);
    if (slot == null) throw new TckExecutionException(
        "TCK case " + caseId + " references unknown " + kind + " '" + name + "'");
    return slot;
  }

  private record RuntimeNames(Map<String, Integer> inputSlots, Map<String, Integer> decisionSlots) { }
}

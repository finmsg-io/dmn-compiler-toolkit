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
    return execute(source, testCase);
  }

  public TckExecutionResult execute(DmnSource source, TckTestCase testCase) {
    return execute(java.util.List.of(source), testCase);
  }

  public TckExecutionResult execute(java.util.List<DmnSource> sources, TckTestCase testCase) {
    if (sources.isEmpty()) {
      throw new IllegalArgumentException("No DMN sources provided");
    }
    DmnSource root = sources.get(0);
    io.finmsg.dmn.compiler.DmnModelResolver resolver = sources.size() > 1
        ? new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(sources.subList(1, sources.size()))
        : new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(java.util.List.of());
    DmnCompilationResult compilation = new DmnCompiler().compile(root, resolver);
    if (!compilation.isSuccess()) {
      throw new TckExecutionException("Compilation failed for TCK case " + testCase.id() + ": "
          + compilation.diagnostics());
    }

    RuntimeNames names = runtimeNames(compilation);
    Map<Integer, Object> inputs = new LinkedHashMap<>();
    compilation.optimizedRuntimeModel().orElseThrow().model().inputs()
        .forEach(input -> inputs.put(input.valueSlot(), null));
    testCase.inputs().forEach((name, value) -> {
      Integer slot = names.inputSlots().get(name);
      if (slot != null) {
        inputs.put(slot, value.runtimeValue());
      }
    });
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

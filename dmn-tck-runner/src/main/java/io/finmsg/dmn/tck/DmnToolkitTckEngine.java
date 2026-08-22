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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Temporary name-to-slot adapter from TCK cases to the compiler and
 * interpreter.
 */
public final class DmnToolkitTckEngine {
	public TckExecutionResult execute(Path modelPath, TckTestCase testCase) throws IOException {
		DmnSource source = new DmnSource(new DmnSourceId(modelPath.toAbsolutePath().normalize().toUri()),
				Files.readAllBytes(modelPath));
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
			throw new TckExecutionException(
					"Compilation failed for TCK case " + testCase.id() + ": " + compilation.diagnostics());
		}
		return execute(compilation, testCase);
	}

	public TckExecutionResult execute(DmnCompilationResult compilation, TckTestCase testCase) {
		RuntimeNames names = runtimeNames(compilation);
		io.finmsg.dmn.ir.RuntimeModel model = compilation.optimizedRuntimeModel().orElseThrow().model();

		if (testCase.invocableName().isPresent()) {
			String invocableName = testCase.invocableName().get();
			Integer bkmSlot = names.bkmSlots().get(invocableName);
			if (bkmSlot == null) {
				throw new TckExecutionException(
						"Unknown invocable '" + invocableName + "' in TCK case " + testCase.id());
			}
			io.finmsg.dmn.ir.RuntimeBkm targetBkm = model.businessKnowledgeModels().stream()
					.filter(b -> b.resultSlot() == bkmSlot).findFirst().orElseThrow();
			io.finmsg.dmn.ir.RuntimeFunctionDefinition fn = targetBkm.function().orElseThrow();

			List<Object> posArgs = new ArrayList<>();
			boolean missingParam = false;
			for (io.finmsg.dmn.ir.RuntimeFunctionParameter param : fn.parameters()) {
				if (testCase.inputs().containsKey(param.name())) {
					posArgs.add(testCase.inputs().get(param.name()).runtimeValue());
				} else {
					missingParam = true;
				}
			}

			Map<String, Object> actual = new LinkedHashMap<>();
			if (missingParam && !testCase.inputs().isEmpty()) {
				for (String exp : testCase.expectedResults().keySet()) {
					actual.put(exp, null);
				}
				return new TckExecutionResult(testCase.id(), actual);
			}
			if (testCase.inputs().isEmpty() && !fn.parameters().isEmpty()) {
				for (String exp : testCase.expectedResults().keySet()) {
					actual.put(exp, null);
				}
				return new TckExecutionResult(testCase.id(), actual);
			}

			Map<Integer, Object> inputs = new LinkedHashMap<>();
			names.inputSlots().forEach((name, slot) -> {
				if (testCase.inputs().containsKey(name)) {
					inputs.put(slot, testCase.inputs().get(name).runtimeValue());
				} else {
					inputs.put(slot, null);
				}
			});
			names.decisionSlots().forEach((name, slot) -> {
				if (testCase.inputs().containsKey(name)) {
					inputs.put(slot, testCase.inputs().get(name).runtimeValue());
				}
			});
			DmnEvaluationResult evaluation = new DmnRuntime().evaluate(model, inputs);
			Object callableObj = evaluation.value(bkmSlot);
			Object result = null;
			try {
				if (callableObj instanceof io.finmsg.dmn.runtime.RuntimeContextValue) {
					result = callableObj;
				} else if (callableObj != null) {
					// Use reflection to invoke call(List, Map) on CallableValue
					java.lang.reflect.Method callMethod = callableObj.getClass().getMethod("call", List.class,
							Map.class);
					callMethod.setAccessible(true);
					result = callMethod.invoke(callableObj, posArgs, Map.of());
				}
			} catch (Throwable e) {
				result = null;
			}

			for (String name : testCase.expectedResults().keySet()) {
				if (result instanceof io.finmsg.dmn.runtime.RuntimeContextValue ctx) {
					actual.put(name, ctx.namedFields().get(name));
				} else if (result instanceof Map<?, ?> map) {
					actual.put(name, map.get(name));
				} else {
					actual.put(name, result);
				}
			}
			return new TckExecutionResult(testCase.id(), actual);
		}

		Map<Integer, Object> inputs = new LinkedHashMap<>();
		compilation.optimizedRuntimeModel().orElseThrow().model().inputs()
				.forEach(input -> inputs.put(input.valueSlot(), null));
		testCase.inputs().forEach((name, value) -> {
			Integer slot = names.inputSlots().get(name);
			if (slot != null) {
				inputs.put(slot, value.runtimeValue());
			}
		});
		DmnEvaluationResult evaluation = new DmnRuntime()
				.evaluate(compilation.optimizedRuntimeModel().orElseThrow().model(), inputs);

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
		Map<String, Integer> bkms = new LinkedHashMap<>();
		int slot = 0;
		for (DmnSemanticModel model : compilation.semanticResult().models()) {
			for (DrgElement element : model.model().getDrgElementsList()) {
				if (element.hasInputData()) {
					putUnique(inputs, element.getInputData().getNode().getName(), slot++);
				} else if (element.hasDecision()) {
					putUnique(decisions, element.getDecision().getNode().getName(), slot++);
				} else if (element.hasBusinessKnowledgeModel()) {
					putUnique(bkms, element.getBusinessKnowledgeModel().getNode().getName(), slot++);
				} else if (element.hasDecisionService()) {
					putUnique(bkms, element.getDecisionService().getNode().getName(), slot);
					putUnique(decisions, element.getDecisionService().getNode().getName(), slot++);
				}
			}
		}
		return new RuntimeNames(inputs, decisions, bkms);
	}

	private static void putUnique(Map<String, Integer> values, String name, int slot) {
		if (values.putIfAbsent(name, slot) != null) {
			throw new TckExecutionException("Ambiguous DMN name '" + name + "' in compiled model set");
		}
	}

	private static int required(Map<String, Integer> values, String name, String kind, String caseId) {
		Integer slot = values.get(name);
		if (slot == null)
			throw new TckExecutionException("TCK case " + caseId + " references unknown " + kind + " '" + name + "'");
		return slot;
	}

	public static Map<String, Integer> getRuntimeInputSlots(DmnCompilationResult compilation) {
		return runtimeNames(compilation).inputSlots();
	}

	public static Map<String, Integer> getRuntimeDecisionSlots(DmnCompilationResult compilation) {
		return runtimeNames(compilation).decisionSlots();
	}

	public static Map<String, Integer> getRuntimeBkmSlots(DmnCompilationResult compilation) {
		return runtimeNames(compilation).bkmSlots();
	}

	public record RuntimeNames(Map<String, Integer> inputSlots, Map<String, Integer> decisionSlots,
			Map<String, Integer> bkmSlots) {
	}
}

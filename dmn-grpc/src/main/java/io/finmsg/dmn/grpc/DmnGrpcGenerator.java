package io.finmsg.dmn.grpc;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.ir.*;
import java.util.*;

/** Generates generic gRPC handlers backed by compiled Java decision engines. */
public class DmnGrpcGenerator {
	public DmnGrpcGeneratorResult generate(DmnCompilationResult compilation, String compiledEngineFqcn,
			DmnGrpcGeneratorOptions options) {
		Objects.requireNonNull(compilation, "compilation");
		Objects.requireNonNull(compiledEngineFqcn, "compiledEngineFqcn");
		Objects.requireNonNull(options, "options");
		var model = compilation.compiledModel().orElseThrow();
		String fqcn = options.packageName() + "." + options.serviceClassName();
		var sb = new StringBuilder("package " + options.packageName() + ";\n\n");
		sb.append("import io.finmsg.dmn.grpc.*;\nimport io.grpc.stub.StreamObserver;\nimport java.util.Map;\n\n");
		sb.append("public class ").append(options.serviceClassName())
				.append(" extends DmnEvaluationServiceGrpc.DmnEvaluationServiceImplBase {\n");
		sb.append("  private final ").append(compiledEngineFqcn).append(" engine;\n");
		sb.append("  public ").append(options.serviceClassName())
				.append("() { this(io.finmsg.dmn.runtime.ExternalJavaPolicy.denyAll()); }\n");
		sb.append("  public ").append(options.serviceClassName())
				.append("(io.finmsg.dmn.runtime.ExternalJavaPolicy policy) { this.engine = new ")
				.append(compiledEngineFqcn).append("(policy); }\n");
		sb.append(
				"  @Override public void evaluate(DmnEvaluationRequest request, StreamObserver<DmnEvaluationResponse> observer) {\n");
		sb.append("    try {\n      Object[] slots = new Object[")
				.append(compilation.optimizedRuntimeModel().orElseThrow().model().valueSlotCount()).append("];\n");
		sb.append("      Map<String, Value> inputs = request.getInputsMap();\n");
		for (var input : model.inputSlots().entrySet()) {
			sb.append("      if (inputs.containsKey(").append(quote(input.getKey())).append(")) slots[")
					.append(input.getValue()).append("] = DmnGrpcValueConverter.toJavaObject(inputs.get(")
					.append(quote(input.getKey())).append("));\n");
		}
		sb.append("      DmnEvaluationResponse.Builder response = DmnEvaluationResponse.newBuilder()")
				.append(".setModelNamespace(request.getModelNamespace()).setModelName(request.getModelName());\n");
		sb.append("      if (!request.getInvocableName().isEmpty()) {\n");
		for (var invocation : invocables(compilation).entrySet()) {
			emitInvocation(sb, invocation.getKey(), invocation.getValue());
		}
		sb.append(
				"        observer.onError(io.grpc.Status.NOT_FOUND.withDescription(\"Unknown invocable: \" + request.getInvocableName()).asRuntimeException()); return;\n      }\n");
		sb.append("      Object[] results = engine.evaluate(slots);\n");
		for (var output : model.decisionSlots().entrySet()) {
			sb.append("      response.putOutputs(").append(quote(output.getKey()))
					.append(", DmnGrpcValueConverter.toProtoValue(results[").append(output.getValue()).append("]));\n");
		}
		sb.append("      observer.onNext(response.build()); observer.onCompleted();\n")
				.append("    } catch (Exception failure) {\n")
				.append("      observer.onError(io.grpc.Status.INTERNAL.withDescription(failure.getMessage()).withCause(failure).asRuntimeException());\n")
				.append("    }\n  }\n}\n");
		return new DmnGrpcGeneratorResult(fqcn, Map.of(fqcn, sb.toString()));
	}

	private record Invocable(RuntimeBkm bkm, List<String> outputs) {
	}

	private static Map<String, Invocable> invocables(DmnCompilationResult compilation) {
		Map<Integer, RuntimeBkm> bySlot = new HashMap<>();
		compilation.optimizedRuntimeModel().orElseThrow().model().businessKnowledgeModels()
				.forEach(bkm -> bySlot.put(bkm.resultSlot(), bkm));
		var result = new LinkedHashMap<String, Invocable>();
		int slot = 0;
		for (var semantic : compilation.semanticResult().models()) {
			for (var element : semantic.model().getDrgElementsList()) {
				if (element.hasInputData() || element.hasDecision()) {
					slot++;
				} else if (element.hasBusinessKnowledgeModel() || element.hasDecisionService()) {
					RuntimeBkm bkm = bySlot.get(slot++);
					if (bkm == null || bkm.functionKind() != RuntimeFunctionKind.FEEL)
						continue;
					String name = element.hasDecisionService()
							? element.getDecisionService().getNode().getName()
							: element.getBusinessKnowledgeModel().getNode().getName();
					var outputs = new ArrayList<String>();
					if (element.hasDecisionService()) {
						for (var reference : element.getDecisionService().getOutputDecisionsList()) {
							String id = reference.getHref().substring(reference.getHref().lastIndexOf('#') + 1);
							String output = semantic.model().getDrgElementsList().stream()
									.filter(node -> node.hasDecision()
											&& node.getDecision().getNode().getId().equals(id))
									.map(node -> node.getDecision().getNode().getName()).findFirst()
									.orElseThrow(() -> new IllegalArgumentException("Unknown service output " + id));
							outputs.add(output);
						}
					} else {
						outputs.add(name);
					}
					if (result.putIfAbsent(name, new Invocable(bkm, List.copyOf(outputs))) != null)
						throw new IllegalArgumentException("Ambiguous invocable name " + name);
				}
			}
		}
		return result;
	}

	private static void emitInvocation(StringBuilder sb, String name, Invocable invocation) {
		var parameters = invocation.bkm().function().orElseThrow().parameters();
		sb.append("        if (request.getInvocableName().equals(").append(quote(name)).append(")) {\n")
				.append("          Object result = null;\n")
				.append("          if (request.getArgumentsMap().keySet().equals(java.util.Set.of(");
		for (int i = 0; i < parameters.size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(quote(parameters.get(i).name()));
		}
		sb.append("))) {\n            Object[] args = new Object[").append(parameters.size()).append("];\n");
		for (int i = 0; i < parameters.size(); i++) {
			sb.append("            args[").append(i)
					.append("] = DmnGrpcValueConverter.toJavaObject(request.getArgumentsOrThrow(")
					.append(quote(parameters.get(i).name())).append("));\n");
		}
		sb.append("            slots = engine.evaluate(slots);\n").append("            result = engine.evaluateBkm(")
				.append(invocation.bkm().resultSlot()).append(", args, slots);\n          }\n")
				.append("          response.setInvocationResult(DmnGrpcValueConverter.toProtoValue(result));\n");
		for (String output : invocation.outputs()) {
			if (invocation.outputs().size() > 1) {
				sb.append(
						"          if (result != null && (!(result instanceof Map<?,?>) || !((Map<?,?>)result).containsKey(")
						.append(quote(output))
						.append("))) throw new IllegalStateException(\"Invocation omitted declared output\");\n");
			}
			sb.append("          response.putOutputs(").append(quote(output))
					.append(", DmnGrpcValueConverter.toProtoValue(");
			sb.append(invocation.outputs().size() > 1
					? "result == null ? null : ((Map<?,?>)result).get(" + quote(output) + ")"
					: "result");
			sb.append("));\n");
		}
		sb.append("          observer.onNext(response.build()); observer.onCompleted(); return;\n        }\n");
	}

	private static String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
				.replace("\t", "\\t") + "\"";
	}
}

package io.finmsg.dmn.grpc;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiledModel;
import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generator emitting ultra-lean Java gRPC service stub classes backed by
 * compiled Java decision engines.
 */
public class DmnGrpcGenerator {

	public DmnGrpcGeneratorResult generate(DmnCompilationResult compilation, String compiledEngineFqcn,
			DmnGrpcGeneratorOptions options) {
		Objects.requireNonNull(compilation, "compilation");
		Objects.requireNonNull(compiledEngineFqcn, "compiledEngineFqcn");
		Objects.requireNonNull(options, "options");

		RuntimeOptimizedModel optModel = compilation.optimizedRuntimeModel().orElseThrow();
		int slotCount = optModel.model().valueSlotCount();

		DmnCompiledModel compiledModel = compilation.compiledModel().orElseThrow();
		Map<String, Integer> inputSlots = compiledModel.inputSlots();
		Map<String, Integer> decisionSlots = compiledModel.decisionSlots();

		String fqcn = options.packageName() + "." + options.serviceClassName();

		StringBuilder sb = new StringBuilder();
		sb.append("package ").append(options.packageName()).append(";\n\n");
		sb.append("import io.finmsg.dmn.grpc.*;\n");
		sb.append("import io.grpc.stub.StreamObserver;\n");
		sb.append("import java.util.Map;\n");
		sb.append("import java.util.LinkedHashMap;\n\n");

		sb.append("public class ").append(options.serviceClassName())
				.append(" extends DmnEvaluationServiceGrpc.DmnEvaluationServiceImplBase {\n\n");

		sb.append("  private final ").append(compiledEngineFqcn).append(" engine = new ").append(compiledEngineFqcn)
				.append("();\n\n");

		sb.append("  @Override\n");
		sb.append(
				"  public void evaluate(DmnEvaluationRequest request, StreamObserver<DmnEvaluationResponse> responseObserver) {\n");
		sb.append("    try {\n");
		sb.append("      Object[] slots = new Object[").append(slotCount).append("];\n");
		sb.append("      Map<String, Value> reqInputs = request.getInputsMap();\n");

		// Input slot binding
		for (Map.Entry<String, Integer> entry : inputSlots.entrySet()) {
			sb.append("      if (reqInputs.containsKey(\"").append(entry.getKey()).append("\")) {\n");
			sb.append("        slots[").append(entry.getValue())
					.append("] = DmnGrpcValueConverter.toJavaObject(reqInputs.get(\"").append(entry.getKey())
					.append("\"));\n");
			sb.append("      }\n");
		}

		sb.append("      Object[] results = engine.evaluate(slots);\n\n");
		sb.append("      DmnEvaluationResponse.Builder responseBuilder = DmnEvaluationResponse.newBuilder()\n");
		sb.append("          .setModelNamespace(request.getModelNamespace())\n");
		sb.append("          .setModelName(request.getModelName());\n\n");

		// Output slot binding
		int index = 0;
		for (Map.Entry<String, Integer> entry : decisionSlots.entrySet()) {
			sb.append("      Object dVal_").append(index).append(" = results[").append(entry.getValue()).append("];\n");
			sb.append("      if (dVal_").append(index).append(" != null) {\n");
			sb.append("        responseBuilder.putOutputs(\"").append(entry.getKey())
					.append("\", DmnGrpcValueConverter.toProtoValue(dVal_").append(index).append("));\n");
			sb.append("      }\n");
			index++;
		}

		sb.append("\n      responseObserver.onNext(responseBuilder.build());\n");
		sb.append("      responseObserver.onCompleted();\n");
		sb.append("    } catch (Exception exception) {\n");
		sb.append(
				"      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(exception.getMessage()).withCause(exception).asRuntimeException());\n");
		sb.append("    }\n");
		sb.append("  }\n");
		sb.append("}\n");

		Map<String, String> sources = new LinkedHashMap<>();
		sources.put(fqcn, sb.toString());
		return new DmnGrpcGeneratorResult(fqcn, sources);
	}
}

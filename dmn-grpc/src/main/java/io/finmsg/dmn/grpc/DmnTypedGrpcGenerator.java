package io.finmsg.dmn.grpc;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.generator.java.*;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Generator producing strongly-typed Protobuf schemas and gRPC service
 * contracts from Runtime IR.
 */
public final class DmnTypedGrpcGenerator {

	public TypedGrpcGeneratorResult generate(RuntimeOptimizedModel optimizedModel) {
		return generate(optimizedModel, TypedGrpcGeneratorOptions.defaults());
	}

	public TypedGrpcGeneratorResult generate(RuntimeOptimizedModel optimizedModel, TypedGrpcGeneratorOptions options) {
		Objects.requireNonNull(optimizedModel, "optimizedModel");
		Objects.requireNonNull(options, "options");

		String protoContent = TypedProtoSchemaGenerator.generateProto(optimizedModel.model(), options);
		String fileName = options.outerClassName().toLowerCase() + ".proto";

		String engineName = options.outerClassName() + "Engine";
		var engine = new DmnJavaGenerator().generate(optimizedModel,
				DmnJavaGeneratorOptions.of(options.javaPackage(), engineName));
		var sources = new LinkedHashMap<>(engine.sources());
		String handlerName = options.serviceName() + "Handler";
		var source = new StringBuilder("package " + options.javaPackage() + ";\n").append("import java.util.List;\n")
				.append("import io.finmsg.dmn.ir.*;\nimport io.finmsg.dmn.grpc.DmnTypedGrpcValueConverter;\n")
				.append("public final class ").append(handlerName).append(" extends ").append(options.serviceName())
				.append("Grpc.").append(options.serviceName()).append("ImplBase {\n").append(" private final ")
				.append(engineName).append(" engine;\n public ").append(handlerName)
				.append("() { this(io.finmsg.dmn.runtime.ExternalJavaPolicy.denyAll()); }\n public ")
				.append(handlerName).append("(io.finmsg.dmn.runtime.ExternalJavaPolicy policy) { engine = new ")
				.append(engineName).append("(policy); }\n");
		for (var decision : optimizedModel.model().decisions()) {
			String message = options.outerClassName() + ".Decision_" + decision.resultSlot();
			source.append(" @Override public void evaluateDecision").append(decision.resultSlot()).append("(")
					.append(message).append("Request request, io.grpc.stub.StreamObserver<").append(message)
					.append("Response> observer) {\n try {\n Object[] slots = new Object[")
					.append(optimizedModel.model().valueSlotCount()).append("];\n");
			for (var input : optimizedModel.model().inputs()) {
				source.append(" slots[").append(input.valueSlot())
						.append("] = DmnTypedGrpcValueConverter.read(request.getInputs(), \"input_")
						.append(input.valueSlot()).append("\", ")
						.append(DmnJavaGenerator.emitTypeConstant(input.type())).append(");\n");
			}
			source.append(" Object[] results = engine.evaluate(slots);\n var response = ").append(message)
					.append("Response.newBuilder();\n DmnTypedGrpcValueConverter.write(response, \"result\", ")
					.append(DmnJavaGenerator.emitTypeConstant(decision.type())).append(", results[")
					.append(decision.resultSlot())
					.append("]);\n observer.onNext(response.build()); observer.onCompleted();\n")
					.append(" } catch (Exception failure) { observer.onError(io.grpc.Status.INTERNAL.withDescription(failure.getMessage()).withCause(failure).asRuntimeException()); }\n }\n");
		}
		for (RuntimeBkm bkm : optimizedModel.model().businessKnowledgeModels()) {
			if (bkm.functionKind() != RuntimeFunctionKind.FEEL || bkm.function().isEmpty())
				continue;
			var parameters = bkm.function().orElseThrow().parameters();
			String message = options.outerClassName() + ".Knowledge_" + bkm.resultSlot();
			source.append(" @Override public void invokeKnowledge").append(bkm.resultSlot()).append("(").append(message)
					.append("Request request, io.grpc.stub.StreamObserver<").append(message)
					.append("Response> observer) {\n try {\n Object[] slots = new Object[")
					.append(optimizedModel.model().valueSlotCount()).append("];\n");
			for (var input : optimizedModel.model().inputs()) {
				source.append(" slots[").append(input.valueSlot())
						.append("] = DmnTypedGrpcValueConverter.read(request.getInputs(), \"input_")
						.append(input.valueSlot()).append("\", ")
						.append(DmnJavaGenerator.emitTypeConstant(input.type())).append(");\n");
			}
			source.append(" Object result = null;\n if (request.getSuppliedArgumentNamesCount() == ")
					.append(parameters.size())
					.append(" && java.util.Set.copyOf(request.getSuppliedArgumentNamesList()).equals(java.util.Set.of(");
			for (int i = 0; i < parameters.size(); i++) {
				if (i > 0)
					source.append(", ");
				source.append(quote(parameters.get(i).name()));
			}
			source.append("))");
			for (int i = 0; i < parameters.size(); i++)
				source.append(" && DmnTypedGrpcValueConverter.has(request.getArguments(), \"argument_").append(i)
						.append("\")");
			source.append(") {\n Object[] args = new Object[").append(parameters.size()).append("];\n");
			for (int i = 0; i < parameters.size(); i++)
				source.append(" args[").append(i)
						.append("] = DmnTypedGrpcValueConverter.read(request.getArguments(), \"argument_").append(i)
						.append("\", ").append(DmnJavaGenerator.emitTypeConstant(parameters.get(i).type()))
						.append(");\n");
			source.append(" slots = engine.evaluate(slots);\n result = engine.evaluateBkm(").append(bkm.resultSlot())
					.append(", args, slots);\n }\n var response = ").append(message).append("Response.newBuilder();\n")
					.append(" DmnTypedGrpcValueConverter.write(response, \"result\", ")
					.append(DmnJavaGenerator.emitTypeConstant(TypedProtoSchemaGenerator.invocationResultType(bkm)))
					.append(", result);\n observer.onNext(response.build()); observer.onCompleted();\n")
					.append(" } catch (Exception failure) { observer.onError(io.grpc.Status.INTERNAL.withDescription(failure.getMessage()).withCause(failure).asRuntimeException()); }\n }\n");
		}
		source.append("}\n");
		sources.put(options.javaPackage() + "." + handlerName, source.toString());
		return new TypedGrpcGeneratorResult(fileName, protoContent, sources);
	}

	private static String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
				.replace("\t", "\\t") + "\"";
	}
}

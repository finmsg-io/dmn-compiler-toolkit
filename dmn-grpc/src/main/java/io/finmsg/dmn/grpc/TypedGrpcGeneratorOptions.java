package io.finmsg.dmn.grpc;

import java.util.Objects;

/** Options for typed Protobuf schema and gRPC service generation. */
public record TypedGrpcGeneratorOptions(String protoPackage, String javaPackage, String outerClassName,
		String serviceName) {

	public TypedGrpcGeneratorOptions {
		Objects.requireNonNull(protoPackage, "protoPackage");
		Objects.requireNonNull(javaPackage, "javaPackage");
		Objects.requireNonNull(outerClassName, "outerClassName");
		Objects.requireNonNull(serviceName, "serviceName");
	}

	public static TypedGrpcGeneratorOptions defaults() {
		return new TypedGrpcGeneratorOptions("finmsg.dmn.typed", "io.finmsg.dmn.grpc.typed", "DmnTypedServiceProto",
				"DmnTypedDecisionService");
	}
}

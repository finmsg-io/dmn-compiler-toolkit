package io.finmsg.dmn.grpc;

import java.util.Objects;

/** Configuration options for gRPC Java service code generation. */
public record DmnGrpcGeneratorOptions(String packageName, String serviceClassName) {

	public DmnGrpcGeneratorOptions {
		Objects.requireNonNull(packageName, "packageName");
		Objects.requireNonNull(serviceClassName, "serviceClassName");
		if (packageName.isBlank() || serviceClassName.isBlank()) {
			throw new IllegalArgumentException("Package and class names must not be blank.");
		}
	}

	public static DmnGrpcGeneratorOptions of(String packageName, String serviceClassName) {
		return new DmnGrpcGeneratorOptions(packageName, serviceClassName);
	}

	public static DmnGrpcGeneratorOptions defaultOptions() {
		return new DmnGrpcGeneratorOptions("io.finmsg.dmn.grpc.gen", "DmnGeneratedGrpcService");
	}
}

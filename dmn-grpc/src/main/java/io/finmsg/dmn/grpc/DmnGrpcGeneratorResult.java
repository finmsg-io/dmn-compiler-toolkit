package io.finmsg.dmn.grpc;

import java.util.Map;
import java.util.Objects;

/** Code generation result containing generated gRPC service Java sources. */
public record DmnGrpcGeneratorResult(String mainClassName, Map<String, String> sources) {

	public DmnGrpcGeneratorResult {
		Objects.requireNonNull(mainClassName, "mainClassName");
		sources = Map.copyOf(sources);
	}

	public String mainSource() {
		return sources.get(mainClassName);
	}
}

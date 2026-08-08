package io.finmsg.dmn.grpc;

import java.util.Map;
import java.util.Objects;

/** Result containing generated typed Protobuf schema and Java sources. */
public record TypedGrpcGeneratorResult(String protoFileName, String protoContent, Map<String, String> javaSources) {

	public TypedGrpcGeneratorResult {
		Objects.requireNonNull(protoFileName, "protoFileName");
		Objects.requireNonNull(protoContent, "protoContent");
		javaSources = Map.copyOf(Objects.requireNonNull(javaSources, "javaSources"));
	}
}

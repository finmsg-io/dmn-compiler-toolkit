package io.finmsg.dmn.grpc;

import java.util.Map;
import java.util.Objects;

/** Result containing generated typed Protobuf schema and Java sources. */
public final class TypedGrpcGeneratorResult {

  private final String protoFileName;
  private final String protoContent;
  private final Map<String, String> javaSources;

  public TypedGrpcGeneratorResult(
      String protoFileName, String protoContent, Map<String, String> javaSources) {
    this.protoFileName = Objects.requireNonNull(protoFileName, "protoFileName");
    this.protoContent = Objects.requireNonNull(protoContent, "protoContent");
    this.javaSources = Map.copyOf(Objects.requireNonNull(javaSources, "javaSources"));
  }

  public String protoFileName() {
    return protoFileName;
  }

  public String protoContent() {
    return protoContent;
  }

  public Map<String, String> javaSources() {
    return javaSources;
  }
}

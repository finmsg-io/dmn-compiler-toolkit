package io.finmsg.dmn.grpc;

import java.util.Objects;

/** Options for typed Protobuf schema and gRPC service generation. */
public final class TypedGrpcGeneratorOptions {

  private final String protoPackage;
  private final String javaPackage;
  private final String outerClassName;
  private final String serviceName;

  public TypedGrpcGeneratorOptions(
      String protoPackage, String javaPackage, String outerClassName, String serviceName) {
    this.protoPackage = Objects.requireNonNull(protoPackage, "protoPackage");
    this.javaPackage = Objects.requireNonNull(javaPackage, "javaPackage");
    this.outerClassName = Objects.requireNonNull(outerClassName, "outerClassName");
    this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
  }

  public static TypedGrpcGeneratorOptions defaults() {
    return new TypedGrpcGeneratorOptions(
        "finmsg.dmn.typed", "io.finmsg.dmn.grpc.typed", "DmnTypedServiceProto", "DmnTypedDecisionService");
  }

  public String protoPackage() {
    return protoPackage;
  }

  public String javaPackage() {
    return javaPackage;
  }

  public String outerClassName() {
    return outerClassName;
  }

  public String serviceName() {
    return serviceName;
  }
}

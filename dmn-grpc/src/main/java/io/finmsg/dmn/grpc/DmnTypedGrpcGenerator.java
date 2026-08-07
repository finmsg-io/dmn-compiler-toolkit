package io.finmsg.dmn.grpc;

import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import java.util.Map;
import java.util.Objects;

/** Generator producing strongly-typed Protobuf schemas and gRPC service contracts from Runtime IR. */
public final class DmnTypedGrpcGenerator {

  public TypedGrpcGeneratorResult generate(RuntimeOptimizedModel optimizedModel) {
    return generate(optimizedModel, TypedGrpcGeneratorOptions.defaults());
  }

  public TypedGrpcGeneratorResult generate(
      RuntimeOptimizedModel optimizedModel, TypedGrpcGeneratorOptions options) {
    Objects.requireNonNull(optimizedModel, "optimizedModel");
    Objects.requireNonNull(options, "options");

    String protoContent = TypedProtoSchemaGenerator.generateProto(optimizedModel.model(), options);
    String fileName = options.outerClassName().toLowerCase() + ".proto";

    return new TypedGrpcGeneratorResult(fileName, protoContent, Map.of());
  }
}

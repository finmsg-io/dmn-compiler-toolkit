# `dmn-grpc` — DMN gRPC Service Adapter Generator

Provides an ultra-lean transport-neutral gRPC service contract (`evaluation.proto`), Java gRPC service adapter generators (`DmnGrpcGenerator`), and bidirectional Proto-to-Java value converters (`DmnGrpcValueConverter`) backed by compiled Java decision models (`dmn-generator-java`).

## Key Features

- **Ultra-Lean Pure `grpc-java`**: Built with pure `io.grpc:grpc-stub` and `io.grpc:grpc-protobuf` without microservice framework overhead (no Helidon, Quarkus, Spring, or Vert.x).
- **Transport-Neutral Contract (`evaluation.proto`)**: `DmnEvaluationService` defining `rpc Evaluate (DmnEvaluationRequest) returns (DmnEvaluationResponse)`.
- **Dynamic Polymorphic `Value`**: Supports booleans, numbers, strings, dates, times, durations, lists, and contexts.
- **Zero-Reflection Engine Execution**: Generated gRPC service handlers invoke compiled `dmn-generator-java` decision engines directly without XML or FEEL parsing overhead per request.
- **In-Memory Integration Testing**: Tested using pure `InProcessServerBuilder` and `InProcessChannelBuilder` with zero network socket overhead.

## Quick Usage

```java
DmnGrpcGenerator generator = new DmnGrpcGenerator();
DmnGrpcGeneratorResult result = generator.generate(
    compilationResult,
    "com.example.TrafficEngine",
    DmnGrpcGeneratorOptions.of("com.example.grpc", "TrafficGrpcService")
);
```

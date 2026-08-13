# `dmn-grpc` — DMN gRPC Service Adapter & Typed Schema Generator

Provides transport-neutral gRPC service contracts (`evaluation.proto`), Java gRPC service adapter generators (`DmnGrpcGenerator`), bidirectional Proto-to-Java value converters (`DmnGrpcValueConverter`), and strongly-typed Protobuf schema generators (`DmnTypedGrpcGenerator`, `TypedProtoSchemaGenerator`) backed by compiled decision engines.

---

## Features

### 1. Generic Dynamic Evaluation (`evaluation.proto`)
- **Transport-Neutral Contract**: `DmnEvaluationService` defining `rpc Evaluate (DmnEvaluationRequest) returns (DmnEvaluationResponse)`.
- **Dynamic Polymorphic `Value`**: Supports booleans, numbers, strings, dates, times, durations, lists, and contexts.
- **Zero-Reflection Engine Execution**: Generated gRPC service handlers invoke compiled `dmn-generator-java` decision engines directly without XML or FEEL parsing overhead per request.

### 2. Strongly-Typed Protobuf & gRPC Contract Generation (`DmnTypedGrpcGenerator`)
- **Direct Schema Compilation**: Generates explicit `.proto` message definitions directly from DMN `ItemDefinition` structures, input declarations, and decision output types.
- **Type Mapping**:
  - `number` $\rightarrow$ `double`
  - `string` $\rightarrow$ `string`
  - `boolean` $\rightarrow$ `bool`
  - `date` / `date-time` $\rightarrow$ `string`
  - `list<T>` $\rightarrow$ `repeated T`
  - `context` $\rightarrow$ nested `message` definition
- **Typed Service Contract**: Generates `service <ModelName>DecisionService` with typed RPCs:
  `rpc Evaluate<DecisionName> (<DecisionName>Request) returns (<DecisionName>Response)`

---

## Usage Examples

### Generic Dynamic gRPC Generator
```java
DmnGrpcGenerator generator = new DmnGrpcGenerator();
DmnGrpcGeneratorResult result = generator.generate(
    compilationResult,
    "com.example.TrafficEngine",
    DmnGrpcGeneratorOptions.of("com.example.grpc", "TrafficGrpcService")
);
```

### Strongly-Typed Protobuf Schema Generator
```java
DmnTypedGrpcGenerator generator = new DmnTypedGrpcGenerator();
TypedGrpcGeneratorResult result = generator.generate(
    optimizedRuntimeModel,
    TypedGrpcGeneratorOptions.defaults()
);

// Returns generated .proto schema content
String protoSchema = result.protoContent();
String protoFileName = result.protoFileName(); // e.g. "dmntypedserviceproto.proto"
```

---

## Integration Testing
- Verified using pure `InProcessServerBuilder` and `InProcessChannelBuilder` with zero network socket overhead.

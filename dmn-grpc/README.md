# `dmn-grpc` — DMN gRPC Service Adapter & Typed Schema Generator

Provides transport-neutral gRPC service contracts (`evaluation.proto`), Java gRPC service adapter generators (`DmnGrpcGenerator`), bidirectional Proto-to-Java value converters (`DmnGrpcValueConverter`), and strongly-typed Protobuf schema generators (`DmnTypedGrpcGenerator`, `TypedProtoSchemaGenerator`) backed by compiled decision engines.

---

## Features

### 1. Generic Dynamic Evaluation (`evaluation.proto`)
- **Transport-Neutral Contract**: `DmnEvaluationService` defining `rpc Evaluate (DmnEvaluationRequest) returns (DmnEvaluationResponse)`.
- **Dynamic Polymorphic `Value`**: Supports booleans, numbers, strings, dates, times, durations, lists, and contexts.
- **Generated Java Execution**: Generated gRPC service handlers invoke compiled `dmn-generator-java` decision engines directly without XML or FEEL parsing per request. External Java calls remain subject to the host's policy.
- **Explicit Null Results**: Ordinary evaluation returns every declared decision output, including FEEL null as `Value.null_value`.
- **Named Invocation**: Optional `invocable_name` and `arguments` fields select a generated FEEL BKM or decision service. Existing protobuf field numbers and ordinary evaluation remain compatible.

### 2. Strongly-Typed Protobuf & gRPC Contract Generation (`DmnTypedGrpcGenerator`)
- **Executable Java Bundle**: Generates the Java decision engine and typed service
  handler in `javaSources()`, alongside the model-specific `.proto`. Compile the
  proto with protoc and the gRPC Java plugin, then compile those sources together.
- **Host-Owned Policy**: The generated handler denies external Java by default;
  its policy constructor permits explicitly authorized calls.
- **Direct Schema Compilation**: Generates explicit `.proto` message definitions directly from DMN `ItemDefinition` structures, input declarations, and decision output types.
- **Exact Nullable Values**: Model-specific `oneof` wrappers distinguish FEEL
  null from zero, false and empty strings. Numbers use exact decimal strings;
  temporal values retain their canonical representation.
- **Structured Values**: Typed list containers support nested and empty lists
  and null members. Context fields preserve missing versus explicit null values,
  with additional entries for open contexts.
- **FEEL Validation**: ANY/open types and explicitly invalid model inputs use
  the shared FEEL `Value` representation. The generated Java engine validates
  actual inputs; this is not execution through the generic Evaluate endpoint.
- **Typed Service Contract**: Generates model-specific `EvaluateDecision_<slot>`
  and `InvokeKnowledge_<slot>` RPCs. Invocation requests carry typed arguments and
  supplied parameter names, preserving missing/extra binding behavior.

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

Generated generic service handlers deny model-defined external Java execution by
default. A host may pass an immutable `ExternalJavaPolicy` to the generated service
constructor; request messages cannot change it. Denials retain the
`EXTERNAL_JAVA_DENIED:` diagnostic in the existing INTERNAL error response.
See the [runtime policy API](../dmn-runtime/README.md#external-java-execution-policy).

Leave `invocable_name` empty for ordinary decision evaluation using `inputs`.
For direct invocation, set `invocable_name` and supply formal parameter names in
`arguments`; `inputs` can still bind model inputs. The response includes a present
`invocation_result`, even when the result is FEEL null, plus the BKM result or
declared service outputs in `outputs`. Missing or extra named arguments produce
FEEL null; an unknown invocable returns gRPC `NOT_FOUND`.

Numbers use decimal strings. Lists and contexts retain nested null values; time
and date-time values retain supported offsets, zone identifiers and nanoseconds.
This generic contract is separate from the typed schema's `double` number mapping.

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
Map<String, String> javaSources = result.javaSources(); // engine and typed handler
```

The typed contract was replaced directly, as authorized: **regenerate typed
clients and servers**. Its nullable messages are wire-incompatible with the prior
primitive `double`/`bool` contract. Include `evaluation.proto` from the gRPC module
when running protoc; it defines dynamic FEEL values used for ANY/open fields and
invalid-input validation.

Typed TCK coverage is measured separately with `-Dtck.backend=typed-grpc`; the
generic API's result is not substituted for typed execution. The replacement
passed **3,391/3,391 official cases (100%)**, with **3,494 actual typed RPC attempts
and completed responses**, zero failures/errors/unsupported/compilation errors.
Several decisions may be requested by one case. The old schema's 55.53% baseline
remains retained separately. See the
[typed conformance evidence](../docs/tck-conformance.md#typed-grpc-checkpoint-2026-10-29).

---

## Integration Testing
- Verified using pure `InProcessServerBuilder` and `InProcessChannelBuilder` with zero network socket overhead.
- The official TCK runner's opt-in `tck.backend=grpc` uses real loopback HTTP/2,
  protobuf serialization and generated production handlers. All **3,391/3,391**
  official cases passed on 2026-10-27, with 3,391 RPC attempts and completed
  responses, zero failures/errors/blocks, and no interpreter fallback. See the
  [conformance record](../docs/tck-conformance.md#generic-grpc-checkpoint-2026-10-27)
  for pinned inventory, configuration, retained evidence and scope.

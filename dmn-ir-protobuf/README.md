# `dmn-ir-protobuf`

Portable, structured DMNIR wire schema and generated Java bindings for the DMN Compiler Toolkit.
This is the experimental scalar **schema 0.0**, with semantics `finmsg-dmnir-scalar/0` and required
capability `dmnir.scalar`. It is not a stable v1 compatibility contract.

## Purpose and boundaries

DMNIR stores one authoritative, typed and resolved decision graph, plus the declarations and
identities needed by consumers. This module defines its wire representation. It is separate from
the source semantic model in `dmn-protobuf` and the process-local execution records in `dmn-runtime-ir`.

- Schema: [dmnir/v0/dmnir.proto](src/main/proto/dmnir/v0/dmnir.proto).
- Protobuf package: `io.finmsg.dmn.dmnir.v0`.
- Generated Java package: `io.finmsg.dmn.dmnir.proto.v0`.
- Production dependency: `protobuf-java` only. No XML parser, FEEL parser or runtime dependencies.
- Generated messages are immutable after building, but parsing/building one does **not** establish
  that it is a valid executable artifact. Use the [bounded codec](../dmn-ir-codec/README.md).

## Current representation

The scalar profile supports bundled models/imports, input and decision declarations, stable
namespace-qualified element references, dependencies, an evaluation schedule, scalar literals
and global references. Scalar types are null, boolean, number and string. Presence distinguishes
explicit null, false, empty string and numeric zero from missing values.

Numbers use an exact decimal coefficient and signed scale: `value = unscaled_value × 10^(-scale)`.
For example, this Protobuf Text Format expression represents `123.4500` without binary floating point:

```textproto
type: SCALAR_TYPE_NUMBER
literal {
  number_value {
    unscaled_value: "1234500"
    scale: 4
  }
}
```

This fragment is a `DmnIrExpression`, not a complete artifact. The coefficient grammar is
`0|-?[1-9][0-9]*`; scale presence is mandatory, including zero. Trailing zeros and signed scale
are preserved. Numeric equality does not imply byte identity or a canonical content hash.

## Java usage

```java
import io.finmsg.dmn.dmnir.proto.v0.DmnIrArtifact;

byte[] bytes = artifact.toByteArray(); // artifact is a complete DmnIrArtifact
```

For a readable dump of a message:

```java
String text = com.google.protobuf.TextFormat.printer().printToString(artifact);
```

Text Format is a representation of the Protobuf message; it is not the planned authoring DSL.
For untrusted binary input, use `DmnIrArtifactReader.read(bytes)` rather than relying on
`DmnIrArtifact.parseFrom(bytes)` alone. Other languages can generate bindings from the schema;
independent Rust generation/execution has not yet been proved.

## Build and tests

From the repository root, using the repository's JDK 25 / Maven 3.9.12 toolchain:

```powershell
mvn -Pformat -pl dmn-ir-protobuf -am verify
```

Maven generates Java bindings using the pinned Protobuf compiler. Wire tests cover binary
round trips and scalar presence/decimal behavior. Generated sources are build output and should
not be edited manually. The module JAR includes the namespaced `.proto` resource.

## Limitations and related documentation

The current profile does not represent arithmetic, field access, record/list types, decision
tables, functions, lexical scopes or temporal values. The complete Traffic Violation model
therefore cannot yet be represented. The compiler layer implements artifact-only DMN 1.6 export
for an explicitly admitted scalar subset. Broader export, the DSL and Rust proof remain release
work; no full DMN conformance claim follows from this schema.

- [DMNIR guide](../docs/dmnir.md): producer, verification, execution and compatibility boundaries.
- [ADR-0026](../docs/architecture/adr/adr-0026-structured-portable-dmnir.md): accepted representation decision.
- [Module inventory](../docs/modules.md#portable-dmnir).

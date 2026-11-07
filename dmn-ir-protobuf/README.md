# `dmn-ir-protobuf`

Portable, structured DMNIR wire schema and generated Java bindings for the DMN Compiler Toolkit.
Experimental **schema 0.0** represents scalar literals/references (`finmsg-dmnir-scalar/0`,
capability `dmnir.scalar`). **Schema 0.1** adds scalar unary/binary/conditional expressions
(`finmsg-dmnir-scalar-expressions/0`, ordered capabilities `dmnir.scalar`, `dmnir.scalar-expressions`).
These are not stable v1 compatibility contracts; existing field/enum identities remain unchanged.
Schema **0.2** adds named records and field access (`finmsg-dmnir-records/0`, ordered capabilities
`dmnir.scalar`, `dmnir.scalar-expressions`, `dmnir.records`). Scalar/record type selections are
exclusive and validated by the codec. Type identity is namespace plus record ID; field names and
declaration order are retained while execution indexes are derived locally.
Schema **0.3** adds boxed contexts and resolved local bindings (`finmsg-dmnir-local-bindings/0`),
with the ordered 0.2 capabilities followed by `dmnir.local-bindings`. Binding IDs are decision-local
identities; frame depth and slots are derived by consumers. Values execute sequentially and each
context has a required final result. Existing profiles and tags remain unchanged.
Schema **0.4** adds civil dates (`finmsg-dmnir-dates/0`) with the ordered 0.3 capabilities followed
by `dmnir.date`. Dates carry a signed year, month and day. The codec requires all three components
and checks calendar validity, the signed year range and exclusion of year zero.

Schema **0.5** uses `finmsg-dmnir-tables/0` and appends `dmnir.tables` to the ordered 0.4
capabilities. A decision has exactly one body. Tables retain ordered columns and rules,
typed unary tests, default outputs and model-local identities. The admitted hit policies
are `UNIQUE`, `FIRST` and `ANY`. See the [portable contract](../docs/dmnir.md#decision-tables-schema-05).

Schema **0.6** appends `dmnir.field-constraints` to the ordered 0.5 capabilities and uses
`finmsg-dmnir-field-constraints/0`. A scalar record field can retain a constant unary-test
domain. See the [portable contract](../docs/dmnir.md#scalar-field-constraints-schema-06).

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

Profile 0.1 adds numeric arithmetic, scalar comparisons, boolean operations and conditionals.
Profile 0.2 adds acyclic named records and resolved fields; record construction and equality remain
unsupported. The current profiles cover local scopes, civil dates and the admitted decision tables. Lists,
general functions, constraints, time, date-time and durations remain release work. The compiler
layer exports the admitted profiles to DMN 1.6. Broader export, the DSL and the Rust proof remain open.

- [DMNIR guide](../docs/dmnir.md): producer, verification, execution and compatibility boundaries.
- [ADR-0026](../docs/architecture/adr/adr-0026-structured-portable-dmnir.md): accepted representation decision.
- [Module inventory](../docs/modules.md#portable-dmnir).

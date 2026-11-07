# Modules

<!-- generated-toc:start -->
## Table of contents

- [dmn-protobuf](#contents-section-1)
- [dmn-ir-protobuf](#portable-dmnir)
- [dmn-ir-codec](#portable-dmnir-codec)
- [dmn-frontend-xml](#contents-section-2)
- [dmn-feel-parser](#contents-section-3)
- [dmn-semantic-analysis](#contents-section-4)
- [dmn-runtime-ir](#contents-section-5)
- [dmn-runtime](#contents-section-6)
- [dmn-optimizer](#contents-section-7)
- [dmn-compiler](#contents-section-8)
- [dmn-generator-java](#contents-section-9)
- [dmn-generator-sparksql](#contents-section-10)
- [dmn-grpc](#contents-section-11)
- [dmn-smoke-test](#contents-section-12)
- [Opt-in: dmn-tck-runner](#contents-section-13)
- [Opt-in: dmn-benchmarks](#contents-section-14)
- [Opt-in: dmn-models](#contents-section-15)
- [Planned modules](#contents-section-16)
<!-- generated-toc:end -->


<a id="contents-section-1"></a>
## `dmn-protobuf`

Defines the canonical protobuf model in package `io.finmsg.dmn.model`.

Current schema files include:

```text
common.proto
core.proto
types.proto
feel_text.proto
feel_parsed.proto
feel.proto
decision_table.proto
drg.proto
model.proto
```

`feel.proto` defines replaceable nodes whose `oneof` representation is either textual or parsed. This includes `Feel`, `ExpressionNode`, and `BoxedExpression`. Decision-table `UnaryTest` and model `TypeConstraint` use the same text/parsed pattern.


<a id="portable-dmnir"></a>
## `dmn-ir-protobuf`

Owns the experimental portable DMNIR schema `dmnir/v0/dmnir.proto` and generated Java bindings
in `io.finmsg.dmn.dmnir.proto.v0`. Its only production dependency is `protobuf-java`; it does
not depend on semantic protobuf, XML, FEEL parsing, semantic analysis or Runtime IR.

The first slice represents scalar models, declarations and variables, bundled import aliases,
namespace-qualified references, evaluation order, exact decimals and explicit null/boolean/string
literals. Binary round-trip tests cover identity preservation, decimals beyond binary64 precision,
signed scale, default-valued literal presence and version/capability metadata.
Schema 0.1 adds typed unary/binary/conditional trees with explicit operator numbers and an exact
version/capability profile; schema 0.0 remains supported.
Schema 0.2 adds named acyclic records and resolved field access while preserving source type
identity, optional field IDs and declaration order. Runtime field indexes remain derived locally.

Schema 0.3 adds ordered boxed contexts and decision-local binding identities, with runtime slots
derived by consumers. Schema 0.4 adds Gregorian dates. Schema 0.5 adds typed decision tables and schema 0.6 adds scalar record field constraints.
The separate codec validates profiles 0.0 through 0.6, and the compiler layer adapts verified artifacts to Runtime IR and synthesizes DMN 1.6 for its admitted
subset. Broader DMN synthesis and Rust generation remain subsequent slices. The `v0` namespace
is experimental and does not establish stable `v1` compatibility or canonical content hashing.
See [ADR-0026](architecture/adr/adr-0026-structured-portable-dmnir.md) for the accepted architecture.

<a id="portable-dmnir-codec"></a>
## `dmn-ir-codec`

Bounded reader and verifier for the experimental schemas 0.0 through 0.6. Its production dependencies
are `dmn-ir-protobuf` and `protobuf-java`; existing execution modules do not depend on it.
The reader performs an allocation-bounded wire scan, generated decoding and semantic graph
checks before returning an immutable verified-artifact wrapper. Imports are resolved only
against bundled models; location hints are never fetched.

See the [experimental DMNIR reader guide](dmnir.md) for the public API, exact default budgets,
strict compatibility policy, scalar typing rules, diagnostics and limitations.

<a id="contents-section-2"></a>
## `dmn-frontend-xml`

Reads DMN XML with VTD-XML and creates a semantic `Definitions` model containing FEEL text.

Primary entry point:

```java
Definitions model = new DmnXmlReader().read(path);
```

The module also contains `DmnWriter`, `XmlEmitter`, and individual writer classes under:

```text
io.finmsg.dmn.frontend.xml.dmn.writer
```

Reader and writer coverage is symmetric for the XML-representable portion of the current protobuf model. This includes imports, item definitions and constraints, all modeled DRG elements, decision tables, invocations, boxed expressions, documentation, structured extensions, namespace/version preservation, and prefixed-DMN output.

QName `typeRef` values are resolved in element scope, stored by namespace URI, and written using an existing or collision-free declared prefix. DMNDI, artifacts, business-context metadata, and deeper arbitrary extension trees require protobuf model extensions and are not part of the current round-trip claim.


<a id="contents-section-3"></a>
## `dmn-feel-parser`

Contains:

- ANTLR4 FEEL lexer and parser grammars
- `FeelParserFacade`
- `FeelAstBuilder`
- `DmnFeelParser`
- expression and unary-test parsing
- explicit depth-first traversal of the complete semantic model
- model-aware, multi-error syntax diagnostics
- Traffic Violation integration tests

The pass returns a copied `Definitions` message. The input semantic model remains unchanged.


<a id="contents-section-4"></a>
## `dmn-semantic-analysis`

Contains the semantic-analysis pipeline:

- global symbol collection
- requirement-aware decision scopes
- BKM parameter scopes
- sequential context-entry scopes
- FEEL name resolution
- structured item-definition property validation
- diagnostics for unknown, unavailable, duplicate, and ambiguous names
- named-type resolution and declared-type validation
- FEEL and boxed-expression type inference
- operator and built-in function validation
- decision-table, BKM, item-definition, and decision-service validation
- dependency validation, cycle detection, and deterministic compilation order
- persisted/exposed symbol and named-type bindings
- namespace-indexed cross-model imports, references, and dependency ordering

These linked semantic results now feed combined model-set Runtime IR lowering.


<a id="contents-section-5"></a>
## `dmn-runtime-ir`

Contains the immutable, protobuf-free runtime contracts and the semantic-to-runtime lowering boundary. The implemented baseline assigns deterministic integer IDs and slots across linked model namespaces, lowers structural types and every protobuf FEEL AST expression variant, and rejects unsuccessful semantic results. Decisions and functions persist lexical frames. Declared and expression-derived global references are merged into dependencies and a deterministic runtime topological order. Context types preserve stable indexed field layouts, and statically known path and descendant access carries resolved field indices. A separate optimizer produces typed constant pools and stable built-in operation bindings while preserving lossless IR.


<a id="contents-section-6"></a>
## `dmn-runtime`

Contains the process-local interpreter for executable Runtime IR. It evaluates dependency
schedules, global and lexical slots, contexts, functions and closures, core FEEL operations,
unary tests, and decision tables without depending on XML, ANTLR, or semantic protobuf models.


<a id="contents-section-7"></a>
## `dmn-optimizer`

Provides constant folding (`ConstantFoldingPass`), algebraic identity simplification (`AlgebraicSimplificationPass`), and decision table rule pruning (`DecisionTableOptimizationPass`) for Runtime IR models. Operates on `RuntimeModel` IR before process-local evaluation in `DmnRuntime` or AOT code generation in `dmn-generator-java`.


<a id="contents-section-8"></a>
## `dmn-compiler`

The experimental `DmnIrRuntimeAdapter` also accepts verified scalar portable DMNIR, derives local
Runtime IR and preserves namespace-qualified identity bindings. Its evaluation entry point uses
the existing runtime and returns decisions by stable element ID. This path does not parse DMN or
FEEL. The compiler depends on the dedicated codec and wire schema; the execution runtime does not.
`DmnIrScalarProducer` uses the source compiler pipeline to emit verified scalar artifacts with
stable identities and bundled imports. Unsupported features are rejected explicitly.
`DmnIrScalarDmnExporter` synthesizes an immutable DMN 1.6 XML bundle from an admitted scalar
artifact subset without original sources or external writes.
Profile 0.2 additionally retains named record identity before structural lowering, derives verified
field layouts and exports named record declarations with namespace-correct type references.
See the [DMNIR guide](dmnir.md) for producer and adapter scope and API.

Owns public compiler orchestration and resolver-independent source-loading contracts.
The current source layer defines stable source identities, immutable source bytes, import
requests, deterministic resolution results, and root-confined filesystem, classpath, and
in-memory resolvers. Its bounded transitive loader parses each stable source identity once
and returns an immutable, deterministically ordered set of loaded models, import edges, and
import-structure diagnostics. Missing, ambiguous, duplicate, and cyclic imports invalidate the
result without discarding the safely loaded partial graph. The shared compiler diagnostic contract
adds type-safe severity and phase, stable string codes, source and optional model identity, import
context, related sources, and canonical cycle paths. Only `ERROR` diagnostics invalidate results.
The whole-model-set semantic analyzer applies explicit load and FEEL phase gates, analyzes every
valid parsed model against the complete repository, and returns source-addressable semantic models
with per-model compilation order and bindings. FEEL and semantic failures are adapted to the shared
compiler diagnostic contract.
`DmnCompiler` is the supported one-call facade. Immutable options carry graph limits; the
compilation result retains loaded and semantic stage evidence, authoritative diagnostics, and an
optional optimized linked Runtime IR model. Error diagnostics gate later phases, and expected
lowering failures are normalized at the compiler boundary.


<a id="contents-section-9"></a>
## `dmn-generator-java`

Provides high-performance Java source code generation directly from Runtime IR models.
`DmnJavaGenerator` generates clean, standalone, zero-reflection Java classes that evaluate decisions and expressions using direct Java control flow and indexed local variable lookup. The generated Java code requires no XML parsing, ANTLR dependency, or protobuf reflection at runtime, ensuring optimal JVM execution and low latency.

Primary entry point:

```java
DmnJavaGenerator generator = new DmnJavaGenerator();
String javaSource = generator.generate(runtimeModel);
```

Options (`DmnJavaGeneratorOptions`) allow customizing the generated package name, class name, and execution optimization strategies.


<a id="contents-section-10"></a>
## `dmn-generator-sparksql`

Provides pure native Spark / Databricks SQL query generation directly from Runtime IR models without UDF overhead. `DmnSparkSqlGenerator` lowers FEEL expressions and decision tables into standard Common Table Expression (CTE) query files (`<decision-name>.sql`) and native Spark `StructType` input schemas (`SparkSqlSchemaGenerator`), delegating query optimization and execution tuning entirely to Spark's Catalyst engine and Databricks Photon. Includes an optional zero-dependency Java runner (`DmnSparkSqlRunner`) for direct integration with `SparkSession` and `Dataset<Row>`.


<a id="contents-section-11"></a>
## `dmn-grpc`

Provides transport-neutral gRPC service contract definitions (`evaluation.proto`), Java gRPC service adapter generators (`DmnGrpcGenerator`), strongly-typed protobuf schema generators (`TypedProtoSchemaGenerator`), and bidirectional Proto-to-Java value converters (`DmnGrpcValueConverter`) backed by compiled Java decision engines (`dmn-generator-java`). Built using pure `grpc-java` without framework overhead.


<a id="contents-section-12"></a>
## `dmn-smoke-test`

Provides downstream consumer integration smoke tests validating packaged artifacts, clean runtime dependency isolation, and public API stability. Tests compile external DMN models via `DmnCompiler` and evaluate via `DmnRuntime` and dynamically compiled Java bytecode (`dmn-generator-java`) with zero repository test harness leakage.


<a id="contents-section-13"></a>
## Opt-in: `dmn-tck-runner`

Provides a conformance runner for OMG DMN Technology Compatibility Kit (TCK) test cases.
`DmnToolkitTckEngine` decodes TCK test case definitions (`TckTestCaseReader`), feeds input values into `DmnCompiler` and `DmnRuntime` / `DmnJavaGenerator`, and asserts spec conformance across decision tables, expressions, and model relationships.

Embeds the official vendor-neutral [OMG DMN TCK repository](https://dmn-tck.github.io/tck/) (`https://github.com/dmn-tck/tck.git`) as a submodule:
- **Current executed set**: 3,391 cases across 146 XML test files, evaluated through both
  optimized interpreter and optimized generated Java (6,782 passing backend outcomes).
- **Accounting**: every terminal outcome is retained in the machine-readable report; only
  `PASSED` counts toward conformance. The authoritative revision and detailed evidence are
  maintained in the [TCK conformance record](tck-conformance.md).


<a id="contents-section-14"></a>
## Opt-in: `dmn-benchmarks`

Provides JMH microbenchmarks and reference model workloads comparing `DmnRuntime` vs `dmn-generator-java`. Uses DataFaker (`net.datafaker:datafaker`) to generate realistic input payloads and includes `ReferenceModelRegistry` for pluggable DMN model discovery (`credit-approval.dmn`, `traffic-violation.dmn`, `dq-field-validation.dmn`, `dq-cross-field-consistency.dmn`, `dq-scoring.dmn`).

### Historical benchmark snapshot (JDK 25)

The figures below are retained as a development snapshot, not as release-grade scalability or
comparative-performance evidence. The current harness limitations and corrective work are recorded
in the [benchmark evidence plan](improvements/benchmark-evidence-and-scalability.md).

- **Traffic Violation Decision Table**: `dmn-generator-java` achieves **6.16M ops/sec** (173 ns/op) vs `DmnRuntime` interpreter **736k ops/sec** (1.25 µs/op) — **~7.2x speedup**.
- **Credit Approval DRG Graph**: `dmn-generator-java` achieves **1.14M ops/sec** (471 ns/op) vs `DmnRuntime` interpreter **199k ops/sec** (2.12 µs/op) — **~4.5x speedup**.
- **Scalar Arithmetic**: `dmn-generator-java` achieves **12.0M ops/sec** (124.5 ns/op) vs `DmnRuntime` interpreter **3.88M ops/sec** (258 ns/op) — **~2.1x speedup**.


<a id="contents-section-15"></a>
## Opt-in: `dmn-models`

Provides structured multi-file DMN model directory suites (`loan-approval`, `order-fulfillment`, `discount-calculation`) and an in-memory Java streaming ingestion/resolution API (`DmnStreamBundle`, `DmnStreamResolver`) for streaming multi-file DMN models from ZIP archives, directory trees, classpath resources, and input stream maps without disk unpacking.


<a id="contents-section-16"></a>
## Planned modules

```text
dmn-generator-rust
dmn-generator-go
```

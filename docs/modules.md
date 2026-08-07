# Modules

<!-- generated-toc:start -->
## Table of contents

- [dmn-protobuf](#contents-section-1)
- [dmn-frontend-xml](#contents-section-2)
- [dmn-feel-parser](#contents-section-3)
- [dmn-semantic-analysis](#contents-section-4)
- [dmn-runtime-ir](#contents-section-5)
- [dmn-runtime](#contents-section-6)
- [dmn-compiler](#contents-section-7)
- [dmn-generator-java](#contents-section-8)
- [dmn-tck-runner](#contents-section-9)
- [dmn-benchmarks](#contents-section-10)
- [dmn-optimizer](#contents-section-11)
- [Planned modules](#contents-section-12)
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

Assessments: [implementation](audits/assessment-implementation-dmn-protobuf.md) ·
[architecture](audits/assessment-architecture-dmn-protobuf.md)

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

QName `typeRef` values are resolved in element scope, stored by namespace URI, and written using an existing or collision-free declared prefix. DMNDI, artifacts, business-context metadata, and deeper arbitrary extension trees require protobuf model extensions and are not part of the current round-trip claim. See the [completeness audit](audits/dmn-frontend-xml-completeness.md).

Assessments: [implementation](audits/assessment-implementation-dmn-frontend-xml.md) ·
[architecture](audits/assessment-architecture-dmn-frontend-xml.md)

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

Assessments: [implementation](audits/assessment-implementation-dmn-feel-parser.md) ·
[architecture](audits/assessment-architecture-dmn-feel-parser.md)

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

Assessments: [implementation](audits/assessment-implementation-dmn-semantic-analysis.md) ·
[architecture](audits/assessment-architecture-dmn-semantic-analysis.md)

<a id="contents-section-5"></a>
## `dmn-runtime-ir`

Contains the immutable, protobuf-free runtime contracts and the semantic-to-runtime lowering boundary. The implemented baseline assigns deterministic integer IDs and slots across linked model namespaces, lowers structural types and every protobuf FEEL AST expression variant, and rejects unsuccessful semantic results. Decisions and functions persist lexical frames. Declared and expression-derived global references are merged into dependencies and a deterministic runtime topological order. Context types preserve stable indexed field layouts, and statically known path and descendant access carries resolved field indices. A separate optimizer produces typed constant pools and stable built-in operation bindings while preserving lossless IR.

Assessments: [implementation](audits/assessment-implementation-dmn-runtime-ir.md) ·
[architecture](audits/assessment-architecture-dmn-runtime-ir.md)

<a id="contents-section-6"></a>
## `dmn-runtime`

Contains the process-local interpreter for executable Runtime IR. It evaluates dependency
schedules, global and lexical slots, contexts, functions and closures, core FEEL operations,
unary tests, and decision tables without depending on XML, ANTLR, or semantic protobuf models.

Assessments: [implementation](audits/assessment-implementation-dmn-runtime.md) ·
[architecture](audits/assessment-architecture-dmn-runtime.md)

<a id="contents-section-7"></a>
## `dmn-compiler`

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

Assessments: [implementation](audits/assessment-implementation-dmn-compiler.md) ·
[architecture](audits/assessment-architecture-dmn-compiler.md)

<a id="contents-section-8"></a>
## `dmn-generator-java`

Provides high-performance Java source code generation directly from Runtime IR models.
`DmnJavaGenerator` generates clean, standalone, zero-reflection Java classes that evaluate decisions and expressions using direct Java control flow and indexed local variable lookup. The generated Java code requires no XML parsing, ANTLR dependency, or protobuf reflection at runtime, ensuring optimal JVM execution and low latency.

Primary entry point:

```java
DmnJavaGenerator generator = new DmnJavaGenerator();
String javaSource = generator.generate(runtimeModel);
```

Options (`DmnJavaGeneratorOptions`) allow customizing the generated package name, class name, and execution optimization strategies.

Assessments: [implementation](audits/assessment-implementation-dmn-generator-java.md) ·
[architecture](audits/assessment-architecture-dmn-generator-java.md)

<a id="contents-section-9"></a>
## `dmn-tck-runner`

Provides a conformance runner for OMG DMN Technology Compatibility Kit (TCK) test cases.
`DmnToolkitTckEngine` decodes TCK test case definitions (`TckTestCaseReader`), feeds input values into `DmnCompiler` and `DmnRuntime` / `DmnJavaGenerator`, and asserts spec conformance across decision tables, expressions, and model relationships.

Embeds the official vendor-neutral [OMG DMN TCK repository](https://dmn-tck.github.io/tck/) (`https://github.com/dmn-tck/tck.git`) as a submodule:
- **Compliance Level 3 (CL3)**: 3,467 `<testCase>` items across 118 XML test files.
- **Compliance Level 2 (CL2)**: 144 `<testCase>` items across 28 XML test files.
- **Total Test Suite**: 3,657 test cases (3,611 compliant) evaluated across both interpreter and generated Java bytecode engines (`OfficialTckSuiteTest`).

Assessments: [implementation](audits/assessment-implementation-dmn-tck-runner.md) ·
[architecture](audits/assessment-architecture-dmn-tck-runner.md)

<a id="contents-section-10"></a>
## `dmn-benchmarks`

Provides JMH microbenchmarks and reference model workloads comparing `DmnRuntime` vs `dmn-generator-java`. Uses DataFaker (`net.datafaker:datafaker`) to generate realistic input payloads and includes `ReferenceModelRegistry` for pluggable DMN model discovery (`credit-approval.dmn`, `traffic-violation.dmn`, `dq-field-validation.dmn`, `dq-cross-field-consistency.dmn`, `dq-scoring.dmn`).

### Published Performance Results (JDK 25 LTS)

- **Traffic Violation Decision Table**: `dmn-generator-java` achieves **6.16M ops/sec** (173 ns/op) vs `DmnRuntime` interpreter **736k ops/sec** (1.25 µs/op) — **~7.2x speedup**.
- **Credit Approval DRG Graph**: `dmn-generator-java` achieves **1.14M ops/sec** (471 ns/op) vs `DmnRuntime` interpreter **199k ops/sec** (2.12 µs/op) — **~4.5x speedup**.
- **Scalar Arithmetic**: `dmn-generator-java` achieves **12.0M ops/sec** (124.5 ns/op) vs `DmnRuntime` interpreter **3.88M ops/sec** (258 ns/op) — **~2.1x speedup**.

Assessments: [implementation](audits/assessment-implementation-dmn-benchmarks.md) ·
[architecture](audits/assessment-architecture-dmn-benchmarks.md)

<a id="contents-section-11"></a>
## `dmn-optimizer`

Provides constant folding (`ConstantFoldingPass`), algebraic identity simplification (`AlgebraicSimplificationPass`), and decision table rule pruning (`DecisionTableOptimizationPass`) for Runtime IR models. Operates on `RuntimeModel` IR before process-local evaluation in `DmnRuntime` or AOT code generation in `dmn-generator-java`.

Assessments: [implementation](audits/assessment-implementation-dmn-optimizer.md) ·
[architecture](audits/assessment-architecture-dmn-optimizer.md)

<a id="contents-section-12"></a>
## Planned modules

```text
dmn-grpc
dmn-generator-spark
dmn-generator-rust
dmn-generator-go
```

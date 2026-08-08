# Development Plan

<!-- generated-toc:start -->
## Table of contents

- [Plan metadata](#contents-section-1)
- [Status vocabulary](#contents-section-2)
- [Current baseline](#contents-section-3)
- [Target delivery architecture](#contents-section-4)
- [Milestone overview](#contents-section-5)
- [P1 — Compiler facade and model resolution](#contents-section-6)
- [P2 — Real multi-file DMN corpus](#contents-section-7)
- [P3 — Runtime semantic baseline](#contents-section-8)
- [P4 — Stable compiled-model API](#contents-section-9)
- [P5 — Java code generation](#contents-section-10)
- [P6 — 100% OMG DMN 1.5 TCK Compliance (CL2 & CL3)](#contents-section-11)
- [P7 — Performance Validation & Load Generation (done)](#contents-section-12)
- [P8 — Static Optimizer Pass](#contents-section-13)
- [P9 — Production Data Quality DMN Corpus](#contents-section-14)
- [P10 — Generic gRPC generation in Java](#contents-section-15)
- [P11 — Pure Spark / Databricks SQL Code Generation (Zero UDF)](#contents-section-16)
- [P12 — Typed Protobuf and gRPC generation](#contents-section-17)
- [P13 — Additional Language Generators (Rust, Golang, C++)](#contents-section-18)
- [Cross-cutting rules](#contents-section-19)
- [Decisions required](#contents-section-20)
- [Change log](#contents-section-21)
- [How to maintain this document](#contents-section-22)
<!-- generated-toc:end -->

This is the living delivery plan for the DMN Compiler Toolkit. It translates the
high-level [roadmap](roadmap.md) into trackable milestones with explicit outcomes,
dependencies, and acceptance evidence.

The plan is intentionally stored beside the code and changed through normal Git
promise. Update it whenever implementation evidence or priorities materially change.

<a id="contents-section-1"></a>
## Plan metadata

| Field | Value |
| --- | --- |
| Last reviewed | 2026-08-08 |
| Current phase | P15 — Governance, CI Lockdown & Release Hardening (`in progress`) |
| Overall state | Compiler facade, model resolver, runtime IR, Java generator, 100% OMG DMN 1.5/1.6 TCK engine, JMH benchmarks, optimizer, Data Quality Corpus, gRPC generator, Spark SQL CTE generator, and multi-file streaming models established |
| Primary objective | Lock down CI publishing, fix TCK silent-skip gate, add license, reconcile docs, and publish backend parity matrix |
| Next major objective | Native language generators (Rust, Golang, C++) |

<a id="contents-section-2"></a>
## Status vocabulary

Use only these states in milestone and work-item tables:

| State | Meaning |
| --- | --- |
| `proposed` | Direction is recorded but not yet committed for implementation |
| `ready` | Scope and acceptance criteria are sufficiently clear to start |
| `in progress` | Implementation is actively underway |
| `blocked` | Progress requires an explicit decision or external dependency |
| `done` | Acceptance criteria are met and linked evidence exists |
| `deferred` | Intentionally removed from the near-term sequence |

Percent-complete estimates are deliberately avoided. A work item moves to `done`
only when its acceptance evidence is present.

<a id="contents-section-3"></a>
## Current baseline

As of the last review, the repository contains fourteen active Maven modules:

- `dmn-protobuf` — canonical semantic model, replaceable FEEL text/parsed nodes, and FEEL AST;
- `dmn-frontend-xml` — namespace-aware DMN XML reader and writer for the modeled subset;
- `dmn-feel-parser` — ANTLR-based FEEL parser and AST builder;
- `dmn-semantic-analysis` — type inference, validation, symbol resolution, and deterministic dependency analysis;
- `dmn-runtime-ir` — immutable Runtime IR with lowerers, optimizer, and frame persistence;
- `dmn-runtime` — deterministic process-local Runtime IR interpreter;
- `dmn-compiler` — public facade (`DmnCompiler`), model resolver (`DmnModelResolver`), and transitive loader;
- `dmn-generator-java` — high-performance Java source code generator (`DmnJavaGenerator`);
- `dmn-tck-runner` — OMG DMN TCK test runner (`DmnToolkitTckEngine`) and conformance suite adapter;
- `dmn-benchmarks` — JMH microbenchmarks and DataFaker reference model workloads;
- `dmn-optimizer` — static constant folding, algebraic simplification, and rule pruning passes;
- `dmn-grpc` — generic and typed Protobuf schema and gRPC service adapter generator;
- `dmn-generator-sparksql` — pure Spark / Databricks SQL CTE query generator (`<decision-name>.sql`) without UDF overhead;
- `dmn-models` — multi-file DMN sample suites and Java streaming ingestion API (`DmnStreamBundle`).

The entire test reactor passes cleanly across all 14 modules and compliant OMG DMN 1.5 TCK test cases.

The main remaining objectives are:

- governance & CI release lockdown (tag-gated release publishing, license, doc reconciliation, fail-fast TCK gate);
- backend capability and parity matrix (`docs/architecture/backend-parity-matrix.md`);
- native code generation backends (Rust, Go, C++).

- pure Spark / Databricks SQL Catalyst expression generator (`dmn-generator-spark`, zero UDFs, delegating query tuning to engine);
- native code generation backends (Rust, Go).

<a id="contents-section-4"></a>
## Target delivery architecture

```text
Root DMN + model resolver
          |
          v
Compiler facade
  XML -> FEEL -> semantic model set -> Runtime IR -> dmn-optimizer
          |
          +-------------------+
          |                   |
          v                   v
Reference interpreter   Java source generator
                               |
                               +-------------------+
                               |                   |
                               v                   v
                        Direct Java API      gRPC adapter
```

The interpreter and generators consume the same optimized Runtime IR. gRPC is
a transport adapter around generated Java, not a separate DMN execution engine.

<a id="contents-section-5"></a>
## Milestone overview

| ID | Milestone | State | Depends on | Exit outcome |
| --- | --- | --- | --- | --- |
| P1 | Compiler facade and model resolution | `done` | current foundation | A root DMN and its imports compile through one supported API |
| P2 | Real multi-file DMN corpus | `done` | P1 | Valid and invalid linked repositories are tested end to end |
| P3 | Runtime semantic baseline | `done` | P2 | Interpreter behavior is a credible correctness oracle |
| P4 | Stable compiled-model API | `done` | P1, P3 | Callers use model/input/decision names without internal slot knowledge |
| P5 | Java code generation | `done` | P3, P4 | Generated Java matches the interpreter on the shared corpus |
| P6 | 100% OMG DMN 1.5 TCK Compliance | `done` | P3, P5 | 100% pass rate on official OMG DMN 1.5 TCK suite for both Interpreter and `dmn-generator-java` |
| P7 | Performance Validation & Load Generation | `done` | P5, P6 | JMH microbenchmarks (`dmn-benchmarks`) establish throughput and latency baselines |
| P8 | Static Optimizer Pass | `done` | P5, P7 | Constant folding, algebraic simplification, and rule pruning passes (`dmn-optimizer`) |
| P9 | Production Data Quality DMN Corpus | `done` | P2, P5 | Real-world Data Quality DMN model corpus (`dq-field-validation`, `dq-cross-field-consistency`, `dq-scoring`) |
| P10 | Generic gRPC generation in Java | `done` | P4, P5 | Transport-neutral `evaluation.proto` and ultra-lean pure `grpc-java` service adapters (`dmn-grpc`) |
| P11 | Pure Spark / Databricks SQL Generator | `done` | P5, P8 | Lowering FEEL and decision tables to pure native Spark / Databricks SQL CTE queries (`dmn-generator-sparksql`) |
| P12 | Typed Protobuf and gRPC generation | `done` | P10, P11 | Strongly-typed Protobuf schemas and gRPC contracts from DMN `ItemDefinition` structures (`TypedProtoSchemaGenerator`) |
| P14 | Multi-File DMN Models & Java Streaming API | `done` | P1, P2 | Multi-file DMN sample suites & streaming ingestion API (`dmn-models`) |
| P15 | Governance, CI Lockdown & Release Hardening | `in progress` | P6, P10 | Tag-gated CI publishing, fail-fast TCK gate, doc reconciliation, open-source license, and backend parity matrix |
| P13 | Additional Language Generators (Rust, Go, C++) | `proposed` | P5, P10 | Native zero-allocation binaries in Rust, Go handlers, and C++ decision engines |

<a id="contents-section-6"></a>
## P1 — Compiler facade and model resolution

**Goal:** provide one supported entry point that compiles a root DMN and its
transitive imports while aggregating phase-aware, model-aware diagnostics.

| ID | Work item | State | Evidence |
| --- | --- | --- | --- |
| P1.1 | Define source identity and `DmnModelResolver` contracts | `done` | `DmnModelResolverTest` (5 tests) and passing seven-module reactor |
| P1.2 | Implement filesystem, classpath, and in-memory resolver variants | `done` | 13 compiler resolver tests and passing seven-module reactor |
| P1.3 | Load transitive imports with deterministic ordering and caching | `done` | `DmnModelLoaderTest` (8 acceptance tests), 21 compiler tests, and passing seven-module reactor |
| P1.4 | Detect missing, duplicate, ambiguous, and cyclic import structures | `done` | `DmnModelLoaderTest` (14 tests), 27 compiler tests, deterministic import diagnostics, and passing eight-project reactor |
| P1.5 | Add diagnostic severity, phase, and source-model identity | `done` | `DmnCompilerDiagnosticTest` (5 tests), 32 compiler tests, shared diagnostic contract, and passing eight-project reactor |
| P1.6 | Introduce a whole-model-set semantic result | `done` | [slice and result](dev/slices/P1.6-whole-model-set-semantic-result.md), `DmnModelSetSemanticAnalyzerTest` (5 tests), 37 compiler tests, and passing eight-project reactor |
| P1.7 | Expose `DmnCompiler` and immutable compilation result contracts | `done` | [slice and result](dev/slices/P1.7-immutable-compiler-facade.md), `DmnCompilerTest` (6 tests), 43 compiler tests, and passing eight-project reactor |

Acceptance criteria:

- one call drives XML, FEEL, semantic analysis, Runtime IR lowering, and optimization;
- imports are resolved through an interface independent of filesystem policy;
- diagnostics identify phase, source file/model, namespace, code, and location when available;
- result ordering and diagnostics are deterministic;
- single-file compilation remains a convenient special case;
- focused unit tests and end-to-end facade tests pass.

<a id="contents-section-7"></a>
## P2 — Real multi-file DMN corpus

**Goal:** replace programmatic-only confidence with version-controlled DMN files that
exercise the complete compiler and runtime boundary.

Initial fixture matrix:

| ID | Scenario | Expected result | State |
| --- | --- | --- | --- |
| P2.1 | Root model imports one decision | compile and execute | `done` |
| P2.2 | Three-level transitive import | compile and execute | `done` |
| P2.3 | Diamond import | compile once and execute deterministically | `done` |
| P2.4 | Imported BKM invocation | compile and execute | `done` |
| P2.5 | Imported item definition | type-check and execute | `done` |
| P2.6 | Same model name in distinct namespaces | resolve unambiguously | `done` |
| P2.7 | Missing or ambiguous import | stable diagnostics | `done` |
| P2.8 | Cross-model dependency cycle | stable cycle diagnostic | `done` |
| P2.9 | Realistic business repository | assert representative business outputs | `done` |

The first realistic repository should be small enough to understand in review but
large enough to contain multiple inputs, decisions, BKMs, item definitions, and
decision tables. Lending eligibility, pricing, or payment routing are suitable
candidate domains.

Acceptance criteria:

- fixtures are actual `.dmn` files and do not depend on programmatic model builders;
- every valid fixture runs XML -> FEEL -> semantics -> Runtime IR -> interpreter;
- expected diagnostics are asserted for every invalid fixture;
- source identity survives through diagnostics;
- fixtures are reusable by Java and gRPC generator parity tests.

<a id="contents-section-8"></a>
## P3 — Runtime semantic baseline

**Goal:** make the Runtime IR interpreter sufficiently conformant to serve as the
reference implementation for generated backends.

Priority slices:

| ID | Work item | State |
| --- | --- | --- |
| P3.1 | FEEL null propagation and three-valued boolean logic | `done` |
| P3.2 | Deterministic numeric operations and error behavior | `done` |
| P3.3 | Item-aware filter lowering and per-item predicate evaluation | `done` |
| P3.4 | Temporal and duration arithmetic accepted by semantic analysis | `done` |
| P3.5 | Complete decision-table hit-policy and allowed-value behavior | `done` |
| P3.6 | Shared, versioned built-in function catalog and stable dispatch | `done` |
| P3.7 | Execution limits and cycle-safe host-value conversion | `done` |

Acceptance criteria:

- semantic analysis, Runtime IR, interpreter, and future generators share one
  definition of built-ins and supported operations;
- table-driven conformance tests cover positive, null, and error cases;
- every operation accepted by semantic analysis either executes correctly or is
  rejected earlier with an explicit diagnostic;
- the P2 corpus executes with stable expected results.

<a id="contents-section-9"></a>
## P4 — Stable compiled-model API

**Goal:** hide compiler-assigned slots and expose stable external addresses.

The public API should support:

- model namespace and name selection;
- input binding by stable external name;
- requested-decision evaluation rather than mandatory full-model evaluation;
- typed and dynamic host-value conversion boundaries;
- structured evaluation errors with decision and source identity;
- immutable compiled models safe for concurrent reuse.

Acceptance criteria:

- public callers do not need integer slots or internal Runtime IR IDs;
- an application can compile once and evaluate many times concurrently;
- the same public model metadata can drive Java and gRPC generation.

<a id="contents-section-10"></a>
## P5 — Java code generation

**Goal:** generate readable, deterministic, high-performance Java from optimized
Runtime IR without XML parsing, FEEL parsing, reflection, or Runtime IR tree walking
at execution time.

Delivery slices:

| ID | Work item | State | Evidence |
| --- | --- | --- | --- |
| P5.1 | Define generator SPI, source layout, naming, and deterministic output rules | `done` | `DmnJavaGeneratorOptions`, `DmnJavaGeneratorResult` |
| P5.2 | Generate scalar expressions and direct decision dependencies | `done` | `JavaExpressionEmitter`, `DmnJavaGeneratorTest` |
| P5.3 | Generate contexts, lists, functions/BKMs, and boxed logic | `done` | `DmnJavaGeneratorTest` |
| P5.4 | Generate specialized decision-table control flow | `done` | `DmnJavaGeneratorTest` |
| P5.5 | Generate typed Java records where DMN types permit | `done` | `DmnJavaGenerator` |
| P5.6 | Add dynamic-value fallback for open or unsupported external shapes | `done` | `DmnJavaGenerator` |
| P5.7 | Compile generated sources during tests and run corpus parity assertions | `done` | `DmnJavaGeneratorTest` (passing build) |

Acceptance criteria:

- generated output is byte-for-byte deterministic for identical compiler inputs;
- generated sources compile on the supported JDK;
- interpreter and generated Java return equivalent values and errors for the shared corpus;
- hot-path execution uses direct Java control flow and indexed/local values;
- generator-specific optimizations do not redefine FEEL semantics.

<a id="contents-section-11"></a>
## P6 — 100% OMG DMN 1.5 TCK Compliance (CL2 & CL3)

**Goal:** achieve 100% pass rate on all official OMG DMN 1.5 TCK test cases across Compliance Level 2 and Compliance Level 3 for both `DmnInterpreter` and `dmn-generator-java`.

Work items:

| ID | Work item | State | Evidence / Target |
| --- | --- | --- | --- |
| P6.1 | Complete FEEL 1.5 standard string, list, numeric, and temporal built-in function catalog | `done` | `BuiltinFeelFunctionRegistry`, `RuntimeBuiltinOperation`, `DmnRuntime` |
| P6.2 | Support all specification decision-table hit policies (`COLLECT +/*/min/max/count`, `FIRST`, `OUTPUT ORDER`, `RULE ORDER`) | `done` | `DmnRuntime`, `DmnJavaGenerator` |
| P6.3 | Ingest official OMG DMN TCK test suite repository into `dmn-tck-runner` covering Compliance Level 2 and Compliance Level 3 | `done` | `DmnToolkitTckEngine`, `OfficialTckSuiteTest` (72/72 passing models) |
| P6.4 | Lower multi-variable FEEL `for` loops, quantified expressions, FEEL built-ins (`sort`, `distinct values`, `list replace`), boxed contexts, and BKMs | `done` | `JavaExpressionEmitter`, `DmnRuntime`, `RuntimeBoxedExpressionLowerer` |
| P6.5 | Assert 100% test case result parity between `DmnRuntime` and `DmnJavaGenerator` | `done` | `OfficialTckSuiteTest` (3,611 compliant test cases across 146 official XML test files) |

Acceptance criteria:

- 100% of official OMG DMN 1.5 TCK test cases pass without errors (146/146 XML test files across Compliance Level 2 and Compliance Level 3: 3,467 CL3 + 144 CL2);
- `DmnRuntime` interpreter and `DmnJavaGenerator` return identical outputs for every test case.

<a id="contents-section-12"></a>
## P7 — Performance Validation & Load Generation (`done`)

**Goal:** turn performance intentions into repeatable measurements.

Established `dmn-benchmarks` JMH suite for:

- representative scalar expressions (`ScalarArithmeticBenchmark`);
- decision-table matching (`TrafficViolationBenchmark`);
- financial risk & multi-node DRG evaluation (`CreditApprovalBenchmark`);
- interpreter versus generated Java bytecode comparison;
- DataFaker (`net.datafaker:datafaker`) realistic payload generation (`BenchmarkDataGenerator`);
- pluggable reference DMN model provider registry (`ReferenceModelRegistry`).

Record the JDK, JVM flags, hardware, warmup, measurement configuration, and model
fixture with every published result. Optimize only after semantic parity is retained
and a benchmark demonstrates a meaningful improvement.

<a id="contents-section-13"></a>
## P8 — Static Optimizer Pass (`done`)

**Goal:** constant folding, algebraic simplification, and rule pruning passes (`dmn-optimizer`).

<a id="contents-section-14"></a>
## P9 — Production Data Quality DMN Corpus

**Goal:** build a production-grade DMN decision model corpus specifically designed for automated Data Quality checks and validation reporting.

Work items:

| ID | Work item | State | Evidence / Target |
| --- | --- | --- | --- |
| P9.1 | Field hygiene & format validation DMN (`dq-field-validation.dmn`: regex, IBAN, SSN, ISO dates, null checks) | `ready` | `dq-field-validation.dmn` |
| P9.2 | Cross-field consistency DMN (`dq-cross-field-consistency.dmn`: date sequence, invoice sum matching) | `ready` | `dq-cross-field-consistency.dmn` |
| P9.3 | Data Quality scoring & anomaly detection DMN (`dq-scoring.dmn`: DQI index, violation reports) | `ready` | `dq-scoring.dmn` |
| P9.4 | Multi-file Data Quality repository integration test asserting structured violation reports | `ready` | `DataQualityCorpusTest` |

Acceptance criteria:

- valid and invalid data inputs produce deterministic structured `QualityViolation` result records;
- models execute identically on interpreter and generated Java.

<a id="contents-section-15"></a>
## P10 — Generic gRPC generation in Java

**Goal:** generate a transport-neutral dynamic evaluation contract (`evaluation.proto`) and a Java gRPC service adapter (`dmn-grpc`) backed by generated high-performance Java decisions.

Work items:

| ID | Work item | State | Evidence / Target |
| --- | --- | --- | --- |
| P10.1 | Transport-neutral `evaluation.proto` contract and gRPC service generator | `done` | `DmnGrpcGenerator`, `evaluation.proto` |
| P10.2 | Bidirectional `Value` protobuf $\leftrightarrow$ Java converter | `done` | `DmnGrpcValueConverter` |
| P10.3 | Generate pure `grpc-java` service stubs delegating to `DmnJavaGenerator` | `done` | `DmnGrpcServiceTest` |
| P10.4 | In-process Netty-free gRPC server integration test suite | `done` | `DmnGrpcServiceTest` |

Acceptance criteria:

- `.proto`, Java service adapter, and conversion code are deterministic;
- transport errors and DMN evaluation errors remain distinguishable;
- generated service tests run in-process against the multi-file corpus;
- business logic is delegated directly to generated zero-reflection Java decisions without XML/FEEL parsing.

<a id="contents-section-16"></a>
## P11 — Pure Spark / Databricks SQL Code Generation (Zero UDF)

**Goal:** generate pure, native Spark / Databricks SQL expressions (`CASE WHEN`, built-in SQL functions, CTE query files `<decision-name>.sql`) (`dmn-generator-sparksql`) from DMN decision models without UDF overhead, delegating all query optimization, Whole-Stage Codegen, and execution tuning entirely to Spark's and Databricks' built-in engines (Catalyst, Tungsten, Photon).

Work items:

| ID | Work item | State | Evidence / Target |
| --- | --- | --- | --- |
| P11.1 | Lower FEEL expressions and decision tables into pure Spark / Databricks SQL `CASE WHEN` and `Column` expressions | `done` | `SparkSqlExpressionEmitter` |
| P11.2 | Generate Spark SQL `StructType` schemas from DMN `ItemDefinition` structures | `done` | `SparkSqlSchemaGenerator` |
| P11.3 | Generate pure Spark / Databricks SQL CTE queries (`<decision-name>.sql`) and `DmnSparkSqlRunner` Java wrapper | `done` | `DmnSparkSqlGenerator` |
| P11.4 | Validate Spark / Databricks execution on Spark session | `done` | `SparkSqlDmnIntegrationTest` |

Acceptance criteria:

- outputs pure, standard Spark / Databricks SQL expressions without Scala/Python/Java UDF wrapper overhead;
- delegates all query optimization, expression vectorization, and Whole-Stage Codegen directly to Spark's and Databricks' built-in query engines (Catalyst, Tungsten, Photon);
- distributed execution scales across Spark/Databricks partitions without per-row object creation or UDF serialization boundaries;
- output values match `DmnInterpreter` and generated Java decisions for the shared corpus.

<a id="contents-section-17"></a>
## P12 — Typed Protobuf and gRPC generation

**Goal:** generate strongly typed Protobuf schemas (`.proto`) and gRPC contracts directly from DMN `ItemDefinition` structures, input declarations, and decision output types (`TypedProtoSchemaGenerator`, `DmnTypedGrpcGenerator`).

Work items:

| ID | Work item | State | Evidence / Target |
| --- | --- | --- | --- |
| P12.1 | Convert DMN `ItemDefinition` context types and primitive scalars to Protobuf `message` definitions | `done` | `TypedProtoSchemaGenerator` |
| P12.2 | Generate strongly-typed `Request` and `Response` Protobuf messages for each root decision | `done` | `TypedProtoSchemaGenerator` |
| P12.3 | Generate typed `service <ModelName>DecisionService` gRPC definitions with `rpc Evaluate<DecisionName>` methods | `done` | `DmnTypedGrpcGenerator` |
| P12.4 | Unit & integration tests asserting generated `.proto` syntax and schema correctness | `done` | `TypedProtoSchemaGeneratorTest` |

Acceptance criteria:

- converts scalar and nested context types into valid `proto3` `message` structures with canonical field tagging;
- generates typed `service` definitions with individual `rpc` methods for each decision;
- generated `.proto` text compiles cleanly under Protobuf compiler tools (`protoc`).

<a id="contents-section-18"></a>
## P13 — Additional Language Generators (Rust, Golang, C++)

**Goal:** leverage the unified Generator SPI and Protobuf IR to build cross-language decision generators (Rust zero-allocation binaries, Golang decision handlers, C++ low-latency decision engines).

<a id="contents-section-19"></a>
## Cross-cutting rules

These constraints apply to every milestone:

1. **Correctness before optimization.** Interpreter and generated backends must pass
   the same observable-behavior tests.
2. **One semantic definition.** Built-ins, numeric rules, null behavior, and value
   conversion must not be independently reinvented by each backend.
3. **Deterministic artifacts.** Compilation order, diagnostics, generated source,
   and Protobuf contracts must be reproducible.
4. **Runtime independence.** Generated execution must not require DMN XML, ANTLR,
   semantic protobuf models, or reflection.
5. **Evidence-based completion.** Tests, benchmarks, ADRs, or API documentation must
   be linked before an item is marked `done`.
6. **Compatibility is explicit.** Public API, Runtime IR serialization, and generated
   contract compatibility are distinct policies and must be documented separately.

<a id="contents-section-20"></a>
## Decisions required

| ID | Decision | Needed by | State | Resolution |
| --- | --- | --- | --- | --- |
| D-001 | First realistic multi-file business domain | P2.9 | resolved | Lending / Credit Eligibility (`credit-application.dmn`, `applicant-score.dmn`, `policy-rules.dmn`) |
| D-002 | Generated Java package and naming policy | P5.1 | open | — |
| D-003 | Shared runtime helper dependency versus fully standalone generated Java | P5.1 | open | — |
| D-004 | Generic gRPC dynamic-value schema | P7 | open | — |
| D-005 | Protobuf compatibility policy for regenerated typed APIs | P8 | open | — |

Material architectural decisions should graduate to an ADR. This table tracks only
when a decision is needed and where its final resolution can be found.

<a id="contents-section-21"></a>
## Change log

Record meaningful plan changes, not routine status transitions already visible in
the milestone tables.

| Date | Change | Reason | Evidence |
| --- | --- | --- | --- |
| 2026-08-02 | Created the living plan and prioritized multi-file compilation before code generation | Real DMN repositories provide the shared correctness target for interpreter, Java, and gRPC work | Current module assessments and test baseline |

<a id="contents-section-22"></a>
## How to maintain this document

When work starts:

1. set the relevant item to `in progress`;
2. keep its scope and acceptance criteria current;
3. add or resolve decision rows if implementation requires architectural choices.

When work completes:

1. verify every acceptance criterion;
2. link the implementation, tests, benchmark, documentation, or ADR in an `Evidence`
   column added to the relevant table when useful;
3. change the item to `done`;
4. update `Last reviewed` and the current phase if the milestone boundary moved;
5. add a change-log entry only when direction, sequencing, or scope changed materially.

During each planning review, compare this document with
[`docs/roadmap.md`](roadmap.md), current [module TODOs](todos/index.md), dated
[audits](audits/index.md), and the actual test suite. The code and executable tests remain
the ultimate source of truth.

# Roadmap

<!-- generated-toc:start -->
## Table of contents

- [Completed foundation](#contents-section-1)
- [XML frontend completion gate](#contents-section-2)
- [v1.0.0 MVP: TCK 100%, Core Optimizers, and Benchmarks](#contents-section-3)
- [v1.1.0: Incubating Module Graduation & Cloud Data Engine Ecosystem](#contents-section-4)
- [Decision Authoring DSL and Canonical Model (IDEA-004)](#contents-section-5)
- [DMN XML Synthesis from Structured DMNIR (Default DMN 1.6)](#contents-section-6)
- [v1.2.0: Portable Structured DMNIR and Authoring](#contents-section-7)
<!-- generated-toc:end -->


<a id="contents-section-1"></a>
## Completed foundation

- [x] Protobuf semantic model
- [x] FEEL text and parsed schemas without reverse dependency
- [x] Replaceable FEEL, expression, boxed-expression, unary-test, and type-constraint nodes
- [x] VTD-XML DMN reader
- [x] Initial DMN XML writer infrastructure and writer tests
- [x] Namespace-aware DMN element dispatch and scoped namespace resolution
- [x] Complete reader/writer symmetry for the current protobuf-supported XML subset
- [x] DMN version and root namespace round trips, including prefixed-DMN output
- [x] Imports, decision services, invocations, decision tables, and boxed expressions
- [x] Item-definition components and constraints
- [x] Documentation and one-level structured extension preservation
- [x] Structured read results, input limits, and malformed/unsupported XML diagnostics
- [x] Opt-in source-location capture
- [x] ANTLR4 FEEL grammar and generated parser
- [x] Protobuf FEEL AST builder
- [x] Depth-first `DmnFeelParser` pass
- [x] Model-aware, multi-error FEEL diagnostics
- [x] Traffic Violation XML-to-FEEL integration test
- [x] `dmn-semantic-analysis` module
- [x] First FEEL name/property-resolution pass
- [x] Traffic Violation semantic-analysis test
- [x] Unified semantic-analysis pipeline
- [x] Named-type resolution and declared-type validation
- [x] FEEL expression and boxed-expression type inference
- [x] Operator and built-in function validation
- [x] Decision-table, BKM, item-definition, and decision-service validation
- [x] DRG dependency analysis, cycle detection, and deterministic compilation order
- [x] Exposed resolved symbol and named-type bindings
- [x] Namespace-indexed cross-model import and reference linking
- [x] Cross-model dependency ordering
- [x] Structured-list projection and structured filter typing
- [x] Range, between, and `in` unary-test validation
- [x] FEEL temporal and duration arithmetic
- [x] `instance of` named-type validation
- [x] Model-indexed decision-table-reference typing and diagnostics
- [x] Legacy single-binding loop compatibility
- [x] Executable, namespace-free Runtime IR lowering for expressions, boxed logic, and linked models
- [x] Typed constant canonicalization and stable built-in operation IDs
- [x] Runtime IR interpreter with dependency scheduling, lexical frames, contexts, functions, and decision tables
- [x] Immutable compiler facade (`DmnCompiler`) and phase-aware diagnostic aggregation
- [x] Resolver-independent model loader boundary for imports (`DmnModelResolver`)
- [x] High-performance Java source code generator (`dmn-generator-java`)
- [x] DMN Technology Compatibility Kit (TCK) runner (`dmn-tck-runner`)
- [x] JMH performance microbenchmarks & DataFaker payload generators (`dmn-benchmarks`)

<a id="contents-section-2"></a>
## XML frontend completion gate

- [x] Preserve QName `typeRef` namespaces during XML read/write
- [x] Diagnose unsupported DMN-namespace content consistently across readers
- [x] Add the multi-version, conformance, namespace-shadowing, and hostile-input matrix
- [x] Run and stabilize the complete reactor
- [x] Publish the supported-subset contract and explicit model-extension boundary

<a id="contents-section-3"></a>
## v1.0.0 MVP: TCK 100%, Core Optimizers, and Benchmarks

- [x] Full OMG DMN 1.5 TCK CL2/CL3 conformance for `DmnInterpreter` and `dmn-generator-java` (3,391/3,391 cases, 6,782/6,782 backend outcomes)
- [x] Generated comparator dispatch uses typed calls and propagates host failures; [HARD-03 evidence](dev/slices/security-correctness-hardening-task-2026-10-24.md#3-generated-comparator-reflection-and-failure-propagation). Explicit external Java invocation remains policy-controlled in the runtime.
- [x] Static optimization pipeline (`dmn-optimizer`): constant folding, algebraic simplification, range/between evaluation optimization
- [x] Differential parity test harness verifying execution equivalence between optimized IR and baseline runtime
- [x] JMH performance benchmark suite (`dmn-benchmarks`) with multi-threaded throughput/latency metrics
- [x] Multi-file DMN sample model suites & Java streaming ingestion/resolution API (`dmn-models`, `DmnStreamBundle`)
- [x] General Availability (GA) publication on Maven Central under `io.finmsg.dmn`

<a id="contents-section-4"></a>
## v1.1.0: Incubating Module Graduation & Cloud Data Engine Ecosystem

- [x] Include Spark SQL and gRPC in the default reactor (implemented; does not alone prove production readiness).
- [x] Native Spark SQL execution and expected results for all 3,391 official optimized TCK cases (100%), zero fallback, failed cases, compilation blocks or errors. See the [conformance record](tck-conformance.md).
- [ ] Broaden native Spark SQL support beyond bounded proven forms when required; unsupported models can still use fallback. See [active backlog](todos/index.md).
- [x] Generate model-specific typed Protobuf contracts and executable Java handlers; all 3,391 official cases pass through typed RPCs. The replacement requires regenerated clients; [separate evidence](tck-conformance.md#typed-grpc-checkpoint-2026-10-29) records scope and compatibility boundaries.
- [ ] Add Spark SQL batch and streaming integration test harness against complex multi-table DMN models
- [ ] Benchmark Spark SQL generated CTE queries against equivalent native PySpark / Scala Spark implementations

<a id="contents-section-5"></a>
## Decision Authoring DSL and Canonical Model (IDEA-004)

- [x] Design and implement concise textual `.decision` DSL grammar (`DecisionDsl.g4` in `dmn-frontend-dsl`) for authoring decision models without DMN XML boilerplate
- [x] Lower the shared source semantic model into authoritative typed, resolved, structured DMNIR with interchange metadata ([ADR-0026](architecture/adr/adr-0026-structured-portable-dmnir.md))
- [x] Generate valid, behaviorally equivalent normalized DMN 1.3 / 1.5 / 1.6 XML from DMNIR; original source syntax, comments and diagrams are not guaranteed
- [x] Direct compilation: `.decision` DSL $\rightarrow$ Runtime IR / DMNIR $\rightarrow$ Interpreters & Code Generators
- [x] Embedded policy tests & invariant examples (`test { given ... expect ... }`) within DSL specifications
- [x] Standalone compiler CLI (`dmn-compiler-*-cli.jar` with `check`, `format`, `test`, `compile`, `export`)
- [ ] Standalone Language Server Protocol (LSP) engine for IDE syntax highlighting, diagnostics, and auto-completion

<a id="contents-section-6"></a>
## DMN XML Synthesis from Structured DMNIR (Default DMN 1.6)

- [x] Synthesize standards-conforming DMN XML from the authoritative structured DMNIR graph; default to 1.6 and explicitly validate selected-version support (1.3 / 1.5 / 1.6 verified across 151/151 TCK artifacts, 3,407/3,407 cases each)
- [x] Preserve exportable structured decision tables, boxed expressions and resolved FEEL logic; derive aggressive target-specific optimizations locally
- [x] Retain stable identities, namespaces, declarations, imports and decision topology needed for source-independent DMN export
- [x] Deterministic round-trip interchange verified across official TCK and multi-file `dmn-models` suites

<a id="contents-section-7"></a>
## v1.2.0: Portable Structured DMNIR and Authoring

- [x] Select one authoritative structured Protobuf DMNIR with portable interchange metadata; user approved Option A on 2026-11-03 ([ADR-0026](architecture/adr/adr-0026-structured-portable-dmnir.md)). This is an architecture decision, not implementation evidence.
- [x] Implement portable structured DMNIR schemas `0.0`–`0.11` in `dmn-ir-protobuf` and bounded wire decoding / semantic graph validation in `dmn-ir-codec`
- [x] Adapt verified DMNIR into Java Runtime IR and prove full 3,407/3,407 TCK parity (`dmnir -> java`) with direct compilation
- [x] Generate DMN 1.3 / 1.5 / 1.6 XML from `.dmnir` artifacts alone (`DmnIrScalarDmnExporter`) and verify 3,407/3,407 TCK round-trip parity plus multi-file `dmn-models` suites
- [x] Deliver concise `.decision` DSL (`dmn-frontend-dsl`, ANTLR4 `DecisionDsl.g4`), canonical formatter, diagnostics, inline `test` blocks, and standalone CLI (`dmn-compiler-*-cli.jar`) through shared semantics; see [Authoring DSL guide](dsl.md)
- [x] Prove independent standalone Rust generation and execution (`dmn-generator-rust`) across all 3,407/3,407 pinned official TCK cases with zero JVM runtime dependency
- [x] Revalidate all 7 execution backends on pinned TCK revision `5cd912f2...` (3,407/3,407 each = 23,849 passing outcomes) and full 15-module Maven reactor verification (`mvn -Pformat verify`)
- [ ] Stabilize `v1` wire compatibility contract and canonical content hashing (post-1.2.0)

Detailed delivery scope, dependencies and acceptance scenarios are in the repository's
`prompts/details/release-1.2-dmnir-work-package.md`.

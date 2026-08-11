# Development Plan

<!-- generated-toc:start -->
## Table of contents

- [Plan metadata](#contents-section-1)
- [Improvement delivery tracks](#contents-section-27)
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
- [P7 — Performance Validation & Load Generation](#contents-section-12)
- [P8 — Static Optimizer Pass](#contents-section-13)
- [P9 — Production Data Quality DMN Corpus](#contents-section-14)
- [P10 — Generic gRPC generation in Java](#contents-section-15)
- [P11 — Pure Spark / Databricks SQL Code Generation (Zero UDF)](#contents-section-16)
- [P12 — Typed Protobuf and gRPC generation](#contents-section-17)
- [P13 — Multi-File DMN Models & Java Streaming API](#contents-section-18)
- [P14 — Governance, CI Lockdown & Release Hardening](#contents-section-19)
- [P15 — SWIFT MT564 Data Quality Reference Showcase](#contents-section-20)
- [P16 — Production Readiness Graduation](#contents-section-21)
- [P17 — Automated Release Train](#contents-section-22)
- [P18 — Documentation and Positioning](#contents-section-23)
- [P19 — Capability Maturity and Evidence Graduation](#contents-section-24)
- [P20 — Additional Language Generators (Rust, Golang, C++)](#contents-section-25)
- [Cross-cutting rules](#contents-section-26)
- [Decisions required](#contents-section-28)
- [Change log](#contents-section-29)
- [How to maintain this document](#contents-section-30)
- [Versioning and Compatibility Policy](#contents-section-31)
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
| Last reviewed | 2026-08-09 |
| Current phase | P15 — SWIFT MT564 Data Quality Reference Showcase (`in progress`) |
| Overall state | Compiler facade, model resolver, runtime IR, Java generator, 100% OMG DMN 1.5/1.6 TCK engine, JMH benchmarks, optimizer, Data Quality Corpus, gRPC generator, Spark SQL CTE generator, and multi-file streaming models established |
| Primary objective | Deliver a full working SWIFT MT564 DQM reference showcase using reusable imported BKMs, a protobuf-defined canonical input, executable scenarios, interpreter/generated-Java parity, and a reference benchmark |
| Next major objective | Resume conformance-evidence, release-safety, production-graduation, and documentation hardening after the MT564 reference outcome |

### Agreed near-term implementation order

The MT564 DQM reference showcase is the deliberate first priority. This is a product-driven exception
to the otherwise preferred order of completing release and evidence hardening first. The showcase
must still avoid unsupported production, certification, or performance claims until the corresponding
gates are complete.

| Phase | Outcome | State | Primary specification/proposal | Exit condition |
| --- | --- | --- | --- | --- |
| 0A | Prepare the MT564 DQM contracts while the protobuf input is pending | `completed` | [DQ-001](improvements/examples/data-quality/reusable-bkm-library-spec.md), [DQ-002](improvements/examples/data-quality/swift-mt564-showcase-spec.md) | Violation/report contract, candidate BKM signatures, rule traceability structure, scenario format, and implementation skeleton ready |
| 0B | Integrate the supplied protobuf contract and deliver the full working MT564 DQM model | `completed` | [DQ-002](improvements/examples/data-quality/swift-mt564-showcase-spec.md) | Reviewed protobuf-to-DMN mapping, reusable imported BKMs, selected-scope MT564 rules, valid/invalid/boundary fixtures, and executable quality report |
| 0C | Prove parity and establish the MT564 reference benchmark | `ready` after 0B | [DQ-004](improvements/examples/data-quality/swift-mt564-benchmark-spec.md), [BENCH-001](improvements/examples/benchmark-evidence/refine-execution-benchmarks-spec.md) | Interpreter/generated-Java correctness parity plus trustworthy latency, throughput, allocation, and retained benchmark evidence |
| 1 | Restore claim and release trust | `ready` after Phase 0 | [Conformance closure](improvements/dmn-conformance-accelerator-closure.md), [release train](improvements/automated-release-train.md), [documentation](improvements/documentation-and-positioning.md) | Safe release controls, strict TCK catalogue accounting, corrected public claims, and initial maturity labels |
| 2 | Establish the production and security baseline | `ready` after Phase 1 | [Production readiness graduation](improvements/production-readiness-graduation.md) | Controlled release-candidate gates, public compatibility boundaries, security/dependency checks, and external-consumer verification |
| 3 | Make conformance and benchmark evidence reusable | `ready` after Phase 2 | [TCK-ACC-002](improvements/examples/conformance-accelerator/actionable-evidence-spec.md), [BENCH-002](improvements/examples/benchmark-evidence/scalability-study-spec.md) | Focused reproduction, structured reports, baseline diffs, corrected scalability matrix, and durable CI evidence |
| 4 | Automate frequent releases | `ready` after Phase 3 | [Automated release train](improvements/automated-release-train.md) | Automated versions, notes, evidence, publication, provenance, and released-artifact consumer test |
| 5 | Consolidate documentation and positioning | `ready` after Phase 3 evidence | [Documentation and positioning](improvements/documentation-and-positioning.md) | Architect/developer/DMN-user views and selling points derive from canonical evidence without duplicated facts |
| 6 | Decide and possibly extract the authoring toolkit | `conditional` after MT564 dogfooding | [DQ-003](improvements/examples/data-quality/authoring-toolkit-spec.md) | Recorded friction proves the smallest validate/test/evaluate workflow is worth supporting, or the tool is explicitly deferred |
| 7 | Graduate capabilities and ratchet quality gates | `proposed` after repeated releases | [Capability maturity](improvements/capability-maturity-and-incubation.md), [production graduation](improvements/production-readiness-graduation.md) | Evidence-backed maturity decisions, stable regression ranges, trustworthy badges, and stronger release gates |

The [pragmatic spec-driven delivery method](improvements/spec-driven-delivery-system.md) applies
throughout these phases. It is not a phase that must finish before product implementation. The MT564
showcase is its first concrete pilot.

### Ordered delivery backlog

The following list expands the phase table into the agreed implementation order. Items remain in the
plan even when another outcome is intentionally moved ahead of them.

1. **Deliver the MT564 DQM reference model.** Complete P15.1-P15.7: protobuf-bound canonical input,
   reusable imported BKMs, full selected-scope rule model, scenario evidence, and interpreter/
   generated-Java parity. Work not requiring the external protobuf starts immediately; input mapping
   and authoritative rules resume as soon as the contract arrives.
2. **Correct the benchmark harness and benchmark MT564.** Complete BENCH-001 before accepting the
   DQ-004 reference results. Remove shared mutable worker state, distinguish direct/adapter/end-to-end
   paths, add allocation evidence, and retain the MT564 baseline outside `target/`.
3. **Make releases and public claims safe.** Execute release `R0`: prevent accidental publication,
   define tag/branch authority, publish only clean reviewed commits, confirm coordinates/versioning,
   and require essential reactor gates.
4. **Close critical TCK catalogue accounting.** Execute TCK-ACC-001/C0. Every discovered entry must
   receive one terminal classification; parsing or compilation failures must never disappear through
   `continue`.
5. **Repair documentation trust.** Execute documentation `P0`: remove unsupported certification
   language, reconcile TCK counts, separate implemented/incubating/planned/historical material, and
   stop manually copying evidence facts.
6. **Establish the maturity contract.** Execute incubation `I0`: accept the maturity vocabulary,
   choose the canonical owner, assign candidate states, and make Spark SQL/gRPC limitations visible.
7. **Establish the production and security baseline.** Execute production-graduation `P0-P1`:
   supported platform matrix, public compatibility boundaries, dependency/license/security checks,
   hostile-input limits, external-consumer verification, installation, and rollback evidence.
8. **Complete the conformance accelerator.** Execute TCK-ACC-002/C1-C2: shared FEEL comparator,
   phase-aware results, focused reproduction, deterministic JSON/Markdown, CI artifacts, and TCK
   revision baseline diffs. Only then mark IDEA-001 implemented.
9. **Produce general scalability evidence.** Execute BENCH-002 after the harness correction: run the
   controlled thread matrix, calculate scaling/efficiency, retain environment metadata, and avoid
   thresholds until normal variance is known.
10. **Automate the release train.** Execute `R1-R3`: versions, categorized release notes, complete
    evidence gates, signing/checksums/provenance, publication, and verification from released
    artifacts. Add badges only when backed by canonical evidence.
11. **Consolidate documentation and positioning.** Complete audience views for architects,
    developers, and DMN users; generate capability/conformance/benchmark projections; synchronize
    navigation/TOCs; and use the MT564 reference showcase as an executable product proof point.
12. **Decide whether to extract the authoring toolkit.** Review the MT564 friction log against DQ-003.
    If justified, start with only multi-file validate/test/evaluate and focused scenario selection;
    otherwise explicitly defer it.
13. **Graduate capabilities and ratchet gates.** After repeated successful releases, make evidence-
    backed maturity decisions, introduce stable regression ranges and coverage thresholds, strengthen
    security/provenance, and publish trustworthy badges.
14. **Reassess additional language generators.** Keep P20 deferred until the reference showcase,
    production release, and evidence infrastructure demonstrate stronger user value than another
    backend.

### Immediate specifications

The next executable specifications are:

1. [DQ-001 - Reusable data-quality BKM library](improvements/examples/data-quality/reusable-bkm-library-spec.md)
   and [DQ-002 - SWIFT MT564 reference showcase](improvements/examples/data-quality/swift-mt564-showcase-spec.md),
   starting with contracts/scenario structure while the protobuf input is pending;
2. protobuf integration and the full DQ-002 rule/scenario implementation when the external contract
   arrives;
3. [BENCH-001 - Correct the execution benchmark harness](improvements/examples/benchmark-evidence/refine-execution-benchmarks-spec.md)
   followed by [DQ-004 - MT564 reference benchmark](improvements/examples/data-quality/swift-mt564-benchmark-spec.md);
4. [TCK-ACC-001 - Trustworthy catalogue accounting](improvements/examples/conformance-accelerator/catalogue-accounting-spec.md)
   plus release `R0` and documentation trust repair;
5. the production/security baseline, followed by TCK-ACC-002 and the remaining release/evidence
   phases above.

DQ-003 authoring-tool implementation is intentionally absent from the immediate list. Its
specification exists so MT564 dogfooding can make the later decision evidence-based.

<a id="contents-section-27"></a>
## Improvement delivery tracks

Every accepted improvement proposal remains explicitly tracked. The phase and ordered-backlog
sections above determine execution order; this table prevents cross-cutting work from disappearing
merely because it is not represented by a new Maven module.

| Improvement track | Current plan position | State | Development-plan ownership |
| --- | --- | --- | --- |
| [Pragmatic spec-driven delivery](improvements/spec-driven-delivery-system.md) | Operating method used from Phase 0 onward | `in progress` | Applied to every active specification; refine from MT564 pilot evidence rather than building a workflow engine |
| [Data-quality patterns and authoring](improvements/data-quality-patterns-and-authoring.md) | Phases 0A-0C; P15 | `in progress` | Full protobuf-bound MT564 DQM, reusable BKMs, parity, reference benchmark, and conditional authoring decision |
| [Trustworthy benchmark evidence and scalability](improvements/benchmark-evidence-and-scalability.md) | Phase 0C and Phase 3; P7/P15.8-P15.9 | `ready` | Correct the harness before MT564 benchmark evidence, then produce the broader scalability matrix |
| [DMN conformance accelerator closure](improvements/dmn-conformance-accelerator-closure.md) | Phase 1 and Phase 3; P6 follow-up | `ready` | Strict catalogue accounting first; actionable classifications, focused reproduction, and durable evidence afterward |
| [Production readiness graduation](improvements/production-readiness-graduation.md) | Phase 2 | `ready` | Release-candidate, security, compatibility, hostile-input, external-consumer, installation, and rollback gates |
| [Pragmatic automated release train](improvements/automated-release-train.md) | Release R0 in Phase 1; R1-R3 in Phase 4 | `ready` | Safe publication first; then versions, release notes, evidence, provenance, publication, and released-consumer verification |
| [Documentation and positioning](improvements/documentation-and-positioning.md) | Trust repair in Phase 1; full consolidation in Phase 5 | `ready` | Correct claims early; later generate audience views, selling points, navigation, and evidence projections |
| [Capability maturity and incubation](improvements/capability-maturity-and-incubation.md) | Contract in Phase 1; graduation/ratchets in Phase 7 | `ready` | Establish maturity vocabulary and ownership early; graduate capabilities only from retained evidence |

Production readiness and release automation are separate but connected tracks:

- **Production readiness graduation defines the gates** a candidate must satisfy.
- **The automated release train executes and records those gates** before publishing artifacts.

The release train must not invent weaker success criteria than the production-readiness contract.

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
| P7 | Performance Validation & Load Generation | `in progress` | P5, P6 | JMH microbenchmarks (`dmn-benchmarks`), phase isolation, complex models (`originations`, `ranked-loan-products`), and DataFaker load generation |
| P8 | Static Optimizer Pass | `done` | P5, P7 | Constant folding, algebraic simplification, and rule pruning passes (`dmn-optimizer`) |
| P9 | Production Data Quality DMN Corpus | `done` | P2, P5 | Real-world Data Quality DMN model corpus (`dq-field-validation`, `dq-cross-field-consistency`, `dq-scoring`) |
| P10 | Generic gRPC generation in Java | `done` | P4, P5 | Transport-neutral `evaluation.proto` and ultra-lean pure `grpc-java` service adapters (`dmn-grpc`) |
| P11 | Pure Spark / Databricks SQL Generator | `done` | P5, P8 | Lowering FEEL and decision tables to pure native Spark / Databricks SQL CTE queries (`dmn-generator-sparksql`) |
| P12 | Typed Protobuf and gRPC generation | `done` | P10, P11 | Strongly-typed Protobuf schemas and gRPC contracts from DMN `ItemDefinition` structures (`TypedProtoSchemaGenerator`) |
| P13 | Multi-File DMN Models & Java Streaming API | `done` | P1, P2 | Multi-file DMN sample suites & streaming ingestion API (`dmn-models`) |
| P14 | Governance, CI Lockdown & Release Hardening | `in progress` | P6, P10 | Tag-gated CI publishing, fail-fast TCK gate, doc reconciliation, open-source license, and backend parity matrix |
| P15 | SWIFT MT564 Data Quality Reference Showcase | `in progress` | P2, P4, P5, P9 | Full working protobuf-bound MT564 DQM model, reusable BKM rules, scenario/parity evidence, and reference benchmark |
| P16 | Production Readiness Graduation | `ready` | P14, P15 | Controlled production candidate with explicit compatibility, security, support, consumer, installation, and rollback evidence |
| P17 | Automated Release Train | `ready` | P16 | Frequent reproducible releases with versions, notes, evidence gates, provenance, publication, and released-consumer verification |
| P18 | Documentation and Positioning | `ready` | P15-P17 evidence | Architect, developer, and DMN-user views plus generated claims, capability, conformance, and benchmark projections |
| P19 | Capability Maturity and Evidence Graduation | `ready` | P16-P18 | Stable/incubating contracts, evidence-backed graduation, trustworthy badges, and quality-gate ratchets |
| P20 | Additional Language Generators (Rust, Go, C++) | `deferred` | P5, P10, P15-P19 | Reassess native Rust, Go, and C++ generators after the reference showcase, production release, and evidence infrastructure |

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
## P7 — Performance Validation & Load Generation

**Goal:** turn performance intentions into repeatable measurements across simple and complex DMN models (`originations`, `ranked-loan-products`).

Work items:

| ID | Work item | State | Evidence / Target |
| --- | --- | --- | --- |
| P7.1 | Baseline scalar & decision-table JMH benchmarks (`ScalarArithmeticBenchmark`, `TrafficViolationBenchmark`, `CreditApprovalBenchmark`) | `done` | `dmn-benchmarks` |
| P7.2 | Complex model performance benchmarks (`originations`, `ranked-loan-products`) | `in progress` | `OriginationsBenchmark`, `dmn-benchmarks` |
| P7.3 | Phase-isolated compiler benchmarks (XML/FEEL Parsing, Semantic Analysis, IR Lowering, Static Optimization) | `in progress` | `CompilerPhaseBenchmark`, `dmn-benchmarks` |
| P7.4 | High-cardinality data-driven load generator for realistic stress testing (`net.datafaker`) | `ready` | `BenchmarkDataGenerator` |

Acceptance criteria:
- measures compilation, parsing, optimization, and execution performance on complex multi-decision models;
- verifies zero-regression throughput and latency across interpreter and generated Java AOT backends;
- provides data-driven load generation for stress and scalability testing.

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
## P13 — Multi-File DMN Models & Java Streaming API

**Goal:** provide multi-file DMN sample suites (`loan-approval`, `order-fulfillment`, `discount-calculation`) and a zero-disk-unpacking Java streaming ingestion API (`DmnStreamBundle`, `DmnStreamResolver`).

Work items:

| ID | Work item | State | Evidence / Target |
| --- | --- | --- | --- |
| P13.1 | Multi-file DMN directory suites in `src/main/resources/models/` | `done` | `loan-approval`, `order-fulfillment`, `discount-calculation` |
| P13.2 | In-memory ZIP/JAR streaming (`fromZip`) without disk unpacking | `done` | `DmnStreamBundle.fromZip` |
| P13.3 | Directory tree (`fromDirectory`) & classpath (`fromClasspath`) streaming | `done` | `DmnStreamBundle.fromDirectory`, `fromClasspath` |
| P13.4 | Stream Map (`fromStreams`) & DMN source collection streaming | `done` | `DmnStreamBundle.fromStreams`, `fromSources` |
| P13.5 | Automatic DRG root entry-point detection (`findRootSource`) | `done` | `DmnStreamBundle.findRootSource` |
| P13.6 | Policy-boundary `DmnModelResolver` implementation | `done` | `DmnStreamResolver` |
| P13.7 | Multi-file streaming test suite | `done` | `DmnStreamBundleTest` |

Acceptance criteria:

- loads multi-file DMN archives directly from ZIP, JAR, Directory, or Stream Map without requiring disk unpacking;
- automatically identifies root DMN decision entry-points across linked models;
- integrates cleanly with `DmnCompiler` and `DmnModelResolver`.

<a id="contents-section-19"></a>
## P14 — Governance, CI Lockdown & Release Hardening

**Goal:** address repository audit findings (ChatGPT & Gemini 3.6 Flash assessments), lock down CI publishing, enforce build quality gates, establish release policies, and formalize backend parity.

Work items:

| ID | Work item | State | Evidence / Target |
| --- | --- | --- | --- |
| P14.1 | Fail-fast TCK suite execution guard (`OfficialTckSuiteTest` fails hard if missing) | `done` | `OfficialTckSuiteTest.java` |
| P14.2 | Lock down CI release deployment (`v*.*.*` tag-gated) and add concurrency cancellation | `done` | `.github/workflows/ci.yml` |
| P14.3 | Single-source documentation reconciliation (14 active modules across all docs) | `done` | `README.md`, `development-plan.md` |
| P14.4 | Apache License 2.0 open-source adoption governance | `done` | `LICENSE` |
| P14.5 | Formalize backend capability and semantic parity matrix | `done` | `docs/architecture/backend-parity-matrix.md` |
| P14.6 | Root POM build quality gates (Maven ≥ 3.9, JDK 25 LTS, JaCoCo, Spotless `-Pformat`, OWASP `-Psecurity-scan`) | `done` | `pom.xml` |
| P14.7 | Upgrade Spark engine dependency to 4.2.0 and resolve transitive convergence | `done` | `pom.xml` `spark.version=4.2.0` |
| P14.8 | Create root `CHANGELOG.md`, `docs/tck-conformance.md`, and Versioning Policy | `done` | `CHANGELOG.md`, `docs/tck-conformance.md` |
| P14.9 | Release profile source and Javadoc packaging (`maven-source-plugin`, `maven-javadoc-plugin`) | `proposed` | `pom.xml` release profile |
| P14.10 | Automated JMH performance regression threshold assertions in CI | `proposed` | `.github/workflows/ci.yml` |
| P14.11 | Automated FEEL parser fuzzing and XML hostile identifier resilience tests | `proposed` | `dmn-feel-parser` test suite |

### Open Points for 1.0.0 Release Candidate

1. **`-sources.jar` and `-javadoc.jar` release artifacts (P14.9):** Configure `maven-source-plugin` and `maven-javadoc-plugin` in the release deployment profile in root `pom.xml` so published GitHub Packages releases contain full source code and Javadoc artifacts.
2. **Automated JMH benchmark performance regression thresholds (P14.10):** Establish automated latency/throughput assertion bounds in CI using `dmn-benchmarks` to flag evaluation performance regressions.
3. **Parser fuzzing & hostile inputs (P14.11):** Build automated fuzz testing for `dmn-feel-parser` AST generation and XML hostile-identifier stress tests in `dmn-frontend-xml`.

<a id="contents-section-20"></a>
## P15 — SWIFT MT564 Data Quality Reference Showcase

**Goal:** deliver a full working, maintained MT564 data-quality management reference model that
accepts the externally supplied protobuf-defined canonical message, composes reusable validation
BKMs, returns structured violations and a quality report, executes equivalently through interpreter
and generated Java, and has reproducible benchmark evidence.

This milestone is the current first priority. It builds on P9 concepts but replaces generic benchmark
fixtures with a reviewed multi-file reference showcase and an explicit protobuf integration boundary.

Work items:

| ID | Work item | State | Dependency/evidence |
| --- | --- | --- | --- |
| P15.1 | Define canonical `QualityViolation`, `QualityReport`, rule metadata, and deterministic ordering | `in progress` | [DQ-001](improvements/examples/data-quality/reusable-bkm-library-spec.md) |
| P15.2 | Receive and review the external MT564 protobuf root message, imports, examples, version, and compatibility semantics | `blocked` | Protobuf contract to be supplied from the user's other project |
| P15.3 | Define and test protobuf-to-DMN canonical input mapping without duplicating the schema contract | `blocked` | P15.2, [DQ-002](improvements/examples/data-quality/swift-mt564-showcase-spec.md) |
| P15.4 | Implement the reusable imported BKM library required by the selected MT564 rule catalogue | `ready` | P15.1, provisional signatures reviewed when P15.2 arrives |
| P15.5 | Implement the full selected-scope MT564 DQM root model and domain-rule model | `blocked` | P15.2-P15.4 plus applicable standards/version and domain review |
| P15.6 | Add valid, invalid, boundary, multi-violation, unsupported-scope, and representative-size scenario fixtures | `in progress` | Scenario structure may start now; authoritative field fixtures require P15.2/P15.5 |
| P15.7 | Prove interpreter and generated-Java structural parity through the public compiler path | `ready` | P15.5-P15.6 |
| P15.8 | Correct relevant benchmark harness state/invocation issues | `ready` | [BENCH-001](improvements/examples/benchmark-evidence/refine-execution-benchmarks-spec.md) |
| P15.9 | Add valid, single-violation, multi-violation, and large-structure JMH cases with retained reference evidence | `ready` | P15.7-P15.8, [DQ-004](improvements/examples/data-quality/swift-mt564-benchmark-spec.md) |
| P15.10 | Publish reference-showcase documentation, supported scope, limitations, rule traceability, and runnable example | `ready` | P15.5-P15.9 |
| P15.11 | Review the authoring friction log and decide whether DQ-003 earned implementation | `conditional` | Completed showcase dogfooding evidence |

Acceptance criteria:

- the supplied protobuf contract is the canonical normalized MT564 input boundary;
- the root DMN imports and invokes reusable domain-neutral validation BKMs;
- the selected MT564 scenario has a reviewed, traceable rule catalogue and explicit unsupported
  variants;
- valid, invalid, boundary, multiple-violation, and representative-size scenarios are deterministic;
- interpreter and generated Java produce structurally equivalent quality reports;
- violations remain authoritative and cannot be hidden by an aggregate score;
- the benchmark distinguishes core evaluation from end-to-end adaptation and retains raw evidence;
- examples contain only synthetic or approved anonymized data;
- documentation presents a reference data-quality showcase, not a complete or certified SWIFT
  validator;
- the authoring toolkit remains conditional on observed friction and does not delay the DQM model.

Implementation details and current decisions are owned by
[the improvement proposal](improvements/data-quality-patterns-and-authoring.md) and its linked specs.
This development-plan milestone owns only priority, coarse status, dependencies, and exit evidence.

<a id="contents-section-21"></a>
## P16 — Production Readiness Graduation

**State:** `ready` after the MT564 reference outcome and Phase 1 trust repairs

**Goal:** graduate the toolkit to a controlled, supportable production candidate using the explicit
compatibility, security, hostile-input, external-consumer, installation, rollback, and operational
gates in the [production-readiness proposal](improvements/production-readiness-graduation.md).

P16 defines the release gates. It does not itself publish artifacts.

<a id="contents-section-22"></a>
## P17 — Automated Release Train

**State:** `ready` after P16 gates are accepted

**Goal:** implement the [pragmatic automated release train](improvements/automated-release-train.md),
starting with safe publication and then automating versions, categorized release notes, evidence,
signing/checksums/provenance, publication, and verification from released artifacts.

P17 executes and records P16 gates; it must not replace them with weaker workflow success criteria.

<a id="contents-section-23"></a>
## P18 — Documentation and Positioning

**State:** `ready`, with trust corrections performed earlier where claims are unsafe

**Goal:** complete the [documentation and positioning proposal](improvements/documentation-and-positioning.md)
using canonical release, conformance, capability, MT564, and benchmark evidence. Provide focused
architect, developer, and DMN-user views while eliminating manually synchronized facts.

<a id="contents-section-24"></a>
## P19 — Capability Maturity and Evidence Graduation

**State:** `ready` for the initial maturity contract; graduation waits for repeated evidence

**Goal:** apply the [capability maturity and incubation policy](improvements/capability-maturity-and-incubation.md),
graduate capabilities only when their production evidence passes, and introduce trustworthy badges,
coverage/performance ranges, and stronger quality ratchets after stable release history exists.

<a id="contents-section-25"></a>
## P20 — Additional Language Generators (Rust, Golang, C++)

**State:** `deferred`

**Goal:** leverage the unified Generator SPI and Protobuf IR to build cross-language decision
generators such as Rust binaries, Go decision handlers, and C++ decision engines when user demand and
retained evidence justify another backend.

P20 is intentionally the final milestone in the current plan. It is reconsidered only after:

- P15 delivers the MT564 reference showcase and benchmark;
- production-readiness and automated-release phases establish a supportable distribution path;
- conformance, benchmark, and documentation evidence are trustworthy and reusable;
- a concrete consumer or product outcome establishes priority over deepening existing backends.

No new language backend should be started merely to expand the module list.

<a id="contents-section-26"></a>
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

<a id="contents-section-28"></a>
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

<a id="contents-section-29"></a>
## Change log

Record meaningful plan changes, not routine status transitions already visible in
the milestone tables.

| Date | Change | Reason | Evidence |
| --- | --- | --- | --- |
| 2026-08-09 | Made P15 MT564 DQM the immediate priority and sequenced the following improvement phases | A full working reference DQM is the selected near-term product outcome; its protobuf input will be supplied externally | [DQ improvement proposal](improvements/data-quality-patterns-and-authoring.md) and linked specifications |
| 2026-08-02 | Created the living plan and prioritized multi-file compilation before code generation | Real DMN repositories provide the shared correctness target for interpreter, Java, and gRPC work | Current module assessments and test baseline |

<a id="contents-section-30"></a>
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

---

<a id="contents-section-31"></a>
## Versioning and Compatibility Policy

Last reviewed: 2026-08-08

### Semantic versioning

The project follows [Semantic Versioning 2.0.0](https://semver.org/):

- **PATCH** — backwards-compatible bug fixes with no API changes
- **MINOR** — backwards-compatible new capabilities; all existing public API preserved
- **MAJOR** — breaking changes to public API; migration guide required

### Public Java API stability

A method, class, or interface is part of the public API when:
- it is `public` or `protected`, **and**
- it resides in a package **not** named `*.internal.*`

All public API additions are tagged with `@since <version>` Javadoc.
Removal requires a major version increment and a deprecation cycle spanning at least one minor release.

### Protobuf schema compatibility contract

The `.proto` schemas in `dmn-protobuf` act as the compiler's stable
semantic contract between modules:

- **Allowed without a major version bump:** adding new optional fields, adding new enum values at the end
- **Requires a major version bump:** removing or renumbering fields, changing field types

Serialized protobuf fixtures in `dmn-tck-runner` serve as golden-file regression guards.

### Supported JDK matrix

| JDK | Status |
| --- | --- |
| JDK 25 LTS | ✅ Required minimum; tested in CI |
| JDK 26+ | Not yet validated; tracked in P13 |

The minimum JDK requirement is enforced by `maven-enforcer-plugin` in the root POM.

### Build toolchain

| Tool | Required minimum | Enforced by |
| --- | --- | --- |
| Apache Maven | 3.9 | `maven-enforcer-plugin` |
| JDK | 25 LTS | `maven-enforcer-plugin` |

### GitHub Packages artifact retention

- **SNAPSHOT** artifacts: retained for the lifetime of the feature branch; not guaranteed stable
- **Release** artifacts (tagged `v*.*.*`): retained indefinitely; immutable once published
- **Supported versions:** the two most recent minor releases receive bug fixes; older releases are community-supported only

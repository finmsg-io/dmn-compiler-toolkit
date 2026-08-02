# Development Plan

This is the living delivery plan for the DMN Compiler Toolkit. It translates the
high-level [roadmap](roadmap.md) into trackable milestones with explicit outcomes,
dependencies, and acceptance evidence.

The plan is intentionally stored beside the code and changed through normal Git
commits and pull requests. It describes the current direction rather than a fixed
promise. Update it whenever implementation evidence or priorities materially change.

## Plan metadata

| Field | Value |
| --- | --- |
| Last reviewed | 2026-08-02 |
| Current phase | P1 — compiler facade and real multi-file models |
| Overall state | compiler foundation established; production execution not yet ready |
| Primary objective | compile, validate, and execute realistic linked DMN model sets |
| Next major objective | generate correct, benchmarkable Java from optimized Runtime IR |

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

## Current baseline

As of the last review, the repository contains:

- a namespace-aware DMN XML reader and writer for the modeled subset;
- an ANTLR-based FEEL parser and protobuf FEEL AST;
- type inference, validation, symbol resolution, and deterministic dependency analysis;
- namespace-indexed cross-model import and reference linking;
- executable Runtime IR lowering for linked models;
- typed constant canonicalization and stable built-in operation IDs;
- an experimental process-local Runtime IR interpreter.

The semantic-analysis suite has 67 passing tests and the Runtime IR suite has 27
passing tests. The repository currently has only one real `.dmn` test fixture,
`TrafficViolation.dmn`; most linked-model tests construct models programmatically.

The main gaps are:

- no public compiler facade or import-loading boundary;
- no realistic multi-file DMN corpus exercised end to end;
- incomplete FEEL/runtime conformance, notably null behavior, filters, temporal
  arithmetic, and decision-table policies;
- no stable name-based compiled-model API;
- no Java, Protobuf API, or gRPC generator;
- no JMH baseline validating performance claims.

## Target delivery architecture

```text
Root DMN + model resolver
          |
          v
Compiler facade
  XML -> FEEL -> semantic model set -> Runtime IR -> optimizer
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

The interpreter and generators must consume the same optimized Runtime IR. gRPC is
a transport adapter around generated Java, not a separate DMN execution engine.

## Milestone overview

| ID | Milestone | State | Depends on | Exit outcome |
| --- | --- | --- | --- | --- |
| P1 | Compiler facade and model resolution | `ready` | current foundation | A root DMN and its imports compile through one supported API |
| P2 | Real multi-file DMN corpus | `ready` | P1 | Valid and invalid linked repositories are tested end to end |
| P3 | Runtime semantic baseline | `proposed` | P2 | Interpreter behavior is a credible correctness oracle |
| P4 | Stable compiled-model API | `proposed` | P1, P3 | Callers use model/input/decision names without internal slot knowledge |
| P5 | Java code generation | `proposed` | P3, P4 | Generated Java matches the interpreter on the shared corpus |
| P6 | Performance validation | `proposed` | P5 | JMH establishes reproducible interpreter and generated-code baselines |
| P7 | Generic gRPC generation | `proposed` | P4, P5 | Generated service exposes dynamic DMN evaluation |
| P8 | Typed Protobuf and gRPC generation | `proposed` | P7 | Eligible DMN types produce deterministic typed service contracts |

## P1 — Compiler facade and model resolution

**Goal:** provide one supported entry point that compiles a root DMN and its
transitive imports while aggregating phase-aware, model-aware diagnostics.

| ID | Work item | State |
| --- | --- | --- |
| P1.1 | Define source identity and `DmnModelResolver` contracts | `ready` |
| P1.2 | Implement filesystem, classpath, and in-memory resolver variants | `proposed` |
| P1.3 | Load transitive imports with deterministic ordering and caching | `proposed` |
| P1.4 | Detect missing, duplicate, ambiguous, and cyclic import structures | `proposed` |
| P1.5 | Add diagnostic severity, phase, and source-model identity | `proposed` |
| P1.6 | Introduce a whole-model-set semantic result | `proposed` |
| P1.7 | Expose `DmnCompiler` and immutable compilation result contracts | `proposed` |

Acceptance criteria:

- one call drives XML, FEEL, semantic analysis, Runtime IR lowering, and optimization;
- imports are resolved through an interface independent of filesystem policy;
- diagnostics identify phase, source file/model, namespace, code, and location when available;
- result ordering and diagnostics are deterministic;
- single-file compilation remains a convenient special case;
- focused unit tests and end-to-end facade tests pass.

## P2 — Real multi-file DMN corpus

**Goal:** replace programmatic-only confidence with version-controlled DMN files that
exercise the complete compiler and runtime boundary.

Initial fixture matrix:

| ID | Scenario | Expected result | State |
| --- | --- | --- | --- |
| P2.1 | Root model imports one decision | compile and execute | `proposed` |
| P2.2 | Three-level transitive import | compile and execute | `proposed` |
| P2.3 | Diamond import | compile once and execute deterministically | `proposed` |
| P2.4 | Imported BKM invocation | compile and execute | `proposed` |
| P2.5 | Imported item definition | type-check and execute | `proposed` |
| P2.6 | Same model name in distinct namespaces | resolve unambiguously | `proposed` |
| P2.7 | Missing or ambiguous import | stable diagnostics | `proposed` |
| P2.8 | Cross-model dependency cycle | stable cycle diagnostic | `proposed` |
| P2.9 | Realistic business repository | assert representative business outputs | `proposed` |

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

## P3 — Runtime semantic baseline

**Goal:** make the Runtime IR interpreter sufficiently conformant to serve as the
reference implementation for generated backends.

Priority slices:

| ID | Work item | State |
| --- | --- | --- |
| P3.1 | FEEL null propagation and three-valued boolean logic | `proposed` |
| P3.2 | Deterministic numeric operations and error behavior | `proposed` |
| P3.3 | Item-aware filter lowering and per-item predicate evaluation | `proposed` |
| P3.4 | Temporal and duration arithmetic accepted by semantic analysis | `proposed` |
| P3.5 | Complete decision-table hit-policy and allowed-value behavior | `proposed` |
| P3.6 | Shared, versioned built-in function catalog and stable dispatch | `proposed` |
| P3.7 | Execution limits and cycle-safe host-value conversion | `proposed` |

Acceptance criteria:

- semantic analysis, Runtime IR, interpreter, and future generators share one
  definition of built-ins and supported operations;
- table-driven conformance tests cover positive, null, and error cases;
- every operation accepted by semantic analysis either executes correctly or is
  rejected earlier with an explicit diagnostic;
- the P2 corpus executes with stable expected results.

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

## P5 — Java code generation

**Goal:** generate readable, deterministic, high-performance Java from optimized
Runtime IR without XML parsing, FEEL parsing, reflection, or Runtime IR tree walking
at execution time.

Delivery slices:

| ID | Work item | State |
| --- | --- | --- |
| P5.1 | Define generator SPI, source layout, naming, and deterministic output rules | `proposed` |
| P5.2 | Generate scalar expressions and direct decision dependencies | `proposed` |
| P5.3 | Generate contexts, lists, functions/BKMs, and boxed logic | `proposed` |
| P5.4 | Generate specialized decision-table control flow | `proposed` |
| P5.5 | Generate typed Java records where DMN types permit | `proposed` |
| P5.6 | Add dynamic-value fallback for open or unsupported external shapes | `proposed` |
| P5.7 | Compile generated sources during tests and run corpus parity assertions | `proposed` |

Acceptance criteria:

- generated output is byte-for-byte deterministic for identical compiler inputs;
- generated sources compile on the supported JDK;
- interpreter and generated Java return equivalent values and errors for the shared corpus;
- hot-path execution uses direct Java control flow and indexed/local values;
- generator-specific optimizations do not redefine FEEL semantics.

## P6 — Performance validation

**Goal:** turn performance intentions into repeatable measurements.

Establish JMH benchmarks for:

- representative scalar expressions;
- decision-table matching;
- BKM/function invocation;
- single-decision and complete-model evaluation;
- interpreter versus generated Java;
- throughput, latency, and allocation rate.

Record the JDK, JVM flags, hardware, warmup, measurement configuration, and model
fixture with every published result. Optimize only after semantic parity is retained
and a benchmark demonstrates a meaningful improvement.

## P7 — Generic gRPC generation

**Goal:** generate a transport-neutral dynamic evaluation contract and a Java gRPC
adapter backed by generated Java decisions.

The first service should support model and decision selection plus recursively typed
dynamic DMN values. It should define explicit mappings for nulls, errors, decimals,
dates, times, durations, lists, and contexts.

Acceptance criteria:

- `.proto`, Java service adapter, and conversion code are deterministic;
- transport errors and DMN evaluation errors remain distinguishable;
- generated service tests run in-process against the P2 corpus;
- business logic is delegated to the same generated Java backend used without gRPC.

## P8 — Typed Protobuf and gRPC generation

**Goal:** generate ergonomic per-model or per-decision Protobuf contracts when DMN
input and output shapes are statically known.

Open design questions that must be resolved before this milestone becomes `ready`:

- mapping FEEL decimals without precision loss;
- representing null versus absent values;
- mapping dates, times, date-times, and both duration families;
- naming and package stability across model evolution;
- compatibility rules for regenerated `.proto` files;
- fallback behavior for `Any`, open contexts, heterogeneous lists, and functions.

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

## Decisions required

| ID | Decision | Needed by | State | Resolution |
| --- | --- | --- | --- | --- |
| D-001 | First realistic multi-file business domain | P2.9 | open | — |
| D-002 | Generated Java package and naming policy | P5.1 | open | — |
| D-003 | Shared runtime helper dependency versus fully standalone generated Java | P5.1 | open | — |
| D-004 | Generic gRPC dynamic-value schema | P7 | open | — |
| D-005 | Protobuf compatibility policy for regenerated typed APIs | P8 | open | — |

Material architectural decisions should graduate to an ADR. This table tracks only
when a decision is needed and where its final resolution can be found.

## Change log

Record meaningful plan changes, not routine status transitions already visible in
the milestone tables.

| Date | Change | Reason | Evidence |
| --- | --- | --- | --- |
| 2026-08-02 | Created the living plan and prioritized multi-file compilation before code generation | Real DMN repositories provide the shared correctness target for interpreter, Java, and gRPC work | Current module assessments and test baseline |

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

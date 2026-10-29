# Module TODOs and active backlog

Last reviewed: 2026-10-29

Module delivery status records the implemented foundation, not completion of all
security, correctness, operational or documentation work. Spark SQL and gRPC are
in the default reactor; that membership alone is not a production-readiness claim.
Historical checkpoints remain dated evidence and do not override the current backlog.

## Active work, in priority order

| ID | Priority | Work item | Status | Primary owner / acceptance |
| --- | --- | --- | --- | --- |
| TCK-EXIT | P1 | Resolve post-results Surefire shutdown warning | final reporting policy verified for full interpreter/Spark; typed rerun pending | [Investigation](dmn-grpc.md#typed-service-execution-and-conformance-measurement); duplicate passed-test XML output removed, console/failure diagnostics retained, unchanged shutdown timeout |
| HARD-02 | P1 | Reproduce and consolidate Spark SQL literal escaping | legacy timeout validation deferred | [Hardening task, section 2](../dev/slices/security-correctness-hardening-task-2026-10-24.md); encoder and both generator regressions passed; default full TCK passed; legacy full TCK has two timeouts, deferred by user; validation remains open |
| COVERAGE | P2 | Measured coverage baselines and enforcement | baseline recorded; enforcement deferred by user | [Hardening task, section 4](../dev/slices/security-correctness-hardening-task-2026-10-24.md); measured thresholds and missing error-path tests |
| P19.1 | P2 | Explicit TCK inventory/outcome regression ratchet | planned | [Runner task](dmn-tck-runner.md); case-key/backend baseline and failure checks, beyond forbidding filters |
| TCK-COLD | P2 | Diagnose prior first-use timeout; evaluate warmup from measurements | planned | [Runner P19.2](dmn-tck-runner.md); later passing runs do not prove the cause or a fix |
| BENCH-003 | P2 | Continuous benchmark regression monitoring | planned; CI acceptance unverified | [Benchmarks](dmn-benchmarks.md) |
| FEEL-FUZZ-CI | P2 | Continuous randomized grammar fuzzing in CI | planned; CI acceptance unverified | [Parser](dmn-feel-parser.md); distinct from existing bounded fuzz tests |
| P17.4 | P2 | Smoke verification of published release packages | planned; CI acceptance unverified | [Consumer smoke tests](dmn-smoke-test.md); distinct from local consumer tests |
| FOLLOW-UP | P2 | Concurrency guarantees, deployment boundaries and gradual refactoring | investigation | [Hardening task, section 6](../dev/slices/security-correctness-hardening-task-2026-10-24.md); do not treat unverified observations as defects |

The two cross-module task documents own implementation acceptance criteria; module
pages link to them. `planned` means delivery remains open; `proposed` means a future
initiative has not been accepted for implementation. `done` applies only to the
named deliverable with evidence. The former `ready` label is not proof of delivery.

## Completed milestones and continuing boundaries

- Typed gRPC TCK conformance achieved (2026-10-29): **3,391/3,391 expected-result
  passes (100%)**, 3,494 completed typed RPCs, zero failed/error/unsupported/blocked
  cases. The directly replaced typed contract requires regenerated clients and
  servers. The old 55.53% baseline is retained separately; see the
  [passing typed record](../tck-conformance.md#typed-grpc-checkpoint-2026-10-29).

- Generic gRPC Evaluate TCK goal achieved locally (2026-10-27): **3,391/3,391**
  expected-result passes, 3,391 RPC attempts and completed responses through
  generated Java handlers over HTTP/2/protobuf. Zero failures/errors/blocks;
  typed services remain separate. See the [conformance record](../tck-conformance.md#generic-grpc-checkpoint-2026-10-27).

- DOC-01 documentation drift remediation completed locally (2026-10-26): public
  claims/evidence refreshed; current links and publication/orphan roles enforced;
  historical journals archived; strict MkDocs severities reviewed. All 49 tooling
  tests passed and all 166 Markdown pages classified, zero unclassified. See
  [completion evidence](../dev/slices/documentation-drift-remediation-task-2026-10-24.md#completion-evidence-2026-10-26).
  Policy/guide changes were committed and pushed in 256715d.

- All-case native Spark TCK goal achieved (2026-10-26): **3,391/3,391**
  official cases executed natively and matched expected results; **100% native,
  zero fallback**, zero failures, errors, compilation blocks or unknown routes.
  No static rejection counted as a pass. Pinned revision:
  `20274cd2ba9cad805db6114f331c743f4b2603a1`. The final invoke_007 binding fix
  changed exactly one route from fallback to native; other outcomes were identical.
  Maven exit 0, no shutdown warning. See
  [section 5 evidence](../dev/slices/security-correctness-hardening-task-2026-10-24.md#5-audit-expected-tck-rejections).
  The implementation, handover and retained accounting are published together in this checkpoint. Broader native FEEL and deferred
  legacy-parser timeouts remain outside this completed milestone.

- HARD-03 typed comparator dispatch and failure propagation are implemented and
  validated (2026-10-25): eight module tests and 25 focused official Java TCK
  cases passed. See [section 3 evidence](../dev/slices/security-correctness-hardening-task-2026-10-24.md#3-generated-comparator-reflection-and-failure-propagation).
  This is focused Java evidence; HARD-02's Spark timeouts remain deferred.

- HARD-01 external Java policy is implemented and validated (2026-10-25): default
  denial before class resolution, explicit exact-target permission, and host-owned
  policy across runtime, generated Java, Spark and gRPC. See the
  [implementation and checkpoint](../dev/slices/security-correctness-hardening-task-2026-10-24.md).
  The full Spark TCK retained 3,391 passing outcomes and identical routes; this run
  emitted a Surefire fork-shutdown warning, retained with its evidence.

- Earlier native milestones excluded compiler-rejected cases from execution.
  Those historical results did not satisfy the current all-3,391 requirement;
  the strict run above supersedes them. Static rejection cannot pass in the
  current runner, and every requested output must be present and match.
- Broader native FEEL support remains a separate extension. Bounded proofs
  can still route models outside this pinned TCK inventory to fallback.
- Spark SQL and gRPC default-reactor membership is implemented. Broader maturity,
  streaming, performance and contract-completion goals remain separately scoped.

## Default reactor modules

| Module | Delivered foundation | Open work / details |
| --- | --- | --- |
| `dmn-protobuf` | Semantic model and bindings | [Module scope](dmn-protobuf.md) |
| `dmn-frontend-xml` | Supported-subset reader/writer | [Deferred model scope](dmn-frontend-xml.md) |
| `dmn-feel-parser` | FEEL parsing and bounded hostile-input tests | [Continuous fuzzing](dmn-feel-parser.md) |
| `dmn-semantic-analysis` | Semantic validation | [Module scope](dmn-semantic-analysis.md); rejection audit above |
| `dmn-runtime-ir` | Runtime IR contracts and lowering | [Future P20 dependency](dmn-runtime-ir.md) |
| `dmn-runtime` | Process-local interpreter | [Module scope](dmn-runtime.md); HARD-01 and concurrency follow-up above |
| `dmn-optimizer` | Optimization passes | [Module scope](dmn-optimizer.md); external-call policy must cover folding |
| `dmn-compiler` | Compilation facade and linking | [P20 proposal owner](dmn-compiler.md); cross-module hardening above |
| `dmn-generator-java` | AOT Java source generation | [Module scope](dmn-generator-java.md); HARD-03 remains open |
| `dmn-generator-sparksql` | Generator, hybrid bridge and native executable-TCK milestone | [Current scope and extensions](dmn-generator-sparksql.md) |
| `dmn-grpc` | Contract, adapter generation and integration tests | [Module scope](dmn-grpc.md); deployment responsibility investigation above |
| `dmn-smoke-test` | Local external-consumer verification | [Published-package verification](dmn-smoke-test.md) |

## Opt-in verification, benchmark and sample modules

| Module | Delivered foundation | Open work / details |
| --- | --- | --- |
| `dmn-tck-runner` | Official runner and accounting | [Ratchet, timeout and rejection work](dmn-tck-runner.md) |
| `dmn-benchmarks` | Benchmark suite and summarization | [CI monitoring](dmn-benchmarks.md) |
| `dmn-models` | Sample models and streaming ingestion helpers | [Module scope](dmn-models.md) |

## Proposed future initiatives

- **P20: cross-language code generation** (Rust/Go/C++): one proposal owned by
  [the compiler backlog](dmn-compiler.md), with Java-generator and Runtime-IR
  dependencies linked there. Do not count the three module entries as three
  separate approved deliveries or promise zero-GC behavior without evidence.
- Authoring DSL/LSP/CLI, optimized-IR XML synthesis, Spark streaming integration
  and comparative Spark benchmarks remain [roadmap](../roadmap.md) work. Revalidate
  their scope and evidence before assigning implementation priority.

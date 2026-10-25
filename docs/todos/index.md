# Module TODOs and active backlog

Last reviewed: 2026-10-25

Module delivery status records the implemented foundation, not completion of all
security, correctness, operational or documentation work. Spark SQL and gRPC are
in the default reactor; that membership alone is not a production-readiness claim.
Historical checkpoints remain dated evidence and do not override the current backlog.

## Active work, in priority order

| ID | Priority | Work item | Status | Primary owner / acceptance |
| --- | --- | --- | --- | --- |
| HARD-02 | P1 | Reproduce and consolidate Spark SQL literal escaping | in progress | [Hardening task, section 2](../dev/slices/security-correctness-hardening-task-2026-10-24.md); encoder and both generator regressions passed; default full TCK passed; legacy full TCK has two timeouts, validation remains open |
| HARD-03 | P1 | Generated comparator reflection and error propagation | open | [Hardening task, section 3](../dev/slices/security-correctness-hardening-task-2026-10-24.md); behavior and ADR reconciliation |
| TCK-REJECT | P1 | Audit the 676 expected compiler rejections | open | [Hardening task, section 5](../dev/slices/security-correctness-hardening-task-2026-10-24.md); case-level attribution to intended errors |
| DOC-01 | P1 | Public documentation, evidence provenance and drift checks | open | [Documentation task](../dev/slices/documentation-drift-remediation-task-2026-10-24.md); public descriptions and CI checks, not duplicate backend implementation |
| COVERAGE | P2 | Measured coverage baselines and enforcement | open | [Hardening task, section 4](../dev/slices/security-correctness-hardening-task-2026-10-24.md); measured thresholds and missing error-path tests |
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

- HARD-01 external Java policy is implemented and validated (2026-10-25): default
  denial before class resolution, explicit exact-target permission, and host-owned
  policy across runtime, generated Java, Spark and gRPC. See the
  [implementation and checkpoint](../dev/slices/security-correctness-hardening-task-2026-10-24.md).
  The full Spark TCK retained 3,391 passing outcomes and identical routes; this run
  emitted a Surefire fork-shutdown warning, retained with its evidence.

- Official optimized Spark SQL TCK native conversion is **done for the executable
  inventory**: 2,715 native (100% of 2,715 executable; 80.06% of all 3,391), zero
  fallback. Separately, 676 compiler-rejected cases (19.94% of all) executed no SQL.
  All 3,391 outcomes passed; failed/errors/unknown zero. See the
  [verified checkpoint](../dev/slices/native-temporal-chains-2026-10-24.md).
- Runner summaries explicitly label compiler rejection as no SQL execution and
  show non-rejected/all-case denominators. That reporting change is done; the
  rejection audit and public-documentation adoption remain open.
- Broader native FEEL support is a separate, demand-driven extension. Bounded proofs
  can still route other models to fallback. Do not reopen the completed official
  TCK conversion milestone or claim unrestricted native support.
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

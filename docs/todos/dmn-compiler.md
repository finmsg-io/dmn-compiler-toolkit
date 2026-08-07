# `dmn-compiler` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-08-07

The compiler module owns public compilation orchestration and resolver-independent model-source contracts.
It provides `DmnCompiler` (one-call facade), `DmnModelResolver` / `InMemoryDmnModelResolver` (bounded transitive model loader), `DmnCompilerDiagnostic` (phase-aware diagnostics), and `DmnCompilationResult` (immutable model-set compilation result).

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P0 | Transitive DMN XML model loader | `done` | `DmnModelResolver`, `InMemoryDmnModelResolver` |
| P0 | Compiler one-call facade | `done` | `DmnCompiler.compile(DmnSource)` |
| P1 | Phase-aware diagnostic contract | `done` | `DmnCompilerDiagnostic` aggregates Load, XML, FEEL, Semantic, and IR diagnostics |
| P1 | 100% OMG DMN 1.5 TCK conformance | `done` | `OfficialTckSuiteTest` (3,611 compliant test cases passing across CL2 & CL3) |

<a id="contents-section-2"></a>
## Next planned work

| Priority | Work item | Status |
| --- | --- | --- |
| P1 | Generic gRPC service adapter generator (`dmn-grpc`) | `ready` |
| P2 | Spark SQL Catalyst expression generator (`dmn-generator-spark`) | `proposed` |
| P2 | Constant folding & IR simplification pass (`dmn-optimizer`) | `proposed` |

Historical context: [compiler assessment](../audits/assessment-implementation-dmn-compiler.md).

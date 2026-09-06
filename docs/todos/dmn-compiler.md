# `dmn-compiler` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-09-06

The `dmn-compiler` module owns public compilation orchestration and resolver-independent model-source contracts.
It provides `DmnCompiler` (one-call compiler facade), `DmnModelResolver` / `FilesystemDmnModelResolver` / `InMemoryDmnModelResolver` (transitive multi-model loader), `DmnCompilerDiagnostic` (phase-aware aggregated diagnostics), and `DmnCompilationResult` (immutable model-set compilation result).

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P1.1 | Transitive DMN XML model loader | `done` | `DmnModelLoader`, `FilesystemDmnModelResolver`, `InMemoryDmnModelResolver` |
| P1.2 | Compiler one-call facade (`DmnCompiler`) | `done` | `DmnCompiler.compile(DmnSource, DmnModelResolver)` |
| P1.3 | Phase-aware diagnostic contract | `done` | `DmnCompilerDiagnostic` aggregates Load, XML, FEEL, Semantic, and IR diagnostics |
| P1.4 | Immutable whole-model-set compilation result | `done` | `DmnCompilationResult` |
| P6 | Full official CL2/CL3 TCK conformance | `done` | 100% strict self-verified conformance (3,391/3,391 cases, 6,782/6,782 outcomes); see TCK-CONF-001 |
| P13 | Multi-file streaming resolver integration | `done` | `DmnStreamResolver` (in `dmn-models`) |
| P14 | Fail-fast TCK execution guard & build quality gates | `done` | `OfficialTckSuiteTest` throws `IllegalStateException` if assets missing |
| P14.11 | Automated FEEL parser fuzzing & hostile-input stress tests | `done` | `FeelParserFuzzAndHostileInputTest` |

<a id="contents-section-2"></a>
## Next planned work

| Priority | Work item | Status | Target / Evidence |
| --- | --- | --- | --- |
| P20 | Cross-language decision generators (Rust, Golang, C++) | `proposed` | Define native code generator SPI and lower Runtime IR to native targets |

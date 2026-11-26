# Capability maturity

This page is the canonical summary of product maturity. Detailed module ownership belongs in the
[module catalogue](modules.md), conformance counts belong in the
[TCK conformance record](tck-conformance.md), and future milestones in the
[roadmap](roadmap.md).

| Capability | Maturity | Evidence boundary |
| --- | --- | --- |
| Compiler, semantic analysis, Runtime IR, interpreter | Established | 100% strict self-verified CL2/CL3 conformance: 3,407/3,407 cases (`5cd912f2...`, 2026-11-24) |
| Portable DMNIR (`dmn-ir-protobuf`, `dmn-ir-codec`) & `dmnir -> java` | Established | 100% strict self-verified CL2/CL3 conformance: 3,407/3,407 cases exported (`151` `.dmnir` files) and executed via portable Java round-trip |
| Java source generator (`dmn-generator-java`) | Established | 100% strict self-verified CL2/CL3 conformance: 3,407/3,407 cases, full parity with reference interpreter |
| Standalone Rust generator (`dmn-generator-rust`) | Incubating | 100% strict self-verified CL2/CL3 conformance: 3,407/3,407 cases compiled & executed standalone from `.dmnir` with zero JVM runtime dependency |
| Optimizer | Established | Unit and integration coverage; static constant folding and rule pruning enabled by default |
| TCK runner | Established | Strict accounting suite verifies all 3,407 CL2/CL3 cases across all 7 backends with zero silent exclusions (`-Ptck`, `-Ptck-spark`, `-Ptck-grpc`, `-Ptck-typed-grpc`) |
| Benchmarks | Established | Verified 3-fork JMH scalability matrix across 1, 2, 4, 8 threads with GC allocation profiling (`results/scalability-summary.md`) |
| Generic gRPC generation | Incubating | Independently measured 3,407/3,407 official passes over HTTP/2/protobuf (`3,407` RPCs); deployment assurance and compatibility remain separate |
| Model-specific typed gRPC generation | Incubating | Independently measured 3,407/3,407 passes through 3,510 typed RPCs (`0` unsupported); deployment assurance remains separate |
| Spark SQL generation | Incubating | Independently measured 3,407/3,407 native official passes (100.00%), zero fallback; broader models and deployments remain bounded |
| DMNIR → DMN 1.3 / 1.5 / 1.6 XML regeneration (`DmnIrScalarDmnExporter`) | Established | Artifact-only XML synthesis verified across all 151 TCK artifacts (3,407/3,407 cases on each of DMN 1.3, 1.5, and 1.6) plus multi-file `dmn-models` suites |
| `.decision` Authoring DSL & standalone CLI (`dmn-frontend-dsl`, `dmn-compiler-*-cli.jar`) | Incubating | ANTLR4 grammar (`DecisionDsl.g4`), canonical formatter, inline `test` blocks, `check`/`format`/`test`/`compile`/`export` CLI, and DSL ↔ DMNIR ↔ DMN 1.3/1.5/1.6 parity tests; see [Authoring DSL guide](dsl.md) |
| Data-quality and multi-file model suites | Established examples | Implemented reference assets and multi-file Java streaming ingestion |
| Go and C++ generators | Planned | No implementation claim |
| Language Server (LSP) and IDE plugins | Planned | ANTLR4 grammar (`DecisionDsl.g4`) is ready for reuse; LSP/IDE plugins are not yet implemented |

Maturity labels describe evidence, compatibility, and supportability—not merely whether source code
exists. Claims for an individual release must link to evidence produced from that release.

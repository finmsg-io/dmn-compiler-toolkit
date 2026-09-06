# Module TODOs

<!-- generated-toc:start -->
## Table of contents

- [Core Reactor Modules](#contents-section-1)
- [Verification & Benchmark Modules](#contents-section-2)
- [Incubator Modules](#contents-section-3)
<!-- generated-toc:end -->

Last reviewed: 2026-09-06

This directory contains the current work list and status per Maven module. These documents track module-local gaps and completed deliverables; cross-module delivery sequencing belongs in the [roadmap](../roadmap.md).

<a id="contents-section-1"></a>
## Core Reactor Modules

| Module | Purpose & Subsystem | Current Status | Details |
| --- | --- | --- | --- |
| `dmn-protobuf` | Protobuf semantic model definitions & Java bindings | `done` | [dmn-protobuf.md](dmn-protobuf.md) |
| `dmn-frontend-xml` | Namespace-aware VTD-XML reader & round-trip writer | `done` | [dmn-frontend-xml.md](dmn-frontend-xml.md) |
| `dmn-feel-parser` | ANTLR4 FEEL 1.5 parser & AST builder | `done` | [dmn-feel-parser.md](dmn-feel-parser.md) |
| `dmn-semantic-analysis` | Scoped symbol resolution, validation, & dependency ordering | `done` | [dmn-semantic-analysis.md](dmn-semantic-analysis.md) |
| `dmn-runtime-ir` | Immutable Runtime IR lowering & frame layout | `done` | [dmn-runtime-ir.md](dmn-runtime-ir.md) |
| `dmn-runtime` | Deterministic process-local Runtime IR interpreter | `done` | [dmn-runtime.md](dmn-runtime.md) |
| `dmn-optimizer` | Constant folding, algebraic simplification, & rule pruning | `done` | [dmn-optimizer.md](dmn-optimizer.md) |
| `dmn-compiler` | Multi-model orchestration, resolvers, & diagnostic facade | `done` | [dmn-compiler.md](dmn-compiler.md) |
| `dmn-generator-java` | High-performance AOT Java bytecode generator | `done` | [dmn-generator-java.md](dmn-generator-java.md) |
| `dmn-smoke-test` | External consumer & outside-reactor verification suite | `done` | [dmn-smoke-test.md](dmn-smoke-test.md) |

<a id="contents-section-2"></a>
## Verification & Benchmark Modules

| Module | Purpose & Subsystem | Current Status | Details |
| --- | --- | --- | --- |
| `dmn-tck-runner` | Official OMG DMN 1.5 TCK runner (3,391/3,391 cases passing) | `done` | [dmn-tck-runner.md](dmn-tck-runner.md) |
| `dmn-benchmarks` | Multi-threaded JMH scalability benchmarks & GC profiling | `done` | [dmn-benchmarks.md](dmn-benchmarks.md) |
| `dmn-models` | Multi-file DMN sample suites & Java streaming ingestion | `done` | [dmn-models.md](dmn-models.md) |

<a id="contents-section-3"></a>
## Incubator Modules

| Module | Purpose & Subsystem | Current Status | Details |
| --- | --- | --- | --- |
| `dmn-grpc` | Pure Java gRPC service stub & dynamic value adapter generator | `done` | [dmn-grpc.md](dmn-grpc.md) |
| `dmn-generator-sparksql` | Pure Spark / Databricks SQL CTE query generator (Spark 4.2.0) | `done` | [dmn-generator-sparksql.md](dmn-generator-sparksql.md) |

An item is marked `done` when completed and verified with test evidence; Git history records completed work.

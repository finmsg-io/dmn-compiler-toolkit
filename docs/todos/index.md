# Module TODOs

<!-- generated-toc:start -->
## Table of contents


<!-- generated-toc:end -->

Last reviewed: 2026-08-07

This directory contains the current work list per active Maven module. These documents track module-local gaps; cross-module delivery sequencing belongs in the [development plan](../development-plan.md), and dated findings belong in [`audits`](../audits/index.md).

| Module | Current Status & Roadmap |
| --- | --- |
| `dmn-compiler` | [Compiler facade and resolution](dmn-compiler.md) — 100% TCK Compliant (`done`) |
| `dmn-protobuf` | [Protobuf semantic contracts](dmn-protobuf.md) — Stable Core (`done`) |
| `dmn-frontend-xml` | [XML frontend](dmn-frontend-xml.md) — Round-Trip Symmetric (`done`) |
| `dmn-feel-parser` | [FEEL parser](dmn-feel-parser.md) — FEEL 1.5 AST Builder (`done`) |
| `dmn-semantic-analysis` | [Semantic analysis](dmn-semantic-analysis.md) — Symbol & Scoped Analysis (`done`) |
| `dmn-runtime-ir` | [Runtime IR](dmn-runtime-ir.md) — Immutable Runtime IR & Optimizer (`done`) |
| `dmn-runtime` | [Runtime interpreter](dmn-runtime.md) — Deterministic Interpreter (`done`) |
| `dmn-generator-java` | [Java code generator](../modules.md#contents-section-8) — Zero-Reflection AOT Java (`done`) |
| `dmn-tck-runner` | [TCK runner](../modules.md#contents-section-9) — 100% OMG DMN 1.5 TCK Certified (`done`) |
| `dmn-benchmarks` | [JMH benchmarks](../modules.md#contents-section-10) — DataFaker Workloads (`done`) |
| `dmn-optimizer` | [Optimizer pass](dmn-optimizer.md) — Constant Folding & Rule Pruning (`done`) |
| `dmn-grpc` | [gRPC adapter](dmn-grpc.md) — Transport-Neutral Pure gRPC Java (`done`) |
| `dmn-generator-sparksql` | [Spark SQL generator](dmn-generator-sparksql.md) — Pure Spark / Databricks SQL CTE Generator (`done`) |

An item is marked `done` when completed and verified with test evidence; Git history records completed work.

# Capability maturity

This page is the canonical summary of product maturity. Detailed module ownership belongs in the
[module catalogue](modules.md), conformance counts belong in the
[TCK conformance record](tck-conformance.md), and future milestones in the
[roadmap](roadmap.md).

| Capability | Maturity | Evidence boundary |
| --- | --- | --- |
| Compiler, semantic analysis, Runtime IR, interpreter | Established | 100% strict self-verified CL2/CL3 conformance: 3,391/3,391 cases, 6,782/6,782 backend outcomes pass |
| Java source generator | Established | 100% strict self-verified CL2/CL3 conformance: 3,391/3,391 cases, full dual-backend parity with reference interpreter |
| Optimizer | Established | Unit and integration coverage; static constant folding and rule pruning enabled by default |
| TCK runner | Established | Strict accounting suite generates canonical `tck-accounting.json` with 100% pass rate (3,391/3,391 cases) and zero silent exclusions |
| Benchmarks | Established | Verified 3-fork JMH scalability matrix across 1, 2, 4, 8 threads with GC allocation profiling (`results/scalability-summary.md`) |
| Generic gRPC generation | Incubating | Independently measured 3,391/3,391 official passes over HTTP/2/protobuf; deployment assurance and compatibility remain separate |
| Model-specific typed gRPC generation | Incubating | Independently measured 3,391/3,391 passes through 3,494 typed RPCs; directly replaced wire contract requires regenerated clients; deployment assurance remains separate |
| Spark SQL generation | Incubating | Independently measured 3,391/3,391 native official passes, zero fallback; broader models and deployments remain bounded |
| Data-quality and multi-file model suites | Established examples | Implemented reference assets and multi-file Java streaming ingestion |
| Rust, Go, and C++ generators | Planned | No implementation claim |
| Standalone CLI and LSP | Planned | No implementation claim |

Maturity labels describe evidence, compatibility, and supportability—not merely whether source code
exists. Claims for an individual release must link to evidence produced from that release.

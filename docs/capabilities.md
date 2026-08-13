# Capability maturity

This page is the canonical summary of product maturity. Detailed module ownership belongs in the
[module catalogue](modules.md), conformance counts belong in the
[TCK conformance record](tck-conformance.md), and delivery order belongs in the
[development plan](development-plan.md).

| Capability | Maturity | Evidence boundary |
| --- | --- | --- |
| Compiler, semantic analysis, Runtime IR, interpreter | Established | Core reactor and interpreter conformance tests; public compatibility and hostile-input graduation remain open |
| Java source generator | Established | Interpreter/Java parity suite; released-artifact consumer verification remains open |
| Optimizer | Established | Unit and integration coverage; broader optimization-equivalence reporting remains desirable |
| TCK runner | Evidence hardening | Self-verified CL2/CL3 execution exists; complete catalogue accounting and reusable reports are the next conformance work |
| Benchmarks | Evidence hardening | JMH suite exists; current multithreaded results are not publication-grade until BENCH-001/BENCH-002 complete |
| Generic and typed gRPC generation | Incubating | Implemented and tested in-reactor; public contract compatibility and a broader parity corpus remain open |
| Spark SQL generation | Incubating | Implemented and integration-tested for its supported corpus; it does not inherit Java/interpreter TCK coverage |
| Data-quality and multi-file model suites | Established examples | Implemented reference assets; MT564 parity and retained benchmark evidence remain current work |
| Rust, Go, and C++ generators | Planned | No implementation claim |
| Standalone CLI and LSP | Planned | No implementation claim |

Maturity labels describe evidence, compatibility, and supportability—not merely whether source code
exists. Claims for an individual release must link to evidence produced from that release.

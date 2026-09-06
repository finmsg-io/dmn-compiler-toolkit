# `dmn-benchmarks` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-09-06

The `dmn-benchmarks` module owns JMH microbenchmarks, reference DMN model provider registries (`ReferenceModelRegistry`), realistic data-driven payload generators (`BenchmarkDataGenerator` via `net.datafaker`), and multi-threaded scalability evidence capture.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P7.1 | Baseline scalar arithmetic JMH benchmark | `done` | `ScalarArithmeticBenchmark` |
| P7.1 | Decision-table matching JMH benchmark | `done` | `TrafficViolationBenchmark` |
| P7.1 | Financial risk DRG evaluation JMH benchmark | `done` | `CreditApprovalBenchmark` |
| P7.1 | Synthetic payload generator (`net.datafaker`) | `done` | `BenchmarkDataGenerator` |
| P7.1 | Benchmark integrity and Data Quality corpus tests | `done` | `BenchmarkIntegrityTest`, `DataQualityCorpusTest` |
| P7.2 | Complex model JMH benchmarks (`originations`, `ranked-loan-products`) | `done` | `OriginationsBenchmark`, `RankedLoanProductsBenchmark` |
| P7.3 | Phase-isolated compiler benchmarks | `done` | `CompilerPhaseBenchmark` (XML, FEEL, Semantic, Lowering, Optimization) |
| BENCH-002 | Multi-threaded scalability suite (1/2/4/8 threads) & GC profiling | `done` | `dmn-benchmarks/scripts/run-scalability.ps1` (`jmh-*.json`) |
| BENCH-002 | Automated scalability summarizer & Markdown/CSV generation | `done` | `tools/summarize_scalability_benchmarks.py` (`scalability-summary.md`) |

<a id="contents-section-2"></a>
## Next planned work

| Priority | Work item | Status | Target / Evidence |
| --- | --- | --- | --- |
| BENCH-003 | Continuous regression monitoring in CI | `ready` | Automated threshold validation against baseline scalability matrix |

# `dmn-benchmarks` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-08-08

The `dmn-benchmarks` module owns JMH microbenchmarks, reference DMN model provider registries (`ReferenceModelRegistry`), and realistic data-driven payload generators (`BenchmarkDataGenerator` via `net.datafaker`).

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P7.1 | Baseline scalar arithmetic JMH benchmark | `done` | `ScalarArithmeticBenchmark` |
| P7.1 | Decision-table matching JMH benchmark | `done` | `TrafficViolationBenchmark` |
| P7.1 | Financial risk DRG evaluation JMH benchmark | `done` | `CreditApprovalBenchmark` |
| P7.1 | Synthetic payload generator (`net.datafaker`) | `done` | `BenchmarkDataGenerator` |
| P7.1 | Benchmark integrity and Data Quality corpus tests | `done` | `BenchmarkIntegrityTest`, `DataQualityCorpusTest` |

<a id="contents-section-2"></a>
## Next planned work

| Priority | Work item | Status | Target / Evidence |
| --- | --- | --- | --- |
| P7.2 | Complex model JMH benchmarks (`originations`, `ranked-loan-products`) | `ready` | `dmn-benchmarks` |
| P7.3 | Phase-isolated compiler benchmarks (XML/FEEL Parsing, Semantic Analysis, IR Lowering, Static Optimization) | `ready` | `dmn-benchmarks` |
| P7.4 | High-cardinality data-driven load generator for realistic stress testing | `ready` | `BenchmarkDataGenerator` |

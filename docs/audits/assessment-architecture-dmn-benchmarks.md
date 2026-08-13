# `dmn-benchmarks` architecture assessment

Assessment date: 2026-08-07  
Status: **IMPLEMENTED** (JMH Microbenchmarks & Reference Workloads)

## Assessment

| Dimension | Rating |
| --- | --- |
| Architecture | ★★★★★ |
| Responsibility | ★★★★★ |
| Coupling | ★★★★★ |
| Cohesion | ★★★★★ |
| Maturity | Production Ready (DataFaker Integration & Pluggable Registry) |

## Scope & Architectural Boundary

`dmn-benchmarks` provides standardized microbenchmarks and reference model workloads evaluating execution throughput (ops/sec) and latency across the **DMN Runtime IR Interpreter** (`DmnRuntime`) and **Generated Java Bytecode** (`dmn-generator-java`).

### Key Architectural Principles Verified

1. **JMH Microbenchmark Harness**: Standardized Java Microbenchmark Harness (JMH 1.37) configuration compiled into an executable `benchmarks.jar` via `maven-shade-plugin`.
2. **DataFaker Payload Generator**: Integrates `net.datafaker:datafaker` (`BenchmarkDataGenerator`) to produce seeded, realistic input payload batches (credit scores, salaries, loan amounts, speeds) to prevent JIT constant-folding or dead-code elimination (DCE).
3. **Pluggable Model Provider (`ReferenceModelRegistry`)**: Scans `src/main/resources/models/*.dmn` for pluggable model benchmark discovery. Any custom `.dmn` XML file added to that directory is automatically discoverable and benchmarkable by name.
4. **Dual-Engine Execution Parity**: Verifies value parity between interpreted execution (`DmnRuntime`) and compiled Java bytecode execution (`dmn-generator-java`) across all benchmark workloads.

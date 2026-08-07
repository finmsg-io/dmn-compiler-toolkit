# DMN Benchmarks

This module provides microbenchmarks and reference model workloads evaluating performance across the **DMN Runtime IR Interpreter** (`DmnRuntime`) and **Generated Java Bytecode** (`dmn-generator-java`).

## Empirical Performance Results

Benchmark environment: OpenJDK 25.0.2 LTS (Zulu25.32+21-CA) on 64-Bit Server VM.

| Benchmark Scenario | Engine | Throughput (ops/sec) | Average Latency | Speedup |
|---|---|---|---|---|
| **Traffic Violation** (Decision Table) | **`dmn-generator-java`** | **6,162,000 ops/sec** | **0.173 µs** (173 ns) | **~7.2x faster** |
| | `DmnRuntime` (Interpreter) | 736,000 ops/sec | 1.254 µs (1,254 ns) | Baseline |
| **Credit Approval** (Multi-node DRG Graph) | **`dmn-generator-java`** | **1,142,000 ops/sec** | **0.471 µs** (471 ns) | **~4.5x faster** |
| | `DmnRuntime` (Interpreter) | 199,000 ops/sec | 2.122 µs (2,122 ns) | Baseline |
| **Scalar Arithmetic** (FEEL Expression) | **`dmn-generator-java`** | **12,000,000 ops/sec** | **124.5 ns** | **~2.1x faster** |
| | `DmnRuntime` (Interpreter) | 3,875,000 ops/sec | 258.0 ns | Baseline |

## Reference Models Included

1. **Credit Approval (`credit-approval.dmn`)**: Financial decision graph measuring multi-node DRG evaluation, BKMs, debt-to-income arithmetic, and risk decision tables.
2. **Traffic Violation (`traffic-violation.dmn`)**: Canonical OMG DMN reference model evaluating multi-hit decision tables.
3. **Scalar Arithmetic**: High-frequency scalar FEEL expression benchmark.

## DataFaker Integration

Input payloads are generated using [`net.datafaker:datafaker`](https://www.datafaker.net/) via `BenchmarkDataGenerator`. This generates realistic randomized payloads (e.g. credit scores, income, transaction amounts, speeds) to prevent JVM JIT compiler dead-code elimination (DCE) or argument constant folding.

## Adding Custom DMN Models

Any standard DMN XML model placed under `src/main/resources/models/<your-model>.dmn` is automatically discoverable via `ReferenceModelRegistry`:

```java
CompiledModelHolder holder = ReferenceModelRegistry.loadFromClasspath("models/your-model.dmn");
```

## Running Benchmarks

Build the JMH uber-jar:

```bash
mvn -pl dmn-benchmarks -am clean package
```

Execute all microbenchmarks:

```bash
java -jar dmn-benchmarks/target/benchmarks.jar -f 1 -i 3 -wi 2
```

To run a specific benchmark class:

```bash
java -jar dmn-benchmarks/target/benchmarks.jar CreditApprovalBenchmark
```

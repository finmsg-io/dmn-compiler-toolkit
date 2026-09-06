# DMN Benchmarks

This module provides microbenchmarks and reference model workloads evaluating performance across the **DMN Runtime IR Interpreter** (`DmnRuntime`) and **Generated Java Bytecode** (`dmn-generator-java`).

## Verified Performance Snapshot (Single-Threaded Baseline)

The following figures reflect the latest verified 3-fork JMH scalability benchmark suite with the GC profiler enabled.

Benchmark environment: OpenJDK 25.0.2 LTS (build 25.0.2+10-LTS, Azul Systems, Inc.) on Intel(R) Core(TM) i7-10510U CPU @ 1.80GHz (4 physical cores, 8 logical threads, 16 GB RAM).

| Benchmark Scenario | Engine | Throughput (`thrpt` [⬆️]) | Average Latency (`avgt` [⬇️]) | Allocation | Speedup vs Interpreter |
|---|---|---|---|---|---|
| **Traffic Violation** (Decision Table) | **`dmn-generator-java`** | **6,395,000 ops/sec** (6.395 ops/µs) | **0.189 µs** (189 ns) | 444 B/op | **~6.7x faster** (throughput) |
| | `DmnRuntime` (Interpreter) | 961,000 ops/sec (0.961 ops/µs) | 1.242 µs (1,242 ns) | 2,515 B/op | Baseline |
| **Credit Approval** (Multi-node DRG Graph) | **`dmn-generator-java`** | **1,979,000 ops/sec** (1.979 ops/µs) | **0.457 µs** (457 ns) | 836 B/op | **~4.4x faster** (throughput) |
| | `DmnRuntime` (Interpreter) | 454,000 ops/sec (0.454 ops/µs) | 1.749 µs (1,749 ns) | 3,200 B/op | Baseline |
| **Originations** (Loan Decisioning) | **`dmn-generator-java`** | **1,303,000 ops/sec** (1.303 ops/µs) | **0.685 µs** (685 ns) | 1,840 B/op | **~6.1x faster** (throughput) |
| | `DmnRuntime` (Interpreter) | 215,000 ops/sec (0.215 ops/µs) | 3.722 µs (3,722 ns) | 7,168 B/op | Baseline |
| **Ranked Loan Products** (Multi-Stage Graph) | **`dmn-generator-java`** | **1,615,000 ops/sec** (1.615 ops/µs) | **0.832 µs** (832 ns) | 1,232 B/op | **~6.2x faster** (throughput) |
| | `DmnRuntime` (Interpreter) | 259,000 ops/sec (0.259 ops/µs) | 4.298 µs (4,298 ns) | 7,249 B/op | Baseline |
| **Scalar Arithmetic** (FEEL Expression) | **`dmn-generator-java`** | **10,950,000 ops/sec** (0.011 ops/ns) | **91.3 ns** | 208 B/op | **~2.2x faster** (throughput) |
| | `DmnRuntime` (Interpreter) | 4,960,000 ops/sec (0.005 ops/ns) | 223.3 ns | 624 B/op | Baseline |

For complete multi-threaded concurrency results across 1, 2, 4, and 8 threads, see the [Scalability Summary](results/scalability-summary.md).

## Reference Models Included

1. **Credit Approval (`credit-approval.dmn`)**: Financial decision graph measuring multi-node DRG evaluation, BKMs, debt-to-income arithmetic, and risk decision tables.
2. **Traffic Violation (`traffic-violation.dmn`)**: Canonical OMG DMN reference model evaluating multi-hit decision tables.
3. **Scalar Arithmetic**: High-frequency scalar FEEL expression benchmark.
4. **Originations**: Multi-stage eligibility, affordability, risk-band, and routing benchmark with deterministic DataFaker workloads.
5. **Ranked Loan Products**: Multi-stage customer segmentation, capacity, term, and product recommendation benchmark.
6. **Compiler Phases**: Isolated XML parsing, semantic analysis, Runtime IR lowering, Runtime IR optimization, and full-pipeline compilation.

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
java -jar dmn-benchmarks/target/benchmarks.jar -f 3 -i 5 -wi 5 -prof gc
```

To run a specific benchmark class:

```bash
java -jar dmn-benchmarks/target/benchmarks.jar CreditApprovalBenchmark
```

Execution benchmark names identify the measured layer:

- `interpreterCore_*`: pre-mapped slot inputs through `DmnRuntime`;
- `generatedDirect_*`: stable `GeneratedDecisionEngine` interface invocation;
- `generatedAdapter_*`: reflective `Method.invoke` adapter overhead included;
- `generatedEndToEnd_*`: named-input-to-slot mapping plus generated evaluation;
- `invocationControl_*`: payload selection/control cost for the smallest model.

Trial state contains compiled models and immutable payload collections. Every JMH worker receives a
thread-scoped `BenchmarkCursor`; no mutable cursor or payload is shared. `DmnRuntime`, immutable
Runtime IR, and generated engine instances are intentionally shared and covered by concurrent parity
tests.

For retained allocation and scalability evidence, run:

```powershell
./dmn-benchmarks/scripts/run-scalability.ps1
```

The equivalent Linux command is `./dmn-benchmarks/scripts/run-scalability.sh`. Both use three forks,
the JMH GC profiler, 1/2/4/8 threads, and write raw JSON plus environment metadata under
`dmn-benchmarks/results/`.

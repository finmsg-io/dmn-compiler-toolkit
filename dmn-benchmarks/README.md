# DMN Benchmarks

This module provides microbenchmarks and reference model workloads evaluating performance across the **DMN Runtime IR Interpreter** (`DmnRuntime`) and **Generated Java Bytecode** (`dmn-generator-java`).

## Historical, pre-BENCH-001 performance snapshot

The following figures predate the corrected harness and are retained only as historical context.
They must not be used as release-grade performance or scalability claims.

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
4. **Originations**: Multi-stage eligibility, affordability, risk-band, and routing benchmark with deterministic DataFaker workloads.
5. **Ranked Loan Products**: Multi-stage customer segmentation, capacity, term, and product recommendation benchmark.
6. **SWIFT MT564 Data Quality**: Valid, single-violation, multi-violation, and representative large normalized-message scenarios.
7. **Compiler Phases**: Isolated XML parsing, semantic analysis, Runtime IR lowering, Runtime IR optimization, and full-pipeline compilation.

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

Run the publication-oriented MT564 matrix with `run-mt564-reference.ps1` or
`run-mt564-reference.sh`. These scripts retain scenario/path JSON and metadata, validate the complete
matrix, and generate deterministic Markdown and CSV summaries. The `largeStructure` fixture includes
128 synthetic option entries, but the current three scalar MT564 rules do not traverse that collection;
its evidence therefore covers normalized input mapping and output adaptation rather than collection
validation scaling.

# DMN Benchmarks

This module provides microbenchmarks and reference model workloads for evaluating performance across the **DMN Interpreter** (`DmnInterpreter`) and **Generated Java Bytecode** (`dmn-generator-java`).

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

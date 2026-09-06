# DMN Consumer Smoke Test (`dmn-smoke-test`)

This module validates the public API and integration surfaces of the DMN Compiler Toolkit from the perspective of an external downstream consumer.

## Purpose

The `dmn-smoke-test` module acts as an automated acceptance verification gate (Gate 6) ensuring that:
1. **Public API Contracts**: Core components (`DmnCompiler`, `DmnCompiledModel`, `DmnRuntime`, `DmnJavaGenerator`) function seamlessly through public dependency coordinates.
2. **Interpreter Evaluation**: Decision models compile and execute deterministically via `DmnRuntime` using typed Java Maps and Protobuf `EvaluationRequest`/`EvaluationResponse` contracts.
3. **AOT Java Code Generation**: Generated Java source code compiles dynamically via `javax.tools.JavaCompiler`, loads into isolated classloaders, and executes with 100% value parity.
4. **Concurrency & Thread-Safety**: Shared compiled models support concurrent multi-threaded execution across worker threads without race conditions or state corruption.
5. **Zero Internal Leakage**: Validates that external applications interact only with public API contracts without requiring internal compiler ASTs or VTD-XML dependencies.

## Test Coverage

| Test Class | Subsystem Under Test | Key Verifications |
| :--- | :--- | :--- |
| [`ConsumerInterpretationSmokeTest`](src/test/java/io/finmsg/dmn/smoke/ConsumerInterpretationSmokeTest.java) | Runtime IR Interpreter (`DmnRuntime`) | - Public metadata and input/decision type reflection<br>- Multi-node DRG financial decisioning evaluation<br>- Boundary, negative, and edge-case validation<br>- High-concurrency multithreaded evaluation (100 parallel tasks) |
| [`ConsumerJavaCodegenSmokeTest`](src/test/java/io/finmsg/dmn/smoke/ConsumerJavaCodegenSmokeTest.java) | AOT Java Generator (`dmn-generator-java`) | - Dynamic Java source emission from Runtime IR<br>- In-memory dynamic compilation with `javax.tools.JavaCompiler`<br>- Strongly-typed `GeneratedDecisionEngine` execution<br>- Identical value parity against the reference interpreter |

## Running the Smoke Tests

Execute the smoke tests as part of the core reactor:

```bash
mvn -pl dmn-smoke-test test
```

Or build and test with all upstream dependencies:

```bash
mvn -pl dmn-smoke-test -am test
```

## Sample Model Included

- `src/test/resources/models/loan-eligibility.dmn`: Multi-stage loan decision graph evaluating debt-to-income arithmetic, credit risk categorization tables, and personalized loan offer recommendations.

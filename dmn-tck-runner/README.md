# DMN TCK Runner

This module is conformance test infrastructure. It reads input and expected result values from DMN TCK-style `*-test-01.xml` files, binds them to adjacent DMN models by name, compiles those models through `DmnCompiler`, and evaluates them across both `DmnRuntime` (interpreter) and `dmn-generator-java` (compiled Java code).

It also supports Spark SQL and the opt-in generic gRPC backend. Generic gRPC
achieved **3,391/3,391 official expected-result passes**, with 3,391 actual RPC
attempts and completed responses over HTTP/2/protobuf (2026-10-27), including all
eleven direct-invocation cases. Generated production handlers execute generated
Java; no interpreter fallback or compilation rejection counts as a pass. See
the [dated conformance record](../docs/tck-conformance.md#generic-grpc-checkpoint-2026-10-27).

After installing the reactor dependencies, reproduce with:

```text
mvn -Ptck -pl dmn-tck-runner -Dtck.backend=grpc -Dtck.requireFull=true -Dtck.timeoutSeconds=120 -Dtest=OfficialTckSuiteTest test
```

The gRPC ledger is `target/tck-accounting-optimized-grpc.json`; retained public
evidence is `docs/assets/tck-accounting-grpc.json`. The historical interpreter/Java
figures below are a separately dated checkpoint, not one combined backend run.

## Official OMG DMN TCK status

The module embeds the official vendor-neutral [OMG DMN Technology Compatibility Kit (TCK)](https://dmn-tck.github.io/tck/) repository as a Git submodule under `src/test/resources/tck-official` (pointing to [`https://github.com/dmn-tck/tck.git`](https://github.com/dmn-tck/tck.git)).

### Strict declared denominator and current result

| Measure | Count |
|---|---:|
| CL2/CL3 XML test files | 146 |
| CL2/CL3 DMN files | 150 |
| Available CL2/CL3 cases | 3,391 |
| Passing cases | **3,391 / 3,391 (100%)** |
| Passing optimized-interpreter outcomes | **3,391 / 3,391 (100%)** |
| Passing optimized-generated-Java outcomes | **3,391 / 3,391 (100%)** |
| Non-passing or excluded outcomes | **0 / 6,782** |

This result is tied to official TCK revision `20274cd2ba9cad805db6114f331c743f4b2603a1`.
The strict runner accounts for every discovered case on both backends; discovery, decoding,
compilation, execution, unsupported, invalid, and excluded outcomes cannot disappear from the
denominator. Only `PASSED` counts as passing.

### Execution & Verification

The suite runner `OfficialTckSuiteTest` dynamically discovers and executes all official test cases:

```text
mvn -Ptck verify
```

For every test case, the test suite:
1. Compiles the DMN XML model into optimized Runtime IR using `DmnCompiler`.
2. Evaluates input bindings through `DmnInterpreter`.
3. Generates and executes Java source using `DmnJavaGenerator` when compilation succeeds.
4. Records a terminal outcome for each case/backend pair and fails the conformance goal for every
   non-passing outcome.

The canonical machine-readable evidence is written to
`dmn-tck-runner/target/tck-accounting-optimized.json`. The `tck` profile selects only the optimized
model and requires the complete official corpus. It is deliberately outside the default reactor so
ordinary Tier-1 development remains fast; CI and release verification run it as a separate gate.

The name-to-slot mapping in `DmnToolkitTckEngine` is internal conformance infrastructure to facilitate direct model evaluation.

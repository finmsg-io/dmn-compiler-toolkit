# DMN TCK Runner

This module is conformance test infrastructure. It reads input and expected result values from DMN TCK-style `*-test-01.xml` files, binds them to adjacent DMN models by name, compiles those models through `DmnCompiler`, and evaluates them across both `DmnRuntime` (interpreter) and `dmn-generator-java` (compiled Java code).

## Official OMG DMN TCK status

The module embeds the official vendor-neutral [OMG DMN Technology Compatibility Kit (TCK)](https://dmn-tck.github.io/tck/) repository as a Git submodule under `src/test/resources/tck-official` (pointing to [`https://github.com/dmn-tck/tck.git`](https://github.com/dmn-tck/tck.git)).

### Strict declared denominator and current result

| Measure | Count |
|---|---:|
| CL2/CL3 XML test files | 146 |
| CL2/CL3 DMN files | 150 |
| Available CL2/CL3 cases | 3,391 |
| Passing cases | **169 (4.98%)** |
| Compilation-blocked/non-passing cases | **3,222 (95.02%)** |
| Passing backend outcomes | 338 / 6,782 |

The former 100% conformance claim was invalid because the runner silently omitted discovery,
decoding, and compilation failures. Only `PASSED` counts as passing.

### Execution & Verification

The suite runner `OfficialTckSuiteTest` dynamically discovers and executes all official test cases:

```text
mvn -pl dmn-tck-runner -am test
```

For every test case, the test suite:
1. Compiles the DMN XML model into optimized Runtime IR using `DmnCompiler`.
2. Evaluates input bindings through `DmnInterpreter`.
3. Generates and executes Java source using `DmnJavaGenerator` when compilation succeeds.
4. Records a terminal outcome for each case/backend pair and fails the conformance goal for every
   non-passing outcome.

The name-to-slot mapping in `DmnToolkitTckEngine` is internal conformance infrastructure to facilitate direct model evaluation.

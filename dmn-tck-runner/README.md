# DMN TCK Runner

This module is conformance test infrastructure. It reads input and expected result values from DMN TCK-style `*-test-01.xml` files, binds them to adjacent DMN models by name, compiles those models through `DmnCompiler`, and evaluates them across both `DmnRuntime` (interpreter) and `dmn-generator-java` (compiled Java code).

## Official OMG DMN TCK Compliance

The module embeds the official vendor-neutral [OMG DMN Technology Compatibility Kit (TCK)](https://dmn-tck.github.io/tck/) repository as a Git submodule under `src/test/resources/tck-official` (pointing to [`https://github.com/dmn-tck/tck.git`](https://github.com/dmn-tck/tck.git)).

### Test Suite Breakdown

| Conformance Level | XML Test Files | Test Cases (`<testCase>`) | Description |
|---|---|---|---|
| **Compliance Level 3 (CL3)** | **118** | **3,467** | Advanced FEEL expressions, boxed contexts, loops, filters, date/time math, UDFs |
| **Compliance Level 2 (CL2)** | **28** | **144** | Standard decision tables and basic literal expressions |
| **Non-Compliant / Experimental** | **2** | **46** | Non-standard vendor extensions |
| **Total Test Suite** | **146** | **3,657** | **3,611 standard compliant test cases** |

### Execution & Verification

The suite runner `OfficialTckSuiteTest` dynamically discovers and executes all official test cases:

```text
mvn -pl dmn-tck-runner -am test
```

For every test case, the test suite:
1. Compiles the DMN XML model into optimized Runtime IR using `DmnCompiler`.
2. Evaluates input bindings through `DmnInterpreter`.
3. Generates and executes zero-reflection Java bytecode using `DmnJavaGenerator`.
4. Asserts 100% value parity between expected outputs, interpreted results, and generated Java results.

The name-to-slot mapping in `DmnToolkitTckEngine` is internal conformance infrastructure to facilitate direct model evaluation.

# DMN TCK Runner

This module is conformance test infrastructure. It reads the input and expected result values from
DMN TCK-style `*-test-01.xml` files, binds them to an adjacent DMN model by name, compiles that model
through `DmnCompiler`, and evaluates it with `DmnRuntime`.

The repository-owned `smoke` fixture keeps the normal build deterministic and offline:

```text
mvn -pl dmn-tck-runner -am test
```

The current slice supports canonical null, boolean, decimal, and string values in the test-case
decoder. Executable smoke coverage currently establishes decimals and a literal decision as the
first passing baseline. Lists, contexts, temporal values, external catalogue discovery, capability
classification, and reports remain later TCK slices.

The name-to-slot mapping in `DmnToolkitTckEngine` is deliberately internal conformance
infrastructure. It should be replaced when the public compiled-model API supports named inputs and
decisions.

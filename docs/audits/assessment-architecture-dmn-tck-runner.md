# Architecture Assessment — `dmn-tck-runner`

Assessment date: 2026-08-07

## Scope & Architectural Boundary

`dmn-tck-runner` is the integration test harness and compliance verification runner module for the DMN Compiler Toolkit.

### Key Principles Verified
1. **Test-Scoped Integration**: `dmn-tck-runner` sits above the compiler, interpreter, and code generator modules, ensuring zero reverse dependencies into core production modules.
2. **Dual-Engine Validation**: Executes identical TCK input vectors against both `DmnInterpreter` and dynamically in-memory compiled `dmn-generator-java` classes, enforcing 100% value parity.
3. **Spec Alignment**: Ingests official OMG DMN TCK XML test definitions directly, verifying real-world DMN 1.5 specification conformance.

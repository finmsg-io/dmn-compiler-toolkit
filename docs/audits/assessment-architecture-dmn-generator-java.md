# Architecture Assessment — `dmn-generator-java`

Assessment date: 2026-08-07

## Scope & Architectural Boundary

`dmn-generator-java` is the primary ahead-of-time (AOT) backend module. It transforms optimized Runtime IR (`RuntimeOptimizedModel`) into standalone Java source files.

### Key Principles Verified
1. **No Downstream Dependencies**: `dmn-generator-java` depends only on `dmn-runtime-ir` and `dmn-compiler`. It contains no references to XML, ANTLR, or Protobuf runtime reflection.
2. **Zero Runtime Framework Overhead**: Generated Java classes depend only on standard JDK 21+ library classes (`java.math.BigDecimal`, `java.util.List`, `java.util.Map`).
3. **Deterministic Emission**: Source code generation is byte-for-byte deterministic for identical compiler inputs.

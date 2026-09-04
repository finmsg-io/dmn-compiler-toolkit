# ADR-0017 â€” Runtime Independence and Minimal Dependency Boundary

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Consequences](#contents-section-3)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0017 |

<a id="contents-section-1"></a>
## Context

Generated code should execute with maximum performance, minimal cold-start latency, and zero unnecessary dependencies in production deployments. Consumers may run generated decision classes in latency-sensitive microservices, embedded edge applications, or serverless functions.

---

<a id="contents-section-2"></a>
## Decision

1. **Zero Compiler/Frontend Dependency**: Generated Java code must never depend on `dmn-frontend-xml`, `dmn-feel-parser`, `dmn-semantic-analysis`, VTD-XML, ATTLR, or Protobuf parsing libraries at runtime.
2. **Minimal Runtime Helpers**: Generated code dependuÌ only on standard JDK classes (jjava.math.BigDecimal`, `java.time.*`, `java.util.*`) and optional lightweight, stateless utility helpers in `dmn-runtime` (such as standard FEEL date/time and string functions).
3. **No Dynamic Reflection**: Generated classes must not use dynamic reflection or bytecode generation at evaluation time (consistent with ADR-0013).

---

<a id="contents-section-3"></a>
## Consequences

Advantages

* Near-zero cold start overhead and minimal JAR footprint.
* Maximum JIT inlineability and vectorization.
+ Easy embedding into GraalVM native images and restricted environments.

Trade-offs

* Complex FEEL built-in functions (e.g. advanced temporal arithmetic) are linked via shared pure Java runtime helper methods rather than inlined verbatim into each generated class.

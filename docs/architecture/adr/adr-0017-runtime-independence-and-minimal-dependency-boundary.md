# ADR-0017: Runtime Independence and Minimal Dependency Boundary

## Status
Accepted

## Context
A primary design goal of the DMN Compiler Toolkit is enabling high-throughput, low-latency execution in diverse deployment topologies (standalone microservices, latency-critical trading loops, Spark distributed jobs, embedded Java applications).
If the runtime library (dmn-runtime) or generated Java code pulls in heavy transitive dependencies (e.g. XML parsers, ANTLR runtime, bytecode manipulation libraries like ByteBuddy/ASM, reflection utilities), it creates dependency bloat, potential classpath conflicts (JAR hell) with consuming applications, and increases cold-start / execution latency.

## Decision
1. **Zero Third-Party Dependencies for Generated Code**: Generated Java source code (dmn-generator-java) shall have **zero** third-party library dependencies. Generated code depends only on standard Java SE types (java.lang.*, java.math.BigDecimal, java.time.*, java.util.*).
2. **Minimal Runtime Dependency Boundary**: The dmn-runtime module shall remain strictly separated from build-time compiler components (dmn-compiler, dmn-frontend-xml, dmn-feel-parser). It shall not depend on ANTLR, VTD-XML, or code generation frameworks.
3. **Transport / Engine Layering**: Optional integrations (gRPC transport in dmn-grpc, Spark SQL generation in dmn-generator-spark-sql) reside in dedicated child modules and never pollute the core execution runtime.

## Consequences
### Positive
- Consuming applications can embed generated decision classes without classpath conflicts or security audit overhead from transitive dependencies.
- Sub-microsecond execution is achievable without classloader overhead or reflection.
- Minimal artifact size and instantaneous cold starts.

### Negative / Trade-offs
- Core FEEL built-in function logic (such as string manipulation, list aggregations, and date arithmetic) must be implemented using standard Java libraries or self-contained runtime utilities.\n
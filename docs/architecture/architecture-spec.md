# DMN Compiler Toolkit

## Architecture Specification

**Version 1.2 — Implementation-Aligned Baseline**

Related normative material:

- [Glossary](../glossary.md) — shared terminology and acronyms.
- [ADR guide and index](adr/generall-adr.md) — accepted decisions and their rationale.

## Document status

This version reflects the implementation available in August 2026.

Status labels:

- **IMPLEMENTED** — present in the codebase and covered by automated tests
- **PARTIAL** — implemented for a defined subset
- **FUTURE** — architectural target without an active implementation

<a id="most-important-requirements"></a>
## Most important requirements

> **Review status: UNREVIEWED PROPOSAL.** These are candidate architectural requirements. They
> become normative only after review; current implementation status is determined by code, tests,
> and the linked implementation-aligned chapters.

| ID | Requirement | Primary detail |
| --- | --- | --- |
| AR-001 | Models accepted by semantic analysis must have explicitly defined observable behavior or be rejected before execution. | [Compiler passes](./chapters/10-Compiler%20Passes.md), [testing](./chapters/15-Testing%20Strategy.md) |
| AR-002 | Identical sources, options, and supported toolchain versions must produce deterministic ordering, diagnostics, Runtime IR, and generated artifacts. | [ADR-0011](adr/adr-0011-deterministic-compilation.md) |
| AR-003 | Runtime execution and generated backends must not depend on DMN XML, ANTLR, or semantic protobuf traversal. | [ADR-0008](adr/adr-0008-runtime-without-xml-knowledge.md), [Runtime IR](./chapters/09-Runtime%20IR.md) |
| AR-004 | Compiler stages must treat their inputs as immutable and return explicit result contracts. | [ADR-0005](adr/adr-0005-immutable-intermediate-representations.md) |
| AR-005 | Diagnostics must carry stable phase, severity, code, source/model identity, path, and location where available. | [ADR-0012](adr/adr-0012-diagnostics-as-first-class-objects.md), [failure model](./chapters/25-Failure%20Model%20and%20Resilience.md) |
| AR-006 | Root models and transitive imports must compile through resolver-independent, deterministic, confined source-loading policies. | [System context](./chapters/22-System%20Context%20and%20External%20Interfaces.md), [security](./chapters/24-Security%20and%20Trust%20Boundaries.md) |
| AR-007 | The interpreter and generated backends must share Runtime IR semantics and pass the same observable-behavior corpus. | [Runtime IR](./chapters/09-Runtime%20IR.md), [testing](./chapters/15-Testing%20Strategy.md) |
| AR-008 | XML, FEEL, model graphs, host values, and evaluation must have configurable deterministic resource limits. | [Security](./chapters/24-Security%20and%20Trust%20Boundaries.md), [failure model](./chapters/25-Failure%20Model%20and%20Resilience.md) |
| AR-009 | Public callers must address models, inputs, and decisions by stable external identities rather than compiler-assigned slots. | [Public API](./chapters/17-Public%20API.md) |
| AR-010 | Public APIs, semantic protobuf, Runtime IR persistence, generated Java, diagnostics, and generated Protobuf contracts must have separate explicit compatibility policies. | [Data lifecycle and compatibility](./chapters/26-Data%20Lifecycle%20Caching%20and%20Compatibility.md), [ADR-0025](adr/adr-0025-runtime-ir-persistence-and-compatibility-boundary.md) |
| AR-011 | Performance claims and optimizations must be supported by reproducible benchmarks without weakening semantic parity. | [Performance](./chapters/16-Performance.md) |
| AR-012 | Production module dependencies must remain acyclic and point toward lower-level contracts; compiler orchestration stays above the stages it coordinates. | [Maven modules](./chapters/05-Maven%20Modules.md), [ADR-0014](adr/adr-0014-maven-multi-module-architecture.md) |

Current modules:

```text
dmn-compiler-toolkit
├── dmn-protobuf
├── dmn-frontend-xml
├── dmn-feel-parser
├── dmn-semantic-analysis
├── dmn-runtime-ir
├── dmn-runtime
├── dmn-compiler
├── dmn-generator-java
└── dmn-tck-runner
```

Current executable compiler path:

```text
DMN XML Source Graph
  → DmnModelResolver / DmnCompiler Facade
  → DmnXmlReader (VTD-XML)
  → Definitions with FEEL text
  → DmnFeelParser (ANTLR4)
  → Definitions with parsed FEEL AST
  → DmnSemanticPipeline / DmnModelSetSemanticAnalyzer
  → typed linked model set, deterministic compilation order, and semantic diagnostics
  → RuntimeIrLowerer (immutable Runtime IR with integer slots)
  ├── DmnInterpreter (process-local runtime execution)
  └── DmnJavaGenerator (zero-reflection Java code generation)
```

The semantic-analysis stage implements reference and structured-property resolution, exposes successful symbol and named-type bindings, performs type and expression analysis, validates operators, functions, imports, and DMN structures, and analyzes dependency graphs across namespace-linked models. Bindings are exposed as an immutable side table rather than persisted in protobuf AST nodes. Structural Runtime IR, typed constants, bound value-slot references, unary/binary operators, boxed logic, and decision tables are implemented. Process-local interpretation (`dmn-runtime`), one-call compiler facade (`dmn-compiler`), high-performance Java code generation (`dmn-generator-java`), and TCK conformance testing (`dmn-tck-runner`) are implemented. Optimization passes and multi-language backends (Rust, Go) remain future stages.

## Table of contents

Core section: [Most important requirements](#most-important-requirements)

1. [Vision](./chapters/01-Vision.md)
2. [Architecture Principles](./chapters/02-Architecture%20Principles.md)
3. [Overall Architecture](./chapters/03-Overall%20Architecture.md)
4. [Logical Component Architecture](./chapters/04-Logical%20Component%20Architecture.md)
5. [Maven Modules](./chapters/05-Maven%20Modules.md)
6. [Package Layout](./chapters/06-Package%20Layout.md)
7. [Semantic Model](./chapters/07-Semantic%20Model.md)
8. [FEEL AST](./chapters/08-FEEL%20AST.md)
9. [Runtime IR](./chapters/09-Runtime%20IR.md)
10. [Compiler Passes](./chapters/10-Compiler%20Passes.md)
11. [XML Frontend](./chapters/11-XML%20Frontend.md)
12. [FEEL Parser](./chapters/12-FEEL%20Parser.md)
13. [Java Generator](./chapters/13-Java%20Generator.md)
14. [Future Generators](./chapters/14-Future%20Generators.md)
15. [Testing Strategy](./chapters/15-Testing%20Strategy.md)
16. [Performance](./chapters/16-Performance.md)
17. [Public API](./chapters/17-Public%20API.md)
18. [Roadmap](./chapters/18-Roadmap.md)
19. [Internal Compiler Architecture](./chapters/19-Internal%20Compiler%20Architecture.md)
20. [Reference Material](./chapters/20-Appendices.md)
21. [Stakeholders and Quality Attributes](./chapters/21-Stakeholders%20and%20Quality%20Attributes.md)
22. [System Context and External Interfaces](./chapters/22-System%20Context%20and%20External%20Interfaces.md)
23. [Deployment and Operational Architecture](./chapters/23-Deployment%20and%20Operational%20Architecture.md)
24. [Security and Trust Boundaries](./chapters/24-Security%20and%20Trust%20Boundaries.md)
25. [Failure Model and Resilience](./chapters/25-Failure%20Model%20and%20Resilience.md)
26. [Data Lifecycle, Caching, and Compatibility](./chapters/26-Data%20Lifecycle%20Caching%20and%20Compatibility.md)
27. [Observability and Supportability](./chapters/27-Observability%20and%20Supportability.md)
28. [Build, Release, and Supply Chain](./chapters/28-Build%20Release%20and%20Supply%20Chain.md)
29. [Architecture Traceability and Conformance](./chapters/29-Architecture%20Traceability%20and%20Conformance.md)
30. [Architecture Risks and Technical Debt](./chapters/30-Architecture%20Risks%20and%20Technical%20Debt.md)

Reference: [Glossary](../glossary.md)

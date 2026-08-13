

# Appendix A — Architecture Decision Records (ADR)

<!-- generated-toc:start -->
## Table of contents

- [A.1 Purpose](#contents-section-1)
- [Accepted decisions](#contents-section-2)
- [ADR lifecycle](#contents-section-3)
- [ADR template](#contents-section-4)
- [Future ADRs](#contents-section-5)
- [ADR management guidelines](#contents-section-6)
<!-- generated-toc:end -->


<a id="contents-section-1"></a>
## A.1 Purpose

Architecture Decision Records (ADRs) capture the rationale behind significant architectural decisions made during the development of the DMN Compiler Toolkit.

Terms used by the ADRs have their normative definitions in the project
[glossary](../../glossary.md).

While the architecture specification describes *what* the system is, ADRs explain *why* it was designed that way.

Each ADR documents:

* the problem being addressed
* the decision that was made
* the alternatives that were considered
* the consequences of the decision

ADRs provide historical context, improve maintainability, and help future contributors understand the reasoning behind architectural choices.

<a id="contents-section-2"></a>
## Accepted decisions

| ADR | Decision |
| --- | --- |
| [ADR-0001](adr-0001-compiler-architecture-instead-of-interpreter.md) | Compiler architecture instead of interpreter |
| [ADR-0002](adr-0002-protobuf-as-internal-semantic-representation.md) | Protobuf as internal semantic representation |
| [ADR-0003](adr-0003-vtd-xml-instead-of-dom-or-jaxb.md) | VTD-XML instead of DOM or JAXB |
| [ADR-0004](adr-0004-antlr-for-feel-parsing.md) | ANTLR for FEEL parsing |
| [ADR-0005](adr-0005-immutable-intermediate-representations.md) | Immutable intermediate representations |
| [ADR-0006](adr-0006-runtime-ir.md) | Runtime IR |
| [ADR-0007](adr-0007-compiler-pass-architecture.md) | Compiler pass architecture |
| [ADR-0008](adr-0008-runtime-without-xml-knowledge.md) | Runtime without XML knowledge |
| [ADR-0009](adr-0009-integer-based-runtime-references.md) | Integer-based runtime references |
| [ADR-0010](adr-0010-multi-backend-architecture.md) | Multi-backend architecture |
| [ADR-0011](adr-0011-deterministic-compilation.md) | Deterministic compilation |
| [ADR-0012](adr-0012-diagnostics-as-first-class-objects.md) | Diagnostics as first-class objects |
| [ADR-0013](adr-0013-no-reflection-in-generated-code.md) | No reflection in generated code |
| [ADR-0014](adr-0014-maven-multi-module-architecture.md) | Maven multi-module architecture |
| [ADR-0015](adr-0015-open-extension-model.md) | Open extension model |
| [ADR-0025](adr-0025-runtime-ir-persistence-and-compatibility-boundary.md) | Runtime IR persistence and compatibility boundary |

---

<a id="contents-section-3"></a>
## ADR lifecycle

Each Architecture Decision Record progresses through one of the following states.

| Status     | Description                               |
| ---------- | ----------------------------------------- |
| Proposed   | Decision under discussion                 |
| Accepted   | Approved and implemented                  |
| Superseded | Replaced by another ADR                   |
| Deprecated | No longer recommended but still supported |
| Rejected   | Considered but intentionally not adopted  |

---

<a id="contents-section-4"></a>
## ADR template

Every Architecture Decision Record should follow a consistent structure.

```text
ADR-XXXX

Title

Status

Context

Decision

Alternatives Considered

Consequences

References
```

---

<a id="contents-section-5"></a>
## Future ADRs

The following topics are expected to require additional Architecture Decision Records as the project evolves:

| ADR      | Topic                                      |
| -------- | ------------------------------------------ |
| ADR-0016 | Incremental compilation strategy           |
| ADR-0017 | Runtime caching model                      |
| ADR-0018 | Bytecode generation vs. source generation  |
| ADR-0019 | GraalVM native image support               |
| ADR-0020 | Distributed execution model (Spark/Flink)  |
| ADR-0021 | WebAssembly backend                        |
| ADR-0022 | Plugin and extension loading mechanism     |
| ADR-0023 | Compiler observability and metrics         |
| ADR-0024 | Language Server Protocol (LSP) integration |

---

<a id="contents-section-6"></a>
## ADR management guidelines

Architecture Decision Records should be:

* version-controlled alongside the source code
* immutable once accepted (except for status updates)
* referenced from relevant documentation and code where appropriate
* reviewed as part of major architectural changes

When a decision is replaced, the original ADR should be marked **Superseded** and linked to its successor, preserving the historical reasoning.

---

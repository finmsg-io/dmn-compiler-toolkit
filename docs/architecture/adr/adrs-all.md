# Appendix A — Architecture Decision Records (ADR)

## A.1 Purpose

Architecture Decision Records (ADRs) capture the rationale behind significant architectural decisions made during the development of the DMN Compiler Toolkit.

While the architecture specification describes *what* the system is, ADRs explain *why* it was designed that way.

Each ADR documents:

* the problem being addressed
* the decision that was made
* the alternatives that were considered
* the consequences of the decision

ADRs provide historical context, improve maintainability, and help future contributors understand the reasoning behind architectural choices.

---

# A.2 ADR Lifecycle

Each Architecture Decision Record progresses through one of the following states.

| Status     | Description                               |
| ---------- | ----------------------------------------- |
| Proposed   | Decision under discussion                 |
| Accepted   | Approved and implemented                  |
| Superseded | Replaced by another ADR                   |
| Deprecated | No longer recommended but still supported |
| Rejected   | Considered but intentionally not adopted  |

---

# A.3 ADR Template

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

# ADR-0001 — Compiler Architecture Instead of Interpreter

**Status**

Accepted

---

## Context

Most existing DMN implementations execute XML documents directly.

This requires repeated parsing, validation, dependency resolution, and FEEL interpretation during runtime.

---

## Decision

The DMN Compiler Toolkit adopts a compiler-based architecture.

Compilation transforms DMN into Runtime IR and optionally generated source code.

Runtime performs execution only.

---

## Alternatives

* XML Interpreter
* Hybrid Interpreter
* Reflection-based execution

---

## Consequences

Advantages

* significantly faster execution
* deterministic runtime
* easier optimization
* multiple code generators

Disadvantages

* more complex compiler
* compilation step required

---

# ADR-0002 — Protobuf as Internal Semantic Representation

**Status**

Accepted

---

## Context

The Semantic Model must be:

* language neutral
* immutable
* serializable
* versionable

---

## Decision

Use Protocol Buffers as the canonical schema for the Semantic Model.

Generated language bindings become immutable compiler data structures.

---

## Alternatives

* Java records
* JAXB classes
* JSON
* Jackson
* Custom POJOs

---

## Consequences

Advantages

* language independence
* compact serialization
* schema evolution
* code generation support

Disadvantages

* build-time code generation
* protobuf dependency

---

# ADR-0003 — VTD-XML Instead of DOM or JAXB

**Status**

Accepted

---

## Context

XML parsing should be fast, memory efficient, and avoid unnecessary object creation.

---

## Decision

Use VTD-XML as the XML frontend implementation.

---

## Alternatives

* DOM
* SAX
* StAX
* JAXB

---

## Consequences

Advantages

* zero-copy navigation
* low memory usage
* fast XPath support
* no object graph creation

Disadvantages

* smaller community
* lower familiarity

---

# ADR-0004 — ANTLR for FEEL Parsing

**Status**

Accepted

---

## Context

FEEL is a complete expression language with a formal grammar.

---

## Decision

ANTLR4 is used to generate the FEEL parser.

---

## Alternatives

* handwritten recursive descent parser
* parser combinators
* JavaCC

---

## Consequences

Advantages

* mature tooling
* grammar readability
* excellent diagnostics
* grammar evolution

Disadvantages

* generated source code
* ANTLR runtime dependency

---

# ADR-0005 — Immutable Intermediate Representations

**Status**

Accepted

---

## Context

Compiler passes should remain deterministic and thread-safe.

---

## Decision

Semantic Model, FEEL AST, and Runtime IR are immutable.

---

## Alternatives

Mutable object graphs.

---

## Consequences

Advantages

* thread safety
* easier testing
* predictable compiler passes
* incremental compilation

Disadvantages

* additional object creation during compilation

---

# ADR-0006 — Runtime IR

**Status**

Accepted

---

## Context

Code generators require a common execution representation.

---

## Decision

Introduce a Runtime Intermediate Representation between semantic analysis and code generation.

---

## Alternatives

Generate Java directly from the Semantic Model.

---

## Consequences

Advantages

* backend independence
* reusable optimizations
* simpler generators

Disadvantages

* one additional compiler stage

---

# ADR-0007 — Compiler Pass Architecture

**Status**

Accepted

---

## Context

The compiler should support future optimizations without modifying existing components.

---

## Decision

Implement every transformation as an independent compiler pass.

---

## Alternatives

Large monolithic compiler.

---

## Consequences

Advantages

* extensibility
* independent testing
* reusable optimizations

Disadvantages

* more classes
* pipeline management

---

# ADR-0008 — Runtime Without XML Knowledge

**Status**

Accepted

---

## Context

Runtime performance should not depend on XML complexity.

---

## Decision

The runtime never parses XML.

---

## Alternatives

Lazy XML interpretation.

---

## Consequences

Advantages

* smaller runtime
* better performance
* fewer dependencies

---

# ADR-0009 — Integer-Based Runtime References

**Status**

Accepted

---

## Context

String lookups are slower than indexed access.

---

## Decision

Runtime IR uses integer identifiers for all executable entities.

---

## Alternatives

* String identifiers
* UUIDs
* Maps

---

## Consequences

Advantages

* cache-friendly
* fast lookup
* reduced memory

---

# ADR-0010 — Multi-Backend Architecture

**Status**

Accepted

---

## Context

Future code generators should reuse the same compiler frontend.

---

## Decision

Every backend consumes Runtime IR.

---

## Supported Backends

* Java
* Rust
* Go
* Spark SQL
* LLVM
* WebAssembly

---

# ADR-0011 — Deterministic Compilation

**Status**

Accepted

---

## Context

Reproducible builds simplify debugging and testing.

---

## Decision

Compilation must always produce identical Runtime IR for identical inputs.

---

## Consequences

* stable testing
* reproducible builds
* easier binary comparison

---

# ADR-0012 — Diagnostics as First-Class Objects

**Status**

Accepted

---

## Context

Compiler errors should be structured rather than free-form log messages.

---

## Decision

Every compiler phase produces `Diagnostic` objects containing severity, code, message, source location, and optional remediation.

---

## Consequences

Advantages

* IDE integration
* machine-readable diagnostics
* improved user experience

---

# ADR-0013 — No Reflection in Generated Code

**Status**

Accepted

---

## Context

Reflection inhibits JVM optimizations and increases runtime overhead.

---

## Decision

Generated code must use direct method calls and strongly typed APIs.

---

## Consequences

Advantages

* better JIT optimization
* lower latency
* simpler debugging

---

# ADR-0014 — Maven Multi-Module Architecture

**Status**

Accepted

---

## Context

The compiler consists of distinct concerns with different dependency requirements.

---

## Decision

Organize the project into independent Maven modules aligned with the compiler pipeline.

---

## Consequences

Advantages

* clear dependency boundaries
* smaller artifacts
* parallel development

---

# ADR-0015 — Open Extension Model

**Status**

Accepted

---

## Context

Organizations may need custom compiler passes, functions, or code generators.

---

## Decision

Provide stable extension points while preserving the integrity of the core pipeline.

---

## Consequences

Advantages

* extensibility
* easier adoption
* reduced need for forks

---

# A.4 Future ADRs

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
| ADR-0025 | Semantic versioning of the Runtime IR      |

---

# A.5 ADR Management Guidelines

Architecture Decision Records should be:

* version-controlled alongside the source code
* immutable once accepted (except for status updates)
* referenced from relevant documentation and code where appropriate
* reviewed as part of major architectural changes

When a decision is replaced, the original ADR should be marked **Superseded** and linked to its successor, preserving the historical reasoning.

---

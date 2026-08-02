
# ADR-0001 — Compiler Architecture Instead of Interpreter

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Alternatives](#contents-section-3)
- [Consequences](#contents-section-4)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0001 |

<a id="contents-section-1"></a>
## Context

Most existing DMN implementations execute XML documents directly.

This requires repeated parsing, validation, dependency resolution, and FEEL interpretation during runtime.

---

<a id="contents-section-2"></a>
## Decision

The DMN Compiler Toolkit adopts a compiler-based architecture.

Compilation transforms DMN into Runtime IR and optionally generated source code.

Runtime performs execution only.

---

<a id="contents-section-3"></a>
## Alternatives

* XML Interpreter
* Hybrid Interpreter
* Reflection-based execution

---

<a id="contents-section-4"></a>
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


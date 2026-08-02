
# ADR-0001 — Compiler Architecture Instead of Interpreter

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0001 |

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


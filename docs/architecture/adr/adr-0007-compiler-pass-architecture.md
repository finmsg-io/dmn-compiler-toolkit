
# ADR-0007 — Compiler Pass Architecture

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0007 |

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


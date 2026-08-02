
# ADR-0005 — Immutable Intermediate Representations

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0005 |

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


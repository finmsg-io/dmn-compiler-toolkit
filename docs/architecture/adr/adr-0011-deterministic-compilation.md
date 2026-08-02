
# ADR-0011 — Deterministic Compilation

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0011 |

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


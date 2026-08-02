
# ADR-0006 — Runtime IR

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0006 |

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


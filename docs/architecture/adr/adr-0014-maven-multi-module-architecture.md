
# ADR-0014 — Maven Multi-Module Architecture

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0014 |

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


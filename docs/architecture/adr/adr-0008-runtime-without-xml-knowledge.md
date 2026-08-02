
# ADR-0008 — Runtime Without XML Knowledge

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0008 |

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


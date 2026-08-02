
# ADR-0009 — Integer-Based Runtime References

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0009 |

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


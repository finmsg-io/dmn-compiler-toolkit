
# ADR-0009 — Integer-Based Runtime References

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
| Decision | ADR-0009 |

<a id="contents-section-1"></a>
## Context

String lookups are slower than indexed access.

---

<a id="contents-section-2"></a>
## Decision

Runtime IR uses integer identifiers for all executable entities.

---

<a id="contents-section-3"></a>
## Alternatives

* String identifiers
* UUIDs
* Maps

---

<a id="contents-section-4"></a>
## Consequences

Advantages

* cache-friendly
* fast lookup
* reduced memory

---


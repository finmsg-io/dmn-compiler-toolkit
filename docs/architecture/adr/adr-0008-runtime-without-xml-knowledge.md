
# ADR-0008 — Runtime Without XML Knowledge

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
| Decision | ADR-0008 |

<a id="contents-section-1"></a>
## Context

Runtime performance should not depend on XML complexity.

---

<a id="contents-section-2"></a>
## Decision

The runtime never parses XML.

---

<a id="contents-section-3"></a>
## Alternatives

Lazy XML interpretation.

---

<a id="contents-section-4"></a>
## Consequences

Advantages

* smaller runtime
* better performance
* fewer dependencies

---


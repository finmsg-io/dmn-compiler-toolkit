
# ADR-0006 — Runtime IR

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
| Decision | ADR-0006 |

<a id="contents-section-1"></a>
## Context

Code generators require a common execution representation.

---

<a id="contents-section-2"></a>
## Decision

Introduce a Runtime Intermediate Representation between semantic analysis and code generation.

---

<a id="contents-section-3"></a>
## Alternatives

Generate Java directly from the Semantic Model.

---

<a id="contents-section-4"></a>
## Consequences

Advantages

* backend independence
* reusable optimizations
* simpler generators

Disadvantages

* one additional compiler stage

---


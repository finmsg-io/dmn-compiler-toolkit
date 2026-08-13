
# ADR-0007 — Compiler Pass Architecture

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
| Decision | ADR-0007 |

<a id="contents-section-1"></a>
## Context

The compiler should support future optimizations without modifying existing components.

---

<a id="contents-section-2"></a>
## Decision

Implement every transformation as an independent compiler pass.

---

<a id="contents-section-3"></a>
## Alternatives

Large monolithic compiler.

---

<a id="contents-section-4"></a>
## Consequences

Advantages

* extensibility
* independent testing
* reusable optimizations

Disadvantages

* more classes
* pipeline management

---


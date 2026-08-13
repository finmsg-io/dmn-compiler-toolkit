
# ADR-0011 — Deterministic Compilation

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Consequences](#contents-section-3)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0011 |

<a id="contents-section-1"></a>
## Context

Reproducible builds simplify debugging and testing.

---

<a id="contents-section-2"></a>
## Decision

Compilation must always produce identical Runtime IR for identical inputs.

---

<a id="contents-section-3"></a>
## Consequences

* stable testing
* reproducible builds
* easier binary comparison

---


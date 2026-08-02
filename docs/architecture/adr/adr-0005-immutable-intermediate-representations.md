
# ADR-0005 — Immutable Intermediate Representations

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
| Decision | ADR-0005 |

<a id="contents-section-1"></a>
## Context

Compiler passes should remain deterministic and thread-safe.

---

<a id="contents-section-2"></a>
## Decision

Semantic Model, FEEL AST, and Runtime IR are immutable.

---

<a id="contents-section-3"></a>
## Alternatives

Mutable object graphs.

---

<a id="contents-section-4"></a>
## Consequences

Advantages

* thread safety
* easier testing
* predictable compiler passes
* incremental compilation

Disadvantages

* additional object creation during compilation

---


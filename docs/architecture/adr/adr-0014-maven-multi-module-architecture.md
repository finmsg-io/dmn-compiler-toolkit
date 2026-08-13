
# ADR-0014 — Maven Multi-Module Architecture

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Consequences](#contents-section-3)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0014 |

<a id="contents-section-1"></a>
## Context

The compiler consists of distinct concerns with different dependency requirements.

---

<a id="contents-section-2"></a>
## Decision

Organize the project into independent Maven modules aligned with the compiler pipeline.

---

<a id="contents-section-3"></a>
## Consequences

Advantages

* clear dependency boundaries
* smaller artifacts
* parallel development

---


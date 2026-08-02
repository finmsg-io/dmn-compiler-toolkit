
# ADR-0012 — Diagnostics as First-Class Objects

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Consequences](#contents-section-3)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0012 |

<a id="contents-section-1"></a>
## Context

Compiler errors should be structured rather than free-form log messages.

---

<a id="contents-section-2"></a>
## Decision

Every compiler phase produces `Diagnostic` objects containing severity, code, message, source location, and optional remediation.

---

<a id="contents-section-3"></a>
## Consequences

Advantages

* IDE integration
* machine-readable diagnostics
* improved user experience

---


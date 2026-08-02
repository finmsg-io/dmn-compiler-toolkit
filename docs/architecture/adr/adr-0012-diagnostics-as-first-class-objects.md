
# ADR-0012 — Diagnostics as First-Class Objects

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0012 |

## Context

Compiler errors should be structured rather than free-form log messages.

---

## Decision

Every compiler phase produces `Diagnostic` objects containing severity, code, message, source location, and optional remediation.

---

## Consequences

Advantages

* IDE integration
* machine-readable diagnostics
* improved user experience

---


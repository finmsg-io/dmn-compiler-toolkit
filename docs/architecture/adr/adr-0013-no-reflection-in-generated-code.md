
# ADR-0013 — No Reflection in Generated Code

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0013 |

## Context

Reflection inhibits JVM optimizations and increases runtime overhead.

---

## Decision

Generated code must use direct method calls and strongly typed APIs.

---

## Consequences

Advantages

* better JIT optimization
* lower latency
* simpler debugging

---


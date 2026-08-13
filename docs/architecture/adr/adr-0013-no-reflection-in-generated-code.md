
# ADR-0013 — No Reflection in Generated Code

<!-- generated-toc:start -->
## Table of contents

- [Context](#contents-section-1)
- [Decision](#contents-section-2)
- [Consequences](#contents-section-3)
<!-- generated-toc:end -->

| Field | Value |
| --- | --- |
| Status | Accepted |
| Decision | ADR-0013 |

<a id="contents-section-1"></a>
## Context

Reflection inhibits JVM optimizations and increases runtime overhead.

---

<a id="contents-section-2"></a>
## Decision

Generated code must use direct method calls and strongly typed APIs.

---

<a id="contents-section-3"></a>
## Consequences

Advantages

* better JIT optimization
* lower latency
* simpler debugging

---


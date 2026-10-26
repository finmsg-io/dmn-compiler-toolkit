
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
Sort comparators dispatch through the same generated `FeelCallable` interface as
FEEL functions, or the existing `BiFunction` adapter for two-argument BKMs.
Comparator exceptions propagate; a returned null retains the runtime's equality
mapping and is distinct from a failed invocation.

This rule governs generated dispatch. Model-defined external Java functions call
the explicit `DmnRuntime.invokeExternalJava` helper, whose policy-controlled
reflection is a deliberate runtime boundary (HARD-01), not generated reflective
method discovery. Production engines deny these calls by default; hosts must
approve exact targets. This ADR does not claim that the entire runtime call graph
is reflection-free. See the [hardening evidence](../../dev/slices/security-correctness-hardening-task-2026-10-24.md).

---

<a id="contents-section-3"></a>
## Consequences

Advantages

* better JIT optimization
* lower latency
* simpler debugging

---


# `dmn-runtime` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Open follow-up work](#open-follow-up-work)
<!-- generated-toc:end -->

Last reviewed: 2026-10-24

`dmn-runtime` is the process-local interpreter for executable Runtime IR models (`RuntimeModel`). It operates with zero runtime dependencies on XML, ANTLR, or Protobuf reflection, evaluating dependency schedules, global and lexical slots, contexts, functions, closures, FEEL built-ins, and decision table hit policies.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P0 | Process-local Runtime IR interpreter | `done` | `DmnRuntime` |
| P0 | FEEL null propagation & 3-valued logic | `done` | Evaluates FEEL nulls, overloads, and temporal operations |
| P0 | Decision table hit policies | `done` | Evaluates `UNIQUE`, `FIRST`, `COLLECT`, `RULE ORDER`, `OUTPUT ORDER` |
| HARD-01 | Default-deny external Java policy with exact-target permission | `done` | [Cross-backend validation, 2026-10-25](../dev/slices/security-correctness-hardening-task-2026-10-24.md) |
| P1 | Full official CL2/CL3 TCK compliance | `done` | 100% strict self-verified conformance (3,391/3,391 cases); see TCK-CONF-001 |
| P1 | Dual-engine value parity with `dmn-generator-java` | `done` | 6,782/6,782 required backend outcomes pass (100% parity) |


## Open follow-up work

The delivered interpreter foundation does not close these cross-module tasks:

| ID | Priority | Work item | Status | Owner |
| --- | --- | --- | --- | --- |
| FOLLOW-UP | P2 | Verify concurrency/ownership guarantees with distinct evaluation inputs and policies | investigation | [Hardening section 6](../dev/slices/security-correctness-hardening-task-2026-10-24.md) |

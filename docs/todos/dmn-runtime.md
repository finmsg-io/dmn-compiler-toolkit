# `dmn-runtime` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
<!-- generated-toc:end -->

Last reviewed: 2026-08-07

`dmn-runtime` is the process-local interpreter for executable Runtime IR models (`RuntimeModel`). It operates with zero runtime dependencies on XML, ANTLR, or Protobuf reflection, evaluating dependency schedules, global and lexical slots, contexts, functions, closures, FEEL built-ins, and decision table hit policies.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P0 | Process-local Runtime IR interpreter | `done` | `DmnRuntime` |
| P0 | FEEL null propagation & 3-valued logic | `done` | Evaluates FEEL nulls, overloads, and temporal operations |
| P0 | Decision table hit policies | `done` | Evaluates `UNIQUE`, `FIRST`, `COLLECT`, `RULE ORDER`, `OUTPUT ORDER` |
| P1 | 100% OMG DMN 1.5 TCK compliance | `done` | `OfficialTckSuiteTest` (3,611 compliant test cases passing across CL2 & CL3) |
| P1 | Dual-engine value parity with `dmn-generator-java` | `done` | Bit-for-bit output match across all TCK test cases |

Historical context: [runtime assessment](../audits/assessment-implementation-dmn-runtime.md).

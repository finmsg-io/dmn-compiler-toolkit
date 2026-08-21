# `dmn-runtime-ir` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-08-07

Runtime IR is immutable, protobuf-free, and namespace-free at execution time (`RuntimeModel`). It covers all modeled FEEL expressions, boxed decision logic, decision tables, and BKM functions. Model-set lowering (`RuntimeIrLowerer`), lexical frames, dependency topological schedules, indexed contexts, constant pooling, and stable built-in operation IDs (`RuntimeIrOptimizer`) are fully implemented.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P0 | Immutable Runtime IR contracts & slot assignment | `done` | `RuntimeModel`, `RuntimeDecision`, `RuntimeExpression` |
| P0 | Semantic-to-IR lowering pass | `done` | `RuntimeIrLowerer` lowers protobuf models to IR |
| P0 | Constant pool & built-in ID optimizer | `done` | `RuntimeIrOptimizer` produces `RuntimeOptimizedModel` |
| P1 | Dual-backend consumer interface | `done` | Consumed by `DmnRuntime` and `dmn-generator-java` |

<a id="contents-section-2"></a>
## Next planned work

| Priority | Work item | Status |
| --- | --- | --- |
| P1 | Constant folding & expression simplification pass (`dmn-optimizer`) | `proposed` |

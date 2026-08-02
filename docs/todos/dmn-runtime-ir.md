# `dmn-runtime-ir` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current work](#contents-section-1)
<!-- generated-toc:end -->

Last reviewed: 2026-08-02

Runtime IR is immutable, protobuf-free, namespace-free at execution time, and covers
all currently modeled FEEL expressions and boxed decision logic. Model-set lowering,
lexical frames, dependencies, indexed contexts, constant pooling, and stable built-in
IDs are implemented.

<a id="contents-section-1"></a>
## Current work

| Priority | Work item | Completion evidence |
| --- | --- | --- |
| P0 | Persist filter item slot/frame metadata required for correct per-item evaluation | Lowering and runtime tests cover `?`, item properties, numeric filters, and captures |
| P1 | Make accepted temporal, duration, numeric, and decision-table semantics explicit enough for every backend | Interpreter and generated-code parity fixtures need no AST/protobuf recovery |
| P1 | Add optimization passes for constant folding and safe expression simplification | Semantic parity tests pass and optimized IR is deterministic |
| P2 | Add requested-decision dependency pruning and decision-table specialization | Tests prove unused graph nodes are removed without changing results |
| P2 | Define generator-facing metadata only when a concrete Java generation slice requires it | Java generator consumes IR without XML, ANTLR, or semantic protobuf dependencies |
| P3 | Expand real multi-model pipeline fixtures and invariant fuzz/property coverage | Invalid aggregates fail early and valid linked fixtures lower deterministically |

Runtime IR remains process-local under ADR-0025. A durable schema is deferred until
there is a compilation-cache or transport requirement.

Historical context: [Runtime IR assessment](../audits/assessment-implementation-dmn-runtime-ir.md).

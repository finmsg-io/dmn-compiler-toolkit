# `dmn-semantic-analysis` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current work](#contents-section-1)
<!-- generated-toc:end -->

Last reviewed: 2026-08-02

Semantic analysis is a compiler-grade baseline for the modeled DMN/FEEL subset,
including persisted bindings, named types, cross-model linking, cycles, and
deterministic compilation order.

<a id="contents-section-1"></a>
## Current work

| Priority | Work item | Completion evidence |
| --- | --- | --- |
| P1 | Add severity and source-model identity to diagnostics and strengthen deduplication | Linked models cannot collide by path and success ignores non-error diagnostics |
| P1 | Introduce a first-class whole-model-set result | Compiler facade consumes aggregate models, bindings, diagnostics, and order directly |
| P2 | Add negative linked-model fixtures: cycles, diamonds, ambiguity, multi-hop references, and imported BKMs | Actual multi-file DMNs assert stable results and diagnostics |
| P2 | Establish one versioned built-in catalog shared with Runtime IR and backends | Validation and execution use stable operation IDs, arity, overload, and null rules |
| P2 | Decompose large analyzers after shared traversal behavior is protected by tests | Schema evolution has one obvious exhaustive traversal path |
| P3 | Document protobuf input/output ownership and preservation guarantees | Pass contracts state copying, mutation, and preserved fields explicitly |

Historical context: [semantic-analysis assessment](../audits/assessment-implementation-dmn-semantic-analysis.md).

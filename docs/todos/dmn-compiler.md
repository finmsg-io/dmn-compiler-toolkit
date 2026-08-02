# `dmn-compiler` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current work](#contents-section-1)
<!-- generated-toc:end -->

Last reviewed: 2026-08-02

The compiler module owns public compilation orchestration and resolver-independent
model-source contracts. It provides source identity, import requests, deterministic
resolution, root-confined resolver variants, and bounded transitive loading into an
immutable source-and-import graph. Invalid import structures produce stable, ordered
diagnostics while preserving every safely loaded model and resolved edge. Shared compiler
diagnostics carry severity, phase, stable code, source/model identity, and structured import
context; result validity depends only on error severity.
Whole-model-set analysis now parses FEEL behind a phase gate and analyzes every valid source against
the complete repository while retaining per-model order and binding evidence.

<a id="contents-section-1"></a>
## Current work

| Priority | Work item | Completion evidence |
| --- | --- | --- |
| P1 | Adapt and aggregate diagnostics from all compiler stages | XML, FEEL, semantic, lowering, optimization, and generation diagnostics use the shared contract |
| P1 | Expose the immutable compiler facade | One call compiles a root source through optimized Runtime IR |

Historical context: [compiler assessment](../audits/assessment-implementation-dmn-compiler.md).

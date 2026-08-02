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
diagnostics while preserving every safely loaded model and resolved edge.

<a id="contents-section-1"></a>
## Current work

| Priority | Work item | Completion evidence |
| --- | --- | --- |
| P1 | Aggregate phase-aware, source-aware diagnostics | Every diagnostic identifies phase and originating source |
| P1 | Expose the immutable compiler facade and model-set result | One call compiles a root source through optimized Runtime IR |

Historical context: [compiler assessment](../audits/assessment-implementation-dmn-compiler.md).

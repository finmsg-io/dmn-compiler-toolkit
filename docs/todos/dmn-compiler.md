# `dmn-compiler` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current work](#contents-section-1)
<!-- generated-toc:end -->

Last reviewed: 2026-08-02

The compiler module owns public compilation orchestration and resolver-independent
model-source contracts. Source identity, import requests, deterministic resolution
results, plus root-confined filesystem, classpath, and in-memory resolvers.

<a id="contents-section-1"></a>
## Current work

| Priority | Work item | Completion evidence |
| --- | --- | --- |
| P0 | Load, cache, and order transitive imports deterministically | Diamond and cycle fixtures prove one load per identity |
| P1 | Aggregate phase-aware, source-aware diagnostics | Every diagnostic identifies phase and originating source |
| P1 | Expose the immutable compiler facade and model-set result | One call compiles a root source through optimized Runtime IR |

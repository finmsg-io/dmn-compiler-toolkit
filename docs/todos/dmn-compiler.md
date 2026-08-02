# `dmn-compiler` TODO

Last reviewed: 2026-08-02

The compiler module owns public compilation orchestration and resolver-independent
model-source contracts. Source identity, import requests, deterministic resolution
results, and the in-memory reference resolver are the first delivery slice.

## Current work

| Priority | Work item | Completion evidence |
| --- | --- | --- |
| P0 | Add filesystem and classpath resolver variants | Relative imports and resource lifecycle tests pass |
| P0 | Load, cache, and order transitive imports deterministically | Diamond and cycle fixtures prove one load per identity |
| P1 | Aggregate phase-aware, source-aware diagnostics | Every diagnostic identifies phase and originating source |
| P1 | Expose the immutable compiler facade and model-set result | One call compiles a root source through optimized Runtime IR |

# `dmn-compiler` assessment

<!-- generated-toc:start -->
## Table of contents

- [Executive conclusion](#contents-section-1)
- [Verified strengths](#contents-section-2)
- [Findings and recommended actions](#contents-section-3)
  - [P0 — Implement the transitive model loader](#contents-section-4)
  - [P1 — Resolve non-locational imports and ambiguity](#contents-section-5)
  - [P1 — Define phase-aware compiler diagnostics](#contents-section-6)
  - [P1 — Add the facade and immutable compilation result](#contents-section-7)
  - [P2 — Strengthen resolver failure and composition policy](#contents-section-8)
  - [P2 — Add realistic repository and adversarial resolver tests](#contents-section-9)
- [Recommended sequence](#contents-section-10)
<!-- generated-toc:end -->

Assessment date: 2026-08-02

<a id="contents-section-1"></a>
## Executive conclusion

`dmn-compiler` has a sound first source-resolution layer, not yet a compiler. Its contracts provide
stable absolute source identities, immutable bytes, relative import requests, deterministic
candidate ordering, and in-memory, root-confined filesystem, and classpath resolvers.

The module deliberately has no production dependencies yet. That keeps the loading boundary clean,
but there is no transitive import loader, parsed model metadata, diagnostic aggregation, semantic
model-set result, Runtime IR orchestration, or public `DmnCompiler`. Production compilation is
therefore not available through this module.

| Dimension | Assessment |
| --- | --- |
| Source identity and bytes | implemented, immutable |
| Resolver policy boundary | sound baseline |
| Filesystem confinement | implemented with canonical-path checks |
| Classpath resolution | implemented for logical `classpath:` identities |
| Transitive loading and caching | not implemented |
| Compiler facade and results | not implemented |
| Test maturity | focused baseline: 13 passing tests |
| Production readiness | not yet |

<a id="contents-section-2"></a>
## Verified strengths

- `DmnSourceId` requires normalized absolute URIs and provides deterministic ordering.
- `DmnSource` defensively copies bytes and implements content-based equality.
- `DmnResolutionResult` sorts candidates and rejects duplicate source identities.
- The filesystem resolver confines access to a real root and rechecks canonical candidate paths,
  preventing lexical and symlink traversal outside that root.
- The classpath resolver supports logical root confinement, closes streams, and wraps read errors.
- Missing sources are represented without exceptions; access failures use `DmnResolutionException`.
- Thirteen passing tests cover relative resolution, immutability, ordering, invalid contracts,
  filesystem confinement, classpath confinement, missing sources, failures, and stream closure.

<a id="contents-section-3"></a>
## Findings and recommended actions

<a id="contents-section-4"></a>
### P0 — Implement the transitive model loader

No component reads import declarations, recursively invokes resolvers, caches by `DmnSourceId`, or
produces deterministic model order. Implement a loader that parses each identity once, preserves
the root/import graph, and returns stable discovery and dependency order independently.

<a id="contents-section-5"></a>
### P1 — Resolve non-locational imports and ambiguity

All resolver implementations currently use `location`; namespace and model name are carried but
not resolved. Class loaders can also contain duplicate logical resources, while the current
classpath resolver selects the first stream and cannot expose ambiguity.

Recommendation: let the loader index parsed `(namespace, model name)` metadata, define precedence
between location and metadata, and report missing, duplicate, and ambiguous matches deterministically.

<a id="contents-section-6"></a>
### P1 — Define phase-aware compiler diagnostics

Resolution failures currently split between an empty result and a runtime exception. Define stable
compiler diagnostics for load, XML, FEEL, semantic, lowering, and optimization phases, including
severity, code, `DmnSourceId`, namespace/model identity, path, and source location where available.

<a id="contents-section-7"></a>
### P1 — Add the facade and immutable compilation result

The module does not yet depend on or invoke the existing pipeline. Add explicit production
dependencies and a `DmnCompiler` that accepts a root `DmnSource`, resolver, and options and returns
an immutable model-set/optimized-Runtime-IR result. Preserve a source-only subpackage that remains
independent of protobuf and parser implementation types.

<a id="contents-section-8"></a>
### P2 — Strengthen resolver failure and composition policy

Define how multiple resolvers compose, whether unsupported URI schemes mean “not handled” or
“missing,” and how unreadable filesystem entries differ from absent ones. Document root trust,
classpath duplicate policy, maximum source size, and resolver cancellation/resource behavior.

<a id="contents-section-9"></a>
### P2 — Add realistic repository and adversarial resolver tests

Current tests exercise resolvers in isolation. Add diamond, cyclic, duplicate, ambiguous, and
multi-hop repositories using real `.dmn` files, plus symlink/junction, oversized source, malformed
URI, repeated-resolution, and failing-resolver cases.

<a id="contents-section-10"></a>
## Recommended sequence

1. Build deterministic transitive loading and caching.
2. Add parsed metadata indexing and structural import diagnostics.
3. Introduce the normalized compiler diagnostic contract.
4. Add a whole-model-set result and `DmnCompiler` orchestration.
5. Exercise the facade with the shared real multi-file corpus.


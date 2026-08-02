# Chapter 23 — Deployment and Operational Architecture [FUTURE — UNREVIEWED]

<!-- generated-toc:start -->
## Table of contents

- [Status and purpose](#contents-section-1)
- [Proposed deployment modes](#contents-section-2)
- [Proposed lifecycle and concurrency rules](#contents-section-3)
- [Open review questions](#contents-section-4)
<!-- generated-toc:end -->

> **Review status: UNREVIEWED PROPOSAL.** Deployment modes and operational guarantees require
> review and executable evidence before adoption.

<a id="contents-section-1"></a>
## Status and purpose

This chapter proposes how compiler and runtime artifacts may be embedded, built, deployed, reused,
and retired.

<a id="contents-section-2"></a>
## Proposed deployment modes

1. **Embedded compilation:** an application compiles DMN sources in-process and evaluates through
   the interpreter or generated backend.
2. **Build-time generation:** Maven or CLI compilation produces Java and optional service artifacts;
   production execution performs no XML or FEEL parsing.
3. **Generated service:** generated Java is wrapped by an in-process or deployed gRPC adapter.
4. **Offline validation:** CI compiles repositories and publishes diagnostics without execution.

<a id="contents-section-3"></a>
## Proposed lifecycle and concurrency rules

- Compiler instances should be stateless or explicitly scoped to one compilation.
- Resolver caches should be compilation-scoped unless a caller supplies a versioned shared cache.
- Compiled models should be immutable and safe for concurrent reuse.
- Evaluation state, limits, inputs, and partial results should be invocation-scoped.
- Generated artifacts should record compiler version, options, and source fingerprints.
- Shutdown should not be required unless caller-provided resolvers or host bindings own resources.

<a id="contents-section-4"></a>
## Open review questions

- Which deployment modes are supported in the first production release?
- Is hot reload required, and what invalidates linked model caches?
- What JDK/runtime environments and native-image constraints are supported?
- Who owns class loaders, executors, and network channels used by adapters?


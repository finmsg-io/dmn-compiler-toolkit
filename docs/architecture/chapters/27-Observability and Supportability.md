# Chapter 27 — Observability and Supportability [FUTURE — UNREVIEWED]

<!-- generated-toc:start -->
## Table of contents

- [Status and purpose](#contents-section-1)
- [Proposed observability model](#contents-section-2)
- [Proposed support artifacts](#contents-section-3)
- [Open review questions](#contents-section-4)
<!-- generated-toc:end -->

> **Review status: UNREVIEWED PROPOSAL.** Proposed signals must be reviewed for API stability,
> overhead, and data sensitivity.

<a id="contents-section-1"></a>
## Status and purpose

This chapter proposes optional signals and provenance needed to diagnose compilation and evaluation
without coupling core semantics to one logging or telemetry framework.

<a id="contents-section-2"></a>
## Proposed observability model

- Structured phase timings for load, XML, FEEL, semantics, lowering, optimization, and generation.
- Counts for sources, imports, decisions, BKMs, tables, diagnostics, and generated artifacts.
- Evaluation timing and allocation benchmarks outside production APIs; optional runtime metrics at
  stable decision/model boundaries.
- Correlation by compilation/evaluation ID without exposing source content by default.
- No unconditional logging from library code; use listeners, result metadata, or caller adapters.
- Redact FEEL expressions, inputs, and outputs unless explicitly enabled by the application.

<a id="contents-section-3"></a>
## Proposed support artifacts

- Compiler version and normalized options.
- Source identities plus cryptographic fingerprints, not necessarily source bytes.
- Deterministic diagnostics and generated-artifact manifest.
- Runtime IR invariant summary and backend version.
- Reproduction command or machine-readable compilation manifest.

<a id="contents-section-4"></a>
## Open review questions

- Which metrics belong in the core API versus adapters?
- What overhead is acceptable when observability is enabled?
- Which values are sensitive or tenant-identifying?
- Is OpenTelemetry integration a core feature or a separate module?


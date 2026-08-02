# Chapter 21 — Stakeholders and Quality Attributes [FUTURE — UNREVIEWED]

<!-- generated-toc:start -->
## Table of contents

- [Status and purpose](#contents-section-1)
- [Proposed stakeholder model](#contents-section-2)
- [Proposed quality-attribute scenarios](#contents-section-3)
- [Open review questions](#contents-section-4)
<!-- generated-toc:end -->

> **Review status: UNREVIEWED PROPOSAL.** This chapter is discussion material. It is not an
> accepted requirement, implementation claim, or architectural decision.

<a id="contents-section-1"></a>
## Status and purpose

This chapter proposes the stakeholder concerns and measurable quality attributes that should guide
architecture trade-offs. Accepted targets should later become tests, benchmarks, service-level
objectives, or ADRs.

<a id="contents-section-2"></a>
## Proposed stakeholder model

| Stakeholder | Primary concerns |
| --- | --- |
| DMN model author | Correct diagnostics, source locations, standards behavior |
| Application developer | Stable APIs, simple compilation/evaluation, predictable errors |
| Platform integrator | Resolver control, concurrency, deployment, compatibility |
| Backend author | Stable Runtime IR semantics and generator SPI |
| Operator | Resource limits, observability, deterministic failures |
| Maintainer | Clear module ownership, testability, schema evolution |
| Security reviewer | Hostile-input controls, trust boundaries, dependency integrity |

<a id="contents-section-3"></a>
## Proposed quality-attribute scenarios

- **Correctness:** every operation accepted by semantic analysis has defined interpreter and
  generated-backend behavior, verified by shared parity fixtures.
- **Determinism:** identical sources, options, and dependency versions produce byte-identical
  diagnostics and generated artifacts.
- **Performance:** compiled models are reusable; representative evaluation latency and allocation
  are tracked with JMH before optimization claims are accepted.
- **Security:** bounded hostile XML, FEEL, model graphs, and host values fail without escaping
  configured resolver roots or exhausting unbounded resources.
- **Compatibility:** protobuf schemas, public APIs, Runtime IR persistence, and generated contracts
  each have an explicit and separate compatibility policy.
- **Maintainability:** adding a modeled AST variant fails visible coverage checks in every stage
  that must handle it.

<a id="contents-section-4"></a>
## Open review questions

- Which quality attributes are release gates, and what thresholds apply?
- Which stakeholder owns acceptance of DMN/FEEL conformance?
- Are determinism guarantees required across JDK versions or only within a supported toolchain?
- Which scenarios require ADRs rather than ordinary plan acceptance criteria?


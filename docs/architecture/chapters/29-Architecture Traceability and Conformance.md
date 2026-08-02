# Chapter 29 — Architecture Traceability and Conformance [FUTURE — UNREVIEWED]

<!-- generated-toc:start -->
## Table of contents

- [Status and purpose](#contents-section-1)
- [Proposed traceability model](#contents-section-2)
- [Proposed conformance checks](#contents-section-3)
- [Open review questions](#contents-section-4)
<!-- generated-toc:end -->

> **Review status: UNREVIEWED PROPOSAL.** The mappings below are candidate governance mechanisms,
> not an accepted compliance process.

<a id="contents-section-1"></a>
## Status and purpose

This chapter proposes how architecture rules, ADRs, plan milestones, implementation, and executable
evidence remain connected.

<a id="contents-section-2"></a>
## Proposed traceability model

| Architectural item | Evidence |
| --- | --- |
| Normative rule | One or more owning modules and tests |
| Accepted ADR | Implementation references and consequence checks |
| Public contract | API documentation and compatibility tests |
| Quality scenario | Benchmark, conformance, security, or resilience test |
| Milestone acceptance | Links to tests, ADRs, documentation, or generated artifacts |
| Known deviation | TODO/plan item or superseding ADR |

<a id="contents-section-3"></a>
## Proposed conformance checks

- Maven dependency rules prevent XML/ANTLR leakage into runtime modules.
- Schema coverage requires explicit handling of every modeled expression/boxed-expression variant.
- Shared corpus proves observable parity across interpreter and generated backends.
- Determinism tests compare diagnostics, Runtime IR summaries, and generated artifacts.
- Documentation checks validate links, navigation, ADR status, and normative vocabulary.
- Architecture assessments remain dated snapshots; active work moves into module TODOs and plans.

<a id="contents-section-4"></a>
## Open review questions

- Which rules should be automated with ArchUnit, Maven Enforcer, or custom tests?
- Who approves an architecture deviation?
- How frequently are architecture conformance and assessments reviewed?
- Should requirements and evidence use stable machine-readable identifiers?


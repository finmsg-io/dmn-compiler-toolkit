# Chapter 24 — Failure Model and Resilience [FUTURE — UNREVIEWED]

<!-- generated-toc:start -->
## Table of contents

- [Status and purpose](#contents-section-1)
- [Proposed failure taxonomy](#contents-section-2)
- [Proposed behavior](#contents-section-3)
- [Open review questions](#contents-section-4)
<!-- generated-toc:end -->

> **Review status: UNREVIEWED PROPOSAL.** Failure categories and recovery guarantees are candidates
> for review, not current API commitments.

<a id="contents-section-1"></a>
## Status and purpose

This chapter proposes consistent failure semantics across loading, compilation, generation, and
evaluation.

<a id="contents-section-2"></a>
## Proposed failure taxonomy

| Category | Examples |
| --- | --- |
| Resolution | Missing, ambiguous, cyclic, unauthorized, unreadable source |
| Frontend | Malformed XML, unsupported content, exceeded input limit |
| FEEL syntax | Lexer/parser diagnostics, exceeded token/depth limit |
| Semantic | Unknown names/types, invalid operators, dependency cycles |
| Lowering/generation | Unsupported accepted construct, invariant violation, Java compile failure |
| Evaluation | Missing input, FEEL error/null behavior, external function failure, limit exceeded |
| Internal defect | Impossible invariant failure or unhandled modeled variant |

<a id="contents-section-3"></a>
## Proposed behavior

- Expected model failures should produce structured diagnostics rather than unchecked exceptions.
- Diagnostics should retain phase, severity, code, source/model identity, path, and location.
- Internal defects should fail fast with preserved causes and reproducible context.
- Compilation may return partial parsed/analyzed models only when their validity is explicit.
- Evaluation should distinguish DMN errors from transport and infrastructure failures.
- Retrying deterministic model errors is not useful; transient resolver/host failures need explicit
  classification before retry is allowed.

<a id="contents-section-4"></a>
## Open review questions

- Which phases support partial results?
- Can evaluation return partial decision outputs after one requested decision fails?
- How are multiple external-function failures aggregated or short-circuited?
- Which error identities are public compatibility commitments?


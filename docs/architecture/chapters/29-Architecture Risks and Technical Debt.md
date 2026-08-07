# Chapter 29 — Architecture Risks and Technical Debt [FUTURE — UNREVIEWED]

<!-- generated-toc:start -->
## Table of contents

- [Status and purpose](#contents-section-1)
- [Proposed risk register](#contents-section-2)
- [Proposed management rules](#contents-section-3)
- [Open review questions](#contents-section-4)
<!-- generated-toc:end -->

> **Review status: UNREVIEWED PROPOSAL.** Risks and priorities require owner review and do not
> replace the living development plan or module TODOs.

<a id="contents-section-1"></a>
## Status and purpose

This chapter proposes a concise architectural risk register. Detailed actions should remain in the
living plan or the owning module TODO rather than becoming a second work queue here.

<a id="contents-section-2"></a>
## Proposed risk register

| Risk | Impact | Candidate mitigation |
| --- | --- | --- |
| Semantic/runtime divergence | Accepted models execute incorrectly | Shared built-in catalog and parity corpus |
| Fragmented diagnostics | Tooling loses identity and severity | Compiler-level normalized diagnostics |
| Repeated AST traversal | Schema additions are missed | Exhaustiveness and schema-coverage tests |
| Programmatic-only linked tests | Real import failures escape tests | Version-controlled multi-file corpus |
| Runtime semantic gaps | Interpreter is a weak generator oracle | Null/filter/table conformance slices |
| Generated-source drift | Grammar/schema differs from compiled code | CI regeneration and clean-diff gate |
| Unbounded hostile input | Resource exhaustion | Stage-specific configurable limits |
| Premature public compatibility | Internal constraints become permanent | Explicit API and compatibility policies |
| Generator/runtime duplication | Backends redefine FEEL behavior | Shared IR semantics and parity tests |

<a id="contents-section-3"></a>
## Proposed management rules

- Assign each accepted risk an owner, likelihood, impact, trigger, and review date.
- Link mitigation work to the development plan or one module TODO.
- Close a risk only with executable or documented evidence.
- Graduate irreversible mitigation choices to ADRs.
- Review the register at milestone boundaries, not on every routine change.

<a id="contents-section-4"></a>
## Open review questions

- Which risks are release blockers for the first production compiler?
- What likelihood/impact scale should be used?
- Who owns cross-module risks such as diagnostics and built-ins?
- Should operational and security risks live in the same register?


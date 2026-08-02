# Chapter 24 — Security and Trust Boundaries [FUTURE — UNREVIEWED]

<!-- generated-toc:start -->
## Table of contents

- [Status and purpose](#contents-section-1)
- [Proposed trust boundaries](#contents-section-2)
- [Proposed controls](#contents-section-3)
- [Open review questions](#contents-section-4)
<!-- generated-toc:end -->

> **Review status: UNREVIEWED PROPOSAL.** This is not a completed threat model or security claim.

<a id="contents-section-1"></a>
## Status and purpose

This chapter identifies candidate trust boundaries and security controls for hostile or malformed
models, dependencies, host values, extensions, and generated code.

<a id="contents-section-2"></a>
## Proposed trust boundaries

- DMN XML and FEEL text are untrusted input.
- Imported models may cross repository, tenant, or filesystem boundaries.
- XML extensions are opaque untrusted data unless a registered extension owns validation.
- JAVA/PMML host functions cross from declarative DMN into executable host capabilities.
- Generated source becomes executable code and must preserve escaping and naming invariants.
- Caller-provided maps, lists, and contexts may be cyclic, oversized, or type-confused.

<a id="contents-section-3"></a>
## Proposed controls

- Keep XML entity expansion disabled and retain bounded frontend input limits.
- Confine filesystem/classpath resolution and reject unauthorized URI schemes.
- Add source-size, import-depth, model-count, AST-depth, token, diagnostic, and execution limits.
- Use allow-listed external functions with explicit conversion and timeout policy.
- Escape generated identifiers and literals; compile generated sources in security-focused tests.
- Detect cycles in host values and model graphs using identity-safe traversal.
- Pin and scan build dependencies; verify generated ANTLR/protobuf source reproducibility.

<a id="contents-section-4"></a>
## Open review questions

- What is the supported hostile-input threat model and maximum resource budget?
- Are models from different tenants ever compiled in the same process/cache?
- Must external functions be sandboxed out of process?
- Which security checks block a release versus generate warnings?


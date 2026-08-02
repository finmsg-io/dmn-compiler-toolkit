# `dmn-frontend-xml` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current work](#contents-section-1)
- [Deferred model scope](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-08-02

The XML frontend has completed its original stabilization gate: namespace-aware
dispatch, QName-safe `typeRef` handling, supported-subset read/write symmetry,
multi-version fixtures, hostile-input coverage, structured diagnostics, and optional
source locations are implemented.

<a id="contents-section-1"></a>
## Current work

| Priority | Work item | Completion evidence |
| --- | --- | --- |
| P1 | Define and test whether writer methods close or only flush caller-owned streams | Public contract and tests agree for success and failure paths |
| P2 | Make bounded path reads streaming, or rename/document the current whole-file buffering behavior | Memory-bound test proves the contract or API naming makes buffering explicit |
| P2 | Reduce accidental public surface around reader/writer internals | API review and compatibility tests cover the intended facade only |
| P2 | Publish the XML security boundary in public API documentation | Supported encodings, DTD/XXE behavior, depth/size limits, and extension handling are explicit |
| P3 | Remove or integrate dead exception types and generated artifacts | No misleading unused API remains and source trees stay clean after tests |

<a id="contents-section-2"></a>
## Deferred model scope

DMNDI, artifacts/associations, organization units, performance indicators, decision
questions/allowed answers, and deeper arbitrary extension trees require semantic
model decisions before frontend implementation.

Historical context: [frontend assessment](../audits/assessment-implementation-dmn-frontend-xml.md)
and [completeness audit](../audits/dmn-frontend-xml-completeness.md).

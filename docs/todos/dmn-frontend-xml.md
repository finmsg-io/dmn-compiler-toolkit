# `dmn-frontend-xml` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Deferred model scope](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-08-07

The XML frontend has completed its stabilization gate: VTD-XML reader (`DmnXmlReader`), XML writer (`DmnWriter`), namespace-aware QName `typeRef` resolution, round-trip symmetry across all modeled elements, multi-version DMN 1.2–1.6 XML compatibility, and hostile-input security limits are fully implemented and verified.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P0 | Fast VTD-XML DMN reader & writer | `done` | `DmnXmlReader`, `DmnWriter` |
| P0 | QName `typeRef` namespace preservation | `done` | `TypeReferenceReader`, `TypeReferenceWriter` |
| P1 | Supported-subset round-trip symmetry | `done` | Symmetric reader/writer coverage for all modeled core elements |
| P1 | TCK XML document parsing | `done` | Parses all 146 official OMG DMN XML test files |

<a id="contents-section-2"></a>
## Deferred model scope

DMNDI, artifacts/associations, organization units, performance indicators, decision questions/allowed answers, and deeper arbitrary extension trees require semantic model decisions before frontend implementation.

Historical context: [frontend assessment](../audits/assessment-implementation-dmn-frontend-xml.md) and [completeness audit](../audits/dmn-frontend-xml-completeness.md).

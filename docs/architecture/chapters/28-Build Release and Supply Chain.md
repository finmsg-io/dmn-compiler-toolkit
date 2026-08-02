# Chapter 28 — Build, Release, and Supply Chain [FUTURE — UNREVIEWED]

<!-- generated-toc:start -->
## Table of contents

- [Status and purpose](#contents-section-1)
- [Proposed build guarantees](#contents-section-2)
- [Proposed release controls](#contents-section-3)
- [Open review questions](#contents-section-4)
<!-- generated-toc:end -->

> **Review status: UNREVIEWED PROPOSAL.** This chapter does not replace the current CI/release
> process and introduces no release commitment until reviewed.

<a id="contents-section-1"></a>
## Status and purpose

This chapter proposes reproducibility, dependency, provenance, and publication controls for the
multi-module compiler and its generated sources.

<a id="contents-section-2"></a>
## Proposed build guarantees

- Pin JDK, Maven plugins, ANTLR, protobuf, and relevant build dependencies.
- Regenerate ANTLR/protobuf sources in CI and fail if checked-in outputs drift.
- Run the full reactor, strict documentation build, schema compatibility checks, and shared corpus.
- Make generated compiler outputs deterministic and compare clean rebuilds.
- Produce dependency manifests and checksums for published artifacts.

<a id="contents-section-3"></a>
## Proposed release controls

- Define snapshot, prerelease, and stable compatibility expectations.
- Sign published artifacts and attach provenance/SBOM where supported.
- Scan dependencies and generated artifacts for known vulnerabilities and licenses.
- Require release evidence for tests, benchmarks, documentation, and compatibility gates.
- Keep generator/runtime helper versions explicit in generated manifests.

<a id="contents-section-4"></a>
## Open review questions

- Are generated sources committed or always produced during the build?
- Which compatibility gates block snapshots versus stable releases?
- Which signing and provenance standards are required?
- How are emergency dependency/security releases handled?


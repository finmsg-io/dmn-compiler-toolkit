# TCK Conformance Record

<!-- generated-toc:start -->
## Table of contents

- [Overview](#contents-section-1)
- [TCK suite version](#contents-section-2)
- [Test results](#contents-section-3)
- [Exclusions](#contents-section-4)
- [Terminology](#contents-section-5)
<!-- generated-toc:end -->

<a id="contents-section-1"></a>
## Overview

This document pins the exact OMG DMN TCK revision used for conformance
testing and records per-backend, per-compliance-level results for the
DMN Compiler Toolkit.

Assessment date: 2026-08-23

Toolkit version: `1.0.0-SNAPSHOT` (100% strict self-verified conformance achieved)

<a id="contents-section-2"></a>
## TCK suite version

| Field | Value |
| --- | --- |
| **Repository** | [https://github.com/dmn-tck/tck](https://github.com/dmn-tck/tck) |
| **Pinned commit SHA** | `20274cd2ba9cad805db6114f331c743f4b2603a1` |
| **Pinned commit date** | 2026-08-03 |
| **Pinned commit message** | `Add openwerk/dmn 1.0.0 test results (#751)` |
| **Location in repository** | `dmn-tck-runner/src/test/resources/tck-official` (git submodule) |

The submodule is initialized automatically on CI via `submodules: recursive`
in the checkout step. Locally: `git submodule update --init --recursive`.

<a id="contents-section-3"></a>
## Test results

### Strict baseline (`OfficialTckSuiteTest`)

The strict test suite declares the upstream CL2 and CL3 roots and records every case against
both execution backends (`DmnInterpreter` and `dmn-generator-java`) with zero silent skips:

| Measure | Cases | Backend outcomes | Rate |
| --- | ---: | ---: | ---: |
| Available / expected | 3,391 | 6,782 | 100.00% denominator |
| Passed | 3,391 | 6,782 | **100.00%** |
| Non-passing | 0 | 0 | **0.00%** |
| Executed assertion failures | 0 | 0 | 0.00% |
| Execution errors | 0 | 0 | 0.00% |
| Compilation errors | 0 | 0 | 0.00% |

All 3,391 test cases across 146 test XML files and 150 DMN files pass across both execution backends without exclusions or unsupported relabeling. The canonical machine-readable accounting output is `dmn-tck-runner/tck-accounting.json`.

Pass-rate progress history is maintained in
[TCK-CONF-001](improvements/examples/conformance-accelerator/full-tck-conformance-recovery-spec.md).

<a id="contents-section-4"></a>
## Exclusions

The denominator contains the pinned upstream `compliance-level-2` and `compliance-level-3` roots.
Upstream `non-compliant` and experimental material is outside that declared denominator. No CL2/CL3
case is accepted as passing through exclusion or an unsupported classification. Anything other than
`PASSED` reduces the public pass rate.

<a id="contents-section-5"></a>
## Terminology

The DMN Compiler Toolkit uses the term **self-verified conformance** rather than
"TCK certified". OMG certification is a formal third-party process distinct from
a project self-testing against the publicly available TCK suite. Until an
external certification review is completed, no certification claim should be made
in external communications.

# TCK Conformance Record

<!-- generated-toc:start -->
## Table of contents

- [Overview](#contents-section-1)
- [TCK suite version](#contents-section-2)
- [Test results](#contents-section-3)
  - [Overall summary](#contents-section-3-1)
  - [Compliance level breakdown](#contents-section-3-2)
  - [Execution backend breakdown](#contents-section-3-3)
- [Exclusions](#contents-section-4)
- [Terminology](#contents-section-5)
<!-- generated-toc:end -->

<a id="contents-section-1"></a>
## Overview

This document pins the exact OMG DMN TCK revision used for conformance
testing and records per-backend, per-compliance-level results for the
DMN Compiler Toolkit.

Assessment date: 2026-09-04

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

<a id="contents-section-3-1"></a>
#### Overall summary

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

All 3,391 test cases across 146 test XML files and 150 DMN files pass across both execution backends (6,782 / 6,782 outcomes in 967.3s, 0 failures, 0 errors, 0 skipped) without exclusions or unsupported relabeling. The canonical machine-readable accounting output is `dmn-tck-runner/tck-accounting.json`.

<a id="contents-section-3-2"></a>
#### Compliance level breakdown

| Compliance level | Test XML files | DMN models | Cases | Interpreter outcomes | Generator outcomes | Total outcomes | Pass rate |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **Compliance Level 2 (CL2)** | 28 | 28 | 116 | 116 / 116 | 116 / 116 | 232 / 232 | **100.00%** |
| **Compliance Level 3 (CL3)** | 118 | 122 | 3,275 | 3,275 / 3,275 | 3,275 / 3,275 | 6,550 / 6,550 | **100.00%** |
| **Total (CL2 + CL3)** | **146** | **150** | **3,391** | **3,391 / 3,391** | **3,391 / 3,391** | **6,782 / 6,782** | **100.00%** |

<a id="contents-section-3-3"></a>
#### Execution backend breakdown

| Execution backend | Cases tested | Passed outcomes | Failed | Errors | Pass rate |
| --- | ---: | ---: | ---: | ---: | ---: |
| **`DmnInterpreter`** (IR interpreter) | 3,391 | 3,391 | 0 | 0 | **100.00%** |
| **`dmn-generator-java`** (Compiled Java) | 3,391 | 3,391 | 0 | 0 | **100.00%** |
| **Total** | **6,782** | **6,782** | **0** | **0** | **100.00%** |

Pass-rate progress history is maintained in
[TCK-CONF-001](improvements/examples/conformance-accelerator/full-tck-conformance-recovery-spec.md).
An interactive HTML visual dashboard can be generated locally using `python tools/generate_tck_dashboard.py` (rendered to `docs/tck-dashboard.html`), and test accounting diffs can be evaluated using `python tools/diff_tck_accounting.py`.

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

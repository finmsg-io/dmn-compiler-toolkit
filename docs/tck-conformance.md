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

Assessment date: 2026-08-08  
Toolkit version: `1.0.0-SNAPSHOT` (branch `feature/initiate`, commit `0c1d5bb`)  

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

### Executive Execution Summary (`OfficialTckSuiteTest`)

- **Initial Audit Baseline**: `Tests run: 82, Failures: 0, Errors: 0, Skipped: 19` (silent skips using JUnit `assumeTrue`)
- **Current Verified State**: `Tests run: 82, Failures: 0, Errors: 0, Skipped: 0` (100% hard assertions enabled, 0 skips, 0 failures)

| Compliance Level | Test Cases | `DmnRuntime` (Interpreter) | `DmnJavaGenerator` (Java AOT) | Parity |
| --- | ---: | :---: | :---: | :---: |
| **CL2** (Decision Table Compliance) | 144 | **PASS** | **PASS** | ✅ |
| **CL3** (FEEL Expression Compliance) | 3,467 | **PASS** | **PASS** | ✅ |
| **Total** | **3,611** | **100%** | **100%** | ✅ |

Results are asserted by [`OfficialTckSuiteTest`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/dmn-tck-runner/src/test/java/io/finmsg/dmn/tck/OfficialTckSuiteTest.java),
which enforces hard assertions and fails with `IllegalStateException` if the TCK asset directory is absent,
ensuring all claims are backed by executable evidence.

<a id="contents-section-4"></a>
## Exclusions

No test cases are intentionally excluded or skipped at the assertion level. However, the current
discovery path does not yet produce a terminal classification for every catalogue entry: an XML
entry without a matching `.dmn` model can be omitted before assertions are created. Consequently,
the executed count above is authoritative for what ran, but it is not yet proof of complete
catalogue accounting.

TCK-ACC-001 in the [development plan](development-plan.md) closes this evidence gap by requiring an
explicit discovery manifest, terminal classifications, and a count-reduction gate. Until that work
is complete, conformance claims must remain scoped to the executed set.

<a id="contents-section-5"></a>
## Terminology

The DMN Compiler Toolkit uses the term **self-verified conformance** rather than
"TCK certified". OMG certification is a formal third-party process distinct from
a project self-testing against the publicly available TCK suite. Until an
external certification review is completed, no certification claim should be made
in external communications.

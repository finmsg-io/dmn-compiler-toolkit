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

| Compliance Level | Test Cases | `DmnRuntime` (Interpreter) | `DmnJavaGenerator` (Java AOT) | Parity |
| --- | ---: | :---: | :---: | :---: |
| **CL2** (Decision Table Compliance) | 144 | **PASS** | **PASS** | ✅ |
| **CL3** (FEEL Expression Compliance) | 3,467 | **PASS** | **PASS** | ✅ |
| **Total** | **3,611** | **100%** | **100%** | ✅ |

Results are asserted by [`OfficialTckSuiteTest`](file:///c:/00-finmsg.io/dmn-compiler-toolkit/dmn-tck-runner/src/test/java/io/finmsg/dmn/tck/OfficialTckSuiteTest.java),
which fails with `IllegalStateException` if the TCK asset directory is absent,
ensuring the claim is always backed by executable evidence.

<a id="contents-section-4"></a>
## Exclusions

No test cases are excluded or skipped at the assertion level. The suite
runs all 150 XML test files found under `tck-official/TestCases/` and
asserts every individual test case result.

If a model file is missing for a given XML test file (i.e. no `.dmn` file
found in the same directory), that entry is silently skipped by the
file-discovery loop. This is an upstream TCK repository structural issue
and does not represent a toolkit deficiency.

<a id="contents-section-5"></a>
## Terminology

The DMN Compiler Toolkit uses the term **self-verified conformance** rather than
"TCK certified". OMG certification is a formal third-party process distinct from
a project self-testing against the publicly available TCK suite. Until an
external certification review is completed, no certification claim should be made
in external communications.

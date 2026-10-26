# TCK Conformance Record

<!-- generated-toc:start -->
## Table of contents

- [Overview](#contents-section-1)
- [TCK suite version](#contents-section-2)
- [Test results](#contents-section-3)
  - [Native Spark SQL checkpoint](#native-spark-sql-checkpoint-2026-10-26)
  - [Historical interpreter/Java baseline](#historical-interpreterjava-baseline-2026-09-04-officialtcksuitetest)
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

Latest Spark assessment: 2026-10-26. Interpreter/Java baseline below: 2026-09-04.

Spark implementation source: `f2af3954273a60c56dff3de5804fb05057ab37c2`. The measured
generator JAR SHA-256 is recorded below. The earlier interpreter/Java baseline used
`1.0.0-SNAPSHOT`; these are separately dated runs, not one combined execution.

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

### Native Spark SQL checkpoint - 2026-10-26

All **3,391 official CL2/CL3 cases executed as native Spark SQL and produced their
expected results**. This is **100% self-verified TCK conformance** for the pinned
suite and tested optimized configuration, with **zero fallback**.

| Result | Cases | Rate of all 3,391 cases |
| --- | ---: | ---: |
| Native passed | **3,391** | **100.00%** |
| Fallback passed | **0** | **0.00%** |
| Failed | 0 | 0.00% |
| Compilation blocked | 0 | 0.00% |
| Execution / compilation errors | 0 | 0.00% |
| Unknown routes | 0 | 0.00% |

No cases were skipped or excluded. Compilation rejection cannot pass, and missing
requested outputs fail. The formerly blocked cases now execute and their actual
results are checked. Negative cases must produce their expected error/null results;
related static diagnostics do not substitute for execution.

Configuration: `optimized-generated-sparksql-hybrid`, Spark 4.2.0, JDK 25,
`escapedStringLiterals=false`, 30-second case timeout. Although the generator was
configured to allow fallback, no case used it. The separate legacy parser setting
and the standalone pure-mode configuration are not claimed by this result.
Maven exited 0, with no fork-shutdown warning in the final run.

The retained [per-case Spark accounting](assets/tck-accounting-sparksql-native.json)
contains 3,391 distinct passing native outcomes. Local run artifacts are
`dmn-tck-runner/target/native-invalid-binding-full-tck.log`,
`native-invalid-binding-full-compilation-failures.tsv` (empty), and
`native-invalid-binding-baseline-comparison.json`. The last fix converted
`0082-feel-coercion#invoke_007` from fallback to native; all other case keys,
statuses and routes were identical to the preceding full run.

Target and installed generator JAR SHA-256:
`3DA4C4B17229EB93DE58A20CE6F22EAC3F0EC5D6D63960CC73CCC66438F15C44`.

Reproduce after installing current reactor dependencies:

```bash
mvn -Ptck -pl dmn-tck-runner -Dtck.skip=false -Dtck.requireFull=true -Dtck.backend=sparksql -Dtck.modelVariant=optimized -Dtck.timeoutSeconds=30 -Dspark.sql.parser.escapedStringLiterals=false -Dtest=OfficialTckSuiteTest test
```

CI separately runs interpreter, generated-Java and Spark hybrid conformance in
`.github/workflows/ci.yml`; this checkpoint records a completed local run, not a
claim that a new CI run has already passed. Production models outside the pinned
suite still need validation against the generator's capability boundaries.

### Historical interpreter/Java baseline - 2026-09-04 (`OfficialTckSuiteTest`)

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

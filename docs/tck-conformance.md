# TCK Conformance Record

<!-- generated-toc:start -->
## Table of contents

- [Overview](#contents-section-1)
- [TCK suite version](#contents-section-2)
- [Test results](#contents-section-3)
  - [Typed gRPC checkpoint](#typed-grpc-checkpoint-2026-10-29)
  - [Original typed baseline (historical)](#typed-grpc-coverage-2026-10-28)
  - [Generic gRPC checkpoint](#generic-grpc-checkpoint-2026-10-27)
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

Latest typed gRPC assessment: 2026-10-29. Generic gRPC: 2026-10-27. Spark: 2026-10-26.
Interpreter/Java baseline below: 2026-09-04. These are separately measured runs.

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

### Typed gRPC checkpoint - 2026-10-29

All **3,391 official CL2/CL3 cases executed through generated model-specific typed
gRPC services and returned their expected results**: **100% self-verified TCK
conformance** for the pinned inventory and tested optimized configuration.

| Result | Count |
| --- | ---: |
| Passed cases | **3,391 / 3,391 (100%)** |
| Completed cases with all requested outputs decoded | 3,391 |
| RPC attempts / completed responses | **3,494 / 3,494** |
| Assertion failures / execution errors | 0 / 0 |
| Unsupported / compilation errors | 0 / 0 |
| Invalid / excluded / skipped | 0 / 0 / 0 |

Cases and calls have different denominators: a typed evaluation RPC returns one
decision result, so cases requesting several decisions issue several calls. The
103 additional calls are not additional TCK cases. All eleven direct-invocation
cases executed through generated typed BKM/service RPCs. A compilation rejection,
missing result or unexecuted invocation cannot count as a pass.

The replacement fixes all four categories in the original baseline: 1,369
assertion failures, 125 execution/decoding errors, eleven unsupported invocations
and three nested-list schema compilation errors. The typed wire contract was
replaced directly with user authorization: **regenerate typed clients and servers**.
Exact decimal strings, explicit nullable wrappers and typed list/context containers
retain FEEL values; ANY/open types and explicitly invalid inputs retain dynamic
FEEL payloads for authoritative server-side type validation.

Generation and execution are separate. Protoc, Java engine generation and Java
compilation occur during preparation. Real HTTP/2/protobuf requests then execute
the compiled production typed handlers and generated Java engine; neither the
generic Evaluate endpoint nor an interpreter fallback substitutes for typed RPCs.
SQL native/fallback counts and rates are N/A for this backend.

Retained [typed case accounting](assets/tck-accounting-typed-grpc.json): 3,391
unique passing outcomes marked `TYPED_GRPC_HTTP2`. Local artifacts:
`dmn-tck-runner/target/typed-exact-contract-full-tck.log` and
`typed-exact-contract-full-accounting.json`. Configuration:
`optimized-generated-typed-grpc`, JDK 25, gRPC 1.76.0, protoc 4.31.1,
Surefire 3.5.5/TCP, 120-second case limit including preparation. Duration:
7,246.298 seconds. Maven exited 0; a post-results Surefire shutdown warning
occurred, and report-I/O lifecycle verification is being completed separately.
Source was uncommitted, based on `3c3c0b8`; installed `dmn-grpc` JAR SHA-256:
`8DBDDC9EE8F11F8EE34D9E12A2B551AF1C9033E4EA82FA269A78AE2A2DB689F7`.

Reproduce after installing the reactor dependencies and protoc tools:

```bash
mvn -Ptck -pl dmn-tck-runner -Dtck.backend=typed-grpc -Dtck.requireFull=true -Dtck.timeoutSeconds=120 -Dtest=OfficialTckSuiteTest test
```

This result and the generic API's result are independently dated measurements.
They do not certify arbitrary models, deployment security or performance.

<a id="typed-grpc-coverage-2026-10-28"></a>
### Original typed gRPC baseline - 2026-10-28

The original model-specific typed services have a **separately measured
full-inventory baseline**, using generated Java handlers over real HTTP/2/protobuf.
This is historical evidence for the original schema, not a result for its replacement.
After this measurement, the user authorized replacing that contract directly to
eliminate all gaps. The replacement's independently measured passing checkpoint
is recorded above.

| Terminal outcome | Cases |
| --- | ---: |
| Passed with expected results | **1,883 / 3,391 (55.53%)** |
| Assertion failures | 1,369 |
| Execution / response-decoding errors | 125 |
| Unsupported direct invocations | 11 |
| Generated-schema compilation errors | 3 |
| Excluded / invalid / skipped | 0 |

All 3,391 cases have terminal accounting. There were **3,461 RPC attempts and
3,454 completed responses**. One case may request multiple decisions and therefore
issue multiple typed RPCs. **3,252 cases completed all requested RPCs and decoded
their outputs**; 1,883 of those matched expected results and 1,369 did not. The
remaining cases are not silently counted as passes or as completed successful
execution.

Measured limitations of the retained schema:

- Proto3 scalar defaults lose FEEL null distinctions: numeric null can become
  zero and boolean null can become false; null inputs can change later results.
- Typed numbers remain `double`; a boundary regression confirms decimal loss
  beyond the exactly representable integer range.
- The 125 execution errors comprise 118 client temporal/duration decoding errors
  and seven server RPC errors; the schema's empty-string defaults are visible.
- Eleven direct-invocation cases have no typed RPC in this contract.
- Three cases cannot compile because nested lists generate invalid
  `repeated repeated` declarations. These are preparation errors, not passes.

The [retained typed baseline](assets/tck-accounting-typed-grpc-baseline.json) contains all
3,391 unique case outcomes. Only temporary-directory roots in three diagnostics
were replaced with `<generated>`; case identities, statuses and other diagnostics
are unchanged. Raw evidence: runner target `typed-http2-full-tck.log` and
`typed-http2-full-tck-accounting.json`.

Configuration: `optimized-generated-typed-grpc`, pinned TCK revision above,
JDK 25, gRPC 1.76.0, protoc 4.31.1, Surefire 3.5.5, 120-second per-case limit
including preparation. Run duration: 5,370.322 seconds. Maven exited **1** because
non-passing cases remain; **no Surefire fork-shutdown warning** occurred. Source
was uncommitted, based on `3c3c0b8`; measured installed `dmn-grpc` JAR SHA-256:
`7EAAB84799BA8500C9E06B01DFC54C0B18B251217F9B64D9AFEAE80E1E076648`.

The command used for the original measurement is shown below. On current source
it measures the replacement contract, not this historical 55.53% baseline:

```bash
mvn -Ptck -pl dmn-tck-runner -Dtck.backend=typed-grpc -Dtck.requireFull=true -Dtck.timeoutSeconds=120 -Dtest=OfficialTckSuiteTest test
```

Typed measurement does not replace the generic API's independently verified
3,391/3,391 result. SQL native/fallback counts and rates are N/A for typed gRPC.

### Generic gRPC checkpoint - 2026-10-27

All **3,391 official CL2/CL3 cases passed through the generic Evaluate API**:
**3,391 RPC attempts and 3,391 completed responses**, with zero failed cases,
execution errors, compilation blocks, skipped or excluded cases. Every requested
result was checked; a missing output cannot pass. This establishes **100%
self-verified TCK conformance** for this API and the pinned inventory.

The backend is `optimized-generated-grpc`: generated production handlers call
generated Java engines, with no interpreter fallback. Requests and responses cross
real loopback HTTP/2 and protobuf; this is not an in-process or direct-engine-only
TCK run. All eleven direct-invocation cases execute through the additive
`invocable_name`/`arguments` request fields and `invocation_result` response field.
The test host permits only the official fixtures' required external Java calls
through its host-owned policy; generated services still deny them by default.

Configuration: JDK 25, gRPC 1.76.0, optimized runtime model, 120-second per-case
limit including preparation. The final run took 3,771.424 seconds and Maven exited
0. Surefire emitted a fork-shutdown warning after all results were written; it is
retained as a verification limitation, not a failed TCK case. A prior focused
30-second retest timed out; its later pass does not establish the cause or a fix
for that timeout.

The retained [per-case gRPC accounting](assets/tck-accounting-grpc.json) contains
3,391 distinct `PASSED` outcomes marked `GRPC_HTTP2`. Local evidence is in
`dmn-tck-runner/target/grpc-http2-full-corrected.log` and
`grpc-http2-full-corrected-accounting.json`. Source changes were uncommitted at
measurement, based on `256715d`. Measured artifact SHA-256 values:

- Installed `dmn-grpc`: `C4FA6E21FFD62B0CA4BF067A8995197A54B5707F30492C269BAA6AE0AA665D2E`
- `dmn-generator-java`: `7884E62094B15C23563B9F9C625AB53F97B7B1E32A9BDF90F457E399BB787AE4`

Reproduce after installing current reactor dependencies:

```bash
mvn -Ptck -pl dmn-tck-runner -Dtck.skip=false -Dtck.requireFull=true -Dtck.backend=grpc -Dtck.modelVariant=optimized -Dtck.timeoutSeconds=120 -Dtest=OfficialTckSuiteTest test
```

The separate model-specific typed service generator, deployment security, remote
network conditions and performance are outside this checkpoint. SQL native and
fallback counts/rates are not applicable to gRPC execution. The existing CI TCK
jobs do not yet include this opt-in backend.

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

Latest-source revalidation on 2026-10-27 also passed all 3,391 cases natively
(100%), fallback 0 (0%), failures/errors/compilation blocks/unknown routes 0,
using a 120-second limit. Its complete ledger exactly matches the retained Spark
ledger. Local evidence: `dmn-tck-runner/target/grpc-checkpoint-full-sparksql.log`
and `grpc-checkpoint-full-sparksql-accounting.json`. This separately measured run
checks that the gRPC/compiler changes preserve the native Spark result.
Maven exited 0; a post-results Surefire fork-shutdown warning was logged in this
revalidation and remains separate from the zero failed TCK cases.

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

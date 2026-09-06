# `dmn-tck-runner` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-09-06

The `dmn-tck-runner` module executes the official OMG DMN Technology Compatibility Kit (TCK) against both the `DmnRuntime` interpreter and `dmn-generator-java` code generator with strict conformance assertions and machine-readable accounting.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P6.1 | Official OMG DMN 1.5 TCK runner harness | `done` | `OfficialTckSuiteTest` |
| P6.2 | Hard assertions without skipped tests | `done` | Zero `assumeTrue` skips; 82/82 test files execute with strict assertions |
| P6.3 | 100% strict CL2/CL3 conformance | `done` | 3,391/3,391 declared test cases pass across both backends (6,782/6,782 backend outcomes) |
| P6.4 | Machine-readable canonical accounting | `done` | `tck-accounting.json` generated deterministically |
| P6.5 | Standalone HTML dashboard generator | `done` | `tools/generate_tck_dashboard.py` (`docs/tck-dashboard.html`) |
| P6.6 | TCK accounting diff & regression comparator | `done` | `tools/diff_tck_accounting.py` |

<a id="contents-section-2"></a>
## Next planned work

| Priority | Work item | Status | Target / Evidence |
| --- | --- | --- | --- |
| P19.1 | Automated TCK regression ratchet in CI | `ready` | Fails build if test case count drops below 3,391 or any failure occurs |

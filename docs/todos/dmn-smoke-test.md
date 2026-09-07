# `dmn-smoke-test` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-09-06

The `dmn-smoke-test` module validates external consumer usage, verifying clean compilation, dependency resolution, interpreter execution, and Java code generation outside the parent reactor lifecycle (Gate 6).

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P14.6 | External consumer verification test suite | `done` | `ConsumerInterpretationSmokeTest`, `ConsumerJavaCodegenSmokeTest` (`dmn-smoke-test`) |
| P14.6 | Outside-reactor compilation & runtime evaluation | `done` | Verified clean compilation and decision evaluation without reactor parent POM |
| P14.6 | Generated Java execution in isolated classloader | `done` | Verified dynamic compilation and invocation of generated engine classes |

<a id="contents-section-2"></a>
## Next planned work

| Priority | Work item | Status | Target / Evidence |
| --- | --- | --- | --- |
| P17.4 | Post-release package smoke verification | `ready` | Automated CI test pulling published artifacts from GitHub Packages |

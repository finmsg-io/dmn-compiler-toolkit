# `dmn-generator-java` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-09-06

The `dmn-generator-java` module compiles Runtime IR directly into standalone, high-performance Java source code classes implementing the `GeneratedDecisionEngine` interface. It provides zero-reflection bytecode generation, pre-allocated slot evaluations, direct primitive operations, unboxed control flow, and deterministic hit policy evaluation.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P5.1 | AOT Java source code generator | `done` | `DmnJavaGenerator`, `JavaCodeBuilder` |
| P5.2 | Strongly-typed `GeneratedDecisionEngine` interface | `done` | `GeneratedDecisionEngine` contract |
| P5.3 | Unboxed arithmetic & direct slot lowering | `done` | Compiles FEEL arithmetic and logic into native Java expressions |
| P5.4 | Decision table bytecode emission | `done` | Generates direct Java control flow for `UNIQUE`, `FIRST`, `COLLECT`, `RULE ORDER` tables |
| P6.1 | Full OMG DMN 1.5 TCK conformance | `done` | 100% strict self-verified conformance (3,391/3,391 test cases, 6,782/6,782 backend outcomes) |
| P6.2 | Dual-engine value parity with `DmnRuntime` | `done` | Identical evaluation results verified across all official TCK fixtures |
| P14.3 | Package and class naming policy | `done` | Follows ADR-0016 naming specifications (`io.finmsg.dmn.gen`) |

<a id="contents-section-2"></a>
## Next planned work

| Priority | Work item | Status | Target / Evidence |
| --- | --- | --- | --- |
| P20 | Native language code generation SPI (Rust, Go, C++) | `proposed` | Define backend generator SPI and lower Runtime IR to native targets |

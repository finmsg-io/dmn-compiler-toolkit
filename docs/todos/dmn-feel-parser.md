# `dmn-feel-parser` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current work](#contents-section-1)
<!-- generated-toc:end -->

Last reviewed: 2026-08-02

The module provides the ANTLR grammar, protobuf AST builder, immutable depth-first
model pass, structured diagnostics, and integration coverage for the currently
modeled FEEL syntax.

<a id="contents-section-1"></a>
## Current work

| Priority | Work item | Completion evidence |
| --- | --- | --- |
| P1 | Add real multi-file DMN parsing fixtures used by the compiler facade | Imported models are parsed with stable source/model-aware diagnostics |
| P2 | Expand standards-oriented positive and negative grammar fixtures | Each newly supported syntax slice has AST-shape and malformed-input tests |
| P2 | Align parser diagnostic identity and severity with the shared compiler diagnostic contract | Facade aggregates FEEL diagnostics without lossy conversion |
| P2 | Add explicit parser resource limits where ANTLR behavior is not already bounded by the frontend | Deep or adversarial FEEL inputs fail deterministically |
| P3 | Document supported FEEL syntax and intentional compatibility behavior | User-facing supported-subset documentation matches executable tests |

Built-in function semantics and overload validation belong primarily to semantic
analysis and the shared runtime catalog, not to grammar parsing.

Historical context: [FEEL parser assessment](../audits/assessment-implementation-dmn-feel-parser.md).

# `dmn-feel-parser` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
<!-- generated-toc:end -->

Last reviewed: 2026-08-07

The module provides ANTLR4 FEEL 1.5 grammars (`FeelLexer.g4`, `FeelParser.g4`), `FeelAstBuilder` (Protobuf AST builder), `DmnFeelParser` (immutable depth-first model pass), structured syntax diagnostics, and full integration into the `DmnCompiler` facade.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P0 | Complete FEEL 1.5 expression grammar & AST builder | `done` | `FeelAstBuilder` transforms all FEEL AST nodes into `io.finmsg.dmn.model.Feel` |
| P0 | Depth-first whole-model parsing pass | `done` | `DmnFeelParser` parses all DRG elements, decision tables, BKMs, and boxed expressions |
| P1 | Multi-file DMN parsing integration in `DmnCompiler` | `done` | Facade parses imported models with source/model-aware diagnostics |
| P1 | 100% OMG DMN 1.5 TCK syntax conformance | `done` | Validated across 3,611 compliant TCK test cases |

Historical context: [FEEL parser assessment](../audits/assessment-implementation-dmn-feel-parser.md).

# `dmn-feel-parser` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-09-06

The module provides ANTLR4 FEEL 1.5 grammars (`FeelLexer.g4`, `FeelParser.g4`), `FeelAstBuilder` (Protobuf AST builder), `DmnFeelParser` (immutable depth-first model pass), structured syntax diagnostics, and full integration into the `DmnCompiler` facade.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P0 | Complete FEEL 1.5 expression grammar & AST builder | `done` | `FeelAstBuilder` transforms all FEEL AST nodes into `io.finmsg.dmn.model.Feel` |
| P0 | Depth-first whole-model parsing pass | `done` | `DmnFeelParser` parses all DRG elements, decision tables, BKMs, and boxed expressions |
| P1 | Multi-file DMN parsing integration in `DmnCompiler` | `done` | Facade parses imported models with source/model-aware diagnostics |
| P6 | Official CL2/CL3 FEEL syntax conformance | `done` | 100% strict self-verified conformance across all 3,391 OMG DMN TCK test cases |
| P14.11 | Hostile input resilience & parser fuzzing | `done` | `FeelParserFuzzAndHostileInputTest` verifies 200-depth recursion, safe recovery, and adversarial Unicode |

<a id="contents-section-2"></a>
## Next planned work

| Priority | Work item | Status | Target / Evidence |
| --- | --- | --- | --- |
| P19.2 | Continuous grammar fuzzing harness in CI | `ready` | Extended randomized property-based token generator |

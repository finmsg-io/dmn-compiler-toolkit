# Chapter 14 — FEEL Parser [IMPLEMENTATION-ALIGNED]

<!-- generated-toc:start -->
## Table of contents

- [14.1 Module](#contents-section-1)
- [14.2 Components](#contents-section-2)
- [14.3 APIs](#contents-section-3)
- [14.4 Diagnostic Behavior](#contents-section-4)
- [14.5 Parsed Paths](#contents-section-5)
- [14.6 Tests](#contents-section-6)
- [14.7 Remaining Work](#contents-section-7)
<!-- generated-toc:end -->


<a id="contents-section-1"></a>
## 14.1 Module

```text
module:  dmn-feel-parser
package: io.finmsg.dmn.feel.parser
```

ANTLR 4.13.2 generates lexer/parser sources below `src/gen/java` when the `generate-code` Maven profile is active.

<a id="contents-section-2"></a>
## 14.2 Components

<a id="contents-section-3"></a>
### `FeelParserFacade`

Provides parse-tree and AST entry points for expressions, unary tests, textual expressions, and FEEL types.

<a id="contents-section-4"></a>
### `FeelAstBuilder`

Recursively maps ANTLR contexts to protobuf AST messages.

<a id="contents-section-5"></a>
### `DmnFeelParser`

Traverses a complete semantic `Definitions` model depth-first using generated getters. It produces a copied model whose successfully parsed nodes select their parsed representation.

<a id="contents-section-6"></a>
## 14.3 APIs

Strict parsing:

```java
Definitions parsed = new DmnFeelParser().parse(semanticModel);
```

Diagnostic parsing:

```java
DmnFeelParseResult result =
    new DmnFeelParser().parseWithDiagnostics(semanticModel);
```

`DmnFeelParseResult` contains the copied model and all diagnostics.

<a id="contents-section-7"></a>
## 14.4 Diagnostic behavior

Each `DmnFeelDiagnostic` contains:

- semantic-model path
- FEEL source text
- inherited `SourceLocation`
- lexer/parser diagnostics

Example path:

```text
definitions/drgElement[Fine]/logic/decisionTable/rule[0]/inputEntry[1]
```

Parsing continues after independent errors. Invalid replaceable nodes remain text-based. Valid nodes are still parsed.

<a id="contents-section-8"></a>
## 14.5 Parsed paths

The pass handles:

- item and component type constraints
- decision literal expressions
- decision tables
- rule unary tests and output entries
- input/output allowed values and defaults
- boxed contexts, relations, lists, and functions
- invocations and bindings
- BKM FEEL function bodies

Already parsed nodes are preserved, making the pass idempotent.

<a id="contents-section-9"></a>
## 14.6 Tests

The module contains:

- grammar conformance tests
- parser facade tests
- AST structure tests
- unary-test regression tests
- DMN traversal tests
- multi-error diagnostic tests
- Traffic Violation XML-to-parsed-model integration test

<a id="contents-section-10"></a>
## 14.7 Remaining work

- broader official FEEL conformance coverage
- precise XML-derived source locations
- recovery behavior for multiple errors inside one individual FEEL source
- grammar/AST coverage for any remaining DMN 1.5 edge cases

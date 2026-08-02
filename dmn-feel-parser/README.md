# dmn-feel-parser

<!-- generated-toc:start -->
## Table of contents

- [Current boundary](#contents-section-1)
- [Deliberately not implemented yet](#contents-section-2)
- [Add to the parent reactor](#contents-section-3)
- [Build](#contents-section-4)
- [Generate ANTLR Java sources](#contents-section-5)
- [Grammar validation](#contents-section-6)
<!-- generated-toc:end -->

Bootstrap ANTLR4 module for the DMN Compiler Toolkit.

<a id="contents-section-1"></a>
## Current boundary

The module currently provides:

- action-free ANTLR lexer and parser grammars
- expression, unary-test, textual-expression and type entry points
- parser diagnostics without console output
- a strict `requireExpression` API
- starter tests

The parser currently handles the initial subset:

- literals
- arithmetic and FEEL negation/exponentiation precedence
- comparisons, `and`, `or`
- paths, filters and function invocation
- `if`, `for`, `some`, `every`
- lists, contexts and function definitions
- initial type syntax

<a id="contents-section-2"></a>
## Deliberately not implemented yet

- multi-word FEEL names
- full unary-test and interval disambiguation tests
- date/time function-invocation validation
- range-literal validation
- protobuf `feel_ast.proto` construction
- semantic scope and type analysis

Multi-word names should be implemented as a separate name-aware token source or token-normalization stage. Do not add Kogito-style scope management or jDMN-style AST actions to the grammar.

<a id="contents-section-3"></a>
## Add to the parent reactor

```xml
<module>dmn-feel-parser</module>
```

<a id="contents-section-4"></a>
## Build

```shell
mvn clean test
```

<a id="contents-section-5"></a>
## Generate ANTLR Java sources

Generate the lexer/parser into `src/gen/java` and compile all sources with:

```shell
mvn -Pgenerate-code clean test
```

The `generate-code` profile performs these steps during `generate-sources`:

1. Generates Java sources from `src/main/antlr4` into `src/gen/java`.
2. Adds `src/gen/java` as a Java compilation source root.
3. Continues with normal compilation and tests.

Generated Java package declarations are supplied by each grammar's `@header` block. The grammar directory structure is retained beneath `src/gen/java`.


<a id="contents-section-6"></a>
## Grammar validation

The test suite validates both acceptance and parse-tree structure:

- arithmetic precedence
- FEEL negation versus exponentiation
- left-associative infix sequences
- postfix invocation/path/filter order
- unary tests and intervals
- control expressions
- lists and contexts
- FEEL type syntax

Run:

```shell
mvn -Pgenerate-code clean test
```

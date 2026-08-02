# Chapter 8 — FEEL AST [IMPLEMENTATION-ALIGNED]

<!-- generated-toc:start -->
## Table of contents

- [8.1 Purpose](#contents-section-1)
- [8.2 AST root](#contents-section-2)
- [8.3 Implemented nodes](#contents-section-3)
- [8.4 Text and parsed separation](#contents-section-4)
- [8.5 Traversal](#contents-section-5)
- [8.6 Semantic state](#contents-section-6)
- [8.7 Design rules](#contents-section-7)
<!-- generated-toc:end -->


<a id="contents-section-1"></a>
## 8.1 Purpose

The FEEL AST is the protobuf compiler representation produced by `FeelAstBuilder`. It contains no ANTLR parse-tree objects.

```text
FEEL text → ANTLR parse tree → FeelAstBuilder → protobuf Expression
```

<a id="contents-section-2"></a>
## 8.2 AST root

`FeelParsed` contains:

```protobuf
message FeelParsed {
  Expression ast = 1;
}
```

`Expression` is a protobuf `oneof` containing the concrete expression node.

<a id="contents-section-3"></a>
## 8.3 Implemented nodes

- literal and name
- unary and binary expression
- function call and invocation
- if expression
- context and list
- for and quantified expression
- filter and path
- range, between, and in
- instance-of and descendant
- function definition
- unary tests
- provisional decision-table reference

Unary-test AST nodes support comparisons, ranges, ordinary expressions, negation, and wildcard tests.

<a id="contents-section-4"></a>
## 8.4 Text and parsed separation

`feel_text.proto` and `feel_parsed.proto` are independent schemas. The composition point is `feel.proto`, which defines replaceable wrappers.

The parsed representation does not redundantly retain FEEL text. The original semantic model remains available as the text-bearing input model.

<a id="contents-section-5"></a>
## 8.5 Traversal

`FeelAstBuilder` recursively converts ANTLR contexts directly. It does not use a generic DMN visitor.

`DmnFeelParser` then traverses the semantic model depth-first with generated getters and handles:

- item-definition and component constraints
- decisions and BKMs
- literal, boxed, invocation, and decision-table logic
- clauses, rules, unary tests, and output expressions
- contexts, relations, lists, and boxed function definitions

<a id="contents-section-6"></a>
## 8.6 Semantic state

The semantic pipeline validates names and structured paths, resolves named types, checks operators and built-in function calls, and populates `Expression.inferred_type` across the currently supported FEEL and boxed-expression forms. Successful symbol and named-type resolutions are exposed through an immutable `DmnSymbolBinding` side table rather than stored in AST nodes. Unsupported or invalid expressions use an unknown type and diagnostics.

Type analysis includes structured-list property projection, structured filter predicates, temporal and duration arithmetic, orderability checks for ranges and between expressions, subject-aware `in` unary tests, and named-type validation for `instance of`.

`DecisionTableExpression` is an internal ID reference to a decision table in the visible model set. Its type is derived deterministically from declared output clauses and hit policy; missing, unknown, and ambiguous IDs produce diagnostics. Legacy single-binding fields on `ForExpression` and `QuantifiedExpression` remain supported as a protobuf-compatibility fallback, while new AST builders continue to populate their repeated binding fields.

<a id="contents-section-7"></a>
## 8.7 Design rules

1. Parsed AST messages must not depend on ANTLR types.
2. FEEL text is parsed once.
3. Failed nodes remain text-based in diagnostic parsing mode.
4. AST traversal uses exhaustive generated `oneof` switches.
5. Schema changes follow protobuf compatibility rules.

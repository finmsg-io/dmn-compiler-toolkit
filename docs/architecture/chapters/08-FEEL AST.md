# Chapter 8 — FEEL AST [IMPLEMENTED, EVOLVING]

## 8.1 Purpose

The FEEL AST is the protobuf compiler representation produced by `FeelAstBuilder`. It contains no ANTLR parse-tree objects.

```text
FEEL text → ANTLR parse tree → FeelAstBuilder → protobuf Expression
```

## 8.2 AST root

`FeelParsed` contains:

```protobuf
message FeelParsed {
  Expression ast = 1;
}
```

`Expression` is a protobuf `oneof` containing the concrete expression node.

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

## 8.4 Text and parsed separation

`feel_text.proto` and `feel_parsed.proto` are independent schemas. The composition point is `feel.proto`, which defines replaceable wrappers.

The parsed representation does not redundantly retain FEEL text. The original semantic model remains available as the text-bearing input model.

## 8.5 Traversal

`FeelAstBuilder` recursively converts ANTLR contexts directly. It does not use a generic DMN visitor.

`DmnFeelParser` then traverses the semantic model depth-first with generated getters and handles:

- item-definition and component constraints
- decisions and BKMs
- literal, boxed, invocation, and decision-table logic
- clauses, rules, unary tests, and output expressions
- contexts, relations, lists, and boxed function definitions

## 8.6 Semantic state

The current semantic analyzer validates names and structured paths without yet storing resolved symbol IDs in AST nodes. `Expression.inferred_type` exists but comprehensive type inference remains future work.

## 8.7 Design rules

1. Parsed AST messages must not depend on ANTLR types.
2. FEEL text is parsed once.
3. Failed nodes remain text-based in diagnostic parsing mode.
4. AST traversal uses exhaustive generated `oneof` switches.
5. Schema changes follow protobuf compatibility rules.


# Chapter 12 --- FEEL Parser \[PROVISIONAL\]

## 12.1 Current Status

The FEEL parser is not yet implemented as a Maven module or Java parsing
pipeline.

Two protobuf layers already establish the boundary:

``` text
feel_source.proto     implemented source representation
feel_ast.proto        provisional target AST representation
```

The XML frontend currently produces `FeelSource`, `ExpressionSource`,
and boxed-expression source messages. It does not produce `Expression`
AST messages.

## 12.2 Required Transformation

``` text
Semantic Model containing FEEL source
    |
    v
FEEL source traversal
    |
    v
ANTLR lexer and parser
    |
    v
AST builder
    |
    v
Compiler model containing parsed FEEL AST
```

The compiler model is a copy or enrichment of the semantic model, not a
mutation of the original built message. Generated protobuf getters and
builders should be used for deterministic traversal and replacement.

## 12.3 Existing AST Schema

The provisional `Expression` message supports:

-   literal
-   name
-   unary
-   binary
-   function call
-   if expression
-   context
-   list
-   for expression
-   quantified expression
-   filter
-   path
-   invocation
-   decision-table reference
-   inferred type

The schema must be validated against the selected FEEL grammar before
being declared stable.

## 12.4 Traversal Requirement

FEEL compilation requires a generic depth-first protobuf traversal
capable of stepping into nested messages and applying actions at
selected field paths.

The traversal should:

-   use generated getters rather than reflection-heavy generic APIs for
    hot compiler paths
-   descend recursively into singular and repeated message fields
-   preserve deterministic field order
-   identify FEEL source locations by typed path
-   replace source-bearing structures in a copied compiler model
-   support path-specific actions without coupling the traversal to
    individual DMN readers

A visitor over DMN domain classes is not required for this stage.

## 12.5 Parser Responsibilities

The FEEL parser will:

-   parse each FEEL expression exactly once
-   parse unary tests separately where grammar rules differ
-   construct protobuf FEEL AST messages
-   preserve source locations where available
-   produce syntax diagnostics

It will not:

-   resolve DMN references
-   infer final types
-   build the DRG
-   optimize expressions
-   execute FEEL

## 12.6 Next Implementation Steps

``` text
1. Create dmn-feel-parser module.
2. Integrate the selected FEEL ANTLR grammar.
3. Implement source-to-AST builder for literal, name, unary, binary, path, and if expressions.
4. Implement unary-test parsing for decision-table input entries.
5. Implement depth-first semantic-model enrichment.
6. Add Traffic Violation FEEL parsing tests.
7. Stabilize feel_ast.proto only after parser coverage.
```

------------------------------------------------------------------------

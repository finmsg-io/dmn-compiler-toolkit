# Chapter 7 — Semantic Model [IMPLEMENTED]

## 7.1 Canonical representation

The canonical semantic model is the generated protobuf `Definitions` tree. There is no handwritten domain-model layer.

```text
DMN XML → dmn-frontend-xml → io.finmsg.dmn.model.Definitions
```

## 7.2 Protobuf organization

```text
common.proto          metadata, diagnostics, namespaces, references
core.proto            Node and common core structures
types.proto           semantic type references and information items
feel_text.proto       FEEL text and boxed-expression text structures
feel_parsed.proto     parsed FEEL and boxed-expression AST structures
feel.proto            replaceable text/parsed wrappers
decision_table.proto  decision tables and UnaryTest
drg.proto             DRG elements and decision logic
model.proto           Definitions, imports, item definitions, TypeConstraint
```

`feel_text.proto` has no dependency on `feel_parsed.proto`.

## 7.3 Replaceable representation pattern

```protobuf
message Feel {
  oneof representation {
    FeelText text = 1;
    FeelParsed parsed = 2;
  }
}
```

The same pattern is used for:

- `ExpressionNode`
- `BoxedExpression`
- `UnaryTest`
- `TypeConstraint`

The XML frontend creates text branches. The FEEL parsing pass replaces them in a copied model with parsed branches.

## 7.4 Immutability

Every compiler stage receives a built protobuf message. Transforming stages use `toBuilder()`, generated getters, and indexed repeated-field setters to produce a new built message.

The FEEL parsing tests verify:

- the semantic input remains text-based
- the returned model contains parsed nodes
- parsing an already parsed model is idempotent

## 7.5 Type model

`TypeReference` currently supports:

- built-in type
- named type
- list type
- function type

Named types are resolved against `ItemDefinition` declarations during semantic analysis. Structured path validation uses item components.

## 7.6 FEEL representation

`FeelParsed` contains the root `Expression` AST. Unary-test syntax uses `UnaryTestParsed` and `UnaryTestsExpression`.

The current AST includes literals, names, unary and binary operators, calls, invocation, paths, ranges, contexts, lists, loops, quantified expressions, filters, between/in/instance-of expressions, descendants, function definitions, and unary tests.

## 7.7 Current completeness boundaries

Known gaps include:

- source locations are not consistently populated by the XML frontend
- some XML expression IDs are not represented
- recursive item components are not represented
- imports and cross-model linking are incomplete
- resolved symbols and named types are exposed through `DmnSymbolBinding`; they are not persisted in the AST

Semantic analysis populates `Expression.inferred_type` across the currently supported FEEL and boxed-expression forms. Unsupported or invalid expressions retain an unknown type and produce diagnostics where applicable.

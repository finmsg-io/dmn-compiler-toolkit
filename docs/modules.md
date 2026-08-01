# Modules

## `dmn-protobuf`

Defines the canonical protobuf model in package `io.finmsg.dmn.model`.

Current schema files include:

```text
common.proto
core.proto
types.proto
feel_text.proto
feel_parsed.proto
feel.proto
decision_table.proto
drg.proto
model.proto
```

`feel.proto` defines replaceable nodes whose `oneof` representation is either textual or parsed. This includes `Feel`, `ExpressionNode`, and `BoxedExpression`. Decision-table `UnaryTest` and model `TypeConstraint` use the same text/parsed pattern.

## `dmn-frontend-xml`

Reads DMN XML with VTD-XML and creates a semantic `Definitions` model containing FEEL text.

Primary entry point:

```java
Definitions model = new DmnXmlReader().read(path);
```

The module also contains `DmnWriter`, `XmlEmitter`, and individual writer classes under:

```text
io.finmsg.dmn.frontend.xml.dmn.writer
```

Writer coverage is currently partial and must not yet be treated as complete DMN round-trip support.

## `dmn-feel-parser`

Contains:

- ANTLR4 FEEL lexer and parser grammars
- `FeelParserFacade`
- `FeelAstBuilder`
- `DmnFeelParser`
- expression and unary-test parsing
- explicit depth-first traversal of the complete semantic model
- model-aware, multi-error syntax diagnostics
- Traffic Violation integration tests

The pass returns a copied `Definitions` message. The input semantic model remains unchanged.

## `dmn-semantic-analysis`

Contains the semantic-analysis pipeline:

- global symbol collection
- requirement-aware decision scopes
- BKM parameter scopes
- sequential context-entry scopes
- FEEL name resolution
- structured item-definition property validation
- diagnostics for unknown, unavailable, duplicate, and ambiguous names
- named-type resolution and declared-type validation
- FEEL and boxed-expression type inference
- operator and built-in function validation
- decision-table, BKM, item-definition, and decision-service validation
- dependency validation, cycle detection, and deterministic compilation order

Expression and decision-table Runtime IR lowering remain future work.

## `dmn-runtime-ir`

Contains the immutable, protobuf-free runtime contracts and the semantic-to-runtime lowering boundary. The implemented baseline assigns deterministic integer node IDs and value slots, lowers structural types, preserves dependency order through integer references, and rejects semantic results containing diagnostics. Expression instructions, constant pools, and model-set lowering remain future work.

## Planned modules

```text
dmn-optimizer
dmn-codegen-java
dmn-runtime
dmn-compiler-api
dmn-benchmarks
```

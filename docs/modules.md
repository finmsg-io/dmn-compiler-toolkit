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

Reader and writer coverage is symmetric for the XML-representable portion of the current protobuf model. This includes imports, item definitions and constraints, all modeled DRG elements, decision tables, invocations, boxed expressions, documentation, structured extensions, namespace/version preservation, and prefixed-DMN output.

QName `typeRef` values are resolved in element scope, stored by namespace URI, and written using an existing or collision-free declared prefix. DMNDI, artifacts, business-context metadata, and deeper arbitrary extension trees require protobuf model extensions and are not part of the current round-trip claim. See the [completeness audit](audits/dmn-frontend-xml-completeness.md).

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
- persisted/exposed symbol and named-type bindings
- namespace-indexed cross-model imports, references, and dependency ordering

Persisted lexical frame layouts and linked model-set Runtime IR lowering remain future work.

## `dmn-runtime-ir`

Contains the immutable, protobuf-free runtime contracts and the semantic-to-runtime lowering boundary. The implemented baseline assigns deterministic integer IDs and slots, lowers structural types and every protobuf FEEL AST expression variant, preserves dependency order, and rejects unsuccessful semantic results. Decision logic lowering covers literal FEEL, decision tables, recursive boxed contexts/relations/lists/functions, and DMN invocations. BKM slots contain executable typed function definitions with preserved FEEL/Java/PMML kind. Persisted frame layouts, constant pooling, and model-set lowering remain future work.

See the [implementation assessment](todos/dmn-runtime-ir/assessment-dmn-runtime-ir.md) for the
execution-readiness gaps and recommended implementation order.

## Planned modules

```text
dmn-optimizer
dmn-codegen-java
dmn-runtime
dmn-compiler-api
dmn-benchmarks
```

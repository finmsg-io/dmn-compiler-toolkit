# Architecture

## Implemented architecture

The toolkit is a staged compiler rather than an XML-centric interpreter.

```text
DMN XML
  → XML Frontend
  → immutable protobuf Semantic Model with FEEL text
  → FEEL Parser pass
  → copied protobuf model with FEEL AST
  → Semantic Analysis
  → validated model and diagnostics
```

The first four Maven modules implement this pipeline through typed semantic analysis and deterministic dependency ordering for the current single-model scope.

## Stage boundaries

### XML frontend

Maps supported DMN XML structures to protobuf messages. It preserves FEEL text and performs no FEEL parsing or semantic resolution.

### Semantic model

Uses generated protobuf messages as the canonical representation. Replaceable nodes use text/parsed `oneof` branches.

### FEEL parser

Uses ANTLR4 and `FeelAstBuilder` to create protobuf AST nodes. `DmnFeelParser` traverses the model depth-first with generated getters, keeps the input immutable, and provides model-path diagnostics.

### Semantic analysis

The implemented first pass creates requirement-aware scopes and validates FEEL names and structured properties. Type inference and the remaining DMN validation passes follow next.

### Future stages

Optimizer, Runtime IR, code generators, and runtime execution are not implemented.

## Core rules

1. XML-specific code remains in `dmn-frontend-xml`.
2. Compiler passes consume generated protobuf contracts.
3. Every pass treats its input as immutable.
4. Traversal uses generated getters instead of protobuf reflection.
5. Diagnostics identify the semantic-model path and source location where available.
6. Runtime modules must not depend on XML or ANTLR.

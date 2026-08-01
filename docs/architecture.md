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
  → validated linked model set, bindings, order, and diagnostics
  → structural Runtime IR
```

Five Maven modules implement this pipeline through cross-model typed semantic analysis and deterministic structural Runtime IR lowering.

## Stage boundaries

### XML frontend

Maps the current protobuf-supported DMN subset to and from XML. It preserves FEEL text, namespaces, imports, node documentation/extensions, and optional source locations while performing no FEEL parsing or semantic resolution. QName `typeRef` namespace preservation remains open.

### Semantic model

Uses generated protobuf messages as the canonical representation. Replaceable nodes use text/parsed `oneof` branches.

### FEEL parser

Uses ANTLR4 and `FeelAstBuilder` to create protobuf AST nodes. `DmnFeelParser` traverses the model depth-first with generated getters, keeps the input immutable, and provides model-path diagnostics.

### Semantic analysis

The implemented pipeline creates requirement-aware scopes, resolves local and imported symbols/types, validates and infers FEEL types, validates dependencies, and exposes deterministic bindings and compilation order.

### Future stages

Structural Runtime IR is implemented. Expression instruction lowering, optimizer passes, code generators, and runtime execution are not implemented.

## Core rules

1. XML-specific code remains in `dmn-frontend-xml`.
2. Compiler passes consume generated protobuf contracts.
3. Every pass treats its input as immutable.
4. Traversal uses generated getters instead of protobuf reflection.
5. Diagnostics identify the semantic-model path and source location where available.
6. Runtime modules must not depend on XML or ANTLR.

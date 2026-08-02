# Architecture

The normative, detailed design is maintained in the
[architecture specification](architecture/architecture-spec.md). This page is the
short implementation-oriented overview.

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

Seven Maven modules implement this pipeline through cross-model typed semantic analysis,
deterministic Runtime IR lowering, interpretation, and compiler-facing source resolution.

## Stage boundaries

### XML frontend

Maps the current protobuf-supported DMN subset to and from XML. It preserves FEEL text, scoped QName type namespaces, imports, node documentation/extensions, and optional source locations while performing no FEEL parsing or semantic resolution.

### Semantic model

Uses generated protobuf messages as the canonical representation. Replaceable nodes use text/parsed `oneof` branches.

### FEEL parser

Uses ANTLR4 and `FeelAstBuilder` to create protobuf AST nodes. `DmnFeelParser` traverses the model depth-first with generated getters, keeps the input immutable, and provides model-path diagnostics.

### Semantic analysis

The implemented pipeline creates requirement-aware scopes, resolves local and imported symbols/types, validates and infers FEEL types, validates dependencies, and exposes deterministic bindings and compilation order.

### Future stages

Executable Runtime IR, typed constant/built-in indexing, and a process-local interpreter are implemented. Further optimizer passes, host bindings for external functions, and code generators remain future work.

## Core rules

1. XML-specific code remains in `dmn-frontend-xml`.
2. Compiler passes consume generated protobuf contracts.
3. Every pass treats its input as immutable.
4. Traversal uses generated getters instead of protobuf reflection.
5. Diagnostics identify the semantic-model path and source location where available.
6. Runtime modules must not depend on XML or ANTLR.

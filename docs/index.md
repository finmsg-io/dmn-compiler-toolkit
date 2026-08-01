# DMN Compiler Toolkit

The DMN Compiler Toolkit is a performance-oriented compiler infrastructure for Decision Model and Notation (DMN).

It parses DMN XML once, creates an immutable protobuf semantic model, parses FEEL into protobuf AST nodes, performs semantic analysis, and will later lower the validated model into an execution-oriented Runtime IR.

## Current implementation

The repository currently contains four active Maven modules:

- `dmn-protobuf` — semantic model, replaceable FEEL text/parsed nodes, and FEEL AST
- `dmn-frontend-xml` — VTD-XML reader and partial DMN XML writer
- `dmn-feel-parser` — ANTLR4 grammar, AST builder, complete DMN FEEL parsing pass, and model-aware diagnostics
- `dmn-semantic-analysis` — reference resolution, type analysis, DMN validation, and deterministic dependency ordering

The implemented pipeline is:

```text
DMN XML
   │
   ▼
DmnXmlReader
   │
   ▼
Definitions with FEEL text
   │
   ▼
DmnFeelParser
   │
   ▼
Definitions with parsed FEEL AST
   │
   ▼
DmnSemanticAnalyzer
   │
   ▼
Semantic-analysis result and diagnostics
```

Structural Runtime IR and deterministic graph/type lowering are implemented. Expression lowering, optimization, code generation, and execution remain future stages.

## Design goals

- Faithful DMN 1.5 model support
- Semantic model independent from XML implementation details
- Immutable protobuf messages between compiler stages
- Explicit depth-first passes using generated protobuf getters
- Structured, model-aware diagnostics
- Efficient Java code generation
- Extensible backend architecture
- Predictable runtime performance

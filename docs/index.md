# DMN Compiler Toolkit

<!-- generated-toc:start -->
## Table of contents

- [Current implementation](#contents-section-1)
- [Design goals](#contents-section-2)
<!-- generated-toc:end -->

The DMN Compiler Toolkit is a performance-oriented compiler infrastructure for Decision Model and Notation (DMN).

It parses DMN XML once, creates an immutable protobuf semantic model, parses FEEL into protobuf AST nodes, performs semantic analysis, and lowers the validated model into an execution-oriented Runtime IR.

<a id="contents-section-1"></a>
## Current implementation

The repository currently contains seven active Maven modules:

- `dmn-protobuf` — semantic model, replaceable FEEL text/parsed nodes, and FEEL AST
- `dmn-frontend-xml` — namespace-aware VTD-XML reader and writer for the current protobuf-supported DMN subset
- `dmn-feel-parser` — ANTLR4 grammar, AST builder, complete DMN FEEL parsing pass, and model-aware diagnostics
- `dmn-semantic-analysis` — reference resolution, type analysis, DMN validation, and deterministic dependency ordering
- `dmn-runtime-ir` — immutable Runtime IR with deterministic structure, constants, and bound value references
- `dmn-runtime` — experimental process-local interpreter for executable Runtime IR
- `dmn-compiler` — compiler orchestration and resolver-independent model-source contracts

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

Cross-model semantic linking, QName-safe XML type references, linked model-set Runtime IR lowering, all protobuf FEEL AST expression variants, all modeled decision logic, executable BKM functions, persisted lexical frame layouts, expression-derived runtime dependencies, indexed context-field access, initial optimization, and process-local interpretation are implemented. Further optimization, production runtime semantics, and code generation remain work in progress.

<a id="contents-section-2"></a>
## Design goals

- Faithful DMN 1.5 model support
- Semantic model independent from XML implementation details
- Immutable protobuf messages between compiler stages
- Explicit depth-first passes using generated protobuf getters
- Structured, model-aware diagnostics
- Efficient Java code generation
- Extensible backend architecture
- Predictable runtime performance

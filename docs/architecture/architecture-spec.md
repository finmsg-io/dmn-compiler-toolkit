# DMN Compiler Toolkit

## Architecture Specification

**Version 1.2 — Implementation-Aligned Baseline**

## Document status

This version reflects the implementation available in August 2026.

Status labels:

- **IMPLEMENTED** — present in the codebase and covered by automated tests
- **PARTIAL** — implemented for a defined subset
- **FUTURE** — architectural target without an active implementation

Current modules:

```text
dmn-compiler-toolkit
├── dmn-protobuf
├── dmn-frontend-xml
├── dmn-feel-parser
├── dmn-semantic-analysis
└── dmn-runtime-ir
```

Current executable compiler path:

```text
DMN XML
  → DmnXmlReader
  → Definitions with FEEL text
  → DmnFeelParser
  → Definitions with parsed FEEL AST
  → DmnSemanticPipeline
  → typed model, deterministic compilation order, and semantic diagnostics
```

The semantic-analysis stage implements reference and structured-property resolution, exposes successful symbol and named-type bindings, performs type and expression analysis, validates operators, functions, imports, and DMN structures, and analyzes dependency graphs across namespace-linked models. Bindings are exposed as an immutable side table rather than persisted in protobuf AST nodes. Structural Runtime IR, typed constants, and bound value-slot references are implemented; compound expression lowering, optimization, code generation, and runtime execution remain future stages.

## Table of contents

1. [Vision](./chapters/01-Vision.md)
2. [Architecture Principles](./chapters/02-Architecture%20Principles.md)
3. [Overall Architecture](./chapters/03-Overall%20Architecture.md)
4. [Logical Component Architecture](./chapters/04-Logical%20Component%20Architecture.md)
5. [Maven Modules](./chapters/05-Maven%20Modules.md)
6. [Package Layout](./chapters/06-Package%20Layout.md)
7. [Semantic Model](./chapters/07-Semantic%20Model.md)
8. [FEEL AST](./chapters/08-FEEL%20AST.md)
9. [Runtime IR](./chapters/09-Runtime%20IR.md)
10. [Compiler Passes](./chapters/10-Compiler%20Passes.md)
11. [XML Frontend](./chapters/11-XML%20Frontend.md)
12. [FEEL Parser](./chapters/12-FEEL%20Parser.md)
13. [Java Generator](./chapters/13-Java%20Generator.md)
14. [Future Generators](./chapters/14-Future%20Generators.md)
15. [Testing Strategy](./chapters/15-Testing%20Strategy.md)
16. [Performance](./chapters/16-Performance.md)
17. [Public API](./chapters/17-Public%20API.md)
18. [Roadmap](./chapters/18-Roadmap.md)
19. [Internal Compiler Architecture](./chapters/19-Internal%20Compiler%20Architecture.md)
20. [Appendices](./chapters/20-Appendices.md)

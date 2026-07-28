# DMN Compiler Toolkit

The DMN Compiler Toolkit is a performance-oriented compiler infrastructure for Decision Model and Notation.

Its purpose is to transform DMN models into an explicit semantic representation that can be validated, optimized, and translated into efficient executable code.

## Why this project exists

Many DMN engines retain a strong dependency on XML models and runtime interpretation. That approach is flexible, but it can repeatedly perform work that is better handled once during compilation.

The toolkit follows a different model:

```text
Parse once
Validate once
Optimize once
Generate once
Execute many times
```

## Current scope

The current implementation contains:

- A Protobuf-based semantic model
- A VTD-XML-based DMN frontend
- An ANTLR4-based FEEL parser
- A multi-module Maven build
- GitHub Actions CI and GitHub Packages publishing

## Compiler pipeline

```text
DMN XML
   │
   ▼
XML Frontend
   │
   ▼
Semantic Model
   │
   ▼
FEEL Parser
   │
   ▼
Semantic Analysis
   │
   ▼
Optimizer
   │
   ▼
Runtime IR
   │
   ▼
Code Generator
   │
   ▼
Runtime
```

## Project goals

- Faithful DMN 1.5 model support
- Strong separation between frontend, compiler, and runtime
- Efficient Java code generation
- Extensible backend architecture
- Testable compiler passes
- Predictable runtime performance

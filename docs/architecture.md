# Architecture

## Architectural direction

The toolkit is designed as a compiler rather than as an XML-centric interpreter.

The central design decision is that the runtime should not depend on DMN XML structures. XML is an input format handled by the frontend. The compiler operates on a semantic model.

## Main stages

### XML frontend

Reads DMN XML and creates the semantic model.

Responsibilities:

- Namespace handling
- DMN element reading
- Source-location capture
- Structural mapping
- Preservation of FEEL source text for later parsing

### Semantic model

Represents DMN concepts independently from XML.

The model is defined with Protobuf and generated as Java classes.

Benefits:

- Stable contracts between compiler stages
- Efficient serialization
- Explicit schema evolution
- Generated accessors
- Language-independent representation

### FEEL parser

Parses FEEL source text into a compiler-friendly representation.

ANTLR4 is used for lexer and parser generation.

### Semantic analysis

Planned responsibilities:

- Symbol resolution
- Type resolution
- Function resolution
- Dependency validation
- Expression validation
- Decision-table validation

### Optimizer

Planned optimization passes include:

- Constant folding
- Dead-expression elimination
- Reuse of resolved references
- Decision-table specialization
- Precomputed dependency ordering

### Runtime IR

The runtime intermediate representation will contain only the information required for efficient execution.

It should be:

- Independent from XML
- Independent from frontend implementation details
- Suitable for multiple code generators
- Compact and execution-oriented

### Code generators

The first backend is expected to generate optimized Java code.

Future backends may target other execution environments.

## Design principles

1. Semantic model over XML model
2. Compile before execute
3. Explicit compiler passes
4. Runtime independence
5. Performance-first implementation
6. Extensible backends
7. Generated, strongly typed model access
8. Small modules with focused responsibilities

# Modules

## `dmn-protobuf`

Defines the semantic model using Protocol Buffers.

Current model areas include:

- Common node metadata
- Definitions
- Imports
- Item definitions
- Type references
- DRG elements
- Decisions
- Input data
- Business knowledge models
- Knowledge sources
- Decision services
- Decision tables
- FEEL source expressions

The module generates Java classes used by later compiler stages.

## `dmn-frontend-xml`

Reads DMN XML using VTD-XML and creates semantic-model messages.

Implemented readers include:

- Definitions
- Item definitions
- Variables
- Input data
- Decisions
- Decision requirements
- Decision tables
- Input clauses
- Output clauses
- Decision rules
- FEEL source expressions

The frontend intentionally keeps FEEL parsing separate.

## `dmn-feel-parser`

Contains the ANTLR4 grammar and generated FEEL lexer/parser.

Responsibilities:

- Tokenization
- Parsing
- Syntax error reporting
- Preparation for AST construction

## Planned modules

Likely future modules:

```text
dmn-semantic-analysis
dmn-optimizer
dmn-runtime-ir
dmn-codegen-java
dmn-runtime
dmn-benchmarks
```

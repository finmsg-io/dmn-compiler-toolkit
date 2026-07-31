# Chapter 4 --- Logical Component Architecture \[STABLE TARGET / PARTIALLY IMPLEMENTED\]

## 4.1 Purpose

The DMN Compiler Toolkit is composed of a set of loosely coupled
compiler components.

Each component has a single responsibility and communicates only through
well-defined intermediate representations.

The architecture follows a pipeline model in which each component
transforms one representation into another while preserving semantic
correctness.

The objective is to maximize maintainability, extensibility,
testability, and performance.

Current implementation status:

- XML frontend and canonical semantic model are implemented.
- FEEL parsing and protobuf AST construction are implemented.
- The semantic analyzer currently implements name and structured-property resolution.
- The model validator, dependency graph builder, optimizer, Runtime IR, generators, and runtime remain target components.

------------------------------------------------------------------------

# 4.2 Component Overview

``` text
                        +---------------------------+
                        |      DMN XML Reader       |
                        +-------------+-------------+
                                      |
                                      v
                        +---------------------------+
                        |      Semantic Model       |
                        +-------------+-------------+
                                      |
                     +----------------+----------------+
                     |                                 |
                     v                                 v
            +-------------------+             +-------------------+
            |   FEEL Parser      |             | Model Validator   |
            +---------+----------+             +---------+---------+
                      |                                  |
                      +----------------+-----------------+
                                       |
                                       v
                           +---------------------------+
                           |     Semantic Analyzer     |
                           +-------------+-------------+
                                         |
                                         v
                           +---------------------------+
                           | Dependency Graph Builder  |
                           +-------------+-------------+
                                         |
                                         v
                           +---------------------------+
                           | Compiler Pass Framework   |
                           +-------------+-------------+
                                         |
                                         v
                           +---------------------------+
                           |       Runtime Builder     |
                           +-------------+-------------+
                                         |
                                         v
                           +---------------------------+
                           |        Runtime IR         |
                           +-------------+-------------+
                                         |
             +--------------+------------+-------------+-------------+
             |              |                          |             |
             v              v                          v             v
        Java Generator  Rust Generator          Spark Generator  Interpreter
```

------------------------------------------------------------------------

# 4.3 XML Frontend

## Responsibility

Read and write DMN XML documents.

### Responsibilities

-   XML parsing
-   namespace resolution
-   schema version detection
-   source location tracking
-   extension element preservation
-   XML serialization

### Does NOT

-   parse FEEL
-   resolve references
-   validate types
-   optimize expressions

### Input

``` text
DMN XML
```

### Output

``` text
Semantic Model
```

------------------------------------------------------------------------

# 4.4 Semantic Model

The Semantic Model is the canonical representation of a DMN document.

It represents the specification, not the XML syntax.

### Responsibilities

Represent

-   Definitions
-   Decisions
-   BKMs
-   InputData
-   Decision Services
-   Item Definitions
-   Decision Tables
-   FEEL source expressions

The Semantic Model is immutable after construction.

------------------------------------------------------------------------

# 4.5 FEEL Parser

## Responsibility

Convert FEEL source code into an Abstract Syntax Tree.

### Input

``` text
"a+b*c"
```

### Output

``` text
Binary(+)

├── Variable(a)

└── Binary(*)

    ├── Variable(b)

    └── Variable(c)
```

The parser performs syntax analysis only.

------------------------------------------------------------------------

# 4.6 Model Validator

The validator checks structural correctness before semantic analysis.

Examples

-   duplicate IDs
-   duplicate names
-   missing references
-   invalid namespaces
-   malformed decision tables
-   invalid XML combinations

The validator does not resolve types.

------------------------------------------------------------------------

# 4.7 Semantic Analyzer

The semantic analyzer enriches the model.

Responsibilities

-   reference resolution
-   type inference
-   type checking
-   function resolution
-   dependency analysis
-   cycle detection
-   context validation
-   decision table validation

Output

``` text
Validated Semantic Model
```

------------------------------------------------------------------------

# 4.8 Dependency Graph Builder

Constructs the complete Decision Requirements Graph (DRG).

Produces

``` text
Decision A

↓

Decision C

↓

Decision D
```

Used for

-   execution ordering
-   optimization
-   dead code elimination
-   incremental compilation

------------------------------------------------------------------------

# 4.9 Compiler Pass Framework

All optimizations will be implemented as compiler passes.

Every pass

-   receives one model
-   produces one model

No pass mutates its input.

Example

``` text
Input

↓

Constant Folding

↓

Output
```

------------------------------------------------------------------------

# 4.10 Runtime Builder

Transforms the semantic model into Runtime IR.

Responsibilities

-   integer ID assignment
-   compact memory layout
-   reference flattening
-   expression lowering
-   execution graph generation

------------------------------------------------------------------------

# 4.11 Runtime IR

The Runtime IR is the execution model.

Characteristics

-   immutable
-   compact
-   cache friendly
-   serialization friendly
-   XML free
-   FEEL free

Contains

``` text
RuntimeDecision

RuntimeVariable

RuntimeExpression

RuntimeFunction

ExecutionGraph
```

------------------------------------------------------------------------

# 4.12 Code Generators

Every generator consumes Runtime IR.

Never XML.

Never FEEL.

Never Semantic Model.

Generators

-   Java
-   Rust
-   Go
-   Spark SQL
-   Interpreter
-   Future generators

------------------------------------------------------------------------

# 4.13 Runtime

The runtime executes Runtime IR.

Responsibilities

-   decision execution
-   expression evaluation
-   function invocation
-   result construction

The runtime performs

-   no XML parsing
-   no FEEL parsing
-   no semantic analysis

------------------------------------------------------------------------

# 4.14 Diagnostics Framework

Diagnostics are collected throughout the pipeline.

Categories

-   Error
-   Warning
-   Information
-   Hint

Every diagnostic includes

-   error code
-   message
-   source location
-   compiler phase
-   optional fix suggestion

------------------------------------------------------------------------

# 4.15 Extension Framework

The architecture supports future extensions.

Examples

-   custom functions
-   custom type systems
-   optimization plugins
-   alternative generators
-   additional XML namespaces

Extensions must never violate the Architecture Principles.

------------------------------------------------------------------------

# 4.16 Component Interaction Rules

The following rules are mandatory:

1.  Components communicate only through defined intermediate
    representations.
2.  Components must not access internal state of other components.
3.  Compiler passes are stateless.
4.  Runtime components never depend on compiler components.
5.  XML components never appear in runtime modules.
6.  Code generators never modify Runtime IR.
7.  Diagnostics are propagated, never ignored.

------------------------------------------------------------------------

# 4.17 Component Dependency Matrix

| Component         | Depends On          |
| ----------------- | ------------------- |
| XML Reader        | VTD-XML             |
| Semantic Model    | Common Model        |
| FEEL Parser       | ANTLR               |
| Validator         | Semantic Model      |
| Semantic Analyzer | Validator, FEEL AST |
| Dependency Graph  | Semantic Analyzer   |
| Compiler Passes   | Dependency Graph    |
| Runtime Builder   | Compiler Passes     |
| Code Generator    | Runtime IR          |
| Runtime           | Runtime IR          |


No component may depend on a higher layer.

------------------------------------------------------------------------

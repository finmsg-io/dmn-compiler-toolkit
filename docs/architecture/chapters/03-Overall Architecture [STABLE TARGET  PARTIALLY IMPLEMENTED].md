# Chapter 3 --- Overall Architecture \[STABLE TARGET / PARTIALLY IMPLEMENTED\]

## 3.1 Overview

The DMN Compiler Toolkit is designed as a multi-stage compiler.

Each stage performs exactly one transformation and produces a
well-defined intermediate representation. The output of one stage
becomes the input of the next.

The architecture intentionally separates XML processing, semantic
analysis, optimization, and code generation. This separation allows each
component to evolve independently while maintaining a stable overall
architecture.

                            +----------------+
                            |   DMN XML      |
                            +--------+-------+
                                     |
                                     v
                        +-------------------------+
                        |      XML Frontend       |
                        |        (VTD-XML)        |
                        +------------+------------+
                                     |
                                     v
                        +-------------------------+
                        |     Semantic Model      |
                        |   (protobuf / immutable)|
                        +------------+------------+
                                     |
                                     v
                        +-------------------------+
                        |      FEEL Parser        |
                        |        (ANTLR4)         |
                        +------------+------------+
                                     |
                                     v
                        +-------------------------+
                        |        FEEL AST         |
                        +------------+------------+
                                     |
                                     v
                        +-------------------------+
                        |   Semantic Analysis     |
                        +------------+------------+
                                     |
                                     v
                        +-------------------------+
                        |  Optimization Passes    |
                        +------------+------------+
                                     |
                                     v
                        +-------------------------+
                        |      Runtime IR         |
                        +------------+------------+
                                     |
              +-----------+----------+-----------+-----------+
              |           |          |           |           |
              v           v          v           v           v
          Java        Rust       Go      Spark SQL    Interpreter

------------------------------------------------------------------------

# 3.2 Compiler Stages

The target compiler consists of seven major stages. The current
implementation completes the XML Frontend to Semantic Model
transformation.

  Stage               Input                  Output
  ------------------- ---------------------- -----------------
  XML Frontend        DMN XML                Semantic Model
  FEEL Parser         FEEL text              FEEL AST
  Semantic Analyzer   Semantic Model + AST   Validated Model
  Optimizer           Validated Model        Optimized Model
  Runtime Builder     Optimized Model        Runtime IR
  Code Generator      Runtime IR             Target Language
  Runtime             Runtime IR             Decision Result

Each stage is isolated and independently testable.

------------------------------------------------------------------------

# 3.3 Frontend

The frontend is responsible only for reading and writing DMN XML.

Responsibilities:

-   XML parsing
-   namespace handling
-   schema version detection
-   location tracking
-   diagnostics
-   extension elements
-   serialization

The frontend does **not**

-   interpret FEEL
-   resolve references
-   perform type checking
-   execute decisions

------------------------------------------------------------------------

# 3.4 Semantic Model

The Semantic Model is the compiler's canonical representation of DMN.

It contains

-   Definitions
-   Decisions
-   BKMs
-   InputData
-   Decision Services
-   Item Definitions
-   Decision Tables
-   FEEL source text
-   Metadata

The Semantic Model intentionally preserves semantic information while
removing XML-specific constructs.

------------------------------------------------------------------------

# 3.5 FEEL Parsing

Every FEEL expression is parsed exactly once.

    literalExpression

    ↓

    ANTLR Parser

    ↓

    FEEL AST

After this stage no compiler component manipulates FEEL text.

All compiler passes operate on the AST.

------------------------------------------------------------------------

# 3.6 Semantic Analysis

The semantic analyzer performs all validation required by the DMN
specification.

Examples include:

-   reference resolution
-   type inference
-   type checking
-   function resolution
-   dependency graph construction
-   cycle detection
-   context validation
-   decision table validation

Errors detected during semantic analysis prevent Runtime IR generation.

------------------------------------------------------------------------

# 3.7 Optimization

Optimization transforms the validated semantic model into a more
efficient representation while preserving semantics.

Typical optimizations include:

-   constant folding
-   dead decision elimination
-   decision inlining
-   common subexpression elimination
-   decision table normalization
-   dependency pruning
-   expression simplification

All optimizations are deterministic.

------------------------------------------------------------------------

# 3.8 Runtime IR

The Runtime Intermediate Representation (Runtime IR) is the execution
model consumed by code generators and interpreters.

Characteristics:

-   XML independent
-   FEEL independent
-   integer-based references
-   immutable
-   optimized for execution
-   cache-friendly
-   serialization-friendly

The Runtime IR contains only executable information.

Documentation, XML metadata, and editor-specific information are
excluded.

------------------------------------------------------------------------

# 3.9 Code Generation

Code generators consume only the Runtime IR.

    Runtime IR
          │
          ├──► Java
          ├──► Rust
          ├──► Go
          ├──► Spark SQL
          └──► Future Backends

This architecture ensures all generators benefit equally from compiler
optimizations.

------------------------------------------------------------------------

# 3.10 Runtime

The runtime is intentionally simple.

Responsibilities:

-   execute Runtime IR
-   evaluate expressions
-   invoke generated functions
-   return decision results

The runtime performs no compilation.

No parsing.

No validation.

No dependency resolution.

This separation minimizes runtime overhead and maximizes execution
performance.

------------------------------------------------------------------------

# 3.11 Architectural Dependency Graph

The following dependency graph is mandatory.

                    +----------------------+
                    |     XML Frontend     |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    |    Semantic Model    |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    |      FEEL AST        |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    | Semantic Analyzer    |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    |  Optimization Passes |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    |     Runtime IR       |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    |   Code Generators    |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    |       Runtime        |
                    +----------------------+

Dependencies must always point downward. Reverse dependencies are
prohibited.

------------------------------------------------------------------------

# 3.12 Benefits of the Architecture

The proposed architecture provides the following advantages:

-   **Separation of Concerns**: Each stage has a single, well-defined
    responsibility.
-   **Performance**: Parsing, validation, and optimization are performed
    once during compilation.
-   **Extensibility**: New optimization passes and code generators can
    be added without modifying existing stages.
-   **Maintainability**: Clear module boundaries reduce coupling and
    simplify long-term evolution.
-   **Testability**: Every stage can be unit-tested independently.
-   **Multi-language Support**: All code generators share the same
    optimized Runtime IR.
-   **Specification Compliance**: Semantic analysis enforces DMN
    correctness while allowing internal optimizations.

------------------------------------------------------------------------

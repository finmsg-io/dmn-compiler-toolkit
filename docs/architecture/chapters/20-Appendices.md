# Appendices

## Appendix A — Design Decisions (ADR)

Every major decision gets documented.

Example

```text
ADR-0001

Why protobuf?

ADR-0002

Why VTD-XML?

ADR-0003

Why Runtime IR?

ADR-0004

Why immutable compiler passes?

ADR-0005

Why compiler architecture instead of interpreter?
```

Years later you'll know **why** decisions were made.

---

## Appendix B — Dependency Rules

For example

```text
generator-java

        ↓

runtime

        ↓

semantic

        ↓

feel

        ↓

model

        ↓

xml
```

Never the opposite.

---

## Appendix C — Coding Standards

Naming

Builders

Exceptions

Logging

Testing

Performance

Documentation

---
I strongly recommend adding a glossary. For a compiler project of this size, a shared vocabulary is almost as important as the architecture itself. It reduces ambiguity for contributors and avoids repeated explanations across the documentation.

---

## Appendix D — Glossary

### D.1 Purpose

This glossary defines the terminology used throughout the DMN Compiler Toolkit.

The definitions are normative for this project and may differ from informal usage in compiler literature.

---

### A

#### Abstract Syntax Tree (AST)

A tree representation of a parsed FEEL expression.

The AST represents the syntactic structure of an expression while removing the concrete grammar syntax.

Example:

```text
a + b * c

↓

Binary(+)

├── Variable(a)

└── Binary(*)

    ├── Variable(b)

    └── Variable(c)
```

---

### B

#### Backend

A component that transforms the Runtime IR into an executable target.

Examples:

* Java
* Rust
* Go
* Spark SQL
* LLVM
* WebAssembly

---

#### BKM (Business Knowledge Model)

A reusable decision logic component defined by the DMN specification.

A BKM behaves similarly to a function.

---

### C

#### Canonical Representation

The authoritative internal representation used by the compiler.

For this project:

```text
Semantic Model
```

is the canonical representation of a DMN model.

---

#### Code Generation

The compiler stage that converts Runtime IR into executable artifacts.

Example:

```text
Runtime IR

↓

Java Source
```

---

#### Compilation

The complete transformation from DMN XML into executable output.

Example:

```text
DMN XML

↓

Compiler Pipeline

↓

Runtime IR

↓

Generated Java
```

---

#### Compiler Pass

An isolated transformation or analysis step within the compiler.

Each pass has exactly one responsibility.

Examples:

* Type Resolution
* Constant Folding
* Runtime IR Generation

---

##### Compiler Pipeline#

The ordered sequence of compiler passes executed during compilation.

---

#### Constant Folding

An optimization that evaluates constant expressions during compilation.

Example:

```text
10 + 20

↓

30
```

---

### D

#### Decision

A unit of executable business logic defined by DMN.

---

#### Decision Graph

The directed dependency graph connecting decisions, input data, and BKMs.

Also known as the Decision Requirements Graph (DRG).

---

#### Decision Table

A tabular representation of business rules consisting of:

* inputs
* outputs
* rules
* hit policy

---

#### Diagnostic

A structured message produced during compilation.

Categories include:

* Error
* Warning
* Information
* Hint

---

#### DRG (Decision Requirements Graph)

The dependency graph defined by the DMN specification.

---

### E

#### Execution Graph

The optimized runtime dependency graph stored inside Runtime IR.

Unlike the DRG, it contains only executable runtime information.

---

#### Extension Point

A stable interface intended for future compiler extensions.

Examples:

* Compiler passes
* Code generators
* Custom functions

---

### F

#### FEEL

Friendly Enough Expression Language.

The expression language defined by the OMG DMN specification.

---

#### FEEL AST

The Abstract Syntax Tree produced from FEEL expressions.

All compiler analysis operates on the AST rather than source text.

---

#### Frontend

The compiler component responsible for reading DMN XML.

Responsibilities include:

* XML parsing
* namespace handling
* diagnostics

---

### I

#### Immutable

An object whose state cannot change after creation.

The Semantic Model, FEEL AST, and Runtime IR are immutable.

---

#### Intermediate Representation (IR)

A representation used internally by the compiler between stages.

Examples:

* Semantic Model
* Runtime IR

---

### J

#### Java Generator

The backend that produces Java source code from Runtime IR.

---

### L

#### Lowering

The process of transforming a high-level representation into a lower-level representation suitable for execution.

Example:

```text
Semantic Model

↓

Runtime IR
```

---

### M

#### Model Validator

A compiler component responsible for structural validation.

It performs checks before semantic analysis.

---

### O

#### Optimization

A compiler transformation that improves performance without changing observable behavior.

Examples:

* Constant Folding
* Inlining
* Dead Decision Elimination

---

### P

#### Pass

See **Compiler Pass**.

---

#### Pass Manager

The compiler component responsible for scheduling and executing compiler passes.

---

#### Pipeline

The ordered sequence of compiler stages.

---

#### Protobuf

Protocol Buffers.

The schema language used for the Semantic Model.

---

### R

#### Reference Resolution

The compiler process that connects symbolic references with their targets.

Example:

```text
Decision A

↓

InputData X
```

---

#### Runtime

The execution engine responsible for evaluating compiled decisions.

The runtime performs:

* no XML parsing
* no FEEL parsing
* no semantic analysis

---

#### Runtime IR

The Runtime Intermediate Representation.

A compact execution-oriented representation consumed by code generators and interpreters.

Characteristics:

* immutable
* optimized
* XML independent
* FEEL independent

---

### S

#### Semantic Analysis

Compiler phase that verifies semantic correctness.

Examples:

* type checking
* reference resolution
* dependency analysis

---

#### Semantic Model

The compiler's canonical representation of a DMN model.

It contains business semantics but no XML-specific information.

---

#### Source Location

The position of an element within the original DMN source document.

Typically:

* file
* line
* column

Used for diagnostics.

---

### T

#### Type Inference

Automatic determination of FEEL expression types.

---

### T

#### Type Resolution

The compiler process of resolving named types into internal representations.

---

### V

#### Validation

The process of checking whether a model satisfies structural and semantic constraints.

Validation occurs in multiple compiler stages.

---

### X

####     XML Frontend

The compiler component responsible for reading and writing DMN XML.

The XML Frontend is not part of the runtime.

---

### D.2 Acronyms

| Acronym  | Meaning                             |
| -------- | ----------------------------------- |
| ADR      | Architecture Decision Record        |
| API      | Application Programming Interface   |
| AST      | Abstract Syntax Tree                |
| BKM      | Business Knowledge Model            |
| CI       | Continuous Integration              |
| CLI      | Command Line Interface              |
| DMN      | Decision Model and Notation         |
| DRG      | Decision Requirements Graph         |
| FEEL     | Friendly Enough Expression Language |
| IR       | Intermediate Representation         |
| JVM      | Java Virtual Machine                |
| JIT      | Just-In-Time Compiler               |
| LSP      | Language Server Protocol            |
| OMG      | Object Management Group             |
| POJO     | Plain Old Java Object               |
| protobuf | Protocol Buffers                    |

---

### D.3 Compiler Pipeline Terminology

The following terms describe the progression of a DMN model through the compiler.

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
FEEL AST
    │
    ▼
Semantic Analysis
    │
    ▼
Optimization
    │
    ▼
Runtime IR
    │
    ▼
Code Generator
    │
    ▼
Executable Artifact
```

Each stage has a clearly defined input and output, ensuring that responsibilities remain separated and transformations are deterministic.

---

### D.4 Naming Conventions

Throughout this specification, the following naming conventions are used consistently:

| Term          | Meaning                                                                                       |
| ------------- | --------------------------------------------------------------------------------------------- |
| **Model**     | A high-level representation of DMN semantics (e.g., Semantic Model, Runtime Model).           |
| **IR**        | An intermediate representation optimized for compiler processing or execution.                |
| **Pass**      | A single compiler transformation or analysis step.                                            |
| **Phase**     | A group of related compiler passes (e.g., Semantic Analysis, Optimization).                   |
| **Frontend**  | Components that ingest and parse source artifacts.                                            |
| **Backend**   | Components that generate executable artifacts from the Runtime IR.                            |
| **Runtime**   | Components responsible only for executing compiled models.                                    |
| **Generator** | A backend that emits code or executable artifacts for a specific target language or platform. |

---

### D.5 Summary

The glossary establishes a common vocabulary for contributors, reviewers, and users of the DMN Compiler Toolkit. By using these definitions consistently across the architecture specification, source code, API documentation, and ADRs, the project minimizes ambiguity and makes collaboration easier as the codebase and community grow.

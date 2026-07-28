# Chapter 5 --- Maven Modules \[IMPLEMENTED / TARGET\]

## 5.1 Purpose

The Maven structure is introduced incrementally. Modules are added only
when an architectural stage has a concrete implementation and a stable
dependency boundary.

The implementation currently contains two active child modules. The
larger module graph remains the target architecture.

## 5.2 Current Project Structure

``` text
dmn-compiler-toolkit
│
├── pom.xml
│
├── dmn-protobuf
│   ├── common.proto
│   ├── core.proto
│   ├── types.proto
│   ├── feel_source.proto
│   ├── feel_ast.proto
│   ├── decision_table.proto
│   ├── drg.proto
│   └── model.proto
│
└── dmn-frontend-xml
    ├── XmlCursor
    ├── VtdXmlCursor
    ├── DmnXmlReader
    ├── DmnVersionDetector
    └── DMN element readers
```

Both modules use:

``` text
groupId:    io.finmsg.dmn
version:    1.0.0-SNAPSHOT
Java:       generated protobuf Java classes and JDK 25 compilation
```

## 5.3 Module: dmn-protobuf \[IMPLEMENTED\]

### Responsibility

Defines the current canonical Semantic Model, FEEL source
representation, provisional FEEL AST schema, common metadata, and DMN
type system.

The generated Java classes use:

``` text
io.finmsg.dmn.model
```

The module currently contains no compiler logic.

### Implemented schema dependency order

``` text
common.proto
    |
    v
core.proto
    |
    +------------------+
    |                  |
    v                  v
types.proto       feel_source.proto
    |                  |
    +--------+---------+
             |
             v
decision_table.proto
             |
             v
drg.proto
             |
             v
model.proto

feel_ast.proto is currently an independent provisional compiler schema.
```

### Implemented model areas

-   common node metadata
-   definitions, imports, namespaces
-   item definitions and item components
-   built-in, named, list, and function type references
-   type constraints
-   DRG elements
-   decisions and input data
-   business knowledge models
-   knowledge sources
-   decision services
-   decision tables, clauses, rules, hit policies, aggregations, and
    orientation
-   FEEL source text
-   boxed-expression source structures
-   invocation source structures
-   provisional FEEL AST structures

## 5.4 Module: dmn-frontend-xml \[IMPLEMENTED\]

### Responsibility

Reads DMN XML with VTD-XML and constructs the protobuf Semantic Model.

Dependencies:

``` text
dmn-frontend-xml
    |
    +-- dmn-protobuf
    +-- vtd-xml 2.13.4
    +-- slf4j-api
```

The active package root is:

``` text
io.finmsg.dmn.frontend.xml
```

The module name is intentionally `dmn-frontend-xml`; references to a
module named `dmn-xml` elsewhere in older plans should be interpreted as
the same architectural frontend stage.

## 5.5 Current Dependency Direction

``` text
dmn-frontend-xml
        |
        v
dmn-protobuf
```

`dmn-protobuf` has no dependency on XML or VTD-XML.

This satisfies the current stage boundary:

``` text
XML frontend -> Semantic Model
```

## 5.6 Target Modules

The following modules remain planned and must not be described as
implemented:

``` text
dmn-feel-parser
dmn-semantic
dmn-graph
dmn-optimizer
dmn-runtime-ir
dmn-runtime
dmn-codegen-api
dmn-codegen-java
dmn-codegen-rust
dmn-codegen-go
dmn-codegen-spark
dmn-compiler-api
dmn-compiler-cli
dmn-maven-plugin
```

A separate `dmn-model` module is not currently required because the
generated classes from `dmn-protobuf` are the canonical Semantic Model,
as established by ADR-0002. Such a module should only be introduced by a
new ADR if generated protobuf classes cease to be the canonical
representation.

## 5.7 Incremental Module Roadmap

Recommended next module sequence:

``` text
1. dmn-protobuf                 implemented
2. dmn-frontend-xml             implemented
3. dmn-feel-parser              next compiler stage
4. dmn-semantic                 reference and type analysis
5. dmn-runtime-ir               executable lowering contract
6. dmn-codegen-java             first production backend
7. dmn-runtime / compiler API   execution and integration
8. optimizer and other backends after correctness baseline
```

## 5.8 Module Design Rules

1.  One architectural responsibility per module.
2.  Dependencies flow toward lower-level contracts.
3.  Protobuf schemas contain no compiler logic.
4.  XML classes never enter runtime artifacts.
5.  New modules are introduced only when their boundary is testable.
6.  The current two-module implementation remains valid while later
    phases are developed.

------------------------------------------------------------------------

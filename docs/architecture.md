# Architecture

<!-- generated-toc:start -->
## Table of contents

- [Implemented architecture](#contents-section-1)
- [Stage boundaries](#contents-section-2)
  - [XML frontend](#contents-section-3)
  - [Semantic model](#contents-section-4)
  - [FEEL parser](#contents-section-5)
  - [Semantic analysis](#contents-section-6)
  - [Future stages](#contents-section-7)
- [Core rules](#contents-section-8)
<!-- generated-toc:end -->

The normative, detailed design is maintained in the
[architecture specification](architecture/architecture-spec.md). This page is the
short implementation-oriented overview. Accepted decisions and their rationale are indexed in
the [ADR guide](architecture/adr/generall-adr.md), and project terminology is defined by the
[glossary](glossary.md).

<a id="contents-section-1"></a>
## Implemented architecture

The toolkit is a staged compiler rather than an XML-centric interpreter.

```text
DMN XML Source Graph
  → Model Resolver & DmnCompiler Facade
  → XML Frontend (VTD-XML)
  → immutable protobuf Semantic Model with FEEL text
  → FEEL Parser pass
  → copied protobuf model with FEEL AST
  → Whole-Model-Set Semantic Analysis
  → validated linked model set, bindings, order, and diagnostics
  → structural Runtime IR lowering
  ├── Process-Local Interpreter (dmn-runtime)
  └── Java Code Generator (dmn-generator-java)
```

Nine Maven modules implement this pipeline through cross-model typed semantic analysis,
deterministic Runtime IR lowering, process-local interpretation, Java source generation, TCK conformance testing, and compiler-facing source resolution.

<a id="contents-section-2"></a>
## Stage boundaries

<a id="contents-section-3"></a>
### XML frontend

Maps the current protobuf-supported DMN subset to and from XML. It preserves FEEL text, scoped QName type namespaces, imports, node documentation/extensions, and optional source locations while performing no FEEL parsing or semantic resolution.

<a id="contents-section-4"></a>
### Semantic model

Uses generated protobuf messages as the canonical representation. Replaceable nodes use text/parsed `oneof` branches.

<a id="contents-section-5"></a>
### FEEL parser

Uses ANTLR4 and `FeelAstBuilder` to create protobuf AST nodes. `DmnFeelParser` traverses the model depth-first with generated getters, keeps the input immutable, and provides model-path diagnostics.

<a id="contents-section-6"></a>
### Semantic analysis

The implemented pipeline creates requirement-aware scopes, resolves local and imported symbols/types, validates and infers FEEL types, validates dependencies, and exposes deterministic bindings and compilation order.

### Compiler facade and model resolution (`dmn-compiler`)

Owns `DmnCompiler`, `DmnCompiledModel`, and resolver-independent source loading (`DmnModelResolver`, `FilesystemDmnModelResolver`, `ClasspathDmnModelResolver`, `InMemoryDmnModelResolver`). Aggregates phase-aware diagnostics and performs whole-model-set compilation.

### Runtime IR (`dmn-runtime-ir`) & Interpreter (`dmn-runtime`)

Lowers validated semantic models into compact, immutable Runtime IR with integer references and deterministic slot layouts. `DmnInterpreter` evaluates Runtime IR models in-memory without XML, ANTLR, or protobuf dependencies.

### Java code generator (`dmn-generator-java`)

Transforms Runtime IR into high-performance, direct Java source code, enabling zero-reflection execution without XML or FEEL dependencies at runtime.

### TCK runner (`dmn-tck-runner`)

Runs OMG DMN Technology Compatibility Kit (TCK) test cases against the toolkit to assert spec conformance and parity between interpreter and generated execution.

<a id="contents-section-7"></a>
### Future stages

Further optimizer passes (constant folding, dead decision elimination), multi-language code generation (Rust, Go, C++), gRPC dynamic adapters, and JMH performance benchmark suites remain active future work.

<a id="contents-section-8"></a>
## Core rules

1. XML-specific code remains in `dmn-frontend-xml`.
2. Compiler passes consume generated protobuf contracts.
3. Every pass treats its input as immutable.
4. Traversal uses generated getters instead of protobuf reflection.
5. Diagnostics identify the semantic-model path and source location where available.
6. Runtime and generated code must not depend on XML or ANTLR.

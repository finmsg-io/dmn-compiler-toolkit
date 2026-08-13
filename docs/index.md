# DMN Compiler Toolkit

<!-- generated-toc:start -->
## Table of contents

- [Current implementation](#contents-section-1)
- [Design goals](#contents-section-2)
<!-- generated-toc:end -->

The DMN Compiler Toolkit is a performance-oriented compiler infrastructure for Decision Model and Notation (DMN).

It parses DMN XML once, creates an immutable protobuf semantic model, parses FEEL into protobuf AST nodes, performs semantic analysis, and lowers the validated model into an execution-oriented Runtime IR.

Start with the [architecture overview](architecture.md), explore our [toolkit assets and competitive differentiators](architecture/toolkit-assets-and-differentiators.md), consult the normative
[architecture specification](architecture/architecture-spec.md) for the intended system, the
[ADR index](architecture/adr/generall-adr.md) for design rationale, and the
[glossary](glossary.md) for shared terminology.

<a id="contents-section-1"></a>
## Current implementation

The repository contains an active multi-module Maven reactor. The canonical inventory and
module-specific limitations are maintained on the [modules page](modules.md); the
[capability maturity page](capabilities.md) distinguishes established, incubating, and planned
functionality. This page intentionally does not duplicate that inventory.

The implemented pipeline is:

```text
DMN XML Source Graph
   │
   ▼
DmnCompiler Facade / Model Resolver
   │
   ▼
Definitions with FEEL text (DmnXmlReader)
   │
   ▼
Definitions with parsed FEEL AST (DmnFeelParser)
   │
   ▼
Semantic-analysis result and diagnostics (DmnSemanticAnalyzer)
   │
   ▼
Immutable Runtime IR (RuntimeIrLowerer)
   │
   ▼
Static Optimizer Pass (dmn-optimizer)
   │
   ├────────────────────────┬────────────────────────┐
   ▼                        ▼                        ▼
Process-local Interpreter   Java Generator           JMH Benchmarks
(dmn-runtime)               (dmn-generator-java)     (dmn-benchmarks)
```

Cross-model compilation, Runtime IR lowering, optimization, process-local interpretation, Java
generation, gRPC adapters, Spark SQL generation, multi-file model ingestion, the data-quality
corpus, TCK execution, and JMH benchmarks are implemented. Implementation does not by itself imply
public-production maturity or identical backend coverage; consult the
[capability maturity page](capabilities.md), [TCK conformance record](tck-conformance.md), and
[development plan](development-plan.md) for the verified boundaries and current work.

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

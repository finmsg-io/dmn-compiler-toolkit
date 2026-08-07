<a id="contents-section-1"></a>
# Audits

<!-- generated-toc:start -->
## Table of contents

- [Audits](#contents-section-1)
<!-- generated-toc:end -->

Audits are dated snapshots of implementation evidence, risks, and recommendations.
They are retained for historical context and are not active work queues. Current
actions belong in the [module TODOs](../todos/index.md) or the
[living development plan](../development-plan.md).

| Audit | Scope |
| --- | --- |
| [Protobuf assessment](assessment-implementation-dmn-protobuf.md) | Semantic protobuf contracts |
| [XML frontend assessment](assessment-implementation-dmn-frontend-xml.md) | Production-readiness findings |
| [XML frontend completeness](dmn-frontend-xml-completeness.md) | Supported XML subset and completion evidence |
| [FEEL parser assessment](assessment-implementation-dmn-feel-parser.md) | FEEL grammar, AST, model pass, and build risks |
| [Semantic-analysis assessment](assessment-implementation-dmn-semantic-analysis.md) | Semantic compiler baseline and risks |
| [Runtime IR assessment](assessment-implementation-dmn-runtime-ir.md) | Lowering contract and optimization boundary |
| [Runtime assessment](assessment-implementation-dmn-runtime.md) | Interpreter correctness and readiness |
| [Compiler assessment](assessment-implementation-dmn-compiler.md) | Source resolution and compiler-facade readiness |
| [Java Generator assessment](assessment-implementation-dmn-generator-java.md) | AOT Java code generation & spec conformance |
| [TCK Runner assessment](assessment-implementation-dmn-tck-runner.md) | OMG DMN 1.5 TCK conformance & dual-engine value parity |
| [Benchmarks assessment](assessment-implementation-dmn-benchmarks.md) | JMH microbenchmarks & DataFaker reference workloads |
| [Optimizer assessment](assessment-implementation-dmn-optimizer.md) | Constant folding & expression simplification pass |
| [Data Quality Corpus assessment](assessment-implementation-data-quality-corpus.md) | Production Data Quality DMN decision models & validation |
| [Module architecture assessment](assessment-module-architecture.md) | Responsibilities, dependencies, and cross-module boundaries |
| [Protobuf architecture assessment](assessment-architecture-dmn-protobuf.md) | Serialized module architecture opinion |
| [XML frontend architecture assessment](assessment-architecture-dmn-frontend-xml.md) | Serialized module architecture opinion |
| [FEEL parser architecture assessment](assessment-architecture-dmn-feel-parser.md) | Serialized module architecture opinion |
| [Semantic-analysis architecture assessment](assessment-architecture-dmn-semantic-analysis.md) | Serialized module architecture opinion |
| [Runtime IR architecture assessment](assessment-architecture-dmn-runtime-ir.md) | Serialized module architecture opinion |
| [Runtime architecture assessment](assessment-architecture-dmn-runtime.md) | Serialized module architecture opinion |
| [Compiler architecture assessment](assessment-architecture-dmn-compiler.md) | Serialized module architecture opinion |
| [Java Generator architecture assessment](assessment-architecture-dmn-generator-java.md) | AOT Java Generator boundary assessment |
| [TCK Runner architecture assessment](assessment-architecture-dmn-tck-runner.md) | TCK Runner boundary assessment |
| [Benchmarks architecture assessment](assessment-architecture-dmn-benchmarks.md) | JMH Microbenchmarks boundary assessment |
| [Optimizer architecture assessment](assessment-architecture-dmn-optimizer.md) | Optimizer pass boundary assessment |
| [Data Quality Corpus architecture assessment](assessment-architecture-data-quality-corpus.md) | Data Quality Corpus boundary assessment |

Audit statements may become stale as implementation advances. Each audit retains
its assessment date; consult current TODOs and executable tests before acting on it.

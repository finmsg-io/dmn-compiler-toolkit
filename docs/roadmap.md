# Roadmap

<!-- generated-toc:start -->
## Table of contents

- [Completed foundation](#contents-section-1)
- [XML frontend completion gate](#contents-section-2)
- [v1.0.0 MVP: TCK 100%, Core Optimizers, and Benchmarks](#contents-section-3)
- [v1.1.0: Incubating Module Graduation & Cloud Data Engine Ecosystem](#contents-section-4)
- [Decision Authoring DSL and Canonical Model (IDEA-004)](#contents-section-5)
- [DMN XML Synthesis & Export from Optimized IR (DMN 1.5/1.6)](#contents-section-6)
<!-- generated-toc:end -->


<a id="contents-section-1"></a>
## Completed foundation

- [x] Protobuf semantic model
- [x] FEEL text and parsed schemas without reverse dependency
- [x] Replaceable FEEL, expression, boxed-expression, unary-test, and type-constraint nodes
- [x] VTD-XML DMN reader
- [x] Initial DMN XML writer infrastructure and writer tests
- [x] Namespace-aware DMN element dispatch and scoped namespace resolution
- [x] Complete reader/writer symmetry for the current protobuf-supported XML subset
- [x] DMN version and root namespace round trips, including prefixed-DMN output
- [x] Imports, decision services, invocations, decision tables, and boxed expressions
- [x] Item-definition components and constraints
- [x] Documentation and one-level structured extension preservation
- [x] Structured read results, input limits, and malformed/unsupported XML diagnostics
- [x] Opt-in source-location capture
- [x] ANTLR4 FEEL grammar and generated parser
- [x] Protobuf FEEL AST builder
- [x] Depth-first `DmnFeelParser` pass
- [x] Model-aware, multi-error FEEL diagnostics
- [x] Traffic Violation XML-to-FEEL integration test
- [x] `dmn-semantic-analysis` module
- [x] First FEEL name/property-resolution pass
- [x] Traffic Violation semantic-analysis test
- [x] Unified semantic-analysis pipeline
- [x] Named-type resolution and declared-type validation
- [x] FEEL expression and boxed-expression type inference
- [x] Operator and built-in function validation
- [x] Decision-table, BKM, item-definition, and decision-service validation
- [x] DRG dependency analysis, cycle detection, and deterministic compilation order
- [x] Exposed resolved symbol and named-type bindings
- [x] Namespace-indexed cross-model import and reference linking
- [x] Cross-model dependency ordering
- [x] Structured-list projection and structured filter typing
- [x] Range, between, and `in` unary-test validation
- [x] FEEL temporal and duration arithmetic
- [x] `instance of` named-type validation
- [x] Model-indexed decision-table-reference typing and diagnostics
- [x] Legacy single-binding loop compatibility
- [x] Executable, namespace-free Runtime IR lowering for expressions, boxed logic, and linked models
- [x] Typed constant canonicalization and stable built-in operation IDs
- [x] Runtime IR interpreter with dependency scheduling, lexical frames, contexts, functions, and decision tables
- [x] Immutable compiler facade (`DmnCompiler`) and phase-aware diagnostic aggregation
- [x] Resolver-independent model loader boundary for imports (`DmnModelResolver`)
- [x] High-performance Java source code generator (`dmn-generator-java`)
- [x] DMN Technology Compatibility Kit (TCK) runner (`dmn-tck-runner`)
- [x] JMH performance microbenchmarks & DataFaker payload generators (`dmn-benchmarks`)

<a id="contents-section-2"></a>
## XML frontend completion gate

- [x] Preserve QName `typeRef` namespaces during XML read/write
- [x] Diagnose unsupported DMN-namespace content consistently across readers
- [x] Add the multi-version, conformance, namespace-shadowing, and hostile-input matrix
- [x] Run and stabilize the complete reactor
- [x] Publish the supported-subset contract and explicit model-extension boundary

<a id="contents-section-3"></a>
## v1.0.0 MVP: TCK 100%, Core Optimizers, and Benchmarks

- [x] Full OMG DMN 1.5 TCK CL2/CL3 conformance for `DmnInterpreter` and `dmn-generator-java` (3,391/3,391 cases, 6,782/6,782 backend outcomes)
- [x] Zero-dependency reflection-free AOT Java Generator (`dmn-generator-java`)
- [x] Static optimization pipeline (`dmn-optimizer`): constant folding, algebraic simplification, range/between evaluation optimization
- [x] Differential parity test harness verifying execution equivalence between optimized IR and baseline runtime
- [x] JMH performance benchmark suite (`dmn-benchmarks`) with multi-threaded throughput/latency metrics
- [x] Multi-file DMN sample model suites & Java streaming ingestion/resolution API (`dmn-models`, `DmnStreamBundle`)
- [x] General Availability (GA) publication on Maven Central under `io.finmsg.dmn`

<a id="contents-section-4"></a>
## v1.1.0: Incubating Module Graduation & Cloud Data Engine Ecosystem

- [ ] Promote pure Spark / Databricks SQL CTE generator (`dmn-generator-sparksql`, zero UDFs, CTE query graphs) to Tier-1 Core Reactor
- [ ] Finalize strongly-typed Protobuf schema and gRPC service contract generator (`dmn-grpc`, `DmnTypedGrpcGenerator`)
- [ ] Add Spark SQL batch and streaming integration test harness against complex multi-table DMN models
- [ ] Benchmark Spark SQL generated CTE queries against equivalent native PySpark / Scala Spark implementations

<a id="contents-section-5"></a>
## Decision Authoring DSL and Canonical Model (IDEA-004)

- [ ] Design and implement concise textual `.decision` DSL grammar for authoring decision models without DMN XML boilerplate
- [ ] Establish authoring-preserving Canonical Decision Model intermediate representation
- [ ] Bi-directional lossless compilation: `.decision` DSL $\rightarrow$ Canonical Model $\rightarrow$ Standards-Compliant DMN XML
- [ ] Direct compilation: `.decision` DSL $\rightarrow$ Runtime IR $\rightarrow$ Interpreters & Code Generators
- [ ] Embedded policy tests & invariant examples within DSL specifications
- [ ] Standalone Language Server Protocol (LSP) engine for IDE syntax highlighting, diagnostics, and auto-completion
- [ ] Standalone compiler CLI binary (`dmnc`)

<a id="contents-section-6"></a>
## DMN XML Synthesis & Export from Optimized IR (DMN 1.5/1.6)

- [ ] Reverse lowering and decompiler pass: Runtime IR / Optimized Model $\rightarrow$ Standards-Compliant DMN 1.5 / 1.6 XML
- [ ] Synthesize normalized decision tables, boxed expressions, and FEEL logic from folded / simplified IR trees
- [ ] Retain and reconstruct semantic metadata (item definitions, input data, decision topology) into standard DMN XML
- [ ] Deterministic round-trip interchange with third-party visual DMN modelers and BPMN/DMN workflow engines

# Roadmap

<!-- generated-toc:start -->
## Table of contents

- [Completed foundation](#contents-section-1)
- [XML frontend completion gate](#contents-section-2)
- [Next: compiler glue and Runtime IR](#contents-section-3)
- [Later stages](#contents-section-4)
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

<a id="contents-section-2"></a>
## XML frontend completion gate

- [x] Preserve QName `typeRef` namespaces during XML read/write
- [x] Diagnose unsupported DMN-namespace content consistently across readers
- [x] Add the multi-version, conformance, namespace-shadowing, and hostile-input matrix
- [x] Run and stabilize the complete reactor
- [x] Publish the supported-subset contract and explicit model-extension boundary

<a id="contents-section-3"></a>
## Next: compiler glue and Runtime IR

- [ ] Add a compiler facade that aggregates phase diagnostics
- [ ] Add a model resolver/loading boundary for imports
- [x] Stabilize the semantic result contract consumed by Runtime IR

<a id="contents-section-4"></a>
## Later stages

- [x] Structural Runtime IR module and deterministic graph/type lowering
- [x] FEEL expression and decision-table lowering
- [x] Model-set Runtime IR lowering
- [ ] Constant folding and expression simplification
- [ ] Java code generation
- [x] Runtime activation (process-local interpreter baseline)
- [ ] External JAVA/PMML function host bindings and conformance expansion
- [ ] Public compiler API and CLI
- [ ] JMH performance benchmarks

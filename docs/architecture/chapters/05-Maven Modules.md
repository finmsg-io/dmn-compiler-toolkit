# Chapter 5 — Maven Modules [IMPLEMENTATION-ALIGNED]

<!-- generated-toc:start -->
## Table of contents

- [5.1 Current project structure](#contents-section-1)
- [5.2 dmn-protobuf](#contents-section-2)
- [5.3 dmn-frontend-xml](#contents-section-3)
- [5.4 dmn-feel-parser](#contents-section-4)
- [5.5 dmn-semantic-analysis](#contents-section-5)
- [5.6 dmn-runtime-ir](#contents-section-6)
- [5.7 dmn-runtime](#contents-section-7)
- [5.8 dmn-compiler](#contents-section-8)
- [5.9 Target modules](#contents-section-9)
- [5.10 Module rules](#contents-section-10)
<!-- generated-toc:end -->


<a id="contents-section-1"></a>
## 5.1 Current project structure

```text
dmn-compiler-toolkit
├── pom.xml
├── dmn-protobuf
├── dmn-frontend-xml
├── dmn-feel-parser
├── dmn-semantic-analysis
├── dmn-runtime-ir
├── dmn-runtime
└── dmn-compiler
```

All modules use:

```text
groupId: io.finmsg.dmn
version: 1.0.0-SNAPSHOT
Java:    25
```

<a id="contents-section-2"></a>
## 5.2 `dmn-protobuf`

Defines generated contracts in `io.finmsg.dmn.model`.

```text
common.proto
core.proto
types.proto
feel_text.proto
feel_parsed.proto
feel.proto
decision_table.proto
drg.proto
model.proto
```

The text schema does not depend on the parsed schema. `feel.proto` composes both through replaceable wrapper messages.

<a id="contents-section-3"></a>
## 5.3 `dmn-frontend-xml`

Production dependencies:

```text
dmn-protobuf
vtd-xml
slf4j-api
```

Responsibilities:

- DMN version detection
- XML cursor abstraction
- element-specific readers
- semantic protobuf construction
- semantic DMN XML writing for the current protobuf-supported subset
- structured read diagnostics, bounded input, and opt-in source locations
- namespace/version preservation and prefixed-DMN output

<a id="contents-section-4"></a>
## 5.4 `dmn-feel-parser`

Production dependencies:

```text
dmn-protobuf
antlr4-runtime
```

Responsibilities:

- FEEL grammar
- parse-tree creation
- protobuf AST construction
- depth-first semantic-model parsing pass
- strict and diagnostic parsing APIs

`dmn-frontend-xml` is test-scoped for the Traffic Violation integration test.

<a id="contents-section-5"></a>
## 5.5 `dmn-semantic-analysis`

Production dependency:

```text
dmn-protobuf
```

Responsibilities currently implemented:

- declaration collection
- requirement-aware scopes
- FEEL name and property resolution
- semantic diagnostics
- named-type resolution and declared-type validation
- FEEL and boxed-expression type inference
- operator and built-in function validation
- decision-table, BKM, item-definition, and decision-service validation
- DRG dependency validation, cycle detection, and deterministic compilation order
- unified pass orchestration
- persisted/exposed symbol and named-type bindings
- namespace-indexed cross-model import/reference linking and dependency ordering

`dmn-feel-parser` and `dmn-frontend-xml` are test-scoped dependencies only.

<a id="contents-section-6"></a>
## 5.6 `dmn-runtime-ir`

Production dependency:

```text
dmn-semantic-analysis
```

Implemented baseline responsibilities:

- immutable, protobuf-free runtime contracts
- deterministic integer node IDs and value slots
- structural runtime-type lowering
- integer dependency references and evaluation order
- rejection of unsuccessful semantic-analysis results

Typed constants, bound global and lexical-local references, recursive unary/binary operators,
conditional expressions, list and context literals, named function calls, path/property access,
ranges, filters, comparison/test expressions, iteration, quantification, function definitions,
static/dynamic invocation, descendant access, and boxed decision tables are implemented. All
protobuf FEEL AST variants now lower. Decision logic also covers recursive boxed
contexts/relations/lists/functions and DMN invocations. BKM slots contain executable typed
function bodies with preserved function kind. Decisions and nested functions persist isolated
lexical frame sizes, and closure captures carry lexical depth. Declared and expression-derived
references drive deterministic runtime scheduling. Context types retain indexed field layouts,
and statically known path and descendant access is resolved to those indices. Linked semantic
models lower into one namespace-free slot space. Typed constant pools and stable built-in IDs are
provided by a separate optimization pass.

<a id="contents-section-7"></a>
## 5.7 `dmn-runtime`

Production dependency: `dmn-runtime-ir`.

The process-local interpreter executes aggregate evaluation order, global value slots, lexical
frames and closures, core FEEL expressions, contexts and relations, iterations and quantified
expressions, built-ins, invocations, unary tests, and decision tables. External JAVA/PMML
functions require a future host-binding boundary.

<a id="contents-section-8"></a>
## 5.8 `dmn-compiler`

The compiler module owns orchestration and resolver-independent model-source contracts.
Its source layer includes root-confined filesystem, classpath, and in-memory resolvers. The
transitive loader uses the XML frontend to parse location-addressable imports, caches loaded
models by stable source identity, enforces graph bounds, and produces an immutable graph with
deterministically ordered models and import edges. Missing, ambiguous, conflicting-identity,
duplicate-model-identity, and cyclic structures are retained as stable ordered diagnostics;
the result exposes its validity while preserving the safely loaded partial graph. Shared compiler
diagnostics carry type-safe severity and phase, stable string codes, source and optional model
identity, optional import context, related source identities, and canonical cycle paths. An
explicit comparator makes ordering reproducible, and only error severity invalidates a result.
Later facade slices will adapt lower-stage diagnostics and add explicit dependencies on semantic
analysis and Runtime IR as orchestration expands; lower-level modules do not depend on compiler
diagnostic types.

The whole-model-set semantic boundary depends on the FEEL parser and semantic-analysis modules in
that direction only. It first parses every loaded source, stops the set before semantics if FEEL
errors exist, and otherwise analyzes every model against the same complete repository. Its
immutable result is ordered by source identity and retains model identity, per-model compilation
order, bindings, import edges, and normalized compiler diagnostics. The final compiler facade and
Runtime IR orchestration remain later slices.

The public `DmnCompiler` facade now composes source loading, whole-set semantics, Runtime IR
lowering, and optimization. `DmnCompilerOptions` initially carries deterministic graph bounds.
`DmnCompilationResult` retains immutable loaded and semantic stage results, authoritative ordered
diagnostics, and optional optimized Runtime IR. Error diagnostics prevent later phases; expected
lowering failures are adapted at the compiler boundary. This completes the P1 orchestration layer
without introducing reverse dependencies from any lower-level module.

<a id="contents-section-9"></a>
## 5.9 Target modules

```text
dmn-optimizer
dmn-codegen-java
dmn-benchmarks
```

<a id="contents-section-10"></a>
## 5.10 Module rules

1. One architectural responsibility per module.
2. Production dependencies point toward lower-level contracts.
3. Protobuf schemas contain no compiler logic.
4. XML and ANTLR types never cross into later compiler or runtime APIs.
5. Cross-module integration dependencies may remain test-scoped.

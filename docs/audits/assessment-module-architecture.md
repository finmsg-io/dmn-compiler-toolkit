# Maven module architecture assessment

<!-- generated-toc:start -->
## Table of contents

- [Executive conclusion](#contents-section-1)
- [Current dependency architecture](#contents-section-2)
- [Module assessments](#contents-section-3)
  - [dmn-protobuf](#contents-section-4)
  - [dmn-frontend-xml](#contents-section-5)
  - [dmn-feel-parser](#contents-section-6)
  - [dmn-semantic-analysis](#contents-section-7)
  - [dmn-runtime-ir](#contents-section-8)
  - [dmn-runtime](#contents-section-9)
  - [dmn-compiler](#contents-section-10)
- [Cross-module findings](#contents-section-11)
  - [P1 — Normalize diagnostics without reversing dependencies](#contents-section-12)
  - [P1 — Establish a dependency-neutral FEEL built-in catalog](#contents-section-13)
  - [P1 — Keep compiler orchestration above stage contracts](#contents-section-14)
  - [P2 — Protect schema evolution across repeated traversals](#contents-section-15)
  - [P2 — Use one real corpus across stage boundaries](#contents-section-16)
- [Recommended target](#contents-section-17)
<!-- generated-toc:end -->

Assessment date: 2026-08-07

<a id="contents-section-1"></a>
## Executive conclusion

The ten-module architecture is directionally sound and complete. Production dependencies point downward,
runtime execution is isolated from XML/ANTLR/protobuf, generated Java runs without runtime framework dependencies, and integration-only dependencies are test-scoped. There are no production dependency cycles.

<a id="contents-section-2"></a>
## Current dependency architecture

```text
dmn-tck-runner
    -> dmn-compiler
    -> dmn-generator-java
    -> dmn-runtime
         -> dmn-runtime-ir
              -> dmn-semantic-analysis
                   -> dmn-protobuf

dmn-benchmarks
    -> dmn-compiler
    -> dmn-generator-java
    -> dmn-runtime
    -> net.datafaker:datafaker
    -> org.openjdk.jmh:jmh-core

dmn-feel-parser -> dmn-protobuf
dmn-frontend-xml -> dmn-protobuf
```

`dmn-feel-parser` and `dmn-frontend-xml` are test dependencies of later compiler stages where
end-to-end fixtures require them. This preserves production boundaries while allowing integration
evidence. The compiler module is expected to become the top-level orchestrator and may legitimately
depend on the frontend, parser, semantic analysis, and Runtime IR modules.

<a id="contents-section-3"></a>
## Module assessments

<a id="contents-section-4"></a>
### `dmn-protobuf`

**Fit:** correct dependency-base placement. It provides parser-independent semantic and FEEL AST
contracts without compiler logic.

**Risk:** protobuf compatibility, generated mutable builders, and broad schema changes affect nearly
every compiler stage. Keep runtime IDs and execution policy out of this module, add schema-breaking
checks, and document ownership/preservation rules.

<a id="contents-section-5"></a>
### `dmn-frontend-xml`

**Fit:** correct adapter placement above protobuf. VTD-XML and namespace/version policy are contained,
and no later production module depends on XML classes.

**Risk:** frontend diagnostics currently use protobuf diagnostics while other stages define Java
records. Preserve the XML/model boundary and adapt diagnostics at orchestration rather than making
the frontend depend on compiler APIs.

<a id="contents-section-6"></a>
### `dmn-feel-parser`

**Fit:** correct independent transformation from FEEL text to protobuf AST. XML is test-scoped and
ANTLR types do not cross into semantic analysis.

**Risk:** checked-in generated parser drift and parallel traversal logic. Add regeneration checks and
exhaustiveness tests; do not merge FEEL parsing into either XML or semantic analysis.

<a id="contents-section-7"></a>
### `dmn-semantic-analysis`

**Fit:** correct compiler middle-end boundary. Production depends only on protobuf, while bindings,
types, validation, and deterministic dependency order are exposed to lowering.

**Risk:** built-in metadata and diagnostics need consumers in sibling/downstream modules. Avoid
making runtime depend on semantic analysis; extract genuinely shared metadata into a lower neutral
contract or adapt it during Runtime IR lowering.

<a id="contents-section-8"></a>
### `dmn-runtime-ir`

**Fit:** correct boundary between protobuf-rich compilation and namespace-free execution. Runtime
depends only on IR; IR owns lowering and process-local execution contracts.

**Risk:** lowering currently depends on semantic result shapes, which is appropriate, but IR must not
become a dumping ground for XML/source concerns or durable serialization assumptions. Keep source
metadata compiler-side unless execution errors require a compact mapped identity.

<a id="contents-section-9"></a>
### `dmn-runtime`

**Fit:** correct leaf execution module with one production dependency on Runtime IR. It contains no
XML, ANTLR, semantic protobuf, or reflection dependency.

**Risk:** public slot-oriented invocation and hand-written FEEL semantics are not stable application
APIs. Keep the interpreter as a backend; expose names, model selection, conversions, and structured
errors through a compiled-model API above it.

<a id="contents-section-10"></a>
### `dmn-compiler`

**Fit:** correct future top-level orchestration module. Its current source/resolver contracts are
dependency-free and policy-oriented.

**Risk:** adding all pipeline dependencies can mix stable public contracts with orchestration
implementation. Use package/API boundaries first; split a separate API artifact only if consumers
need contracts without compiler implementation dependencies.

<a id="contents-section-11"></a>
## Cross-module findings

<a id="contents-section-12"></a>
### P1 — Normalize diagnostics without reversing dependencies

XML, FEEL, semantics, resolution, lowering, and runtime use different diagnostic/error types. Define
a compiler-level normalized diagnostic and stage adapters. If lower stages eventually need the same
contract independently, extract a tiny dependency-neutral diagnostics module; do not make them
depend upward on `dmn-compiler`.

<a id="contents-section-13"></a>
### P1 — Establish a dependency-neutral FEEL built-in catalog

Semantic validation and runtime dispatch need one versioned definition of names, stable operation
IDs, arity, overloads, and null rules. The catalog cannot live only in semantic analysis because
runtime must not depend on that module. Prefer a small neutral contract below both semantic analysis
and Runtime IR, or lower all required catalog data into IR with automated parity tests.

<a id="contents-section-14"></a>
### P1 — Keep compiler orchestration above stage contracts

`DmnCompiler` should depend on all compile-time stages but expose compiler-owned immutable results,
diagnostics, source identities, and compiled-model metadata. Public callers should not assemble
protobuf builders, semantic side tables, or integer Runtime IR slots.

<a id="contents-section-15"></a>
### P2 — Protect schema evolution across repeated traversals

FEEL/model variants are traversed in parser, semantic analysis, lowering, and sometimes runtime.
Use exhaustive switches where possible and add a schema-coverage test that forces every new variant
to acquire explicit handling at each stage.

<a id="contents-section-16"></a>
### P2 — Use one real corpus across stage boundaries

Unit tests are strong inside several modules, but linked models are mostly programmatic. A shared
version-controlled multi-file corpus should drive XML, FEEL, semantics, IR, interpreter, generated
Java, and transport parity without introducing reverse production dependencies.

<a id="contents-section-17"></a>
## Recommended target

```text
DMN sources + resolver
        -> dmn-compiler facade and normalized diagnostics
             -> XML frontend
             -> FEEL parser
             -> semantic model-set analysis
             -> Runtime IR lowering + optimization
                  -> interpreter
                  -> generated Java
```

Retain the current modules. Add a new shared-contract module only when diagnostics or built-in
metadata demonstrably cannot be adapted/lowered without duplication. No existing module requires a
merge or rewrite.


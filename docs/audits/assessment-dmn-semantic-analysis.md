
# `dmn-semantic-analysis` assessment

<!-- generated-toc:start -->
## Table of contents

- [Executive conclusion](#contents-section-1)
- [Verified strengths](#contents-section-2)
- [Findings and recommended actions](#contents-section-3)
  - [P1 — Strengthen diagnostic identity and severity](#contents-section-4)
  - [P1 — Add model-set analysis as a first-class result](#contents-section-5)
  - [P2 — Decompose the three monolithic analyzers](#contents-section-6)
  - [P2 — Expand and version the FEEL function registry](#contents-section-7)
  - [P2 — Add negative linked-model and conformance fixtures](#contents-section-8)
  - [P3 — Clarify mutable protobuf ownership](#contents-section-9)
- [Recommended sequence](#contents-section-10)
<!-- generated-toc:end -->

Assessment date: 2026-08-02

<a id="contents-section-1"></a>
## Executive conclusion

`dmn-semantic-analysis` is a compiler-grade semantic baseline for the currently modeled DMN and
FEEL subset. It performs name and property resolution, type inference and validation, dependency
analysis, cross-model import/reference linking, and exposes bindings required by Runtime IR.

The module is ready to remain the semantic contract for Runtime IR lowering. Its main risks are
not missing glue but diagnostic identity, incomplete FEEL-library/conformance breadth, and three
large analyzer classes that duplicate traversal responsibilities.

| Dimension | Assessment |
| --- | --- |
| Current modeled DMN/FEEL semantics | strong baseline |
| Persisted symbol/type information | implemented |
| Cross-model linking | implemented |
| Dependency ordering and cycles | implemented |
| Diagnostics | functional, contract needs strengthening |
| Test maturity | good: 67 passing tests |
| Maintainability | moderate risk from large analyzers |
| Full FEEL/DMN conformance | incomplete |

<a id="contents-section-2"></a>
## Verified strengths

- A public `DmnSemanticPipeline` orders reference, type, and dependency passes.
- Successful symbol resolutions are persisted as `DmnSymbolBinding`, including declaration path,
  symbol identity, kind, type, and target namespace.
- `DmnModelRepository` links imports by namespace/name and diagnoses missing, duplicate, unknown,
  and ambiguous imports.
- Cross-model DRG and named-type references participate in dependency and type analysis.
- FEEL analysis covers all currently parsed expression forms, including structured paths,
  filters, ranges, temporal arithmetic, iteration, quantification, functions, and invocation.
- DMN analysis covers decisions, BKMs, decision services, tables, boxed expressions, item types,
  declared variables, requirements, cycles, and deterministic compilation order.
- Production code depends only on `dmn-protobuf`; XML and FEEL parsing are test-scoped.
- The suite has 67 passing tests: 27 FEEL type, 15 DMN type, 13 symbol analysis, six pipeline,
  four dependency, and two XML-to-semantic integration tests.

<a id="contents-section-3"></a>
## Findings and recommended actions

<a id="contents-section-4"></a>
### P1 — Strengthen diagnostic identity and severity

`DmnSemanticDiagnostic` represents every finding as an error-like tuple without severity or model
identity. `isSuccess()` consequently means `diagnostics.isEmpty()`. The pipeline deduplicates only
by `(code, path)`, which can discard distinct diagnostics at one path and can collide when linked
models use equivalent structural paths.

Recommendation: add severity and source-model identity/namespace, define success as absence of
error/fatal diagnostics, and deduplicate by a stable identity including model, code, path, and
relevant message/arguments. Preserve deterministic ordering.

<a id="contents-section-5"></a>
### P1 — Add model-set analysis as a first-class result

The multi-model overload analyzes one root against candidate models. Consumers that lower a
linked model set still assemble individual pipeline results externally. That makes consistent
whole-graph diagnostics and ordering a compiler-facade responsibility without a dedicated
semantic contract.

Recommendation: introduce a `DmnSemanticModelSetResult` containing analyzed models, namespace
identity, aggregate diagnostics, cross-model compilation order, and bindings. Keep the current
single-root API as a convenience wrapper.

<a id="contents-section-6"></a>
### P2 — Decompose the three monolithic analyzers

`DmnSemanticAnalyzer`, `FeelTypeAnalyzer`, and `DmnTypeAnalyzer` are approximately 1,159, 1,011,
and 920 lines. They repeat recursive expression/boxed-expression traversal and make adding a new
AST variant require coordinated edits in several large switches.

Recommendation: extract model indexing/import resolution, FEEL expression traversal, boxed-logic
validation, type compatibility, and diagnostic construction into focused internal components.
Use exhaustive visitors or shared traversal helpers so schema evolution fails visibly.

<a id="contents-section-7"></a>
### P2 — Expand and version the FEEL function registry

The built-in registry currently validates a small core (`not`, conversion/temporal functions,
`count`, `sum`, `min`, and `max`). This is enough for present fixtures but far below the standard
FEEL function library and must remain aligned with runtime dispatch IDs.

Recommendation: define one versioned built-in catalog consumed by semantic validation, Runtime
IR binding, and runtime/code generators. Add overload, named-argument, variadic, and null/error
semantics as conformance slices.

<a id="contents-section-8"></a>
### P2 — Add negative linked-model and conformance fixtures

Existing tests strongly cover unit semantics and basic cross-model cases. Production confidence
would benefit from model-set cycles, diamond imports, namespace/name ambiguity, conflicting IDs,
multi-hop references, imported BKM invocation, and source-location preservation across models.

<a id="contents-section-9"></a>
### P3 — Clarify mutable protobuf ownership

Results expose protobuf `Definitions` models that have been transformed by passes. The records are
immutable, but their contract does not explicitly state whether the returned model is a new value,
whether input builders can be reused, or which parsed/source fields are preserved.

Recommendation: document pass immutability and field-preservation guarantees as part of the
compiler-facing contract.

<a id="contents-section-10"></a>
## Recommended sequence

1. Strengthen diagnostics with severity and model identity.
2. Introduce a whole-model-set semantic result.
3. Add linked-model negative/conformance fixtures.
4. Extract shared traversal and indexing components from the large analyzers.
5. Establish the shared, versioned FEEL built-in catalog.

The first two items are the most valuable compiler-glue work. The module does not need a rewrite
and does not block Runtime IR or runtime iteration.


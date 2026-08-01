# `dmn-runtime-ir` assessment

Status: assessed 2026-08-01 against the implementation, architecture, semantic contracts, and
the passing six-module reactor.

## Assessment

`dmn-runtime-ir` now has a sound compiler-facing baseline. It is immutable, protobuf-free,
execution-oriented, and covers every modeled FEEL AST expression plus all modeled decision logic
forms. Deterministic runtime IDs, global value slots, lexical local slots, structural types,
dependency references, indexed context-field layouts, resolved member access, decision tables,
relations, and recursive boxed expressions are present.

The module is compiler-grade as a **lowering contract**, but it is not yet a complete executable
IR. Several pieces of metadata required by a fast evaluator are currently implicit or recoverable
only by rescanning expression trees.

## Current completion boundary

| Area | Status |
| --- | --- |
| Immutable protobuf-free contracts | complete |
| Deterministic model IDs and global slots | complete for one model |
| FEEL AST expression variants | complete (21/21) |
| Literal, boxed, invocation, and decision-table decision logic | complete |
| Lexical binding resolution | implemented |
| Decision-table structure and tests | implemented |
| Executable BKM function bodies | complete |
| Linked multi-model Runtime IR | missing |
| Persisted evaluator frame sizes/layouts | complete |
| Expression-derived dependencies and runtime order | complete |
| Indexed member/field addressing | complete for statically known context types |
| XML-to-Runtime-IR pipeline fixtures | complete for decision tables, boxed contexts, and BKMs |
| Constant canonicalization/pooling | missing |
| Runtime evaluator or code generator | not yet implemented |

## High-priority gaps

### 1. Complete linked model-set lowering

The lowerer intentionally rejects imported named types and bindings outside the current runtime
model. Cross-model semantic linking is implemented upstream, so Runtime IR is now the stage that
prevents end-to-end linked compilation.

Add a model-set lowering input that assigns deterministic IDs/slots across namespaces and lowers
imported symbol and type bindings without retaining namespace strings in executable references.

## Medium-priority improvements

### 2. Extend pipeline-level lowering tests with linked models

Two integration fixtures now run:

```text
DMN XML -> XML reader -> FEEL parser -> semantic analysis -> Runtime IR
```

They cover Traffic Violation's decision table and nested boxed context plus an executable BKM
function body. Add a two-model import fixture together with linked model-set lowering; the current
single-model lowerer intentionally rejects external bindings.

### 3. Split the monolithic lowerer

`RuntimeIrLowerer` now owns model indexing, type lowering, expression lowering, boxed lowering,
decision-table lowering, lexical-frame allocation, and validation. Extract focused internal
components after frame/model-set design is settled. Suggested boundaries are expression,
decision-table, type, and model-index lowering.

### 4. Canonicalize constants and built-ins

Runtime constants retain source strings, and built-in calls retain function names. This is a good
lossless baseline, but an evaluator would repeatedly parse numbers/temporals and dispatch built-ins
by string.

Add a later canonicalization pass that produces typed constant values or a constant pool and
stable built-in operation IDs. Keep the current lossless representation as pre-optimization IR if
useful.

### 5. Strengthen aggregate invariants

Leaf records generally validate nulls and negative local slots, but model-level contracts do not
yet validate:

- non-negative and unique IDs/value slots;
- dependency and evaluation-order bounds;
- decision-table rule widths and annotation widths;
- hit-policy/aggregation combinations;
- RuntimeType shape invariants;
- local-reference bounds against persisted frames.

Add constructor/factory validation and focused negative tests before exposing Runtime IR as a
public compiler API.

### 6. Define serialization and compatibility policy

The architecture discusses Runtime IR serialization, but the current contracts are Java records
with no versioning boundary. Decide whether Runtime IR is process-local only, Java-serializable,
or mapped to a separate versioned persistence schema. Do not reuse the semantic protobuf model.

## Improvements to avoid for now

Do not yet:

- add an evaluator directly inside `dmn-runtime-ir`;
- expose protobuf types from Runtime IR records;
- build constant pooling into the semantic analyzer;
- freeze Java record serialization as the persistence format;
- optimize away source-independent structure before correctness fixtures exist.

## Recommended implementation order

1. Complete linked model-set lowering and add its two-model XML fixture.
2. Strengthen aggregate invariants.
3. Extract lowerer components.
4. Add canonicalization/constant-pool and optimization passes.
5. Define serialization only when a concrete cache or deployment use case requires it.

## Verification baseline

The six-module Maven reactor passes. `dmn-runtime-ir` currently has 16 tests, and the complete
reactor has 166 passing tests. `git diff --check` passes for the current implementation.

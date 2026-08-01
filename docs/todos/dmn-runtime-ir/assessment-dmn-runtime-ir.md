# `dmn-runtime-ir` assessment

Status: assessed 2026-08-01 against the implementation, architecture, semantic contracts, and
the passing six-module reactor.

## Assessment

`dmn-runtime-ir` now has a sound compiler-facing baseline. It is immutable, protobuf-free,
execution-oriented, and covers every modeled FEEL AST expression plus all modeled decision logic
forms. Deterministic runtime IDs, global value slots, lexical local slots, structural types,
dependency references, decision tables, relations, and recursive boxed expressions are present.

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
| Optimized member/field addressing | missing |
| Constant canonicalization/pooling | missing |
| Runtime evaluator or code generator | not yet implemented |

## High-priority gaps

### 1. Make decision-table marker dependencies explicit

`RuntimeDecisionTableReference` correctly replaces a source table ID with an owning decision
slot. However, marker references are not currently added to `RuntimeDecision.dependencies` or
the computed evaluation order unless the DMN model also declares a matching information
requirement. A valid semantic table reference can therefore exist without an explicit runtime
scheduling edge.

Collect referenced decision slots during lowering and merge them into dependencies before the
evaluation order is finalized.

### 2. Preserve executable context-field addressing

`RuntimeType.context` stores field types but not field names or stable field indices.
`RuntimePathExpression` retains a member string, so an evaluator still needs a name-based lookup
contract that the Runtime IR does not define. This weakens the stated source-name-free and
execution-efficient goals.

Introduce a `RuntimeField` contract containing a stable field index, optional diagnostic name,
and type. Lower path and descendant operations to resolved field indices wherever the source type
is statically known; retain name lookup only for genuinely dynamic contexts.

### 3. Complete linked model-set lowering

The lowerer intentionally rejects imported named types and bindings outside the current runtime
model. Cross-model semantic linking is implemented upstream, so Runtime IR is now the stage that
prevents end-to-end linked compilation.

Add a model-set lowering input that assigns deterministic IDs/slots across namespaces and lowers
imported symbol and type bindings without retaining namespace strings in executable references.

## Medium-priority improvements

### 4. Add pipeline-level lowering tests

The current 12 Runtime IR tests cover the node shapes well, but most construct protobuf and
semantic bindings manually. Add fixtures that run:

```text
DMN XML -> XML reader -> FEEL parser -> semantic analysis -> Runtime IR
```

Prioritize Traffic Violation, a decision-table model, a BKM invocation, nested boxed logic, and a
two-model import. These tests will catch path-contract drift between semantic binding production
and lowering.

### 5. Split the monolithic lowerer

`RuntimeIrLowerer` now owns model indexing, type lowering, expression lowering, boxed lowering,
decision-table lowering, lexical-frame allocation, and validation. Extract focused internal
components after frame/model-set design is settled. Suggested boundaries are expression,
decision-table, type, and model-index lowering.

### 6. Canonicalize constants and built-ins

Runtime constants retain source strings, and built-in calls retain function names. This is a good
lossless baseline, but an evaluator would repeatedly parse numbers/temporals and dispatch built-ins
by string.

Add a later canonicalization pass that produces typed constant values or a constant pool and
stable built-in operation IDs. Keep the current lossless representation as pre-optimization IR if
useful.

### 7. Strengthen aggregate invariants

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

### 8. Define serialization and compatibility policy

The architecture discusses Runtime IR serialization, but the current contracts are Java records
with no versioning boundary. Decide whether Runtime IR is process-local only, Java-serializable,
or mapped to a separate versioned persistence schema. Do not reuse the semantic protobuf model.

## Improvements to avoid for now

Do not yet:

- add an evaluator directly inside `dmn-runtime-ir`;
- expose protobuf types from Runtime IR records;
- assign field/member indices before the frame and runtime-value layout is designed;
- build constant pooling into the semantic analyzer;
- freeze Java record serialization as the persistence format;
- optimize away source-independent structure before correctness fixtures exist.

## Recommended implementation order

1. Add implicit Runtime IR dependency edges from expression references.
2. Introduce indexed context-field layouts and resolved member access.
3. Add XML-to-Runtime-IR integration fixtures.
4. Complete linked model-set lowering.
5. Strengthen aggregate invariants.
6. Extract lowerer components.
7. Add canonicalization/constant-pool and optimization passes.
8. Define serialization only when a concrete cache or deployment use case requires it.

## Verification baseline

The six-module Maven reactor passes. `dmn-runtime-ir` currently has 14 tests, and the complete
reactor has 164 passing tests. `git diff --check` passes for the current implementation.

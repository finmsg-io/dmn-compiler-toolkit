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
| Linked multi-model Runtime IR | complete |
| Persisted evaluator frame sizes/layouts | complete |
| Expression-derived dependencies and runtime order | complete |
| Indexed member/field addressing | complete for statically known context types |
| XML-to-Runtime-IR pipeline fixtures | complete for decision tables, boxed contexts, and BKMs |
| Aggregate executable invariants | complete |
| Constant canonicalization/pooling | complete baseline |
| Stable built-in operation IDs | complete for validated built-ins |
| Runtime evaluator or code generator | not yet implemented |

## High-priority gaps

## Medium-priority improvements

### 1. Extend pipeline-level lowering tests

Two integration fixtures now run:

```text
DMN XML -> XML reader -> FEEL parser -> semantic analysis -> Runtime IR
```

They cover Traffic Violation's decision table and nested boxed context, an executable BKM function
body, and a two-model XML import with an imported structured type and value reference.

### 2. Lowerer decomposition

`RuntimeModelIndex` now owns model-set validation, deterministic source IDs/value slots, local and
qualified address aliases, and named-type catalogs. `RuntimeTypeLowerer` owns protobuf and FEEL
structural type lowering, indexed field layouts, and static member-index resolution.
`RuntimeExpressionLowerer` now owns recursive FEEL expression, unary-test, operator, invocation,
iteration, quantified-expression, nested-function, and lexical-local lowering.
`RuntimeLexicalFrame` centralizes child and captured scope transitions shared with boxed lowering.
`RuntimeDecisionTableLowerer` owns clauses, rules, unary tests, defaults, annotations, and hit
policy metadata. `RuntimeBoxedExpressionLowerer` owns recursive boxed contexts, relations, lists,
and functions. `RuntimeIrLowerer` is now the stable public orchestration facade for model elements,
dependencies, and runtime ordering. This decomposition slice is complete.

### 3. Constant and built-in optimization

`RuntimeIrOptimizer` preserves the lossless model while producing a deterministic optimized
companion with deduplicated typed constants, expression-to-pool uses, and stable IDs for recognized
static built-ins. Numeric, boolean, string, date, time, date-time, and duration values are parsed
once. Unknown and dynamic calls remain unbound for later extension/runtime dispatch.

### 4. Define serialization and compatibility policy

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

1. Define serialization only when a concrete cache or deployment use case requires it.

## Verification baseline

The six-module Maven reactor passes. `dmn-runtime-ir` currently has 27 tests, and the complete
reactor has 177 passing tests. `git diff --check` passes for the current implementation.

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
| Constant canonicalization/pooling | missing |
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

### 2. Continue splitting the lowerer

`RuntimeModelIndex` now owns model-set validation, deterministic source IDs/value slots, local and
qualified address aliases, and named-type catalogs. `RuntimeTypeLowerer` owns protobuf and FEEL
structural type lowering, indexed field layouts, and static member-index resolution.
`RuntimeIrLowerer` still owns expression lowering, boxed lowering, decision-table lowering, and
lexical-frame allocation. Extract expression and decision-table components next while keeping the
public orchestration facade stable.

### 3. Canonicalize constants and built-ins

Runtime constants retain source strings, and built-in calls retain function names. This is a good
lossless baseline, but an evaluator would repeatedly parse numbers/temporals and dispatch built-ins
by string.

Add a later canonicalization pass that produces typed constant values or a constant pool and
stable built-in operation IDs. Keep the current lossless representation as pre-optimization IR if
useful.

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

1. Extract expression and decision-table lowering components.
2. Add canonicalization/constant-pool and optimization passes.
3. Define serialization only when a concrete cache or deployment use case requires it.

## Verification baseline

The six-module Maven reactor passes. `dmn-runtime-ir` currently has 24 tests, and the complete
reactor has 174 passing tests. `git diff --check` passes for the current implementation.

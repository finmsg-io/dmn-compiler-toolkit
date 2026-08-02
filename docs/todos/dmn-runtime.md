# `dmn-runtime` TODO

Last reviewed: 2026-08-02

The runtime is an experimental interpreter baseline that proves Runtime IR is
executable. It is not yet the production semantics oracle or performance target.

## Current work

| Priority | Work item | Completion evidence |
| --- | --- | --- |
| P0 | Implement FEEL null propagation and three-valued logic | Table-driven operator, predicate, unary-test, and decision-table tests pass |
| P0 | Evaluate filters per item using explicit Runtime IR slots and lexical frames | List, context-property, numeric-position, and captured-scope filters pass |
| P0 | Complete hit policies, output priority/order, allowed values, and collect aggregation | Positive and negative conformance tests cover every supported policy |
| P1 | Implement deterministic numeric, temporal, and duration semantics accepted by analysis | Semantic acceptance implies defined runtime behavior |
| P1 | Dispatch built-ins through the shared versioned catalog and stable IDs | Named/positional arguments, overloads, variadics, nulls, and errors align across stages |
| P1 | Add compiler-to-execution fixtures for single and linked real DMNs | Shared corpus executes through the public facade |
| P1 | Add name-based inputs/requested decisions, structured errors, and host-value conversion | Public callers do not use internal integer slots |
| P1 | Add execution step, recursion, collection, and output limits plus cycle detection | Adversarial host values and models fail deterministically |
| P2 | Decompose the interpreter after semantics are locked by tests | Focused evaluator components retain parity with the baseline |

Historical context: [runtime assessment](../audits/assessment-dmn-runtime.md).

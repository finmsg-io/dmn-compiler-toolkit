# `dmn-runtime` assessment

Assessment date: 2026-08-02

## Executive conclusion

`dmn-runtime` is a valid first interpreter baseline and proves that the current Runtime IR is
executable. It correctly consumes the namespace-free graph/slot contracts, executes dependency
order, creates lexical closures, evaluates core expressions, and runs basic decision tables.

It is not yet a production DMN/FEEL runtime. Several implemented branches approximate FEEL or DMN
semantics, and the current five tests cover architectural wiring rather than conformance. The next
work should prioritize semantic correctness before performance or Rust/code-generation parity.

| Dimension | Assessment |
| --- | --- |
| Runtime IR consumption | implemented baseline |
| Aggregate scheduling and slots | implemented |
| Lexical frames and BKM closures | implemented |
| Core expression execution | broad but not conformant |
| Decision-table execution | partial semantics |
| Public invocation/input API | minimal, slot-oriented |
| Safety/resource controls | not implemented |
| Test maturity | early: five passing tests |
| Production readiness | not yet |

## Verified strengths

- `DmnRuntime.evaluate` is stateless per call and consumes only `dmn-runtime-ir`.
- Inputs populate global slots and decisions/BKMs execute in persisted evaluation order.
- Decision and nested-function frame sizes, parameter slots, lexical depth, and closures are used.
- Constants, arithmetic/comparison nodes, conditionals, lists, contexts, paths, descendants,
  ranges, `in`, `between`, `instance of`, loops, quantifiers, functions, invocations, relations,
  unary tests, and table structures have execution branches.
- Runtime contexts support stable indexed access and named access without XML/protobuf objects.
- Missing inputs, invalid operations, unknown built-ins, and external JAVA/PMML BKMs fail
  explicitly through `DmnEvaluationException`.
- Five tests cover dependency order, BKM closure parameters, a UNIQUE table, indexed context/path
  access, missing inputs, and external-function rejection.

## Findings and recommended actions

### P0 — Implement FEEL null and three-valued logic semantics

Boolean operations currently require Java `Boolean`; comparisons and arithmetic commonly throw on
`null`. FEEL defines null propagation and three-valued behavior in many operators, predicates,
unary tests, and decision-table inputs. Java equality/string coercion is not a sufficient substitute.

Recommendation: centralize FEEL value semantics in dedicated operations (`FeelBooleanOps`,
`FeelNumberOps`, `FeelComparison`, and error/null propagation) and add table-driven conformance tests.

### P0 — Correct filter evaluation

`RuntimeFilterExpression` evaluates its predicate once, outside an item frame. A predicate must be
evaluated for every list item with the FEEL item (`?`) and its lexical bindings available. Numeric
position filters are only one branch of filter semantics.

This also exposes a Runtime IR contract gap: the filter node does not persist an item local slot.
Recommendation: extend Runtime IR with the filter item slot/frame layout, lower it explicitly, and
evaluate the predicate per element.

### P0 — Complete decision-table hit-policy semantics

`PRIORITY` currently behaves as `FIRST`, and `OUTPUT_ORDER` behaves as `RULE_ORDER`. Output priority
requires declared output ordering/allowed values. Input/output allowed values are not enforced at
runtime, and COLLECT aggregation needs multi-output and null/error conformance decisions.

Recommendation: normalize priority metadata during lowering/optimization and implement each hit
policy separately, with positive and negative tests for multiple matches and invalid outputs.

### P1 — Implement temporal, duration, and complete numeric semantics

Semantic analysis accepts temporal and duration arithmetic, but runtime binary operations currently
route non-string addition/subtraction through `BigDecimal`. Power uses `double`/`Math.pow`, losing
decimal determinism. Date/time zone handling and duration distinctions need explicit rules.

Recommendation: dispatch arithmetic by Runtime IR type/operator, use deterministic decimal rules,
and implement every combination accepted by semantic analysis.

### P1 — Align built-ins across semantic analysis, IR, and runtime

Runtime dispatch is hand-written and only partially aligned with the semantic registry and optimized
built-in bindings. Named arguments are appended by map iteration for static calls, overload selection
is absent, and the interpreter evaluates `RuntimeModel` rather than consuming optimized bindings.

Recommendation: establish one built-in catalog/specification, dispatch by stable operation ID, and
validate arity, overloads, named arguments, null behavior, and return values consistently.

### P1 — Add an end-to-end compiler-to-execution API and fixtures

The public runtime accepts `Map<Integer, ?>`, requiring callers to know compiler-assigned slots and
decision IDs. There is no supported name/model-namespace input binding, requested-decision API, or
XML → FEEL → semantic → IR → execution fixture in this module.

Recommendation: let the future compiler facade return a compiled-model handle containing stable
external input/decision addresses while retaining integer slots internally. Add single- and
multi-model execution fixtures.

### P1 — Add execution limits and cycle-safe host-value traversal

Large numeric iterations, deeply nested descendants, recursive functions, and cyclic caller-supplied
maps/lists can consume unbounded CPU/stack or recurse indefinitely.

Recommendation: add configurable step, recursion, collection, and output limits plus identity-based
cycle detection for foreign context/list values.

### P2 — Decompose `DmnRuntime`

The interpreter currently combines scheduling, values, expression dispatch, functions, built-ins,
unary tests, and decision tables in one class. Split these only after semantic rules are captured by
tests: model executor, expression evaluator, FEEL operations, function dispatcher, and table evaluator.

### P2 — Improve result and error contracts

Errors currently stop the whole evaluation and carry only a message. A production API needs decision
identity, expression/IR location, error code, cause category, and possibly partial results. Runtime
values also need a documented host-value conversion contract.

## Recommended sequence

1. Add XML-to-runtime and focused FEEL conformance fixtures.
2. Fix null/three-valued logic and deterministic arithmetic.
3. Extend Runtime IR and runtime for item-aware filters.
4. Complete decision-table priority, output ordering, allowed values, and aggregation semantics.
5. Unify built-in metadata and dispatch by stable IDs.
6. Add a compiled-model name/address API and execution limits.
7. Decompose the interpreter, then profile and optimize.

Do not optimize or serialize runtime behavior before steps 1–5 establish executable semantics.
The current interpreter should be labeled an experimental baseline until those slices are complete.

# Chapter 18 — Roadmap [IMPLEMENTATION-ALIGNED]

## 18.1 Implemented compiler foundation — August 2026

```text
[done] protobuf semantic and FEEL text/parsed models
[done] namespace-aware DMN XML reader
[done] semantic XML writer for the current protobuf-supported subset
[done] imports, DRG elements, decision tables, invocations, boxed expressions
[done] item components/constraints, documentation, and structured extensions
[done] namespace/version round trips and prefixed-DMN output
[done] bounded read API, structured XML diagnostics, opt-in source locations
[done] ANTLR FEEL parser and complete semantic-model parsing pass
[done] typed semantic-analysis pipeline and validation
[done] exposed/persisted symbol and named-type bindings
[done] namespace-indexed cross-model import/reference linking and ordering
[done] structural Runtime IR with deterministic IDs, slots, types, and dependencies
[done] typed FEEL constants and bound value-slot references in Runtime IR
[done] recursive unary and binary FEEL operators in Runtime IR
```

## 18.2 Immediate completion gate

```text
[done] preserve QName typeRef namespaces through XML read/write
[done] diagnose unsupported DMN-namespace children consistently
[done] add multi-version and namespace-shadowing fixtures
[done] add DTD/XXE, deep-nesting, malformed-encoding, and hostile-input tests
[done] run and stabilize the full reactor
[done] publish the supported-subset contract
```

## 18.3 Compiler glue and Runtime IR

```text
[ ] add a compiler facade and phase-aware diagnostic aggregation
[ ] add an import source/model resolver boundary
[ ] stabilize linked semantic results as Runtime IR input
[done] lower conditional expressions, list literals, and named function calls to Runtime IR
[done] lower context expressions, lexical-local bindings, and path/property access to Runtime IR
[done] lower ranges, filters, and remaining comparison/test expressions to Runtime IR
[done] lower iteration, quantified, and function-definition expressions to Runtime IR
[done] lower invocation and descendant-access expressions to Runtime IR
[done] lower the decision-table expression marker and boxed decision-table logic to Runtime IR
[done] lower non-table boxed decision logic and DMN invocation decisions
[done] lower executable BKM function bodies and preserve function kind
[done] persist decision and nested-function lexical frame layouts
[done] add implicit Runtime IR dependency edges from expression references
[ ] introduce indexed context-field layouts and resolved member access
[ ] complete linked model-set Runtime IR lowering
```

## 18.4 Model extensions requiring design decisions

```text
[ ] DMNDI
[ ] artifacts and associations
[ ] organization units and performance indicators
[ ] decision questions and allowed answers
[ ] deeper arbitrary extension trees
```

## 18.5 Runtime, generation, and optimization

```text
[ ] constant folding and expression simplification
[ ] dependency pruning and decision-table specialization
[ ] Java generator and runtime activation
[ ] public compiler API and CLI
[ ] JMH and cross-engine correctness/performance comparisons
```

Correctness, explicit diagnostics, and deterministic tests remain prerequisites for code generation.

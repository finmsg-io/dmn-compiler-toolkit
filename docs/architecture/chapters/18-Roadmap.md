# Chapter 18 — Roadmap [IMPLEMENTATION-ALIGNED]

## 18.1 Current state — August 2026

```text
[done] dmn-protobuf
[done] FEEL text/parsed schema separation
[done] replaceable protobuf node pattern
[done] dmn-frontend-xml reader
[done] initial DmnWriter and individual writers
[done] dmn-feel-parser and ANTLR generation
[done] FeelAstBuilder
[done] depth-first DmnFeelParser
[done] UnaryTest and TypeConstraint parsing
[done] model-aware multi-error FEEL diagnostics
[done] Traffic Violation FEEL integration test
[done] dmn-semantic-analysis module
[done] first name and structured-property resolution pass
[done] Traffic Violation semantic-analysis test
[done] unified semantic-analysis pipeline
[done] named-type resolution and declared-type validation
[done] FEEL and boxed-expression type inference
[done] operator and built-in function validation
[done] decision-table, BKM, item-definition, and decision-service validation
[done] DRG dependency validation and cycle detection
[done] deterministic compilation order
[done] exposed symbol and named-type binding table
[done] namespace-indexed import validation and cross-model reference linking
[done] cross-model dependency ordering
[done] structured-list projection and structured filter typing
[done] range, between, and in-expression validation
[done] temporal and duration arithmetic
[done] instance-of named-type validation
[done] model-indexed decision-table-reference typing and diagnostics
[done] legacy single-binding loop compatibility
```

## 18.2 Next milestone — semantic-analysis completion

```text
[ ] stabilize the typed model contract for Runtime IR lowering
```

## 18.3 Dependency analysis — completed baseline

```text
[done] build the single-model DRG dependency graph
[done] validate local href targets
[done] detect unavailable dependencies
[done] detect cycles
[done] establish deterministic compilation order
[done] extend dependency analysis across imported models
```

## 18.4 XML completeness

```text
[ ] complete invocation and decision-service coverage
[ ] populate source locations
[ ] preserve documentation and extension elements
[ ] complete imports
[ ] complete writer coverage
[ ] add lossless round-trip tests
[ ] add XML security tests
```

## 18.5 Runtime and generation

```text
[done] define structural Runtime IR contracts
[done] assign deterministic integer node IDs, slots, and dependency order
[done] lower structural runtime types
[ ] lower typed FEEL AST
[ ] lower decision tables
[ ] lower complete linked model sets
[ ] implement Java generator
[ ] activate generated models without application recompilation
[ ] add public compiler API
```

## 18.6 Optimization and performance

```text
[ ] constant folding
[ ] expression simplification
[ ] dependency pruning
[ ] decision-table specialization
[ ] JMH benchmarks
[ ] cross-engine correctness and performance comparisons
```

Correctness, explicit diagnostics, and deterministic tests remain prerequisites for Runtime IR and code generation.

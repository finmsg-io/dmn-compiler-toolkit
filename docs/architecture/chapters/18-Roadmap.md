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
```

## 18.2 Next milestone — semantic type analysis

```text
[ ] expose or persist resolved symbol bindings
[ ] resolve named types comprehensively
[ ] infer types for FEEL expressions
[ ] validate unary and binary operators
[ ] resolve and validate function calls
[ ] validate decision-table input/output types
[ ] add model-aware type diagnostics
```

## 18.3 Dependency analysis

```text
[ ] build DRG dependency graph
[ ] validate href targets
[ ] detect unavailable dependencies
[ ] detect cycles
[ ] establish deterministic evaluation order
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
[ ] define Runtime IR
[ ] lower typed FEEL AST
[ ] lower decision tables
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


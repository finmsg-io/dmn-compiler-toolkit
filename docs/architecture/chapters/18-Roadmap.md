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
```

## 18.2 Immediate completion gate

```text
[ ] preserve QName typeRef namespaces through XML read/write
[ ] diagnose unsupported DMN-namespace children consistently
[ ] add multi-version and namespace-shadowing fixtures
[ ] add DTD/XXE, deep-nesting, malformed-encoding, and hostile-input tests
[ ] run and stabilize the full reactor
[ ] publish the supported-subset contract
```

## 18.3 Compiler glue and Runtime IR

```text
[ ] add a compiler facade and phase-aware diagnostic aggregation
[ ] add an import source/model resolver boundary
[ ] stabilize linked semantic results as Runtime IR input
[ ] lower typed FEEL AST to Runtime IR instructions
[ ] lower decision tables and complete linked model sets
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

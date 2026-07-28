# Chapter 18 --- Roadmap \[IMPLEMENTATION-ALIGNED\]

## 18.1 Current State --- July 2026

Completed foundation:

``` text
[done] Maven parent and module baseline
[done] dmn-protobuf module
[done] protobuf semantic schemas
[done] FEEL source schema
[done] provisional FEEL AST schema
[done] dmn-frontend-xml module
[done] VTD-XML cursor abstraction and implementation
[done] DMN version detection
[done] root Definitions reader
[done] item definitions and item components
[done] type references and constraints
[done] primary DRG readers
[done] decision logic dispatch
[done] decision tables and rules
[done] hit policy, aggregation, and orientation extraction
[done] boxed context source extraction
[done] Traffic Violation end-to-end XML-to-protobuf read
```

## 18.2 Milestone 1 --- XML Frontend Completeness

Next work within the current modules:

``` text
[ ] automated Traffic Violation assertions
[ ] namespace declaration extraction
[ ] documentation extraction
[ ] extension-element preservation
[ ] source-location population
[ ] decision question and allowed answers
[ ] requirement identity where required
[ ] expression identity where required
[ ] recursive item components decision
[ ] complete import handling
[ ] explicit unsupported-element diagnostics
```

The frontend should be considered functionally useful before it is fully
round-trip complete.

## 18.3 Milestone 2 --- FEEL Parser

``` text
[ ] create dmn-feel-parser
[ ] integrate ANTLR grammar
[ ] parse literal expressions
[ ] parse unary tests
[ ] build protobuf FEEL AST
[ ] implement generic depth-first model enrichment
[ ] attach syntax diagnostics
[ ] parse every FEEL source in Traffic Violation
```

## 18.4 Milestone 3 --- Semantic Analysis

``` text
[ ] named type registry
[ ] element ID registry
[ ] href resolution
[ ] DRG dependency graph
[ ] cycle detection
[ ] FEEL name resolution
[ ] type inference and checking
[ ] decision-table structural validation
[ ] validated compiler model
```

## 18.5 Milestone 4 --- Runtime IR

``` text
[ ] finalize Runtime IR architecture
[ ] assign integer IDs
[ ] lower typed FEEL AST
[ ] lower decision tables
[ ] create execution graph
[ ] define deterministic serialization
```

## 18.6 Milestone 5 --- Java Generation

``` text
[ ] code-generator API
[ ] Java expression emitter
[ ] decision-table emitter
[ ] generated input/output bindings
[ ] Java compilation integration
[ ] runtime activation without DMN recompilation
```

## 18.7 Milestone 6 --- Optimization and Additional Backends

Only after the Java correctness baseline:

``` text
[ ] constant folding
[ ] dead decision elimination
[ ] dependency pruning
[ ] decision-table specialization
[ ] Spark SQL backend
[ ] ByteBuddy or direct bytecode ADR
[ ] Rust, Go, WebAssembly evaluation
```

## 18.8 Milestone 7 --- Tooling

``` text
[ ] compiler public API
[ ] CLI
[ ] Maven plugin
[ ] diagnostics output
[ ] model and IR inspection tools
[ ] benchmarking suite
```

## 18.9 Roadmap Rule

Correct semantic extraction and deterministic tests take priority over
adding modules. New stages should consume the real protobuf contracts
rather than examples from the original architecture draft.

------------------------------------------------------------------------

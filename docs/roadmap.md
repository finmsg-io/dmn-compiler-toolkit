# Roadmap

## Phase 1 — Semantic model

- Complete Protobuf model coverage
- Stabilize type references
- Complete invocation model
- Complete item-definition constraints
- Add source-location coverage

## Phase 2 — XML frontend

- Complete all required DMN readers
- Add namespace-version tests
- Add invalid-document tests
- Add representative DMN 1.5 model fixtures

## Phase 3 — FEEL frontend

- Complete grammar coverage
- Build FEEL AST mapping
- Improve syntax diagnostics
- Add parser conformance tests

## Phase 4 — Semantic analysis

- Resolve symbols and dependencies
- Resolve types
- Validate function calls
- Validate decision tables
- Detect dependency cycles

## Phase 5 — Runtime IR

- Define execution-oriented IR
- Remove frontend-only metadata
- Precompute dependency order
- Represent optimized expressions

## Phase 6 — Optimization

- Constant folding
- Expression simplification
- Dead-code elimination
- Decision-table specialization
- Common subexpression reuse

## Phase 7 — Java code generation

- Generate Java evaluators
- Support isolated class loading
- Add runtime activation without application recompilation
- Add generated-code diagnostics

## Phase 8 — Performance

- Add JMH benchmarks
- Compare with established DMN engines
- Measure startup, compilation, throughput, latency, and memory
- Add regression thresholds to CI

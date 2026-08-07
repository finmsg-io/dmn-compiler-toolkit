# `dmn-optimizer` TODOs

Last reviewed: 2026-08-07

## Scope

Provides static constant folding (`ConstantFoldingPass`), algebraic identity simplification (`AlgebraicSimplificationPass`), and decision table rule pruning (`DecisionTableOptimizationPass`) for Runtime IR models.

## Current Work Items

### Core Pass Engine
- [x] Create `DmnOptimizerOptions` configuration record — `done`
- [x] Create `OptimizerPass` interface — `done`
- [x] Implement `ConstantFoldingPass` static compile-time evaluation engine — `done`
- [x] Implement `AlgebraicSimplificationPass` identity rewrite rules — `done`
- [x] Implement `DecisionTableOptimizationPass` rule pruning for `hitPolicy="FIRST"` tables — `done`
- [x] Implement `DmnOptimizer` main facade pipeline — `done`

### Testing & Verification
- [x] Unit test suite (`DmnOptimizerTest`) — `done`
- [x] Dual-engine integration test suite (`OptimizerIntegrationTest`) — `done`

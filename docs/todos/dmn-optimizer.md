# `dmn-optimizer` TODOs

Last reviewed: 2026-10-24

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


## External execution boundary

HARD-01's trace found no model-selected reflective dispatch in the optimizer.
Spark's separate bounded Java literal folding now checks explicit permission;
runtime evaluation denies by default. Generated artifacts remain specific to their
generation policy. See the verified [policy boundary](../dev/slices/security-correctness-hardening-task-2026-10-24.md).

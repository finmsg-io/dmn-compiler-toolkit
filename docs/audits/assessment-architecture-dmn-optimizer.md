# `dmn-optimizer` architecture assessment

Assessment date: 2026-08-07  
Status: **IMPLEMENTED** (100% Production Ready)

## Executive Summary

`dmn-optimizer` is the optimization pass module of the DMN Compiler Toolkit. Placed at the Runtime IR boundary (`RuntimeModel`), it transforms raw Runtime IR into optimized, reduced-size `RuntimeModel` structures prior to interpretation by `DmnRuntime` or code generation by `dmn-generator-java`.

## Architectural Placement & Boundary

```text
DmnSemanticAnalyzer (Semantic Model)
   │
   ▼
RuntimeIrLowerer (Runtime IR Lowering)
   │
   ▼
Raw RuntimeModel IR
   │
   ▼
DmnOptimizer Pipeline (dmn-optimizer)
   ├── ConstantFoldingPass
   ├── AlgebraicSimplificationPass
   └── DecisionTableOptimizationPass
   │
   ▼
Optimized RuntimeModel IR
   │
   ├────────────────────────┬────────────────────────┐
   ▼                        ▼                        ▼
DmnRuntime               DmnJavaGenerator         DmnBenchmarks
(Interpreter)            (AOT Java Engine)        (JMH Harness)
```

## Core Design Principles

1. **IR-Level Optimization**: Operating on `RuntimeModel` ensures optimizations automatically apply to all execution targets (interpreter, Java generator, future Rust/Go backends).
2. **Zero Semantic Deviation**: All rewrite rules strictly preserve FEEL 1.5 null-propagation, 3-valued logic, and overflow semantics.
3. **Modular Pass Architecture**: Each optimization rule is implemented as a self-contained `OptimizerPass` operating over immutable IR nodes.
4. **Zero Production Coupling**: Depends only on `dmn-runtime-ir` and `dmn-runtime`.

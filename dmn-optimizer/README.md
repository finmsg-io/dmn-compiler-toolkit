# `dmn-optimizer`

Static constant folding, algebraic identity simplification, and decision table rule pruning passes for Runtime IR models.

## Key Assets

- **`ConstantFoldingPass`**: Static compile-time evaluation pass (`1 + 2` → `3`, `10 * 5` → `50`, `"hello " + "world"` → `"hello world"`, `true and false` → `false`, `not(false)` → `true`, `abs(-42)` → `42`, `upper case("test")` → `"TEST"`).
- **`AlgebraicSimplificationPass`**: Identity rewrites (`x + 0` → `x`, `x * 1` → `x`, `not(not(x))` → `x`, `if true then A else B` → `A`).
- **`DecisionTableOptimizationPass`**: Prunes unreachable rules following a catch-all wildcard rule in `hitPolicy="FIRST"` decision tables.
- **`DmnOptimizer`**: Main facade pipeline executing optimization passes on `RuntimeModel` IR.

## Usage

```java
RuntimeModel optimizedModel = new DmnOptimizer().optimize(rawModel);
```

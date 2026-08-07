# `dmn-runtime-ir`

Immutable Runtime IR contracts and lowering boundary separating protobuf compilation from execution backends.

## Key Assets

- **`RuntimeModel`**: Protobuf-free, execution-oriented intermediate representation with integer value slots, constant pool IDs, and dependency schedules.
- **`RuntimeIrLowerer`**: Semantic-to-IR lowering pass.
- **`RuntimeIrOptimizer`**: Constant pool canonicalization and built-in operation ID lowering (`RuntimeOptimizedModel`).

## Usage

```java
RuntimeModel irModel = new RuntimeIrLowerer().lower(semanticResult);
RuntimeOptimizedModel optModel = new RuntimeIrOptimizer().optimize(irModel);
```

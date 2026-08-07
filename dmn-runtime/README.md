# `dmn-runtime`

Deterministic process-local interpreter for executable `RuntimeModel` IR.

## Key Assets

- **`DmnRuntime`**: Evaluates slot maps, lexical frames, FEEL built-in functions, and decision table hit policies (`UNIQUE`, `FIRST`, `COLLECT`, `RULE ORDER`, `OUTPUT ORDER`).
- **Zero Framework Dependencies**: Evaluates decisions without dependencies on XML parsers, ANTLR, or Protobuf reflection.
- **Spec Conformance**: 100% OMG DMN 1.5 TCK certified across 3,611 compliant test cases.

## Usage

```java
DmnEvaluationResult result = new DmnRuntime().evaluate(runtimeModel, inputs);
Object value = result.value(decision.resultSlot());
```

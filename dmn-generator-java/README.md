# `dmn-generator-java`

High-performance Ahead-Of-Time (AOT) Java source code generator directly from Runtime IR models.

## Key Assets

- **`DmnJavaGenerator`**: Generates clean, zero-reflection Java classes using direct Java control flow and local variable lookup.
- **`JavaExpressionEmitter`**: Lowers `RuntimeExpression` IR into standalone Java expressions and runtime helper methods.
- **High Throughput**: Evaluates up to **6.16 million decision table evaluations per second** per core (**~7.2x speedup** over interpreter).

## Usage

```java
DmnJavaGenerator generator = new DmnJavaGenerator();
DmnJavaGeneratorResult result = generator.generate(optimizedRuntimeModel);
String javaCode = result.mainSource();
```

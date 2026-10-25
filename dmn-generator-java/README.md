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

Generated engines deny model-defined external Java calls by default. After
compiling the generated source, the host may construct an engine with an explicit
`ExternalJavaPolicy` argument. Each engine retains its own immutable policy;
authorizing one instance does not authorize another. See the
[runtime policy API](../dmn-runtime/README.md#external-java-execution-policy).

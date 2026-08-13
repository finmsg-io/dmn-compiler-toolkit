# `dmn-compiler`

Public compiler facade and resolver-independent model loading boundary for the DMN Compiler Toolkit.

## Key Assets

- **`DmnCompiler`**: Supported one-call facade executing the complete compilation pipeline: XML reading, FEEL parsing, semantic analysis, diagnostic aggregation, and Runtime IR lowering/optimization.
- **`DmnModelResolver`**: Transitive model loader (`InMemoryDmnModelResolver`) resolving cross-model XML imports by namespace and location.
- **`DmnCompilerDiagnostic`**: Structured, phase-aware diagnostic contract aggregating errors and warnings across all stages.

## Usage

```java
DmnSource source = new DmnSource(new DmnSourceId(URI.create("urn:model")), xmlBytes);
DmnCompilationResult result = new DmnCompiler().compile(source);
RuntimeModel model = result.optimizedRuntimeModel().orElseThrow().model();
```

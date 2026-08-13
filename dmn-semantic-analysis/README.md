# `dmn-semantic-analysis`

Compiler middle-end module providing reference resolution, static type analysis, DMN validation, and DRG topological ordering.

## Key Assets

- **`DmnSemanticAnalyzer`**: Symbol collection, requirement-aware decision scoping, BKM parameter scoping, and FEEL name resolution.
- **Cross-Model Import Resolution**: Namespace-indexed cross-model reference linking and cycle detection.
- **Topological Ordering**: Generates deterministic execution dependency ordering across multi-model DRG decision graphs.

## Usage

```java
DmnSemanticAnalyzerResult result = new DmnSemanticAnalyzer().analyze(parsedModel);
```

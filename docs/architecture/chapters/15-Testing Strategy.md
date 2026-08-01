# Chapter 15 — Testing Strategy [IMPLEMENTED BASELINE]

## 15.1 Current test layers

```text
1. FEEL grammar conformance tests
2. FeelParserFacade tests
3. FeelAstBuilder structure tests
4. DmnFeelParser traversal and idempotency tests
5. model-aware multi-error diagnostic tests
6. XML semantic round-trip tests
7. XML multi-version, namespace, malformed-input, DTD/XXE, and depth-limit tests
8. Traffic Violation XML-to-FEEL integration test
9. semantic-analysis unit and cross-model tests
10. Traffic Violation full frontend-to-semantic-analysis test
11. structural Runtime IR lowering tests
```

## 15.2 Traffic Violation pipeline

The representative integration path is:

```text
TrafficViolation.dmn
  → DmnXmlReader
  → semantic Definitions with text
  → DmnFeelParser
  → parsed Definitions
  → DmnSemanticAnalyzer
  → successful semantic result
```

Assertions cover input-model immutability, decision-table expressions, unary tests, type constraints, boxed context expressions, AST structure, name resolution, property resolution, and parser idempotency.

## 15.3 Diagnostic tests

FEEL diagnostic tests verify:

- collection of multiple independent syntax errors
- precise semantic-model paths
- preservation of invalid text branches
- continued parsing of valid branches
- strict API exception behavior

Semantic-analysis tests verify:

- unknown names
- names unavailable through requirements
- duplicate global declarations
- invalid structured properties
- successful Traffic Violation resolution

## 15.4 Next test priorities

- compiler-facade and import-resolution integration tests
- broader DMN and FEEL conformance fixtures
- deterministic protobuf serialization tests
- Runtime IR expression and decision-table lowering tests

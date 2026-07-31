# Chapter 15 — Testing Strategy [IMPLEMENTED BASELINE]

## 15.1 Current test layers

```text
1. FEEL grammar conformance tests
2. FeelParserFacade tests
3. FeelAstBuilder structure tests
4. DmnFeelParser traversal and idempotency tests
5. model-aware multi-error diagnostic tests
6. XML writer tests
7. Traffic Violation XML-to-FEEL integration test
8. semantic-analysis unit tests
9. Traffic Violation full frontend-to-semantic-analysis test
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

- XML reader unit and negative tests
- complete XML writer round-trip tests
- source-location assertions
- import and namespace tests
- semantic type-inference tests
- dependency-cycle tests
- broader DMN and FEEL conformance fixtures
- security tests for hostile XML
- deterministic protobuf serialization tests


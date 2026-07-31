# Chapter 10 — Compiler Passes [PARTIALLY IMPLEMENTED]

## 10.1 Implemented passes

### FEEL parsing pass

```java
DmnFeelParseResult result =
    new DmnFeelParser().parseWithDiagnostics(semanticModel);
```

Responsibilities:

- copy the semantic protobuf model
- traverse all supported FEEL-bearing paths depth-first
- parse expressions and unary tests
- replace successful text branches with parsed branches
- preserve invalid nodes as text
- collect every syntax error with a semantic-model path

The strict `parse(Definitions)` API throws `DmnFeelParseException` after collection.

### Initial semantic-analysis pass

```java
DmnSemanticAnalysisResult result =
    new DmnSemanticAnalyzer().analyze(parsedModel);
```

Responsibilities currently implemented:

- collect item definitions and DRG declarations
- create requirement-aware scopes
- create function and context scopes
- resolve FEEL names
- validate structured property access
- report duplicate, ambiguous, unavailable, and unknown symbols

The analyzer currently returns the same immutable parsed model plus diagnostics. Persisted resolved bindings and inferred types are future work.

## 10.2 Pass principles

1. **Single responsibility** — parsing and semantic analysis remain separate.
2. **Immutable input** — transformations create copied protobuf messages.
3. **Deterministic traversal** — repeated fields are processed in index order.
4. **Generated accessors** — hot compiler paths avoid protobuf reflection.
5. **Independent tests** — each pass has focused unit and integration tests.
6. **Structured diagnostics** — errors include a model path and source location where available.

## 10.3 Current order

```text
DmnXmlReader
   ↓
DmnFeelParser
   ↓
DmnSemanticAnalyzer
```

FEEL parsing must precede semantic analysis.

## 10.4 Next semantic passes

```text
name resolution
   ↓
named-type resolution
   ↓
expression type inference
   ↓
operator and function validation
   ↓
dependency graph and cycle detection
   ↓
decision-table validation
   ↓
Runtime IR lowering
```

Name resolution is the currently implemented first step.

## 10.5 Future pass infrastructure

A generic pass manager, shared compiler context, optimization pipeline, and timing metrics should be introduced only when multiple later passes require them. The current explicit stage APIs keep dependencies and failure behavior clear.


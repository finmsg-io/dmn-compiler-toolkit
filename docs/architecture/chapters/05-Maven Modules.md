# Chapter 5 — Maven Modules [IMPLEMENTED / TARGET]

## 5.1 Current project structure

```text
dmn-compiler-toolkit
├── pom.xml
├── dmn-protobuf
├── dmn-frontend-xml
├── dmn-feel-parser
├── dmn-semantic-analysis
└── dmn-runtime-ir
```

All modules use:

```text
groupId: io.finmsg.dmn
version: 1.0.0-SNAPSHOT
Java:    25
```

## 5.2 `dmn-protobuf`

Defines generated contracts in `io.finmsg.dmn.model`.

```text
common.proto
core.proto
types.proto
feel_text.proto
feel_parsed.proto
feel.proto
decision_table.proto
drg.proto
model.proto
```

The text schema does not depend on the parsed schema. `feel.proto` composes both through replaceable wrapper messages.

## 5.3 `dmn-frontend-xml`

Production dependencies:

```text
dmn-protobuf
vtd-xml
slf4j-api
```

Responsibilities:

- DMN version detection
- XML cursor abstraction
- element-specific readers
- semantic protobuf construction
- semantic DMN XML writing for the current protobuf-supported subset
- structured read diagnostics, bounded input, and opt-in source locations
- namespace/version preservation and prefixed-DMN output

## 5.4 `dmn-feel-parser`

Production dependencies:

```text
dmn-protobuf
antlr4-runtime
```

Responsibilities:

- FEEL grammar
- parse-tree creation
- protobuf AST construction
- depth-first semantic-model parsing pass
- strict and diagnostic parsing APIs

`dmn-frontend-xml` is test-scoped for the Traffic Violation integration test.

## 5.5 `dmn-semantic-analysis`

Production dependency:

```text
dmn-protobuf
```

Responsibilities currently implemented:

- declaration collection
- requirement-aware scopes
- FEEL name and property resolution
- semantic diagnostics
- named-type resolution and declared-type validation
- FEEL and boxed-expression type inference
- operator and built-in function validation
- decision-table, BKM, item-definition, and decision-service validation
- DRG dependency validation, cycle detection, and deterministic compilation order
- unified pass orchestration
- persisted/exposed symbol and named-type bindings
- namespace-indexed cross-model import/reference linking and dependency ordering

`dmn-feel-parser` and `dmn-frontend-xml` are test-scoped dependencies only.

## 5.6 `dmn-runtime-ir`

Production dependency:

```text
dmn-semantic-analysis
```

Implemented baseline responsibilities:

- immutable, protobuf-free runtime contracts
- deterministic integer node IDs and value slots
- structural runtime-type lowering
- integer dependency references and evaluation order
- rejection of unsuccessful semantic-analysis results

Typed constants, bound value-slot references, and recursive unary/binary operators are
implemented. Conditionals, collections, calls, constant pools, decision tables, and model-set
lowering remain future work.

## 5.7 Target modules

```text
dmn-optimizer
dmn-codegen-java
dmn-runtime
dmn-compiler-api
dmn-benchmarks
```

## 5.8 Module rules

1. One architectural responsibility per module.
2. Production dependencies point toward lower-level contracts.
3. Protobuf schemas contain no compiler logic.
4. XML and ANTLR types never cross into later compiler or runtime APIs.
5. Cross-module integration dependencies may remain test-scoped.

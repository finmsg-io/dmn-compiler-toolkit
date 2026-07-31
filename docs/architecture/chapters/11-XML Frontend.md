# Chapter 11 — XML Frontend [IMPLEMENTED, PARTIAL COVERAGE]

## 11.1 Reader

`DmnXmlReader` uses VTD-XML and supports:

```java
read(Path)
read(InputStream)
read(byte[])
```

It detects the DMN namespace/version and delegates to explicit element readers.

## 11.2 Implemented reader areas

- Definitions and common node metadata
- imports
- item definitions, components, types, and allowed-value constraints
- input data, decisions, BKMs, knowledge sources, and decision services
- information, knowledge, and authority requirements
- literal, decision-table, invocation, and supported boxed logic
- decision-table clauses, rules, annotations, policies, and orientation
- context, relation, list, and function-definition text structures

The frontend always creates FEEL text branches. It never invokes the FEEL parser.

## 11.3 Writer

The module now contains:

- `DmnWriter` in `io.finmsg.dmn.frontend.xml.dmn`
- `XmlEmitter`
- individual writer classes in `io.finmsg.dmn.frontend.xml.dmn.writer`
- writer tests

Current writer coverage includes the implemented core definitions/DRG subset. It remains partial; complete lossless DMN round-trip support has not been claimed.

## 11.4 Frontend boundary

```text
DMN XML → Definitions with FeelText
```

Reference resolution, FEEL parsing, type inference, and execution belong to later modules.

## 11.5 Diagnostics and source locations

XML errors are currently exception-based. `SourceLocation` exists in protobuf, but the reader does not yet populate it consistently. This limits line/column information in later diagnostics, although semantic-model paths are available.

## 11.6 Traffic Violation coverage

Automated cross-module tests now exercise:

```text
TrafficViolation.dmn
  → DmnXmlReader
  → DmnFeelParser
  → DmnSemanticAnalyzer
```

Covered structures include item constraints, decision-table inputs and rules, boxed context entries, requirements, names, and structured properties.

## 11.7 Remaining work

- complete imports and cross-model namespace behavior
- complete invocation and decision-service coverage
- documentation and extension preservation
- source-location population
- full writer coverage and round-trip comparison
- malformed and hostile XML tests
- broader DMN conformance fixtures


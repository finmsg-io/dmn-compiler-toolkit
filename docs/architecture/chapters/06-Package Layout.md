# Chapter 6 — Package Layout [IMPLEMENTED]

## 6.1 Generated model

```text
io.finmsg.dmn.model
```

Contains all Java classes generated from the protobuf schemas, including `Definitions`, `Feel`, `FeelText`, `FeelParsed`, `Expression`, and decision-table types.

## 6.2 XML frontend

```text
io.finmsg.dmn.frontend.xml
├── XmlCursor
├── XmlEmitter
├── XmlReader
├── XmlWriter
├── dmn
│   ├── DmnXmlReader
│   ├── DmnWriter
│   ├── DmnVersionDetector
│   ├── reader
│   └── writer
├── exception
├── util
└── vtd
```

Individual DMN writers belong in:

```text
io.finmsg.dmn.frontend.xml.dmn.writer
```

## 6.3 FEEL parser

```text
io.finmsg.dmn.feel.parser
├── FeelParserFacade
├── FeelAstBuilder
├── DmnFeelParser
├── FeelDiagnostic
├── DmnFeelDiagnostic
├── DmnFeelParseResult
└── DmnFeelParseException
```

ANTLR-generated sources use the same parser package below `src/gen/java`.

## 6.4 Semantic analysis

```text
io.finmsg.dmn.semantic.analysis
├── DmnSemanticAnalyzer
├── DmnSemanticAnalysisResult
└── DmnSemanticDiagnostic
```

## 6.5 Public entry points

Currently usable stage-level entry points:

```text
io.finmsg.dmn.frontend.xml.dmn.DmnXmlReader
io.finmsg.dmn.frontend.xml.dmn.DmnWriter
io.finmsg.dmn.feel.parser.FeelParserFacade
io.finmsg.dmn.feel.parser.DmnFeelParser
io.finmsg.dmn.semantic.analysis.DmnSemanticAnalyzer
```

These are compiler-stage APIs. A stable high-level compiler facade remains future work.

## 6.6 Package rules

1. XML-specific code stays below `frontend.xml`.
2. Generated messages stay below `model`.
3. FEEL parsing stays below `feel.parser`.
4. Semantic passes stay below `semantic.analysis`.
5. Later stages must not import XML or ANTLR implementation types.


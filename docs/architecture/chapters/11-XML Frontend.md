# Chapter 11 — XML Frontend [IMPLEMENTED FOR CURRENT MODEL SUBSET]

## 11.1 Boundary

```text
DMN XML ⇄ Definitions with FEEL text
```

`dmn-frontend-xml` owns XML parsing and semantic XML emission. FEEL parsing, reference/type
resolution, imported-model loading, and execution belong to later compiler stages.

## 11.2 Reader API

`DmnXmlReader` accepts `Path`, `InputStream`, or `byte[]`. The legacy `read` methods return a
model or throw. `readResult` returns `DmnReadResult`, containing an optional model and structured
diagnostics. `DmnReadOptions` supplies a system ID, a configurable input-size limit (16 MiB by
default), and opt-in source-location capture.

Diagnostics currently distinguish oversized input (`DMN-XML-001`), I/O failures
(`DMN-XML-002`), malformed XML (`DMN-XML-003`), and recognized-but-unsupported DMN content
(`DMN-XML-004`).

## 11.3 Namespace and version behavior

- element dispatch uses the effective namespace URI plus local name;
- nested namespace redeclarations and foreign local-name collisions are safe;
- supported DMN vocabulary versions are normalized into the current semantic model;
- non-default vocabulary versions are retained in `Definitions.model_namespace_uri`;
- root namespace declarations are retained in `Definitions.namespaces`;
- the writer emits ordinary unprefixed DMN or collision-free prefixed DMN when the business
  model owns the default namespace.

QName `typeRef` values are resolved in the element's namespace scope and stored as a local type
name plus namespace URI. The writer reuses an existing prefix or declares a collision-free one.

## 11.4 Symmetric reader/writer coverage

The XML-representable portion of the current protobuf model has symmetric reader/writer and
semantic read-write-read coverage:

- definitions metadata, namespaces, versions, and imports;
- item definitions, components, collection flags, and text constraints;
- input data, decisions, BKMs, knowledge sources, and decision services;
- information, knowledge, and authority requirements;
- literal FEEL, invocations/bindings, decision tables, and supported boxed expressions;
- decision-table clauses, policies, orientation, annotations, rules, and defaults;
- contexts, relations, lists, and boxed functions;
- node documentation and one-level structured extension elements.

The writer rejects parsed-only FEEL, unary tests, constraints, or boxed expressions when no
source text exists. Compiler metadata such as inferred types and semantic bindings is not XML.

## 11.5 Source locations and extensions

When enabled, semantic nodes receive system ID, line, column, and byte offset. Capture is off by
default because locations are compiler metadata and would otherwise break semantic round-trip
equality. Extensions preserve one level of namespace-qualified elements, attributes, text, and
root namespace declarations; arbitrary deep XML trees and formatting are not preserved.

## 11.6 Explicit model boundary

DMNDI, artifacts/associations, organization units, performance indicators, decision questions,
allowed answers, and some expression/requirement IDs are not representable in the current
protobuf model. Adding them requires a semantic-model decision rather than reader/writer glue.

## 11.7 Completion gate

Before the current subset is declared compiler-grade complete:

1. add the multi-version, namespace-shadowing, conformance, and hostile-input matrix;
2. run the complete Maven reactor.

See [the XML completeness audit](../../audits/dmn-frontend-xml-completeness.md).

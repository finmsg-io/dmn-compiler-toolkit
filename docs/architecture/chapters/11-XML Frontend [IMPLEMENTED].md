# Chapter 11 --- XML Frontend \[IMPLEMENTED\]

## 11.1 Purpose

The XML frontend is the active compiler frontend. It reads DMN XML using
VTD-XML and produces the protobuf Semantic Model.

It performs no FEEL parsing, reference resolution, type inference,
optimization, or execution.

## 11.2 Module and Packages

``` text
module:  dmn-frontend-xml
package: io.finmsg.dmn.frontend.xml
```

Primary entry point:

``` java
DmnXmlReader reader = new DmnXmlReader();
Definitions definitions = reader.read(path);
```

Supported inputs:

``` java
read(Path)
read(InputStream)
read(byte[])
```

## 11.3 XML Cursor Abstraction

`XmlCursor` isolates DMN readers from VTD-XML.

Implemented cursor capabilities include:

-   root navigation
-   child and sibling navigation
-   parent navigation
-   element and local-name access
-   required and optional attributes
-   typed attribute conversion
-   text access
-   namespace URI and prefix access
-   depth and path information
-   child-existence checks
-   reset and close

`VtdXmlCursor` is the current implementation and parses namespace-aware
XML.

## 11.4 Version Detection

`DmnVersionDetector` inspects the root namespace and creates
`DmnXmlContext`. The context currently carries DMN version and namespace
information for frontend coordination.

Element readers generally use local names and do not embed
DMN-version-specific logic unless necessary.

## 11.5 Reader Pipeline

``` text
DmnXmlReader
    |
    +-- creates VtdXmlCursor
    +-- validates root availability
    +-- detects DMN version
    |
    v
DefinitionsReader
    |
    +-- reads root Node and attributes
    |
    v
DefinitionsBodyReader
    |
    +-- ImportReader
    +-- ItemDefinitionReader
    +-- InputDataReader
    +-- DecisionReader
    +-- BusinessKnowledgeModelReader
    +-- KnowledgeSourceReader
    +-- DecisionServiceReader
```

## 11.6 Implemented Readers

The implementation currently contains readers for:

### Root and common structures

-   Definitions
-   Definition body dispatch
-   Node
-   Import
-   InformationItem / variable
-   TypeReference
-   ItemDefinition
-   ItemComponent
-   TypeConstraint

### DRG structures

-   InputData
-   Decision
-   BusinessKnowledgeModel
-   KnowledgeSource
-   DecisionService
-   InformationRequirement
-   KnowledgeRequirement
-   AuthorityRequirement

### Decision logic

-   DecisionLogic dispatch
-   DecisionTable
-   InputClause
-   OutputClause
-   DecisionRule
-   AnnotationClause
-   Invocation
-   FunctionDefinition

### Boxed-expression source structures

-   Context
-   ContextEntry
-   List
-   Relation
-   RelationColumn
-   RelationRow
-   FunctionDefinition source
-   generic expression-source dispatch

## 11.7 Item Definition Reading

`ItemDefinitionReader` enters the element body and delegates each
`itemComponent` to `ItemComponentReader`.

`ItemComponentReader` reads:

-   node identity
-   `isCollection`
-   child `typeRef`
-   `allowedValues` / type constraints

Constraint source is preserved as a FEEL expression string. It is not
split into Java values during XML reading.

## 11.8 Decision Table Reading

`DecisionTableReader` reads the following XML attributes directly from
the decision-table element:

``` text
hitPolicy
aggregation
preferredOrientation
```

They are normalized to typed protobuf values.

Implemented mappings include:

``` text
UNIQUE, FIRST, PRIORITY, ANY, COLLECT, RULE ORDER, OUTPUT ORDER
SUM, MIN, MAX, COUNT
Rule-as-Row, Rule-as-Column, CrossTable
```

Input and output type references use the common `TypeReferenceReader`,
ensuring `number` and `string` become built-in types rather than named
types.

## 11.9 FEEL Handling

The frontend preserves FEEL text exactly at the semantic boundary.

Examples:

``` text
Violation.Type
Violation.ActualSpeed - Violation.SpeedLimit
[10..30)
>= 30
if TotalPoints >= 20 then \"Yes\" else \"No\"
```

No reader may invoke an ANTLR parser or perform FEEL semantic
interpretation.

## 11.10 Navigation Contract

A reader that calls `firstChild()` must restore the cursor with
`parent()` before returning. Child readers must leave the cursor
positioned on the element at which they were called.

This contract enables deterministic sibling iteration and prevents
reader coupling.

## 11.11 Error Handling

Implemented frontend exceptions include:

``` text
XmlException
XmlReadException
XmlWriteException
MissingAttributeException
InvalidAttributeException
NavigationException
NamespaceException
```

Current errors are exception-based. Structured protobuf diagnostics
exist in `common.proto` but are not yet integrated into the frontend
pipeline.

## 11.12 Writer Status

`XmlWriter`, `XmlEmitter`, and `XmlWriteException` exist as initial
abstractions. A complete DMN XML writer is not implemented.

The current frontend responsibility is therefore:

``` text
DMN XML -> Semantic Model
```

Round-trip serialization remains future work.

## 11.13 Security

The frontend uses VTD-XML rather than DOM/JAXB object graphs. Security
validation must include tests for external entities, oversized text,
excessive nesting, malformed namespaces, and resource limits before
accepting untrusted production documents.

## 11.14 Current Completeness

Validated with the Traffic Violation DMN:

-   root metadata
-   item definitions and components
-   allowed-value constraint source
-   input data
-   decisions
-   information requirements
-   decision-table hit policy and orientation
-   input and output types
-   rule entries
-   context boxed expression

Known remaining frontend/model work:

-   namespace declaration population
-   documentation and extension-element extraction
-   source-location extraction
-   decision question and allowed answers
-   IDs for expression and requirement elements where schemas do not
    contain Node
-   complete imports and all DMN expression kinds
-   XML writer

## 11.15 Design Rules

1.  The frontend produces semantic protobuf messages.
2.  The frontend never parses FEEL.
3.  Readers remain small and element-specific.
4.  Cursor position is restored before returning.
5.  Common type normalization is centralized.
6.  Unsupported content is either explicitly ignored during the current
    bootstrap phase or reported once diagnostics are integrated.
7.  Frontend implementation details never enter runtime modules.

------------------------------------------------------------------------

# Chapter 7 --- Semantic Model \[IMPLEMENTED\]

## 7.1 Purpose

The Semantic Model is the first compiler representation and the current
primary implementation artifact.

It is defined by Protocol Buffers and exposed in Java through generated
immutable message classes and builders. There is no separate handwritten
semantic-model layer.

``` text
DMN XML
    |
    v
dmn-frontend-xml
    |
    v
io.finmsg.dmn.model.Definitions
```

## 7.2 Design Goals

The implemented model provides:

-   XML-independent DMN semantics
-   deterministic protobuf serialization
-   generated immutable messages
-   typed oneof structures instead of generic maps
-   explicit FEEL source preservation
-   separation between FEEL source and FEEL AST
-   language-neutral schemas

## 7.3 Protobuf Organization

``` text
common.proto
    shared metadata, diagnostics, namespaces, references

core.proto
    Node and common core structures

types.proto
    built-in and named types, information items, constraints

feel_source.proto
    FEEL text and boxed-expression source structures

feel_ast.proto
    provisional parsed FEEL AST structures

decision_table.proto
    decision table semantics

drg.proto
    DRG elements and executable DMN logic containers

model.proto
    Definitions root, imports, item definitions
```

## 7.4 Root Model

The root message is `Definitions`.

Implemented fields include:

``` protobuf
message Definitions {
  Node node = 1;
  string namespace = 2;
  string expression_language = 3;
  string type_language = 4;
  string exporter = 5;
  string exporter_version = 6;
  repeated Namespace namespaces = 20;
  repeated Import imports = 21;
  repeated ItemDefinition item_definitions = 22;
  repeated DrgElement drg_elements = 23;
}
```

The XML frontend currently populates the root node, model namespace,
language attributes, imports, item definitions, and supported DRG
elements. Namespace declaration collection is represented by the schema
but is not yet populated by the reader.

## 7.5 Common Node Model

Reusable DMN identity and metadata are represented by composition:

``` protobuf
message Node {
  string id = 1;
  string name = 2;
  string label = 3;
  Documentation documentation = 10;
  ExtensionElements extension_elements = 11;
  SourceLocation source_location = 20;
}
```

Not every source-expression structure currently embeds `Node`.
Consequently, IDs for some XML expression elements are not yet
preserved. This is a known semantic-model completeness item rather than
frontend navigation failure.

## 7.6 Type Model

Implemented type references:

``` text
TypeReference
├── BuiltinType
├── NamedTypeReference
├── ListTypeReference
└── FunctionTypeReference
```

Built-in FEEL types are normalized by `TypeReferenceReader`. Examples
include `string`, `number`, `boolean`, `date`, `time`, durations, range,
any, and null. User-defined names such as `tViolation` remain named
references for later semantic resolution.

## 7.7 Item Definitions

Implemented item-definition structure:

``` protobuf
message ItemDefinition {
  Node node = 1;
  TypeReference type = 2;
  bool is_collection = 3;
  repeated ItemComponent components = 4;
  TypeConstraint constraint = 5;
}
```

Structured item components are extracted from nested `itemComponent`
elements. Each component preserves identity, type, collection flag, and
optional type constraint.

Example implemented result:

``` text
tViolation
├── Code: string
├── Date: date
├── Type: string
│   └── constraint: \"speed\", \"parking\", \"driving under the influence\"
├── SpeedLimit: number
└── ActualSpeed: number
```

Nested components below `ItemComponent` are not currently represented
because `ItemComponent` has no recursive `components` field.

## 7.8 DRG Model

`DrgElement` uses a typed `oneof`:

``` text
InputData
Decision
BusinessKnowledgeModel
KnowledgeSource
DecisionService
```

Decisions contain variable information, decision logic, and
requirements. References remain href-based at this stage and are
resolved later by semantic analysis.

## 7.9 FEEL Source Model

The XML frontend does not parse FEEL. It preserves source using:

``` protobuf
message FeelSource {
  string text = 1;
}
```

Boxed expressions are represented structurally in `feel_source.proto`,
including context, list, relation, function-definition, and other source
forms supported by the current reader.

This is deliberately separate from `feel_ast.proto`.

## 7.10 Decision Tables

The implemented semantic model contains:

-   node metadata
-   hit policy and optional aggregation
-   preferred orientation
-   typed input clauses
-   typed output clauses
-   ordered rules
-   unary-test source text
-   output-expression source text
-   annotations

The reader normalizes built-in input and output type references and
assigns deterministic zero-based rule indexes. Protobuf text format
omits the first rule index because zero is the proto3 default value.

## 7.11 Current Completeness Boundaries

Known model or reader gaps include:

-   XML namespace declarations are represented but not populated.
-   some expression-element IDs are not representable.
-   decision `question` and `allowedAnswers` are not represented.
-   information-requirement IDs are not represented.
-   source locations and extension elements are not yet populated.
-   recursive item components are not represented.
-   FEEL AST schemas exist but are not produced.

These gaps do not invalidate the current compiler boundary. They define
the next semantic-completeness work.

## 7.12 Immutability

Protobuf builders are mutable construction mechanisms local to the
frontend. Built messages are immutable and become the compiler-stage
result.

Compiler passes should follow copy-on-write builder transformations:

``` text
input message
    |
    v
toBuilder / generated getters
    |
    v
enriched output message
```

The semantic and compiler models may share the same protobuf schema
initially, but each compiler phase should produce a new message rather
than mutate the original built instance.

## 7.13 Versioning

Schema evolution follows protobuf rules:

-   field numbers are stable
-   removed numbers are reserved
-   additive fields are preferred
-   semantic breaking changes require an explicit model-version decision

## 7.14 Testing Baseline

The Traffic Violation DMN currently validates the following reader
coverage:

-   definitions metadata
-   structured item definitions
-   built-in and named types
-   type constraints
-   input data
-   decisions and requirements
-   decision tables
-   contexts and FEEL source expressions

Automated assertions should replace manual protobuf-text inspection as
the next testing step.

------------------------------------------------------------------------

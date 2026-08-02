# `dmn-protobuf` assessment

Assessment date: 2026-08-02

<a id="contents-section-1"></a>
## Assessment

<!-- generated-toc:start -->
## Table of contents

- [Assessment](#contents-section-1)
- [Worthwhile improvements](#contents-section-2)
  - [1. Add protobuf compatibility enforcement — high priority](#contents-section-3)
  - [2. Complete the DMN import contract — high priority](#contents-section-4)
  - [3. Support recursive item definitions — high priority](#contents-section-5)
  - [4. Improve source-location granularity — medium/high priority](#contents-section-6)
  - [5. Unify the diagnostics model — medium priority](#contents-section-7)
  - [6. Add resolved-reference annotations carefully — medium priority](#contents-section-8)
  - [7. Clarify extension preservation — low priority](#contents-section-9)
- [Improvements I would avoid](#contents-section-10)
- [Recommended order](#contents-section-11)
<!-- generated-toc:end -->

`dmn-protobuf` is architecturally sound and fits the parent architecture well. It correctly serves as the dependency-free contract layer shared by the XML frontend, FEEL parser, and semantic-analysis modules. I would retain the current protobuf-centric design.

The module aligns particularly well with:

- immutable compiler-stage representations;
- downward-only module dependencies;
- strongly typed contracts;
- isolation from XML and ANTLR implementation types;
- text-to-parsed FEEL replacement through `oneof`;
- a separate future Runtime IR rather than reusing the semantic model at runtime.

The schema organization also closely matches the structure prescribed in
[Chapter 7](../architecture/chapters/07-Semantic%20Model.md).

<a id="contents-section-2"></a>
## Worthwhile improvements

<a id="contents-section-3"></a>
### 1. Add protobuf compatibility enforcement — high priority

The architecture calls these “stable protobuf boundaries,” but the module has no automated schema-compatibility protection or direct tests.

Introduce:

- a checked-in descriptor baseline or Buf configuration;
- breaking-change checks in CI;
- protobuf linting;
- serialization determinism and round-trip tests;
- tests for unknown-field preservation.

This becomes important because generated messages are already effectively public APIs.

Also reserve removed fields and names. For example, `Import.import_type = 4` is
commented out in `dmn-protobuf/src/main/proto/model.proto`, but field 4 is not
reserved. A future contributor could accidentally reuse it incompatibly.

The legacy fields in `ForExpression` and `QuantifiedExpression` should be marked
`[deprecated = true]` while compatibility requires retaining them; see
`dmn-protobuf/src/main/proto/feel_parsed.proto`.

<a id="contents-section-4"></a>
### 2. Complete the DMN import contract — high priority

`Import` currently contains only namespace and name. DMN import semantics also require the equivalent of `locationURI` and `importType`, and the latter is visibly commented out.

This is not merely XML fidelity: import type and location affect model linking and therefore semantic correctness. Add both fields before implementing cross-model import resolution.

<a id="contents-section-5"></a>
### 3. Support recursive item definitions — high priority

`ItemDefinition` has components, but `ItemComponent` cannot itself contain components
in `dmn-protobuf/src/main/proto/model.proto`. The architecture already lists this as
a known gap.

Add something like:

```protobuf
repeated ItemComponent components = 5;
```

This is backward-compatible and necessary for nested context types and accurate structured-property analysis.

<a id="contents-section-6"></a>
### 4. Improve source-location granularity — medium/high priority

`SourceLocation` is attached mainly to `Node`, while most FEEL AST expressions and unary tests have no individual span. Consequently, later diagnostics often inherit the location of the surrounding decision or clause rather than the failing expression.

Consider optional locations on:

- `Expression`;
- `UnaryTestsExpression`;
- textual FEEL wrappers;
- type references where useful.

This would materially improve AP-020 observability and diagnostic precision. Keep these optional to avoid unnecessary population and memory cost in workflows that do not need diagnostics.

<a id="contents-section-7"></a>
### 5. Unify the diagnostics model — medium priority

The protobuf module defines a generic `Diagnostic` in
`dmn-protobuf/src/main/proto/common.proto`, but the parser and semantic analyzer
expose their own Java diagnostic records.

Additionally, ADR-0012 requires optional remediation, while the protobuf message contains no remediation or semantic-model path.

Choose one clear direction:

- make the protobuf diagnostic canonical by adding `model_path`, remediation/help information, and related locations; or
- document that diagnostics are stage-specific API objects and remove the claim that every phase produces the protobuf type.

I favor stage-specific diagnostics internally, normalized into one public diagnostic type by the future compiler API. That avoids prematurely freezing every stage’s needs into `dmn-protobuf`.

<a id="contents-section-8"></a>
### 6. Add resolved-reference annotations carefully — medium priority

Names, function calls, and decision-table references remain string-based, for
example `FunctionCall` in `dmn-protobuf/src/main/proto/feel_parsed.proto`. This is
acceptable before semantic analysis, but later passes should not repeatedly resolve
strings.

Add optional stable semantic symbol references to the analyzed copy of the model. Do not use final Runtime IR integer indices here: those belong in `dmn-runtime-ir` and may depend on lowering order.

<a id="contents-section-9"></a>
### 7. Clarify extension preservation — low priority

`ExtensionElement` stores a flat name/value/attribute representation in
`dmn-protobuf/src/main/proto/common.proto`, while its comment promises preservation
of unknown extensions for round-trip serialization. It cannot faithfully preserve
nested XML content, mixed text, or ordering.

Given the project’s “semantics over XML fidelity” principle, the best improvement is probably to narrow that promise rather than build a complete XML subtree model.

<a id="contents-section-10"></a>
## Improvements I would avoid

I would not:

- replace protobuf with handwritten Java domain objects;
- split every `.proto` file into its own Maven artifact;
- retain FEEL text and parsed AST simultaneously in every transformed node;
- place Runtime IR messages in `dmn-protobuf`;
- introduce generic maps for annotations or types;
- add a generic reflection-based traversal framework.

Those changes would either contradict the parent architecture or add complexity without a present payoff.

<a id="contents-section-11"></a>
## Recommended order

1. Compatibility/lint checks and schema tests.
2. Complete `Import`.
3. Recursive `ItemComponent`.
4. Source spans.
5. Decide the diagnostics boundary.
6. Add resolved semantic references when the next analysis/lowering pass needs them.

Build verification was not possible because Maven is not installed or available on this environment’s `PATH`; the assessment is based on the schemas, POMs, architecture specification, and current consumers.

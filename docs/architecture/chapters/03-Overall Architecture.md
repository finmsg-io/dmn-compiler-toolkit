# Chapter 3 — Overall Architecture [PARTIALLY IMPLEMENTED]

## 3.1 Pipeline

```text
DMN XML
   │
   ▼
XML Frontend                 implemented
   │
   ▼
Semantic Model              implemented
   │
   ▼
FEEL Parsing Pass           implemented
   │
   ▼
Parsed Model                implemented
   │
   ▼
Semantic Analysis           implemented for the current single-model scope
   │
   ▼
Optimization                future
   │
   ▼
Runtime IR                  future
   │
   ▼
Code Generation             future
   │
   ▼
Runtime                     future
```

Each stage has a stable protobuf input/output boundary and treats its input as immutable.

## 3.2 Implemented transformations

| Stage | Input | Output | Status |
| --- | --- | --- | --- |
| XML frontend | DMN XML | `Definitions` with FEEL text | Implemented for the current supported DMN subset |
| FEEL parser | Semantic `Definitions` | copied `Definitions` with parsed FEEL | Implemented |
| Semantic pipeline | parsed `Definitions` | typed model, compilation order, and diagnostics | Implemented for the current single-model scope |
| Optimizer | validated model | optimized model | Future |
| Runtime builder | optimized model | Runtime IR | Future |
| Code generator | Runtime IR | target code | Future |

## 3.3 Representation strategy

The semantic and parsed compiler models share the same protobuf schema. Replaceable nodes use a `oneof`:

```text
text representation  →  parsed representation
```

The XML frontend selects the text branch. `DmnFeelParser` creates a copied model and selects the parsed branch only for successfully parsed nodes. The original semantic model remains unchanged.

## 3.4 Dependency direction

```text
dmn-semantic-analysis
        │
        ▼
dmn-protobuf

dmn-feel-parser
        │
        ▼
dmn-protobuf

dmn-frontend-xml
        │
        ▼
dmn-protobuf
```

The parser and XML frontend are test-scoped dependencies of semantic-analysis integration tests; they are not production dependencies of the analyzer.

## 3.5 Current semantic analysis

Implemented:

- global input, decision, and BKM declarations
- requirement-aware decision scopes
- BKM parameter scopes
- sequential context-entry scopes
- FEEL name resolution
- structured item-definition property checks
- duplicate, ambiguous, unavailable, and unknown-name diagnostics
- named-type resolution and declared-type validation
- FEEL expression and boxed-expression type inference
- unary/binary operator and built-in function validation
- decision, decision-table, BKM, item-definition, and decision-service validation
- DRG dependency validation, cycle detection, and deterministic compilation order

Not yet implemented:

- persistent resolved-reference IDs
- import and cross-model linking
- Runtime IR lowering

## 3.6 Runtime boundary

Runtime IR and generators must remain independent from XML, VTD-XML, ANTLR, source-text nodes, and frontend diagnostics.

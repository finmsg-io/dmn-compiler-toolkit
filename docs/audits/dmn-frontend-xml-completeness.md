# DMN XML frontend completeness audit

Status: audited 2026-08-01 against the current protobuf model and the reader/writer test suite.

## Conclusion

`dmn-frontend-xml` is compiler-grade complete for the XML-representable portion of the current
`dmn-protobuf` core model. QName type references are namespace-safe in both directions. The
module should not yet be called complete for the entire DMN specification because broader
model extensions remain intentionally outside the current protobuf subset.

The reader and writer currently round-trip definitions metadata and namespaces, imports,
item definitions and constraints, all five modeled DRG element variants, requirements,
decision services, decision tables, invocations, and the modeled boxed-expression variants.
Node documentation and structured extension elements are also preserved.

## Coverage matrix

| Area | Reader | Writer | Round trip | Status |
| --- | --- | --- | --- | --- |
| Definitions attributes and DMN version | yes | yes | yes | complete |
| Namespace declarations and prefixed DMN | yes | yes | yes | complete |
| Imports | yes | yes | yes | complete |
| Item definitions, components, collections, constraints | yes | yes | yes | complete |
| Input data and information items | yes | yes | yes | complete |
| Decisions and requirements | yes | yes | yes | complete for modeled fields |
| Business knowledge models | yes | yes | yes | complete for modeled fields |
| Knowledge sources | yes | yes | yes | complete for modeled fields |
| Decision services | yes | yes | yes | complete |
| Decision tables, rules, annotations | yes | yes | yes | complete |
| Literal FEEL and invocations | yes | yes | yes | complete for text representation |
| Context, list, relation, boxed function | yes | yes | yes | complete for text representation |
| Documentation and structured extensions | yes | yes | yes | one-level extension structure |
| Source locations | opt-in | compiler metadata | not applicable | complete |
| QName type-reference namespace | yes | yes | yes | complete |

Parsed FEEL, inferred types, and resolved semantic bindings are compiler products rather than
source XML. Writers correctly reject parsed-only values when source text is unavailable.

## Remaining implementation gaps

### Completed — QName type-reference namespace preservation

`TypeReferenceReader` resolves values such as `risk:Applicant` in element scope and stores
both `Applicant` and the namespace URI. The writer reuses an existing prefix or declares a
collision-free prefix when serializing the named reference.

### Completed — Consistent unsupported-content diagnostics

Unknown DMN-namespace children produce `DMN-XML-004` throughout the reader hierarchy. Known
intentionally unmodeled metadata is explicit, and foreign-namespace extension content remains
permitted or preserved according to its structural location.

### Completed — Conformance and hardening matrix

Tests cover supported DMN 1.2 through 1.6 vocabulary namespaces, QName scope/shadowing,
malformed and oversized input, exact byte limits, DTD/XXE declarations in UTF-8 and UTF-16,
configurable depth limits, and semantic read-write-read conformance. The remaining completion
gate is the complete multi-module reactor.

## Model-extension boundary

The following DMN content is not representable in the current protobuf model and requires a
model-design decision before frontend code should be added:

- DMNDI diagram interchange;
- artifacts and associations;
- organization units and performance indicators;
- decision questions, allowed answers, and other business-context metadata;
- deeper arbitrary XML extension trees beyond the current one-level structured representation.

These are extensions to the semantic model, not reader/writer glue-code defects.

## Completion gate

The current frontend subset is complete: the conformance matrix and complete six-module reactor
pass. Full DMN-spec completeness additionally requires an
explicit decision about each model-extension item above.

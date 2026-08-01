# DMN XML frontend completeness audit

Status: audited 2026-08-01 against the current protobuf model and the reader/writer test suite.

## Conclusion

`dmn-frontend-xml` is feature-complete for the XML-representable portion of the current
`dmn-protobuf` core model. QName type references are namespace-safe in both directions. The
module should not yet be called complete for the entire DMN specification because unsupported
content diagnostics and the conformance/security matrix remain incomplete.

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

### P1 — Consistent unsupported-content diagnostics

Unsupported decision logic produces `DMN-XML-004`, but several other readers still ignore
unknown DMN-namespace children. Foreign-namespace extension content should remain permitted;
unknown content in the DMN namespace should be reported consistently.

### P1 — Conformance and hardening matrix

Add representative DMN 1.1 through 1.5 fixtures, QName type-reference cases, namespace
shadowing cases, malformed and oversized input cases, and read-write-read conformance tests.
Run the complete multi-module reactor after this matrix is in place.

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

The current frontend subset can be declared complete after unsupported DMN content is diagnosed
consistently, the conformance matrix passes, and the full reactor succeeds. Full DMN-spec completeness additionally requires an
explicit decision about each model-extension item above.

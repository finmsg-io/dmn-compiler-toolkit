# Current TODOs for `dmn-frontend-xml`

This list supersedes the original gap list. The reader/writer is now symmetric for the
XML-representable portion of the current protobuf model.

## Blocking completion items

- [x] Resolve QName `typeRef` prefixes in element scope and store `NamedTypeReference.namespace`.
- [x] Select or declare the correct prefix when writing a namespace-qualified named type.
- [x] Report unknown DMN-namespace children consistently instead of silently skipping them.
- [ ] Add multi-version, namespace-shadowing, malformed-encoding, DTD/XXE, and deep-nesting tests.
- [ ] Run the complete reactor and publish the supported-subset contract.

## Completed

- [x] Namespace-aware dispatch and scoped namespace resolution.
- [x] Correct import fields and cross-model-relevant metadata.
- [x] Structured read results, diagnostics, system IDs, and bounded input.
- [x] Opt-in line, column, and byte-offset source locations.
- [x] Reader/writer coverage for all modeled DRG elements and decision logic.
- [x] Item definitions, components, collections, and constraints.
- [x] Documentation and one-level structured extension preservation.
- [x] DMN version/root namespace round trips and prefixed-DMN output.
- [x] Semantic read-write-read tests; 26 XML frontend tests currently pass.

## Requires protobuf/model extension

- [ ] Decide whether to model DMNDI, artifacts/associations, organization units, performance
  indicators, decision questions/allowed answers, and additional expression/requirement IDs.
- [ ] Decide whether extension elements must preserve arbitrary-depth XML and mixed content.
- [ ] Decide whether recursive item components are required.

## Architectural constraints

- The frontend creates FEEL text representations only.
- Parsed/inferred compiler data is not serialized unless source text is retained.
- FEEL parsing remains in `dmn-feel-parser`.
- Import loading and semantic resolution remain outside the XML frontend.
- XML implementation types must not escape into downstream compiler stages.

See [the completeness audit](../../audits/dmn-frontend-xml-completeness.md).

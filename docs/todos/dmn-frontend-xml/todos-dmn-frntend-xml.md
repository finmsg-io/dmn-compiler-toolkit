# Known gaps in `dmn-frontend-xml`

The frontend now reads the Traffic Violation model, produces text-based FEEL nodes for the parser stage, and contains an initial `DmnWriter` with individual writer classes.

## Highest priority

1. Complete writer coverage and add semantic read/write/read round-trip tests.
2. Populate `SourceLocation` from the XML cursor.
3. Complete invocation and binding coverage.
4. Complete import and cross-namespace handling.
5. Add negative and security-focused XML tests.

## Reader completeness

- Verify all invocation callable and binding forms.
- Verify decision services, BKMs, knowledge sources, and authority requirements with dedicated fixtures.
- Complete import type, location URI, namespace resolution, and imported-model linking.
- Decide whether recursive item components require a protobuf schema extension.
- Complete `defaultOutputEntry`, annotations, and remaining clause attributes.
- Represent or deliberately document decision questions, allowed answers, and expression/requirement IDs.
- Preserve documentation, mixed content, extension elements, and vendor namespaces.

## Writer completeness

The writer infrastructure is no longer missing, but coverage is partial.

- Cover all supported readers with corresponding writers.
- Preserve namespaces and DMN version correctly.
- Cover decision tables, boxed expressions, invocations, BKMs, services, imports, and extensions.
- Add XML escaping and whitespace tests.
- Add read → write → read semantic-equivalence tests.
- Define expected behavior when the model contains parsed FEEL rather than text.

## Diagnostics and validation

- Populate line, column, offset, length, and system ID where available.
- Introduce a consistent policy for unsupported XML.
- Validate required children and attributes comprehensively.
- Detect duplicate IDs and malformed references or delegate them explicitly to semantic analysis.
- Keep frontend errors separate from FEEL and semantic diagnostics.

## Testing and hardening

- Add focused tests for every reader and writer.
- Add namespace-prefix and multiple-version fixtures.
- Add malformed UTF-8, attributes, nesting, and numeric/boolean tests.
- Verify DTD and external-entity behavior.
- Add resource limits for oversized and deeply nested XML.
- Add large-model allocation and throughput benchmarks.

## Architectural constraints

- The frontend creates text representations only.
- FEEL parsing belongs to `dmn-feel-parser`.
- Reference and type resolution belong to `dmn-semantic-analysis`.
- Shared readers and writers must remain stateless and thread-safe.
- XML implementation types must not escape into later compiler stages.


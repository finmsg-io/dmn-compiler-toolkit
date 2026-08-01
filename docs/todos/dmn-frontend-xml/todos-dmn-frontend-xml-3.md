# XML stabilization plan — implementation status

The decision to stabilize `dmn-frontend-xml` before continuing deeper Runtime IR work was
correct. The critical stabilization program is substantially complete.

## Completed sequence

1. [x] Extended and corrected import schema/reader/writer mappings.
2. [x] Made namespace lookup and reader dispatch namespace-correct.
3. [x] Added stable bounded read APIs, structured results, and diagnostics.
4. [x] Added opt-in source-location capture.
5. [x] Completed reader/writer symmetry for the current protobuf-supported subset.
6. [x] Preserved documentation, structured extensions, root namespaces, and DMN versions.
7. [x] Added structured item definitions/constraints and prefixed-DMN writing.
8. [x] Audited protobuf-to-reader/writer coverage.

## Remaining frontend gate

1. [x] Preserve QName `typeRef` namespaces during reading and writing.
2. [x] Make unsupported-content diagnostics consistent across every reader.
3. [x] Complete the multi-version, conformance, namespace-shadowing, and security matrix.
4. [ ] Run the full Maven reactor and publish the supported-subset boundary.

## Compiler glue after the gate

```text
DmnXmlReader / DmnReadResult
    → import source/model resolver
    → DmnFeelParser / DmnFeelParseResult
    → linked DmnSemanticAnalyzer result
    → RuntimeIrLowerer
    → RuntimeModel
```

The glue layer still needs a public compiler facade that aggregates phase-aware diagnostics,
stops lowering on errors, preserves source locations, resolves imports outside semantic
analysis, and passes deterministically ordered linked models to Runtime IR.

The next code task is the complete Maven reactor and supported-subset declaration. Runtime IR expression lowering
can resume after the frontend completion gate, without waiting for non-core model extensions
such as DMNDI or formatting-preserving XML round trips.

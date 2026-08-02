# P1.4 — Diagnose invalid import structures deterministically

Status: done
Owner: compiler team
Requirements: [AR-002](../architecture/architecture-spec.md#most-important-requirements), [AR-005](../architecture/architecture-spec.md#most-important-requirements), [AR-006](../architecture/architecture-spec.md#most-important-requirements), [AR-008](../architecture/architecture-spec.md#most-important-requirements)
Milestone: P1 — Compiler facade and model resolution

## Outcome

Given a root `DmnSource`, transitive model loading detects missing, duplicate,
ambiguous, and cyclic import structures and reports them through an immutable,
deterministically ordered result. Expected import-graph problems do not escape as
generic loader exceptions. Valid models already loaded before a problem remain
available when doing so is safe and explicitly represented by the result contract.

## In scope

- Detect a location-addressable import that cannot be resolved.
- Detect a resolution result containing more than one candidate.
- Detect duplicate stable source identities when their source contents or model
  identities conflict.
- Detect direct and transitive import cycles.
- Preserve import edges involved in cycles for diagnostic and tooling use.
- Introduce stable import-structure diagnostic codes.
- Return diagnostics in deterministic order independent of resolver registration,
  map iteration, or graph traversal details.
- Define whether each invalid structure prevents traversal of the affected edge or
  invalidates the complete load result.
- Add focused fixtures for missing, ambiguous, duplicate, self-cycle, multi-model
  cycle, and repeated equivalent imports.

## Non-goals

- Namespace and model-name fallback when `locationURI` is absent.
- The complete shared diagnostic contract with normalized phase, severity,
  source/model identity, path, and source location; that belongs to P1.5.
- XML, FEEL, semantic-analysis, or Runtime IR diagnostics.
- Cross-model semantic dependency-cycle detection.
- Recovery from malformed DMN XML.
- Durable diagnostic-code compatibility guarantees beyond the P1 compiler API.
- Compiler facade or whole-model semantic-result integration from P1.6 and P1.7.

## Architectural constraints

- Detection remains in `dmn-compiler`; resolver implementations must not acquire
  graph traversal or compiler orchestration responsibilities.
- Existing `DmnModelResolver` implementations remain policy adapters and return
  deterministic resolution candidates.
- Loader inputs and returned models, edges, and diagnostics are immutable.
- Diagnostic and graph ordering satisfies AR-002 and must not depend on map or
  resolver registration order.
- Cycle detection uses stable `DmnSourceId` identity and must terminate within the
  limits introduced by P1.3.
- Import-graph failures are data errors represented by result contracts, not
  unchecked exceptions; exceptions remain appropriate for violated internal
  invariants.
- The design must remain extensible to the richer P1.5 diagnostic contract without
  changing the meaning of P1.4 diagnostic codes.

## Affected modules and contracts

| Module/contract | Expected change |
| --- | --- |
| `dmn-compiler` | Add import-structure detection and deterministic diagnostic collection |
| `DmnModelLoader` | Continue traversal where safe and report expected graph failures through its result |
| `DmnModelLoadResult` | Expose immutable, deterministically ordered import diagnostics and explicit validity |
| `DmnResolutionResult` | Represent zero, one, or multiple deterministic candidates without losing request context |
| `DmnModelResolver` | Clarify candidate ordering and ambiguity contract |
| `DmnImportEdge` | Preserve resolved and cycle-closing relationships needed for diagnostics |
| Import diagnostic contract | Define stable codes and graph-specific context pending P1.5 enrichment |
| `DmnModelLoaderTest` | Add focused invalid-graph acceptance scenarios |

Suggested initial diagnostic codes:

| Code | Meaning |
| --- | --- |
| `DMN-IMPORT-MISSING` | No source resolved for a location-addressable import |
| `DMN-IMPORT-AMBIGUOUS` | More than one source candidate resolved for one import |
| `DMN-IMPORT-DUPLICATE` | Conflicting sources or model identities occupy the same logical identity |
| `DMN-IMPORT-CYCLE` | An import edge closes a direct or transitive cycle |

## Acceptance scenarios

1. Given an import whose location cannot be resolved, when the graph is loaded,
   then the result contains one `DMN-IMPORT-MISSING` diagnostic identifying the
   importing source and import request.

2. Given a resolver that returns multiple candidates for one request, when the
   graph is loaded, then no candidate is chosen implicitly and one
   `DMN-IMPORT-AMBIGUOUS` diagnostic lists the candidates in stable source-ID order.

3. Given two imports that resolve to the same `DmnSourceId` with identical content,
   when the graph is loaded, then the source is parsed once and no duplicate
   diagnostic is produced.

4. Given conflicting source content for one stable `DmnSourceId`, when the graph is
   loaded, then one `DMN-IMPORT-DUPLICATE` diagnostic is produced and the conflict
   is not silently resolved by traversal order.

5. Given a model that imports itself, when the graph is loaded, then traversal
   terminates, the closing edge is preserved, and one `DMN-IMPORT-CYCLE`
   diagnostic contains the canonical cycle path.

6. Given `A -> B -> C -> A`, when the graph is loaded, then one cycle diagnostic
   reports the stable canonical path `A -> B -> C -> A`.

7. Given a diamond graph with a shared leaf but no cycle, when the graph is loaded,
   then the leaf is parsed once and no cycle or duplicate diagnostic is produced.

8. Given multiple independent invalid imports, when the graph is loaded, then all
   safely discoverable diagnostics are returned rather than only the first error.

9. Reordering source insertion, resolver registration, or import discovery produces
   byte-for-byte equivalent diagnostic codes, context, candidate ordering, cycle
   paths, models, and import edges.

10. Exceeding source-count or depth limits retains the existing deterministic P1.3
    behavior and is not misclassified as an import cycle.

## Open decisions

Resolved for this slice:

- duplicate detection covers conflicting bytes for one `DmnSourceId` and duplicate
  namespace/model-name identities across distinct loaded sources;
- P1.4 uses the import-specific `DmnImportDiagnostic`; P1.5 will enrich it with the
  shared compiler diagnostic fields;
- invalid results retain the safely loaded immutable partial graph and expose
  `isValid()` and `hasErrors()`;
- cycle paths preserve edge direction and rotate to the lexicographically smallest
  source identity;
- traversal continues across independent branches and stops at an invalid or
  already-active edge;
- location-less fallback resolution remains deferred.

## Verification plan

- Focused: `mvn -B -ntp -pl dmn-compiler test`
- Test class: `DmnModelLoaderTest` or a dedicated
  `DmnImportStructureDiagnosticsTest`
- Downstream: compiler only; no runtime contract changes are expected
- Full reactor: required — loader and result contracts are public and the compiler
  depends on the XML frontend
- Documentation:
    - update `docs/development-plan.md`;
    - update `docs/modules.md`;
    - update `docs/todos/dmn-compiler.md`;
    - update the compiler architecture chapter if result contracts change;
    - run `python -m mkdocs build --strict`;
    - run `git diff --check`.

## Completion evidence

- Tests:
    - missing import;
    - ambiguous resolution;
    - equivalent repeated source;
    - conflicting duplicate identity;
    - self-cycle;
    - transitive cycle;
    - independent-error aggregation;
    - deterministic ordering under reordered inputs.

- Documentation/API:
    - `DmnModelLoadResult`;
    - import diagnostic contract;
    - `DmnModelResolver` and `DmnResolutionResult` contract documentation;
    - P1.4 development-plan evidence entry.

- ADR:
    - [ADR-0011 — Deterministic compilation](../architecture/adr/adr-0011-deterministic-compilation.md)
    - [ADR-0012 — Diagnostics as first-class objects](../architecture/adr/adr-0012-diagnostics-as-first-class-objects.md)
    - new ADR not required unless P1.4 changes the accepted diagnostic architecture.

- Benchmark/artifact: not applicable.

## Result

Implemented `DMN-IMPORT-MISSING`, `DMN-IMPORT-AMBIGUOUS`,
`DMN-IMPORT-DUPLICATE`, and `DMN-IMPORT-CYCLE` through immutable
`DmnImportDiagnostic` values. `DmnModelLoadResult` now retains deterministic models,
edges, and diagnostics and explicitly reports validity. Independent invalid branches
aggregate; ambiguous, conflicting, and active-cycle edges stop locally. Cycle-closing
edges remain in the graph and cycle paths are canonical.

Evidence: all 14 `DmnModelLoaderTest` scenarios and all 27 compiler tests pass; the
complete eight-project Maven reactor passes. `git diff --check` and strict MkDocs
validation pass. No new ADR was required. Phase, severity, source location, and the
shared compiler diagnostic shape remain intentionally deferred to P1.5.

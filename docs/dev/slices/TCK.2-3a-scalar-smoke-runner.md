# TCK.2–3a — Establish a scalar TCK smoke runner

## Table of contents

- [Outcome](#outcome)
- [In scope](#in-scope)
- [Non-goals](#non-goals)
- [Architectural constraints](#architectural-constraints)
- [Implemented contracts](#implemented-contracts)
- [Affected modules and contracts](#affected-modules-and-contracts)
- [Acceptance scenarios](#acceptance-scenarios)
- [Resolved decisions](#resolved-decisions)
- [Delivery sequence](#delivery-sequence)
- [Verification plan](#verification-plan)
- [Completion evidence](#completion-evidence)
- [Known conformance evidence](#known-conformance-evidence)
- [Result](#result)

Status: done  
Owner: compiler and runtime team  
Requirements: [AR-002](../../architecture/architecture-spec.md#most-important-requirements), [AR-003](../../architecture/architecture-spec.md#most-important-requirements), [AR-007](../../architecture/architecture-spec.md#most-important-requirements), [AR-012](../../architecture/architecture-spec.md#most-important-requirements)  
Track: DMN conformance accelerator, implementing the first executable portion of TCK.2 and TCK.3 from [IDEA-001](../../ideation/IDEA-001-dmn-conformance-accelerator.md)

## Outcome

A separate `dmn-tck-runner` test-infrastructure module reads scalar inputs and expected named
decision results from TCK-style `*-test-01.xml`, compiles the adjacent DMN model through the public
`DmnCompiler` facade, binds inputs to temporary Runtime IR slots by DMN name, evaluates the model
with `DmnRuntime`, and exposes actual decision values for deterministic assertions. A small
repository-owned offline fixture supplies the first passing decimal smoke baseline.

## In scope

- Add `dmn-tck-runner` as a separate Maven module with production dependencies on `dmn-compiler`
  and `dmn-runtime`.
- Decode TCK `testCases`, `testCase`, `inputNode`, `resultNode`, `expected`, and scalar `value`
  structures without coupling the DMN frontend to test-case XML.
- Preserve decimal precision with `BigDecimal`.
- Decode canonical null, boolean, decimal/integer/double, and string test values.
- Reject malformed cases, duplicate names, invalid booleans, and unsupported scalar types
  explicitly.
- Compile each tested DMN through the complete XML, FEEL, semantic-analysis, Runtime IR lowering,
  and optimization pipeline.
- Map input and decision names to Runtime IR slots inside the TCK module only.
- Evaluate through the Runtime IR interpreter and return values keyed by expected decision name.
- Define JUnit dynamic tests from the XML cases rather than writing one Java test per case.
- Add an offline DMN and adjacent TCK-style test file containing two decimal cases.
- Document the runner's current capability boundary and temporary adapter debt.

## Non-goals

- Pinning or checking out an external DMN TCK revision; that remains TCK.1.
- Discovering or classifying the complete external catalogue.
- Claiming DMN or TCK conformance from the smoke result.
- Lists, contexts, ranges, temporal values, durations, or special FEEL values.
- FEEL-aware structural comparison beyond numeric scale-insensitive comparison in the smoke test.
- Capability manifests, unsupported-case policy, structured reports, baselines, or CI trends.
- Focused execution through `-Dtck.case` or other catalogue filters.
- Imported TCK model sets or external functions.
- A public name-based compiled-model or runtime API.
- Generated-Java or multi-backend execution.

## Architectural constraints

- Production compiler, frontend, semantic, Runtime IR, and runtime modules must not depend on the
  TCK runner.
- TCK test-case XML is decoded by a purpose-built namespace-aware reader with external DTD and
  schema access disabled.
- DMN compilation and execution must use existing public stage boundaries; the runner must not
  recreate compiler stages.
- The runtime remains independent of DMN XML and semantic protobuf traversal.
- Name-to-slot adaptation remains internal to `dmn-tck-runner` and is temporary until P4 supplies a
  stable public named-evaluation API.
- Unknown or ambiguous names and compilation failures fail explicitly; no unexpected failure is
  classified as unsupported.
- Repository-owned smoke tests remain deterministic, offline, and fast enough for the normal Maven
  test lifecycle.

## Implemented contracts

```java
public record TckValue(Kind kind, Object value) {
  enum Kind { NULL, BOOLEAN, NUMBER, STRING }
  Object runtimeValue();
}
```

```java
public record TckTestCase(
    String id,
    String name,
    Map<String, TckValue> inputs,
    Map<String, TckValue> expectedResults) { }
```

```java
public final class TckTestCaseReader {
  List<TckTestCase> read(Path path) throws IOException;
  List<TckTestCase> read(InputStream input) throws IOException;
}
```

```java
public final class DmnToolkitTckEngine {
  TckExecutionResult execute(Path modelPath, TckTestCase testCase) throws IOException;
}
```

These contracts are initial conformance-infrastructure contracts, not yet a promised public API.

## Affected modules and contracts

| Module or contract | Change |
| --- | --- |
| Root reactor POM | Register `dmn-tck-runner` after its compiler and runtime dependencies |
| `dmn-tck-runner` | Add isolated conformance infrastructure module |
| `TckValue` | Represent the canonical scalar subset without decimal precision loss |
| `TckTestCaseReader` | Securely decode TCK-style scalar test-case XML |
| `DmnToolkitTckEngine` | Adapt named cases to compiler and interpreter contracts |
| `TckExecutionResult` | Return actual values keyed by requested decision name |
| Smoke resources | Add one adjacent DMN/test XML pair with two cases |
| Root README | List the new module |
| Runner README | Record execution command, supported subset, and deferred scope |

## Acceptance scenarios

1. A TCK-style XML document decodes multiple test cases in source order.
2. String, canonical boolean forms, null, and decimal values decode into explicit value kinds.
3. Nested `resultNode/expected/value` is decoded as a named expected result.
4. Decimals retain `BigDecimal` precision and compare independently of scale.
5. A case-defined input name binds to the matching compiled DMN input slot.
6. A case-defined result name selects the matching compiled decision result slot.
7. Each case compiles the adjacent DMN through `DmnCompiler` and evaluates through `DmnRuntime`.
8. Two decimal cases defined only in test XML become separate JUnit dynamic tests and pass.
9. Compilation diagnostics identify the TCK case and stop execution.
10. Unknown or ambiguous input and decision names fail explicitly.
11. The normal upstream compiler and runtime test suites remain green.
12. The module can run offline after project dependencies are available locally.

## Resolved decisions

- This task is serialized as `TCK.2–3a` because it implements the scalar parser and a minimal
  decimal interpreter path, but deliberately does not claim completion of all TCK.2/TCK.3 cases.
- A repository-owned smoke fixture precedes external-catalogue integration so failures can be
  attributed to the adapter independently of checkout and revision concerns.
- The test XML controls inputs and expected results; Java supplies only discovery, execution, and
  comparison mechanics.
- Test values use a small canonical wrapper rather than raw XML strings.
- `BigDecimal` is the number representation across decoding, runtime binding, and assertion.
- The current runtime's integer slot model is bridged by deterministic traversal of semantic models
  and executable DRG elements, matching Runtime IR allocation order.
- Duplicate names are rejected rather than guessed across model namespaces.
- Dynamic tests use stable TCK case IDs as their display names.
- External TCK content is not copied into the repository in this slice.

## Delivery sequence

1. Register the dedicated Maven module and its one-way dependencies.
2. Define canonical scalar values and immutable test-case records.
3. Implement secure namespace-aware test XML decoding.
4. Implement the temporary name-to-slot compiler/runtime adapter.
5. Add an adjacent DMN and TCK-style XML smoke fixture.
6. Execute cases as JUnit dynamic tests and compare decimal results.
7. Add focused decoder coverage for string, boolean, null, and nested decimal values.
8. Document scope and verify the module plus reactor.

## Verification plan

- Focused: `mvn -o -B -ntp -pl dmn-tck-runner test`
- Integrated: `mvn -o -B -ntp -pl dmn-tck-runner -am test`
- Tests: `TckTestCaseReaderTest` and `TckSmokeTest`
- Security: external DTD and schema access disabled in the test-case reader
- Architecture: no existing production module depends on `dmn-tck-runner`
- Hygiene: `git diff --check`

## Completion evidence

- `TckTestCaseReaderTest`: one parser scenario covering string, boolean, null, nested expected
  values, and decimal precision.
- `TckSmokeTest`: two dynamic decimal cases compiled and executed from one adjacent DMN/test pair.
- Focused runner result: 3 tests, 0 failures, 0 errors.
- Integrated result: all nine reactor projects succeeded after adding the module.
- Documentation: root module table and runner-specific README describe the boundary.
- ADR: no new ADR; the module follows IDEA-001 and existing compiler/runtime dependency decisions.
- External artifact: not applicable until TCK.1 pins an upstream revision.

## Known conformance evidence

The first fixture initially used the multi-word FEEL name `Applicant age`. Compilation failed in
the FEEL parsing phase because the current grammar parsed the first name component and rejected the
second. The passing baseline therefore uses `age` and `nextAge`, which are inside the currently
supported capability subset.

This is retained as evidence for a future FEEL-name conformance slice. It is not labeled an
unsupported TCK case by the runner because capability classification is not implemented until
TCK.5.

## Result

Implemented the first executable TCK-shaped vertical slice in `dmn-tck-runner`. Test inputs and
expected named decisions now come from TCK-style XML, while the adjacent model passes through the
real compiler facade, optimized Runtime IR, and interpreter. Canonical scalar decoding supports
null, booleans, decimals, and strings; the executable smoke baseline currently proves decimal input,
literal decision evaluation, stable case IDs, and scale-insensitive expected-value comparison.

Three focused tests pass, including two dynamically generated end-to-end cases, and the expanded
nine-project reactor passes. External catalogue pinning and discovery remain TCK.1; broader scalar
execution, structural and temporal values, classification, reports, and CI baselines remain the
subsequent TCK.3b–TCK.7 work.

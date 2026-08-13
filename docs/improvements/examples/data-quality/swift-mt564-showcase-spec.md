# DQ-002 - SWIFT MT564 data-quality reference showcase

Status: example - proposed

Profile: domain showcase

Depends on: DQ-001

Source proposal: [Data-quality BKM patterns, MT564 showcase, and authoring acceleration](../../data-quality-patterns-and-authoring.md)

## Outcome

Given a normalized representation of one selected SWIFT MT564 corporate-action notification
scenario, a DMN model imports reusable data-quality BKMs and produces a deterministic quality report
with traceable field, cross-field, sequence, and plausibility violations.

The showcase demonstrates a practical financial-message use case; it does not claim to parse,
validate, certify, or fully implement every MT564 variant.

The completed model is maintained as a repository reference showcase rather than a temporary test
fixture. Its canonical input/output contracts, scenario bundle, parity evidence, documentation, and
benchmark remain synchronized.

## Required decision before implementation

A domain owner must select and name:

- the exact corporate-action scenario and supported variants;
- the applicable SWIFT/ISO 15022 release and authoritative rule sources;
- the canonical mapping from parsed message fields/sequences into the DMN input context;
- which rule severities block downstream processing;
- whether fixtures may use only synthetic data or approved anonymized examples.

Until this is decided, field/tag examples are illustrative and acceptance is based on the agreed
canonical schema rather than assumptions about the standard.

## Required external input - currently needed

The normalized MT564 input is defined by protobufs used in another project. Their delivery is a
prerequisite for finalizing the authoritative model input and full DQM rules.

The supplied package must include or identify:

- the root protobuf message and all required imports;
- schema version/revision and compatibility policy;
- enum and field-presence semantics;
- representation of dates, times, decimal amounts, currencies, options, sequences, and identifiers;
- synthetic or approved anonymized example messages;
- the mapping/source used to interpret protobuf fields as MT564 concepts.

After receipt, the implementation must not manually duplicate the protobuf shape without a
conformance test. Protobuf-to-DMN mapping should be generated where practical or verified with
positive, missing-field, enum, repeated-field, oneof, unknown-field, and evolution fixtures.

## Phase 0 pilot boundary

The first implementation slice demonstrates the method by example and includes only:

- one reviewed MT564 corporate-action scenario;
- the minimum normalized input fields needed by the selected rules;
- a minimal stable `QualityViolation` contract;
- two or three imported reusable validation BKMs;
- one valid scenario and several focused invalid scenarios;
- interpreter and generated-Java execution parity;
- documented limitations, verification evidence, and authoring friction.

Suggested first rule mix:

1. one required identity/reference value;
2. one allowed event/function code rule;
3. one cross-field or chronological rule.

The precise fields and codes must come from the domain decision described above. Scoring, complete
option validation, large structures, Spark SQL parity, performance benchmarking, and authoring-tool
extraction follow only after the pilot succeeds.

## Phase 0 acceptance examples

1. Given the valid pilot fixture, when the imported-BKM model set is evaluated, then no selected-rule
   violation is returned.
2. Given each focused invalid fixture, when it is evaluated, then the expected stable rule ID, path,
   severity, and explanation are returned.
3. Given a fixture violating multiple selected rules, when it is evaluated, then all applicable
   violations are returned deterministically.
4. Given the same fixture and model set, when interpreter and generated Java execute it, then their
   structured results are equivalent.
5. Given a field or MT564 variant outside the pilot scope, when it is encountered, then the example
   does not imply that it was validated.
6. Given completion of the pilot, when the review occurs, then authoring friction and proposed next
   steps are recorded without automatically expanding scope.

## Observable acceptance examples

1. Given a valid normalized message for the selected scenario, when the root quality decision runs,
   then it returns no error violations and the configured acceptable disposition.
2. Given a missing corporate-action reference or other scenario-required identity, when validation
   runs, then the expected stable rule ID and canonical input path are reported.
3. Given an unsupported event/function code, when code-list validation runs, then the message is
   rejected or flagged according to the reviewed severity policy.
4. Given inconsistent event dates, when chronology rules run, then the report identifies both the
   violated relationship and the relevant paths.
5. Given a conditional option field that is absent for an option requiring it, when validation runs,
   then `requiresWhen` produces the scenario-specific violation.
6. Given duplicated option or sequence identifiers, when validation runs, then deterministic
   duplicate violations identify the repeated key.
7. Given multiple independent defects, when the model runs, then all applicable violations are
   returned; one critical violation cannot be hidden by an aggregate score.
8. Given malformed canonical input, when evaluation cannot apply domain rules safely, then input
   mapping/shape failure is distinguishable from a normal data-quality violation.
9. Given the same fixture, when interpreter and generated Java execute it, then reports are
   structurally equivalent.
10. Given an unsupported MT564 variant, when it is submitted, then the model returns or the adapter
    raises an explicit unsupported-scope outcome rather than a misleading clean report.
11. Given the reference showcase changes, when verification runs, then its scenario evidence,
    interpreter/generated-Java parity, documentation, and benchmark compilation remain valid.

## Constraints

- Accept normalized structured input; raw FIN parsing is outside this specification.
- Derive authoritative rules from the licensed/applicable standards and record rule traceability.
- Use synthetic or approved anonymized fixtures; never commit production financial messages.
- Separate generic BKMs, MT564 rule composition, and the root showcase model.
- Keep rule IDs stable once published; changes require migration/release notes.
- Make scoring secondary to explicit violations and blocking severity.
- Support interpreter and generated Java first; do not weaken semantics for Spark SQL portability.
- Avoid “SWIFT certified,” “complete validator,” or equivalent claims.

## Non-goals

- Parsing or authenticating raw SWIFT FIN messages.
- Network, transport, signature, authorization, or sanctions validation.
- Complete validation of every corporate-action event and sequence variant.
- Automatic repair or enrichment of messages.
- Replacing a licensed standards validator or domain review.
- Benchmarking throughput before correctness and scope are established.
- Treating one workstation's benchmark score as a universal capacity claim.

## Scenario bundle

The minimum bundle contains:

- one valid baseline;
- one focused case for each rule;
- one case at each date/amount boundary;
- missing, blank, malformed, unsupported-code, duplicate, and contradictory cases;
- at least three multi-violation cases;
- one explicitly unsupported variant;
- one maximum expected collection-size case and one excessive-size rejection case if inputs are
  untrusted.

Each scenario declares expected violation IDs, paths, severities, score/disposition if applicable,
and explanatory intent.

## Verification

- Domain review signs off the supported scenario, canonical schema, and rule catalogue.
- Every declared rule has positive, negative, and relevant boundary evidence.
- Multi-file import/BKM invocation is exercised through the public compiler facade.
- All scenarios run against interpreter and generated Java with structural parity.
- Invalid input, unsupported scope, compiler failure, and rule violations remain distinct outcomes.
- Model generation and scenario reports are deterministic on Windows and Linux.
- Documentation links each rule to its authoritative or project-policy source without copying
  restricted standards text.

## Evidence required for completion

- approved scenario and supported-scope statement;
- canonical normalized input and quality-report schemas;
- rule catalogue with IDs, source/rationale, severity, paths, and examples;
- synthetic scenario bundle and expected results;
- interpreter/generated-Java parity report;
- stable benchmark fixture contract consumed by DQ-004;
- explicit limitations and non-certification statement;
- authoring friction log used to evaluate DQ-003.

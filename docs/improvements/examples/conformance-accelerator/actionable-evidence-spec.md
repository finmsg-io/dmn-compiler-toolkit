# TCK-ACC-002 - Actionable and reusable conformance evidence

Status: example - proposed

Profile: conformance reporting and diagnosis

Depends on: TCK-ACC-001

Source proposal: [DMN conformance accelerator closure](../../dmn-conformance-accelerator-closure.md)

## Outcome

A contributor can reproduce and diagnose one conformance failure without running the full suite,
while releases and documentation consume deterministic evidence produced from the same backend-
neutral result model.

## Beneficiaries

- contributors implementing missing DMN or FEEL behavior;
- backend authors checking interpreter/generated-Java parity;
- release maintainers producing trustworthy conformance notes;
- users assessing exact supported scope rather than a context-free percentage.

## Observable acceptance examples

1. Given a stable TCK case identifier, when a contributor runs the documented Maven command, then
   only the matching model/case and selected backend execute and a no-match selection fails.
2. Given a compilation failure, when the result is emitted, then it identifies the compilation phase,
   diagnostics, source model, case identifier, backend, and exact reproduction command.
3. Given an execution or comparison failure, when the result is emitted, then expected and actual
   canonical values plus the first useful structural difference path are present.
4. Given the same semantic value from interpreter and generated Java, when both are compared with the
   expected result, then they use one shared FEEL-aware comparator and equality policy.
5. Given numerically different values beyond an explicitly justified tolerance, when comparison runs,
   then rounding does not silently make them equal.
6. Given one complete run, when reports are generated, then JSON is the canonical result and Markdown
   is a deterministic projection containing revision, denominator, exclusions, backend totals, and
   failure groups.
7. Given CI execution, when the suite completes or fails, then it uploads the structured TCK evidence
   and readable summary.
8. Given a pinned TCK revision update, when the new run is compared with the accepted baseline, then
   added, removed, newly passing, newly failing, and newly unsupported entries are reported.
9. Running report generation twice from the same raw result produces no second diff on Windows or
   Linux.

## Constraints

- Use the strict inventory and terminal classifications from TCK-ACC-001.
- Keep comparison policy backend-neutral and cover numeric, structural, temporal, null, and special
  FEEL values included by the declared suite.
- Store exact values or safe deterministic representations without relying on platform `toString()`.
- Generate human-readable reports from structured results; do not maintain counts independently.
- Retain per-run evidence in CI and version only accepted release or suite-revision summaries.
- Avoid adding a persistent service or database until file artifacts prove insufficient.
- Use “self-verified against pinned OMG DMN TCK revision,” not “certified.”

## Non-goals

- Fixing every conformance failure found by the report.
- Publicly ranking or comparing other DMN vendors.
- Providing a hosted conformance dashboard.
- Treating experimental/non-compliant TCK cases as part of the standard denominator.
- Turning the runner into a production dependency of the compiler or runtime.

## Unknowns and opportunities

- Whether JSON alone is sufficient or JUnit XML should also carry classification metadata
- The minimum structural-diff representation that remains useful across all FEEL value kinds
- Artifact retention duration for pull requests, main builds, and releases
- Opportunity: generate release-note and documentation conformance sections from the same artifact
- Opportunity: reuse backend adapters and comparator for Spark SQL and gRPC parity subsets
- Opportunity: group failures by DMN capability once the classification vocabulary is stable

## Skills and checks

- canonical FEEL value comparison
- compiler/runtime diagnostic attribution
- deterministic JSON and Markdown generation
- Maven case/backend/profile filtering
- CI artifacts and release evidence
- documentation claim review

## Verification

- Unit-test comparison behavior for arbitrary-precision numbers, null, lists, contexts, dates, times,
  date-times, durations, ordering rules, and structural mismatch paths.
- Test each terminal status and compiler phase using controlled fixtures.
- Reproduce a selected passing and failing case through the documented command.
- Generate JSON and Markdown twice and compare byte-for-byte output.
- Validate report totals against the strict catalogue inventory.
- Run interpreter and generated-Java adapters against the same selected cases and comparator.
- Verify CI uploads evidence even when a conformance case fails.
- Verify documentation projections contain no independently typed counts.

## Evidence required for completion

- versioned result schema and status vocabulary;
- tested shared comparator policy;
- focused reproduction commands and no-match behavior;
- deterministic JSON and Markdown example artifacts;
- CI artifact link and retention policy;
- baseline-diff example for a controlled suite change;
- updated conformance page derived from or linked to the canonical evidence.

## Completion decision

This specification is complete when a failure is independently reproducible and diagnosable from its
result record, and every published count can be traced to deterministic structured evidence.

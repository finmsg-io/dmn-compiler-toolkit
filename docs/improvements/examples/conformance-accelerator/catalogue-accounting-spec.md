# TCK-ACC-001 - Trustworthy TCK catalogue accounting

Status: example - proposed

Profile: conformance evidence integrity

Source proposal: [DMN conformance accelerator closure](../../dmn-conformance-accelerator-closure.md)

## Outcome

Maintainers can prove that every entry and test case in the declared pinned TCK denominator reached
exactly one recorded outcome. Missing models, invalid test definitions, compilation failures, and
unsupported capabilities cannot disappear from the report or produce a false 100% claim.

## Beneficiaries

- maintainers approving releases and compatibility statements;
- contributors diagnosing newly failing TCK cases;
- architects and adopters evaluating the scope of self-verified conformance;
- backend authors relying on TCK parity evidence.

## Observable acceptance examples

1. Given the pinned CL2 and CL3 catalogue, when discovery completes, then the report records the
   number of directories, test XML files, DMN files, decoded test cases, and backend executions.
2. Given a test XML file with no deterministically identifiable root DMN model, when discovery runs,
   then the entry is classified as invalid or unsupported according to explicit policy and is not
   silently omitted.
3. Given malformed TCK XML, when decoding fails, then the entry is classified as an invalid TCK case
   with its stable identifier, source path, and diagnostic.
4. Given a model that throws during compilation or returns unsuccessful compilation diagnostics,
   when the strict suite runs, then it records `COMPILATION_ERROR` and fails the conformance gate.
5. Given an approved unsupported capability, when the case is encountered, then its manifest entry,
   reason, owner, and tracking reference appear in the evidence and denominator summary.
6. Given a requested exact case filter that matches nothing, when the focused run starts, then it
   fails rather than reporting an empty successful run.
7. Given the same pinned corpus on Windows and Linux, when discovery runs, then model-root selection
   and inventory are identical.
8. Given a claimed backend with one skipped, unsupported, unaccounted, or failed in-scope case, when
   documentation is generated, then it cannot state 100% for that backend and denominator.

## Constraints

- Pin the suite revision and define the exact included catalogue roots.
- Treat non-compliant and experimental directories separately from the CL2/CL3 denominator.
- Preserve upstream files; do not patch expected values to make the suite green.
- Select a root model by a deterministic naming/metadata rule, never filesystem iteration order.
- Represent each catalogue entry exactly once even when it cannot produce executable test cases.
- Exclusions require a reviewed manifest entry; exceptions and diagnostics do not create exclusions.
- Keep the implementation cross-platform and independent of absolute developer paths.

## Non-goals

- Implementing every newly exposed unsupported DMN capability.
- Adding dashboards, a database, or a hosted conformance service.
- Comparing external DMN engines.
- Publishing an external certification claim.
- Redesigning the compiler pipeline.

## Unknowns and opportunities

- Whether the denominator is best expressed by catalogue roots plus revision or an explicit inventory
- How upstream entries without conventional adjacent models should be classified
- Whether the upstream catalogue provides authoritative version metadata per model
- Opportunity: reuse strict inventory accounting for future DMN/TCK revision upgrades
- Opportunity: expose deterministic catalogue discovery as a small reusable library component

## Skills and checks

- TCK catalogue and schema interpretation
- deterministic cross-platform filesystem discovery
- compiler phase diagnostics
- Maven/JUnit dynamic-test lifecycle
- generated documentation and evidence validation

## Verification

- Add fixtures for missing model, ambiguous root, invalid XML, empty case list, compilation exception,
  unsuccessful compilation, and explicit unsupported classification.
- Assert inventory and terminal-status conservation:

  ```text
  discovered entries = passed + failed + unsupported + invalid + skipped
  ```

- Run the complete pinned CL2/CL3 catalogue and independently compare discovered file/case counts.
- Run discovery on Windows and Linux and compare the machine-readable inventories.
- Verify an exact one-case filter and a no-match failure.
- Verify that removing or corrupting a fixture cannot leave the strict build green.

## Evidence required for completion

- declared suite revision, catalogue roots, denominator, and exclusion policy;
- machine-readable inventory and per-entry terminal outcomes;
- passing negative-path tests for every former silent `continue` condition;
- Windows/Linux deterministic-discovery evidence;
- corrected conformance counts and terminology derived from the verified inventory.

## Completion decision

This specification is complete only when every former silent path has an explicit outcome and the
strict build proves conservation of the declared catalogue. A green subset is insufficient.

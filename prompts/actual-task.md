# Actual task: full official-TCK compliance through DMNIR and generated Rust

Updated: 2026-11-17. Status: in progress; 1,018/3,407 passed under strict comparison.

## Goal and acceptance

Pass every case in the pinned official TCK through DMN compilation, portable DMNIR export, standalone Rust generation, compilation and execution. The required denominator is **3,407 cases**, verified against official TCK revision `5cd912f281831bc535e710aba435e16de198028a`. Discovery must include every numbered test XML file and honor declared model names and input namespaces. Count export rejections, generator rejections, skips and unexecuted cases as unmet coverage. Do not shrink the denominator or weaken expected results to improve the rate.

Completion requires a reproducible full-suite run on the delivered revision with all cases passing and no unexplained omissions. Passing the pinned suite establishes pinned-suite compliance, not proof of every DMN/FEEL behavior.

## Latest full Rust TCK result: 2026-11-17 (strict comparison)

Source: base revision `e028766` plus current working-tree changes. Suite: `5cd912f281831bc535e710aba435e16de198028a`.
Evidence: [case ledger](../target/rust-tck-number-3407-2026-11-17/rust-tck-accounting.tsv),
[manifest](../target/rust-tck-number-3407-2026-11-17/manifest.tsv),
[summary](../target/rust-tck-number-3407-2026-11-17/summary.json),
[provenance](../target/rust-tck-number-3407-2026-11-17/provenance.json),
[run log](../target/rust-tck-number-3407-run-2026-11-17.log).

| Outcome | Cases |
| --- | ---: |
| Passed | 1,018 |
| Export/harness rejection | 2,376 |
| Rust generator rejection | 0 |
| Rust build error | 0 |
| Rust execution error | 0 |
| Result failure | 13 |
| Total / unique accounted cases | 3,407 |

Pass rate **29.88%**. Strict baseline: 995/3,407; **+23 newly passing cases, zero regressions**.
Gains: 21 cases in `0058-feel-number-function`, plus two namespaced-input cases in `1160-multiple-imports`.
CL2: **110/116 passed, six failures**. CL3: **908/3,291 passed**.
Rejections: 2,351 unsupported export profile, 17 unsupported direct invocations,
five unsupported values, two artifact-validation failures, one source-validation failure.

Numeric comparisons now require exact decimal equality; the prior blanket rounding
could accept incorrect results. Historical 1,008/3,391 and 100% CL2 claims are not
current strict-comparison results. The 13 remaining mismatches involve loan/payment
calculations (12 cases) and fractional exponentiation (one case). Some expected values
have fewer digits than computed results; investigate numerical semantics and authoritative
TCK comparison rules rather than truncating runtime values to match fixtures.

The runner compiles 32 generated groups per batch with incremental compilation disabled
and saves batch logs. An earlier monolithic strict run exhausted disk and produced no
complete ledger; it is not a conformance result.

## Work method

1. Inspect existing reports, runner, source and working-tree changes before running expensive checks. Establish suite revision, code revision, commands and trustworthy case accounting; report unavailable evidence explicitly.
2. Trace representative rejected cases to concrete causes. Rank blocker groups by affected case count, dependencies, implementation cost and uncertainty. A case may have several blockers; potential coverage is not a measured gain.
3. Implement a tractable, high-impact group across the complete pipeline. Preserve expected semantics. Verify generated Rust against official expected outcomes, using Java parity as additional evidence.
4. Run focused tests during development. Reuse artifacts and builds. Run the full pinned end-to-end Rust TCK at meaningful integration checkpoints, before claiming completion, and at least at the end of every five-hour usage session before handover. Usage checkpoints also require current full-run numbers. Report the full denominator and delta against the previous full run; focused results or historical totals do not satisfy this requirement. Reserve capacity for these runs; reassess approaches that produce no demonstrated coverage gain.
5. After each increment report changed behavior, command/report paths, newly passing cases, regressions and remaining blockers. Distinguish focused results from full-suite results, historical from current evidence, and facts from hypotheses. Do not project a full-suite pass rate from a subset.
6. Keep this file concise: current goal, sourced baseline, next action and blockers. Store detailed evidence in existing canonical records. Remove superseded status prose; preserve unrelated user edits.

## Architecture and retained release requirements

Use the accepted typed, resolved, structured Protobuf DMNIR architecture in [ADR-0026](../docs/architecture/adr/adr-0026-structured-portable-dmnir.md) and the persistence boundary in [ADR-0025](../docs/architecture/adr/adr-0025-runtime-ir-persistence-and-compatibility-boundary.md). Reuse parsing and semantic analysis. Keep portable DMNIR distinct from process-local Runtime IR. Preserve exact numeric/temporal semantics, lexical scope, deterministic output, compatibility and bounded validation. Standalone Rust consumes the artifact without JVM services, DMN XML or FEEL parsing at runtime.

Release 1.2 also retains artifact-only DMN export (default DMN 1.6), DSL authoring/parity, compatibility and existing-backend regression requirements. Their completion is not established here. See the [work package](details/release-1.2-dmnir-work-package.md) for specifications and historical evidence. Its earlier limited Rust profile does not limit this current full pinned-TCK goal; historical profile completion is not task completion.

Follow [general instructions](generall-prompt.md), [guardrails](details/guardrails.md) and the [development method](../docs/dev/evidence-backed-spec-driven-development.md), subject to the user's current instructions. Usage barriers, updated 2026-11-15: weekly remaining <=7% OR five-hour remaining <=20%. At either barrier stop new implementation and finish the verification/TCK/handover checkpoint in the general instructions. Missing usage accounting is unavailable, not zero. These thresholds supersede historical rules. Avoid redundant verification and unsupported estimates of cost or completion dates. Do not publish, push or change release versions without authorization.

## Next action and limitations

Trace the 13 numerical mismatches and rank the remaining 2,376 exclusions from the current
manifest. Large rejected suites include arithmetic, membership, instance-of, temporal
functions, and lists. These are remaining case counts, not projected feature gains.
Full compliance remains **unmet: 2,389 cases do not pass**.

Focused verification passed: six catalogue-discovery tests, two test-value reader tests,
one compiler/adapter number-conversion test, three Rust runner tests, and a generated-runtime
regression covering exact number parsing and Unicode/empty literal replacement. A preceding
27-test Rust artifact run also passed; the final full run verifies the latest runtime.
Other backend full-suite revalidation and release 1.2 completion are not established.
Usage accounting is unavailable. Rust runs do not measure Spark native/fallback routing.
Detailed commands and caveats: [work package, section 52](details/release-1.2-dmnir-work-package.md#52-current-rust-tck-handover-2026-11-17-strict-3407-case-audit).

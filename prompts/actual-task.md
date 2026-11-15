# Actual task: full pinned-TCK compliance through DMNIR and generated Rust

Updated: 2026-11-15. Status: incomplete.

## Goal and acceptance

Pass every case in the pinned official TCK through DMN compilation, portable DMNIR export, standalone Rust generation, compilation and execution. The recorded denominator is 3,391 cases; verify the suite revision and runner completeness before treating it as authoritative. Count export rejections, generator rejections, skips and unexecuted cases as unmet coverage. Do not shrink the denominator or weaken expected results to improve the rate.

Completion requires a reproducible full-suite run on the delivered revision with all cases passing and no unexplained omissions. Passing the pinned suite establishes pinned-suite compliance, not proof of every DMN/FEEL behavior.

## Latest full TCK result: 2026-11-15

Full post-change run: [summary](../target/rust-tck-full-post-logical-2026-11-15/summary.json), [case outcomes](../target/rust-tck-full-post-logical-2026-11-15/rust-tck-accounting.tsv), [case delta](../target/rust-tck-full-post-logical-2026-11-15/delta-from-baseline.tsv), [export manifest](../target/rust-tck-full-post-logical-2026-11-15/manifest.tsv).

| Recorded outcome | Cases |
| --- | ---: |
| Passed | 99 |
| Export/harness rejection | 3,280 |
| Rust generator rejection | 12 |
| Rust build error | 0 |
| Rust execution error | 0 |
| Result failure | 0 |
| Total | 3,391 |

Pass rate: 2.92%, versus 79/3,391 (2.33%) before the logical-operand fix: 20 newly passing cases, zero regressions. All 3,391 case identities matched the baseline with no duplicates or omissions. Export diagnostics: 3,269 UNSUPPORTED_PROFILE and 11 INVOCATION_UNSUPPORTED. The latter are direct-invocation harness limitations, not demonstrated Java lowering failures. The zero Rust error counts apply only to cases reaching those stages.

Measured on the current uncommitted working tree; source hashes and pinned revision are in the summary. Logs: `target/rust-tck-full-post-logical-export-2026-11-15.log` and `target/rust-tck-full-post-logical-run-2026-11-15.log`; Java arguments: `target/rust-tck-full-post-logical-2026-11-15.args`. The runner and exporter remain untracked. Decision pruning and coarse expected-error handling remain harness limitations documented in the handover. These counts do not establish complete compliance.

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

## Handover: 2026-11-15

- Working tree is uncommitted, based on `7220498bb533808afe28422ebfdb0ffc601179c1`. Pinned TCK checkout verified as `20274cd2ba9cad805db6114f331c743f4b2603a1`.
- Corrected the untracked Rust runner: a successful process with no explicit case result cannot count as a pass; classify failures per case; retain execution logs; reject malformed rows; preserve existing output directories. Two regression tests pass.
- Re-exported all 3,391 cases and reran the corrected runner BEFORE the logical change: 79 passes, 3,300 export rejections, 12 Rust rejections, zero build/execution/result errors. Evidence: `target/rust-tck-audit-2026-11-15/` and `target/rust-tck-run-2026-11-15.log`.
- Implemented scalar non-boolean operands for AND/OR in the Java artifact validator and Rust generator/runtime. FEEL treats these operands as null; preserve short-circuit behavior. Schema fields/versions are unchanged. Older consumers may reject newly admitted signatures.
- AFTER the change: focused conjunction/disjunction suites passed 38/38, followed by the full pinned run above: 99/3,391 passed, 20 newly passing cases, zero regressions.
- Additional verification: 50 logical combinations checked in Java, DMN export/recompilation and generated Rust; codec reactor passed; complete Rust run passed 59 tests; full Maven reactor passed 655 tests with zero failures/errors/skips (`target/logical-scalar-full-reactor-2026-11-15.log`). Its completed Spark regression actions: native 3,034/3,153 (96.23%), fallback 119/3,153 (3.77%), unknown zero; 60 failed SQL actions separate. These are regression actions, not TCK cases. No new full Spark TCK result is claimed.
- Existing README/index cross-repository links fail documentation verification and strict MkDocs. Preserve unrelated README, glossary, index edits and untracked prompt PDFs. Do not reset/clean the working tree.

Next: target the larger export barriers. First-rejection counts: expression-node admission 1,251; null-type admission 482; type resolution 428; scalar-alias admission 410; constrained/missing-ID item definitions 310. These are failure locations, not proven single-feature coverage opportunities. All 12 Rust rejections cite bounded-constant-power admission. Audit expected-error handling before claiming complete negative-case conformance.

Exact commands, changed-file responsibilities and remaining caveats: [work-package handover, section 48](details/release-1.2-dmnir-work-package.md#48-current-rust-tck-handover-2026-11-15). Start there; historical initial-profile completion does not mean this task is complete.

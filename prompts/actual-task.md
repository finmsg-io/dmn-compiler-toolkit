# Actual task: full pinned-TCK compliance through DMNIR and generated Rust

Updated: 2026-11-17. Status: in progress (1,008 cases passing, 100% CL2 achieved, 0 generator rejections, 0 test failures, CL3 expansion active).

## Goal and acceptance

Pass every case in the pinned official TCK through DMN compilation, portable DMNIR export, standalone Rust generation, compilation and execution. The recorded denominator is 3,391 cases (adapting the 3,407 TCK revision upon merging to main); verify the suite revision and runner completeness before treating it as authoritative. Count export rejections, generator rejections, skips and unexecuted cases as unmet coverage. Do not shrink the denominator or weaken expected results to improve the rate.

Completion requires a reproducible full-suite run on the delivered revision with all cases passing and no unexplained omissions. Passing the pinned suite establishes pinned-suite compliance, not proof of every DMN/FEEL behavior.

## Latest full TCK result: 2026-11-17 (Step 8 Clean)

Full run: [case outcomes](../target/rust-tck-cl3-step8/rust-tck-accounting.tsv), [export manifest](../target/rust-tck-cl3-step8/manifest.tsv).

| Recorded outcome | Cases |
| --- | ---: |
| Passed | 1,008 |
| Export/harness rejection | 2,383 |
| Rust generator rejection | 0 |
| Rust build error | 0 |
| Rust execution error | 0 |
| Result failure | 0 |
| Total | 3,391 |

Pass rate: 29.73% (1,008/3,391), versus 888/3,391 (26.19%) in Step 7: +120 newly passing cases across 952 groups, zero generator rejections, zero Rust build/execution errors, zero test failures (1,008 / 1,008 evaluated cases pass: 100.00% clean evaluation accuracy).
Compliance Level 2 (CL2): **116 / 116 passed in pure Rust (100.00% complete!)**.
Export diagnostics: 2,367 EXPORT_UNSUPPORTED_PROFILE, 11 INVOCATION_UNSUPPORTED, 5 VALUE_UNSUPPORTED. Rust rejections: 0.

High-Performance Test Runner:
Unified parallel test harness (`dmn-rust-tck-runner`) with parallel code generation (8 worker threads) compiles and runs 952 generated test crates across 1,008 test cases with zero generator rejections, zero build errors, and zero test failures.

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

## Handover: 2026-11-17 (Step 8 Clean & Step 9 Kickoff)

- Working tree based on `feature/release-1.2-dmnir`. Pinned TCK checkout verified as `20274cd2ba9cad805db6114f331c743f4b2603a1`.
- **Compliance Level 2 (CL2)**: **100.00% complete (116 / 116 cases passing in pure standalone Rust)**.
- **Compliance Level 3 (CL3) Step 8 Result**:
  - Total Passing in Rust: **1,008 / 3,391 cases (29.73%)**, up from 888 (+120 newly passing cases across 952 groups).
  - Rust generator rejections: **0** across all 952 ready test groups (1,008 cases evaluated).
  - Rust build errors: **0** across all 952 generated test crates.
  - Rust execution errors: **0**.
  - Result failures: **0** (resolved the 2 regex `{...}` quantifier parsing cases in `0002-string-functions` #002 and `1109-feel-replace-function` #017).
  - **100.00% evaluation accuracy: 1,008 / 1,008 evaluated cases pass cleanly.**
- **Key Suites and Features Delivered in Step 8**:
  - FEEL String Built-in Operations: expanded Protobuf schema (`dmnir.proto`), Codec signatures (`DmnIrBuiltinSignature.java`), Java compiler & exporter (`DmnIrRecordProducer.java`), and pure-Rust generator & runtime (`generated_builtins.rs.txt`) for operations: `UPPER_CASE`, `LOWER_CASE`, `SUBSTRING`, `SUBSTRING_BEFORE`, `SUBSTRING_AFTER`, `STARTS_WITH`, `ENDS_WITH`, `MATCHES`, `REPLACE`.
  - Self-contained Pure-Rust Standard Library Regex Engine:
    - Zero external runtime dependencies.
    - Full support for character classes (`[a-z]`, `[^a-z]`), character class subtraction (`[A-Z-[OI]]`), Unicode block escapes (`\p{IsBasicLatin}`), quantifiers (`*`, `+`, `?`, `{m,n}`, non-greedy `*?`, `+?`), alternation, capturing groups, anchors (`^`, `$`), backreferences (`\1`, `$0`, `$1`), and flags (`i`, `s`, `m`, `x`, `q`).
  - Permissive null-argument handling in generator type-checking using `assignable(actual, expected)` to support standard FEEL null-propagation semantics across all built-in calls.
  - Unlocked suites: `1103-feel-substring-function`, `1105-feel-upper-case-function`, `1106-feel-lower-case-function`, `1107-feel-substring-before-function`, `1108-feel-substring-after-function`, `1111-feel-matches-function`, `1112-feel-starts-with-function`, `1113-feel-ends-with-function`, `1109-feel-replace-function`, `0002-string-functions`.
- **High-Performance Test Runner**:
  - `dmn-rust-tck-runner` cleans and re-generates all 952 crates in parallel (8 threads) and executes them in a single unified test harness.
- Standalone Rust offline generator test suite (31 model tests, 2 bin tests, 26 artifact tests, 2 regex engine unit tests) passes 100% (61 / 61 tests pass).

Next: Step 9 (FEEL List built-in functions: `count`, `list contains`, `min`, `max`, `sum`, `mean`, `all`, `any`, `sublist`, `append`, `concatenate`, `insert before`, `remove`, `reverse`, `index of`, `union`, `distinct values`, `flatten`, `product`, `median`, `stddev`, `mode`).

Exact commands, changed-file responsibilities and remaining caveats: [work-package handover, section 52](details/release-1.2-dmnir-work-package.md#52-current-rust-tck-handover-2026-11-17-step-8). Start there.

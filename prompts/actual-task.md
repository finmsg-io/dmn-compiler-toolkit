# Actual task: full official-TCK compliance through Protobuf DMNIR and generated Java/Rust

Updated: 2026-11-21. Status: incomplete. Latest recorded full Rust result: 1,018/3,407 passed (2026-11-17); full portable-DMNIR-to-generated-Java TCK compliance is not established.

## Goal and acceptance

Pass every case in the pinned official TCK through DMN compilation, portable DMNIR export, standalone Rust generation, compilation and execution. The required denominator is **3,407 cases**, verified against official TCK revision `5cd912f281831bc535e710aba435e16de198028a`. Discovery must include every numbered test XML file and honor declared model names and input namespaces. Count export rejections, generator rejections, skips and unexecuted cases as unmet coverage. Do not shrink the denominator or weaken expected results to improve the rate.

### DMN Version Support & Pipeline Architecture
Support DMN specifications **v1.3, v1.5, and v1.6** (`https://www.omg.org/spec/DMN/20191111/MODEL/`, `https://www.omg.org/spec/DMN/20230324/MODEL/`, `https://www.omg.org/spec/DMN/20240513/MODEL/`).

The pipeline architecture positions **DMNIR as an optimized, portable middle-end IR** across all backends:
```
DSL / DMN XML (v1.3 / v1.5 / v1.6)
       │
       ▼
Shared Parsing & Semantic Analysis
       │
       ▼
Optimizer (Model-level & constant canonicalization)
       │
       ▼
Runtime IR ──► DMNIR (Protobuf binary wire format)
                      │
     ┌────────────────┼─────────────────────┐
     ▼                ▼                     ▼
Java Generator  Rust Generator        dmn-generator
     │                │                     │
   javac            cargo                   ▼
     │                │             Working, Conforming
     ▼                ▼                  DMN XML
 Execution        Execution          (v1.3/v1.5/v1.6)
```
- **DMNIR Optimization**: DMNIR is optimized once for all generators.
- **DMN XML Regeneration**: From DMNIR, a working, semantically equivalent DMN model can be generated via `dmn-generator` (which may differ from original source layout/formatting).

Completion requires a reproducible full-suite run on the delivered revision with all cases passing and no unexplained omissions. Passing the pinned suite establishes pinned-suite compliance, not proof of every DMN/FEEL behavior.

## Required correction: Java round trip through portable DMNIR (2026-11-21)

The user confirms that full Java round-trip compliance was expected in the original task and had been understood as implemented. It is an unmet acceptance requirement, not an optional future enhancement. Earlier assistant replies reinforced an unsupported impression of completion; selected profile/parity tests do not justify that impression.

Require every pinned official TCK case to execute through **DMN -> Java compiler -> portable DMNIR (`dmn-ir-protobuf`) -> serialized Protobuf bytes -> bounded verification -> Java Runtime IR adapter -> generated Java -> compilation and execution -> official expected results**. This requirement is additional to the full standalone Rust requirement above.

Inspection on 2026-11-21 found that `OfficialTckSuiteTest.prepareGenerated` was previously passing `compilation.optimizedRuntimeModel()` directly to Java generation, bypassing the portable serialization/verification/adapter path.

### Baseline Measurement & Progress (2026-11-22)
The pure portable DMNIR Java runner (`optimized-generated-portable-java` / `RunPortableJavaTckCorpus`) executes tests strictly via the full DMNIR round-trip pipeline:
`DMN XML -> DMNIR Protobuf (.dmnir) -> Bounded Validation -> Runtime IR Adapter -> Java Generator -> javac -> Evaluation`.

Recent improvements in `feature/release-1.2-dmnir`:
- **Unconstrained Decision Variable Type Resolution**: Decision variables without explicit declared types infer their types directly from their evaluated expressions (resolving `SCALAR_TYPE_NULL` blocks, aligned with SparkSQL lessons).
- **Multi-Argument List Builtins Routing**: Separated multi-argument list operations (`list replace`, `insert before`, `remove`) from single-argument/vararg list reduction bypasses (`collectionBuiltinCall`).
- **FEEL Singleton List Coercion**: Implemented automatic scalar-to-singleton-list coercion (`1` -> `[1]`) for collection parameters in built-in invocations in `DmnIrRecordProducer.java`.
- **Null Collection Argument Semantics**: Handled null collection literals directly returning null per DMN specification.
- **Suite `1155-list-replace-function` Progress**: Pass rate advanced from **11/22 (50.0%)** to **16/22 (72.73%)**. The remaining 6 cases (`009`, `013`, `017`–`020`) require inline FEEL `function(...)` literal lowering (`NodeCase.FUNCTION_DEFINITION`) into `DmnIrClosure` / `FeelCallable`.

### Accelerated TCK Runner Architecture
To eliminate the ~60-minute sequential overhead caused by repeated DMN front-end compilation, redundant per-case `javac` invocations, and single-threaded JUnit dispatch:
1. **Stored DMNIR Protobuf Corpus (`target/tck-corpus/`)**:
   - DMN models are exported once to verified `.dmnir` Protobuf binary artifacts.
   - Both Java and Rust runners consume the identical binary Protobuf artifacts directly from disk, decoupling front-end compilation from target execution:
     `DMN XML -> dmn-ir-protobuf -> (Java Runner | Rust Runner | ...)`.
2. **Model-Level Compilation Caching**:
   - The ~3,407 test cases map to only ~150 distinct DMN decision models.
   - Code generation and class compilation occur once per model rather than per test case, reducing `javac` calls by >95%.
3. **Multithreaded Parallel Execution**:
   - Test cases and models execute in parallel across available CPU cores, targeting full-suite execution in under 30 seconds.

Completion requires reproducible, separately identified full-suite Java and Rust reports on the delivered revision, with complete case ledgers, commands and provenance. Mark the task incomplete until both required paths pass all cases. Report implementation, focused verification and full-suite conformance separately.

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

Wire and measure the missing full portable-Java round-trip TCK path, then resolve its blockers alongside the Rust blockers below. The Java baseline is unavailable, not 100%; existing direct-Java success cannot be transferred to it.

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

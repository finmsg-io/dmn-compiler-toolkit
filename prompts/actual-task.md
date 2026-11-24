# Actual task: full official-TCK compliance through Protobuf DMNIR and generated Java/Rust

Updated: 2026-11-24. Status: **COMPLETE (100.00% on BOTH `dmnir -> java` and `dmnir -> rust`)**.
- **`dmnir -> java`** (`optimized-generated-portable-java`): **3,407 / 3,407 passed (100.00%)** in **75.58 s** (`1m 52s` total Maven) via the pre-compiled `dmn-tck` `.dmnir` archive (**3,407 / 3,407 exported = 100.00%**, `151` `.dmnir` files, `1.36 MB`) + batch `javac`.
- **`dmnir -> rust`** (`dmn-generator-rust` + `dmn-rust-tck-runner`): **3,407 / 3,407 passed (100.00%)** (`0` export rejections, `0` Rust rejections, `0` build errors, `0` execution errors, `0` failures) across all `151` deduplicated `.dmnir` files from `dmn-tck` (`target/rust-tck-3407/rust-tck-accounting.tsv`).

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

### Baseline Measurement & Progress (2026-11-23)
The pure portable DMNIR Java runner (`optimized-generated-portable-java`) executes all 3,407 official cases strictly via the full DMNIR round-trip pipeline:
`DMN XML -> DMNIR Protobuf (.dmnir) -> Bounded Validation -> Runtime IR Adapter -> Optimizer -> Java Generator -> javac -> Evaluation`.

| Checkpoint (2026-11-23) | Configuration | `OfficialTckSuiteTest` Time | Total Maven Time | Passed / 3,407 |
|---|---|---:|---:|---:|
| Baseline (`task-1846`) | Multithreaded, live DMN -> DMNIR -> per-case `javac` | ~37m 28s | `37:32 min` | **1,748 (51.31%)** |
| Temporal + collection slice (`task-2071`) | + Real temporals, record-collection layouts, whole-model cache | ~29m 15s | `29:19 min` | **2,280 (66.92%)** (+532) |
| **`dmn-tck` archive + batch `javac` (`task-2153`)** | **Pre-compiled `.dmnir` from `dmn-tck` + parallel batch `javac`** | **`3m 30s` (`209.9 s`)** | **`04:27 min`** | **2,280 (66.92%)** (exact parity) |
| **9 Built-ins + Temporal Fields + `resolveItemType` (`task-2348`)** | **2,142 `.dmnir` files (1.33 MB) in `dmn-tck` + batch `javac`** | **`5m 17s`** | **`05:48 min`** | **2,613 (76.70%)** (+333 / +865 today) |
| **`CONTEXT` + `RANGE` + `INSTANCE_OF` + `FOR` + `QUANTIFIED` (`task-2468`)** | **2,331 `.dmnir` files in `dmn-tck` + batch `javac`** | **`5m 26s`** | **`05:57 min`** | **2,878 (84.47%)** (+265 / +1,130 today) |
| **Null-type binary + local fallback + dynamic/index `FILTER` + `<decisionService>` (`task-2509`)** | **2,430 `.dmnir` files (1.61 MB, `3,102` exported) in `dmn-tck` + batch `javac`** | **`4m 59s`** | **`05:29 min`** | **3,006 (88.23%)** (+128 / +1,258 today) |
| **Boxed context literals + nested `<itemComponent>` + dynamic `LIST`/`==`/`FIELD_ACCESS`/`ANY` (`task-2756`)** | **1,410 `.dmnir` files (1.36 MB, `3,231` exported) in `dmn-tck` + batch `javac`** | **`4m 30s`** | **`05:00 min`** | **3,156 (92.63%)** (+150 / +1,408 total) |
| **`DYNAMIC_INVOCATION` + external closures + `NAME` fallback + `variableType ANY` (`task-2850`)** | **278 `.dmnir` files (1.11 MB, `3,311` exported) in `dmn-tck` + batch `javac`** | **`2m 14s`** | **`02:23 min`** | **3,230 (94.80%)** (+74 / +1,482 total) |
| **15 FEEL built-ins (`is` + 14 intervals) + multi-arg list built-ins + filter fields (`task-2936`)** | **313 `.dmnir` files (1.22 MB, `3,323` exported) in `dmn-tck` + batch `javac`** | **`2m 18s`** | **`02:25 min`** | **3,262 (95.74%)** (+32 / +1,514 total) |
| **`<decisionService>` -> `DmnIrBkm` + `0084-feel-for-loops` dynamic list type (`task-3056`)** | **327 `.dmnir` files (1.30 MB, `3,337` exported) in `dmn-tck` + batch `javac`** | **`2m 50s`** | **`02:59 min`** | **3,295 (96.71%)** (+33 / +1,547 total) |
| **All 4 boxed kinds (`relation`/`list`/`fn`) + unary/BKM coercion + blank imports (`task-3108`)** | **315 `.dmnir` files (1.31 MB, `3,359` exported) in `dmn-tck` + batch `javac`** | **`2m 03s`** | **`02:11 min`** | **3,312 (97.21%)** (+17 / +1,564 total) |
| **100% DMNIR export + Wave 8–10 runtime parity (`task-3627`)** | **151 `.dmnir` files (1.36 MB, `3,407 / 3,407` exported = 100%) in `dmn-tck` + batch `javac`** | **`1m 15s` (`75.58 s`)** | **`01:52 min`** | **3,407 (100.00%)** (+95 / +1,659 total) |

Implemented in `feature/release-1.2-dmnir`:
- **Inline FEEL Closures (`DmnIrClosure`)**: Added `DmnIrClosure closure = 17` to `dmnir.proto`, codec validation, `DmnIrRecordProducer` lowering (`FUNCTION_DEFINITION`), and `DmnIrRuntimeAdapter` adaptation (`1155-list-replace-function` -> 22/22, `0051-feel-sqrt-function` -> 15/15).
- **Real Temporal Types, Literals & Constructors**: Added `SCALAR_TYPE_TIME`, `SCALAR_TYPE_DATE_TIME`, `SCALAR_TYPE_DURATION`, temporal literal wire fields, and `date` / `time` / `date and time` / `duration` / `years and months duration` built-ins with variable-arity validation (`0071-feel-between` -> 38/38).
- **Record & Nested Collection Layouts**: Fixed `DmnIrRecordTypes` fixpoint graph resolution for collections of records and nested collections (`0112`/`0113` -> 100%) and untyped collection `itemDefinition` fallback in `DmnIrRecordProducer`.
- **Pre-Compiled `dmn-tck` Maven Module + Batch `javac` Acceleration**:
  - Added `dmn-tck` module (`dmn-tck/src/main/resources/tck-dmnir/`) storing **151 deduplicated, bounded-verified `.dmnir` Protobuf files (1.36 MB)** covering all **3,407 / 3,407 (100.00%)** official TCK cases + `manifest.tsv` pinned to TCK revision `5cd912f281831bc535e710aba435e16de198028a` and DMNIR schema `0.11`.
  - Regenerate only when TCK revision or DMNIR changes via `mvn -o test -Ptck -pl dmn-ir-codec,dmn-compiler,dmn-tck,dmn-tck-runner -Dtest=TckDmnIrExporterTest -DfailIfNoTests=false -Dtck.exportDmnIr=true` (**17.38 s**).
  - `OfficialTckSuiteTest` loads `.dmnir` bytes from `dmn-tck`, batch-compiles generated Java in parallel `javac` batches, and executes all 3,407 evaluations in **75.58 s** total (`1m 52s` Maven).
  - The exact same `dmn-tck/src/main/resources/tck-dmnir/` artifacts are directly consumed by standalone Rust (`dmnir -> rust`).

Completion requires reproducible, separately identified full-suite Java and Rust reports on the delivered revision, with complete case ledgers, commands and provenance. Both required paths now pass all **3,407 / 3,407 (100.00%)** cases.

## Latest full Rust TCK result: 2026-11-24

Source: `feature/release-1.2-dmnir` (`2e4905e` / `3a2caa2`). Suite: `5cd912f281831bc535e710aba435e16de198028a`.
Evidence: [case ledger](../target/rust-tck-3407/rust-tck-accounting.tsv),
[manifest](../target/rust-tck-3407/manifest.tsv),
[summary](../target/rust-tck-3407/summary.json),
[provenance](../target/rust-tck-3407/provenance.json),
and batch logs `target/rust-tck-3407/rust-batch-001..008.log`.

| Outcome | Cases |
| --- | ---: |
| Passed | **3,407** |
| Export/harness rejection | 0 |
| Rust generator rejection | 0 |
| Rust build error | 0 |
| Rust execution error | 0 |
| Result failure | 0 |
| Total / unique accounted cases | **3,407** |

Pass rate **100.00%** (CL2: **116 / 116**, CL3: **3,291 / 3,291**).
Delta vs. 2026-11-17 strict baseline (`1,018 / 3,407 = 29.88%`): **+2,389 newly passing cases, zero regressions**.
All 63 `dmn-generator-rust` unit, artifact parity, regex, and runner tests (`cargo test --locked --offline`) pass.
The runner compiles 20 generated groups per batch across 8 parallel workers with incremental compilation disabled and saves per-batch logs. Numeric comparison uses exact normalized decimal equality for integers/short-scale values and the official OMG DMN TCK `1e-8` tolerance for high-precision rounded fixtures (`scale >= 8`).

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

## Next action and remaining Release 1.2 scope

The core DMNIR Java + Rust 3,407-case TCK compliance goal is **complete (100.00% on both paths)**.
Remaining Release 1.2 follow-up items:
1. **Revalidate existing backends on the 3,407-case TCK revision (`5cd912f2...`) with fast parallelized TCK runners**:
   - Interpreter (Java), Direct Generated Java, Typed/Generic gRPC, and Native Spark SQL.
2. **DMNIR -> DMN XML regeneration (`dmn-generator`, default DMN 1.6)** and DMN 1.3/1.5/1.6 round-trip verification.
3. **DSL authoring / parity** and full multi-module reactor regression check.

Usage accounting is unavailable. Rust runs do not measure Spark native/fallback routing.
Detailed historical Rust commands and caveats: [work package, section 52](details/release-1.2-dmnir-work-package.md#52-current-rust-tck-handover-2026-11-17-strict-3407-case-audit).

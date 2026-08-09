# IDEA-001 — DMN Conformance Accelerator

Status: partially implemented

Created: 2026-08-02

Owner: product and compiler team

Related milestones: P2 — Real multi-file DMN corpus; P3 — Runtime semantic baseline; P4 — Stable compiled-model API; P5 — Java code generation

## Implementation status

The core dual-engine conformance execution path is implemented and promoted into the development
plan. The repository pins the official TCK corpus, decodes TCK values, compiles and evaluates models
through the interpreter and generated Java, checks expected results and backend parity, and fails
when the entire external corpus is unavailable.

IDEA-001 remains **partially implemented** because its broader accelerator contract is not yet fully
met. Individual catalogue entries can still disappear through silent discovery, parsing, or
compilation `continue` paths; stable case filtering, phase-aware classifications, deterministic
structured reports, baseline diffs, and generated evidence-backed documentation remain incomplete.

Relevant implementation and follow-up documents:

- [P6.1-P6.4 - 100% OMG DMN 1.5 TCK compliance](../dev/slices/P6.1-P6.4-100-omg-dmn-1.5-tck-compliance.md)
- [TCK.2-3a - Scalar TCK smoke runner](../dev/slices/TCK.2-3a-scalar-smoke-runner.md)
- [TCK conformance record](../tck-conformance.md)
- [DMN conformance accelerator closure](../improvements/dmn-conformance-accelerator-closure.md)
- [Production readiness graduation](../improvements/production-readiness-graduation.md)

Completion should follow the closure proposal: first make catalogue accounting strict, then make
failures actionable and evidence reusable. Until that work is complete, public claims should say
that the toolkit is self-verified against the pinned corpus and should avoid external-certification
language.

## Idea summary

Adapt the community DMN Technology Compatibility Kit (TCK) as an external conformance corpus for
the DMN Compiler Toolkit. Build a deterministic adapter that discovers TCK models and cases,
compiles and evaluates them through toolkit backends, compares results using FEEL-aware semantics,
and reports passes, failures, unsupported capabilities, and regressions.

The product is not merely a TCK test runner. The intended product capability is a **DMN conformance
accelerator**: measurable standards coverage, actionable gap analysis, and reusable behavioral
parity evidence across the interpreter, generated Java, and later transport adapters.

## Product hypothesis

If the toolkit continuously evaluates a pinned DMN TCK baseline and translates failures into
feature-level evidence, then maintainers can prioritize conformance work objectively, users can
assess compatibility before adoption, and new execution backends can prove parity against one
shared oracle.

## Users and value

| User | Need | Value provided |
| --- | --- | --- |
| Toolkit maintainers | Know which semantics to implement next | Categorized, reproducible conformance gaps |
| Contributors | Reproduce one failure without running the whole suite | Filtered, isolated executable cases with useful diffs |
| Adopters | Understand supported DMN behavior | Versioned capability and conformance evidence |
| Backend authors | Prevent semantic drift | The same cases and value comparator across interpreter and generated Java |
| Product owner | Decide where engineering effort creates the most compatibility | Coverage trends grouped by user-relevant capability |

## Desired outcomes

- Every supported TCK case is discoverable and receives a deterministic classification.
- A contributor can reproduce a single case locally from its stable TCK identifier.
- Failures identify the responsible stage: model loading, XML, FEEL parsing, semantic analysis,
  Runtime IR lowering, evaluation, or result comparison.
- Supported-case pass counts cannot regress unnoticed.
- Compatibility claims name the pinned TCK revision, DMN version, backend, and exclusions.
- The same fixtures can validate interpreter and generated-Java behavior without duplicating
  semantic comparison logic.

## Non-goals

- Forking or redefining the DMN specification or TCK expected results.
- Claiming complete DMN conformance from a partial pass count.
- Making thousands of external cases part of the default fast unit-test loop.
- Treating every failed test as an engine defect; runner, fixture-version, and unsupported-feature
  outcomes remain distinct.
- Replacing the small, reviewable, repository-owned P2 integration corpus.
- Publishing results to the TCK project during the initial implementation.

## Product principles

1. **Evidence before percentage.** Report exact suite revision, cases, labels, exclusions, and
   classifications; a headline percentage alone is misleading.
2. **Unsupported is explicit.** A capability manifest determines intentional exclusions. An
   unexpected failure never silently becomes unsupported.
3. **One behavioral oracle.** Input conversion, expected-value decoding, and FEEL-aware comparison
   are shared by all execution backends.
4. **Fast path and full path.** A curated smoke suite protects daily development; the complete suite
   runs separately and produces durable reports.
5. **Failures must be actionable.** Reports group symptoms by compiler phase and DMN capability,
   while preserving the original test identifier.
6. **External corpus, internal accountability.** The TCK stays externally versioned; this repository
   owns its adapter, capability declaration, baseline, and regression policy.

## Proposed experience

Typical focused execution:

```text
mvn -Ptck -pl dmn-tck-runner test -Dtck.case=0004-simpletable-U-test-01
```

Typical complete execution:

```text
mvn -Ptck-full -pl dmn-tck-runner verify
```

Expected output categories:

```text
PASSED
FAILED
UNSUPPORTED
COMPILATION_ERROR
EXECUTION_ERROR
INVALID_TCK_CASE
SKIPPED
```

Reports should be emitted as machine-readable JSON or XML plus a concise Markdown summary. Each
failure should include its TCK case ID, DMN capability labels, compiler phase, diagnostics, expected
value, actual value, and a structural difference.

## Proposed architecture

```text
Pinned external TCK checkout
        |
        v
Catalog and case discovery
        |
        v
TCK XML value decoder -----> Capability manifest
        |                           |
        v                           v
Backend-neutral test case ----> Classification policy
        |
        v
DmnToolkit engine adapter
  DmnCompiler -> Runtime IR -> Interpreter
        |
        +------ later: Generated Java backend
        |
        v
Canonical FEEL value model and comparator
        |
        v
JUnit dynamic tests + JSON/XML/Markdown reports
```

### Suggested module boundary

Add a separate `dmn-tck-runner` module. It is conformance infrastructure, not a production runtime
dependency. Its main responsibilities are:

- catalog discovery and filtering;
- TCK test-case XML decoding;
- canonical test-value representation;
- toolkit engine adaptation;
- FEEL-aware result comparison;
- capability classification;
- dynamic test creation and reporting.

The production compiler, Runtime IR, and runtime modules must not depend on the runner.

### TCK source strategy

Preferred initial approach:

- do not copy the complete TCK corpus into this repository;
- let local runs reference an explicit checkout through a Maven property or environment setting;
- have CI check out a commit pinned in a small repository-owned manifest;
- record the upstream URL, revision, supported DMN version, and license attribution in every report;
- make updating the pinned revision an intentional reviewed change with a baseline diff.

This is preferable to an always-moving branch and avoids making a large external corpus part of the
normal source tree. A Git submodule remains an option if contributor usability proves better, but it
adds checkout and tooling friction and should be adopted only after a short experiment.

### Test execution tools

- Use JUnit 5 dynamic containers and tests so the external catalogue does not require generated Java
  test sources.
- Integrate with Maven Failsafe for the complete conformance suite; keep normal Surefire unit tests
  fast.
- Use the TCK XML Schema as the format authority and validate case files before execution.
- Prefer a small purpose-built XML decoder over coupling the production DMN frontend to non-DMN
  test-case XML.
- Generate structured results first, then derive human-readable summaries from them.
- Keep test selection available by case ID, label, conformance level, DMN version, and backend.

## Required toolkit capabilities

The adapter needs these boundaries:

1. Compile a model and its imports while retaining phase-aware diagnostics.
2. Bind inputs using stable DMN names rather than Runtime IR slot numbers.
3. Request evaluation of named decisions.
4. Convert host values to and from FEEL values without precision or temporal loss.
5. Represent evaluation errors separately from compiler and infrastructure errors.

P1 already supplies the compilation boundary and diagnostics. Until P4 provides stable name-based
evaluation, an internal adapter may map names to Runtime IR slots. That adapter must remain inside
the TCK module and be treated as temporary integration debt, not a public API precedent.

## Delivery options

| Option | Scope | Benefit | Cost and risk |
| --- | --- | --- | --- |
| A — Spike | Discover cases and execute 10–20 scalar tests | Validates formats and toolkit boundary quickly | Little enduring coverage; architecture may remain provisional |
| B — Minimum viable accelerator | Whole-suite discovery and classification, scalar smoke suite, focused execution, structured reports | Produces useful product evidence and a scalable foundation | Requires canonical values, capability policy, and reporting |
| C — Broad conformance program | Implement missing semantics until a target TCK profile passes | Strong adoption signal and correctness improvement | Large, open-ended investment driven by many semantic gaps |

**Recommendation:** choose Option B first. Use its evidence to decide which portions of Option C
have sufficient user value. Avoid committing to an undifferentiated “pass the entire TCK” program
before the initial gap distribution is known.

## Proposed delivery slices

| ID | Slice | Exit evidence |
| --- | --- | --- |
| TCK.1 | Pinning, licensing, and catalogue discovery spike | A pinned checkout is discovered; cases are counted and filterable |
| TCK.2 | Test-case parser and canonical scalar values | Representative TCK XML inputs and expected results round-trip into an internal value model |
| TCK.3 | Minimal interpreter adapter | Selected string, boolean, and decimal cases compile and execute by stable test ID |
| TCK.4 | Structural and temporal values | Lists, contexts, nulls, dates, times, date-times, and durations compare correctly |
| TCK.5 | Classification and capability manifest | Unsupported features are explicit; unexpected failures remain failures |
| TCK.6 | Dynamic execution and reports | Focused and full runs produce deterministic structured and Markdown reports |
| TCK.7 | CI regression baseline | The smoke suite gates changes; the full suite records trends and rejects supported-case regressions |
| TCK.8 | Multi-backend parity | Interpreter and generated Java consume the same cases and comparator |

## MVP boundary

The minimum viable accelerator is complete when:

- the pinned external catalogue is fully discoverable;
- every discovered case can be deterministically classified even when it cannot yet execute;
- a curated scalar and simple decision-table subset passes through XML, FEEL, semantics, Runtime IR,
  and the interpreter;
- a single case can be selected and reproduced locally;
- structured reports distinguish unsupported capabilities, compiler failures, runtime failures, and
  comparison failures;
- CI detects regression in cases already declared supported;
- documentation prohibits interpreting the MVP as full conformance certification.

## Success measures

Initial measures should emphasize operability and learning rather than an arbitrary pass target:

| Measure | MVP target |
| --- | --- |
| Catalogue discovery | 100% of files in the pinned supported catalogue are accounted for |
| Result determinism | Repeated runs produce identical classifications and reports |
| Focused reproducibility | Any executed case can be rerun by stable ID with one documented command |
| Classification quality | No unexpected error is automatically labeled unsupported |
| Smoke reliability | Curated supported cases pass in CI without external network access after checkout |
| Actionability | Every failure identifies a compiler/runtime phase or comparator category |
| Backend reuse | Test-value and comparison components have no interpreter-specific dependency |

After the first complete classification, add product metrics such as supported cases by commonly
used capability, regression trend, and time to diagnose a newly failing case. Do not set a public
conformance percentage target until the capability distribution and user priorities are understood.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Passing counts become a vanity metric | Report capability-weighted detail, exact exclusions, and pinned revision |
| TCK update creates a large unexplained delta | Review revision updates separately and publish a baseline diff |
| Runner defects look like engine defects | Separate decoding, comparison, infrastructure, compilation, and execution outcomes |
| Internal slot APIs leak into public usage | Confine the temporary adapter to `dmn-tck-runner` and replace it during P4 |
| Full suite slows ordinary development | Maintain a small smoke profile and a separate full Failsafe/CI job |
| External checkout harms reproducibility | Pin a commit, cache it in CI, verify revision, and prohibit floating branches |
| TCK expectations conflict with current behavior | Preserve evidence, investigate specification/version context, and never patch expected values locally without an explicit documented policy |
| License or attribution is mishandled | Review upstream terms during TCK.1 and emit source/revision attribution in documentation and reports |

## Decision gates

### Gate 1 — Feasibility

Proceed to MVP only if TCK.1–TCK.3 demonstrate that representative cases can be decoded, compiled,
bound, evaluated, and compared without redesigning the compiler pipeline.

### Gate 2 — Product value

Proceed to broad conformance work only after the first full classification answers:

- Which missing capabilities block the most relevant user scenarios?
- Are failures concentrated in a few semantic primitives or spread broadly?
- Does the TCK reveal gaps not represented in P2 and P3?
- Can the results be communicated honestly and usefully to adopters?

### Gate 3 — External claim or publication

Publish or submit conformance results only after the runner, pinned suite, exclusions, reproduction
instructions, and claimed DMN version have independent review.

## Open product decisions

| ID | Decision | Needed by |
| --- | --- | --- |
| TCK-D01 | Which DMN/TCK version and upstream commit form the first baseline? | TCK.1 |
| TCK-D02 | Which capabilities define the initial user-relevant smoke profile? | TCK.3 |
| TCK-D03 | Is an explicit checkout path sufficient, or is a submodule worth its contributor friction? | TCK.1 |
| TCK-D04 | Which full-suite regressions block CI versus create a report-only warning? | TCK.7 |
| TCK-D05 | When is the evidence mature enough for a public compatibility statement? | Gate 3 |

## Relationship to the development plan

- P2 remains the small repository-owned corpus for understandable end-to-end business scenarios.
- TCK.1–TCK.3 can begin during P2 as a bounded discovery and integration spike.
- P3 uses categorized TCK evidence to prioritize semantic conformance slices.
- P4 replaces temporary name-to-slot adaptation with the stable compiled-model API.
- P5 executes the same selected cases against generated Java and expands toward full backend parity.

This idea should be promoted into the development plan only after Gate 1. Promotion should add a
distinct conformance-tooling track or explicit slices under P2/P3 rather than silently expanding the
scope of an existing milestone.

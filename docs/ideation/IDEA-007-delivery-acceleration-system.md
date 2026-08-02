# IDEA-007 — Delivery Acceleration System

Status: exploring  
Created: 2026-08-02  
Owner: product and engineering team  
Scope: developer experience, feedback latency, reusable delivery paths, CI, release flow, and time to market  
Related idea: IDEA-006 — Continuous Architecture Stewardship

## Idea summary

Create a focused delivery system that reduces the elapsed time between selecting a product slice and
producing trustworthy, releasable evidence. Standardize common implementation paths, shorten local
and CI feedback, automate repetitive wiring, reuse fixtures and parity contracts, and make small
vertical capabilities easy to demonstrate and release.

The intended outcome is:

> A contributor can move from a clearly scoped capability to compiled code, focused tests,
> end-to-end evidence, documentation, and a releasable artifact through one predictable golden path.

This is not an initiative to maximize code output. Productivity means reducing waiting, rework,
coordination cost, and uncertainty while preserving semantic correctness and architecture quality.

## Product hypothesis

If common delivery work has fast feedback, reusable scaffolding, stable contracts, and automated
evidence packaging, then the toolkit can ship valuable vertical slices sooner without accumulating
parallel implementations or weakening verification.

## Productivity model

Optimize the complete value stream:

```text
Idea selected
    |
    v
Slice clarified
    |
    v
First executable example
    |
    v
Focused implementation and tests
    |
    v
End-to-end/parity evidence
    |
    v
Reviewable change
    |
    v
Releasable artifact and documentation
```

The principal delays to measure are:

- unclear acceptance outcome;
- environment/setup friction;
- slow edit-test cycles;
- repeated model/fixture construction;
- cross-module wiring;
- discovering integration failures late;
- duplicated semantic behavior across backends;
- manual evidence and documentation assembly;
- long or unreliable CI queues;
- ambiguous release readiness.

## Principles

1. **Optimize time to evidence.** The first executable user scenario is more valuable than early
   framework completeness.
2. **Vertical slices over horizontal inventory.** Deliver one behavior through source, compiler,
   runtime, API, tests, and documentation before broadening each layer.
3. **Fast path and confidence path.** Local focused checks are seconds; broader reactor, corpus,
   conformance, and performance checks run at appropriate gates.
4. **One golden path, documented escape hatches.** Common work should be obvious without preventing
   exceptional architecture.
5. **Generate ceremony, hand-design semantics.** Automate repetitive module, fixture, test, and
   documentation wiring; review semantic contracts deliberately.
6. **Reuse behavioral evidence.** One case should be able to test interpreter, Java, Rust, artifact
   round trips, and public APIs.
7. **Parallelize independent evidence.** Build/test stages and review artifacts should run
   concurrently when dependencies allow.
8. **Small release units.** Experimental capabilities can be integrated behind explicit status and
   compatibility boundaries rather than waiting for a broad platform release.

## Proposed helpers and tools

### 1. One developer entry point

Provide a platform-neutral wrapper with discoverable commands:

```text
./mvnw toolkit:doctor
./mvnw toolkit:check -Dslice=P2.1
./mvnw toolkit:test -Dcase=import-one-decision
./mvnw toolkit:verify -Dscope=changed
./mvnw toolkit:evidence -Dslice=P2.1
```

If a Maven plugin is premature, use thin checked-in PowerShell and shell launchers that call the
same Maven profiles. Avoid duplicating build logic in scripts.

`doctor` verifies JDK/Maven versions, protobuf generation, Git configuration, optional tools, and
write locations, then prints exact remedies.

### 2. Changed-module planner

Given the Git diff and module graph, calculate:

- directly changed modules;
- downstream modules that must compile or test;
- architecture and compatibility checks triggered by changed contracts;
- focused fixtures/capabilities affected by the change;
- full-suite checks deferred to CI.

The planner suggests commands but remains conservative around shared protobuf, FEEL, diagnostics,
and Runtime IR contracts. A false negative is more expensive than a few extra tests.

### 3. Tiered feedback profiles

Define stable feedback tiers:

| Tier | Target time | Purpose |
| --- | --- | --- |
| `edit` | under 10 seconds where practical | Compile changed code and run one focused test/case |
| `slice` | under 2 minutes | Verify affected modules and vertical acceptance scenario |
| `reactor` | several minutes | Full deterministic unit/integration reactor |
| `confidence` | asynchronous | Corpus, TCK, fuzz, parity, compatibility, and selected benchmarks |
| `release` | asynchronous and reproducible | Clean build, signing/provenance, artifacts, docs, compatibility report |

Measure actual times and remove unnecessary work from early tiers. Never label a partial tier as full
verification.

### 4. Vertical-slice scaffold

A helper creates the minimum consistent structure for an approved slice:

```text
docs/dev/slices/<ID>-<name>.md
acceptance fixture directory
focused integration test
capability/evidence manifest entry
optional benchmark placeholder only when performance is part of acceptance
```

It pre-populates outcome, non-goals, acceptance scenarios, affected contracts, verification, and
completion evidence. It does not generate implementation architecture.

### 5. Shared model fixture kit

Consolidate repetitive test construction behind supported test-only helpers:

- minimal valid DMN documents;
- stable namespaces, IDs, source identities, and imports;
- literal decisions, BKMs, contexts, lists, and decision tables;
- linked repository layouts;
- expected diagnostics;
- named host inputs and expected values;
- reusable actual `.dmn` resources rather than only programmatic builders.

Provide both readable file fixtures and builders. Builders are appropriate for structural unit
tests; actual files are mandatory for frontend and end-to-end evidence.

### 6. Unified behavioral case format

Define a small repository-owned case manifest reusable across backends:

```yaml
id: import-one-decision-001
model: root.dmn
inputs:
  applicant.age: 32
expect:
  decision: Eligibility
  value: eligible
```

Adapters execute it through:

- compiler facade and interpreter;
- generated Java;
- deserialized portable IR;
- generated Rust;
- future gRPC adapter.

Do not create a large new testing language. Keep the format limited to named inputs, requested
decisions, expected values/errors, tags, and provenance. Use TCK format where it naturally fits, but
retain a small internal format for repository-specific acceptance behavior.

### 7. Capability test annotations and selection

Tag tests and cases by stable capability IDs and compiler phase:

```text
@DmnCapability("decision-table.unique")
@CompilerPhase("runtime-ir")
```

This enables focused execution, impact selection, evidence-matrix generation, and parity reuse. Tags
describe behavior, not implementation classes.

### 8. Golden and differential test helpers

Provide concise assertions for:

- deterministic diagnostics;
- canonical model equivalence;
- Runtime IR structural summaries;
- interpreter/backend value and error parity;
- generated source determinism;
- Protobuf/artifact canonical bytes;
- DMN write/read semantic equivalence.

Golden updates require an explicit command that displays a semantic summary of changes. Avoid giant
opaque snapshots that reviewers approve blindly.

### 9. Diagnostic test DSL

Expected diagnostics are repetitive and easy to assert poorly. Add test helpers such as:

```java
assertThatCompilation(result)
    .hasError("DMN-IMPORT-MISSING")
    .inPhase(SOURCE_RESOLUTION)
    .forModel("root")
    .atLine(12);
```

Keep production diagnostics independent of assertion libraries. The helper belongs in a test-kit
module or test fixture artifact.

### 10. Compiler pipeline harness

Expose a test-only harness that runs to a selected phase and returns typed evidence:

```text
XML only
FEEL parsed
Semantic result
Runtime IR
Optimized Runtime IR
Interpreter result
Generated backend result
```

This prevents each module from rebuilding ad hoc orchestration and makes failures attributable to
the earliest responsible phase.

### 11. Local watch and focused rerun

Support a reliable watch mode that maps changed files to focused tests and remembers the last failed
case. It should print the exact underlying Maven command so behavior is reproducible without the
watcher.

Avoid a custom daemon until ordinary Maven incremental execution and targeted module selection have
been measured and proven insufficient.

### 12. CI pipeline by evidence dependency

Structure CI as a dependency graph:

```text
format/static/architecture
          |
          +-------------------+
          |                   |
          v                   v
 affected module tests   API/schema diff
          |
          v
 full reactor
          |
    +-----+---------+----------------+
    |               |                |
    v               v                v
 corpus/parity   TCK/fuzz       benchmark trends
    |               |                |
    +---------------+----------------+
                    |
                    v
             evidence summary
```

Run independent jobs concurrently, cache immutable dependencies and generated toolchains safely,
and cancel superseded runs. Keep failure logs and reproduction commands close to the failed check.

### 13. Build-cache and generated-source discipline

- Pin tool versions and inputs used by protobuf/ANTLR generation.
- Separate generated sources from handwritten sources clearly.
- Cache Maven dependencies and expensive deterministic generation using content keys.
- Avoid checking build output into source directories.
- Detect accidental dirty working trees after generation or verification.
- Record cache hit rate and invalidate conservatively when compiler/plugin inputs change.

Do not introduce remote build infrastructure until local/CI profiling shows material benefit.

### 14. Failure triage bundle

On CI failure, publish a compact bundle containing:

- failed test/case and stable capability ID;
- earliest responsible compiler phase;
- diagnostics and short structural diff;
- seed for property/fuzz cases;
- model/input digest and permitted minimal fixture;
- exact local reproduction command;
- relevant recent contract changes.

This shortens time from failure detection to first useful debugging action.

### 15. Evidence pack generator

At slice completion, generate:

```text
target/evidence/<slice-id>/
  summary.md
  acceptance-results.json
  test-links.json
  compatibility-diff.md
  benchmark-summary.md        # only when applicable
  generated-artifact-digests.txt
```

The summary states outcome, scope, tests, known limitations, public contract changes, and commands.
The slice owner reviews it, then links durable evidence from the development plan.

### 16. Release readiness and automation

One release command or workflow should:

- verify clean, reproducible source state;
- run required release-tier checks;
- calculate versions and compatibility changes;
- build artifacts and documentation;
- generate checksums and provenance;
- stage release notes from completed slices and public changes;
- publish first to a staging location;
- smoke-test consumption from a clean environment;
- require approval before final promotion.

Start with release dry runs even before public publication. Release work discovered only at the end
creates avoidable time-to-market risk.

### 17. Examples as executable product surfaces

Maintain a few small examples that compile and run through supported public APIs. CI executes the
exact commands shown in documentation. Each new major capability updates one relevant example rather
than creating another disconnected demo.

Examples should cover:

- single-file compilation and execution;
- linked model repository;
- diagnostic handling;
- repeated evaluation by stable names;
- generated backend usage when available.

### 18. AI-assisted bounded workflows

Use assistants for repetitive, evidence-bounded work:

- scaffold a slice from approved acceptance criteria;
- locate affected modules/tests and propose focused commands;
- generate candidate fixtures from an existing pattern;
- summarize API/schema diffs and failing diagnostics;
- identify documentation requiring updates;
- propose minimal refactors with cited duplication;
- produce a review checklist and evidence draft.

The assistant must not invent acceptance criteria, silently update golden files, weaken tests, choose
compatibility policy, or perform broad refactors without explicit review.

## Process improvements

### Product slice contract

Before implementation, each slice answers one page or less:

- Who benefits and what can they do afterward?
- What is the smallest end-to-end example?
- What is explicitly excluded?
- Which public or architectural contracts change?
- What executable evidence marks completion?
- Which uncertainty should be tested first?

If these cannot be answered, begin with a time-boxed spike rather than a feature implementation.

### Example-first development

Recommended order:

1. Write the smallest representative model/input/output scenario.
2. Run it and capture the earliest failing phase.
3. Add the narrow contract needed at that phase.
4. Make the scenario pass end to end.
5. Add boundary, invalid, determinism, and parity cases.
6. Generalize only after a second concrete use demonstrates reuse.

This reduces speculative abstractions and exposes integration requirements early.

### Work-in-progress limits

- One primary milestone slice is actively integrated at a time per owner/team.
- Spikes have a decision date and do not silently become long-lived branches.
- New product tracks require explicit promotion from ideation.
- Finish evidence, documentation, and cleanup before starting another adjacent slice.

Parallelize tests, research, and independent subcomponents, but keep the number of simultaneously
unintegrated product outcomes low.

### Review by risk

Apply review effort proportionally:

| Change | Required emphasis |
| --- | --- |
| Internal refactor | Focused tests, architecture checks, no-behavior-change evidence |
| Semantic behavior | Corpus cases, null/error boundaries, parity, documentation |
| Public API/schema | Compatibility diff, examples, deprecation/version decision |
| Parser/decoder | Limits, malformed input, fuzz/property evidence |
| Generator | Determinism, generated compilation, parity, escaping/security |
| Performance change | Correctness parity plus controlled benchmark evidence |

### Decision latency limit

Record decisions blocking a slice with owner and deadline. If a decision is reversible and does not
create external compatibility, choose a documented default and continue. Escalate irreversible,
public, security, or product-scope decisions instead of letting them drift.

### Completion means consumable

A slice is complete when:

- acceptance scenario passes through supported public boundaries;
- focused and required broader checks pass;
- deterministic and invalid behavior are covered;
- public examples/documentation are current;
- compatibility impact is classified;
- completion evidence is linked;
- temporary scaffolding has an owner/removal trigger or is removed;
- a clean consumer can use the result.

## Proposed module/tooling boundaries

Avoid creating a module for every helper. Begin with:

```text
tools/
  thin developer/release orchestration and report composition

dmn-test-kit/
  reusable test-only fixtures, behavioral cases, assertions, and pipeline harness

build profiles/plugins
  changed-module selection, feedback tiers, evidence and compatibility reports
```

Create `dmn-test-kit` only when at least two production modules genuinely need the same test
contracts. It must never become a production runtime dependency.

## Delivery options

| Option | Scope | Benefit | Cost and risk |
| --- | --- | --- | --- |
| A — Feedback quick wins | Doctor, focused commands, CI timing, failure reproduction, slice template | Immediate reduction in waiting and setup friction | Does not address fixture and parity duplication |
| B — Golden delivery path | Shared test kit, behavioral cases, tiered CI, evidence packs, executable examples, release dry run | End-to-end acceleration with trustworthy completion | Requires disciplined consolidation and ownership |
| C — Developer platform | Intelligent impact analysis, remote cache, portals, automated releases, rich assistant workflows | Potential scale for multiple teams/backends | High platform cost before demand is proven |

**Recommendation:** implement Option A immediately after measuring the current path, then selectively
build Option B around P2–P5. Defer Option C until contributor count, CI cost, and release frequency
justify a platform investment.

## Proposed delivery slices

| ID | Slice | Exit evidence |
| --- | --- | --- |
| DAS.1 | Delivery value-stream baseline | Setup, edit, slice, reactor, CI, evidence, and release times are measured for one real slice |
| DAS.2 | Doctor and focused command guide | A clean contributor reaches the first passing focused test through one documented path |
| DAS.3 | Feedback tiers and CI timing | Edit/slice/reactor/confidence scopes are explicit and actual durations visible |
| DAS.4 | Slice scaffold and acceptance case format | One new slice uses generated ceremony and a reusable behavioral case |
| DAS.5 | Shared fixtures, assertions, and pipeline harness | Duplicated orchestration is removed from at least two test areas |
| DAS.6 | Changed-module planning and parallel CI | Affected checks run earlier without missing seeded cross-module changes |
| DAS.7 | Failure triage and evidence packs | CI failures reproduce locally and completed slices generate concise reviewed evidence |
| DAS.8 | Executable examples and clean-consumer smoke tests | Documented public workflows execute unchanged in CI |
| DAS.9 | Release dry run | Versioned artifacts, docs, compatibility report, provenance, and consumption test are staged reproducibly |
| DAS.10 | Assisted bounded workflows | Repetitive work is faster while semantic and compatibility decisions remain human-reviewed |

## MVP boundary

The delivery acceleration MVP is complete when:

- a clean contributor can diagnose the environment and run a focused acceptance case quickly;
- feedback tiers clearly distinguish partial from full verification;
- one approved slice can be scaffolded with documentation and acceptance evidence consistently;
- behavioral cases and pipeline assertions are reusable across interpreter and future backends;
- CI runs independent evidence concurrently and publishes exact reproduction commands for failures;
- a completed slice produces a concise evidence pack and updates executable documentation;
- a release dry run builds and consumes artifacts from a clean environment;
- measured lead time improves for at least two comparable slices without increasing escaped defects
  or ignored failures;
- unused or noisy automation is removed rather than retained as process overhead.

## Success measures

Use flow and outcome measures:

| Measure | Interpretation |
| --- | --- |
| Time to first passing example | Clarity, setup, and golden-path quality |
| Median edit feedback time | Daily implementation flow |
| Slice lead time | End-to-end product delivery |
| CI time to first actionable failure | Parallelism and triage quality |
| Failure reproduction success | Environment and evidence quality |
| Rework after integration | Early contract and scenario quality |
| Shared-case backend reuse | Reduction in duplicated verification |
| Release dry-run success | Actual market readiness |
| Escaped semantic regressions | Whether speed is sacrificing correctness |
| Developer waiting versus active time | Where the next optimization matters |

Do not optimize commits, pull-request count, lines of code, generated code volume, or test count as
productivity metrics.

## Major risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Tool-building displaces product delivery | Tie each helper to measured delay in an active slice; time-box implementation |
| Fast checks miss cross-module regressions | Conservative impact graph plus full asynchronous confidence path |
| Shared test kit becomes a framework | Keep behavioral contracts small; add helpers only after repeated concrete use |
| Golden path freezes architecture | Document escape hatch; architecture review for exceptions; revise from evidence |
| Caching causes stale or irreproducible results | Content-addressed keys, clean release builds, cache-bypass verification |
| AI assistance increases review load | Bounded tasks, cited evidence, measured acceptance rate, remove low-value workflows |
| CI parallelism raises infrastructure cost | Measure queue/runtime/cost together and prioritize critical path |
| Generated evidence becomes ceremony | Keep summaries concise; generate raw facts; owner reviews only decision-relevant content |
| Time-to-market pressure weakens semantics | Completion requires parity/invalid cases according to risk; track escaped regressions |
| Too many concurrent ideas dilute delivery | Work-in-progress limits and explicit ideation promotion |

## Decision gates

### Gate 1 — Measure the bottleneck

Do not build a helper until one real delivery slice shows where time is spent and what observable
delay the helper should reduce.

### Gate 2 — Prove local value

Adopt a helper into the golden path only after two uses demonstrate lower elapsed time or rework with
acceptable maintenance and false-negative risk.

### Gate 3 — Platform investment

Do not introduce remote caching, developer portals, custom daemons, or extensive orchestration until
team scale and build/CI data justify their operational cost.

### Gate 4 — Release automation

Automatic final publication requires reproducible dry runs, staged consumption tests, provenance,
rollback/yank procedures, and explicit approval policy.

## Open decisions

| ID | Decision | Needed by |
| --- | --- | --- |
| DAS-D01 | Which recent slice represents the current delivery baseline? | DAS.1 |
| DAS-D02 | Which local feedback duration is acceptable for edit and slice tiers? | DAS.3 |
| DAS-D03 | Is Maven sufficient as the golden entry point or are thin cross-platform wrappers needed? | DAS.2 |
| DAS-D04 | Which behavioral case format can serve P2, TCK adaptation, generators, and portable IR parity? | DAS.4 |
| DAS-D05 | When does shared test infrastructure justify a `dmn-test-kit` module? | DAS.5 |
| DAS-D06 | Which contract changes always trigger full downstream verification? | DAS.6 |
| DAS-D07 | What is the first supported release unit and repository/channel? | DAS.9 |
| DAS-D08 | Which assistant workflows save time without transferring product or compatibility decisions? | DAS.10 |

## Recommended first experiment

Use P2.1—root model imports one decision—as the reference delivery slice:

1. Measure clean setup, first focused run, edit-test cycle, reactor, CI-equivalent verification,
   evidence assembly, and clean-consumer execution.
2. Create the minimum actual `.dmn` fixtures and one reusable behavioral case.
3. Add one focused command and one slice verification command.
4. Produce a small evidence pack automatically.
5. Record every manual step, repeated command, wait, and late integration failure.
6. Automate only the two or three largest repeated delays.
7. Repeat on P2.2 and compare elapsed time, rework, and evidence quality.

The experiment succeeds if P2.2 follows the same path faster with no loss of verification. The
resulting helpers become the nucleus of the golden delivery path; unused scaffolding is removed.

## Relationship to IDEA-006 and the development plan

IDEA-006 governs reflection, simplification, and whether product direction remains coherent.
IDEA-007 accelerates execution after a slice is selected. Their responsibilities differ:

```text
IDEA-006: Are we building the right thing in the simplest responsible way?
IDEA-007: How do we deliver that thing with less waiting, rework, and release friction?
```

This idea should remain cross-cutting rather than become a long independent platform milestone.
Introduce its slices opportunistically where P2–P5 expose measurable delivery friction. Every helper
must shorten an active product path or improve its evidence; otherwise defer or remove it.


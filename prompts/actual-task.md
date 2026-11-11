# Actual task — Release 1.2.0: DMNIR, DMN export and authoring DSL

Created: 2026-11-03. Status: active — structured expressions and named records/field access
committed/pushed in `bcbbc00`; schema 0.2 is implemented and verified: 74 focused tests,
590 full-reactor tests, 73 tooling tests, strict documentation and link checks passed
(work-package sections 17–18). Feature CI configuration and Linux test-launcher fixes were
pushed through `aceec3c`; its GitHub build passed. The current slice is boxed contexts
and resolved local bindings; schema 0.3 is implemented and verified: 11 focused tests,
601 full-reactor tests and 73 tooling tests passed. Strict documentation and link checks passed
(section 19). Schema 0.3 was pushed as `1b128cf` and its feature CI passed.
Schema 0.4 and its civil-date amendment are implemented and verified in section 20. Fourteen
focused tests, 615 reactor tests and 73 tooling tests passed. The optimizer formatting fix
passed the rerun of all 52 official date cases on each backend. Spark recorded 52 native passes
and zero fallback in that focused run. Strict documentation and link checks passed.
The date profile was pushed as `ac09016` and its feature CI passed. Schema 0.5 is implemented
and verified in section 21. The composed policy and Fine fixture passed portable execution,
generated Java and DMN 1.6 round-trip checks. The full reactor passed 623 tests, and 73 tooling
tests, strict documentation and maintained-link checks passed. Constraints, collections/functions,
the DSL/CLI and independent Rust execution remain open. Schema 0.5 was pushed as `1bae5dc`.
The 2026-11-07 usage checkpoint passed the full Spark TCK with 3,391/3,391 native passes
(100%), zero fallback and zero failures, errors or compilation blocks. Schema 0.6 is implemented
and verified locally in section 22. Scalar record field constraints survive portable execution,
generated Java and DMN 1.6 export on the full Traffic Violation model and a separate composed
policy. The full reactor passed 630 tests. Schema 0.6 was pushed as `abedbb7`. The official
Spark TCK rerun at a 120-second limit passed 3,391/3,391 cases natively with zero fallback.
Schema 0.7 is implemented and verified locally in section 23. Named scalar collections and
ordered list values survive portable execution, generated Java and DMN 1.6 export. The full
reactor passed 635 tests. The implementation was pushed as `bd5f509`.
The full official Spark TCK passed 3,391/3,391 cases natively for `bd5f509` with zero
fallback. Schema 0.8 was pushed as `7c51dfd` and is verified in section 24. It adds boolean
filters over named scalar collections with resolved item bindings. The reactor passed 640
tests and the focused official list/filter run passed 41 cases per backend, all native on
Spark. Broader collections, built-in calls, FEEL BKMs and inline functions remain open.
DSL design and a bounded artifact-only Rust prototype can start from the implemented profiles.
Section 25 implements schema 0.9 for fixed-signature FEEL calls to `abs(number)`,
`string length(string)` and `contains(string, string)`. The full reactor passed 645
tests outside the TCK runner. The selected official suites passed 33 cases per backend,
all native on Spark. Schema 0.9 was pushed as `a9e84b8`. Section 26 implements schema
0.10 for resolved named FEEL BKMs. The final reactor passed 650 tests outside the TCK
runner. The full pinned Spark TCK passed 3,391/3,391 cases natively with zero fallback.
Schema 0.10 was pushed as `ceb52d5`. Section 27 implements and verifies schema 0.11
for inline FEEL functions with lexical capture and positional invocation. The final
reactor passed 654 tests outside the TCK runner, and the full Spark TCK passed
3,391/3,391 natively with zero fallback. Schema 0.11 was pushed as `a9472d6`.
The first artifact-only Rust proof is implemented locally in section 28. Rust
1.99.1 generated and ran a dependency-free crate from a bundled scalar 0.0
artifact and matched the Java adapter outputs. The artifact-only CI passed on
GitHub at `713f300`. Section 29 adds Rust support for the comparison and
conditional subset of 0.1, pushed as `a781815` with hosted Rust parity passing.
Section 30 adds exact Rust addition and subtraction, pushed as `5872c59`.
All nine Rust tests and hosted Rust parity passed. Section 31 adds exact Rust
multiplication, pushed as `d21c51a` with 13 Rust tests and hosted parity passing.
Section 32 adds Rust DECIMAL128 division, pushed as `0280cff` with 16 tests,
118 Java parity vectors and hosted Rust parity passing. Section 33 adds Rust
named records and field access, verified locally with 20 Rust tests passing.
Powers, contexts, tables, other Rust profiles and DSL parity remain open.
Target: **1.2.0**. Supersedes the completed 1.1.0 release/CI task.

## Requested outcome

Create **DMNIR**, a distilled, portable intermediate format containing all information needed
by an independent generator to generate code executing a DMN model. Prove consumption with
Rust. The DMNIR artifact must also generate standards-conforming DMN XML, defaulting to
**DMN 1.6**, with explicit supported-version selection. Provide a pleasant, concise DSL for
authoring the same decision semantics.

The user requested starting this task on 2026-11-03 and approved **Option A**: one authoritative,
typed, resolved, structured Protobuf DMNIR with declaration/interchange metadata. The decision
is recorded in [ADR-0026](../docs/architecture/adr/adr-0026-structured-portable-dmnir.md).
Current branch: `feature/release-1.2-dmnir`. Field layout and remaining contract decisions still
require the work-package gates. Version changes and publication remain separate actions.

## Required context

Read [general instructions](generall-prompt.md), [guardrails](details/guardrails.md), the
[evidence-backed development method](../docs/dev/evidence-backed-spec-driven-development.md),
and the [release 1.2 work package](details/release-1.2-dmnir-work-package.md).
The work package defines scope, architecture gates, dependencies and acceptance evidence.

Local paths:

- Toolkit: `C:\00-finmsg.io\dmn-compiler-toolkit`
- Ideation: `C:\00-finmsg.io\dmn-compiler-toolkit-ideation`
- Maven: `C:\10-tools\apache-maven-3.9.12\bin\mvn.cmd`
- Prompts: `prompts/generall-prompt.md` and `prompts/details/guardrails.md`

Review IDEA-003, IDEA-004, IDEA-010, SPEC-001, SPEC-002, `proto/dmn_ir_v1.proto` and
`examples/credit_risk.decision` in ideation. They are proposal inputs, not accepted production
contracts. The user's DMN 1.6 default supersedes SPEC-002's DMN 1.5 default.

## Architecture constraints

Follow the normative architecture principles and accepted ADRs, particularly ADR-0025/0026.
ADR-0020–0024 are reserved future topics in the ADR index; preserve those IDs and inspect
both accepted decisions and reservations before allocating any new ADR number.
Keep the source semantic model, authoritative portable DMNIR and process-local Runtime IR
contracts distinct. The artifact stores decision logic once; code generators and DMN export
consume that same resolved structured graph. Retain stable identities and declarations needed
for self-contained export. Derive optimized execution graphs locally. Do not introduce separate
stored export/execution policies or rely on original source files for DMN regeneration.

Reuse FEEL parsing, semantic analysis and lowering. Rust consumes a verified artifact without
DMN XML, FEEL parsing or JVM compiler services. Preserve exact numeric/temporal semantics,
stable external identities, deterministic output, bounded validation and explicit compatibility.

## Work sequence

1. R12-00: inventory and agree feature, semantics, version and consumer profiles.
2. R12-01: review architecture, export-retention strategy and module boundaries.
3. R12-02–04: wire contract, bounded verifier and Java artifact round-trip/parity.
4. R12-05: artifact-only DMN export, default 1.6, with validation and behavioral parity.
5. R12-06–08: DSL readability, parser/formatter, CLI workflows and inline tests.
6. R12-09: independent Rust generation, compilation and execution proof.
7. R12-10–11: compatibility/security hardening and release readiness.

R12-02a/R12-03a/R12-04a are complete. R12-04a adds a compiler-layer adapter from verified
scalar DMNIR to process-local Runtime IR and evaluation by namespace-qualified element ID.
The initial eight adapter tests and final nine adapter/generated-Java parity tests passed with
zero failures/errors/skips. Focused tests do not measure native/fallback routing. Full reactor
verification passed: 542 tests, zero failures/errors/skips. Current-run Spark routing across four
instrumented suites: native 3,034/3,153 (96.23%), fallback 119/3,153 (3.77%), unknown zero;
60 failed SQL actions are counted separately, not JUnit failures or official TCK results.
See work-package section 14 and `docs/dmnir.md` for contracts, scope and per-run evidence.
R12-04b now implements scalar DMN-to-artifact emission; its 11 final focused producer/generated-Java
tests passed, including a separate-JVM artifact consumer. Full-reactor verification passed:
553 tests, zero failures/errors/skips (`target/release-1.2-r12-04b-reactor.log`). Current-run Spark
SQL-action totals: native 3,034/3,153 (96.23%), fallback 119/3,153 (3.77%), unknown zero;
60 failed SQL actions are separate from that completed-action denominator and JUnit failures.
This is regression evidence, not official TCK coverage. Its initial
table-rejection test failure was fixed before the final focused pass. Tooling tests (73), strict
docs and documentation validation passed. See work-package section 15 for the approved API and
per-run evidence. Broader expression production remains pending.
R12-05a now implements artifact-only DMN 1.6 synthesis for an explicitly admitted scalar subset.
Ten final focused exporter/generated-Java tests passed, including offline official-XSD validation
and recompiled execution parity. Full reactor passed: 562 tests, zero failures/errors/skips;
73 tooling tests, strict docs and documentation validation passed. See work-package section 16 and
`target/release-1.2-r12-05a-reactor.log`. Current-run Spark regression actions: native 3,034/3,153
(96.23%), fallback 119/3,153 (3.77%), unknown zero; 60 failed SQL actions counted separately.
This does not replace the official TCK checkpoint. Broader DMN export, independent DMN
engine validation, richer wire expressions, DSL and Rust remain pending.
The established official Spark TCK checkpoint remains **3,391/3,391 native passes (100%),
zero fallback (0%)**, as recorded in `docs/tck-conformance.md`. The mixed regression-action
fallback percentage above includes fallback-path tests and must not be described as a TCK
regression or evidence that the pinned-suite zero-fallback goal is incomplete. No new full TCK
run was performed for R12-04b; the default reactor does not replace that checkpoint.
R12-02b/03b/04c/05b now adds schema 0.1 unary/binary/conditional expressions while retaining
0.0 artifacts. Wire/operator validation, source admission, runtime adaptation, generated Java and
artifact-only DMN 1.6 export are implemented for the reviewed scalar signatures. Final reactor:
576 tests, zero failures/errors/skips; 73 tooling tests and strict docs passed. Current-run mixed
Spark regression actions: native 3,034/3,153 (96.23%), fallback 119/3,153 (3.77%), unknown zero,
60 failed SQL actions separate. This is not a TCK result. Actual saved 0.0 binaries accepted 0.0
and rejected a new 0.1 artifact. See work-package section 17 for per-run evidence and the isolated
Traffic Violation expression example. Complete Traffic Violation still requires records, fields,
local contexts and tables. At the 0.1 checkpoint these changes were uncommitted; the preceding pushed baseline was `92d975c`.
Schema 0.2 now retains named record identity, supports nested/imported records and resolved field
access, and proves runtime/generated-Java/DMN 1.6 round-trip parity. Full reactor passed 590 tests
with zero failures/errors/skips (`target/release-1.2-records-reactor.log`). Current-run mixed Spark
actions: native 3,034/3,153 (96.23%), fallback 119/3,153 (3.77%), unknown zero; 60 failed SQL
actions counted separately. These include deliberate fallback tests and are not official TCK
results. The established 3,391/3,391 native, zero-fallback TCK checkpoint is unchanged.
Temporal fields/constraints, tables and local contexts remain required for complete Traffic
Violation. The existing facade's null-valued context conversion issue is documented separately;
the portable runtime and generated-Java parity checks preserve null fields.
Remaining R12-00 profile review and the broader R12-02/R12-03 contracts are not complete.
The architecture choice is accepted; do not ask for Option A approval again.
Do not mark dependent slices ready
while architecture or public-contract decisions remain open. Record measured evidence per
slice. Documentation preparation is not implementation or conformance evidence.

Release readiness requires all mandatory work-package gates, including artifact-only Rust
execution, artifact-only DMN 1.6 export, DSL parity and existing-backend regressions.
Full Rust DMN conformance, LSP/visual tooling and further native backends are separate scope
decisions. Publishing requires explicit external-side-effect authorization.

Rust named records were committed and pushed as `3b7f1e4`. Rust local contexts
(DMNIR 0.3) are implemented with sequential identity-based bindings, nested scope,
nominal record typing and propagated arithmetic bounds. All 23 Rust tests passed
locally. See work-package section 34 for the contract and verification scope.

Rust DMNIR 0.4 civil dates are implemented, including calendar validation, typed
date inputs, literals, ordering/equality and dates in records and local contexts.
All 25 Rust tests passed locally and generated Rust matched 494 Java results.
See work-package section 35. Time-of-day, offsets and durations remain outside
this profile. The preceding contexts slice was pushed as `9f26b85`.

Rust DMNIR 0.5 decision tables are implemented with UNIQUE/FIRST/ANY policies,
scalar/record outputs, defaults and unary comparisons/ranges/negation/wildcards.
All 29 Rust tests and documentation validation passed. Generated Rust matched
36 Java table evaluation vectors, including UNIQUE and ANY conflict errors.
See work-package section 36. The preceding date commit `0387552` passed hosted
Rust parity and Maven CI. Rust tests do not measure Spark native/fallback routing.

Rust schema 0.6 scalar record field constraints are implemented. Constant domains
are checked recursively at input and decision-result binding, including table
outputs/defaults. All 31 Rust tests passed and generated Rust matched 63 Java
constraint vectors. See work-package section 37. The preceding table commit
`4526649` passed hosted Rust parity. Rust native/fallback routing is not measured.

Constraint implementation `c0748d1` passed hosted Rust parity and Maven CI.
The 2026-11-11 usage checkpoint completed the full current-source Spark TCK:
3,391/3,391 native passes (100%), zero fallback (0%), failures, execution errors,
compilation errors, expected model rejections or unknown routes. See work-package
section 38 and the retained constraints checkpoint log/accounting in `target/`.
Rust 0.7 collections have not been started because the usage checkpoint rule
stopped new implementation at 19% remaining. Next work is named scalar
collections and ordered lists; schema 0.8 filters/builtins remain later scope.

The usage window reset and Rust schema 0.7 named scalar collections are now
implemented. Ordered lists preserve duplicates, nulls and exact scalar values
through imported types, record fields, local bindings and conditionals. A full
34-test Rust run and two later focused import/depth tests passed, matching 24
Java collection vectors. See work-package section 39. The known independent
README/index link-validation failures remain unchanged. Native/fallback routing
is not applicable to these Rust tests. Next is schema 0.8 filters and builtins.

Rust schema 0.8 scalar filters are implemented. Filters retain only true predicate
results, preserve order/null sources, capture outer locals and enforce item scope.
Runtime visit and repeated arithmetic budgets are checked before iteration.
All 40 Rust tests passed, including 24 Java filter vectors and budget boundaries.
See work-package section 40. Known unrelated README/index link failures remain.
Collections were pushed as `a5ab42c`; hosted CI was still queued at the last check.
Next is schema 0.9 builtins, correcting the earlier combined 0.8 wording.

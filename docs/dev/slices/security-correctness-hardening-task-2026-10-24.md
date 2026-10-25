# Task: security and correctness hardening after native TCK coverage

Date: 2026-10-24

Status: HARD-01 implemented and validated on 2026-10-25; HARD-02 is next. Remaining hardening work stays open.

Baseline commit: `518e37668f145305cd132ca5004167150804eb79`

## Objective

Address the assessment's verified security and correctness concerns while preserving
DMN/FEEL behavior and making the limits of conformance evidence explicit. Implement
the work in small, independently reviewable changes, in the priority order below.

Current priority/status roll-up is maintained in the [active backlog](../../todos/index.md).
This task owns execution-policy, escaping, comparator behavior, coverage, rejection
semantics and operational investigations. The documentation task owns public wording,
publication, evidence links and documentation CI. Cross-references are dependencies,
not duplicate implementation work. P0 is external-call policy; P1 is escaping,
comparator correctness and rejection audit; coverage and investigations are P2.

## Evidence and qualifications

Source inspection confirmed that `DmnRuntime.invokeExternalJava` loads a
model-specified class and invokes a method without a policy check in that method.
Whether an untrusted request can reach this path through every service/backend
has not yet been traced. Generated Java sort code also contains reflective calls
and swallowed invocation failures, contradicting accepted ADR-0013. Spark SQL
literal escaping uses several different helpers; a concrete defect must still be
reproduced with executing tests.

The assessment did not build or execute the project. Treat its size, coverage,
concurrency and deployment observations as investigation leads, not verified
defects. Do not assume that DMN requires unrestricted access to Java methods.

The verified baseline is:

| Official TCK classification | Cases | Share of executable cases | Share of all cases |
| --- | --- | --- | --- |
| Native Spark SQL | 2,715 | 100% of 2,715 | 80.06% of 3,391 |
| Fallback | 0 | 0% of 2,715 | 0% of 3,391 |
| Compiler-rejected: no Spark SQL executed | 676 | Excluded | 19.94% of 3,391 |

All 3,391 recorded outcomes passed; failed/errors/unknown were zero. The 676
rejections are passing negative-test outcomes, not native or fallback execution.
Their precise rejection reasons remain unaudited against the intended TCK errors.
The runner currently accepts a compiler rejection when all requested results
expect errors. These cases do not establish a Spark platform limitation.

Generator regression: 161/161 tests passed; 2,973 native SQL actions (96.15%),
119 fallback actions (3.85%), denominator 3,092, unknown zero. All 59 failed SQL
actions were expected assertions: 58 native and one fallback. These action counts
include intentional fallback tests and are distinct from official TCK cases.

## 1. External Java function policy — highest priority

Implemented and validated (2026-10-25): `ExternalJavaPolicy` is immutable and
defaults to deny. `allowOnly(Set<Method>)` approves exact trimmed class/signature
strings; whitespace inside a signature remains significant. The explicitly named
`unrestrictedTrustedUse()` mode preserves unrestricted reflection for trusted models.
Denial throws `ExternalJavaDeniedException`, a `DmnEvaluationException` subtype,
with the stable `EXTERNAL_JAVA_DENIED:` diagnostic before parameter or target class
resolution. Existing null/type/lookup failure behavior for approved targets remains.

Invocation-path trace and trust boundaries:

| Path | Policy ownership and enforcement |
| --- | --- |
| Interpreter and compiler facade | A new `DmnRuntime()` denies; an explicit constructor policy is retained by evaluation state and closures. All three descriptor invocation paths call the checked helper. The compiler facade uses the default runtime. |
| Shared helper API | The two-argument `invokeExternalJava` denies. The explicit policy overload checks before resolving parameter classes, loading the target, and invoking it. There is no global mutable policy or authorization cache. |
| Optimizer | Existing constant folding handles built-ins and IR expressions; inspection found no external reflection dispatch. Model-selected descriptors remain subject to runtime/backend checks. |
| Generated Java | Each engine has a final policy, with a denying no-argument constructor and explicit host policy constructor. Descriptor helpers use that instance policy, including BKM methods. Generated comparator reflection propagates policy denial; replacing the remaining comparator reflection is HARD-03. |
| Spark generation | Options carry an explicit policy. The bounded Java literal proof checks exact permission before reflective inspection and folds only its existing pure-JDK subset. Other generation-time runtime proofs retain default denial. |
| Spark fallback and generated runner | Each serializable UDF carries its immutable policy alongside its model payload. Generated runner source reconstructs the same explicit policy. New generation under denial cannot reuse an approved fold. |
| gRPC | Generated handlers construct a denying engine by default; the host may supply a policy through the handler constructor. Requests do not select policy. Existing INTERNAL responses retain the denial diagnostic. |
| Official TCK | A test-only exact list covers the signatures in 0076, including intentionally missing classes/methods. Official fixtures and production defaults remain unchanged. |

Generated SQL, runner sources and compiled Java are trusted executable artifacts.
Spark artifacts are specific to the generation policy, including already folded
constants; regenerate from the original IR when changing policy. Do not share a
cached generated artifact across trust configurations. A runtime policy controls
model-selected targets, not arbitrary host Java code, returned host objects, or the
transitive behavior of an approved method. Approving a method grants its full effects.

Fixed generator-selected calls remain independent of model-selected Java policy:
`Math.pow(double,double)` in exact-decimal SQL, and the temporal factories
`LocalDate.of/parse/ofEpochDay`, `LocalTime.of/parse`, `OffsetTime.parse`,
`LocalDateTime.parse`, `ZoneOffset.of/ofTotalSeconds`, `Period.of/parse`, and
`Duration.ofSeconds/parse`. Their class and method names are selected by generator
code; model values enter as arguments. The separate pure-JDK literal proof is still
model-selected and therefore requires policy permission, even for these JDK classes.

Initial validation: the focused runtime command passed 12 JUnit tests,
zero failures/errors/skips. Native/fallback SQL actions 0/0, denominator 0, rates
undefined, failed/unknown SQL actions 0/0. At that point, later backend changes required fresh
verification; that initial result alone did not establish preserved conformance. An initial build lacked
the TCK profile and failed reactor selection before testing. A later build with
that profile began TCK execution because `tck.skip` overrides `skipTests`; it was
interrupted and is not completed conformance evidence.

The first complete generator regression ran 162 tests: 161 passed, one failed
because the new policy regression compared decimal `1.0` with `1` using scale-
sensitive equality. The denial assertions passed. That test now compares the full
approved value with the shared runtime. SQL actions in that run: 2,974 native
(96.12%), 120 fallback (3.88%), denominator 3,094, unknown 0; all 60 failed SQL
actions were expected assertions. Runtime tests passed 12/12 and generated Java
tests passed 3/3 in that run. Log: `dmn-runtime/target/external-java-policy-regressions.log`.
The subsequent run adds a generated-comparator denial test and a real in-process
gRPC denial/permission test. It passed 12 runtime, 4 generated Java, 4 gRPC, and
162 Spark generator tests (182 total), with no failures/errors/skips. Its SQL
actions were again 2,974 native (96.12%), 120 fallback (3.88%), denominator 3,094,
unknown 0; all 60 failed SQL actions were expected assertions. Log:
`dmn-runtime/target/external-java-policy-regressions-final.log`. A build-only attempt before
these regressions stopped on a Windows mapped-file formatting lock in the gRPC
generator, with no tests or SQL execution; the subsequent formatter retry passed
that file. The initial cache-restricted attempt also executed no tests or SQL.

The focused official 0076 run passed 54/54 outcomes: 18 interpreter, 18 generated
Java, 18 Spark. Spark had 18 native (100% of 18 executable), zero fallback (0%),
zero compiler rejections, failed/errors/unknown zero, Maven exit 0. This is focused
evidence, not full-suite conformance. Log/accounting:
`dmn-tck-runner/target/external-java-policy-focused-tck.log` and
`external-java-policy-focused-accounting.json`. The full optimized Spark TCK result is recorded below.

Source state: baseline `518e376` plus uncommitted HARD-01 changes. After correcting
two source comments to reflect host policy ownership, the installed artifacts were
rebuilt with tests skipped. These comments do not alter execution behavior. Target
and installed Spark generator JARs match SHA256
`7C6762A5F08A8676652E30BDF028F136CBA5161475C3B7DC47DDD2B759404478`;
runtime JARs match
`02506309366F64C991C3617ACDD3B28C54E71AD2FD5EDD6E01194EF3614C4A1F`.
Generated Java JAR SHA256:
`46AA7A6D45D02E4B677DD59D5A019519AA9146340AABCB83839A254602829EE2`.
Production sources remain frozen during official validation. Formatting check,
canonical documentation facts (12 reactor modules), strict MkDocs and whitespace
checks passed; they executed no SQL (denominator 0, native/fallback 0/0 with
undefined rates, failed/unknown 0/0).

Full optimized Spark checkpoint, 2026-10-25: **3,391/3,391 outcomes passed**;
**2,715 native (100% of 2,715 executable; 80.06% of all cases), zero fallback
(0% of executable cases)**. Separately, **676 expected compiler rejections
(19.94% of all cases)** executed no SQL. Failed, execution errors, unexpected
compilation errors, unsupported, excluded, invalid and unknown outcomes: zero.
All case keys, statuses and route diagnostics exactly match the 2026-10-24
temporal-chain baseline; no route or rejection changes. The reasons for the
676 rejections remain unaudited as before.

Maven exited 0, but this run emitted the Surefire warning
`Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0).`
Assertions passed; clean fork shutdown remains unverified. This warning belongs
to this checkpoint and is not attributed to the earlier baseline. Investigating
shutdown/thread ownership remains operational follow-up, without a proven cause.

Evidence under `dmn-tck-runner/target/`: `external-java-policy-full-tck.log`,
`external-java-policy-full-accounting.json`, `external-java-policy-full-rejections.tsv`,
and `external-java-policy-baseline-comparison.json`. Target/installed generator
hashes remained identical after the run. Reproduction uses the installed artifacts:

```powershell
$env:SPARK_LOCAL_HOSTNAME = 'localhost'
mvn --batch-mode -Ptck -pl dmn-tck-runner -Dtest=OfficialTckSuiteTest `
  -Dtck.skip=false -Dtck.requireFull=true -Dtck.backend=sparksql `
  -Dtck.modelVariant=optimized -Dspark.ui.retainedJobs=20 `
  -Dspark.ui.retainedStages=20 -Dspark.sql.ui.retainedExecutions=40 test
```

The parent supplies the 4 GB heap and module-opening JVM flags. The focused
three-backend run uses `-Dtck.requireFull=false -Dtck.filter=0076 -Dtck.backend=all`.
No official fixture was edited. No commit or push was performed. At 18% remaining
in the five-hour window, the prompt's checkpoint rule stopped new implementation;
verification and handover completed. Resume with HARD-02's executing literal
escaping reproducers, then HARD-03. Public documentation remediation remains open.

- [x] Trace model-specified Java invocation through runtime evaluation, constant
  folding, generated Java, Spark fallback, and gRPC entry points. Record where
  model trust and execution policy enter each path.
- [x] Introduce a policy that denies model-defined external Java execution by
  default. Allow approved methods by exact class and method signature; make any
  unrestricted compatibility mode an explicit trusted-use opt-in.
- [x] Check the policy before class loading or initialization and before invoking
  the target. Ensure optimization, generated code, helper APIs and cached models
  cannot bypass the policy or reuse authorization from a different policy.
- [x] Keep fixed generator-selected JDK built-ins separate from model-selected
  external functions. Document and review their permitted targets explicitly.
- [x] Return a defined, consistent policy-denial diagnostic across backends. Keep
  denial distinguishable from ordinary FEEL null/type errors where the API permits.
- [x] Test default denial, explicit permission, wrong signatures, attempts to use
  an unapproved class, and absence of side effects or class initialization on denial.
- [x] Demonstrate that process termination cannot be invoked by an untrusted model.
  Use a harmless test target or an isolated child process; never call `System.exit`
  in the test runner JVM.
- [x] Configure the official external-function cases with the minimum explicit
  test policy they require. Do not weaken production defaults or edit TCK fixtures
  merely to retain a passing result.

Acceptance: all identified entry points enforce the policy, approved calls retain
their semantics, denial is tested without executing the forbidden operation, and
trust assumptions plus compatibility changes are documented.

## 2. Spark SQL literal escaping — correctness priority

- [ ] Inventory literal construction in the emitter, generator, calendar helpers,
  exact arithmetic helpers and the new temporal functions.
- [ ] Execute reproducers for apostrophes, backslashes, combined quote/backslash
  sequences, newlines, valid Unicode and strings resembling SQL syntax.
- [ ] Identify the Spark parser settings affecting string literals. State which
  settings generated SQL supports; test supported configurations or reject
  incompatible configurations clearly.
- [ ] Consolidate string literal encoding in one verified helper. Keep identifier
  quoting and binary payload encoding as separate operations.
- [ ] Check the generated Java/SQL boundary so each layer escapes text exactly once.
- [ ] Compare complete returned values with the runtime, including model constants
  and temporal constructor inputs. Check that text cannot become SQL syntax.

Acceptance: executing regressions demonstrate exact value preservation, all relevant
literal sites use the shared convention, and supported parser assumptions are
documented. Inconsistent style alone must not be reported as a reproduced bug.

## 3. Generated comparator reflection and failure propagation

- [ ] Replace reflective sort-comparator dispatch with an explicit callable
  interface or strongly typed adapter shared with generated functions.
- [ ] Define and preserve behavior for comparator true, false, null and errors.
  Do not silently turn invocation failures into equality.
- [ ] Exercise comparator closures, bindings, nulls, ties and failures through
  generated Java, comparing behavior with the shared runtime.
- [ ] Search emitted sources for reflective dispatch and swallowed exceptions
  after the change. Investigate other occurrences rather than assuming sort is
  the only path.
- [ ] Resolve ADR-0013 implementation compliance and any deliberate external-function
  exception as part of HARD-03. Supply verified behavior and limitations to the
  documentation task, which owns publishing the updated parity descriptions.

Acceptance: generated comparator execution uses the intended callable API, failures
follow the defined semantics, and architecture claims match the implementation.

## 4. Coverage baseline and missing error paths

- [ ] Measure current line and branch coverage per module using a documented test
  profile. Distinguish default-build tests from opt-in TCK coverage.
- [ ] Select targeted tests for malformed input, coercion boundaries, denied external
  calls and backend error propagation based on uncovered behavior.
- [ ] Exclude generated ANTLR sources and other justified generated artifacts from
  hand-written-code coverage metrics.
- [ ] Add JaCoCo checks using measured baselines and an agreed ratchet policy.
  Do not invent thresholds or derive quality from test/main line-count ratios.
- [ ] Record commands, exclusions and baseline values so CI results are reproducible.

Acceptance: CI detects coverage regression for the agreed scope, and focused tests
cover identified behavioral gaps rather than duplicating implementation details.

## 5. Audit expected TCK rejections

- [ ] Classify all 676 saved rejection diagnostics and relate each case to its
  expected error condition. Repeated diagnostic categories are not a substitute
  for case-level attribution.
- [ ] Distinguish correct static rejection, a different compiler error masking the
  intended error, and an error that requires runtime execution to test properly.
- [ ] Tighten acceptance where necessary so an unrelated compilation failure cannot
  satisfy an error-expecting case. Preserve official fixtures unchanged.
- [ ] Where runtime evaluation is required, define a correct executable error plan
  and verify its behavior before classifying it as native or fallback.
- [x] Runner summary distinguishes native/fallback passes from compiler rejection
  with no SQL execution and prints non-rejected/all-case denominators. Implemented
  in `518e376`; this completed reporting work is not part of the remaining audit.
- [ ] Check unclassified/unknown routes explicitly in the accounting/ratchet work
  and prevent them from being hidden by aggregate pass counts. Public propagation
  of the existing reporting distinctions belongs to the documentation task.

Acceptance: every retained expected rejection has traceable justification; unresolved
cases remain visibly unaudited. Explain any reclassification or denominator change.
Do not preserve 676 artificially or describe compiler rejection as SQL execution.

## 6. Follow-up investigations and maintainability

- [ ] Establish runtime ownership and thread-safety guarantees with concurrent
  evaluation tests using distinct inputs and policies. Inspect mutable shared state;
  absence of concurrency primitives alone is not evidence of a defect.
- [ ] Clarify whether gRPC provides service handlers or a deployable server. Document
  ownership of authentication, TLS, request limits and deadlines at that boundary.
- [ ] Extract expression families from large classes gradually, after relevant
  behavioral tests exist. Keep behavior changes separate from structural moves.

Acceptance: verified findings become bounded follow-up tasks. Avoid a broad rewrite
or adding deployment machinery outside the module's intended responsibility.

## Validation and handover requirements

1. Reproduce a correctness issue before fixing it; use focused behavior tests for
   security policy changes. Record observed results, not inferred success.
2. After every test run, report native/fallback action counts and rates with their
   denominator, plus failed and unknown counts. Separate expected SQL errors from
   test failures. If no SQL ran, report denominator zero and undefined rates.
3. Keep SQL action counts, JUnit test counts and official TCK case counts separate.
   Internal Spark execution identifiers are not progress counts for test cases.
4. Run affected module regressions and the full generator suite as appropriate.
   Run the full official TCK for backend/policy changes before claiming preserved
   conformance. Follow the agreed full-TCK cadence during extended work.
5. Freeze production sources while tests/install/TCK execute. Verify that the
   installed generator artifact matches the tested artifact and record its hash.
6. Preserve official TCK fixtures. Report any outcome, route or rejection changes
   against the baseline; never hide a new rejection by changing the denominator.
7. Run required formatting checks, documentation fact verification, strict MkDocs
   build and whitespace checks. Update handover evidence and known limitations.
8. Keep each implementation batch reviewable. Commit/push only when authorized;
   this task-document request does not itself request implementation or a commit.

## References

- [Verified temporal checkpoint and rejection semantics](native-temporal-chains-2026-10-24.md)
- [ADR-0013: no reflection in generated code](../../architecture/adr/adr-0013-no-reflection-in-generated-code.md)
- [Backend parity matrix](../../architecture/backend-parity-matrix.md)
- [Spark SQL generator work](../../todos/dmn-generator-sparksql.md)
- [TCK runner work](../../todos/dmn-tck-runner.md)

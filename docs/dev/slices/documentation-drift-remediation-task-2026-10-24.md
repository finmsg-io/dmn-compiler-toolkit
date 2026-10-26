# Task: reconcile public documentation with implementation and evidence

Date: 2026-10-24

Status: In progress; public capability/evidence refresh verified 2026-10-26; link and publication-policy checks remain open

Reviewed implementation baseline: `f2af3954273a60c56dff3de5804fb05057ab37c2`.
Original intake baseline: `518e37668f145305cd132ca5004167150804eb79`.

## Objective

Make public documentation accurately describe module maturity, backend capabilities
and verified conformance. Strengthen automated checks for preventable drift while
preserving historical evidence with its original provenance and limitations.

Implement this task separately from the
[security and correctness hardening task](security-correctness-hardening-task-2026-10-24.md).
The [active backlog](../../todos/index.md) tracks priorities and current status.
This task owns public documentation, evidence publication and documentation checks.
Backend behavior, execution policy, coverage enforcement and rejection-reason auditing
belong to the hardening task; reference its results rather than duplicate delivery.

Spark publication checkpoint (2026-10-26): the README badge target, default-reactor
wording and TCK commands were corrected. Public conformance and Spark architecture
now record 3,391/3,391 native cases, zero fallback, and zero failures/errors/blocks,
with retained per-case accounting. This fulfills the user-requested achievement
publication; capability-matrix review, broader link review and drift-check expansion
below remain open. See [verified execution evidence](security-correctness-hardening-task-2026-10-24.md#5-audit-expected-tck-rejections).

## Confirmed starting problems (before this publication)

- README's TCK badge links to missing `docs/tck/index.md`; the published source
  page is `docs/tck-conformance.md`.
- README describes Spark SQL and gRPC as incubating opt-in modules, although both
  are in the default reactor. Its build examples and module descriptions need review.
- The public conformance page describes interpreter and Java evidence but omits
  the verified Spark SQL checkpoint.
- The backend parity matrix has an old review date, local filesystem links,
  inaccurate reflection claims and oversimplified type representations. Aggregate
  TCK results do not substantiate its blanket per-feature parity guarantees.
- The matrix is not directly listed in navigation. Development slices and proposal
  material need an explicit publication/navigation policy. Recount orphan pages
  during implementation; the review's page totals are not a maintained baseline.
- Root-level `TCK_SPARKSQL_STATUS.md` and several documentation pages accumulate
  chronological handover entries instead of presenting concise current guidance.
- `tools/verify_documentation.py` checks reactor-module headings/order and limited
  certification wording. A passing result is not general documentation validation.
  Strict MkDocs builds also do not establish semantic accuracy of published claims.

## 1. Repair public entry points — first priority

- [ ] Fix the README badge target and validate every README relative link.
- [ ] Align README module status, default build descriptions and commands with
  `pom.xml` and the documented graduation decision. Do not infer production
  readiness merely from default-reactor membership.
- [x] Review roadmap wording so implemented Java and Spark generation are clearly
  distinguished from any still-planned native machine-code backend.
- [x] Update the public conformance page with separately identified interpreter,
  Java and Spark checkpoints. Describe the current CI backend jobs accurately.
- [x] Preserve careful self-verified-conformance terminology without implying
  third-party certification.

Acceptance: readers can follow the headline evidence link, identify which modules
build by default, and understand each backend's measured status without reading a
handover journal. Commands and module descriptions agree with the actual reactor.

## 2. Refresh the capability and parity matrix

- [x] Replace `file:///` and machine-specific paths with links that work on GitHub
  and the published site. Add the matrix to the intended architecture navigation.
- [x] Replace blanket parity guarantees with per-backend capability descriptions:
  representation, supported operations, native eligibility, fallback conditions,
  known limitations and supporting test/evidence links.
- [x] Correct numeric, temporal, duration, list and context representations against
  the implementation. Distinguish ordinary Spark columns from exact projections
  and versioned binary payloads.
- [x] Describe nanosecond, offset, named-zone and duration-identity preservation
  only for the paths where it is implemented and verified.
- [x] State that the temporal native path uses Spark `try_reflect` built-ins to
  call selected JDK factories. Distinguish this from a FEEL evaluator UDF and from
  unrestricted model-defined Java calls; do not describe it as zero reflection.
- [x] Publish an accurate description of current Java comparator reflection and
  ADR-0013 compliance; link HARD-03 for the owned code/behavior remediation.
  Refresh the description when that separately verified change is delivered.
- [x] Review hit-policy and error-semantics rows against executing tests instead
  of retaining unsupported generalizations.
- [x] Update the review date after reviewing the content, not merely the metrics.

Acceptance: each material capability claim has traceable support and a stated
boundary. Aggregate pass rates are presented as inventory evidence, not proof of
every possible input, type combination or FEEL model.

## 3. Establish current status and evidence provenance

- [x] Choose one concise public location for current Spark SQL conformance and
  limitations; link it from README, the conformance page and the parity matrix.
- [x] Identify the machine-readable evidence for each checkpoint by backend,
  model variant, upstream TCK revision, toolkit commit or source state, artifact
  identity, run date and command/profile.
- [x] Publish reviewed checkpoint evidence in a tracked, durable location. Public
  claims must not depend solely on ignored local `target/` files.
- [x] Keep interpreter/Java evidence separate from Spark evidence unless the same
  revision and compatible run scope have been established. Do not silently turn
  historical 6,782-outcome evidence into a claimed combined three-backend run.
- [x] Propagate the implemented runner distinctions into public guidance: native,
  fallback, compiler-rejected/no SQL, failures, errors and unknowns, with explicit
  executable-case/all-case denominators. Runner reporting is already implemented;
  this item owns publication, not a second runner change or the rejection audit.
- [x] Separate assertion outcomes from process health: record Maven exit status,
  timeouts and forced-exit warnings. An unresolved warning remains attached to
  its own checkpoint; do not label that run unqualifiedly error-free or transfer
  its warning to a different successful run.

Current verified Spark checkpoint: **3,391 native passes (100%), zero fallback
(0%), failures, compilation blocks, errors or unknown routes**; denominator all
3,391 cases. Source `f2af395`, pinned TCK revision and artifact/run identity are
recorded in the [public conformance record](../../tck-conformance.md). The retained
JSON is `docs/assets/tck-accounting-sparksql-native.json`.

Historical intake baseline (2026-10-24; rejection-as-pass, not current conformance):

| Classification | Cases | Executable-case share | All-case share |
| --- | --- | --- | --- |
| Native Spark SQL | 2,715 | 100% of 2,715 | 80.06% of 3,391 |
| Fallback | 0 | 0% of 2,715 | 0% of 3,391 |
| Compiler-rejected: no Spark SQL executed | 676 | Not included | 19.94% of 3,391 |

That historical runner reported 3,391 passing outcomes and Maven exit zero,
but 676 cases did not execute. They were not native passes. The later strict
checkpoint above supersedes those counts without rewriting the historical evidence.
See the [temporal checkpoint](native-temporal-chains-2026-10-24.md).

Acceptance: current claims resolve to durable, identifiable evidence, and readers
can distinguish SQL execution from expected compiler rejection and historical runs.

## 4. Separate published guidance from working history

- [ ] Inventory Markdown pages and classify them as public guidance, historical
  evidence, proposal/example material or unpublished working notes.
- [ ] Decide which pages belong in navigation, which are intentionally reachable
  through an index, and which should be excluded from the published site. A page
  outside direct navigation is not automatically an error.
- [ ] Give intentional exceptions an explicit policy or allowlist with rationale.
  Add checks for pages that violate the chosen policy.
- [ ] Move or archive the root Spark status journal under an appropriate development
  history location. Update links and consider a short compatibility pointer at the
  old path if external references exist.
- [ ] Replace chronological checkpoint prefixes in public architecture/status pages
  with current summaries and links to dated evidence.
- [ ] Preserve historical numbers, warnings and source context. Do not rewrite old
  checkpoints to make them agree with today's results.

Acceptance: public pages explain the current system; working history remains
discoverable and clearly dated. No useful evidence is deleted simply to silence
navigation warnings.

## 5. Extend automated documentation verification

- [x] Retain the existing reactor-module and certification-wording checks, while
  making their scope explicit in tool output and contributor documentation.
- [ ] Check relative links in README and the agreed published-document scope,
  including reference-style links, images, fragments and URL-encoded paths where
  applicable. Resolve against the source page and documented MkDocs conventions.
- [ ] Reject machine-specific `file:///` links in published guidance. Explicitly
  scope or allowlist historical quotations/examples instead of silently ignoring
  all development documentation.
- [ ] Validate navigation targets and enforce the agreed publication/orphan policy.
- [x] Validate maintained current metric blocks against their designated accounting
  evidence. Check backend/revision/scope compatibility as well as totals and
  percentages. Do not grep every historical number against the latest JSON.
- [x] Fail clearly when designated evidence is missing, malformed or internally
  inconsistent, including duplicate case keys or inconsistent status totals.
- [ ] Add focused tool tests for broken links, invalid fragments, local paths,
  metric mismatches and intended exceptions. Include positive cases so valid
  archived history and intentionally indexed pages remain supported.
- [ ] Run all planned checks in CI and review publication/link severities. The
  evidence checks and their tests already run through the existing CI and docs
  deployment workflows; general link/navigation policy checks are still pending.

Acceptance: representative regressions produce actionable file/line diagnostics
and fail CI; valid current documents and explicitly permitted historical material
pass. Documentation checks do not imply that architectural claims were proved.

## Verified continuation - 2026-10-26

The capability matrix was rewritten after inspecting schema/codec mappings,
JVM temporal handling, native emitter behavior and HARD-03 evidence. Machine-local
links, unsupported timing/Photon claims and blanket parity/reflection guarantees
were removed. The matrix is now in MkDocs architecture navigation. The roadmap
records completed typed comparator dispatch and all-3,391 native execution.

`tools/verify_documentation.py` now validates the designated Spark checkpoint
against the retained ledger: full pinned inventory, unique case keys, exact
backend/revision, status totals, all-native outcomes and current table counts/rates.
Historical metrics are not compared with the current ledger. Missing/malformed
evidence and misleading fallback/static-rejection routes fail explicitly.
Existing CI and docs-deployment commands run the enhanced checker and tests.

Verification: 10 documentation tool tests passed; canonical facts passed for
12 default reactor modules; strict MkDocs and whitespace checks passed. The
README relative-path scan found all six detected local link/image targets exist;
this is not yet complete fragment/reference-link validation. These checks run
no Spark SQL: native/fallback actions 0/0, denominator 0, rates N/A, failed/unknown
SQL actions 0/0. The full Spark TCK remains the measured 3,391-native/zero-fallback
checkpoint; no runtime or generator code changed in this continuation.

Remaining work: complete link/fragment/image checking, machine-local-path checks
across an explicit published scope, navigation/orphan classification and policy,
and journal/history separation. Add negative and positive tool tests for those
checks. Changes in this continuation are uncommitted; no new push was requested.

## Delivery and validation

1. Deliver small batches: public entry-point fixes; capability/evidence refresh;
   publication/history organization; automated checks and CI wiring.
2. Preserve the existing hardening task and unrelated changes. Update the slices
   index and affected references when moving or adding documents.
3. Run documentation verifier tests after changing verification logic, canonical
   fact checks, strict MkDocs build, link/navigation checks and whitespace checks.
4. Documentation-only edits do not require a new full TCK. Reuse correctly scoped
   verified evidence; rerun backend tests if executable behavior is changed.
5. For every test run, report native/fallback SQL action counts and rates with the
   denominator, failed and unknown counts separately. For documentation tool tests
   with no SQL execution, report zero actions, denominator zero and undefined rates.
6. Finish with a handover identifying repaired claims, remaining unaudited items,
   published/archived locations and exactly which automated checks now exist.

## References

- [Security and correctness hardening task](security-correctness-hardening-task-2026-10-24.md)
- [Backend parity matrix](../../architecture/backend-parity-matrix.md)
- [Public TCK conformance page](../../tck-conformance.md)
- [Documentation structure](../../documentation-structure.md)
- [Verified temporal checkpoint](native-temporal-chains-2026-10-24.md)

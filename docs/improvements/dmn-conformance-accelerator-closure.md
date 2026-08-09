# DMN conformance accelerator closure

Status: decision candidate

Created: 2026-08-09

Scope: close evidence and product gaps in IDEA-001 after implementation of the TCK execution path

## Assessment

IDEA-001 is **substantially implemented as a dual-engine TCK execution suite**, but it is not yet
complete as the proposed **DMN Conformance Accelerator**.

The hard technical work is present:

- the official TCK repository is pinned as a Git submodule at
  `20274cd2ba9cad805db6114f331c743f4b2603a1`;
- TCK XML values include scalar, structural, and temporal forms;
- models run through the compiler and interpreter;
- generated Java is compiled and evaluated in memory;
- interpreter and generated results are compared with expected values;
- missing TCK assets fail instead of producing a false green build;
- CI initializes submodules and runs the module in the Maven reactor.

What remains is not another semantics program. It is the smaller but important work of proving that
every intended catalogue entry was accounted for, preserving actionable results, and aligning public
claims with that evidence.

## Rating

| Dimension | Rating | Reason |
| --- | ---: | --- |
| Core TCK ingestion and execution | 8/10 | Official corpus, value decoding, interpreter, and generated Java paths exist |
| Backend parity coverage | 8/10 | Both primary engines are exercised against shared cases, though comparison policy is embedded in tests |
| Catalogue accounting | 3/10 | Several discovery, parsing, and compilation conditions silently `continue` |
| Failure classification and diagnosis | 3/10 | JUnit assertions exist, but IDEA-001 result categories and phase-aware structured outcomes do not |
| Reproducibility and focused use | 5/10 | The corpus revision is pinned, but stable case filtering and one-case reproduction are absent |
| Durable conformance evidence | 3/10 | No canonical JSON/XML result artifact or deterministic generated summary owns the claims |
| Documentation integrity | 4/10 | Counts and terminology conflict across documents; some pages still say “certified” |
| CI regression protection | 6/10 | Missing assets fail and the suite runs, but CI does not verify expected catalogue/case counts or publish a TCK-specific report |
| Overall IDEA-001 completion | **6/10** | Strong conformance test implementation; incomplete accelerator and evidence contract |

This rating does not mean the engine is 60% conformant. It means roughly six tenths of the broader
IDEA-001 product capability is demonstrably present. Engine conformance must be stated only from a
complete, classified run.

## Highest-priority gaps

### G1 - Silent catalogue loss

Severity: **critical**

Value: **very high**

Effort: **small to medium**

`OfficialTckSuiteTest` silently skips an entry when:

- no adjacent DMN model can be selected;
- test-case XML parsing throws;
- no DMN sources are collected;
- compilation throws;
- compilation returns unsuccessful diagnostics.

The resource-root guard fixes only the “submodule entirely missing” failure. It does not prove that
all intended XML files, models, and test cases reached assertions.

This directly conflicts with statements such as “all 150 XML files” and “no exclusions.” A compile
failure is precisely the conformance evidence the accelerator should retain, not a reason to omit a
case from the denominator.

Recommended correction:

1. Discover the catalogue into explicit entries before execution.
2. Give every entry exactly one terminal classification.
3. Assert expected discovered, executable, unsupported, failed, and skipped counts.
4. Fail the strict conformance gate on every unapproved non-pass classification.
5. Allow exclusions only through a reviewed manifest with a reason and tracking reference.

### G2 - No structured classification model

Severity: **high**

Value: **high**

Effort: **medium**

IDEA-001 proposed distinct outcomes such as passed, unsupported, compilation error, execution error,
invalid TCK case, and skipped. The current official suite mainly produces dynamic JUnit tests and
silently discards several pre-test failures.

Introduce a small backend-neutral result record containing:

- suite revision, compliance level, model/test identifier, and case identifier;
- backend;
- terminal status;
- failing phase and diagnostics;
- expected and actual canonical values or their structural difference;
- declared capability/exclusion, when applicable.

JUnit should consume this model and enforce policy. JSON and Markdown should be projections of the
same results rather than separate handwritten truths.

### G3 - Claims and counts do not have one owner

Severity: **high**

Value: **high**

Effort: **small to medium**

Current documents mention different totals, including 146 and 150 XML files, 3,611 cases, 621 cases,
72 models, and 82 executed tests. Some pages use “self-verified conformance”; others still use
“certified” or “guaranteed.”

The TCK repository also contains compliant and non-compliant/experimental areas. A raw recursive XML
count is not automatically the denominator for a DMN 1.5 CL2/CL3 claim.

Recommended correction:

- generate counts and per-backend status from the structured result artifact;
- define the exact included catalogue roots and approved exclusions;
- make `docs/tck-conformance.md` a generated or evidence-linked projection;
- remove copied numeric claims from other documents or generate their marked sections;
- use “self-verified against pinned OMG DMN TCK revision” unless external certification exists.

### G4 - No focused case reproduction

Severity: **medium**

Value: **high for maintainers**

Effort: **small**

The proposed `-Dtck.case=<stable-id>` workflow is not implemented. Contributors must run or debug the
whole suite, and there is no common filter by case, model, compliance level, or backend.

Add deterministic filtering with exact-match semantics and fail when a requested filter selects
nothing. Print the exact reproduction command with every failure.

### G5 - Comparator policy is test-local and potentially permissive

Severity: **high**

Value: **high**

Effort: **medium**

The official-suite normalizer is embedded in `OfficialTckSuiteTest`. Numeric values are rounded to
eight decimal places before comparison, and strings surrounded by quote characters are rewritten.
That may be appropriate for particular FEEL cases, but it can also hide precision defects or conflate
serialization artifacts with semantic values.

Extract one reviewed FEEL-aware canonical comparator shared by every backend. Its equality and
tolerance rules must be explicit and tested for:

- arbitrary-precision decimals and numeric special cases;
- null and three-valued logic;
- lists and contexts, including ordering rules;
- dates, times, date-times, time zones, and durations;
- ranges and function/external values if included;
- structural difference paths.

Do not change comparison tolerance merely to make the corpus green.

### G6 - Multi-model root selection is implicit

Severity: **high**

Value: **medium to high**

Effort: **medium**

All `.dmn` files in a directory are collected, but the compilation root is `sources.get(0)`. File
iteration order is not a semantic root-selection rule and may differ across filesystems. The initially
derived adjacent model path is not used to select the root.

Resolve the root deterministically from the test-file basename or explicit TCK metadata, then use the
remaining sources as imports. Add Windows/Linux tests for multi-model cases and fail on ambiguity.

### G7 - Generated-Java execution duplicates adapter logic

Severity: **medium**

Value: **medium**

Effort: **medium**

Compilation, slot mapping, generated-source compilation, result extraction, and normalization are
implemented directly in test classes. `OfficialTckSuiteTest`, `TckFullConformanceTest`, and the JAR
test contain overlapping logic, increasing drift risk.

Create small interpreter and generated-Java backend adapters behind one runner contract. Keep JUnit
responsible for test presentation and policy, not engine integration details.

### G8 - No durable TCK-specific CI artifact or baseline diff

Severity: **medium**

Value: **high for releases**

Effort: **medium**

CI prints a generic total from all Surefire XML reports. It does not publish a TCK-specific inventory,
per-backend results, exclusions, suite revision, or change from the accepted baseline.

Upload the structured TCK result and generated summary. On a TCK revision update, show newly added,
removed, passed, failed, and unsupported cases. Do not introduce an external database initially.

### G9 - IDEA and roadmap status are stale

Severity: **low**

Value: **medium**

Effort: **small**

IDEA-001 remains `exploring`, while the development plan says the associated conformance milestone is
done. After the evidence gate is corrected, mark the implemented idea appropriately and move remaining
enhancements into this improvement proposal rather than maintaining two conflicting roadmaps.

## Recommended scope

Do not reopen the completed semantics work or build a general conformance service. Deliver three
thin slices.

### C0 - Make the claim trustworthy

- replace silent `continue` paths with terminal classifications;
- define included catalogue roots and expected counts;
- select multi-model roots deterministically;
- fail strict CI on unaccounted entries;
- correct copied “certified,” guaranteed, and inconsistent count claims.

This is the release-blocking slice.

### C1 - Make failures actionable

- extract the shared canonical comparator;
- add phase-aware structured results;
- support exact case/model/backend filters;
- include a reproduction command and structural difference in failures.

### C2 - Make evidence reusable

- emit deterministic JSON plus Markdown;
- upload the evidence in CI and retain it for releases;
- generate conformance documentation facts from the evidence;
- produce a baseline diff when the pinned TCK revision changes.

## Decisions requested

### D1 - What is the product boundary?

Recommendation: **complete IDEA-001 as an accelerator**, not merely rename it as a test suite.

| Option | Consequence |
| --- | --- |
| A - Test suite only | Close the idea now, narrow all documentation, and drop classification, filtering, and durable reporting promises |
| **B - Minimal accelerator (recommended)** | Implement C0-C2; preserves the differentiated product value with limited additional machinery |
| C - Conformance service | Add dashboards, history store, public API, and hosted reports; not justified yet |

### D2 - What belongs in the strict denominator?

Recommendation: **pinned DMN 1.5 CL2 and CL3 catalogue only**, with non-compliant/experimental cases
reported separately and no silent exclusions.

This decision must specify catalogue roots, versions, and treatment of entries without a conventional
adjacent model.

### D3 - When may “100%” be stated?

Recommendation: only when the generated evidence shows that every entry in the declared denominator
has a terminal `PASSED` result for each claimed backend, with zero unaccounted, skipped, unsupported,
or failed entries. Say **self-verified**, not certified.

### D4 - Where should evidence live?

Recommendation: CI artifacts for every run plus a small versioned evidence summary for release or TCK
revision baselines. Avoid committing every raw development run.

## Acceptance criteria for closure

IDEA-001 can be marked implemented when:

- every in-scope catalogue entry and test case has exactly one recorded terminal outcome;
- discovery, parsing, compilation, execution, and comparison failures cannot disappear from counts;
- one case can be reproduced by stable identifier;
- interpreter and generated Java use the same canonical comparison policy;
- structured results identify suite revision, backend, stage, status, expected value, and actual value;
- CI verifies the declared inventory and uploads a TCK-specific report;
- conformance documentation and claims derive from that evidence;
- terminology clearly distinguishes self-verification from external certification.

## Recommendation

Approve **D1 option B**, the proposed strict denominator in **D2**, and the evidence rules in **D3-D4**.
Implement C0 before relying on the existing conformance percentage in release positioning. C1 and C2
then turn the already strong execution implementation into the useful, reproducible accelerator that
IDEA-001 originally described.

## Worked specifications

- [Trustworthy TCK catalogue accounting](examples/conformance-accelerator/catalogue-accounting-spec.md)
- [Actionable and reusable conformance evidence](examples/conformance-accelerator/actionable-evidence-spec.md)

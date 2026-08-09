# Data-quality BKM patterns, MT564 showcase, and authoring acceleration

Status: candidate backlog

Created: 2026-08-09

Scope: decompose IDEA-009 into a reusable rule library, a SWIFT MT564 showcase, and optional
evidence-led authoring tools

## Opinion

The most valuable next step is not a general authoring toolkit. It is a credible domain showcase that
forces the reusable pattern boundary to become real.

Build data-quality rules as imported BKMs, then compose them in a SWIFT MT564 corporate-action
notification model. Establish it as one of the repository's maintained reference showcases, with
correctness scenarios and a reproducible benchmark for interpreter and generated Java. This creates
user-visible value, exercises multi-file DMN behavior, and reveals which authoring problems are worth
automating.

The authoring toolkit should therefore be a second, lower-priority track. We should eat our own
dogfood while building the showcase, but dogfooding means observing and removing proven friction. It
does not mean delaying the showcase until a CLI, DSL, or IDE exists.

## Current baseline

The repository already has three generic models under `dmn-benchmarks`:

- `dq-field-validation.dmn`;
- `dq-cross-field-consistency.dmn`;
- `dq-scoring.dmn`.

They demonstrate useful field, cross-field, scoring, and structured-result concepts and have an
interpreter/generated-Java parity test. However:

- they are benchmark resources rather than a versioned pattern-library boundary;
- their rules are decisions, not a documented reusable BKM API with stable signatures;
- they are not imported into a domain-specific root model;
- they do not validate a realistic financial-message lifecycle;
- their production-ready assessments are stronger than the retained domain and consumer evidence;
- there is no scenario bundle or fast authoring feedback loop intended for model authors.

Reuse the ideas and tests, but do not simply relabel benchmark fixtures as a production pattern
library.

## Part 1 - Reusable rules and SWIFT MT564 showcase

Priority: **high**

### Required external input

The canonical MT564 message contract will be supplied from another project as protobuf definitions.
It is currently **needed** and not yet available in this repository.

When supplied, record:

- protobuf files and transitive imports;
- package and message type forming the DQM input boundary;
- schema/version or source revision;
- field-presence semantics, oneofs, repeated structures, enums, timestamps, decimals, and unknown-field
  policy;
- mapping between protobuf fields and MT564/domain terminology;
- representative synthetic or approved anonymized messages;
- compatibility expectations for future schema evolution.

Until then, design work may refine output contracts, BKM signatures, rule metadata, and test strategy,
but the authoritative input mapping and full rule implementation remain blocked. Do not invent a
competing canonical input schema and later maintain two contracts.

### Outcome

A model author can import a small, documented library of data-quality BKMs and compose them into a
SWIFT MT564-specific model. Given a normalized MT564 representation, the model returns deterministic,
structured violations and an overall quality disposition while preserving traceability to the rule
and message field.

The MT564 model becomes a reference showcase: it has an owner, stable entry point, documented scope,
scenario evidence, backend parity, benchmark history, and a compatibility policy for its canonical
input/output contracts.

### Boundary: validate data quality, not parse FIN text

The first showcase accepts a normalized message context produced by an upstream parser or fixture
adapter. It does not implement the SWIFT FIN grammar inside FEEL.

This separation matters:

```text
MT564 FIN text
      |
      v
Existing/future parser and canonical mapping
      |
      v
Normalized MT564 context
      |
      v
DMN data-quality model
      |
      v
Quality report and violations
```

Parsing, network validation, authentication, and full standards conformance are separate concerns.
The showcase validates declared presence, shape, allowed values, cross-field consistency, sequence
relationships, and business plausibility over a documented canonical input.

### Reusable BKM library

Start with a deliberately small contract. Candidate BKMs include:

| BKM | Purpose | Example result |
| --- | --- | --- |
| `requiredValue` | Detect missing/null/blank required values | zero or one violation |
| `matchesPattern` | Validate a string against a supplied pattern | zero or one violation |
| `allowedValue` | Validate membership in a supplied code list | zero or one violation |
| `validDateOrder` | Validate an earlier/later temporal relationship | zero or one violation |
| `requiresWhen` | Require a field when a condition is true | zero or one violation |
| `mutuallyExclusive` | Reject incompatible simultaneous fields/options | zero or one violation |
| `uniqueBy` | Detect duplicate entries by a stable key | violation list |
| `weightedQualityScore` | Derive a bounded score from violation severities | score/context |

Each validation BKM receives rule metadata or enough parameters to return a canonical
`QualityViolation` rather than a bare boolean.

Candidate canonical violation:

```text
{
  ruleId: "MT564-...",
  severity: "ERROR" | "WARNING" | "INFO",
  path: "...",
  message: "...",
  actualValue: ...,
  expected: "...",
  suggestedFix: "..."
}
```

Avoid placing customer-specific field names, code lists, or message text inside generic BKMs.
Domain models supply those values.

### MT564 showcase model

Use a multi-file model set:

```text
dq-core.dmn                reusable QualityViolation and validation BKMs
dq-scoring.dmn             reusable severity/score aggregation
swift-mt564-rules.dmn      MT564 rule composition and domain code lists
swift-mt564-showcase.dmn   root inputs, decisions, and QualityReport
```

The exact MT564 rules and terminology must be reviewed against the licensed/applicable SWIFT and
ISO 15022 documentation used by the project. Repository examples are illustrative and must not imply
official SWIFT validation or certification.

Candidate rule groups:

1. **Envelope and identity** - required message identity, sender/receiver context, preparation or
   creation timestamp, and corporate-action reference.
2. **Event classification** - required corporate-action event type, function/update semantics, and
   supported code-list membership.
3. **Instrument and account identity** - presence and shape of the relevant security/account
   identifiers for the chosen scenario.
4. **Dates and chronology** - declared dates are parseable and obey scenario-specific ordering.
5. **Options and entitlements** - required option data exists, identifiers are unique, and
   conditional fields agree with the option/event type.
6. **Amounts, rates, and currencies** - values have plausible signs/scales and required currency or
   qualifier relationships.
7. **Sequence consistency** - repeated structures are complete, non-contradictory, and refer to the
   same corporate-action event.

Do not attempt all MT564 variants in the first slice. Select one representative corporate-action
scenario and explicitly list supported and unsupported variants.

### Scenario evidence

Create reviewable normalized fixtures rather than random-only data:

- one valid baseline message;
- one case per rule boundary;
- several messages containing multiple independent violations;
- null, empty, malformed, duplicate, and contradictory structures;
- update/cancellation or optionality cases only when included in the selected scenario;
- adversarial size/depth cases proportional to the intended trust boundary.

Each fixture states expected violation IDs, paths, severities, score, and final disposition. The same
fixtures should run against the interpreter and generated Java. Spark SQL is a later parity target
only for the subset it can represent honestly.

### What makes the showcase credible

- A domain reviewer approves the canonical input mapping and rule catalogue.
- Every rule has an identifier, rationale/source reference, severity, examples, and expected failure.
- Unsupported MT564 variants are explicit.
- The output distinguishes malformed input, rule violations, and engine failures.
- Results are deterministic and backend parity is executable.
- A JMH benchmark uses the same reviewed fixture family and reports interpreter and generated-Java
  latency, throughput, allocation, and scaling without including model compilation in evaluation
  scores.
- Documentation says “MT564 data-quality showcase,” not “complete SWIFT validator.”

### Reference benchmark

Add a dedicated MT564 benchmark to `dmn-benchmarks` after the first scenario slice is correct. It
should measure at least:

- a valid baseline message;
- a message with one violation;
- a message with multiple independent violations;
- a realistically large repeated-option/sequence structure within the supported scenario.

Keep two benchmark layers:

1. **Core evaluation** with pre-mapped inputs, for engine comparison.
2. **End to end** with canonical input adaptation and structured quality report, for consumer cost.

Run interpreter and generated Java through equivalent paths. Use thread-scoped mutable state and
immutable shared fixtures, avoid reflective invocation in the direct generated-engine score, and
retain a separately named adapter score if reflection is part of a supported consumer path.

The initial reference report should include one-thread latency and a 1/2/4/8-thread throughput curve,
bounded by available processors. Capture JMH GC allocation data and store release/reference evidence
outside Maven's disposable `target/` directory. Follow the broader
[benchmark evidence proposal](benchmark-evidence-and-scalability.md).

## Part 2 - Evidence-led authoring toolkit

Priority: **lower, conditional**

### Dogfooding approach

Build the reusable BKM library and first MT564 vertical slice using current repository workflows.
Maintain a short friction log containing:

- task attempted;
- repeated manual steps;
- time/rework caused;
- existing workaround;
- potential reusable automation;
- frequency and affected users.

Only automate a problem when it recurs, is costly/error-prone, and has a sufficiently stable desired
workflow.

### Likely minimum useful tool

The first tool should probably be a scenario runner, not a new authoring language:

```text
dmn validate <root.dmn>
dmn test <root.dmn> --scenarios <directory>
dmn evaluate <root.dmn> --input <json>
```

Useful first capabilities:

- load a multi-file model from a root plus imports;
- show phase-aware diagnostics with source locations;
- execute JSON scenarios against named decisions;
- compare structured expected results;
- select one scenario by stable ID;
- optionally watch files and rerun affected scenarios;
- emit readable console output and deterministic JSON;
- use the same public compiler/runtime path as consumers.

Do not begin with a proprietary DSL, graphical editor, language server, plug-in marketplace, or
sub-10ms performance promise. IDEA-004 already explores a broader authoring DSL; avoid duplicating
that decision here.

### Extraction rule

Keep showcase-specific test helpers local at first. Extract them into a reusable tool when at least
two model families need the workflow or the same manual sequence has caused meaningful rework more
than once.

## Priorities and ratings

| Work item | Product value | Learning value | Effort | Priority |
| --- | ---: | ---: | ---: | ---: |
| Define canonical violation and BKM signatures | 9/10 | 9/10 | medium | P0 |
| Select one MT564 scenario and canonical input mapping | 9/10 | 10/10 | medium | P0 |
| Implement first end-to-end MT564 rule slice | 10/10 | 10/10 | medium | P0 |
| Add complete selected-scenario rule/fixture catalogue | 9/10 | 8/10 | medium-high | P1 |
| Interpreter/generated-Java parity and negative cases | 9/10 | 8/10 | medium | P1 |
| MT564 reference JMH benchmark and retained baseline | 8/10 | 8/10 | medium | P1 |
| Minimal JSON scenario runner extracted from real use | 7/10 | 9/10 | medium | P2, conditional |
| Watch mode and author feedback improvements | 5/10 | 6/10 | medium | P3, evidence-led |
| New DQ authoring DSL or graphical toolkit | 4/10 | 5/10 | high | defer |
| Spark SQL parity for supported DQ subset | 6/10 | 8/10 | high | later decision |

## Delivery sequence

This initiative is the first practical pilot of the repository's
[pragmatic spec-driven delivery method](spec-driven-delivery-system.md). The pilot starts with a thin
MT564 end-to-end result and uses the experience to refine both the pattern contracts and the delivery
defaults.

### Phase 0 pilot - One working example

The first implementation increment is smaller than the full DQ0/DQ1 scope:

1. select one representative MT564 corporate-action scenario;
2. define only the normalized fields needed by the first rules;
3. define the minimal `QualityViolation` shape;
4. implement two or three reusable BKMs;
5. import them into one MT564 root model;
6. demonstrate one valid case and several focused invalid cases;
7. prove interpreter/generated-Java parity;
8. document evidence, limitations, authoring friction, and the next expansion decision.

The first slice succeeds by producing a correct, understandable vertical example. It does not need
the complete MT564 rule catalogue, scoring, benchmark, authoring CLI, Spark SQL support, or external
publication.

### DQ0 - Decide the contract

- choose the representative MT564 corporate-action scenario;
- define normalized input and `QualityViolation`/`QualityReport` outputs;
- identify the authoritative rule sources and domain reviewer;
- document supported variants and non-goals.

DQ0 also imports and reviews the supplied protobuf contract. The protobuf message becomes the source
of truth for the normalized input boundary; any DMN context or Java mapping is a generated or tested
projection of it.

### DQ1 - Prove reuse end to end

- implement the smallest useful BKM set;
- import it into the MT564 rule model;
- execute one valid and several invalid fixtures;
- prove interpreter/generated-Java parity;
- record authoring friction.

### DQ2 - Complete the selected showcase

- expand the reviewed rule catalogue and boundary scenarios;
- add score/disposition only when severity policy is accepted;
- document rule traceability, limitations, and extension points;
- retain deterministic reports and examples.

### DQ3 - Establish the reference benchmark

- add valid, single-violation, multi-violation, and large-structure benchmark parameters;
- measure equivalent interpreter and generated-Java core/end-to-end paths;
- run allocation profiling and a controlled concurrency curve;
- retain raw JMH data, environment metadata, and a generated summary as reference evidence;
- avoid regression thresholds until repeated runs establish normal variance.

### AUTH0 - Decide whether tooling earned its cost

- review the friction log after DQ1/DQ2;
- reuse an existing runner/API if it already solves the problem;
- otherwise extract the smallest scenario validate/test/evaluate workflow;
- measure reduction in author feedback time or repeated manual work.

### AUTH1 - Grow only from use

- add watch mode, templates, or editor integration only when repeated use justifies them;
- coordinate any new authoring syntax with IDEA-004 rather than creating a DQ-only language.

## Phase 0 completion review

At the end of the first vertical slice, answer:

- Did the normalized input boundary avoid mixing parsing with data-quality decisions?
- Were the selected BKM signatures genuinely reusable or shaped only around MT564?
- Could a domain reviewer trace every violation to a rule and canonical input path?
- Did interpreter and generated Java execute the same model set and scenarios?
- Which authoring step caused repeated manual work or avoidable errors?
- Which initially proposed document, rule, or tool did not influence implementation or verification?
- Is the next best outcome expanding the MT564 rule catalogue, correcting a platform gap, or
  extracting a small authoring helper?

The review chooses the next slice from evidence. It must not automatically authorize the complete
roadmap.

## Decisions requested

### DQ-D01 - First MT564 scenario

Recommendation: choose one commonly understood, fixture-friendly corporate-action notification
scenario with options and dates rich enough to demonstrate cross-field rules. A domain owner must
name the exact scenario and applicable standards/version before rules are treated as authoritative.

### DQ-D02 - Canonical input boundary

Recommendation: normalized structured context, not raw FIN text. Add raw-message parsing as a
separate integration later.

### DQ-D03 - Output policy

Recommendation: violation list is authoritative; score and PASS/WARNING/REJECT are configurable
projections. Scoring must not hide critical violations.

### DQ-D04 - Tooling gate

Recommendation: approve a minimal scenario-runner extraction only after DQ1 records repeated
friction. Do not block DQ1 on authoring-tool implementation.

### DQ-D05 - Repository boundary

Recommendation: keep patterns and showcase inside this repository until another consumer exists and
the BKM contract stabilizes. Extracting a separate repository earlier would add versioning friction
without proving reuse.

## Success criteria

- the MT564 root model imports and invokes reusable validation BKMs;
- rule metadata and structured violations remain domain-traceable;
- valid and invalid normalized fixtures cover the selected scenario's boundaries;
- interpreter and generated Java produce equivalent results;
- the maintained JMH benchmark covers representative MT564 quality paths and retains reproducible
  reference evidence;
- unsupported variants and the non-certification boundary are explicit;
- the pattern contracts are understandable without benchmark internals;
- authoring automation is justified by recorded use and demonstrably shortens feedback or reduces
  mistakes.

## Worked specifications

- [Reusable data-quality BKM library](examples/data-quality/reusable-bkm-library-spec.md)
- [SWIFT MT564 data-quality showcase](examples/data-quality/swift-mt564-showcase-spec.md)
- [SWIFT MT564 reference benchmark](examples/data-quality/swift-mt564-benchmark-spec.md)
- [Evidence-led authoring toolkit](examples/data-quality/authoring-toolkit-spec.md)

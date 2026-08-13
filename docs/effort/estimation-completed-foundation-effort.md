# Completed foundation effort estimate

Estimate ID: EST-002  
Prepared: 2026-08-02  
Estimate class: retrospective engineering-equivalent range  
Confidence: low-to-medium  
Estimated boundary: repository foundation through P1.7 at commit `6f8f55f`  
Related forward estimate: [EST-001 — P2–P3 completion effort](estimation-P2-P3-completion-effort.md)

## Executive estimate

The completed repository foundation through P1.7 represents approximately **240 conventional
engineering person-days**, with a plausible range of **190–290 person-days**.

| Completed area | Expected effort | Plausible range |
| --- | ---: | ---: |
| Build, Protobuf model, and CI foundation | 23 person-days | 18–28 person-days |
| DMN XML reader, writer, conformance, and hardening | 37 person-days | 30–45 person-days |
| FEEL grammar, parser, AST construction, and diagnostics | 26 person-days | 20–32 person-days |
| Semantic analysis and cross-model linking | 50 person-days | 40–60 person-days |
| Runtime IR and initial optimization foundation | 40 person-days | 32–48 person-days |
| Experimental process-local interpreter | 11 person-days | 8–14 person-days |
| P1 compiler facade and model resolution | 27 person-days | 22–32 person-days |
| Integration, tests, audits, architecture, and delivery documentation | 25 person-days | 20–31 person-days |
| **Total after overlap and rounding** | **239 person-days** | **190–290 person-days** |

For portfolio planning, use **240 person-days** as the expected case. The range is more meaningful
than the point estimate because this is a retrospective reconstruction rather than recorded
timesheet data.

## Meaning of this estimate

This is a **replacement-cost and engineering-equivalent estimate**: the effort a conventional small
engineering team would reasonably plan to reproduce the current tested and documented capability
from the initial product direction.

It is not:

- a claim about the actual hours worked;
- an invoice or productivity score;
- elapsed calendar duration;
- a line-of-code conversion;
- an estimate of completing the entire DMN product vision;
- evidence that every current implementation area is production-complete.

The same person-day definition is used by EST-001: approximately six focused engineering hours,
including design, implementation, tests, review preparation, documentation, and normal local
verification.

## Repository evidence

At the estimate boundary, the repository contains approximately:

| Evidence | Observed quantity |
| --- | ---: |
| Git commits | 102 before the later ideation-portfolio commit |
| Tracked Java files | 254 |
| Tracked Java lines | 22,279 |
| Test-source lines | approximately 6,004 |
| Main-source lines | approximately 13,856 |
| Protobuf schema lines | 1,187 |
| Markdown documentation lines | 12,734, including later ideation material in the working snapshot |
| DMN fixtures | Four tracked copies representing one substantive `TrafficViolation.dmn` model |

Counts exclude obvious generated/build-output directories such as `target`, `site`, `serialized`,
and `zipped`. They are context only; they do not mechanically determine the effort estimate.

Git history places the initial repository work between 2026-07-28 and the P1.7 completion on
2026-08-02. That unusually short elapsed period demonstrates concentrated and AI-assisted delivery,
but Git cannot reveal actual human attention, parallel tool execution, discarded work, or review
time.

## Estimation method

The estimate was reconstructed by capability area rather than commit count or source volume:

1. Identify the implemented outcome and its supporting tests/documentation.
2. Separate initial implementation from integration, hardening, and architectural reconciliation.
3. Estimate conventional design, coding, testing, debugging, and documentation effort.
4. Apply a range for uncertainty and implementation overlap.
5. Avoid summing every commit independently because many commits are iterative refinements of the
   same capability.
6. Round the aggregate after accounting for shared infrastructure and cross-module work.

The estimate assumes an experienced Java/compiler engineer familiar with Maven and Protobuf but not
already carrying a complete implementation of the repository in working memory.

## Area estimates

### Build, Protobuf model, and CI foundation — 23 days

Included outcomes:

- multi-module Maven structure and dependency direction;
- Protobuf semantic model and generated Java integration;
- replaceable FEEL text and parsed representations;
- CI setup and reactor stabilization;
- initial project and documentation-site infrastructure.

The lower range assumes schema requirements were already well understood. The upper range includes
normal schema iteration, generated-code integration, and CI/environment troubleshooting.

### DMN XML frontend — 37 days

Included outcomes:

- namespace-aware DMN reader and writer;
- modeled reader/writer symmetry;
- QName and `typeRef` namespace preservation;
- imports, requirements, decision services, BKMs, item definitions, and boxed expressions;
- prefixed and multi-version namespace behavior;
- structured diagnostics and source locations;
- unsupported-content handling, hostile-input limits, and conformance/security fixtures.

This area carries substantial replacement cost because correctness depends on many interacting XML,
namespace, model, diagnostic, and round-trip cases rather than only element parsing.

### FEEL parser — 26 days

Included outcomes:

- ANTLR grammar and generation;
- Protobuf FEEL AST builder;
- expression, boxed-expression, unary-test, and type-constraint parsing;
- model-wide parsing pass;
- multi-error, model-aware diagnostics;
- Traffic Violation integration and grammar-policy fixes.

The estimate includes grammar debugging and source-to-AST verification, not complete FEEL runtime
semantics, which remains part of P3.

### Semantic analysis — 50 days

Included outcomes:

- symbol and property resolution;
- lexical scopes and resolved binding exposure;
- type inference across scalar, collection, context, function, and boxed expressions;
- built-in validation;
- named types, item definitions, constraints, BKMs, decision services, and decision tables;
- dependency analysis, cycle detection, and deterministic ordering;
- namespace-indexed cross-model linking and cross-model type/reference resolution;
- unified immutable analysis result and diagnostics.

This is the largest completed area because it establishes cross-cutting semantic behavior used by
Runtime IR and every future execution backend.

### Runtime IR and optimization foundation — 40 days

Included outcomes:

- immutable executable types, inputs, decisions, BKMs, expressions, unary tests, and tables;
- deterministic IDs, global slots, lexical frames, and context-field indices;
- lowering of all currently modeled FEEL expressions and boxed logic;
- linked model-set lowering and implicit dependencies;
- structural validation and aggregate invariants;
- lowerer decomposition;
- typed constant canonicalization and stable built-in operation IDs;
- pipeline integration tests and serialization-policy design.

The estimate covers the execution-oriented representation and initial optimizer, not portable
serialization or generated Java/Rust backends.

### Experimental interpreter — 11 days

Included outcomes:

- dependency-scheduled input, decision, and BKM execution;
- lexical frames, functions, contexts, lists, relations, ranges, and unary tests;
- decision-table baseline;
- initial built-in and temporal value handling;
- explicit failures for missing inputs and external functions;
- focused interpreter tests and runtime-module integration.

The value is intentionally lower than a production runtime estimate. Known semantic shortcuts and
limited focused coverage are explicitly assigned to P3 in EST-001.

### P1 compiler facade and model resolution — 27 days

Included outcomes:

- source identities and resolver contracts;
- filesystem, classpath, and in-memory resolvers;
- bounded deterministic transitive import loading and caching;
- missing, duplicate, ambiguous, and cyclic import diagnostics;
- shared severity, phase, and source-aware compiler diagnostics;
- whole-model-set semantic result;
- immutable compiler facade and compilation result through optimized Runtime IR;
- 43 focused compiler tests and reactor evidence reported by the development plan.

P1 benefits from the prior XML, FEEL, semantic, and Runtime IR foundation, but its orchestration,
security boundaries, deterministic loading, diagnostics, and acceptance testing remain meaningful
independent work.

### Integration, audits, architecture, and documentation — 25 days

Included outcomes:

- complete-reactor integration and regression correction;
- module assessments and TODO reconciliation;
- implementation-aligned architecture chapters and ADRs;
- roadmap and development-plan refinement;
- supported-subset and compatibility policy documentation;
- slice specifications and completion evidence;
- README and documentation-site structure.

Some documentation is naturally produced alongside the areas above. The total is adjusted for that
overlap rather than treating all documentation as additive work.

## Comparison with remaining P2–P3 estimate

| Scope | Expected effort | Plausible range |
| --- | ---: | ---: |
| Completed foundation through P1.7 | 240 person-days | 190–290 person-days |
| Remaining P2 and P3 | 90 person-days | 65–122 person-days |
| **Foundation through completed P3** | **330 person-days** | **255–412 person-days** |

The ranges should not be summed as independent statistical extremes; several uncertainties are
correlated. The combined range is useful for order-of-magnitude portfolio planning, not contractual
forecasting.

P2/P3 appears smaller than the completed foundation because it builds on substantial existing XML,
FEEL, semantic, Runtime IR, and compiler infrastructure. P3 nevertheless has meaningful uncertainty
because semantic correctness can expose required changes across those established layers.

## AI-assisted delivery interpretation

The completed work was produced within a very short calendar period. The appropriate interpretation
is not that the work has a replacement cost of only a few person-days. It indicates that AI-assisted,
tool-driven implementation compressed research, drafting, repetitive coding, test creation, and
documentation activities.

Three different measures should remain separate:

| Measure | Meaning |
| --- | --- |
| Elapsed calendar time | Time between repository start and completion commit |
| Actual human effort | Human attention and working hours, which were not recorded here |
| Engineering-equivalent effort | Conventional planning/replacement effort represented by the result |

Only the third is estimated by this document. Actual human effort cannot be recovered reliably from
Git history.

## Confidence and limitations

Confidence is low-to-medium because:

- no timesheets or per-slice actuals exist;
- commit boundaries do not map cleanly to work packages;
- large portions were produced through unusually accelerated collaboration;
- some source volume is generated while other compact semantic code carries high design cost;
- documentation and integration work overlap implementation areas;
- current tests demonstrate a strong foundation but not production completeness.

The estimate should be used to understand scale, replacement cost, and the productivity multiplier—not
to assess individual performance or claim accounting precision.

## Recommendation

Adopt **240 person-days** as the retrospective expected case for portfolio comparison. Use the
completed slices to improve future estimation:

1. record start, first executable evidence, completion, and rework for P2 slices;
2. compare actual P2/P3 effort with EST-001 ranges;
3. recalibrate future person-day estimates after P2.3 and P3.1;
4. retain separate measures for elapsed time, human attention, and engineering-equivalent output;
5. avoid using source volume or commit count as productivity targets.

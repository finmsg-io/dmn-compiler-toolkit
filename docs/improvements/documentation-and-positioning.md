# Documentation and positioning improvement proposal

Status: candidate backlog  
Created: 2026-08-09  
Scope: documentation structure, audience views, product positioning, automation, and decision hygiene

## Opinion

The repository has valuable documentation, but too many documents behave as current truth. The
result is expensive synchronization and declining trust. The solution is not to improve every
document independently. It is to reduce the number of authoritative sources, provide short
audience-specific views, generate repeated facts, and move historical material out of the normal
reading path.

Current evidence:

- `docs/` contains 137 Markdown files and approximately 20,578 lines.
- Architecture material alone contains 51 files and approximately 9,244 lines.
- `README.md` says 14 modules while `docs/index.md` says 12 and omits implemented modules.
- `docs/index.md` presents Spark SQL, data-quality models, and multi-file models as next steps even
  though the root README and development plan describe them as implemented.
- `docs/development-plan.md` repeats native-generator and Spark-related objectives.
- `mkdocs.yml` points to obsolete architecture chapter names and numbers.
- Many files contain hand-maintained generated TOC blocks, but no repository script or CI check
  currently owns their regeneration.
- Dated audits, completed delivery slices, plans, TODOs, architecture chapters, module READMEs, and
  landing pages repeat implementation status.

## Target information architecture

Keep one short root README as the product pitch and repository entry point. Provide three views,
each answering the questions of one reader without copying the full architecture.

| View | Reader's question | Content | Target length |
| --- | --- | --- | --- |
| Architect | Can this fit our platform and governance constraints? | boundaries, execution targets, determinism, portability, compatibility, security, ADR links | 2–4 pages |
| Developer | Can I build, embed, extend, test, and debug it? | quick start, supported API, module dependency map, extension points, build profiles | 2–4 pages |
| DMN user | What can I model and where can I run it? | supported DMN/FEEL scope, examples, diagnostics, deployment choices, limitations | 2–4 pages |

Recommended canonical documents:

| Fact | Single owner |
| --- | --- |
| Product promise and verified selling points | root `README.md` |
| Current modules and dependency graph | generated from Maven reactor metadata |
| Supported DMN/FEEL behavior | executable conformance data plus one generated capability page |
| Architecture rules | architecture specification and accepted ADRs |
| Active delivery priorities | one concise backlog/plan |
| Module-local implementation detail | module README or generated API documentation, not both |
| Historical analysis | dated audits in an archive, excluded from primary navigation |

The current `docs/audits`, completed `docs/dev/slices`, and effort estimates should remain available
for provenance but should be clearly historical and removed from the main navigation. Their active
findings must be transferred to the canonical backlog before archival.

## Selling points

The strongest positioning is not simply “another DMN engine.” It is a compiler toolkit that turns
one decision model into multiple execution forms while preserving a shared semantic pipeline.

Proposed concise message:

> Compile DMN once, validate it early, and run the same decision semantics as an interpreter,
> generated Java, native Spark SQL, or a generated service boundary.

Evidence-backed differentiators today:

1. **Compiler architecture:** parsing, semantic analysis, Runtime IR, optimization, and generation
   are explicit stages rather than one opaque runtime interpreter.
2. **Multiple execution targets:** process-local Runtime IR evaluation, standalone generated Java,
   Spark/Databricks SQL without UDFs, and generated gRPC adapters share the same compiler model.
3. **Deployment flexibility:** XML and FEEL parsing can be removed from the runtime path when using
   generated artifacts.
4. **Deterministic and diagnosable:** immutable intermediate representations, stable ordering, and
   structured phase-aware diagnostics are first-class design goals.
5. **Conformance and parity infrastructure:** official TCK execution and cross-backend comparison
   provide executable evidence rather than prose-only compatibility claims.
6. **Extensible backend boundary:** Runtime IR allows additional generators without rebuilding the
   XML and semantic front end.

Claims that need evidence before prominent marketing use:

- “faster,” “zero allocation,” “100 MB smaller,” or similar comparative claims need reproducible
  benchmark artifacts and named comparison conditions;
- “runs everywhere” should be replaced by an explicit deployment matrix;
- “100% certified” should distinguish passing the imported TCK from certification by an external
  authority;
- exact TCK model/test counts must be generated from the test report, not copied into prose.

Future convenience tools can become a second product pillar: model linting, scenario generation,
explainability, migration assistance, visual diagnostics, IDE integration, and deployment packaging.
These improve user experience without weakening the core compiler positioning.

## Automation strategy

Do not synchronize prose by copying it. Generate repeated facts and fail CI when generated content
or links are stale.

Recommended automation, in order:

1. **Build documentation strictly:** run `mkdocs build --strict` in CI to catch invalid navigation,
   missing links, and warnings.
2. **Remove manual page TOCs:** use MkDocs' page TOC/navigation features. If committed TOCs remain a
   requirement, provide one deterministic script and a CI `--check` mode.
3. **Generate module catalog:** derive artifact ID, name, description, and dependency edges from the
   Maven effective reactor; inject it between owned markers in one canonical page.
4. **Generate conformance and benchmark facts:** publish machine-readable JSON/CSV from tests and
   benchmarks, then render summary tables from those artifacts.
5. **Validate documentation contracts:** check local links, unique ADR IDs, recognized status
   values, existing MkDocs nav targets, and absence of active TODOs in archived audits.
6. **Detect stale generated content:** CI runs generators and fails on `git diff --exit-code`.
7. **Add ownership metadata:** every living document declares owner/type and review trigger; avoid
   arbitrary review dates where a code-change trigger can be defined.

A content change should trigger only the generators relevant to its inputs. For example, a POM
change regenerates the module catalog; a TCK report change regenerates conformance tables; Markdown
heading changes either require no committed TOC or trigger the TOC checker.

## Open-decision audit

The decision table in `docs/development-plan.md` is stale.

| Decision | Finding | Recommended state/action |
| --- | --- | --- |
| D-001 realistic multi-file domain | Resolution is recorded | Keep resolved |
| D-002 Java package and naming policy | Implemented by `DmnJavaGeneratorOptions`; defaults and overrides exist | Resolve and link code/tests; create ADR only if this is a public compatibility promise |
| D-003 runtime helper versus standalone Java | Implemented as standalone generated Java with embedded helper methods and JDK-only imports | Resolve and link ADR-0013 plus generator tests |
| D-004 generic gRPC dynamic-value schema | Implemented in `evaluation.proto` and converters | Resolve and link schema/tests |
| D-005 typed Protobuf compatibility policy | Typed generation exists, but a stable regeneration/field-number compatibility policy is not evident | Keep open; assign to release/API governance and create an ADR before promising compatibility |

Completed development slices also retain “Open decisions” headings. A completed slice should record
the resolution or explicitly link the decision that closed it; it should not look like a current
decision queue.

## Prioritized backlog

### P0 — Restore trust

- Fix MkDocs navigation and enable strict CI builds.
- Correct the 12/14-module contradiction and other already-implemented “next steps.”
- Reconcile D-002 through D-005 as described above.
- Remove or qualify unsupported performance and certification claims.

### P1 — Create reader views

- Rewrite the root README around the concise product message and verified proof points.
- Add architect, developer, and DMN-user landing pages.
- Add a deployment-target matrix and a supported-capability matrix.
- Make each view link into canonical details instead of repeating them.

### P2 — Reduce duplication

- Archive dated audits, completed slices, and superseded estimates outside primary navigation.
- Replace repeated module inventories with one generated catalog.
- Collapse roadmap, development plan, and module TODOs into one hierarchy with one owner per fact.
- Decide whether module READMEs are packaging summaries or deep documentation; do not make them both.

### P3 — Automate maintenance

- Add strict MkDocs, link, navigation, and decision-status checks.
- Generate module, conformance, and benchmark tables from executable sources.
- Remove committed TOCs or create a deterministic TOC check/update tool.
- Add a documentation-change checklist to pull requests based on changed source areas.

### P4 — Strengthen product proof

- Publish reproducible benchmark methodology and results before making comparative performance
  claims.
- Publish backend parity results as generated evidence.
- Add end-to-end examples for Java, Spark SQL, and gRPC from one shared DMN model.
- Build convenience tooling around diagnostics, scenarios, explainability, and packaging.

## Additional ideas

- Treat documentation pages like APIs: assign stability (`normative`, `living`, `historical`) and
  prevent historical pages from presenting current status.
- Maintain a small machine-readable capability manifest. Use it to generate the deployment matrix,
  supported-feature page, README badges, and release notes.
- Use one “golden journey” DMN model across all audience views so readers can compare interpreter,
  Java, Spark SQL, and gRPC outcomes directly.
- Add evidence links beside every selling point: benchmark, TCK report, test, ADR, or example.
- Prefer diagrams generated from module dependencies and capability manifests over manually copied
  architecture inventories.

## Suggested execution sequence

This proposal should be implemented theme by theme rather than as one large rewrite:

1. documentation correctness and CI guardrails;
2. open-decision reconciliation;
3. audience views and product positioning;
4. duplication reduction and archival;
5. generated catalogs and evidence pages;
6. convenience-tool documentation as those capabilities are delivered.

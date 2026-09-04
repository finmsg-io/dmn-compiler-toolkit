# DOC-001 — Trustworthy audience-oriented documentation

Status: implemented  
Profile: documentation  
Source proposal: [Documentation and positioning improvement](../../documentation-and-positioning.md)

## Outcome

Architects, developers, and DMN users can each reach accurate, relevant information within two
navigation choices. Repeated project facts have one owner, generated projections cannot silently
drift, and unsupported product claims are clearly separated from verified evidence.

## Beneficiaries

- Architects evaluating platform fit, governance, deployment, and compatibility
- Developers building, embedding, extending, testing, or operating the toolkit
- DMN users evaluating supported behavior, diagnostics, and execution targets
- Maintainers who currently reconcile duplicated documentation manually

## Success signals

- `mkdocs build --strict` passes in local development and CI.
- All configured navigation targets and internal links resolve.
- One canonical module inventory reports the actual Maven reactor modules.
- Root and site landing pages no longer contradict the canonical inventory or capability manifest.
- Architect, developer, and DMN-user journeys reach their primary answers within two navigation
  choices.
- Every prominent selling point links to executable evidence or is explicitly labeled as a target.
- Changes to POM modules, conformance results, or generated documentation fail CI when projections
  are stale.

## Observable acceptance examples

1. Given a new Maven module, when its POM is added to the reactor and generated documentation is not
   refreshed, then the documentation drift check fails with the affected generated section.
2. Given a renamed Markdown page, when its old path remains in MkDocs navigation, then strict
   documentation validation fails before merge.
3. Given an architect landing on the site, when they follow the architecture path, then deployment
   targets, system boundaries, compatibility promises, and accepted decisions are reachable without
   navigating through audits or delivery history.
4. Given a developer, when they follow the developer path, then a verified build command, supported
   public API, module dependency view, generation workflow, and debugging guidance are available.
5. Given a DMN user, when they follow the user path, then supported DMN/FEEL scope, a complete
   example, execution choices, diagnostics, and known limitations are available.
6. Given a performance or conformance claim, when no retained evidence source exists, then the claim
   is qualified or excluded from the verified selling-point section.
7. Running documentation generators twice produces no second diff.

## Constraints

- Preserve accepted ADR history and dated audit provenance.
- Do not turn archived assessments into active truth.
- Avoid introducing another manually maintained module or capability list.
- Use relative repository links and cross-platform deterministic output.
- Generated files must use canonical LF and run through the same lifecycle locally and in CI.
- Keep the root README concise enough to serve as product pitch and repository entry point.
- Do not claim external certification unless an external certifying authority exists.

## Non-goals

- Rewriting all architecture chapters in one delivery.
- Producing new benchmarks or extending DMN semantics.
- Deleting historical audits before active findings are transferred.
- Building a general-purpose documentation platform before repository conventions are proven.
- Designing future convenience tools beyond positioning them as an explicit future product pillar.

## Unknowns and opportunities

- Whether committed per-page TOCs should be removed or regenerated deterministically
- Whether the capability manifest should be YAML, JSON, or derived completely from tests
- Whether historical material remains in `docs/` or moves below a clearly excluded archive root
- Whether audience views should be pages, navigation tabs, or generated filtered views
- Opportunity: reuse the capability manifest for release notes, README badges, and website content
- Opportunity: use one golden DMN model across Java, interpreter, Spark SQL, and gRPC examples

## Evidence and completion

Completion requires:

- strict site build and link/navigation validation;
- generator idempotence and clean-worktree checks;
- tests for module/capability projection logic;
- before/after contradiction inventory;
- evidence review for every promoted selling point;
- a usability walkthrough for each audience journey;
- updated decision states where implementation already resolved the choice.

## Completion evidence

- Created dedicated audience landing guides:
  - `docs/guides/architect-guide.md`: AOT compiler philosophy vs. reflection engines, deployment topologies (Embedded, Sidecar gRPC, Spark SQL), latency/throughput profiles, security trust boundaries, and ADR index.
  - `docs/guides/developer-guide.md`: Maven dependency coordinates, `DmnRuntime` interpreter evaluation, AOT Java code generation (`dmn-generator-java`), strongly-typed direct execution, diagnostic sinks, and CLI tooling.
  - `docs/guides/modeler-guide.md`: DMN 1.5 compliance scope (100% CL2/CL3 across 3,391 cases), supported boxed expressions mindmap, FEEL syntax reference (arithmetic, temporal, filter lists), decision table hit policies, and interactive TCK dashboard walkthrough.
- Integrated guides directly into `mkdocs.yml` top navigation under `Guides`.
- Automated documentation consistency verified via `tools/verify_documentation.py`.

## Stop, pivot, or continue

- Continue when each slice removes a measurable contradiction or manual synchronization point.
- Pivot if generated projections require more maintenance than the duplication they replace.
- Stop adding audience pages when user questions can be answered through existing canonical pages
  with simpler navigation.

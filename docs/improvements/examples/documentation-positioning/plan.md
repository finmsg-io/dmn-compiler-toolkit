# DOC-001 — Delivery plan

Status: example — shaping  
Specification: [`spec.md`](spec.md)  
Design: [`design.md`](design.md)

## Planning principle

Deliver independently useful vertical slices. Do not begin with a repository-wide rewrite or a
custom documentation framework.

## Slice 1 — Correctness baseline

Outcome: existing documentation builds strictly and no longer contains known top-level
contradictions.

- Fix obsolete MkDocs navigation and broken internal links.
- Reconcile module counts and already-implemented “future” statements.
- Reconcile D-002 through D-005 against code and accepted decisions.
- Qualify unsupported certification/performance claims.
- Add strict documentation validation to CI.

Evidence:

- `mkdocs build --strict` passes.
- Link/navigation validation passes.
- Contradiction checklist is empty for identified cases.
- CI demonstrates failure on a deliberately invalid navigation fixture or checker test.

## Slice 2 — Audience journeys

Outcome: each reader has a short, accurate entry path.

- Rewrite the README around the verified product promise.
- Add architect, developer, and DMN-user views.
- Introduce a deployment-target matrix.
- Validate the golden journey from one shared DMN example.

Evidence:

- Three audience walkthroughs answer their defined questions within two navigation choices.
- No audience page owns a duplicated module/capability inventory.
- Every promoted selling point has an evidence link or target label.

## Slice 3 — First generated projection

Outcome: the module catalog cannot drift from the Maven reactor.

- Define generated-region markers.
- Generate module name, description, and dependency edges from reactor metadata.
- Add update/check modes and idempotence tests.
- Replace repeated hand-maintained module tables with links or projections.

Evidence:

- Adding a fixture module makes check mode fail.
- Update mode restores consistency.
- A second update produces no diff.
- Windows and Linux output bytes are identical.

## Slice 4 — Historical separation

Outcome: audits and completed slices remain available without presenting themselves as current
truth.

- Transfer still-active findings to the canonical backlog.
- Add explicit historical status and dates.
- Remove historical collections from primary navigation.
- Migrate paths only after agreeing URL compatibility.

Evidence:

- Primary navigation contains no dated audit tree.
- Historical pages remain discoverable through archive/search.
- Active findings have canonical owners.

## Slice 5 — Capability and proof projections

Outcome: conformance, backend parity, and performance claims derive from retained evidence.

- Introduce the smallest viable capability manifest.
- Generate conformance and backend matrices.
- Connect benchmark claims only after reproducible result publication.
- Reuse projections for release notes or badges where valuable.

Evidence:

- Manifest schema validation passes.
- Capability/evidence references resolve.
- Stale generated tables fail CI.
- Claims without evidence cannot appear as verified.

## Risk-first order

1. Prove strict build and link checking with the current MkDocs stack.
2. Decide committed TOC policy before creating a TOC generator.
3. Prove one Maven-derived projection before designing a general manifest framework.
4. Test cross-platform deterministic output before replacing multiple tables.
5. Archive only after current findings are transferred.

## Progress protocol

After every slice:

- report completed outcome and evidence;
- record deviations and newly discovered opportunities;
- state explicitly whether work continues, is blocked, or awaits instructions;
- ask for authority before changing accepted scope or making irreversible archival moves.

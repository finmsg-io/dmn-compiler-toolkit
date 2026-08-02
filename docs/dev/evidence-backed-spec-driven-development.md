# Evidence-Backed Specification-Driven Development

<!-- generated-toc:start -->
## Table of contents

- [Purpose](#contents-section-1)
- [Core principle](#contents-section-2)
- [Development loop](#contents-section-3)
- [Canonical document roles](#contents-section-4)
- [When a slice is ready](#contents-section-5)
- [Reusable completion gate](#contents-section-6)
- [Proportional verification](#contents-section-7)
- [Example: P1.3 transitive import loading](#contents-section-8)
  - [Slice specification](#contents-section-9)
  - [Suggested delivery sequence](#contents-section-10)
  - [Expected evidence](#contents-section-11)
- [Reusable slice template](#contents-section-12)
- [Process guardrails](#contents-section-13)
<!-- generated-toc:end -->

<a id="contents-section-1"></a>
## Purpose

This guide defines the project’s lightweight approach to specification-driven development. It
preserves architectural discipline without requiring a complete design for every future capability
before implementation can proceed.

The approach is called **evidence-backed specification-driven development**: specify one observable
slice, implement it vertically, prove it with executable evidence, and update only the canonical
documentation affected by the result.

<a id="contents-section-2"></a>
## Core principle

```text
Architectural requirement
        -> milestone work item
        -> acceptance scenarios
        -> implementation and tests
        -> linked completion evidence
```

A specification states intent and observable behavior. Tests, benchmarks, generated artifacts, and
validated documentation establish whether the intent has actually been achieved.

The process is deliberately not “specify the entire product first.” Future material may remain
`UNREVIEWED`; only the decisions and requirements needed by the next slice must be ready.

<a id="contents-section-3"></a>
## Development loop

1. Select one requirement or milestone work item.
2. Write a short slice specification containing observable behavior, non-goals, constraints,
   acceptance scenarios, affected modules, and open decisions.
3. Mark the slice `ready` only when implementation can proceed without a major unresolved design
   choice.
4. Implement the smallest end-to-end behavior that produces useful evidence.
5. Add positive, negative, and deterministic tests proportional to the slice’s risk.
6. Run focused verification first, then downstream or full-reactor checks where required.
7. Link the evidence from the development plan and update only canonical status documents.
8. Create an ADR only when the slice makes a durable architectural decision.

<a id="contents-section-4"></a>
## Canonical document roles

| Artifact | Role |
| --- | --- |
| [Architecture specification](../architecture/architecture-spec.md) | Enduring requirements, boundaries, and intended system structure |
| [ADRs](../architecture/adr/generall-adr.md) | Significant decisions, alternatives, rationale, and consequences |
| [Development plan](../development-plan.md) | Milestone sequencing, acceptance criteria, state, and evidence |
| [Module TODOs](../todos/index.md) | Current module-local gaps, not completion history |
| Slice specification | Temporary implementation contract for one bounded increment |
| Tests and benchmarks | Executable behavioral and quality evidence |
| Assessments | Dated snapshots; not active work queues |

Avoid duplicating the same requirement across several narrative documents. Link to the authoritative
source and add detail only where the document has a distinct responsibility.

<a id="contents-section-5"></a>
## When a slice is ready

A slice may move to `ready` when:

- its observable outcome and non-goals are explicit;
- acceptance scenarios cover the primary success and failure behavior;
- affected architectural requirements and modules are known;
- no unresolved choice would materially change public contracts or module boundaries;
- required fixtures and test boundaries are identifiable;
- any necessary ADR is accepted, or the slice intentionally avoids that decision.

Implementation details that can be changed locally do not need to be decided before work begins.

<a id="contents-section-6"></a>
## Reusable completion gate

A normal slice is complete when all applicable items are satisfied:

- [ ] Acceptance scenarios are implemented as executable tests.
- [ ] Positive, negative, and boundary behavior is covered.
- [ ] Deterministic ordering or output is tested where relevant.
- [ ] Focused module tests pass.
- [ ] Affected downstream modules pass when a shared contract changed.
- [ ] The complete reactor passes when dependencies, POMs, schemas, or public shared contracts changed.
- [ ] Strict documentation and internal-link validation pass for documentation changes.
- [ ] Public contracts and compatibility implications are documented.
- [ ] The development-plan item links to completion evidence and has the correct state.
- [ ] Completed module TODO entries are removed; historical context remains in Git and assessments.
- [ ] No unrelated files or generated artifacts are included.

<a id="contents-section-7"></a>
## Proportional verification

| Change | Minimum verification |
| --- | --- |
| Local implementation detail | Focused module tests |
| Shared Java/protobuf contract | Owning module and affected downstream modules |
| Module dependency or parent POM | Complete Maven reactor |
| Documentation only | TOC/link validation, `git diff --check`, strict MkDocs build |
| Runtime semantics | Focused conformance cases and shared corpus parity |
| Generator behavior | Deterministic output, generated-source compilation, interpreter parity |
| Security/resource limit | Positive behavior, rejection boundary, and adversarial test |
| Performance claim | Reproducible JMH benchmark plus retained semantic parity |

Verification should be strong enough to detect the likely failure modes of the change, without
running every expensive check for an isolated low-risk edit.

<a id="contents-section-8"></a>
## Example: P1.3 transitive import loading

This example applies the process to development-plan item P1.3: load transitive imports with
deterministic ordering and caching.

<a id="contents-section-9"></a>
### Slice specification

**Requirement links:** AR-002 (determinism), AR-006 (resolver-independent transitive imports),
AR-008 (bounded model graphs).

**Outcome:** given a root `DmnSource`, load every location-addressable transitive import once and
return an immutable model-source graph in deterministic order.

**First vertical increment:**

```text
root DmnSource
    -> parse root imports
    -> resolve one imported source
    -> parse imported model
    -> cache by DmnSourceId
    -> return deterministic loaded source set
```

**In scope:**

- location-based imports resolved through `DmnModelResolver`;
- recursive traversal of imported sources;
- one resolver call and parse per stable `DmnSourceId`;
- deterministic source ordering independent of map iteration;
- immutable result containing root identity, sources, and import edges;
- focused single-level, transitive, and diamond tests.

**Non-goals for the first increment:**

- namespace/model-name fallback when `locationURI` is absent;
- complete missing/ambiguous/cycle diagnostic taxonomy from P1.4;
- semantic analysis, Runtime IR lowering, or execution;
- durable caches shared between compilations;
- filesystem watching or hot reload.

**Acceptance scenarios:**

1. A root with no imports returns exactly the root source.
2. A root importing one model resolves and returns both sources.
3. A three-level chain loads root, intermediate, and leaf in stable order.
4. A diamond graph resolves and parses the shared leaf exactly once.
5. Reordering resolver registration does not change the result order.
6. A resolver returning a source whose identity was already loaded reuses the cached source.
7. Exceeding configured source-count or import-depth limits fails deterministically.

**Open decision intentionally deferred:** cycle classification belongs to P1.4. P1.3 may stop
recursion through its identity cache and preserve the edge so P1.4 can diagnose the cycle later.

<a id="contents-section-10"></a>
### Suggested delivery sequence

1. Define immutable loaded-source and import-edge result contracts.
2. Implement the root-only case.
3. Add one direct import through the in-memory resolver.
4. Add recursive traversal and identity-based caching.
5. Define and test deterministic ordering.
6. Add diamond and limit tests.
7. Run `dmn-compiler` tests, then the complete reactor because the compiler contract is public.
8. Mark P1.3 complete only when evidence is linked from the development plan.

<a id="contents-section-11"></a>
### Expected evidence

- Focused loader tests covering all acceptance scenarios.
- Resolver spy/counting evidence that diamond dependencies load once.
- Deterministic results across different registration/insertion orders.
- Complete Maven reactor success.
- Updated compiler module documentation and P1.3 evidence entry.

<a id="contents-section-12"></a>
## Reusable slice template

Copy the following template into the development-plan work item, an issue, or a temporary design
note. A separate document is unnecessary for a small slice when the plan entry remains readable.

```markdown
# <Slice ID> — <Outcome-oriented title>

Status: proposed | ready | in progress | blocked | done
Owner: <person or team, if useful>
Requirements: <AR IDs and links>
Milestone: <plan item>

## Outcome

<One paragraph describing the externally observable result.>

## In scope

- <behavior or contract included in this slice>
- <behavior or contract included in this slice>

## Non-goals

- <explicitly deferred behavior>
- <adjacent concern owned by another slice>

## Architectural constraints

- <module/dependency rule>
- <determinism, immutability, compatibility, security, or runtime rule>

## Affected modules and contracts

| Module/contract | Expected change |
| --- | --- |
| `<module>` | <change> |

## Acceptance scenarios

1. Given <context>, when <action>, then <observable result>.
2. Given <invalid/boundary context>, when <action>, then <diagnostic/failure>.
3. Repeated or reordered execution produces <deterministic result>.

## Open decisions

- <decision required before ready, or “None”>

## Verification plan

- Focused: `<command or test class>`
- Downstream: `<affected modules or “not required”>`
- Full reactor: required | not required — <reason>
- Documentation: `<checks>`

## Completion evidence

- Tests: <links/names>
- Documentation/API: <links>
- ADR: <link or not required>
- Benchmark/artifact: <link or not applicable>

## Result

<Fill when done: implementation summary, deviations, and follow-up items.>
```

<a id="contents-section-13"></a>
## Process guardrails

- Prefer a thin vertical slice over completing one layer exhaustively without integration evidence.
- Do not review every future proposal before implementing the next ready milestone.
- Do not create an ADR for a local reversible implementation choice.
- Do not mark a slice done because code exists; require linked acceptance evidence.
- Do not use an assessment as a TODO list.
- Do not let a context/options object become a mutable service locator.
- Automate recurring integrity checks instead of repeating manual documentation audits.
- Revisit the process when it adds delay without finding defects or when escaped defects reveal a
  missing gate.


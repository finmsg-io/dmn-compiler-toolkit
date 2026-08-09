# Pragmatic spec-driven delivery

Status: candidate backlog  
Created: 2026-08-09  
Scope: a small, reusable method for turning goals into verified outcomes

## Why simplify

The original proposal contained useful ideas but too much machinery for a first version: several
artifact types, a state machine, YAML schemas, workflow commands, quality profiles, and process
metrics. That could make maintaining the delivery system a project of its own.

Process earns its cost only when it prevents misunderstanding, catches defects earlier, or shortens
delivery. We should therefore start with the smallest useful structure, apply it to real work, and
add rules only in response to observed problems.

The practical goal is:

> Agree on the outcome, preserve implementation freedom, verify the real user path, and record what
> was learned.

## The minimal model

```text
Reusable principles
        ↓
Repository instructions
        ↓
Short outcome specification
        ↓
Selected reusable skills
        ↓
Implementation and executable evidence
```

Only two artifacts are normally needed for concrete work:

1. a short specification, which may live in the task/issue rather than a new file;
2. a short selection of relevant skills or verification procedures.

The implementation and its tests provide the result. The final handoff records evidence and
learning; a separate evidence document is unnecessary for ordinary work.

## Reusable defaults

Defaults should be written once and inherited rather than copied into every feature.

| Artifact | Purpose |
| --- | --- |
| `CONSTITUTION.md` | Stable principles: outcomes, evidence, safety, learning, and proportional process |
| `AGENTS.md` | Repository commands, boundaries, generated files, verification, and communication rules |
| Reusable skills | Procedures for recurring work such as Maven, generators, documentation, and releases |

A project should override only what differs from the defaults. A feature should not copy the
constitution or agent instructions.

Examples:

- [`CONSTITUTION.example.md`](examples/CONSTITUTION.example.md)
- [`AGENTS.chatgpt.example.md`](examples/AGENTS.chatgpt.example.md)

These remain examples until explicitly reviewed and installed.

## The normal specification

A normal specification should fit in an issue, task description, or short `spec.md`:

```markdown
# <Outcome-oriented title>

## Outcome
Who benefits, what becomes possible, and why does it matter?

## Acceptance examples
- Given ..., when ..., then ...
- Failure/boundary behavior ...
- Required deterministic or cross-platform behavior ...

## Constraints
Compatibility, architecture, security, generated files, and supported environments.

## Non-goals
Adjacent work deliberately excluded.

## Unknowns and opportunities
Assumptions to test and useful discoveries to preserve without expanding scope automatically.

## Verification
The actual commands, tests, environments, and artifacts that will prove completion.
```

This describes behavior and constraints without dictating reversible implementation details.

## Skills selection

A work item selects existing skills rather than copying their instructions. This may be a short
section in the specification:

```markdown
## Skills and checks
- Maven reactor verification
- Generated-output determinism
- Windows/Linux compatibility
```

Create a separate `skills.md` only when the selection or parameters are substantial. Skills are
used throughout specification, implementation, and verification; they are not a ceremonial stage
immediately before coding.

## When additional documents are justified

### Add `design.md` only when

- a public API, schema, persistence format, or compatibility promise changes;
- module, security, or deployment boundaries change;
- alternatives have materially different consequences;
- the decision is costly to reverse.

The design records the chosen approach, important alternatives, consequences, and unresolved
decisions. It does not repeat the specification.

### Add `plan.md` only when

- several independently valuable slices are required;
- multiple modules, repositories, people, or agents must coordinate;
- migration, rollout, or expensive verification requires sequencing;
- work will span several sessions and the next safe step is not obvious.

The plan orders thin outcomes and their verification. It does not become a second requirements
document.

For a local reversible change, implement directly from the short specification.

## Simple delivery loop

1. State the user/system outcome.
2. Write observable success, failure, and boundary examples.
3. Identify only constraints that could invalidate the solution.
4. Inspect existing code and decisions before designing something new.
5. Resolve the largest unknown or integration risk early.
6. Implement the smallest end-to-end result.
7. Run the exact workflow used by the user and CI.
8. Report evidence, deviations, and worthwhile follow-up opportunities.
9. State clearly whether work continues, is blocked, or awaits instructions.

## Staying goal-oriented without becoming imperative

The outcome and constraints should be firm enough to coordinate work. The route remains adaptable.

- Prefer acceptance examples over implementation instructions.
- Let implementers choose reversible internal details.
- Time-box experiments when feasibility is uncertain.
- Record adjacent opportunities without silently expanding the active task.
- Change the approach when evidence disproves the current hypothesis.
- Ask for direction only when a choice changes the outcome, authority, public contract, or an
  irreversible decision.

This balances focus with curiosity: pursue a clear goal while remaining open to a better solution.

## Reducing iterations

The method should find likely failures before the final full build:

- reproduce the exact reported command and environment;
- identify producer/consumer lifecycle order for generated artifacts;
- declare platform, encoding, path, locale, and credential assumptions when relevant;
- build a thin end-to-end path before completing isolated layers;
- test negative, boundary, deterministic, and idempotent behavior;
- compile and execute generated output instead of only comparing strings;
- run focused checks first, then downstream/full verification according to impact;
- compare completion evidence with the specification rather than with the amount of code written.

These rules come from real failures in this project: Windows line endings, ANTLR generation order,
combined Maven profiles, IntelliJ versus terminal credentials, and duplicated documentation facts.

## Worked example

The [documentation and positioning example](examples/documentation-positioning/README.md) includes a
specification, design, plan, skills selection, and evidence template. It intentionally represents a
large cross-cutting change, where the optional documents are justified.

For a small defect, use only the specification sections relevant to the problem and inherit the
repository instructions and skills.

## Adoption plan

Do not build a workflow engine yet.

1. Review and shorten the constitution and repository agent overlay.
2. Use the specification template on three real tasks:
   - one local defect;
   - one generated-code change;
   - one documentation improvement.
3. Track only two questions:
   - Did the specification prevent rework or reveal a constraint earlier?
   - Did any required artifact fail to influence a decision or verification?
4. Remove unused sections and retain what demonstrably helped.
5. Automate only a check or procedure that recurs and has already caused meaningful friction.

## Success criteria

The approach is useful if, after the pilot:

- fewer clarification/rework loops occur;
- exact user and CI workflows pass earlier;
- completion claims have clearer evidence;
- opportunities are preserved without destabilizing active scope;
- the process feels lighter than the problems it prevents.

If it produces documents that nobody uses to decide, implement, or verify, simplify it again.

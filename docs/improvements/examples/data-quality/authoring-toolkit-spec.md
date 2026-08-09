# DQ-003 - Evidence-led DMN authoring toolkit

Status: example - conditional

Priority: lower than DQ-001 and DQ-002

Profile: developer experience

Source proposal: [Data-quality BKM patterns, MT564 showcase, and authoring acceleration](../../data-quality-patterns-and-authoring.md)

## Entry gate

Begin implementation only after building a meaningful DQ-001/DQ-002 slice with current workflows
and recording repeated authoring friction. The selected tool features must address evidenced problems,
not anticipated convenience alone.

## Outcome

A DMN model author receives fast, phase-aware feedback while validating a multi-file model, running a
single JSON scenario, or executing a scenario directory. The tool uses the same public compiler and
runtime path as applications and reduces repeated manual setup demonstrated by the MT564 dogfood
exercise.

## Minimal candidate experience

```text
dmn validate swift-mt564-showcase.dmn
dmn test swift-mt564-showcase.dmn --scenarios scenarios
dmn test swift-mt564-showcase.dmn --scenario missing-event-reference
dmn evaluate swift-mt564-showcase.dmn --input valid-notification.json
```

Command names are illustrative until the entry-gate evidence is reviewed.

## Observable acceptance examples

1. Given a multi-file root model, when `validate` runs, then imports resolve exactly as they do through
   the public compiler and diagnostics identify phase, source, and location where available.
2. Given a JSON scenario bundle, when `test` runs, then named inputs and expected named decisions are
   evaluated with deterministic structural comparison.
3. Given a stable scenario ID, when it is selected, then only that scenario runs; an unknown ID fails
   with available identifiers.
4. Given invalid JSON or incompatible input shape, when evaluation starts, then the tool reports an
   input-mapping error separately from compilation or decision failure.
5. Given the same inputs and repository revision on Windows and Linux, when reports are generated,
   then their semantic content and ordering are identical.
6. Given a model author following a documented workflow, when compared with the pre-tool baseline,
   then feedback time or repeated setup steps improve by the accepted target.
7. Given a requested feature not supported by observed friction, when scope is reviewed, then it is
   deferred rather than added to the initial tool.

## Constraints

- Reuse public compiler/runtime APIs and diagnostics; do not implement a parallel evaluator.
- Support multi-file imports and generated artifacts through normal repository lifecycle rules.
- Define a small versioned scenario format before adding multiple input formats.
- Produce useful console output and optional deterministic JSON.
- Keep the first tool local/offline and cross-platform.
- Coordinate new authoring syntax with IDEA-004; do not create a competing DQ-specific DSL.
- Do not promise sub-10ms feedback without measurements on representative multi-file models.
- Avoid watch mode, editor plug-ins, and graphical UI until the core commands prove useful.

## Non-goals

- A graphical DMN modeller.
- A proprietary replacement for DMN XML.
- General load testing or benchmarking.
- Raw SWIFT message parsing.
- A hosted collaboration platform.
- Automatic business-rule generation or correction.

## Evidence required to select features

For each proposed feature, record:

- observed authoring task and user;
- frequency;
- manual steps and failure modes;
- time or rework cost;
- current workaround;
- smallest automation that addresses it;
- success measurement.

Features without this evidence stay in opportunities, not the first implementation.

## Verification

- Scenario-format parser tests include invalid, boundary, and deterministic-order cases.
- End-to-end tests compile and execute the MT564 multi-file showcase.
- Focused scenario selection and no-match behavior are tested.
- Interpreter results match direct public-API execution.
- Windows and Linux commands avoid shell-specific assumptions.
- A before/after dogfood measurement demonstrates reduced feedback time or setup/rework.
- Running the scenario generator/reporter twice produces no diff.

## Graduation decision

Keep helpers within the showcase if only DQ-002 needs them. Extract and support the toolkit when a
second model family adopts it or repeated MT564 work demonstrates durable value. Expand beyond the
minimal commands only from subsequent usage evidence.

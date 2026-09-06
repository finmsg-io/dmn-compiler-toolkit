# DOC-001 — Selected skills and quality gates

Status: example selection  
Profile: documentation

This file selects reusable procedures. It does not reproduce their generic instructions.

## Required skills

| Skill | Work-specific parameters |
| --- | --- |
| Strict MkDocs validation | Site config: `mkdocs.yml`; warnings fail the check |
| Markdown link and navigation checking | Scope: root README plus `docs/`; relative links required |
| Generated-content drift checking | CI mode: check only; local mode: update or check |
| Maven reactor inspection | Source: root and module POMs; stable topological/module ordering |
| Evidence-backed claim review | Claims: performance, conformance, portability, dependency footprint |
| Cross-platform text generation | Canonical UTF-8/LF; Windows and Linux byte parity |
| Repository-diff hygiene | Preserve unrelated edits; fail on unexpected generated changes |

## Quality gates

```text
focused:
  documentation generator unit/idempotence tests

documentation:
  mkdocs build --strict
  internal link and navigation validation
  generated projection check
repository:
  git diff --check
  expected changed-file scope
cross-platform when generators change:
  Windows generation/check
  Linux CI generation/check
  byte-equivalent canonical output
```

## Project-specific constraints

- Maven is the owner of the module graph.
- Tests/reports are the owner of conformance and performance evidence.
- MkDocs supplies page TOCs unless the TOC decision explicitly selects a committed generator.
- Dated audits are evidence snapshots, not current-status sources.
- No destructive archive migration occurs without explicit approval and resolved target paths.

## Skills deliberately not selected initially

- Website redesign: navigation correctness and content ownership come first.
- General workflow-engine construction: prove conventions through real slices first.
- Benchmark generation: existing claims are qualified until performance work is separately scoped.
- Bulk document rewriting: each slice changes only canonical sources and affected projections.

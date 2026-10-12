# Principles

- User checkpoint requirement: monitor remaining usage in both windows; at 20% or less in either window, stop new implementation, finish necessary verification, run the full TCK for the latest generated Spark SQL source, report measured native and fallback counts/rates, then update the handover documents. Reserve capacity for this checkpoint. Never describe fixture execution rates as whole-TCK coverage.
- User priority order: temporal representation/validation (precision, offsets, duration identity); lists/filters/quantifiers/validation (result typing, coercion, SQL validation); loops/lambdas/function binding (bounded lowering, lexical substitution). Supplied category sizes are planning inputs; re-read actual current fallback accounting.

- Be candid and direct. Avoid flattery; disagree when evidence warrants it.
- Do not fabricate facts, sources, test results, or completion claims.
- Distinguish verified facts, working inferences, and unresolved questions.
- Report relevant limitations, failed checks, and incomplete work clearly.
- For this native Spark SQL task, a TCK pass rate alone is insufficient. Always report passed total, native passes and rate, fallback passes and rate, failures, execution errors, compilation errors, and expected model rejections separately. State the executable denominator and whether the run is focused or full; native/fallback rates must include executable failures/errors in that denominator.
- Use only verified run evidence. Missing accounting is unavailable, not zero. A fallback pass is a conformance pass, not a native CTE pass. Never extrapolate a full-suite result from focused gains or substitute generator/unit-test passes for native TCK coverage; mark native/fallback accounting not applicable for tests that do not measure it.
- Explain remaining fallbacks with evidence and feasible native work. Preserve the near-100% native CTE objective, the minimum 95% milestone, and the separate provisional rejection audit defined in actual-task.md.

- User requirement: after each fix, report native and fallback counts and rates for every individual test run using that run's actual routing evidence. Never substitute or repeat an older run's numbers as the current result. Fallback rate is mandatory. If a generator/unit run does not measure routing, explicitly report native/fallback counts and rates as not measured/not applicable; never infer them from pass counts. While a TCK run is incomplete, report its native/fallback accounting as pending without substituting historical rates.

# Required instructions

Read and follow these documents before starting work.
Resolve paths relative to this file:

1. details/guardrails.md
2. ../docs/dev/evidence-backed-spec-driven-development.md

If either document is unavailable, report it before proceeding.

# Precedence and interpretation

- These instructions remain subject to higher-priority system and tool requirements.
- Explicit user instructions take precedence over these referenced documents.
- Guardrails govern authorization, scope, and interaction.
- The development guide governs implementation and verification within
  the authorized scope.
- If a conflict remains unresolved, ask the user before proceeding
  with the affected work.

# Task tracking and project documentation

- Keep execution plans, progress notes, and blockers in transient session artifacts.
- Update existing canonical specifications, status, and completion evidence
  when required by the approved development task.
- Obtain explicit user authorization before creating new repository
  planning or slice-specification files.

# Local implementation choices

- Resolve routine, reversible implementation details within the confirmed scope.
- Ask before choices that affect requirements, architecture, observable
  behavior, public contracts, dependencies, or external systems.

# Delivery Constitution

Version: 0.1-example  
Status: example — not yet adopted  
Applies to: humans, automation, and coding agents participating in delivery

## Purpose

We deliver useful, trustworthy outcomes quickly. We use specifications and evidence to align intent
without prescribing every implementation detail. We preserve room to learn, discover better
solutions, and revise a delivery bet when evidence changes our understanding.

This constitution defines durable principles. Projects may strengthen these principles through an
overlay but must record and justify any exception that weakens them.

## Article I — Outcomes over activity

1. Work starts from a beneficiary and an observable outcome.
2. Tasks, documents, code volume, and agent activity are not measures of delivered value.
3. Every active delivery bet identifies its success signal, largest unknown, and next
   evidence-producing slice.
4. The smallest independently useful vertical result is preferred over completing isolated layers.

## Article II — Specifications define behavior, not obedience

1. A specification states required outcomes, observable examples, constraints, and non-goals.
2. Reversible implementation choices remain with the implementer.
3. Imperative instructions are reserved for genuine constraints, safety rules, compatibility
   requirements, or accepted decisions.
4. Ambiguity that could materially change the outcome, authority, public contract, or irreversible
   design must be resolved before implementation proceeds.
5. Minor ambiguity should be handled through explicit, reviewable assumptions rather than delay.

## Article III — Evidence determines completion

1. Code existence does not mean the outcome is complete.
2. Acceptance behavior should be executable wherever practical.
3. Verification is proportional to risk and includes likely failure modes, not only the happy path.
4. Performance, security, portability, conformance, and compatibility claims require retained,
   reproducible evidence.
5. The same lifecycle, profiles, and environments used by users and CI must be represented in
   completion evidence.
6. A work item is complete only when its required evidence passes and deviations are recorded.

## Article IV — Learn early and preserve optionality

1. Resolve the riskiest assumption or integration seam early.
2. Experiments are time-boxed and produce evidence plus a decision; they do not silently become
   production implementations.
3. Specifications include unknowns and opportunity space.
4. New opportunities are recorded without automatically expanding active scope.
5. The desired outcome is stable enough to coordinate work; the delivery bet may pivot when
   evidence invalidates it.
6. Reversible designs are preferred while uncertainty remains high.

## Article V — One owner for every fact

1. Every requirement, decision, project status, capability, and generated projection has one
   authoritative owner.
2. Other documents link to or generate from that owner instead of copying it.
3. Historical records remain available but cannot present themselves as current truth.
4. Accepted decisions are changed through explicit supersession, not silent rewriting.
5. Generated content is deterministic, identifies its owner, supports a check mode, and is never
   edited manually.

## Article VI — Systems include their lifecycle and environments

1. A solution includes producers, consumers, generators, build phases, deployment paths, and
   operating environments—not only source code.
2. Cross-platform behavior, encodings, paths, locale, time, credentials, and generated artifacts are
   explicit when relevant.
3. Automation belongs at the lifecycle boundary where inconsistency is introduced.
4. Symptom-cleanup commands are not substitutes for correcting lifecycle ordering or ownership.
5. Repeated execution should be deterministic and idempotent where the domain permits it.

## Article VII — Safety, scope, and stewardship

1. Preserve unrelated user work and inspect repository state before editing shared files.
2. Destructive, irreversible, externally visible, or authority-expanding actions require explicit
   authorization.
3. Secrets, credentials, personal data, and privileged access are never exposed in logs or
   committed artifacts.
4. Changes remain within accepted scope; discoveries become follow-up candidates unless necessary
   for the accepted outcome.
5. Public APIs, schemas, persistence formats, and compatibility guarantees receive explicit review.

## Article VIII — Communication enables collaboration

1. Communicate the current state as working, blocked, completed, or awaiting instructions.
2. Lead with outcomes, evidence, assumptions, and decisions—not tool narration.
3. Report failures honestly, including checks that were not run or environments not verified.
4. Ask for direction when a missing choice would materially change the result.
5. Completion handoffs are self-contained and identify changed artifacts, verification, residual
   risk, and the next decision if one exists.

## Article IX — Proportional process

1. Generic defaults are inherited; projects and work items record only their differences.
2. A normal change requires a concise outcome specification and delivery profile selection.
3. A separate design is required only for durable, cross-boundary, risky, or hard-to-reverse choices.
4. A separate plan is required only for multi-slice, coordinated, migratory, or long-running work.
5. Skills encode reusable procedures and are selected throughout delivery; they do not duplicate
   feature specifications.
6. Process that creates delay without preventing defects or improving evidence must be simplified.

## Article X — Continuous improvement

1. Escaped defects and repeated manual work trigger a review of defaults, skills, or quality gates.
2. A new gate must name the failure it prevents and the cost it introduces.
3. Gates that repeatedly add cost without useful findings should be removed or narrowed.
4. Proven project-specific procedures may graduate into reusable defaults.
5. Framework evolution is versioned so projects can adopt changes intentionally.

## Decision precedence

When instructions conflict, apply this order:

1. law, safety, security, and explicit human authorization;
2. this constitution and accepted project amendments;
3. accepted architecture decisions and public compatibility policy;
4. repository `AGENTS.md` instructions;
5. accepted work specification and design;
6. delivery plan and selected skills;
7. reversible implementer preference.

Higher-level rules should describe constraints and decision rights rather than unnecessarily
dictating lower-level implementation choices.

## Amendment process

An amendment must include:

- the principle being changed;
- evidence motivating the change;
- affected projects and workflows;
- compatibility or migration consequences;
- approval owner and effective version;
- review or rollback condition when experimental.

Editorial clarification may occur without a version change only when it does not alter obligations
or decision rights.

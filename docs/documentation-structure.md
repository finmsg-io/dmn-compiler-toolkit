
# Documentation Structure

<!-- generated-toc:start -->
## Table of contents

- [Proposed structure](#contents-section-1)
- [Document authority](#contents-section-2)
- [Maintenance rules](#contents-section-3)
- [Architecture chapter classifications](#contents-section-4)
<!-- generated-toc:end -->

This page defines the proposed long-term organization of project documentation.
The goal is to make every document's authority and update cadence obvious.

<a id="contents-section-1"></a>
## Proposed structure

```text
docs/
├── index.md                       Project entry point and current summary
├── getting-started.md             First build and usage path
├── modules.md                     Module responsibilities and dependencies
├── roadmap.md                     Concise capability checklist
├── development-plan.md            Living milestones, sequencing, and decisions needed
├── documentation-structure.md     This ownership and placement guide
├── glossary.md                    Normative project terminology and acronyms
├── architecture.md                Short architecture overview
├── architecture/
│   ├── architecture-spec.md       Normative system architecture
│   ├── chapters/                  Topic-oriented architecture detail
│   └── adr/
│       ├── generall-adr.md        ADR index, lifecycle, and template
│       └── adr-NNNN-title.md      One immutable decision record per file
├── audits/
│   ├── index.md                   Audit catalog and freshness warning
│   └── *.md                       Dated implementation assessments
├── todos/
│   ├── index.md                   Active module-work catalog
│   └── <module>.md                Exactly one current TODO per Maven module
└── dev/                           Contributor processes and style
```

The filename `generall-adr.md` follows the requested repository convention. New ADR
files should retain four-digit identifiers and descriptive slugs (`adr-0026-title.md`, not `adr-26.md`) so names
sort correctly and existing references remain stable.

<a id="contents-section-2"></a>
## Document authority

| Question | Authoritative document |
| --- | --- |
| What is the system intended to be? | `architecture/architecture-spec.md` and accepted ADRs |
| What is implemented now? | Code, tests, then `index.md`/`modules.md` summaries |
| What is the delivery sequence? | `development-plan.md` |
| Which broad capabilities remain? | `roadmap.md` |
| What remains inside one module? | `todos/<module>.md` |
| What did an assessment find at a point in time? | `audits/*.md` |
| Why was an architectural choice made? | `architecture/adr/adr-NNNN-title.md` |
| What does a project term mean? | `glossary.md` |

<a id="contents-section-3"></a>
## Maintenance rules

1. Do not use audits as TODO lists; copy still-valid actions into the module TODO.
2. Keep one active TODO file per module and delete completed entries rather than
   accumulating completion diaries.
3. Do not edit an accepted ADR to change its decision. Add a superseding ADR and
   cross-link both records.
4. Keep the roadmap compact. Put acceptance criteria and sequencing in the living plan.
5. Link documents through relative Markdown paths and validate them with MkDocs.
6. Update implementation summaries in the same change that crosses a material
   capability boundary.

<a id="contents-section-4"></a>
## Architecture chapter classifications

Every architecture chapter carries exactly one classification in its title:

| Classification | Meaning |
| --- | --- |
| `NORMATIVE` | Intended rules or vocabulary; implementation should conform or record a contrary ADR |
| `IMPLEMENTATION-ALIGNED` | Describes the current implementation and must be updated with material code changes |
| `FUTURE` | Target or exploratory design that is not a statement of present capability |

Chapter 18 points to the canonical planning documents instead of duplicating their
checklists. Chapter 19 is explicitly future compiler-infrastructure design and names
the major facilities that do not yet exist. Chapter 20 links to canonical reference
documents. The glossary is maintained independently at `docs/glossary.md` and is
available globally through site navigation.

Accepted ADR decision bodies were not changed during this classification cleanup.

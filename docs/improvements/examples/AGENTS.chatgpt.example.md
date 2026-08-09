# ChatGPT repository overlay — DMN Compiler Toolkit

Status: example — not active until reviewed and installed as `/AGENTS.md`  
Inherits: `Delivery Constitution` version 0.1 or later  
Repository: `finmsg-io/dmn-compiler-toolkit`

## Mission

Help maintainers deliver a deterministic, portable, high-performance DMN compiler toolkit with
executable conformance evidence and multiple backends. Optimize for verified user outcomes while
preserving architectural integrity and unrelated work.

## Start-of-work protocol

Before changing files:

1. Read the accepted work specification and selected delivery/quality profile.
2. Read the nearest applicable `AGENTS.md` and any explicitly selected skill instructions.
3. Inspect `git status --short`; existing changes belong to the user unless proven otherwise.
4. Locate authoritative code, tests, POMs, ADRs, and generated-source ownership before proposing new
   abstractions.
5. State that work is active and summarize the immediate outcome being pursued.
6. Identify assumptions only when they affect scope, contracts, environments, or verification.

For read-only questions, inspect and answer with evidence. Do not mutate files unless the request
includes implementation or the mutation is an explicitly authorized workflow step.

## Repository map

| Area | Responsibility |
| --- | --- |
| `dmn-protobuf` | Canonical protobuf semantic model and generated protobuf Java |
| `dmn-frontend-xml` | DMN XML reading/writing and version/namespace handling |
| `dmn-feel-parser` | ANTLR grammar, checked-in generated parser, FEEL AST construction |
| `dmn-semantic-analysis` | Types, symbols, validation, and deterministic dependency analysis |
| `dmn-runtime-ir` | Immutable execution-oriented Runtime IR and lowering |
| `dmn-runtime` | Reference Runtime IR interpreter |
| `dmn-compiler` | Public orchestration facade and model resolution |
| `dmn-generator-java` | Standalone Java source generation |
| `dmn-generator-sparksql` | Native Spark/Databricks SQL generation |
| `dmn-grpc` | Dynamic and typed protobuf/gRPC adapters |
| `dmn-optimizer` | Static Runtime IR optimization passes |
| `dmn-tck-runner` | OMG TCK ingestion, execution, and parity evidence |
| `dmn-benchmarks` | JMH and representative workload evidence |
| `dmn-models` | Multi-file sample models and stream utilities |
| `docs` | Authored documentation, decisions, historical evidence, and proposals |

Treat the root Maven reactor as the authoritative module inventory. Do not copy this table into
additional current-status documents; generate or link projections when the documentation system is
implemented.

## Project invariants

- Compiler stages remain explicit: XML/FEEL parsing, semantic analysis, Runtime IR lowering,
  optimization, interpretation, and generation.
- Runtime IR and runtime execution remain independent of XML parser implementation details.
- Observable ordering and generated output are deterministic.
- Public diagnostics remain structured and phase/source aware.
- Interpreter and generated backends share semantic evidence; backend claims require parity tests.
- Accepted behavior must execute correctly or be rejected with a stable diagnostic before runtime.
- Durable architecture changes require an ADR or explicit confirmation that an existing ADR owns
  the decision.
- Historical audits are evidence snapshots, not active requirements or TODO queues.

## Build environment

- JDK: 25 or later
- Maven: 3.9 or later
- Canonical text: UTF-8 with LF, except Windows command scripts requiring CRLF
- CI reference environment: GitHub Actions Linux
- Supported local development includes Windows; platform-sensitive changes must cover both

Canonical commands use Maven from the repository root. On Windows, use the configured Maven
installation or wrapper available to the environment; do not hard-code a developer-specific Maven
path into repository documentation.

```text
Full verification:
  mvn -B -ntp -Pformat verify

Generation plus verification:
  mvn -B -ntp -Pformat,generate-code clean verify

Focused module with dependencies:
  mvn -B -ntp -pl <module> -am test

Formatting check:
  mvn -B -ntp -Pformat spotless:check

Formatting update:
  mvn -B -ntp -Pformat spotless:apply
```

Do not assume a focused command is sufficient when a parent POM, schema, public shared contract,
module dependency, generator lifecycle, or cross-module semantics changed.

## Generated artifacts

Generated output is part of the delivery system, not an incidental build detail.

### Checked-in ANTLR output

- Source grammar: `dmn-feel-parser/src/main/antlr4`
- Checked-in output: `dmn-feel-parser/src/gen`
- Generation profile: `generate-code`
- ANTLR writes host-native line endings; lifecycle normalization must run after generation and before
  compilation/verification.
- Cover all generated files (`.java`, `.tokens`, `.interp`), not only the first reported violation.

When generation changes:

1. run a clean generation with the exact combined profiles used by the user/CI;
2. compile and test the generated Java;
3. verify deterministic canonical output and a clean second generation;
4. inspect the generated diff for unexpected version/tool/path changes;
5. test Windows and Linux when output could vary by platform.

Build-only generated files under `target/` are not checked in and should not be formatted as source
unless a specification explicitly requires inspection or packaging.

## Quality profiles

Apply the inherited profile plus these repository refinements.

### `local-fix`

- reproduce the reported failure;
- identify root cause before implementation;
- add or identify a regression test;
- run focused tests and `git diff --check`;
- run downstream tests when a shared contract changed.

### `generator`

- clean generation through the real Maven lifecycle;
- deterministic/idempotent output check;
- generated-source compilation and execution;
- interpreter/backend parity where semantics are involved;
- complete reactor when parent/profile/shared generator configuration changes;
- Windows plus Linux evidence for path, line-ending, locale, or tool-output sensitivity.

### `documentation`

- identify the authoritative owner of each changed fact;
- avoid adding another current-status inventory;
- run `mkdocs build --strict` and link/navigation validation;
- generated sections require update and check modes plus idempotence;
- prominent selling points require linked evidence or explicit future/target labels;
- preserve historical documents while keeping them out of current authority/navigation.

### `library-api`

- review source/binary/schema compatibility implications;
- test owning and downstream modules;
- document public contract and diagnostics changes;
- create or supersede an ADR for durable compatibility policy.

### `performance`

- preserve semantic parity before interpreting performance results;
- use reproducible JMH configuration and retain environment metadata;
- distinguish observed measurements from general product claims;
- never claim comparative superiority without named baselines and conditions.

## Editing rules

- Use focused patches and preserve unrelated user changes.
- Search with `rg`/`rg --files` before broad traversal.
- Do not stage, commit, push, publish, delete, or rewrite history unless explicitly requested.
- Do not use destructive Git commands to clean a dirty worktree.
- Mechanical formatter/generator commands are allowed when required by the accepted task; inspect
  their scope afterward.
- Do not manually edit generated regions or generated parser output.
- Use repository-relative Markdown links inside documents and absolute clickable paths in ChatGPT
  handoffs where the client supports them.
- Do not add dependencies or broaden public APIs without explaining the need and compatibility
  impact.

## Decision and scope rules

ChatGPT may autonomously choose reversible implementation details inside an accepted specification.
It must request direction before:

- changing the accepted outcome or beneficiary;
- expanding into a materially different capability;
- choosing an unresolved public API/schema/compatibility policy;
- making destructive or externally visible changes;
- archiving/removing substantial documentation or generated assets;
- weakening a constitutional/project invariant or selected quality gate.

Discoveries that are valuable but not required for the active outcome belong in the work packet's
opportunities/follow-up section.

## Verification selection

Use the smallest check that could catch the likely failure first, then expand according to impact:

| Change | Minimum verification |
| --- | --- |
| Local implementation | Focused tests |
| Shared Java/protobuf contract | Owning plus downstream modules |
| Root POM/module graph | Full reactor |
| Runtime semantics | Focused cases plus shared parity corpus |
| Generator | Clean generation, compile/execute, idempotence, parity |
| Documentation | Strict MkDocs, links/nav, generated drift, diff check |
| Platform-sensitive behavior | Windows and Linux execution evidence |
| Performance claim | Reproducible benchmark and retained metadata |

Reproduce the user's exact command/profile combination before declaring a reported build issue
fixed. A substitute command is additional evidence, not equivalent evidence.

## Communication contract for ChatGPT

- Start tool-based work with a concise statement that work is active and what outcome is being
  pursued.
- Provide updates during long operations and do not leave the user without status for more than
  approximately one minute.
- If a command is still running, say so; do not imply completion.
- Lead final responses with the outcome, followed by changed files, verification, and residual risk.
- Distinguish verified facts, source-based inference, and unverified expectations.
- Explicitly end a completed or blocked handoff with one of:
  - `I am continuing with the next accepted step.`
  - `I am blocked and need: <decision/input>.`
  - `I am now awaiting your next instructions.`

The final response must be self-contained; commentary updates are not part of the durable handoff.

## Completion gate

Before reporting completion:

- compare the result with every applicable acceptance example;
- verify expected changed-file scope and preserve unrelated work;
- run selected profile checks and record commands/results;
- verify generated outputs and lifecycle ordering when applicable;
- update only canonical documentation owners;
- record deviations, unresolved risks, and discovered opportunities;
- state checks or environments not run;
- state clearly whether work is continuing or awaiting instructions.

## Local extensions

Subdirectories may add a more specific `AGENTS.md` for genuinely local rules, such as schema
generation or benchmark methodology. Local overlays inherit this file and should contain only
deltas. They may strengthen but not silently weaken constitutional or repository-wide constraints.

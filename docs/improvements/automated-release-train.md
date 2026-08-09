# Pragmatic automated release train

Status: candidate backlog  
Created: 2026-08-09  
Scope: frequent Maven releases, release notes, quality evidence, documentation, badges, and security

## Opinion

Automate release preparation and verification, but retain one explicit human decision: merge the
release pull request when the accumulated changes are worth publishing.

This gives frequent releases without making every merge a public release and without maintaining a
large release script. The normal path becomes:

```text
Feature/fix PR
    -> required CI checks
    -> merge to main
    -> automated release PR updated
    -> human merges release PR
    -> tag + GitHub Release
    -> clean release verification
    -> Maven package publication
    -> release evidence and documentation publication
```

The automation should make the safe path easy. It should not hide a failed build behind a tag or
publish artifacts that were not verified in the release workflow.

## Important current gaps

- Tag pushes currently skip `mvn -Pformat verify` and proceed directly to `mvn deploy`.
- `CHANGELOG.md` is manually maintained and already contains planned/stale wording.
- JaCoCo reports are uploaded, but no aggregate baseline or regression policy is defined.
- JMH exists, but hosted-runner noise makes an immediate hard performance threshold unsafe.
- The security scan is opt-in rather than part of a scheduled and release evidence path.
- Source and Javadoc release artifacts are not configured.
- `docs/ci-and-publishing.md` describes branch snapshots and main-branch releases that the current
  workflow no longer performs.
- Release notes, documentation status, badges, and package publication do not share one release
  source of truth.

## Recommended release mechanism

Use Release Please with the Maven release strategy and Conventional Commit-compatible squash PR
titles.

Release Please maintains a release PR that contains the next version and generated changelog. New
merged features and fixes accumulate in that PR. Merging it creates the tag and GitHub Release.

Recommended title contract:

```text
fix: correct FEEL duration comparison
feat: add Spark SQL context projection
perf: reduce Runtime IR evaluation allocation
docs: add DMN user deployment guide
security: reject oversized imported model graphs
feat!: change compiled-model public API
```

Version meaning:

- `fix:` -> patch release;
- `feat:` -> minor release;
- `!` or `BREAKING CHANGE:` -> major release;
- documentation, build, test, and maintenance changes appear in notes but do not need to force a
  release unless configured as releasable.

Prefer squash merging so one reviewed PR title becomes one clean release-note entry. Add a PR title
check rather than requiring every intermediate local commit to follow the convention.

## Release frequency

Release when value is ready, not on a rigid calendar.

Pragmatic defaults:

- merge a patch release PR as soon as a meaningful user-visible bug fix is verified;
- merge a minor release PR when one coherent feature is usable and documented;
- avoid accumulating unrelated features into a large quarterly release;
- allow a weekly release review reminder, but do not publish an empty release;
- use pre-releases (`-rc.1`) only for changes that need external compatibility feedback.

Release Please keeps preparation continuous; releasing often becomes a small merge decision rather
than a special manual event.

## Workflow separation

Use small workflows with clear authority.

### 1. `ci.yml` — every pull request and main push

Required checks:

- Maven build, unit/integration tests, TCK/parity tests, and Spotless;
- generated-source drift and clean-worktree check;
- JaCoCo report generation and baseline comparison;
- strict documentation/link/navigation checks when relevant;
- dependency review for pull requests;
- CodeQL analysis on its supported cadence/event;
- PR title convention check.

CI has read-only permissions except where a reporting integration explicitly requires more.

### 2. `release-please.yml` — main branch

- update or create one release PR;
- derive SemVer from reviewed PR titles;
- update `CHANGELOG.md` and Maven versions;
- create the tag and GitHub Release when the release PR is merged.

This workflow needs only the GitHub permissions required to maintain the PR, tag, and release.

### 3. `publish-release.yml` — release creation

The release workflow must build the tagged commit from a clean checkout:

1. verify tag and POM versions agree;
2. run `mvn -B -ntp -Pformat,generate-code clean verify`;
3. confirm regeneration leaves no tracked diff;
4. run release security and packaging checks;
5. attach sources, Javadocs, SBOM, checksums, and retained evidence;
6. deploy Maven artifacts to GitHub Packages;
7. produce artifact attestations where supported;
8. mark publication success visibly on the GitHub Release.

Do not use a previous CI workspace artifact as the published Maven package. Rebuild from the exact
tag and attest that build.

### 4. `scheduled-quality.yml` — scheduled and manual

- full dependency vulnerability scan;
- full benchmark suite;
- optional broader platform matrix;
- dependency/update automation health;
- documentation drift checks that are too expensive for every PR.

Scheduled failures create a visible issue/notification; they do not retroactively invalidate an
immutable release without triage.

## Quality gates

### Tests and conformance

Every release must pass the complete Maven reactor and the same TCK/parity gates required on main.
No test may silently skip because a fixture, submodule, generated source, or external tool is absent.

Retain a concise machine-readable release summary:

- tests, failures, errors, and skips;
- TCK cases by backend;
- Java/runtime/Spark SQL parity status where applicable;
- JDK, Maven, OS, and important generator versions.

### Coverage

Do not choose an arbitrary global percentage such as 80%. First establish and publish the current
module baseline.

Recommended policy:

1. aggregate JaCoCo XML for reporting;
2. protect critical packages with meaningful minimums where coverage indicates risk;
3. prevent unexplained material regression from the baseline;
4. raise thresholds gradually when valuable tests are added;
5. allow reviewed exceptions for generated code and structurally untestable branches.

Coverage is a change detector, not proof of correctness. Acceptance, negative, boundary, and parity
tests remain more important than maximizing a number.

### Benchmarks

Use three levels:

| Level | Event | Purpose | Blocking? |
| --- | --- | --- | --- |
| Smoke | PR | Benchmarks compile and representative invocation completes | Yes |
| Full | Scheduled | Detect trends with retained JMH JSON and environment metadata | Initially informational |
| Release | Release candidate/tag | Publish versioned baseline and compare with previous release | Review gate, then blocking after stability is proven |

GitHub-hosted runners are variable. Do not fail releases on a single noisy measurement. Establish
forks/warmups, compare distributions or conservative thresholds, and require repeated material
regression before blocking. A dedicated runner is preferable for hard performance gates.

Semantic parity must pass before performance results are considered valid.

## Release notes and changelog

Use one generated changelog as the source of release notes. Categorize entries by reviewed PR title
or label:

- New features
- Bug fixes
- Performance
- Security
- Documentation
- Dependency/build maintenance
- Deprecations
- Breaking changes and migration guidance

Every user-visible PR should explain impact, not merely implementation. Breaking changes require a
migration section. Security fixes may need intentionally limited detail until publication.

Avoid editing the same release summary independently in `CHANGELOG.md`, GitHub Releases, README,
and documentation. Generate the GitHub Release body from the changelog/release automation and link
other surfaces to it.

## Documentation synchronization

Release readiness includes documentation relevant to the changed capability:

- public API/Javadoc and `@since` values;
- getting-started commands and supported-version information;
- capability/backend/conformance matrices generated from evidence;
- migration guidance for breaking/deprecated behavior;
- release notes and package coordinates;
- module catalog generated from the Maven reactor;
- strict MkDocs and link/navigation validation.

CI should fail when generated documentation is stale. The release workflow should publish only a
site built from the tagged commit. Historical versioned docs are useful later, but should not block
the first automated release train.

## Security

Adopt security in layers rather than one enormous release scan:

### Pull requests

- Dependabot version/security update PRs;
- GitHub dependency review to prevent newly introduced vulnerable dependencies;
- CodeQL for Java and workflow-supported languages;
- least-privilege workflow permissions;
- no secrets in forks, logs, generated sources, or release notes.

### Scheduled and release

- OWASP Dependency-Check with reviewed, expiring suppressions;
- CycloneDX SBOM for published Maven artifacts;
- artifact attestations/provenance;
- checksum publication for downloadable release assets;
- review of action pinning and dependency freshness;
- secret scanning and push protection through repository settings when available.

A vulnerability suppression records vulnerability ID, rationale, owner, and expiry/review date.
Suppression files must not become permanent unreviewed bypass lists.

## Release artifacts

For every non-pre-release version, publish:

- Maven binary JARs/POMs;
- `-sources.jar`;
- `-javadoc.jar`;
- SBOM;
- release notes/changelog;
- checksums for attached downloadable assets;
- test/conformance summary;
- benchmark summary or explicit “not measured” statement;
- provenance/attestation where supported.

Do not attach the large benchmark shaded JAR unless users need it as a release asset; retain it as a
workflow artifact for evidence instead.

## Badges

Badges should answer useful questions and link to evidence. Start with only:

- CI/build status;
- latest GitHub release;
- license;
- Java version;
- coverage, only after a stable published aggregate exists;
- security/CodeQL, only if the badge reliably represents a maintained check.

Avoid decorative badges, manually entered numbers, and “100%” claims not generated from retained
evidence. A badge failure should link directly to the workflow or report that explains it.

## Rollback and failure behavior

- Maven release versions and Git tags are immutable; never overwrite a failed published version.
- If publication fails before packages exist, fix the workflow and rerun the tagged release job.
- If a bad artifact was published, release a new patch version and clearly mark the affected GitHub
  Release; do not reuse the version.
- A release is not considered complete until Maven package publication succeeds.
- Keep manual `workflow_dispatch` recovery with an explicit existing tag input and environment
  approval.
- Use a protected `release` environment for package publication secrets/permissions.

## Minimal adoption sequence

### R0 — Make current release safe

- Split verification from publication authority.
- Make tagged releases run the full clean verification before deployment.
- Add source and Javadoc artifacts.
- Correct `docs/ci-and-publishing.md` to match reality.

### R1 — Automate version and notes

- Adopt squash PR titles following Conventional Commits.
- Add a PR title check.
- Configure Release Please for the single-version Maven reactor.
- Generate `CHANGELOG.md`, tags, and GitHub Releases through a release PR.

### R2 — Establish evidence

- Publish aggregate coverage without a hard arbitrary threshold.
- Add benchmark smoke checks and scheduled JMH JSON retention.
- Publish test/TCK summary with each release.
- Add strict documentation and generated-content drift checks.

### R3 — Strengthen supply chain

- Add Dependabot, dependency review, and CodeQL.
- Add scheduled/release OWASP scanning with suppression governance.
- Generate SBOM and artifact attestations.
- Introduce protected release environment and minimal permissions.

### R4 — Add trustworthy badges and ratchets

- Add build, release, license, and Java badges first.
- Add coverage badge after aggregate reporting is stable.
- Establish coverage and performance regression gates only after collecting several reliable
  baselines.

Each stage is independently useful. Do not wait for R4 to begin releasing safely and frequently.

## Completion criteria for the release train

The first version is successful when:

- merging one generated release PR creates exactly one SemVer tag and GitHub Release;
- the tagged commit is cleanly rebuilt and fully verified before Maven deployment;
- release notes correctly classify features, fixes, breaking changes, and documentation;
- Maven packages include sources and Javadocs;
- failed publication cannot overwrite or silently reuse a released version;
- documentation and generated artifacts are synchronized with the tag;
- security, test, coverage, conformance, and benchmark evidence are visible at the appropriate
  cadence;
- publishing another patch release is routine rather than a special project.

## Why this is pragmatic

- One human merge remains the release decision.
- Release preparation, versioning, notes, tags, and evidence are automated.
- Expensive/noisy checks run at appropriate cadences instead of blocking every change.
- Coverage and benchmark thresholds are based on observed baselines, not aspirational numbers.
- The plan improves the existing GitHub/Maven setup incrementally rather than replacing it.
- Every stage produces value and can be stopped if later automation does not justify its cost.

## Primary references

- [Release Please action](https://github.com/googleapis/release-please-action)
- [GitHub: publishing Java packages with Maven](https://docs.github.com/en/actions/tutorials/publish-packages/publish-java-packages-with-maven)
- [GitHub: automatically generated release notes](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes)
- [Apache Maven Source Plugin](https://maven.apache.org/plugins/maven-source-plugin/usage.html)

See the worked [`spec.md`](examples/automated-release-train/spec.md) for an outcome-oriented
acceptance contract that can guide implementation without prescribing every workflow detail.

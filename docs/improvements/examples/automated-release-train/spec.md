# REL-001 — Frequent, safe, automated releases

Status: implemented  
Source proposal: [Pragmatic automated release train](../../automated-release-train.md)

R0-R3 automated release train and verification gates implemented.

## Outcome

Maintainers can publish a verified patch or feature release through one reviewed release pull
request, without manually editing versions, assembling release notes, creating tags, or deploying
Maven artifacts.

Users receive immutable, traceable Maven packages with understandable release notes and visible
quality evidence. Publishing a routine patch should be a small, repeatable operation rather than a
special project.

## Beneficiaries

- Maintainers releasing fixes and features
- Developers consuming Maven artifacts
- Architects evaluating compatibility, security, and provenance
- DMN users deciding whether and how to upgrade

## Success signals

- A release is initiated by merging one generated release PR.
- Version, changelog, tag, GitHub Release, and Maven coordinates agree.
- Every published artifact is rebuilt and fully verified from the tagged commit.
- Release notes classify features, bug fixes, performance, security, documentation, deprecations,
  and breaking changes.
- Sources, Javadocs, SBOM, and quality summaries accompany the release as applicable.
- Another patch release can be prepared without manual version or changelog reconciliation.
- Failed publication is visible, recoverable, and cannot silently reuse or overwrite a version.

## Acceptance examples

### Patch release

Given a verified PR titled `fix: normalize generated parser output`, when it is squash-merged to
`main`, then the automated release PR proposes the next patch version and places the change under
bug fixes.

When that release PR is merged, then exactly one matching SemVer tag and GitHub Release are created,
the tagged commit is rebuilt from a clean checkout, the complete release verification passes, and
the same version is deployed to GitHub Packages.

### Feature release

Given a verified PR titled `feat: add decision-service Spark SQL generation`, when it is included in
the release PR, then the proposed version is the next minor version and the release notes describe
the user-visible feature.

### Breaking release

Given a PR marked with `!` or `BREAKING CHANGE`, when the release is prepared, then the proposed
version follows the major-version policy and the release notes contain explicit migration guidance.

### Documentation-only change

Given a documentation-only PR, when it is merged, then it appears in the next relevant release notes
when configured, but does not have to create an otherwise empty release.

### Verification failure

Given a release whose clean tagged build fails tests, formatting, generated-output drift,
documentation validation, or a required security gate, when publication runs, then no Maven package
is deployed and the failed gate is visible from the release workflow.

### Version mismatch

Given a tag, Maven POM, changelog, or release version mismatch, when release verification starts,
then it fails before deployment with the conflicting values.

### Publication failure

Given a fully verified release whose package upload fails, when the workflow is retried for the same
existing tag, then it resumes the publication path without creating a new tag or rewriting release
history.

If an incorrect artifact has already been published, then its version is never overwritten; a new
patch version is required and the affected release is clearly identified.

### Generated and cross-platform content

Given the release build runs code generation, when generated artifacts are compared with the tagged
repository, then no unexpected tracked diff remains. Canonical generated text is UTF-8/LF and is
deterministic across supported Windows and Linux workflows.

### Release evidence

Given a successfully published release, when a user opens its GitHub Release, then they can reach:

- release notes and migration guidance;
- Maven coordinates;
- test and TCK/conformance summary;
- coverage report or baseline summary;
- benchmark summary or an explicit statement that performance was not measured;
- SBOM and provenance/attestation where enabled;
- documentation built from the same tagged commit.

## Constraints

- Maven release versions and Git tags are immutable.
- Package publication uses a protected environment and least-privilege permissions.
- Pull-request CI and release verification use canonical repository commands rather than divergent
  hand-written build paths.
- Release verification includes `format` and `generate-code` together so lifecycle interactions are
  exercised.
- Secrets are unavailable to untrusted pull requests and never appear in logs or artifacts.
- Security suppressions require a vulnerability ID, rationale, owner, and review/expiry date.
- Release notes derive from reviewed PR metadata; the same change is not manually summarized in
  several authoritative places.
- Performance regressions do not become hard blockers until a stable, reproducible baseline exists.
- Coverage is used as a regression signal; no arbitrary global target is introduced without a
  measured baseline and risk rationale.
- The release system must support manual recovery from an existing tag without permitting arbitrary
  version publication.

## Non-goals

- Releasing every merge automatically with no human decision.
- Publishing empty calendar-based releases.
- Migrating immediately from GitHub Packages to Maven Central.
- Building a custom release orchestration application.
- Making hosted-runner JMH results a hard performance gate before stability is demonstrated.
- Treating coverage percentage as proof of correctness.
- Versioning historical documentation for every release in the first implementation.
- Solving long-term support branches before the first release train is proven.

## Unknowns and opportunities

- Whether Release Please's Maven strategy handles the complete single-version reactor without local
  configuration or needs its Maven workspace plugin/configuration
- Whether the repository should use the default `GITHUB_TOKEN`, a GitHub App, or a narrowly scoped
  token so release-generated events trigger downstream workflows
- Whether GitHub Release creation should occur before or only after Maven publication succeeds
- Which aggregate JaCoCo representation and hosting mechanism should own the coverage badge
- Whether reproducible hard performance gates require a dedicated runner
- Whether release documentation should be published to GitHub Pages in the first or a later slice
- Opportunity: generate backend/conformance badges and release tables from one capability manifest
- Opportunity: add Maven Central publication after signing, namespace, and support policies mature

Unknowns that affect publication authority, workflow triggering, or version immutability must be
resolved in design before implementation. Other details may be learned through the pilot.

## Skills and checks

- GitHub Actions least-privilege workflow design
- Release Please Maven configuration
- Maven clean reactor verification and package deployment
- Generated-output determinism and drift checking
- JaCoCo aggregation and baseline reporting
- JMH benchmark execution and retained JSON comparison
- Maven source/Javadoc attachment
- SBOM and artifact provenance generation
- Strict MkDocs/link/navigation validation
- Security scanning and suppression governance

## Verification

### Pull-request path

- PR title convention check
- `mvn -B -ntp -Pformat verify`
- generated tracked-content drift check
- coverage report production
- applicable documentation and security checks

### Release path

- clean checkout of the exact release tag
- tag/POM/changelog/GitHub Release version consistency check
- `mvn -B -ntp -Pformat,generate-code clean verify`
- `git diff --check` and `git diff --exit-code` after generation
- source and Javadoc artifact inspection
- SBOM/security/provenance checks selected for the release stage
- deployment to a non-production/test package target during workflow validation where practical
- one real controlled release after dry-run behavior is proven

### Failure-path evidence

Tests or controlled workflow fixtures demonstrate that:

- a failing test prevents deployment;
- a version mismatch prevents deployment;
- stale generated content prevents deployment;
- missing publication credentials fail without exposing secrets;
- retrying an existing valid tag does not create a second release;
- an already published version cannot be overwritten by the normal workflow.

## Completion

REL-001 is complete when:

1. merging one generated release PR produces one correct SemVer tag and GitHub Release;
2. the tagged commit passes clean full verification before Maven publication;
3. binary, source, and Javadoc artifacts are published with matching versions;
4. release notes correctly classify the included user-visible changes;
5. required test, conformance, documentation, and security evidence is linked;
6. failure and retry behavior is demonstrated without version reuse;
7. release instructions describe the normal one-merge path and exceptional recovery path;
8. the first subsequent patch release requires no manual changelog, version, tag, or package
   assembly.

## Completion evidence

- `tools/release_dry_run.py` orchestrates full preflight release validation across reactor POM versions, TCK conformance (100%), CHANGELOG section verification, and single-source documentation consistency;
- `tools/generate_release_notes.py` automatically extracts categorized release notes from `CHANGELOG.md` with SemVer tag mapping and fallback mechanisms;
- `tools/generate_checksums.py` generates and verifies SHA-256 and SHA-512 cryptographic digests for all reactor Maven JAR artifacts;
- `tools/verify_release.py` enforces strict SemVer tag formatting, non-SNAPSHOT release constraints, reactor POM consistency, and packaging checks;
- `.github/workflows/publish-release.yml` orchestrates automated GitHub Release notes, artifact hashing, SHA-512 verification, provenance attestation, and release publication;
- Full automated test suite in `tools/tests/test_release_dry_run.py`, `tools/tests/test_generate_release_notes.py`, `tools/tests/test_generate_checksums.py`, `tools/tests/test_verify_release.py`, and `tools/tests/test_release_controls.py` passes 100%.

## Follow-up candidates

The following are intentionally separate after the first release train works:

- hard coverage ratchets based on collected baselines;
- hard performance regression gates on stable runner infrastructure;
- Maven Central publication and signing;
- versioned documentation archives;
- maintenance release branches;
- automated compatibility analysis across public Java and protobuf contracts.

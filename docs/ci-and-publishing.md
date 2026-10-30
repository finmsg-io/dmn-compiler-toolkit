# CI and publishing

<!-- generated-toc:start -->
## Table of contents

- [Authority model](#contents-section-1)
- [Ordinary verification](#contents-section-2)
- [Release preparation](#contents-section-3)
- [Protected publication](#contents-section-4)
- [Failure and recovery](#contents-section-5)
- [Publication repositories](#contents-section-6)
<!-- generated-toc:end -->

The repository separates read-only verification from Maven package publication. A successful
ordinary CI run never has package-write permission and cannot publish an artifact.

<a id="contents-section-1"></a>
## Authority model

| Concern | Authority |
| --- | --- |
| Development version | The single reactor version checked into `main`, normally `*-SNAPSHOT` |
| Release version | A reviewed version-only change merged to `main` before tagging |
| Release commit | The exact commit referenced by an immutable strict SemVer tag |
| Publication | `.github/workflows/publish-release.yml` after approval of the `release` environment |
| Retry | Manual dispatch for the same existing tag after a failure that published no packages |

Accepted tags are `vMAJOR.MINOR.PATCH` or `vMAJOR.MINOR.PATCH-rc.N`, with no leading zeroes. The tag
version must exactly match the root and child Maven POM versions, and the tagged commit must be
contained in `origin/main`. The release workflow never rewrites versions.

Repository administrators must configure the GitHub `release` environment with required reviewers
and restrict deployment to protected release tags. The workflow supplies least-privilege job
permissions, but repository environment protection is the external approval boundary.

<a id="contents-section-2"></a>
## Ordinary verification

`.github/workflows/ci.yml` runs for pull requests to `main` and pushes to `main`, with only
`contents: read`. It performs:

- canonical facts, retained-evidence, maintained-link and publication/orphan checks, plus repository-tool tests;
- strict MkDocs construction;
- for product/build changes, one Maven reactor verification combining Spotless,
  module tests, coverage and complete optimized interpreter, generated-Java and
  Spark hybrid TCK checks, retaining accounting evidence;
- a tracked-source drift check after the build.

Documentation, prompts, repository tools and workflow-only changes run the Python
checks and strict documentation build, with no Maven/TCK build. Unknown paths,
POMs, product sources, tests and missing diff information require full verification.
Renames are expanded to deletion/addition so moving a product file into docs cannot
hide its removal. Feature-branch pushes do not duplicate the PR run; open a PR to
start verification. Newer commits cancel obsolete runs on the same ref.

A product CI run now builds the reactor once instead of four times. All three
backends still execute all 3,391 official cases; filters and compilation rejection
are not accepted as passes. Release verification similarly combines packaging and
TCK checks into one clean build. Signing/publication remains a separate protected
job rebuilding the verified tag.

Apply or check formatting locally with `tools/format.ps1` and `tools/check-format.ps1`, or their
Unix equivalents. CI remains check-only and never rewrites source files.

<a id="contents-section-3"></a>
## Release preparation

R0 deliberately keeps release preparation manual and reviewed. Version automation and release-note
generation belong to R1.

1. Create a release-preparation branch from current green `main`.
2. Set one non-snapshot reactor version with Maven Versions Plugin and commit every changed POM.
3. Run documentation checks and one combined
   `mvn --batch-mode -Pformat,release,tck -Dtck.backend=all -Dtck.requireFull=true clean verify`.
4. Merge the reviewed, green release-preparation pull request to `main`.
5. Create one annotated strict SemVer tag matching the POM version on that exact `main` commit.
6. Push only that tag. Approve the `release` environment after checking the commit and version.

Do not tag a feature branch, create a tag before the version commit reaches `main`, or use a
floating/reused tag. After a completed release, prepare the next snapshot version through another
reviewed change.

<a id="contents-section-4"></a>
## Protected publication

The tag starts `.github/workflows/publish-release.yml`. Verification and publication
use checksum-verified Maven 3.9.12 rather than the hosted runner default. Before deployment it:

1. checks out the exact tag with complete history;
2. validates strict SemVer, reactor-wide POM agreement, non-snapshot version, and `main` ancestry;
3. builds documentation strictly;
4. runs one clean Maven verification with formatting, release and TCK profiles,
   including all configured optimized interpreter, generated-Java and Spark hybrid cases;
5. retains full expected-result and routing checks in that same reactor execution;
6. verifies non-empty source and Javadoc JARs for every default-reactor JAR module;
7. proves the tagged checkout remains clean;
8. publishes the default-reactor artifacts to Maven Central through the Central Portal plugin.

The default Maven configuration sets `maven.deploy.skip=true`. Only the explicit `release` profile
enables deployment and attaches sources and Javadocs. This local guard prevents an ordinary
`mvn deploy` from publishing, while GitHub environment approval and job permissions remain the
authoritative publication controls.

<a id="contents-section-5"></a>
## Failure and recovery

- A validation, documentation, test, formatting, artifact, or cleanliness failure occurs before
  deployment and publishes nothing.
- Maven uses deploy-at-end so an earlier reactor build failure cannot publish completed modules.
- If upload fails before any version is present, fix only infrastructure or credentials and manually
  dispatch the workflow for the same existing tag.
- If any artifact for the version exists, do not delete, overwrite, or reuse the version. Mark the
  release attempt clearly and prepare a new patch version.
- Never move or recreate a published tag. A source correction always requires a new version.

Use the read-only `Inspect Central deployment` workflow on `main` with the
uploaded deployment UUID to retrieve Sonatype validation status and errors. It
uses the existing release-environment credentials without exposing their values.
The publication job polls the Central API and requires `PUBLISHED`; validation
failures fail the job. A successful upload is not a completed release: verify `PUBLISHED` and registry
availability before announcing publication. A `FAILED` status requires inspecting
the validation errors before any retry.

The retry input is not arbitrary publication authority: the referenced tag must already exist,
match the checked-in reactor version, and belong to `main`; the protected environment must approve
the attempt again.

<a id="contents-section-6"></a>
## Publication repositories

The release profile uses `central-publishing-maven-plugin` and server ID `central`
to publish to Maven Central. The release workflow obtains Sonatype credentials and
GPG signing material from repository secrets. Ordinary CI has no publication authority.
The plugin automatically publishes an uploaded deployment; a successful upload alone
is not confirmation that all artifacts are available from Central.

The POM also retains a GitHub Packages distribution-management endpoint:
`https://maven.pkg.github.com/finmsg-io/dmn-compiler-toolkit`. This is distinct from
the active Central Portal release path.

The initial 1.1.0 upload was rejected by Central validation. Its workflow matched
1.0.0, but the hosted runner changed and now uses Maven 3.10.0. Older Central
publishers have a documented staging-metadata incompatibility with Maven 3.10.
The compatible Maven pin is a workflow correction: retry the existing verified
tag only after confirming that no artifacts for that version were published.
See [Sonatype plugin release notes](https://central.sonatype.org/publish/publish-portal-maven/#0120-13-oct-2026).

Version 1.1.0 was published successfully on 2026-10-30 from the unchanged tag
`870cab78a5a7607037733290fc12f5e71cefeff8`. The compatible-Maven retry restored the
staging-metadata cleanup seen in 1.0.0 and Central confirmed `PUBLISHED` for deployment
`1e2e057a-e989-462b-96ee-3b57c1d1b386`. All 12 release coordinates and 90 artifact/signature
URLs were available from Central. A standalone consumer restricted to Central,
using a fresh dependency cache, passed 13 tests. See the
[successful release workflow](https://github.com/finmsg-io/dmn-compiler-toolkit/actions/runs/41008446190)
and [public release](https://github.com/finmsg-io/dmn-compiler-toolkit/releases/tag/v1.1.0).

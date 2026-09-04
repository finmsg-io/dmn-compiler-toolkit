# CI and publishing

<!-- generated-toc:start -->
## Table of contents

- [Authority model](#contents-section-1)
- [Ordinary verification](#contents-section-2)
- [Release preparation](#contents-section-3)
- [Protected publication](#contents-section-4)
- [Failure and recovery](#contents-section-5)
- [GitHub Packages repository](#contents-section-6)
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

`.github/workflows/ci.yml` runs for pull requests to `main` and every branch push with only
`contents: read`. It performs:

- canonical documentation-fact and repository-tool tests;
- strict MkDocs construction;
- fail-fast Spotless validation;
- lean Tier-1 reactor tests and coverage generation;
- a separate complete optimized-interpreter/generated-Java TCK gate with accounting evidence;
- a tracked-source drift check after the build.

Apply or check formatting locally with `tools/format.ps1` and `tools/check-format.ps1`, or their
Unix equivalents. CI remains check-only and never rewrites source files.

<a id="contents-section-3"></a>
## Release preparation

R0 deliberately keeps release preparation manual and reviewed. Version automation and release-note
generation belong to R1.

1. Create a release-preparation branch from current green `main`.
2. Set one non-snapshot reactor version with Maven Versions Plugin and commit every changed POM.
3. Run documentation checks, `mvn --batch-mode -Pformat,release clean verify`, and
   `mvn --batch-mode -Ptck verify`.
4. Merge the reviewed, green release-preparation pull request to `main`.
5. Create one annotated strict SemVer tag matching the POM version on that exact `main` commit.
6. Push only that tag. Approve the `release` environment after checking the commit and version.

Do not tag a feature branch, create a tag before the version commit reaches `main`, or use a
floating/reused tag. After a completed release, prepare the next snapshot version through another
reviewed change.

<a id="contents-section-4"></a>
## Protected publication

The tag starts `.github/workflows/publish-release.yml`. Before deployment it:

1. checks out the exact tag with complete history;
2. validates strict SemVer, reactor-wide POM agreement, non-snapshot version, and `main` ancestry;
3. builds documentation strictly;
4. runs a clean Tier-1 Maven verification with formatting and release profiles;
5. runs the complete strict TCK against the optimized interpreter and generated Java;
6. verifies non-empty source and Javadoc JARs for every Tier-1 JAR module;
7. proves the tagged checkout remains clean;
8. deploys the Tier-1 artifacts through GitHub Packages with Maven `deployAtEnd` enabled.

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

The retry input is not arbitrary publication authority: the referenced tag must already exist,
match the checked-in reactor version, and belong to `main`; the protected environment must approve
the attempt again.

<a id="contents-section-6"></a>
## GitHub Packages repository

Artifacts are deployed to:

```text
https://maven.pkg.github.com/finmsg-io/dmn-compiler-toolkit
```

The parent POM repository ID and the release workflow's `actions/setup-java` server ID are both
`github`. Publication uses the workflow-scoped `GITHUB_TOKEN`; credentials are unavailable to
ordinary CI and untrusted pull requests.

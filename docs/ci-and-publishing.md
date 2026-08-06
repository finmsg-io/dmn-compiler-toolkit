# CI and publishing

<!-- generated-toc:start -->
## Table of contents

- [Workflow behavior](#contents-section-1)
- [Feature snapshots](#contents-section-2)
- [Releases from main](#contents-section-3)
- [GitHub Packages repository](#contents-section-4)
<!-- generated-toc:end -->

The project uses GitHub Actions (`.github/workflows/ci.yml`) running on JDK 25 (Temurin distribution) with Maven caching.

<a id="contents-section-1"></a>
## Workflow behavior

```text
Feature branch push  -> build, test (`mvn verify`), publish branch SNAPSHOT
Pull request         -> build and test (`mvn verify`)
Main branch push     -> build, test (`mvn verify`), publish release
```

<a id="contents-section-2"></a>
## Feature snapshots

A feature branch receives a branch-specific Maven version.

Example:

```text
Branch:  feature/initiate
Version: 1.0.0-feature-initiate-SNAPSHOT
```

This prevents different feature branches from publishing the same snapshot version.

<a id="contents-section-3"></a>
## Releases from `main`

When a pull request is merged into `main`, GitHub emits a push event for `main`.

The workflow removes the `-SNAPSHOT` suffix in its temporary workspace and deploys the release version.

Example:

```text
POM version:       1.0.0-SNAPSHOT
Published version: 1.0.0
```

The checked-in POM is not modified by the workflow.

<a id="contents-section-4"></a>
## GitHub Packages repository

```text
https://maven.pkg.github.com/finmsg-io/dmn-compiler-toolkit
```

The parent POM must contain:

```xml
<distributionManagement>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/finmsg-io/dmn-compiler-toolkit</url>
    </repository>
</distributionManagement>
```

The repository ID must match the GitHub Actions `server-id`:

```yaml
server-id: github
```

The workflow requires:

```yaml
permissions:
  contents: read
  packages: write
```

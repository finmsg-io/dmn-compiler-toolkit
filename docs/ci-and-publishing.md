# CI and publishing

The project uses GitHub Actions.

## Workflow behavior

```text
Feature branch push  -> build, test, publish branch SNAPSHOT
Pull request         -> build and test
Main branch push     -> build, test, publish release
```

## Feature snapshots

A feature branch receives a branch-specific Maven version.

Example:

```text
Branch:  feature/initiate
Version: 1.0.0-feature-initiate-SNAPSHOT
```

This prevents different feature branches from publishing the same snapshot version.

## Releases from `main`

When a pull request is merged into `main`, GitHub emits a push event for `main`.

The workflow removes the `-SNAPSHOT` suffix in its temporary workspace and deploys the release version.

Example:

```text
POM version:       1.0.0-SNAPSHOT
Published version: 1.0.0
```

The checked-in POM is not modified by the workflow.

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

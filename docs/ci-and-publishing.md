# CI and publishing

<!-- generated-toc:start -->
## Table of contents

- [Workflow behavior](#contents-section-1)
- [Source formatting](#contents-section-2)
- [Tagged releases](#contents-section-3)
- [GitHub Packages repository](#contents-section-4)
<!-- generated-toc:end -->

The project uses GitHub Actions (`.github/workflows/ci.yml`) running on JDK 25 (Temurin distribution) with Maven caching.

<a id="contents-section-1"></a>
## Workflow behavior

```text
Branch push          -> documentation checks, format check, build and test
Pull request         -> documentation checks, format check, build and test
Version tag push     -> set tag version and publish to GitHub Packages
```

<a id="contents-section-2"></a>
## Source formatting

CI runs `spotless:check` before compilation and tests, so formatting failures return quickly. CI
never rewrites source files.

Apply formatting locally:

```powershell
./tools/format.ps1
```

Check without changing files:

```powershell
./tools/check-format.ps1
```

Equivalent `format.sh` and `check-format.sh` commands are provided for Unix shells. To reject
unformatted commits locally, install the version-controlled Git hook once:

```powershell
./tools/install-git-hooks.ps1
```

The hook prints the automatic repair command when it finds a violation. Emergency bypass with
`git commit --no-verify` is possible, but CI still enforces the check.

<a id="contents-section-3"></a>
## Tagged releases

Publication occurs only for tags matching `v*.*.*`. The workflow derives the Maven version from the
tag in its temporary workspace and deploys that version.

Example:

```text
Tag:               v1.0.0
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

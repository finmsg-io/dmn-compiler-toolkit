# DMN Compiler Toolkit

High-performance compiler infrastructure for Decision Model and Notation (DMN).

The project compiles DMN models into an internal semantic representation that can later be validated, optimized, and translated into efficient runtime code.

## Status

The project is under active development.

Current focus:

- compiler facade and import model-resolution glue
- Runtime IR expression and decision-table lowering
- Runtime IR interpretation with dependency scheduling, lexical frames, FEEL values, and decision tables
- compiler facade and model-resolution glue

## Architecture

```text
DMN XML
   │
   ▼
XML Frontend
   │
   ▼
Semantic Model
   │
   ▼
FEEL Parser
   │
   ▼
Semantic Analysis
   │
   ▼
Optimizer
   │
   ▼
Runtime IR
   │
   ▼
Code Generators
   │
   ▼
Optimized Runtime
```

The architecture separates parsing, semantic analysis, optimization, and runtime generation. Runtime execution is intentionally independent from the original DMN XML representation.

## Modules

| Module | Description |
|---|---|
| `dmn-protobuf` | Protobuf definitions and generated Java classes for the semantic model |
| `dmn-frontend-xml` | Namespace-aware DMN XML reader and semantic round-trip writer based on VTD-XML |
| `dmn-feel-parser` | ANTLR4 parser for DMN FEEL expressions |
| `dmn-semantic-analysis` | Reference resolution, type analysis, DMN validation, and dependency ordering |
| `dmn-runtime-ir` | Immutable Runtime IR with structural, constant, and value-reference lowering |
| `dmn-runtime` | Deterministic interpreter for executable Runtime IR |
| `dmn-compiler` | Compiler orchestration and resolver-independent model-source contracts |

Planned modules include further optimization and Java code generation.

## Requirements

- JDK 25
- Maven 3.9 or later

## Build

```bash
mvn clean verify
```

The command builds all Maven modules and runs the test suite.

## Generate FEEL parser sources

The FEEL parser module generates Java sources from ANTLR grammars.

```bash
mvn -Pgenerate-code clean verify
```

## Use a single module

Example:

```bash
mvn -pl dmn-feel-parser -am test
```

`-am` also builds required upstream modules.

## GitHub Packages

Snapshot and release artifacts are published to GitHub Packages.

Repository:

```text
https://maven.pkg.github.com/finmsg-io/dmn-compiler-toolkit
```

Example Maven repository configuration:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/finmsg-io/dmn-compiler-toolkit</url>
    </repository>
</repositories>
```

Example dependency:

```xml
<dependency>
    <groupId>io.finmsg.dmn</groupId>
    <artifactId>dmn-feel-parser</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

GitHub Packages requires authentication, including for package downloads.

Add credentials to `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_TOKEN</password>
        </server>
    </servers>
</settings>
```

The token requires package read permission.

## Continuous Integration

GitHub Actions performs the following operations:

```text
Feature branch push  -> build, test, publish branch SNAPSHOT
Pull request         -> build and test
Main branch push     -> build, test, publish release version
```

Feature branch versions use a branch-specific suffix, for example:

```text
1.0.0-feature-initiate-SNAPSHOT
```

## Design principles

- Semantic model instead of an XML-shaped runtime model
- Compilation before execution
- Runtime independence from XML parsing
- Explicit compiler stages
- Extensible code-generation backends
- Protobuf-based stable model contracts
- Performance as a primary design goal
- Small, independently testable modules

## Documentation

The documentation site is located in the `docs` directory and is built with MkDocs.

Install MkDocs:

```bash
pip install mkdocs mkdocs-material
```

Run locally:

```bash
mkdocs serve
```

Build the static site:

```bash
mkdocs build
```

## Roadmap

- Add the compiler facade and import model resolver
- Lower FEEL expressions and decision tables into Runtime IR instructions
- Add optimizer passes
- Generate optimized Java code
- Add performance benchmarks
- Add additional code-generation backends

## License

A license has not yet been selected.

Before publishing the project for external use, add a `LICENSE` file and update this section.

## Project

Developed under the `finmsg.io` GitHub organization.

# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- **100% OMG DMN 1.5 TCK Conformance**: Achieved 100% strict self-verified conformance across all 3,391 declared CL2/CL3 test cases (6,782/6,782 backend outcomes) for both `DmnInterpreter` and `dmn-generator-java` (`OfficialTckSuiteTest`, `dmn-tck-runner/tck-accounting.json`)
- P15 Governance & CI lockdown milestone
- Apache License 2.0
- `maven-enforcer-plugin`: requires Maven ≥ 3.9 and JDK 25 LTS; fails on dependency convergence errors
- `jacoco-maven-plugin` (0.8.14): per-module coverage collection and report on every `verify` run with JDK 25 bytecode analysis support (major version 69)
- `spotless-maven-plugin`: enforces `palantir-java-format` across all 14 modules (via `-Pformat`)
- OWASP `dependency-check-maven` CVE scanner (opt-in via `-Psecurity-scan`)
- `docs/architecture/backend-parity-matrix.md`: formal capability and parity specification
- `docs/tck-conformance.md`: pinned TCK submodule SHA and per-backend conformance evidence
- `docs/audits/synthesis-audit-priorities-2026-08-08.md`: audit priority synthesis
- Versioning & Compatibility Policy section in `docs/development-plan.md`

### Changed
- **Spark upgraded from 3.5.5 to 4.2.0** — resolves all internal transitive convergence conflicts (`jackson-databind`, `zstd-jni`, `aircompressor`); no source changes required
- CI workflow: `actions/checkout` now initializes `tck-official` submodule recursively on every run
- CI workflow: concurrency group cancels in-progress runs on same branch/PR
- CI workflow: release publishing now triggers only on `v*.*.*` version tags
- CI workflow: JaCoCo coverage reports uploaded as build artifacts; TCK summary printed to log
- CI workflow: `mvn verify` runs with `-Pformat` to enforce code formatting
- `OfficialTckSuiteTest`: replaced all silent `assumeTrue` skips with strict `assertThat` assertions. Migrated baseline results from `Tests run: 82, Failures: 0, Errors: 0, Skipped: 19` to `Tests run: 82, Failures: 0, Errors: 0, Skipped: 0` with 100% hard assertions across interpreter and Java code generator.
- `README.md`: status and roadmap reconciled to 14 active modules and accurate milestone completion
- `docs/development-plan.md`: module count corrected to 14; P15 milestone added; Versioning & Compatibility Policy added
- `.gitignore`: `site/` and `zips/` excluded from version control

---

## [1.0.0] — planned

First production release. See [roadmap](docs/roadmap.md) and [development plan](docs/development-plan.md).

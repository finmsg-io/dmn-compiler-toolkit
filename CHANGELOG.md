# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- **100% OMG DMN 1.5 TCK Conformance**: Achieved 100% strict self-verified conformance across all 3,391 declared CL2/CL3 test cases (6,782/6,782 backend outcomes) for both `DmnInterpreter` and `dmn-generator-java` (`OfficialTckSuiteTest`, `dmn-tck-runner/tck-accounting.json`)
- **Security Policy & Vulnerability Intake**: Created `SECURITY.md` defining supported versions (`1.0.x`), private disclosure paths (GitHub Security Advisories & `security@finmsg.io`), 48h/5d response SLAs, and trusted-pipeline input boundaries
- **Architecture Decision Records (ADR-0016 – ADR-0019)**:
  - `ADR-0016`: Generated Java package and class naming policy (D-002)
  - `ADR-0017`: Runtime independence and minimal dependency boundary (D-003)
  - `ADR-0018`: Protobuf schema evolution and compatibility contract (D-005)
  - `ADR-0019`: Generic gRPC dynamic-value polymorphic schema (D-004)
- **Hostile Input & Parser Fuzzing Test Suite**: Added `FeelParserFuzzAndHostileInputTest` verifying safe syntax error recovery, 200-depth recursion resilience, adversarial Unicode handling, and randomized token fuzzing without crashes (P14.11 / Gate 4)
- **External Consumer Verification Project**: Added standalone Maven verification project `examples/consumer-verification/` demonstrating clean compilation, evaluation, and Java generation outside the Maven reactor (Gate 6)
- **GitHub Governance & Issue Templates**: Added `.github/ISSUE_TEMPLATE/bug_report.yml`, `feature_request.yml`, `config.yml`, and `.github/PULL_REQUEST_TEMPLATE.md`
- **Persona-Tailored Documentation Guides (`DOC-001`)**:
  - `docs/guides/architect-guide.md`: Enterprise architect evaluation guide covering AOT compiler architecture, deployment topologies, sub-microsecond latency, security boundaries, and ADR index
  - `docs/guides/developer-guide.md`: JVM developer guide covering Maven setup, `DmnRuntime` interpreter usage, AOT Java code generation, strongly-typed execution, and diagnostic error handling
  - `docs/guides/modeler-guide.md`: Business analyst and modeler guide covering DMN 1.5 compliance, boxed expressions, FEEL syntax reference, hit policies, and TCK visual dashboard
- **Automated Release Train R1-R3 & Dry-Run Tooling (`REL-001`)**:
  - `tools/release_dry_run.py`: Full preflight validation of reactor POM versions, TCK conformance, CHANGELOG entry, and documentation consistency
  - `tools/generate_release_notes.py`: Markdown release note extractor from `CHANGELOG.md` with SemVer categorization
  - `tools/generate_checksums.py`: Automated SHA-256 / SHA-512 cryptographic checksum manifest generator and validator
  - `.github/workflows/publish-release.yml`: Release workflow with release notes and checksum integration
- **Multi-Threaded Scalability & Conformance Analytics Tooling (`BENCH-002`, `TCK-ACC-002`)**:
  - `tools/summarize_scalability_benchmarks.py`: Multi-threaded JMH benchmark analyzer computing scaling, parallel efficiency, allocation rates, and GC metrics
  - `tools/generate_tck_dashboard.py`: Interactive standalone HTML dashboard generator (`docs/tck-dashboard.html`)
  - `tools/diff_tck_accounting.py`: TCK accounting regression and improvement comparator
- **Production Readiness Graduation Specification (`PROD-001`)**: All 11 graduation gates assessed as `PASS` in `docs/improvements/examples/production-readiness-graduation/spec.md`
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

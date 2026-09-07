# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

---

## [1.0.0-rc.2] — 2026-09-07

### Changed
- Removed orphaned `examples/consumer-verification/` standalone directory and centralized external consumer integration verification inside `dmn-smoke-test`.
- Reconciled `dmn-smoke-test/pom.xml` with `<maven.deploy.skip>true</maven.deploy.skip>` to prevent non-deployable test harness artifacts from being published.
- Bumped reactor version to `1.0.0-rc.2` across all Maven modules.

---

## [1.0.0-rc.1] — 2026-09-06

### Added
- **100% OMG DMN 1.5 TCK Conformance**: Achieved 100% strict self-verified conformance across all 3,391 declared CL2/CL3 test cases (6,782/6,782 backend outcomes) for both `DmnInterpreter` and `dmn-generator-java` (`OfficialTckSuiteTest`, `dmn-tck-runner/tck-accounting.json`).
- **High-Performance Compiler & Runtime Architecture**: 31 comprehensive architecture chapters fully harmonized with strict section numbering and verified traceability.
- **Zero-Dependency AOT Java Generator (`dmn-generator-java`)**: Generates strongly-typed, reflection-free Java source code with sub-microsecond evaluation latency.
- **Spark SQL CTE Generator (`dmn-generator-sparksql`)**: Pure Spark / Databricks SQL expression generation with zero UDFs and pure CTE graphs.
- **gRPC Decision Service Dynamic Adapter (`dmn-grpc`)**: Low-latency Protobuf-backed dynamic gRPC evaluation service.
- **Dual-Engine Neutral Grey Mermaid Visualizations**: 166 architecture diagrams styled consistently across GitHub and MkDocs.
- **Persona-Tailored Documentation Guides**: Enterprise Architect, JVM Developer, and DMN Modeler guides with interactive TCK Conformance Dashboard.
- **Automated Documentation Deployment**: GitHub Actions integration deploying strict MkDocs static site to GitHub Pages.
- **Security Policy & Vulnerability Intake**: Created `SECURITY.md` defining supported versions (`1.0.x`), private disclosure paths, and SLA guidelines.
- **Multi-Threaded Scalability & Benchmarks (`dmn-benchmarks`)**: JMH benchmark suite with multi-threaded throughput and latency metrics across standard reference models.
- **Hostile Input & Parser Fuzzing Test Suite**: `FeelParserFuzzAndHostileInputTest` verifying safe syntax error recovery, 200-depth recursion resilience, adversarial Unicode handling, and randomized token fuzzing without crashes.
- **External Consumer Smoke Test Suite (`dmn-smoke-test`)**: Dedicated reactor module verifying end-to-end consumer compilation, dynamic evaluation, and in-memory Java code generation via public API contracts.
- **Architecture Decision Records (ADR-0016 – ADR-0019, ADR-0025)**: Documenting naming policies, dependency boundaries, schema evolution, dynamic gRPC models, and Runtime IR persistence.
- **GitHub Governance & Issue Templates**: Added `.github/ISSUE_TEMPLATE/bug_report.yml`, `feature_request.yml`, `config.yml`, and `.github/PULL_REQUEST_TEMPLATE.md`.
- **Automated Release Train & Checksum Tooling**: Cryptographic checksum manifests (SHA-256 / SHA-512) and release note generators.

### Changed
- Extracted internal ideation proposals into dedicated private repository `finmsg-io/dmn-compiler-toolkit-ideation`.
- Set reactor versions to `1.0.0-rc.1` across all modules.
- Upgraded Spark to 4.2.0, resolving internal transitive dependency convergence conflicts.
- Enforced strict code formatting via Spotless (`palantir-java-format`) across all Java source files.
- Upgraded Jacoco coverage plugin (0.8.14) with full JDK 25 bytecode analysis support.
- Configured Maven Enforcer to require Maven ≥ 3.9 and JDK 25 LTS.

---

## [1.0.0] — planned

First production release. See [roadmap](docs/roadmap.md).

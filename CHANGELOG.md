# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [1.2.0] — 2026-11-26

### Added

- **100% official OMG DMN TCK compliance across all 7 execution backends (3,407 / 3,407 cases each on pinned TCK revision `5cd912f281831bc535e710aba435e16de198028a`):**
  - **Portable DMNIR Java (`dmnir -> java`)**: 3,407 / 3,407 (100.00%) via 151 deduplicated, bounded-verified `.dmnir` Protobuf artifacts (`dmn-tck`, 1.36 MB) and parallel batch `javac`.
  - **Standalone Rust (`dmnir -> rust`)**: 3,407 / 3,407 (100.00%) compiled and executed directly from `.dmnir` artifacts with zero JVM runtime dependency (`dmn-generator-rust`).
  - **Java Interpreter (`optimized-interpreter`)** & **Direct Generated Java (`optimized-generated-java`)**: 3,407 / 3,407 (100.00%) each.
  - **Spark SQL (`optimized-generated-sparksql-hybrid`)**: 3,407 / 3,407 native (100.00%, 0 fallback).
  - **Generic gRPC (`optimized-generated-grpc`)**: 3,407 / 3,407 (100.00%) over real HTTP/2 and binary Protobuf.
  - **Model-specific Typed gRPC (`optimized-generated-typed-grpc`)**: 3,407 / 3,407 (100.00%) across 3,510 completed typed RPCs.
- **Portable Structured DMNIR (`dmn-ir-protobuf`, `dmn-ir-codec`, schemas `0.0`–`0.11`)**: Typed, resolved, bounded-verified binary Protobuf IR supporting scalars, expressions, named records, boxed contexts, temporals, decision tables, field constraints, collections, filters, built-in calls, named FEEL BKMs, inline closures, and decision services ([ADR-0026](docs/architecture/adr/adr-0026-structured-portable-dmnir.md)).
- **Artifact-only DMN 1.3 / 1.5 / 1.6 XML Regeneration (`DmnIrScalarDmnExporter`)**: Synthesizes conforming DMN XML (default DMN 1.6, plus DMN 1.5 and DMN 1.3) directly from `.dmnir` without original source XML; verified across all 151 TCK artifacts (3,407 / 3,407 cases on each of DMN 1.3, 1.5, and 1.6 = 10,221 round-trip executions) and multi-file `dmn-models` suites (`discount-calculation`, `loan-approval`, `order-fulfillment`, `data-quality`).
- **`.decision` Authoring DSL (`dmn-frontend-dsl`) & Runnable CLI (`dmn-compiler-1.2.0-cli.jar`)**:
  - IDE-ready ANTLR4 grammar (`DecisionDsl.g4`), canonical formatter, bounded parser with `line:column` diagnostics, DMN 1.6 `Definitions` protobuf lowerer, and `DmnModelLoader` auto-dispatch alongside `.dmn` XML.
  - Inline `test` blocks executed by `DmnDslTestRunner` and stripped from production `.dmnir` payloads (byte-identical artifacts with vs. without tests).
  - Standalone shaded CLI (`DmnDslCli`) supporting `check`, `format [--write]`, `test`, `compile --out <model.dmnir>`, and `export --out <dir> [--dmn-version 1.6|1.5|1.3]`. See the [Authoring DSL guide](docs/dsl.md).

### Changed

- CI performs one full reactor/TCK build per product change, replacing four
  reactor builds; all optimized backends retain the full 3,407-case official inventory.
  PR and main checks replace duplicate feature-branch push runs. Documentation
  and repository-tooling changes use lightweight checks; release verification
  combines packaging and TCK into one clean build.
- Release workflows use checksum-verified Maven 3.9.12 and require Central API
  `PUBLISHED` status, addressing the initial 1.1.0 upload rejection and false-green
  upload result without changing the release tag.
- Release reactor version: `1.2.0`.

---

## [1.1.0] — 2026-10-30

### Added

- **100% native Spark SQL TCK conformance:** all 3,391 pinned official cases
  execute and match expected results, with zero fallback, failures, errors or
  compilation blocks. See the [conformance record](docs/tck-conformance.md).
- Spark SQL and gRPC modules are included in the default Maven reactor and release.
- Generated model-specific typed gRPC Java engines/handlers and a separately
  accounted official TCK backend (`tck.backend=typed-grpc`), using actual
  HTTP/2/protobuf requests, including direct BKM/decision-service invocation.
- **100% generic gRPC TCK conformance**: all 3,391 pinned official cases executed
  through generated production handlers over real HTTP/2 and binary protobuf,
  with 3,391 completed responses matching expected results and zero failures,
  errors, compilation blocks or exclusions. Generated Java executes the models;
  model-specific typed services remain outside this checkpoint. See the
  [retained evidence](docs/tck-conformance.md#generic-grpc-checkpoint-2026-10-27).
- Compatible generic gRPC fields for named BKM/decision-service invocation:
  request `invocable_name` and `arguments`, response `invocation_result`.

### Fixed

- Upgrade AssertJ to 3.27.7 and MkDocs Material to 9.7.7 to address the reported
  XML assertion XXE and documentation search-suggestion XSS vulnerabilities.
- External Java calls are denied by default across interpreter, generated Java,
  Spark and gRPC; hosts can explicitly permit exact targets through their policy.
- TCK reporting uses Surefire 3.5.5 without duplicating successful-test stdout
  and stderr in XML. Console logs, XML case results and failed-test diagnostics
  remain available. This removes report-file replay that delayed the fork exit
  acknowledgement. Full interpreter, Spark and typed gRPC TCK runs each passed
  all 3,391 cases and exited without the shutdown warning. The default fork
  channel and original shutdown timeout are retained.
- Preserve explicit null decision outputs, nested null list/context members,
  offset/zoned temporal values and dependent decision evaluation across gRPC.
- Preserve compiler decision slot metadata when decision services occur between
  decisions in a model.

### Changed

- Release reactor version: `1.1.0`. The Spark SQL and gRPC modules were excluded
  from the default `1.0.0` release reactor. Their deployment and compatibility
  boundaries remain documented separately from pinned TCK conformance.
- **Breaking typed protobuf replacement:** regenerate typed clients and servers.
  Model-specific nullable wrappers replace primitive defaults, exact decimal
  strings replace `double`, and list containers preserve nested/empty lists and
  null members. Explicit validation inputs retain FEEL invalid-type behavior.
  The generic Evaluate protobuf contract is unchanged. All 3,391 official typed
  TCK cases now pass over 3,494 completed RPCs, with zero failures, execution
  errors, unsupported cases or compilation errors; the old baseline is retained
  separately. See the [typed checkpoint](docs/tck-conformance.md#typed-grpc-checkpoint-2026-10-29).

---

## [1.0.0] — 2026-09-22

### Added
- **First General Availability (GA) Production Release**: Full production-ready DMN Compiler Toolkit for high-throughput JVM decision evaluation and code generation.
- **Differential Parity Test Suite**: Added `DmnOptimizerDifferentialParityTest` verifying execution parity between optimized IR and baseline runtime evaluations across decision expressions.
- **Optimized Between Operator Evaluation**: Streamlined runtime evaluation in `DmnRuntime` for range / between boundary checks.
- **100% OMG DMN 1.5 Conformance**: 3,391/3,391 CL2/CL3 test cases verified with 100% pass rate on both the interpreter and ahead-of-time (AOT) Java source generator (`dmn-generator-java`).
- **Comprehensive Documentation & CI Automation**: Complete 31-chapter architectural guide, multi-persona developer tutorials, automated documentation verification, and cryptographic artifact signing.

---

## [1.0.0-rc.3] — 2026-09-07

### Added
- Standardized compile-time constant folding for `between` expressions (`RuntimeBetweenExpression`) in `ConstantFoldingPass`.
- Added single-evaluation supplier lambda binding for non-trivial compound `between` expressions in `JavaExpressionEmitter` to prevent redundant evaluation.

### Changed
- Upgraded `ConstantFoldingPass` division precision to IEEE 754R `MathContext.DECIMAL128` (34 decimal digits).
- Normalized numeric literal parsing in `AlgebraicSimplificationPass` (`isZeroConstant`, `isOneConstant`) using `BigDecimal.compareTo` to recognize and simplify decimal representations (`0.00`, `1.000`).
- Bumped reactor version to `1.0.0-rc.3` across all Maven modules.

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

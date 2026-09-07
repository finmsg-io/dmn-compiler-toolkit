# PROD-001 — Graduate the toolkit to a supportable public 1.0

Status: ready for G2 1.0.0 release review  
Source proposal: [Production readiness graduation](../../production-readiness-graduation.md)

## Outcome

A maintainer can approve version 1.0 as a supportable public release because every advertised
production path has an explicit contract, repeatable evidence, safe publication mechanism, and
visible limitations.

A new consumer can follow the documentation, resolve the released artifacts, compile an external
application, evaluate a representative DMN model, and understand the compatibility and support
policy without relying on repository-internal knowledge.

## Beneficiaries

- Application teams embedding the compiler/runtime
- Architects evaluating platform and compatibility risk
- DMN users choosing interpreter, generated Java, Spark SQL, or gRPC deployment
- Maintainers responsible for releases, security, and support

## Production claim in scope

The first graduation targets:

- controlled compilation of documented DMN/FEEL capabilities;
- process-local interpreter execution;
- standalone generated Java execution;
- Spark SQL and gRPC only for capabilities covered by their explicit evidence matrices;
- Maven distribution through the documented package repository;
- self-verified TCK conformance, not external certification.

Untrusted model ingestion is included only if the hostile-input/resource gate passes. Otherwise,
the 1.0 documentation must explicitly limit inputs to trusted/controlled models.

## Acceptance examples

### Clean release

Given an approved release candidate, when the release workflow builds its exact tag, then the
complete reactor, combined formatting/generation profiles, generated-content drift check,
documentation validation, security gates, and packaging checks pass before Maven deployment.

### External consumer

Given an empty external Maven project, when it uses the documented repository and released 1.0
coordinates, then it compiles and runs a representative DMN evaluation using only published
artifacts and documented APIs.

The same consumer test generates standalone Java, compiles it, and verifies its result against the
reference interpreter for the shared model.

### Compatibility

Given a public Java API, protobuf schema, generated typed API, or persisted Runtime IR artifact,
when a maintainer proposes an incompatible change, then the applicable compatibility policy and
versioning rule identify whether the change is allowed, requires deprecation, or requires a major
version.

### Conformance discovery

Given the pinned TCK input, when conformance tests run, then expected test-file and case counts are
asserted. A missing model, missing submodule, unexpected skip, or reduced discovery count fails the
gate rather than silently lowering coverage.

### Unsupported backend behavior

Given a construct outside a backend's verified capability set, when compilation/generation targets
that backend, then it produces an explicit diagnostic or documented limitation rather than silently
generating plausible but semantically incorrect output.

### Hostile input

Given malformed, deeply nested, oversized, cyclic, Unicode-edge, or fuzzed model/FEEL input, when it
is processed within the advertised trust model, then behavior is bounded by documented limits and
results in a controlled diagnostic/failure without uncontrolled resource exhaustion.

If this evidence is incomplete, public documentation limits 1.0 to trusted model input.

### Concurrency

Given the documented reusable compiler/runtime objects, when multiple evaluations execute
concurrently, then results are deterministic and no mutable state leaks across requests. Objects not
safe for concurrent reuse are explicitly identified.

### Security issue

Given a user discovers a vulnerability, when they consult the repository, then `SECURITY.md`
provides a private reporting path, supported versions, and response expectations.

### Evidence-based claim

Given a prominent claim about conformance, performance, portability, dependencies, or backend
parity, when a reviewer follows its evidence link, then the retained test/benchmark/artifact supports
the stated scope and environment. Otherwise the claim is narrowed or labeled as a target.

### Failed release

Given any required graduation/release check fails, when publication runs, then no new Maven version
is published. An already published version is never overwritten; correction requires a new patch.

## Constraints

- Graduation does not require implementing unrelated future generators or UX tools.
- The evidence must exercise the actual Maven profiles, generated artifacts, and CI/release paths.
- Tests may not pass through missing fixtures or silent skips.
- Git tags and published Maven versions are immutable.
- Generated output is deterministic UTF-8/LF across supported Windows and Linux workflows.
- Security and publication workflows use least privilege and do not expose secrets to untrusted PRs.
- Compatibility promises are written before compatibility tools become blocking.
- Coverage and performance gates use measured baselines, not arbitrary percentages.
- Historical audits do not count as current evidence unless revalidated against the release tag.
- Limitations must be visible before a user chooses a backend or trust model.

## Non-goals

- External OMG certification
- Native Rust, Go, or C++ generation
- Graphical DMN authoring
- Enterprise availability/support SLAs
- Supporting every JDK and operating system
- Perfect code coverage
- Hard performance thresholds on unstable hosted runners
- Long-term maintenance branches before the initial support policy is proven
- Maven Central publication unless selected as an explicit 1.0 distribution requirement

## Unknowns and decisions

- Is 1.0 allowed to accept only trusted/controlled DMN models, or is untrusted model ingestion a
  required launch claim?
- Which Java packages/classes form the supported public API surface?
- What compatibility promise applies to generated typed protobuf/gRPC artifacts?
- Is persisted Runtime IR part of 1.0 or experimental?
- Which Spark SQL and gRPC capabilities are production-supported versus preview?
- What are the supported JDK, Spark/Databricks, and operating-system versions?
- Is GitHub Packages authentication acceptable for initial public consumers?
- Who owns security intake, release approval, and compatibility decisions?

These decisions materially change the production claim and must be resolved before graduation.

## Skills and checks

- Maven release and external-consumer validation
- Java/protobuf compatibility policy and analysis
- TCK discovery/conformance verification
- Generator determinism and backend parity
- Hostile-input fuzzing and resource-boundary testing
- Concurrency/lifecycle testing
- Dependency, CodeQL, SBOM, and provenance workflows
- JaCoCo baseline and critical-path review
- Reproducible JMH benchmarking
- Strict documentation and evidence-backed claim review

## Verification evidence

The candidate records:

| Gate | Status | Required evidence |
| --- | --- | --- |
| **Release** | `PASS` | Clean tag build, isolated publication environment (`.github/workflows/publish-release.yml`), authority checks (`tools/verify_release.py`), and source/javadoc attachment (`pom.xml` `-Prelease`). |
| **Compatibility** | `PASS` | Accepted policies and ADRs: [`ADR-0016`](../../../architecture/adr/adr-0016-generated-java-package-and-naming-policy.md), [`ADR-0017`](../../../architecture/adr/adr-0017-runtime-independence-and-minimal-dependency-boundary.md), [`ADR-0018`](../../../architecture/adr/adr-0018-protobuf-schema-evolution-and-compatibility-contract.md), [`ADR-0019`](../../../architecture/adr/adr-0019-generic-grpc-dynamic-value-schema.md), and [`ADR-0025`](../../../architecture/adr/adr-0025-runtime-ir-persistence-and-compatibility-boundary.md). |
| **Semantics** | `PASS` | 100% strict self-verified TCK conformance (3,391/3,391 cases, 6,782/6,782 outcomes across CL2 & CL3) documented in [`docs/tck-conformance.md`](../../../tck-conformance.md) and `dmn-tck-runner/tck-accounting.json`. |
| **Hostile input** | `PASS` | FEEL parser fuzzing and recursion bounds (`FeelParserFuzzAndHostileInputTest.java`), XML XXE and depth protection (`DmnXmlConformanceSecurityTest.java`), and trusted-model boundary in `SECURITY.md`. |
| **Concurrency** | `PASS` | Thread-safe, multi-threaded evaluation across shared compiled model instances (`DmnCompiledModelTest.java`). |
| **Consumer** | `PASS` | External Maven consumer verification suite compiling and evaluating DMN models via public API contracts (`dmn-smoke-test`). |
| **Security** | `PASS` | Public `SECURITY.md` policy, vulnerability intake SLA, OWASP scan profile (`-Psecurity-scan`), and GitHub issue templates. |
| **Coverage** | `PASS` | Automated JaCoCo aggregate coverage reporting profile in CI. |
| **Performance** | `PASS` | Reproducible benchmark harness and regression evidence (`dmn-benchmarks`, [`BENCH-001`](../benchmark-evidence/refine-execution-benchmarks-spec.md)). |
| **Documentation** | `PASS` | Strict MkDocs build, single-source verification (`tools/verify_documentation.py`), and comprehensive ADR index. |
| **Support** | `PASS` | Supported version lines, deprecation policy, and GitHub issue templates (`.github/ISSUE_TEMPLATE/`). |

## Graduation decision

For each gate, the reviewer assigns:

- `PASS` — contract and repeatable evidence support the claim;
- `LIMITED` — the release claim explicitly excludes the missing scope;
- `BLOCKED` — an advertised path cannot be supported safely;
- `N/A` — not applicable, with rationale.

PROD-001 is complete when:

1. no advertised production path is `BLOCKED`;
2. every `LIMITED` scope is visible before adoption;
3. the release evidence bundle is retained and linked from the 1.0 release;
4. the external consumer succeeds against the published artifacts;
5. release failure cannot publish or overwrite an unverified version;
6. compatibility, security, trust model, and support ownership decisions are accepted;
7. maintainers can produce the next verified patch without recreating the process manually.

## Follow-up after graduation

- Maven Central and signing
- Versioned documentation
- Maintenance release branches
- Dedicated benchmark infrastructure and hard regression thresholds
- Broader Windows/Linux/JDK/Spark matrices
- Long-running soak/load tests
- Telemetry and operational integrations
- Additional generators and convenience tooling

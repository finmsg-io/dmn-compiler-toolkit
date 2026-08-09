# Capability maturity and incubation

Status: candidate backlog  
Created: 2026-08-09  
Scope: honest maturity labels, compatibility expectations, and evidence-based graduation

## Opinion

The toolkit should distinguish a stable core from newer execution targets without delaying the
first production release until every backend has identical maturity.

An `incubating` marker is useful for Spark SQL and gRPC capabilities because they are implemented
and valuable, but their supported scope, compatibility, external-consumer evidence, and operational
matrices are not yet as strong as the interpreter/Java path.

Do not rename Maven artifacts to include `incubator`. Artifact coordinates are an integration
contract; renaming them again at graduation creates unnecessary consumer migration. Keep stable
coordinates such as `dmn-generator-sparksql` and express maturity consistently in metadata,
documentation, APIs, release notes, and generated capability views.

## Maturity vocabulary

Use exactly four public states:

| State | Meaning | Compatibility promise | Recommended use |
| --- | --- | --- | --- |
| `experimental` | Feasibility and learning; behavior or API may change substantially | None beyond the current version | Evaluation and feedback only |
| `incubating` | Useful and tested within a documented scope; important contracts/evidence remain incomplete | Best effort; incompatible changes require release notes but may occur before graduation | Controlled production with explicit limitations |
| `stable` | Supported scope, compatibility policy, consumer path, and production evidence are established | Semantic-versioning and published compatibility policy | General production use |
| `deprecated` | Still supported temporarily but scheduled for replacement/removal | Preserved for the documented deprecation window | Migration only |

Avoid additional terms such as preview, beta, early access, provisional, or production candidate.
Too many labels make the contract unclear.

## Label capabilities, not only modules

A module can contain capabilities with different maturity. For example:

| Module/capability | Candidate state | Reason |
| --- | --- | --- |
| Runtime interpreter | `stable` candidate | Reference execution and broad TCK evidence |
| Standalone Java generator | `stable` candidate | Strong interpreter/TCK parity evidence |
| Spark SQL generation | `incubating` | Narrower parity corpus and engine-specific semantic/version surface |
| Generic dynamic gRPC adapter | `incubating` | Implemented schema/adapter; consumer, lifecycle, and operational evidence need strengthening |
| Typed protobuf/gRPC generation | `incubating` | Typed API compatibility and field-number evolution policy remain unresolved |
| Persisted Runtime IR artifacts | `experimental` or `incubating` | Serialization/version compatibility boundary is not yet a stable public promise |

The final state must be assigned through the production graduation review rather than copied from
this proposal.

## Where the marker appears

One machine-readable capability manifest should own maturity. Generated/documented projections may
appear in:

- root README capability summary;
- module README header;
- capability/backend matrix;
- Maven POM description or properties;
- Java package documentation;
- public Java APIs through a project-owned annotation;
- generated Javadocs;
- release notes when state or compatibility changes;
- website badges/labels where useful.

Do not maintain each projection independently.

Example manifest shape:

```yaml
capabilities:
  spark-sql-generation:
    module: dmn-generator-sparksql
    maturity: incubating
    since: 1.0.0
    scope: docs/reference/spark-sql-capabilities.md
    evidence: dmn-generator-sparksql parity report
    graduation: docs/improvements/examples/capability-incubation/spark-sql-spec.md
```

The manifest is a future automation option, not required to start. Initially, one canonical table
may own the state if all other pages link to it.

## Optional Java annotation

A project-owned annotation can make instability visible in source and Javadocs without adding an
external annotation dependency:

```java
@Incubating(
    since = "1.0",
    reason = "Typed protobuf compatibility policy is not yet stable"
)
public final class DmnTypedGrpcGenerator {
}
```

Recommended properties:

- `@Documented` so Javadocs expose the status;
- `@Retention(CLASS)` so build tooling can inspect compiled artifacts;
- targets for package, type, method, constructor, and field as needed;
- `since`, `reason`, and optional tracking/reference values;
- no runtime behavior and no reflection requirement.

An annotation is a projection, not the source of the maturity decision. Avoid annotating every
internal class; mark public entry points and packages that users can reasonably adopt.

## Required incubating contract

Every incubating capability states:

- supported behavior and explicit exclusions;
- supported runtime/platform versions;
- API/schema/semantic compatibility promise;
- known limitations and failure behavior;
- production-use recommendation;
- executable evidence currently available;
- unresolved decisions;
- graduation criteria;
- owner or review trigger.

Unsupported behavior must be rejected explicitly where practical. `Incubating` is not permission to
return silently incorrect results.

## Change policy

For an incubating capability:

- incompatible changes are allowed before graduation when required by learning;
- changes still require release notes and migration guidance;
- silent format/schema changes are not acceptable;
- behavior already advertised as supported requires regression tests;
- security fixes may change behavior immediately with clear disclosure;
- deprecation is preferred when real consumers are known and migration is feasible.

For a stable capability, normal semantic versioning and its specific compatibility policy apply.

A move from `stable` back to `incubating` is not a normal status edit. It indicates a broken promise
and requires an explicit compatibility/support decision.

## Graduation evidence

A capability graduates from `incubating` to `stable` only when all applicable gates pass:

1. supported scope and exclusions are explicit;
2. unsupported inputs fail predictably;
3. public API/schema/semantic compatibility policy is accepted;
4. external consumer example compiles and runs from released artifacts;
5. parity/conformance evidence covers the advertised scope;
6. runtime/platform version matrix is tested;
7. security and hostile-input behavior are proportional to its trust boundary;
8. documentation, migration, lifecycle, and operational guidance are complete;
9. performance claims have reproducible evidence or are omitted;
10. no release-blocking open decision remains.

Graduation should occur in a normal release and appear under a release-note category such as
“Graduated capabilities.” It does not require renaming packages or Maven artifacts.

## Demotion and deprecation

- Use `deprecated` when a supported capability has a replacement or removal plan.
- Use a security advisory/known limitation when stable behavior is temporarily unsafe; do not hide
  it by relabeling the capability.
- Use `experimental` for new feasibility work, not as a demotion mechanism for shipped contracts.
- Removal follows the compatibility/deprecation policy appropriate to the capability.

## Initial candidate assignments

For the first public release review:

| Capability | Proposed state | Production message |
| --- | --- | --- |
| Interpreter | `stable` candidate | General production use within documented DMN scope |
| Java generator | `stable` candidate | General production use with interpreter parity evidence |
| Spark SQL generator | `incubating` | Controlled use within tested Spark/Databricks and FEEL subset |
| Generic gRPC adapter | `incubating` | Controlled service integration; dynamic schema/lifecycle contract under stabilization |
| Typed gRPC generator | `incubating` | Controlled use; generated schema compatibility not yet stable |

These labels narrow claims; they do not weaken existing correctness tests.

## Pragmatic adoption

### I0 — Establish the contract

- Accept the four-state vocabulary.
- Choose one canonical maturity table.
- Mark Spark SQL, generic gRPC, and typed gRPC candidate scopes.
- Remove conflicting “production ready” claims from non-canonical documentation.

### I1 — Make status visible

- Add module/API/Javadoc markers generated or linked from the canonical owner.
- Add known limitations, supported versions, and production-use recommendations.
- Add a release-note category for maturity changes.

### I2 — Execute graduation specs

- Run the Spark SQL, generic gRPC, and typed gRPC worked specifications.
- Strengthen their external-consumer and parity evidence.
- Resolve compatibility decisions before graduating typed contracts.

### I3 — Automate projections

- Introduce a capability manifest only after the fields prove useful.
- Generate maturity tables and validate that public API markers agree.
- Fail CI on conflicting or missing status for published capabilities.

Do not block I0 on building the manifest or annotation processor.

## Why this is pragmatic

- A stable core can graduate without overstating newer backends.
- Consumers understand what may change before adopting a capability.
- Artifact coordinates remain stable through graduation.
- Each capability graduates independently from executable evidence.
- Four labels are enough; the policy avoids a complex product-lifecycle taxonomy.
- Automation follows proven manual ownership rather than preceding it.

## Worked specifications

- [Spark SQL incubation/graduation spec](examples/capability-incubation/spark-sql-spec.md)
- [Generic gRPC incubation/graduation spec](examples/capability-incubation/generic-grpc-spec.md)
- [Typed gRPC incubation/graduation spec](examples/capability-incubation/typed-grpc-spec.md)

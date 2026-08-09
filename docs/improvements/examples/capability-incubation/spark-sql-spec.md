# INC-SPARK-001 — Stabilize Spark SQL generation

Status: example — proposed  
Candidate maturity: `incubating`  
Module: `dmn-generator-sparksql`  
Policy: [Capability maturity and incubation](../../capability-maturity-and-incubation.md)

## Outcome

Users can determine exactly which DMN/FEEL behavior the Spark SQL generator supports, generate SQL
that either matches the reference interpreter or fails explicitly, and deploy it on documented
Spark/Databricks versions with repeatable parity evidence.

## Current production recommendation

Controlled production use is acceptable only for the documented and tested capability subset.
Models outside that subset must not be represented as generally supported.

## Acceptance examples

1. Given a supported decision table/expression, when SQL is generated and executed on each supported
   Spark version, then its typed result matches the reference interpreter on the shared corpus.
2. Given null, decimal precision/scale, date/time/time-zone, list/context, ordering, or three-valued
   logic behavior, then the capability matrix identifies the semantic mapping and parity tests prove
   the advertised case.
3. Given an unsupported FEEL function, hit policy, type, or boxed expression, generation fails with
   a stable diagnostic identifying the unsupported construct.
4. Given a representative batch and streaming integration, generated SQL uses native SQL/Catalyst
   constructs without required UDF execution.
5. Repeated generation from the same Runtime IR produces byte-identical canonical SQL.
6. An external consumer resolves released artifacts, generates SQL from a documented model, runs it
   on a supported engine, and verifies the expected output.

## Required scope declaration

- Supported DMN/FEEL constructs and explicit exclusions
- Spark and Databricks version matrix
- Decimal precision/overflow policy
- Time-zone/date/time mapping
- Null/error and ordering semantics
- Identifier escaping and SQL injection boundary
- Batch versus streaming support
- Generated-query size/complexity limitations

## Constraints

- Interpreter semantics are the reference unless an explicit backend limitation is documented.
- Unsupported behavior fails before users execute plausible incorrect SQL.
- SQL generation remains deterministic and independent from machine locale/line endings.
- Performance claims require retained Spark plan/workload/environment evidence.
- Incubating status does not rename the Maven artifact or Java package.

## Graduation evidence

- Capability matrix linked to executable cases
- Cross-version engine test matrix
- Shared interpreter/Spark parity corpus
- Unsupported-feature diagnostic tests
- External consumer/integration example
- Security review of identifiers/literals and injection boundaries
- Deterministic output tests
- Lifecycle/deployment documentation
- Reproducible representative performance report or no performance claim

## Graduation decision

Graduate to `stable` when every advertised construct has parity evidence, all unsupported constructs
fail explicitly, the supported engine matrix passes, and no blocking semantic/version decision
remains. Otherwise remain `incubating` with a narrower visible scope.

# Backend Capability and Parity Matrix

Status: implementation-aligned reference. Reviewed: 2026-10-26, source `f2af395`.
This page describes representations, verified behavior and capability boundaries;
it does not turn aggregate TCK results into guarantees for every possible model.

## Execution and reflection boundaries

| Component | Execution | Reflection and fallback boundary |
| --- | --- | --- |
| Interpreter (`dmn-runtime`) | JVM Runtime IR evaluation with lexical frames | Model-defined Java descriptors use the explicit, default-deny external Java policy. |
| Java generator (`dmn-generator-java`) | Java source compiled to a JVM engine | Comparator calls use generated `FeelCallable` or the two-argument BKM adapter. External Java calls retain the runtime policy boundary. |
| Spark generator (`dmn-generator-sparksql`) | Native CTE queries with optional decision-level JVM fallback | Routing is chosen before execution. Native temporal SQL uses Spark `try_reflect` with generator-selected JDK factories; permitted model-selected literal Java calls have a separate policy check. |
| Optimizer (`dmn-optimizer`) | Compile-time IR transformations, not a fourth execution backend | Bounded folding and simplification; backend conformance is measured after optimization. |

Module responsibilities are described in the [module catalog](../modules.md).
The [external Java and comparator hardening evidence](../dev/slices/security-correctness-hardening-task-2026-10-24.md)
distinguishes generated comparator dispatch from policy-controlled host reflection.
No blanket reflection-free runtime claim is made.

## FEEL values and transport

The interpreter and generated Java use JVM FEEL values. Spark schemas depend on
the chosen plan: an ordinary column mapping is not a promise of lossless FEEL
transport. Use the generated input schema, matching codec projections and result
decoding described in the [Spark architecture](../sparksql-architecture.md).

| FEEL value | Interpreter / generated Java | Spark representation and boundary |
| --- | --- | --- |
| String / boolean | `String`, nullable `Boolean` | Native string/boolean columns; null behavior is operator-specific. |
| Number | `BigDecimal`; arithmetic uses the runtime's defined precision rules | Ordinary NUMBER maps to DOUBLE. Verified exact plans add lossless projections and encoded results; a rounded external DOUBLE cannot recover its original decimal. |
| Date | `LocalDate` | Ordinary schema mapping has DATE, while lossless hybrid transport uses encoded FEEL values and eligible native projections. |
| Time | `LocalTime` or `OffsetTime` | Encoded values preserve nanoseconds/offsets; eligible native SQL operates on generated projections. A bare timestamp is not the lossless contract. |
| Date and time | `LocalDateTime`, `OffsetDateTime` or `ZonedDateTime` | Encoded values and eligible temporal projections retain precision, offsets and named zones. Support depends on the lowering proof. |
| Year/month duration | `Period` | Encoded FEEL values preserve duration kind, including zero. Do not assume every plan exports a Spark interval. |
| Day/time duration | `Duration` | Encoded FEEL values preserve duration kind and nanoseconds; native arithmetic is admitted by capability checks. |
| List | JVM lists, including mixed values and null members | Arrays when element layouts are native; encoded payloads for other shapes. Native eligibility depends on source and result types. |
| Context | Name/value maps | Structs for verified native field layouts; encoded payloads otherwise. Missing fields, nulls and field ordering need their applicable lowering checks. |
| Function | Runtime/generated closures and lexical bindings | Functions are not exported as Spark values. Statically resolved bounded consumers can lower to native SQL; unsupported subgraphs retain fallback. |

Implementation references: `SparkSqlSchemaGenerator.mapType`,
`SparkSqlFeelValueCodec`, `SparkSqlExactArithmeticInput`, and the individual native
lowering proofs. [Numeric transport evidence](../dev/slices/lossless-numeric-fallback-transport-2026-10-23.md)
explains the distinction between native numeric columns and original FEEL inputs.

## Decision tables and error behavior

| Feature | Verified behavior | Scope / boundary |
| --- | --- | --- |
| UNIQUE, FIRST, PRIORITY, ANY | The pinned official cases pass for the recorded backends. | Matching, priority and output equality follow each policy; no blanket guarantee for untested type combinations. |
| COLLECT, SUM, MIN, MAX, COUNT | Covered by official decision-table cases and executing integration tests. | Native Spark aggregation still requires supported result/input representations. |
| RULE ORDER / OUTPUT ORDER | Recorded official cases preserve required ordering. | Nested or unsupported output shapes require their own capability checks. |
| Invalid arithmetic / coercion | Applicable FEEL error/null results are checked against expected values. | Type-proven invalid expressions can produce executable null plans; structural compilation errors still fail preparation. |
| Predicates, filters, quantifiers | Operator-specific null/error behavior, scalar/list handling and short-circuiting are exercised. | SQL null logic alone does not establish FEEL semantics; invalid nonboolean predicates must retain the runtime behavior. |
| Missing outputs / compilation blocks | Missing requested outputs fail; compilation rejection cannot pass as a negative test. | Every official case must execute and match its requested results. |
| Host policy denial / SQL failure | Denial and unexpected execution failures remain failures. | Native errors and null results do not trigger a fallback retry. |

The executing suites include `OfficialTckSuiteTest`, `SparkSqlDmnIntegrationTest`,
`SparkSqlHybridIntegrationTest`, and generated-Java parity/comparator regressions.
See [hardening evidence](../dev/slices/security-correctness-hardening-task-2026-10-24.md)
for the applicable tests rather than interpreting this table as a new certification.

## Conformance evidence

The [public TCK record](../tck-conformance.md) owns current figures and provenance:

- Optimized Spark SQL: **3,391/3,391 native passes (100%), zero fallback**, failed
  cases, compilation blocks, errors or unknown routes; all official cases executed.
- Interpreter and generated-Java results have separately dated checkpoints. They
  are not silently combined with the Spark run into one three-backend execution.
- [Retained Spark ledger](../assets/tck-accounting-sparksql-native.json): pinned TCK
  revision `20274cd2ba9cad805db6114f331c743f4b2603a1`, optimized hybrid configuration,
  measured with Spark 4.2.0 and JDK 25. Hybrid was allowed but unused.

This establishes self-verified conformance for that pinned inventory. Databricks,
Photon, streaming deployments, arbitrary model combinations and performance need
separate evidence. Generator regression SQL-action rates have a different
denominator from official TCK case counts.

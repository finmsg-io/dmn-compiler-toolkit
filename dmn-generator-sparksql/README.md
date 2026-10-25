# DMN Generator Spark SQL (`dmn-generator-sparksql`)

Generates Spark SQL queries from Runtime IR with an optional decision-level Java
UDF fallback. Pure SQL is the default. Hybrid execution is a Phase 1 correctness
baseline; full TCK conformance and performance remain to be measured.

## Execution modes

- **Pure SQL:** emits native CTE queries. Known unsupported constructs produce
  `UnsupportedRelationalSqlException`. This mode retains a broader native surface
  than the initial hybrid allowlist and still has known TCK failures.
- **Hybrid:** statically inspects each decision's dependency subgraph. Simple
  string/boolean constants, projections, and compatible conditionals stay native.
  Other decisions evaluate their required subgraphs through `DmnRuntime` in a JVM
  UDF. Closures stay inside Java. SQL exceptions and nulls never trigger fallback.

## Using hybrid execution

```java
RuntimeModel model = optimizedModel.model();
var options = DmnSparkSqlGeneratorOptions.of("dmn_inputs", true, Map.of());
var generated = new DmnSparkSqlGenerator().generate(optimizedModel, options);

// valuesBySlot is Map<Integer, ?> with an entry for every input, including nulls.
Row row = SparkSqlFeelValueCodec.inputRow(model, valuesBySlot, generated.inputSchema());
Dataset<Row> input = spark.createDataFrame(List.of(row), generated.inputSchema());
input.createOrReplaceTempView("dmn_inputs");
generated.registerUdfs(spark);

RuntimeDecision decision = model.decisions().getFirst();
String query = generated.sqlFiles().get("Decision_" + decision.resultSlot() + ".sql");
Object value = SparkSqlFeelValueCodec.fromSpark(spark.sql(query).head().get(0));
```

`generated.capabilities()` exposes routing reasons by decision ID.
`generated.udfs()` contains the required functions. Generated Java runners, when
`includeJavaRunner` is enabled, embed the model and register UDFs automatically.
The `of(...)` factory above generates SQL only.

Model-defined external Java calls are denied by default, including calls evaluated
during SQL generation. Hosts may explicitly use
`options.withExternalJavaPolicy(policy)` with the
[runtime policy API](../dmn-runtime/README.md#external-java-execution-policy).
Fallback UDFs and generated runners retain that policy. Fixed generator-selected
JDK built-ins remain separate from model-selected targets. Generated SQL and
runner sources are trusted executable artifacts specific to their generation
policy, including folded constants; regenerate from original IR when changing
policy and keep artifact caches separate for different trust configurations.

Hybrid schemas use native strings/booleans and versioned binary FEEL payloads for
other types. This preserves decimals, mixed lists, null members, contexts, offsets,
nanoseconds, and duration kinds. Build inputs with the codec and decode results
before using them as Java values. Binary columns cannot directly participate in
native Spark arithmetic or field access.

Direct calendar functions over `ANY` inputs additionally receive generated
struct columns containing a value tag and local year/month/day. Their calendar
answers are calculated in SQL; the original binary payload retains precision,
offsets and named-zone identity. Always pair `generated.inputSchema()` with the
three-argument `inputRow` overload shown above, including when supplying a
Dataset to a generated Java runner. The two-argument overload remains available
for schemas containing only the original input columns.

The direct `SparkSqlDecisionUdf(RuntimeModel, decisionId).evaluate(Row)` API returns
a Java FEEL value. Row fields must follow `RuntimeModel.inputs()` order. Decision
IDs and value slots are distinct; IR does not retain source decision names.

For direct BKM or decision-service invocation, use
`SparkSqlInvocationPlan.create(model, bkmId)`. It adds input slots for the declared
parameters and a decision invoking the function. Supply those slots using
`parameterNames()`, generate SQL from `plan.model()`, and execute
`Decision_<plan.resultSlot()>.sql`. Parameters follow FEEL coercion rules; an input
decision parameter overrides its normal global calculation inside the service.

## TCK verification

Run the pinned hybrid suite from the repository root:

```powershell
& 'C:\10-tools\apache-maven-3.9.12\bin\mvn.cmd' -Ptck -pl dmn-tck-runner -am '-Dtck.backend=sparksql' '-Dtck.spark.hybrid=true' '-Dtck.modelVariant=optimized' '-Dtck.requireFull=true' '-Dtck.timeoutSeconds=60' '-Dtest=OfficialTckSuiteTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The report is written to
`dmn-tck-runner/target/tck-accounting-optimized-sparksql-hybrid.json` and identifies
native and fallback passes. The harness executes requested queries on Spark and
does not convert Spark exceptions into null or skip execution when Spark is absent.

## Deployment and limits

- Hybrid execution requires this module, the interpreter, Runtime IR, and their
  dependencies on driver and executor classpaths. Spark is provided. Pure
  generated runners do not need the hybrid runtime dependencies.
- Model payloads require matching toolkit versions, not long-term persistence.
- External BKMs unsupported by `DmnRuntime` fail during planning. Supported
  external Java descriptors require referenced classes on executors.
- Function values cannot be exported as Spark values. Evaluate their consumer
  decision instead. `AllDecisions.sql` requires transportable outputs.
- Shared dependencies may be recomputed. No fixed planning-time, cluster
  throughput, or 100% TCK guarantee is claimed.

See [Spark SQL architecture](../docs/sparksql-architecture.md) for the transport
contract, routing policy, validation scope, and native-promotion plan.

## SQL text literals

Generated text literals preserve their values with either setting of
`spark.sql.parser.escapedStringLiterals` (`false` is Spark's default). The shared
encoder uses ordinary single-quoted text when no escaping is needed. Apostrophes,
backslashes and ASCII controls use a foldable UTF-8 binary-to-string cast, such as
`CAST(X'4f275265696c6c79' AS STRING)` for `O'Reilly`. This also works for struct
field names and regex arguments. Fixed duration/time regexes use `[.]` for literal
decimal points to avoid parser-dependent backslash processing.

SQL identifier quoting and versioned FEEL binary payloads are separate operations.
The generated Java runner escapes the completed SQL once for Java source; it does
not escape model text a second time. Regenerate SQL and runner source together
when adopting this change. Executing regressions cover both parser settings on
Spark 4.2.0; see [HARD-02 evidence](../docs/dev/slices/security-correctness-hardening-task-2026-10-24.md#2-spark-sql-literal-escaping-correctness-priority).

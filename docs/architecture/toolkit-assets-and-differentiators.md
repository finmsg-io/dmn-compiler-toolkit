# Toolkit Assets and Evidence

Reviewed: 2026-10-26 against implementation checkpoint `f2af395`.

The toolkit compiles DMN models through parsing, semantic analysis and Runtime IR,
then supports interpretation, generated Java and generated Spark SQL. This page
summarizes concrete assets and links their evidence; it does not claim measured
superiority over other DMN products.

## Modules and build

The default reactor contains **12 modules** and requires **JDK 25 and Maven 3.9+**.
The [module catalog](../modules.md) lists the authoritative reactor membership and
responsibilities. TCK and benchmark support are enabled through their opt-in
profiles; the parent POM is build configuration, not a separate runtime module.

| Asset | Purpose | Evidence / boundary |
| --- | --- | --- |
| XML frontend and FEEL parser | Read supported DMN XML into the semantic model and parse expressions. | Supported-subset contracts and tests; see [modules](../modules.md). |
| Semantic analysis and Runtime IR | Resolve dependencies, infer types, lower expressions and represent executable decisions. | Shared model/IR contracts; compilation diagnostics remain distinct from execution results. |
| Interpreter | Evaluate Runtime IR on the JVM, including contexts, lexical bindings and decision tables. | [Conformance record](../tck-conformance.md) and runtime tests. |
| Java generator | Generate Java source for model execution. | Generated-source compilation and runtime parity tests; [backend matrix](backend-parity-matrix.md). |
| Spark SQL generator | Generate native SQL with capability checks and optional decision-level fallback. | All 3,391 pinned official cases passed natively; broader support remains bounded. |
| Optimizer | Apply static transformations before execution or generation. | Transformation/parity tests; optimization is not a separate execution backend. |
| gRPC and consumer smoke tests | Generate integration artifacts and verify supported consumer paths. | Default-reactor membership alone is not a production-readiness claim. |
| TCK and benchmark tooling | Record conformance outcomes and run performance experiments. | Correctness and performance require separate measurements. |

## Execution boundaries

Generated Java separates compilation from model evaluation and uses generated
control flow and indexed bindings. This does not mean all evaluation is allocation
free: decimals, lists, contexts, closures and helper calls can allocate objects.
Generated code uses toolkit runtime helpers where required; it is not guaranteed
to depend exclusively on the JDK.

Comparator dispatch uses generated typed calls. Model-defined external Java calls
retain an explicit, default-deny runtime policy and can use reflection when the
host permits the exact target. Spark temporal SQL uses generator-selected JDK
factories through Spark built-ins. See [hardening evidence](../dev/slices/security-correctness-hardening-task-2026-10-24.md)
and the [backend matrix](backend-parity-matrix.md). There is no blanket
reflection-free-runtime claim.

GraalVM/native-image deployment, Databricks/Photon execution and streaming
performance need their own compatibility tests and measurements. Java source
generation alone does not establish those deployment properties.

## Verified conformance

Optimized generated Spark SQL executed **3,391/3,391 pinned official TCK cases
natively (100%)** and matched expected results. Fallback passes, failed cases,
compilation blocks, unexpected errors and unknown routes were all **zero**.

The [public conformance record](../tck-conformance.md) gives the pinned suite,
configuration, retained per-case ledger and reproduction command. Interpreter and
generated-Java checkpoints are separately dated; these results are not presented
as one combined three-backend run.

## Performance claims

AOT source generation, indexed bindings and SQL lowering are implementation
choices that can be measured. They do not establish latency, throughput, memory,
GC or dependency-size advantages over another engine without a controlled,
reproducible comparison. See the [benchmark backlog](../todos/dmn-benchmarks.md)
for the boundary between existing benchmark infrastructure and pending validation.

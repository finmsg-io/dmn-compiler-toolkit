# BENCH-001 - Correct and clarify the execution benchmark harness

Status: implemented
Profile: benchmark correctness  
Source proposal: [Trustworthy benchmark evidence and scalability](../../benchmark-evidence-and-scalability.md)

## Outcome

JMH workers measure interpreter and generated-Java execution without unintended shared mutable
benchmark state. Results distinguish direct engine execution from invocation-adapter overhead, and
the supported concurrent-sharing contract is verified for correctness.

## Observable acceptance examples

1. Given an execution benchmark running with eight threads, when workers select payloads, then each
   worker mutates only its own cursor and no data race is present.
2. Given a compiled model or generated engine intended to be shared, when independent payloads are
   evaluated concurrently, then every result matches its single-threaded expected result.
3. Given an execution object not intended to be shared, when JMH creates workers, then each worker
   receives its own instance and the limitation is documented.
4. Given generated Java execution, when benchmarks run, then a direct or strongly typed invocation
   score is reported separately from `Method.invoke` or other adapter overhead.
5. Given the smallest reference model, when invocation-control overhead is material relative to the
   measured operation, then the report qualifies the engine conclusion.
6. Given the benchmark test suite on Windows or Linux, when the relevant Maven profile runs, then
   benchmark sources compile and correctness tests pass without platform-specific paths.

## Constraints

- Preserve the five reference models and interpreter/generated-Java comparability.
- Keep benchmark setup outside the timed method.
- Do not share mutable payloads between workers.
- Do not silently change the public runtime or generated-engine sharing contract merely to improve a
  benchmark score.
- Keep reflective invocation if it represents a real supported path, but label it as an adapter path.
- Avoid a general benchmarking framework abstraction until duplication causes demonstrated friction.

## Non-goals

- Proving production scalability in this change.
- Setting performance regression thresholds.
- Building dashboards or distributed load generation.
- Optimizing the runtime based only on the corrected benchmark.
- Rewriting all generated APIs solely for benchmark convenience.

## Unknowns and opportunities

- Whether generated engine instances are immutable and intentionally shareable
- Whether a cached typed `MethodHandle` is the production adapter or only a diagnostic comparison
- Whether fixture construction should be centralized without obscuring individual model setup
- Opportunity: reuse concurrency correctness tests as production-readiness evidence
- Opportunity: reduce interpreter allocations after GC evidence identifies the dominant sources

## Skills and checks

- JMH state and invocation semantics
- Java concurrency correctness
- Maven benchmark profile on Windows and Linux
- Generated-code compilation and execution
- Spotless verification for handwritten and generated sources

## Verification

- Run focused benchmark integrity and concurrency correctness tests.
- Compile/package the benchmark JAR through the Maven reactor.
- Run representative one-thread and eight-thread smoke measurements.
- Inspect JMH output to confirm the requested thread count and distinct benchmark-path names.
- Run the repository's formatting check after all relevant generation profiles.
- Confirm that repeated generation produces no source diff.

## Evidence required for completion

- source diff showing benchmark-scoped immutable and thread-scoped mutable state;
- passing concurrent result-equivalence tests;
- raw smoke output for interpreter, direct generated, and adapter paths;
- documented sharing decision for runtime and generated engine instances;
- exact Windows and Linux verification commands.

## Completion evidence

- benchmark fixtures are benchmark-scoped while `BenchmarkCursor` is thread-scoped;
- generated engines implement the reflection-free `GeneratedDecisionEngine` invocation contract;
- benchmark names distinguish interpreter core, generated direct, reflective adapter, end-to-end,
  and invocation-control paths;
- `BenchmarkIntegrityTest` verifies concurrent interpreter/generated parity with eight workers;
- benchmark defaults use three forks and the retained-run scripts use 1/2/4/8 threads with `-prof gc`;
- raw one-thread and eight-thread smoke JSON is retained under `dmn-benchmarks/results/`;
- Windows and Linux commands are documented in `dmn-benchmarks/README.md`.

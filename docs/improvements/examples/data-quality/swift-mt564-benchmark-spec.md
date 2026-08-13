# DQ-004 - SWIFT MT564 reference benchmark

Status: example - proposed

Profile: reference-showcase performance evidence

Depends on: DQ-001, DQ-002, corrected benchmark harness

Source proposal: [Data-quality BKM patterns, MT564 showcase, and authoring acceleration](../../data-quality-patterns-and-authoring.md)

Related proposal: [Trustworthy benchmark evidence and scalability](../../benchmark-evidence-and-scalability.md)

## Outcome

Maintainers and users can reproduce the evaluation cost of the MT564 data-quality reference showcase
for interpreter and generated Java, understand how cost changes with message complexity and
concurrency, and distinguish engine execution from input/output adaptation.

## Benchmark cases

Use deterministic synthetic fixtures derived from the reviewed DQ-002 scenario bundle:

| Case | Purpose |
| --- | --- |
| `validBaseline` | Normal successful evaluation and empty/error-free violation path |
| `singleViolation` | Cost of creating and returning one structured violation |
| `multipleViolations` | Cost of evaluating independent failures and aggregating a report |
| `largeStructure` | Cost over the maximum representative repeated option/sequence collection |

Fixtures must remain semantically valid for their intended case and must not contain production
financial-message data.

## Measurement paths

1. `interpreterCore` - pre-mapped slot inputs evaluated by the interpreter.
2. `generatedJavaDirect` - equivalent pre-mapped inputs evaluated through a direct generated-engine
   call.
3. `interpreterEndToEnd` - canonical normalized input mapping plus structured output adaptation.
4. `generatedJavaEndToEnd` - equivalent generated path including the same consumer-facing adapters.
5. Optional explicitly named adapter/control methods when reflection or scenario lookup overhead must
   be quantified.

Compilation, model loading, source generation, Java compilation, and fixture construction happen in
trial setup and are not included in evaluation scores. Compiler-phase performance belongs in a
separate benchmark.

## Observable acceptance examples

1. Given any benchmark parameter, when interpreter and generated Java execute it, then their outputs
   have already passed DQ-002 structural parity tests.
2. Given the direct generated benchmark, when its timed method runs, then it does not use reflection
   unless the benchmark name explicitly identifies an adapter path.
3. Given eight JMH workers, when payloads are selected, then each worker owns its mutable cursor and
   no benchmark state race exists.
4. Given a reference run, when results are stored, then one-thread latency and 1/2/4/8-thread total
   throughput are available for each engine and fixture, bounded by available processors.
5. Given GC profiling, when the report is generated, then allocation bytes per operation, allocation
   rate, GC count, and GC time are available.
6. Given the same raw result data, when the Markdown/CSV summary is generated on Windows or Linux,
   then output is deterministic.
7. Given `mvn clean`, when disposable build output is removed, then the accepted reference/release
   benchmark evidence remains in its durable location.
8. Given an observed regression, when only one noisy run supports it, then the report does not claim a
   product regression without confirmation against established variance.

## Constraints

- Correctness and backend parity from DQ-002 are prerequisites.
- Use benchmark-scoped immutable fixtures and thread-scoped mutable execution state.
- Compare equivalent semantic work and label core versus end-to-end paths clearly.
- Use at least three forks for accepted comparison/reference evidence.
- Record JMH/JVM/OS/CPU, Git revision, dirty state, model revision, thread count, warmup, measurement,
  forks, and profiler settings.
- Keep quick benchmark-integrity tests in normal CI; run the long reference matrix on demand,
  scheduled infrastructure, or the release train.
- Do not establish hard regression thresholds until repeated controlled results characterize normal
  variance.
- Never describe throughput from one host as service capacity or SLA evidence.

## Non-goals

- Measuring raw SWIFT parsing, networking, gRPC, databases, or Spark scheduling.
- Comparing external DMN engines in the first reference benchmark.
- Including compilation latency in evaluation scores.
- Optimizing the engine merely to improve one showcase score.
- Committing every developer benchmark run.

## Verification

- Benchmark integrity tests prove all four fixture parameters and expected benchmark methods exist.
- Timed methods are inspected/tested to exclude setup and unintended reflection.
- One-thread smoke runs complete for every engine/path/fixture combination.
- The accepted long run uses explicit warmup, measurement, forks, threads, and `-prof gc` settings.
- Raw JSON record counts and thread metadata are validated before summary generation.
- Scaling and parallel efficiency are recalculated from raw scores.
- Reports distinguish observations, supported explanations, and unresolved causes.
- Commands and report generation run on Windows and Linux.

## Evidence required for completion

- benchmark source and integrity tests;
- exact Maven/JMH commands or a versioned profile/script;
- raw accepted JMH JSON and readable log;
- deterministic Markdown/CSV summary;
- environment and Git metadata;
- interpreter/generated-Java correctness link for every fixture;
- documented evidence-retention and refresh policy.

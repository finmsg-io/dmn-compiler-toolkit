# Trustworthy benchmark evidence and scalability

Status: candidate backlog  
Created: 2026-08-09  
Scope: execution benchmarks for the interpreter and generated Java across the five reference models

## Opinion

The existing JMH benchmarks are useful for detecting large performance differences, but the current
multi-threaded results should not yet be treated as definitive scalability evidence.

This is not primarily a load-generation problem. JMH workers invoke the evaluation methods directly
in a tight loop. The larger risks are shared mutable benchmark state, invocation overhead, allocation
pressure, and drawing conclusions from only one and eight threads with a single fork.

The pragmatic response is not a benchmark platform. First correct the harness, add the measurements
needed to explain its results, and publish only claims that the retained evidence supports.

## What the current evidence says

The 2026-08-09 comparison used five warmup iterations of five seconds and five measurement
iterations of fifteen seconds, with one fork. Moving from one to eight JMH threads increased total
throughput by approximately 1.4x to 2.1x rather than 8x.

That result is plausible on an eight-logical-processor Windows host, but several effects are mixed:

- `CreditApprovalBenchmark`, `TrafficViolationBenchmark`, and `ScalarArithmeticBenchmark` use
  `@State(Scope.Benchmark)` and therefore share mutable state between workers;
- payload selection includes a shared `index++` in two of those benchmarks, creating a data race and
  cache-line contention;
- generated Java is invoked through `Method.invoke`, so reflection is included in the score;
- interpreter evaluation creates per-operation arrays, maps, frames, and results;
- one fork provides weak protection against JVM, operating-system, frequency, and thermal variance;
- comparing only one and eight threads does not reveal where scaling stops.

These are hypotheses to isolate with measurements, not excuses for a particular result.

## Desired outcome

Maintainers and users can distinguish:

1. single-request engine latency;
2. total throughput under controlled concurrency;
3. public-API/end-to-end cost;
4. invocation-adapter cost;
5. allocation and garbage-collection cost.

Every published performance or scalability statement links to reproducible results with enough
environment metadata to interpret them.

## Small benchmark model

Use two kinds of JMH state:

- benchmark-scoped immutable fixtures: compiled models and immutable source payloads;
- thread-scoped execution state: cursor/index, runtime or engine instance when required, and any
  mutable per-thread input/output state.

Whether a compiled model or generated engine instance is safe and intended to be shared is a product
contract. Test that contract separately; do not accidentally test it through shared JMH fields.

## Measurement layers

Keep a small set of deliberately named benchmark paths:

| Path | What it answers |
| --- | --- |
| Interpreter core | Cost of evaluating pre-mapped slot inputs |
| Generated direct | Cost of a typed generated-engine call without reflection |
| Generated adapter | Cost through the supported reflective or method-handle adapter |
| Public API/end to end | Cost including named-input conversion and response adaptation |
| Invocation control | Fixed payload-selection and invocation overhead with negligible DMN work |

Do not combine these paths into one headline number. The direct path describes generated execution;
the public path describes consumer experience.

For extremely small models, retain a single-evaluation latency benchmark and add a batched variant
using `@OperationsPerInvocation`. Batching may expose engine cost more clearly, but it must not replace
the latency measurement.

## Scalability protocol

For evidence intended for comparison or publication:

- measure 1, 2, 4, and 8 threads, capped by the host's available logical processors;
- use at least three forks;
- retain explicit warmup and measurement durations;
- run with JMH's GC profiler;
- report total throughput, latency under contention, scaling, and parallel efficiency;
- keep raw JMH JSON and a human-readable summary;
- record the Git commit and whether the worktree was clean.

Definitions:

```text
scaling(N) = throughput(N) / throughput(1)
parallel efficiency(N) = scaling(N) / N
```

An eight-thread average-time result is latency under eight-way contention. It must not be presented
as ordinary single-request latency.

## Environment record

Each retained comparison records at least:

- operating system and architecture;
- CPU model, physical cores, and logical processors where available;
- JVM vendor/version and JVM arguments;
- memory and relevant power/performance settings;
- JMH version, forks, thread counts, warmup, and measurement settings;
- repository revision and dirty/clean state;
- model corpus and benchmark-path definitions.

Missing metadata should qualify the conclusion rather than silently becoming an assumption.

## Correctness before speed

Add concurrency tests for the supported sharing contract before interpreting multi-threaded
performance. Tests should repeatedly evaluate independent payloads from multiple threads and compare
their results with known single-threaded outcomes.

Unsupported sharing must be documented or rejected. Supported sharing must be deterministic and
free of result contamination. A fast result is not evidence if concurrent evaluation is incorrect.

## Reporting rules

- Use `ops/s` for total throughput and state the thread count beside it.
- Report uncertainty from JMH, not only the central score.
- Describe generated-versus-interpreter speedup separately from thread scaling.
- Do not claim linear scalability as an expectation; explain the observed saturation point.
- Treat reflection, input conversion, and batching as explicit benchmark dimensions.
- Store raw evidence outside Maven's disposable `target/` tree when it supports documentation or a
  release claim.
- Keep routine CI benchmarks short; run the longer matrix on demand or in a controlled scheduled job.

## Pragmatic delivery

### B0 - Correct the harness

- Move mutable worker state to `Scope.Thread`.
- Remove shared cursor races.
- make shared-versus-per-thread engine ownership explicit.
- Add concurrency correctness tests for the promised sharing mode.
- Verify that one-thread results remain directionally consistent.

### B1 - Explain the cost

- Add direct generated invocation beside the current adapter path.
- Add an invocation-only control for very small evaluations.
- Capture allocation and GC metrics.
- Add batching only where harness overhead is material and label it clearly.

### B2 - Produce scalability evidence

- Run the five-model matrix at 1, 2, 4, and 8 threads with at least three forks.
- Calculate scaling and parallel efficiency automatically.
- Store raw JSON, environment metadata, and a generated Markdown/CSV summary.
- Review conclusions against uncertainty and correctness evidence.

### B3 - Automate only proven needs

- Add a quick regression profile to normal CI if its duration and variance are acceptable.
- Run long benchmarks on demand or on a controlled release/scheduled runner.
- Add regression thresholds only after enough history exists to set robust tolerances.

Do not begin with a benchmark database, dashboard, distributed load generator, or hard CI gates.
Those may become useful after stable measurements reveal a recurring need.

## Success criteria

- no JMH worker shares an unintended mutable cursor or execution object;
- the supported concurrent-evaluation contract has correctness tests;
- direct execution, adapter overhead, and end-to-end cost are distinguishable;
- allocation evidence explains whether GC is a material constraint;
- scalability reports cover intermediate thread counts and calculate efficiency;
- every published claim can be reproduced from retained raw results and metadata;
- the benchmark suite remains understandable and runnable on Windows and Linux.

## Worked specifications

- [Correct and clarify the execution benchmark harness](examples/benchmark-evidence/refine-execution-benchmarks-spec.md)
- [Produce reproducible scalability evidence](examples/benchmark-evidence/scalability-study-spec.md)

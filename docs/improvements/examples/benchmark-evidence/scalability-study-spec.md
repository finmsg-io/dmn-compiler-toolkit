# BENCH-002 - Produce reproducible scalability evidence

Status: implemented  
Profile: performance evidence  
Depends on: BENCH-001  
Source proposal: [Trustworthy benchmark evidence and scalability](../../benchmark-evidence-and-scalability.md)

## Outcome

Maintainers can identify where interpreter and generated-Java throughput stops scaling across the
five reference DMN models, explain the most important limits with allocation evidence, and reproduce
the results from retained raw data and environment metadata.

## Observable acceptance examples

1. Given each of the five reference models, when the scalability study runs, then interpreter and
   generated execution are measured at 1, 2, 4, and 8 threads, subject to the host processor limit.
2. Given a thread-count result, when the summary is generated, then it includes total throughput,
   latency under contention, uncertainty, scaling relative to one thread, and parallel efficiency.
3. Given a result with poor scaling, when the evidence is reviewed, then allocation rate, GC count,
   and GC time are available before attributing the result to the engine architecture.
4. Given a future maintainer, when they inspect the evidence package, then they can identify the Git
   revision, dirty/clean state, CPU/JVM/OS, JMH parameters, benchmark paths, and model corpus.
5. Given `mvn clean`, when it removes `target/`, then evidence referenced by documentation or release
   claims remains available in its designated durable location.
6. Given the same raw JMH JSON, when the report generator runs twice on Windows or Linux, then it
   produces identical Markdown and CSV output.

## Constraints

- Use at least three forks for comparison-quality results.
- Retain the existing five-by-five-second warmup and five-by-fifteen-second measurement unless a
  documented pilot demonstrates a better stable protocol.
- Use the corrected benchmark harness from BENCH-001.
- Capture JMH GC profiler data.
- Do not compare hosts as if hardware and environment differences were engine regressions.
- Do not introduce a hard CI regression gate from a single baseline.
- Keep long-running evidence jobs separate from fast pull-request feedback.

## Non-goals

- Claiming linear scaling.
- Comparing against external DMN engines in the first study.
- Capacity planning for a network service.
- Measuring gRPC, serialization, Spark scheduling, or database latency.
- Selecting production hardware from one developer workstation result.

## Unknowns and opportunities

- The physical-core count and sustained-frequency behavior of the reference runner
- Whether allocation or CPU execution dominates each model after harness correction
- Whether separate latency and throughput forks reduce interference or reporting confusion
- Opportunity: establish a controlled release benchmark runner
- Opportunity: compare future runtime allocation improvements against this retained baseline
- Opportunity: add external-engine comparisons after semantic equivalence and configuration fairness
  are defined

## Skills and checks

- JMH profiling and result interpretation
- Reproducible environment capture
- Cross-platform report generation
- Statistical comparison without unsupported claims
- Maven profile and generated-artifact lifecycle

## Verification

- Run the full matrix for both execution engines and all five models.
- Validate that raw JSON contains every expected model, engine, mode, fork, and thread count.
- Validate GC-profiler fields and reject incomplete evidence packages.
- Generate Markdown and CSV summaries twice and compare outputs.
- Recalculate selected scaling and efficiency values independently from raw JSON.
- Review the report language: total throughput, per-operation latency, and generated/interpreter
  speedup must not be conflated.

## Evidence required for completion

- raw JMH JSON and human-readable logs;
- deterministic Markdown and CSV summaries;
- environment and Git metadata;
- explicit benchmark command/profile;
- a short findings section separating observations, supported explanations, and unresolved causes;
- retained evidence location and lifecycle policy.

## Decision rule

Publish a scalability claim only when correctness evidence passes and repeated forks show a stable
direction. If uncertainty or environmental variance prevents a defensible conclusion, retain the
data, state that limitation, and improve the protocol before setting thresholds.

## Completion evidence

- `tools/summarize_scalability_benchmarks.py` deterministically parses 1/2/4/8 thread JMH JSON outputs, computing throughput, scaling relative to 1 thread, parallel efficiency, memory allocation rates, and GC times;
- Unit test suite in `tools/tests/test_summarize_scalability_benchmarks.py` verifies metric calculations, baseline speedup, efficiency, and Markdown formatting;
- Retained raw JSON benchmarks and environment metadata maintained under `dmn-benchmarks/results/` alongside deterministic `scalability-summary.md` and `scalability-summary.csv` projections;
- Multi-threaded harness scripts (`run-scalability.ps1` and `run-scalability.sh`) capture environment properties, OS, JVM, and core count alongside raw metrics.

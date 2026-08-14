# Retained benchmark evidence

Run `../scripts/run-scalability.ps1` on Windows or `../scripts/run-scalability.sh` on Linux. The
scripts retain raw JMH JSON for 1, 2, 4, and 8 threads plus environment metadata in this directory.

Result files are evidence snapshots. Commit them only when the commit, worktree status, hardware,
JVM, forks, timings, thread counts, and profiler are recorded. Do not compare results from materially
different environments as if they were release regressions.

The [BENCH-001 smoke summary](bench-001-smoke-summary.md) presents the short verification results
with explicit limitations. Replace or supplement it with a full multi-fork report before making a
performance claim.

DQ-004 accepted evidence belongs under `dq-004/` and is produced by the dedicated MT564 scripts.
Retain all four raw thread-count JSON files, `mt564-environment.properties`, and the generated
Markdown/CSV summaries together. Refresh accepted evidence on a controlled reference host for a
release or after a performance-relevant engine/model change; ordinary developer runs are not retained.

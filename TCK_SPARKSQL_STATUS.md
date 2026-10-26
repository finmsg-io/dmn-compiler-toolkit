# Spark SQL TCK status

The optimized backend executed **3,391/3,391 pinned official TCK cases natively
(100%)**, with **zero fallback, failures, compilation blocks, errors or unknown
routes**. All expected results were checked.

- [Current conformance record and retained evidence](docs/tck-conformance.md)
- [Spark capability boundaries](docs/architecture/backend-parity-matrix.md)
- [Historical checkpoint journal](docs/dev/history/spark-sql-checkpoints-through-2026-10-26.md)
- [Active task backlog](docs/todos/index.md)

The current result is self-verified against the pinned suite; dated evidence
for earlier sources remains in the historical journal.

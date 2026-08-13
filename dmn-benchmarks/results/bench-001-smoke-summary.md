# BENCH-001 smoke summary

These results verify benchmark wiring, distinct execution paths, concurrency, and GC/allocation
capture. They use one fork, one one-second warmup, and one one-second measurement. They are **not
publication-grade performance evidence** and must not be used for regression thresholds or product
claims. The raw JMH JSON and environment record in this directory are authoritative.

## Originations

| Path | 1-thread throughput (ops/µs) | 8-thread throughput (ops/µs) | Allocation (1 thread, B/op) |
| --- | ---: | ---: | ---: |
| Interpreter core | 0.096 | 0.337 | 7,208 |
| Generated direct | 0.666 | 3.482 | 1,242 |
| Generated reflective adapter | 0.881 | 2.823 | 1,240 |
| Generated end to end | 0.609 | 1.515 | 1,337 |

## Ranked Loan Products

| Path | 1-thread throughput (ops/µs) | 8-thread throughput (ops/µs) | Allocation (1 thread, B/op) |
| --- | ---: | ---: | ---: |
| Interpreter core | 0.131 | 0.315 | 7,222 |
| Generated direct | 1.174 | 2.986 | 846 |
| Generated reflective adapter | 1.092 | 3.745 | 846 |
| Generated end to end | 0.991 | 2.946 | 926 |

The single short iteration can invert close results—for example, adapter versus direct—so no
speedup or scalability conclusion is drawn here. The retained three-fork 1/2/4/8 protocol must be
run before publishing conclusions.

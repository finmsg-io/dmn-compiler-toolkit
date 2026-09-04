# DMN Multi-Threaded Scalability Benchmark Summary

Generated deterministically from retained JMH JSON runs.

| Benchmark Method | Scenario | Threads | Score | Error | Unit | Scaling | Efficiency | B/op | GC (ms) |
| :--- | :--- | ---: | ---: | ---: | :--- | ---: | ---: | ---: | ---: |
| `generatedAdapter_Originations` | default | 1 | 0.881 | - | ops/us | 1.00x | 100.0% | 1240.02 | 26.00 |
| `generatedAdapter_Originations` | default | 8 | 2.823 | - | ops/us | 3.20x | 40.1% | 1240.01 | 29.00 |
| `generatedAdapter_RankedLoanProducts` | default | 1 | 1.092 | - | ops/us | 1.00x | 100.0% | 845.75 | 14.00 |
| `generatedAdapter_RankedLoanProducts` | default | 8 | 3.745 | - | ops/us | 3.43x | 42.9% | 845.74 | 19.00 |
| `generatedAdapter_ScalarArithmetic` | default | 1 | 0.008 | - | ops/ns | 1.00x | 100.0% | 128.00 | 24.00 |
| `generatedAdapter_ScalarArithmetic` | default | 8 | 0.027 | - | ops/ns | 3.16x | 39.5% | 128.00 | 38.00 |
| `generatedDirect_Originations` | default | 1 | 0.666 | - | ops/us | 1.00x | 100.0% | 1241.78 | 43.00 |
| `generatedDirect_Originations` | default | 8 | 3.482 | - | ops/us | 5.23x | 65.3% | 1239.79 | 28.00 |
| `generatedDirect_RankedLoanProducts` | default | 1 | 1.174 | - | ops/us | 1.00x | 100.0% | 845.74 | 16.00 |
| `generatedDirect_RankedLoanProducts` | default | 8 | 2.986 | - | ops/us | 2.54x | 31.8% | 845.28 | 27.00 |
| `generatedDirect_ScalarArithmetic` | default | 1 | 0.010 | - | ops/ns | 1.00x | 100.0% | 128.00 | 33.00 |
| `generatedDirect_ScalarArithmetic` | default | 8 | 0.034 | - | ops/ns | 3.29x | 41.2% | 128.00 | 32.00 |
| `generatedEndToEnd_Originations` | default | 1 | 0.609 | - | ops/us | 1.00x | 100.0% | 1336.92 | 20.00 |
| `generatedEndToEnd_Originations` | default | 8 | 1.515 | - | ops/us | 2.49x | 31.1% | 1336.17 | 47.00 |
| `generatedEndToEnd_RankedLoanProducts` | default | 1 | 0.991 | - | ops/us | 1.00x | 100.0% | 925.75 | 18.00 |
| `generatedEndToEnd_RankedLoanProducts` | default | 8 | 2.946 | - | ops/us | 2.97x | 37.2% | 885.74 | 28.00 |
| `interpreterCore_Originations` | default | 1 | 0.096 | - | ops/us | 1.00x | 100.0% | 7207.92 | 27.00 |
| `interpreterCore_Originations` | default | 8 | 0.337 | - | ops/us | 3.53x | 44.1% | 7187.48 | 30.00 |
| `interpreterCore_RankedLoanProducts` | default | 1 | 0.131 | - | ops/us | 1.00x | 100.0% | 7222.14 | 20.00 |
| `interpreterCore_RankedLoanProducts` | default | 8 | 0.315 | - | ops/us | 2.41x | 30.1% | 7261.35 | 48.00 |
| `interpreterCore_ScalarArithmetic` | default | 1 | 0.003 | - | ops/ns | 1.00x | 100.0% | 576.00 | 31.00 |
| `interpreterCore_ScalarArithmetic` | default | 8 | 0.008 | - | ops/ns | 2.80x | 35.1% | 592.00 | 37.00 |
| `invocationControl_ScalarArithmetic` | default | 1 | 0.994 | - | ops/ns | 1.00x | 100.0% | 0.00 | - |
| `invocationControl_ScalarArithmetic` | default | 8 | 3.282 | - | ops/ns | 3.30x | 41.3% | 0.00 | - |

## Environment Metadata

```properties
timestamp_local=2026-08-13T22:52:00+02:00
source_commit=3d6397c
worktree=dirty (BENCH-001 implementation under test)
os=Windows 10 x86_64
logical_processors=8
jvm=OpenJDK 25.0.2 Zulu25.32+21-CA
jmh=1.37
benchmarks=ScalarArithmetic, Originations, RankedLoanProducts; interpreterCore/generatedDirect/generatedAdapter/generatedEndToEnd/invocationControl as applicable
threads=1,8
forks=1 (smoke verification only)
warmup=1x1s
measurement=1x1s
profiler=gc
publication_grade=false
```

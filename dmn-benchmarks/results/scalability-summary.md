# DMN Multi-Threaded Scalability Benchmark Summary

Generated deterministically from retained JMH JSON runs.

> [!IMPORTANT]
> **Key Metric Visual Indicators**:
> - 🚀 **Throughput (`thrpt`)**: Measures operations processed per time unit (`ops/µs`, `ops/ns`, `ops/s`). **Higher is Better (⬆️)**.
> - ⏱️ **Average Latency (`avgt`)**: Measures execution duration per operation (`µs/op`, `ns/op`). **Lower is Better (⬇️)**.

---

## 1. 🚀 Throughput Benchmarks (`thrpt` — Higher is Better ⬆️)

Measures how many decision evaluations the engine can process per unit of time under concurrent load.

| Model | Engine | Invocation Path | Scenario | Threads | Throughput Score [⬆️] | Error (±) | Unit | Speedup (vs 1T) | Parallel Efficiency | Memory (B/op) | GC Time (ms) |
| :--- | :--- | :--- | :--- | ---: | ---: | ---: | :--- | ---: | ---: | ---: | ---: |
| `CreditApproval` | **`Generated Java`** | `Adapter` | default | 1 | **1.938** | ±0.032 | `ops/us` | 1.00x | 100.0% | 862.48 | 1147.00 |
| `CreditApproval` | **`Generated Java`** | `Adapter` | default | 2 | **2.870** | ±0.128 | `ops/us` | 1.48x | 74.0% | 862.48 | 1593.00 |
| `CreditApproval` | **`Generated Java`** | `Adapter` | default | 4 | **3.119** | ±0.168 | `ops/us` | 1.61x | 40.2% | 846.51 | 1824.00 |
| `CreditApproval` | **`Generated Java`** | `Adapter` | default | 8 | **5.145** | ±0.168 | `ops/us` | 2.65x | 33.2% | 822.48 | 1815.00 |
| `CreditApproval` | **`Generated Java`** | `Direct` | default | 1 | **1.979** | ±0.129 | `ops/us` | 1.00x | 100.0% | 835.81 | 1090.00 |
| `CreditApproval` | **`Generated Java`** | `Direct` | default | 2 | **2.779** | ±0.195 | `ops/us` | 1.40x | 70.2% | 849.15 | 1484.00 |
| `CreditApproval` | **`Generated Java`** | `Direct` | default | 4 | **3.205** | ±0.297 | `ops/us` | 1.62x | 40.5% | 833.04 | 1621.00 |
| `CreditApproval` | **`Generated Java`** | `Direct` | default | 8 | **4.126** | ±0.727 | `ops/us` | 2.09x | 26.1% | 840.21 | 2127.00 |
| `CreditApproval` | **`Generated Java`** | `End-to-End` | default | 1 | **1.498** | ±0.333 | `ops/us` | 1.00x | 100.0% | 913.15 | 1121.00 |
| `CreditApproval` | **`Generated Java`** | `End-to-End` | default | 2 | **2.342** | ±0.105 | `ops/us` | 1.56x | 78.2% | 926.48 | 1421.00 |
| `CreditApproval` | **`Generated Java`** | `End-to-End` | default | 4 | **2.769** | ±0.166 | `ops/us` | 1.85x | 46.2% | 886.48 | 1595.00 |
| `CreditApproval` | **`Generated Java`** | `End-to-End` | default | 8 | **3.558** | ±0.360 | `ops/us` | 2.38x | 29.7% | 890.00 | 2109.00 |
| `CreditApproval` | `Interpreter` | `Core` | default | 1 | **0.454** | ±0.138 | `ops/us` | 1.00x | 100.0% | 3199.62 | 1077.00 |
| `CreditApproval` | `Interpreter` | `Core` | default | 2 | **0.760** | ±0.023 | `ops/us` | 1.67x | 83.7% | 3199.62 | 1617.00 |
| `CreditApproval` | `Interpreter` | `Core` | default | 4 | **0.896** | ±0.021 | `ops/us` | 1.97x | 49.3% | 3186.28 | 1900.00 |
| `CreditApproval` | `Interpreter` | `Core` | default | 8 | **1.258** | ±0.028 | `ops/us` | 2.77x | 34.6% | 3172.95 | 2255.00 |
| `Originations` | **`Generated Java`** | `Adapter` | default | 1 | **1.201** | ±0.035 | `ops/us` | 1.00x | 100.0% | 1840.01 | 1142.00 |
| `Originations` | **`Generated Java`** | `Adapter` | default | 2 | **1.755** | ±0.073 | `ops/us` | 1.46x | 73.1% | 1866.68 | 1602.00 |
| `Originations` | **`Generated Java`** | `Adapter` | default | 4 | **2.133** | ±0.060 | `ops/us` | 1.78x | 44.4% | 1853.34 | 2175.00 |
| `Originations` | **`Generated Java`** | `Adapter` | default | 8 | **2.694** | ±0.621 | `ops/us` | 2.24x | 28.0% | 1840.01 | 2243.00 |
| `Originations` | **`Generated Java`** | `Direct` | default | 1 | **1.303** | ±0.160 | `ops/us` | 1.00x | 100.0% | 1840.01 | 1193.00 |
| `Originations` | **`Generated Java`** | `Direct` | default | 2 | **1.785** | ±0.050 | `ops/us` | 1.37x | 68.5% | 1853.34 | 1754.00 |
| `Originations` | **`Generated Java`** | `Direct` | default | 4 | **2.122** | ±0.082 | `ops/us` | 1.63x | 40.7% | 1853.34 | 2199.00 |
| `Originations` | **`Generated Java`** | `Direct` | default | 8 | **2.476** | ±0.247 | `ops/us` | 1.90x | 23.8% | 1840.01 | 2396.00 |
| `Originations` | **`Generated Java`** | `End-to-End` | default | 1 | **1.035** | ±0.124 | `ops/us` | 1.00x | 100.0% | 1936.01 | 1157.00 |
| `Originations` | **`Generated Java`** | `End-to-End` | default | 2 | **1.428** | ±0.030 | `ops/us` | 1.38x | 69.0% | 1928.01 | 1549.00 |
| `Originations` | **`Generated Java`** | `End-to-End` | default | 4 | **1.680** | ±0.053 | `ops/us` | 1.62x | 40.6% | 1911.37 | 1919.00 |
| `Originations` | **`Generated Java`** | `End-to-End` | default | 8 | **1.956** | ±0.433 | `ops/us` | 1.89x | 23.6% | 1903.37 | 2158.00 |
| `Originations` | `Interpreter` | `Core` | default | 1 | **0.215** | ±0.034 | `ops/us` | 1.00x | 100.0% | 7167.72 | 943.00 |
| `Originations` | `Interpreter` | `Core` | default | 2 | **0.343** | ±0.010 | `ops/us` | 1.59x | 79.7% | 7167.82 | 1448.00 |
| `Originations` | `Interpreter` | `Core` | default | 4 | **0.404** | ±0.012 | `ops/us` | 1.88x | 47.0% | 7167.72 | 1837.00 |
| `Originations` | `Interpreter` | `Core` | default | 8 | **0.461** | ±0.044 | `ops/us` | 2.14x | 26.8% | 7167.72 | 2100.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Adapter` | default | 1 | **1.367** | ±0.393 | `ops/us` | 1.00x | 100.0% | 1245.74 | 987.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Adapter` | default | 2 | **2.125** | ±0.039 | `ops/us` | 1.55x | 77.7% | 1219.07 | 1320.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Adapter` | default | 4 | **2.490** | ±0.054 | `ops/us` | 1.82x | 45.5% | 1232.40 | 1690.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Adapter` | default | 8 | **3.272** | ±0.335 | `ops/us` | 2.39x | 29.9% | 1219.07 | 2142.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Direct` | default | 1 | **1.615** | ±0.090 | `ops/us` | 1.00x | 100.0% | 1232.40 | 1111.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Direct` | default | 2 | **2.160** | ±0.024 | `ops/us` | 1.34x | 66.9% | 1232.40 | 1431.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Direct` | default | 4 | **2.493** | ±0.168 | `ops/us` | 1.54x | 38.6% | 1232.40 | 1445.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Direct` | default | 8 | **3.520** | ±0.098 | `ops/us` | 2.18x | 27.2% | 1205.74 | 2115.00 |
| `RankedLoanProducts` | **`Generated Java`** | `End-to-End` | default | 1 | **1.332** | ±0.126 | `ops/us` | 1.00x | 100.0% | 1301.74 | 999.00 |
| `RankedLoanProducts` | **`Generated Java`** | `End-to-End` | default | 2 | **2.039** | ±0.243 | `ops/us` | 1.53x | 76.6% | 1301.74 | 1218.00 |
| `RankedLoanProducts` | **`Generated Java`** | `End-to-End` | default | 4 | **2.145** | ±0.078 | `ops/us` | 1.61x | 40.3% | 1288.40 | 1405.00 |
| `RankedLoanProducts` | **`Generated Java`** | `End-to-End` | default | 8 | **2.804** | ±0.136 | `ops/us` | 2.11x | 26.3% | 1261.74 | 1984.00 |
| `RankedLoanProducts` | `Interpreter` | `Core` | default | 1 | **0.259** | ±0.009 | `ops/us` | 1.00x | 100.0% | 7248.76 | 1009.00 |
| `RankedLoanProducts` | `Interpreter` | `Core` | default | 2 | **0.378** | ±0.010 | `ops/us` | 1.46x | 73.0% | 7262.09 | 1335.00 |
| `RankedLoanProducts` | `Interpreter` | `Core` | default | 4 | **0.379** | ±0.012 | `ops/us` | 1.46x | 36.6% | 7262.09 | 1700.00 |
| `RankedLoanProducts` | `Interpreter` | `Core` | default | 8 | **0.514** | ±0.018 | `ops/us` | 1.99x | 24.9% | 7235.42 | 2065.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Adapter` | default | 1 | **0.011** | ±0.000 | `ops/ns` | 1.00x | 100.0% | 208.00 | 1202.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Adapter` | default | 2 | **0.016** | ±0.001 | `ops/ns` | 1.48x | 73.8% | 208.00 | 1569.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Adapter` | default | 4 | **0.017** | ±0.002 | `ops/ns` | 1.58x | 39.4% | 208.00 | 1720.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Adapter` | default | 8 | **0.019** | ±0.001 | `ops/ns` | 1.76x | 21.9% | 208.00 | 2265.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Direct` | default | 1 | **0.011** | ±0.001 | `ops/ns` | 1.00x | 100.0% | 208.00 | 1283.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Direct` | default | 2 | **0.017** | ±0.000 | `ops/ns` | 1.53x | 76.5% | 208.00 | 1632.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Direct` | default | 4 | **0.020** | ±0.002 | `ops/ns` | 1.75x | 43.7% | 208.00 | 1954.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Direct` | default | 8 | **0.021** | ±0.001 | `ops/ns` | 1.89x | 23.6% | 208.00 | 2372.00 |
| `ScalarArithmetic` | `Harness` | `Control` | default | 1 | **1.668** | ±0.085 | `ops/ns` | 1.00x | 100.0% | 0.00 | - |
| `ScalarArithmetic` | `Harness` | `Control` | default | 2 | **2.525** | ±0.113 | `ops/ns` | 1.51x | 75.7% | 0.00 | - |
| `ScalarArithmetic` | `Harness` | `Control` | default | 4 | **3.584** | ±0.090 | `ops/ns` | 2.15x | 53.7% | 0.00 | - |
| `ScalarArithmetic` | `Harness` | `Control` | default | 8 | **3.669** | ±0.175 | `ops/ns` | 2.20x | 27.5% | 0.00 | - |
| `ScalarArithmetic` | `Interpreter` | `Core` | default | 1 | **0.005** | ±0.000 | `ops/ns` | 1.00x | 100.0% | 624.00 | 1535.00 |
| `ScalarArithmetic` | `Interpreter` | `Core` | default | 2 | **0.007** | ±0.000 | `ops/ns` | 1.50x | 75.0% | 624.00 | 1891.00 |
| `ScalarArithmetic` | `Interpreter` | `Core` | default | 4 | **0.009** | ±0.001 | `ops/ns` | 1.79x | 44.8% | 624.00 | 2501.00 |
| `ScalarArithmetic` | `Interpreter` | `Core` | default | 8 | **0.008** | ±0.001 | `ops/ns` | 1.59x | 19.9% | 624.00 | 2554.00 |
| `TrafficViolation` | **`Generated Java`** | `Adapter` | default | 1 | **5.717** | ±0.460 | `ops/us` | 1.00x | 100.0% | 444.27 | 1353.00 |
| `TrafficViolation` | **`Generated Java`** | `Adapter` | default | 2 | **8.590** | ±0.712 | `ops/us` | 1.50x | 75.1% | 444.27 | 1875.00 |
| `TrafficViolation` | **`Generated Java`** | `Adapter` | default | 4 | **10.293** | ±1.108 | `ops/us` | 1.80x | 45.0% | 444.27 | 2104.00 |
| `TrafficViolation` | **`Generated Java`** | `Adapter` | default | 8 | **9.157** | ±0.483 | `ops/us` | 1.60x | 20.0% | 444.27 | 2330.00 |
| `TrafficViolation` | **`Generated Java`** | `Direct` | default | 1 | **6.395** | ±0.125 | `ops/us` | 1.00x | 100.0% | 444.27 | 1415.00 |
| `TrafficViolation` | **`Generated Java`** | `Direct` | default | 2 | **8.506** | ±0.629 | `ops/us` | 1.33x | 66.5% | 444.27 | 1786.00 |
| `TrafficViolation` | **`Generated Java`** | `Direct` | default | 4 | **10.870** | ±0.964 | `ops/us` | 1.70x | 42.5% | 444.27 | 2286.00 |
| `TrafficViolation` | **`Generated Java`** | `Direct` | default | 8 | **9.906** | ±0.922 | `ops/us` | 1.55x | 19.4% | 444.27 | 2269.00 |
| `TrafficViolation` | **`Generated Java`** | `End-to-End` | default | 1 | **3.990** | ±0.100 | `ops/us` | 1.00x | 100.0% | 484.27 | 1271.00 |
| `TrafficViolation` | **`Generated Java`** | `End-to-End` | default | 2 | **6.676** | ±0.143 | `ops/us` | 1.67x | 83.7% | 484.27 | 1544.00 |
| `TrafficViolation` | **`Generated Java`** | `End-to-End` | default | 4 | **7.605** | ±0.119 | `ops/us` | 1.91x | 47.6% | 476.27 | 1936.00 |
| `TrafficViolation` | **`Generated Java`** | `End-to-End` | default | 8 | **7.067** | ±0.234 | `ops/us` | 1.77x | 22.1% | 484.27 | 2073.00 |
| `TrafficViolation` | `Interpreter` | `Core` | default | 1 | **0.961** | ±0.025 | `ops/us` | 1.00x | 100.0% | 2514.80 | 1409.00 |
| `TrafficViolation` | `Interpreter` | `Core` | default | 2 | **1.425** | ±0.045 | `ops/us` | 1.48x | 74.2% | 2514.80 | 1812.00 |
| `TrafficViolation` | `Interpreter` | `Core` | default | 4 | **1.784** | ±0.034 | `ops/us` | 1.86x | 46.4% | 2514.80 | 2171.00 |
| `TrafficViolation` | `Interpreter` | `Core` | default | 8 | **1.681** | ±0.019 | `ops/us` | 1.75x | 21.9% | 2514.80 | 2293.00 |

---

## 2. ⏱️ Average Latency Benchmarks (`avgt` — Lower is Better ⬇️)

Measures elapsed execution duration per single decision call. (Under multi-threaded load, latency per thread increases due to CPU contention while total aggregate throughput scales).

| Model | Engine | Invocation Path | Scenario | Threads | Avg Latency Score [⬇️] | Error (±) | Unit | Latency Factor (vs 1T) | Memory (B/op) | GC Time (ms) |
| :--- | :--- | :--- | :--- | ---: | ---: | ---: | :--- | ---: | ---: | ---: |
| `CreditApproval` | **`Generated Java`** | `Adapter` | default | 1 | **0.463** | ±0.014 | `us/op` | 1.00x | 862.48 | 1114.00 |
| `CreditApproval` | **`Generated Java`** | `Adapter` | default | 2 | **0.727** | ±0.174 | `us/op` | 1.57x | 862.48 | 1448.00 |
| `CreditApproval` | **`Generated Java`** | `Adapter` | default | 4 | **0.914** | ±0.021 | `us/op` | 1.97x | 822.48 | 1794.00 |
| `CreditApproval` | **`Generated Java`** | `Adapter` | default | 8 | **1.963** | ±0.117 | `us/op` | 4.24x | 822.48 | 2063.00 |
| `CreditApproval` | **`Generated Java`** | `Direct` | default | 1 | **0.457** | ±0.030 | `us/op` | 1.00x | 849.15 | 1218.00 |
| `CreditApproval` | **`Generated Java`** | `Direct` | default | 2 | **0.614** | ±0.037 | `us/op` | 1.34x | 862.48 | 1624.00 |
| `CreditApproval` | **`Generated Java`** | `Direct` | default | 4 | **0.975** | ±0.068 | `us/op` | 2.13x | 843.96 | 1883.00 |
| `CreditApproval` | **`Generated Java`** | `Direct` | default | 8 | **2.085** | ±0.131 | `us/op` | 4.56x | 840.21 | 2153.00 |
| `CreditApproval` | **`Generated Java`** | `End-to-End` | default | 1 | **0.562** | ±0.022 | `us/op` | 1.00x | 926.48 | 1145.00 |
| `CreditApproval` | **`Generated Java`** | `End-to-End` | default | 2 | **0.729** | ±0.018 | `us/op` | 1.30x | 926.48 | 1505.00 |
| `CreditApproval` | **`Generated Java`** | `End-to-End` | default | 4 | **1.212** | ±0.058 | `us/op` | 2.16x | 897.04 | 1638.00 |
| `CreditApproval` | **`Generated Java`** | `End-to-End` | default | 8 | **2.706** | ±0.743 | `us/op` | 4.81x | 893.52 | 1854.00 |
| `CreditApproval` | `Interpreter` | `Core` | default | 1 | **1.749** | ±0.264 | `us/op` | 1.00x | 3199.62 | 1102.00 |
| `CreditApproval` | `Interpreter` | `Core` | default | 2 | **2.270** | ±0.043 | `us/op` | 1.30x | 3199.62 | 1527.00 |
| `CreditApproval` | `Interpreter` | `Core` | default | 4 | **3.451** | ±0.045 | `us/op` | 1.97x | 3192.26 | 1934.00 |
| `CreditApproval` | `Interpreter` | `Core` | default | 8 | **7.430** | ±0.201 | `us/op` | 4.25x | 3172.95 | 1997.00 |
| `Originations` | **`Generated Java`** | `Adapter` | default | 1 | **0.691** | ±0.028 | `us/op` | 1.00x | 1840.01 | 1265.00 |
| `Originations` | **`Generated Java`** | `Adapter` | default | 2 | **0.949** | ±0.017 | `us/op` | 1.37x | 1853.34 | 1708.00 |
| `Originations` | **`Generated Java`** | `Adapter` | default | 4 | **1.463** | ±0.030 | `us/op` | 2.12x | 1853.34 | 2227.00 |
| `Originations` | **`Generated Java`** | `Adapter` | default | 8 | **2.912** | ±0.229 | `us/op` | 4.21x | 1840.01 | 2599.00 |
| `Originations` | **`Generated Java`** | `Direct` | default | 1 | **0.685** | ±0.018 | `us/op` | 1.00x | 1840.01 | 1283.00 |
| `Originations` | **`Generated Java`** | `Direct` | default | 2 | **0.952** | ±0.033 | `us/op` | 1.39x | 1840.01 | 1757.00 |
| `Originations` | **`Generated Java`** | `Direct` | default | 4 | **1.479** | ±0.039 | `us/op` | 2.16x | 1840.01 | 2263.00 |
| `Originations` | **`Generated Java`** | `Direct` | default | 8 | **3.178** | ±0.704 | `us/op` | 4.64x | 1840.01 | 2453.00 |
| `Originations` | **`Generated Java`** | `End-to-End` | default | 1 | **0.864** | ±0.013 | `us/op` | 1.00x | 1936.01 | 1177.00 |
| `Originations` | **`Generated Java`** | `End-to-End` | default | 2 | **1.217** | ±0.047 | `us/op` | 1.41x | 1949.34 | 1558.00 |
| `Originations` | **`Generated Java`** | `End-to-End` | default | 4 | **1.857** | ±0.037 | `us/op` | 2.15x | 1894.73 | 2178.00 |
| `Originations` | **`Generated Java`** | `End-to-End` | default | 8 | **3.739** | ±0.279 | `us/op` | 4.33x | 1886.64 | 2351.00 |
| `Originations` | `Interpreter` | `Core` | default | 1 | **3.722** | ±0.470 | `us/op` | 1.00x | 7167.72 | 1085.00 |
| `Originations` | `Interpreter` | `Core` | default | 2 | **4.985** | ±0.146 | `us/op` | 1.34x | 7167.72 | 1490.00 |
| `Originations` | `Interpreter` | `Core` | default | 4 | **7.759** | ±0.162 | `us/op` | 2.08x | 7167.72 | 2019.00 |
| `Originations` | `Interpreter` | `Core` | default | 8 | **17.582** | ±4.163 | `us/op` | 4.72x | 7167.82 | 2063.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Adapter` | default | 1 | **0.611** | ±0.058 | `us/op` | 1.00x | 1245.74 | 1104.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Adapter` | default | 2 | **0.942** | ±0.267 | `us/op` | 1.54x | 1245.74 | 1342.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Adapter` | default | 4 | **1.257** | ±0.038 | `us/op` | 2.06x | 1219.07 | 1370.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Adapter` | default | 8 | **2.633** | ±0.176 | `us/op` | 4.31x | 1205.74 | 2175.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Direct` | default | 1 | **0.832** | ±0.241 | `us/op` | 1.00x | 1232.40 | 1017.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Direct` | default | 2 | **0.964** | ±0.059 | `us/op` | 1.16x | 1232.40 | 1302.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Direct` | default | 4 | **1.222** | ±0.032 | `us/op` | 1.47x | 1219.07 | 1394.00 |
| `RankedLoanProducts` | **`Generated Java`** | `Direct` | default | 8 | **2.591** | ±0.098 | `us/op` | 3.11x | 1219.07 | 2061.00 |
| `RankedLoanProducts` | **`Generated Java`** | `End-to-End` | default | 1 | **0.764** | ±0.027 | `us/op` | 1.00x | 1301.74 | 995.00 |
| `RankedLoanProducts` | **`Generated Java`** | `End-to-End` | default | 2 | **1.140** | ±0.072 | `us/op` | 1.49x | 1301.74 | 1238.00 |
| `RankedLoanProducts` | **`Generated Java`** | `End-to-End` | default | 4 | **1.446** | ±0.028 | `us/op` | 1.89x | 1275.07 | 1260.00 |
| `RankedLoanProducts` | **`Generated Java`** | `End-to-End` | default | 8 | **3.220** | ±0.336 | `us/op` | 4.22x | 1275.07 | 2036.00 |
| `RankedLoanProducts` | `Interpreter` | `Core` | default | 1 | **4.298** | ±0.222 | `us/op` | 1.00x | 7262.09 | 1000.00 |
| `RankedLoanProducts` | `Interpreter` | `Core` | default | 2 | **6.856** | ±1.528 | `us/op` | 1.60x | 7248.76 | 1212.00 |
| `RankedLoanProducts` | `Interpreter` | `Core` | default | 4 | **8.053** | ±0.108 | `us/op` | 1.87x | 7235.42 | 1342.00 |
| `RankedLoanProducts` | `Interpreter` | `Core` | default | 8 | **16.696** | ±1.219 | `us/op` | 3.88x | 7262.09 | 1915.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Adapter` | default | 1 | **117.099** | ±39.831 | `ns/op` | 1.00x | 208.00 | 1038.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Adapter` | default | 2 | **148.487** | ±10.970 | `ns/op` | 1.27x | 208.00 | 1594.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Adapter` | default | 4 | **202.684** | ±3.100 | `ns/op` | 1.73x | 208.00 | 1874.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Adapter` | default | 8 | **466.026** | ±16.533 | `ns/op` | 3.98x | 208.00 | 2122.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Direct` | default | 1 | **91.347** | ±6.198 | `ns/op` | 1.00x | 208.00 | 1245.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Direct` | default | 2 | **137.063** | ±4.764 | `ns/op` | 1.50x | 208.00 | 1591.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Direct` | default | 4 | **188.744** | ±4.197 | `ns/op` | 2.07x | 208.00 | 2050.00 |
| `ScalarArithmetic` | **`Generated Java`** | `Direct` | default | 8 | **417.193** | ±15.034 | `ns/op` | 4.57x | 208.00 | 2433.00 |
| `ScalarArithmetic` | `Harness` | `Control` | default | 1 | **0.656** | ±0.027 | `ns/op` | 1.00x | 0.00 | - |
| `ScalarArithmetic` | `Harness` | `Control` | default | 2 | **0.960** | ±0.148 | `ns/op` | 1.46x | 0.00 | - |
| `ScalarArithmetic` | `Harness` | `Control` | default | 4 | **1.125** | ±0.017 | `ns/op` | 1.72x | 0.00 | - |
| `ScalarArithmetic` | `Harness` | `Control` | default | 8 | **2.144** | ±0.065 | `ns/op` | 3.27x | 0.00 | - |
| `ScalarArithmetic` | `Interpreter` | `Core` | default | 1 | **223.257** | ±24.906 | `ns/op` | 1.00x | 624.00 | 1464.00 |
| `ScalarArithmetic` | `Interpreter` | `Core` | default | 2 | **393.429** | ±75.846 | `ns/op` | 1.76x | 624.00 | 1805.00 |
| `ScalarArithmetic` | `Interpreter` | `Core` | default | 4 | **435.121** | ±8.908 | `ns/op` | 1.95x | 624.00 | 2323.00 |
| `ScalarArithmetic` | `Interpreter` | `Core` | default | 8 | **999.684** | ±21.018 | `ns/op` | 4.48x | 624.00 | 2836.00 |
| `TrafficViolation` | **`Generated Java`** | `Adapter` | default | 1 | **0.189** | ±0.005 | `us/op` | 1.00x | 444.27 | 1263.00 |
| `TrafficViolation` | **`Generated Java`** | `Adapter` | default | 2 | **0.271** | ±0.017 | `us/op` | 1.44x | 444.27 | 1608.00 |
| `TrafficViolation` | **`Generated Java`** | `Adapter` | default | 4 | **0.393** | ±0.033 | `us/op` | 2.08x | 444.27 | 2089.00 |
| `TrafficViolation` | **`Generated Java`** | `Adapter` | default | 8 | **0.883** | ±0.012 | `us/op` | 4.69x | 444.27 | 2251.00 |
| `TrafficViolation` | **`Generated Java`** | `Direct` | default | 1 | **0.189** | ±0.014 | `us/op` | 1.00x | 444.27 | 1427.00 |
| `TrafficViolation` | **`Generated Java`** | `Direct` | default | 2 | **0.372** | ±0.082 | `us/op` | 1.97x | 444.27 | 1516.00 |
| `TrafficViolation` | **`Generated Java`** | `Direct` | default | 4 | **0.391** | ±0.009 | `us/op` | 2.07x | 444.27 | 2229.00 |
| `TrafficViolation` | **`Generated Java`** | `Direct` | default | 8 | **0.794** | ±0.066 | `us/op` | 4.20x | 444.27 | 2424.00 |
| `TrafficViolation` | **`Generated Java`** | `End-to-End` | default | 1 | **0.287** | ±0.008 | `us/op` | 1.00x | 484.27 | 1305.00 |
| `TrafficViolation` | **`Generated Java`** | `End-to-End` | default | 2 | **0.582** | ±0.176 | `us/op` | 2.03x | 476.27 | 1331.00 |
| `TrafficViolation` | **`Generated Java`** | `End-to-End` | default | 4 | **0.513** | ±0.039 | `us/op` | 1.79x | 476.27 | 1922.00 |
| `TrafficViolation` | **`Generated Java`** | `End-to-End` | default | 8 | **1.080** | ±0.049 | `us/op` | 3.77x | 484.27 | 2034.00 |
| `TrafficViolation` | `Interpreter` | `Core` | default | 1 | **1.242** | ±0.035 | `us/op` | 1.00x | 2514.80 | 1279.00 |
| `TrafficViolation` | `Interpreter` | `Core` | default | 2 | **2.613** | ±0.271 | `us/op` | 2.10x | 2514.80 | 1478.00 |
| `TrafficViolation` | `Interpreter` | `Core` | default | 4 | **2.229** | ±0.045 | `us/op` | 1.80x | 2514.80 | 2371.00 |
| `TrafficViolation` | `Interpreter` | `Core` | default | 8 | **4.589** | ±0.095 | `us/op` | 3.70x | 2514.80 | 2129.00 |

---

## Metric & Column Legend

| Column | Description |
| :--- | :--- |
| **Model** | Evaluated DMN decision model (`CreditApproval`, `TrafficViolation`, `ScalarArithmetic`, `Originations`, `RankedLoanProducts`). |
| **Engine** | Target engine backend: **`Generated Java`** (`dmn-generator-java` AOT compiled Java bytecode) vs **`Interpreter`** (`DmnRuntime` deterministic IR interpreter) vs **`Harness`** (Control baseline). |
| **Invocation Path** | **`Direct`**: Strongly-typed direct interface call (`GeneratedDecisionEngine`) with pre-allocated slot inputs.<br>**`Core`**: Direct interpreter execution on pre-allocated slot inputs.<br>**`Adapter`**: Invocation through dynamic reflective wrapper (`Method.invoke`).<br>**`End-to-End`**: Named map input translation + decision evaluation + output unmarshalling.<br>**`Control`**: Test harness iteration and payload selection baseline. |
| **Scenario** | Workload scenario variant (e.g. `default`, `validBaseline`, `singleViolation`, etc.). |
| **Threads** | Number of concurrent worker threads running the benchmark simultaneously. |
| **Throughput Score [⬆️]** | Rate of evaluations processed per time unit (`ops/µs` = million ops/sec, `ops/ns` = billion ops/sec). **Higher is Better.** |
| **Avg Latency Score [⬇️]** | Elapsed duration per evaluation (`µs/op`, `ns/op`). **Lower is Better.** |
| **Speedup (vs 1T)** | Ratio of multi-threaded throughput to single-threaded baseline ($T_N / T_1$). |
| **Parallel Efficiency** | Multi-threaded scaling efficiency ($(\text{Speedup} / N) \times 100\%$). Ideal linear scaling is $100\%$. |
| **Latency Factor (vs 1T)** | Ratio of multi-threaded latency to single-threaded latency ($L_N / L_1$). Reflects queuing/CPU contention under load. |
| **Memory (B/op)** | Normalized heap memory allocation in **Bytes per Operation** measured by JMH GC profiler. Lower is better; near-zero indicates zero-GC hot execution path. |
| **GC Time (ms)** | Total JVM garbage collection pause time accumulated across measurement iterations. |

## Environment & Hardware Metadata

| Parameter | Value |
| :--- | :--- |
| **Processor / CPU** | `Intel(R) Core(TM) i7-10510U CPU @ 1.80GHz` |
| **Physical Cores** | `4 cores` |
| **Logical / Virtual Cores** | `8 threads` |
| **System Memory (RAM)** | `15.78 GB` |
| **Computer Model** | `LENOVO 20RA001BMZ` |
| **Operating System** | `Microsoft Windows 11 Pro 64-bit (Version 10.0.26200)` |
| **Java Runtime** | `openjdk version "25.0.2" 2026-01-20 LTS` |
| **Git Commit** | `a1f8afcecbebff1b2f5c7e00e77b99d13b026686` (dirty) |
| **Benchmark Protocol** | `3 forks, 5x5s warmup, 5x10s measurement, threads [1,2,4,8], profiler: gc` |

<details><summary>Raw Environment Properties</summary>

```properties
timestamp_utc=2026-09-06T03:44:32.889724+00:00
git_commit=a1f8afcecbebff1b2f5c7e00e77b99d13b026686
git_status=dirty
cpu_processor=Intel(R) Core(TM) i7-10510U CPU @ 1.80GHz
physical_cores=4
logical_processors=8
memory_total_gb=15.78 GB
computer_model=LENOVO 20RA001BMZ
os=Microsoft Windows 11 Pro 64-bit (Version 10.0.26200)
java=openjdk version "25.0.2" 2026-01-20 LTS
threads=1,2,4,8
forks=3
warmup=5x5s
measurement=5x10s
profiler=gc
```

</details>

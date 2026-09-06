# Chapter 19 — Future Generators [FUTURE]

<!-- generated-toc:start -->
## Table of contents

- [19.1 Purpose](#contents-section-1)
- [19.2 Backend Design Principle](#contents-section-2)
- [19.3 Backend Module Architecture](#contents-section-3)
- [19.4 Generator SPI](#contents-section-4)
- [19.5 Backend Capability Model](#contents-section-5)
- [19.6 Rust Generator](#contents-section-6)
- [19.7 Go Generator](#contents-section-7)
- [19.8 Spark SQL Generator](#contents-section-8)
- [19.9 Spark Optimization](#contents-section-9)
- [19.10 LLVM Generator](#contents-section-10)
- [19.11 WebAssembly Generator](#contents-section-11)
- [19.12 Backend Optimization Layer](#contents-section-12)
- [19.13 Backend Validation](#contents-section-13)
- [19.14 Backend Registry](#contents-section-14)
- [19.15 Custom Backend Development](#contents-section-15)
- [19.16 Example: Adding Python Backend](#contents-section-16)
- [19.17 Generated Artifact Metadata](#contents-section-17)
- [19.18 Performance Comparison](#contents-section-18)
- [19.19 Long-Term Vision](#contents-section-19)
- [19.20 Summary](#contents-section-20)
<!-- generated-toc:end -->

!!! note "Backend Status Baseline"
    The primary production Java generator (`dmn-generator-java`) is fully implemented in the main reactor. The distributed Spark SQL generator (`dmn-generator-sparksql`) and gRPC adapter (`dmn-grpc`) are implemented in the incubating profile (`-Pincubator`). The remaining language backends (Rust, Go, LLVM, WebAssembly, and Python) are planned extensions following the decoupled Generator SPI.

<a id="contents-section-1"></a>
## 19.1 Purpose

The DMN Compiler Toolkit is designed as a **multi-backend compiler
platform**.

The primary architectural goal is that all future targets consume the
same optimized Runtime IR.

A new backend must not require:

-   changes to XML parsing
-   changes to FEEL parsing
-   changes to semantic analysis
-   changes to optimization passes

The architecture:

```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    ir["Runtime IR"] --> java["Java Generator"]
    ir --> rust["Rust Generator"]
    ir --> spark["Spark SQL Generator"]
    java --> jvm["JVM App"]
    rust --> native["Native App"]
    spark --> db["Databricks / Spark"]
```
------------------------------------------------------------------------
<a id="contents-section-2"></a>
## 19.2 Backend Design Principle

### BG-001 --- Runtime IR Is the Universal Contract

Every backend consumes **only Runtime IR**.
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    subgraph Allowed["Universal Contract"]
        ir["Runtime IR"] --> bg["Target Generator (e.g. Rust)"]
    end
    subgraph Forbidden["Forbidden Dependencies"]
        xml["DMN XML"] -.->|Never| bg
        feel["FEEL AST"] -.->|Never| bg
        sem["Semantic Model"] -.->|Never| bg
    end
```
------------------------------------------------------------------------

Example: Adding a Rust backend

Requires:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    ir["Runtime IR"] --> rust["Rust Generator"]
```
Does not require:
-   XML changes
-   FEEL changes
-   Compiler frontend changes

------------------------------------------------------------------------
<a id="contents-section-3"></a>
## 19.3 Backend Module Architecture

Recommended Maven structure:
```text
dmn-generator-java
dmn-generator-rust
dmn-generator-go
dmn-generator-spark
dmn-generator-llvm
dmn-generator-wasm
```
------------------------------------------------------------------------
Dependency rule:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    ir["Runtime IR"] --> java["Java Generator"]
    ir --> rust["Rust Generator"]
    ir --> spark["Spark SQL Generator"]
```
Forbidden dependency:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart LR
    rust["Rust Generator"] -. "forbidden dependency" .-> feel["FEEL Parser"]
```
------------------------------------------------------------------------
<a id="contents-section-4"></a>
## 19.4 Generator SPI

All generators implement the same interface:
```java
public interface CodeGenerator {
    String target();

    GeneratedArtifact generate(
        RuntimeModel model
    );
}
```
------------------------------------------------------------------------
Example:
Java:
```java
public class JavaGenerator
implements CodeGenerator {
    public String target() {
        return "java";
    }
}
```
------------------------------------------------------------------------
Rust:
```java
public class RustGenerator
implements CodeGenerator {
    public String target() {
        return "rust";
    }
}
```
------------------------------------------------------------------------
<a id="contents-section-5"></a>
## 19.5 Backend Capability Model

Not every backend supports every feature:
```java
public interface BackendCapabilities {
    boolean supportsContexts();
    boolean supportsDates();
    boolean supportsCustomFunctions();
}
```
------------------------------------------------------------------------

Compiler validates capabilities before emission:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    ir["Runtime IR"] --> check{"Backend Capability Check"}
    check -- Supported --> gen["Emit Target Code"]
    check -- Unsupported Feature --> diag["Emit Diagnostic Warning / Error"]
```
------------------------------------------------------------------------

Example: Spark SQL limitation

A recursive FEEL function may not be supported by distributed SQL engines.

------------------------------------------------------------------------
<a id="contents-section-6"></a>
## 19.6 Rust Generator

Generate native high-performance decision engines.

Architecture:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    ir["Runtime IR"] --> gen["Rust Generator"]
    gen --> src["Rust Source Code"]
    src --> bin["Native Binary / WASM"]
```
------------------------------------------------------------------------
Generated example:
Runtime IR:
```text
LOAD speed
LOAD 100
COMPARE_GT
```
Rust:
```rust
pub fn evaluate(
    input: &TrafficInput
) -> Penalty {
    if input.speed > 100 {
        Penalty::High
    } else {
        Penalty::Low
    }
}
```
------------------------------------------------------------------------
Advantages:
-   native execution
-   low latency
-   zero JVM overhead
-   embedded systems
-   WASM compatibility
------------------------------------------------------------------------
<a id="contents-section-7"></a>
## 19.7 Go Generator

Generate cloud-native, statically compiled decision services with minimal memory footprint.

Architecture:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    ir["Runtime IR"] --> gen["Go Generator"]
    gen --> src["Go Source (.go)"]
    src --> srv["Go Microservice / Static Binary"]
```
------------------------------------------------------------------------
Generated example:
Runtime IR:
```text
LOAD speed
LOAD 100
COMPARE_GT
```
Go:
```go
package decision

type TrafficInput struct {
    Speed int
    Age   int
}
type Penalty int
const (
    Low Penalty = iota
    Medium
    High
)

func Evaluate(input TrafficInput) Penalty {
    if input.Speed > 100 {
        return High
    }
    return Low
}
```
------------------------------------------------------------------------
Advantages:

-   statically linked single binary deployment
-   minimal memory footprint and instant cold starts
-   idiomatic cloud-native microservices
-   serverless functions (AWS Lambda / Google Cloud Functions)
-   edge execution
------------------------------------------------------------------------
<a id="contents-section-8"></a>
## 19.8 Spark SQL Generator

Generate distributed decision execution pipelines for massive tabular datasets and data warehouses.

Architecture:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    ir["Runtime IR"] --> gen["Spark SQL Generator"]
    gen --> sql["Catalyst Expression Tree"]
    sql --> tungsten["Tungsten Whole-Stage CodeGen"]
    tungsten --> spark["Distributed Spark Tasks"]
```
------------------------------------------------------------------------
Generated example:
DMN Decision:
```text
if speed > 100
then "HIGH"
else "LOW"
```
Generated Spark SQL query:
```sql
SELECT
    transaction_id,
    speed,
    CASE
        WHEN speed > 100 THEN 'HIGH'
        ELSE 'LOW'
    END AS penalty_category
FROM traffic_events
```
------------------------------------------------------------------------
Advantages:

-   distributed execution across clusters (Databricks, EMR, BigQuery)
-   processes millions of records per second without per-row JVM object allocation
-   native integration with Spark DataFrame and Catalyst optimization pipelines

------------------------------------------------------------------------
<a id="contents-section-9"></a>
## 19.9 Spark Optimization

Runtime IR enables advanced distributed query optimizations:

### Predicate Pushdown

Filter upstream partitions and parquet row groups before evaluating decisions:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart LR
    subgraph Before["Without Predicate Pushdown"]
        scan1["Read All Partitions"] --> dec1["Evaluate Decision"] --> flt1["Filter: active = true"]
    end
    subgraph After["With Predicate Pushdown"]
        scan2["Read Filtered Partitions"] --> flt2["Filter: active = true"] --> dec2["Evaluate Decision"]
    end
```
------------------------------------------------------------------------
### Column Pruning
Only required decision inputs are loaded into memory:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart LR
    tbl["Parquet Table<br/>(50 columns)"] --> pruner["Column Pruner"]
    pruner --> read["Read 2 columns:<br/>speed, age"]
    read --> dec["Decision Kernel"]
```
------------------------------------------------------------------------
### Vectorized Execution
Emits columnar batch operations targeting Spark Tungsten and Apache Arrow memory:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    subgraph RowOriented["Row-by-Row Interpretation (Slow)"]
        r1["Row 0"] --> r2["Row 1"] --> r3["Row 2"]
    end
    subgraph ColumnBatch["Vectorized ColumnBatch (Fast)"]
        cb["ColumnBatch Buffer<br/>(4,096 rows in off-heap memory)"] --> ve["SIMD Columnar Decision Kernel"]
    end
```
------------------------------------------------------------------------
<a id="contents-section-10"></a>
## 19.10 LLVM Generator

Generate bare-metal, native machine code via LLVM compiler infrastructure.

Architecture:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    ir["Runtime IR"] --> gen["LLVM IR Generator"]
    gen --> llvm["LLVM IR (.ll Module)"]
    llvm --> opt["LLVM Optimizer (opt -O3)"]
    opt --> llc["LLVM CodeGen (llc)"]
    llc --> mc["Bare-Metal Machine Code (.o / .so)"]
```
------------------------------------------------------------------------
Generated example:
Runtime IR:
```text
LOAD speed
LOAD 100
COMPARE_GT
```
LLVM IR:
```llvm
define i32 @evaluate_penalty(i32 %speed) {
entry:
  %cmp = icmp sgt i32 %speed, 100
  %res = select i1 %cmp, i32 3, i32 1
  ret i32 %res
}
```
------------------------------------------------------------------------
Benefits:

-   sub-nanosecond execution latency
-   hardware-specific CPU instruction scheduling (AVX-512, NEON)
-   zero runtime dependency or virtual machine overhead

------------------------------------------------------------------------
<a id="contents-section-11"></a>
## 19.11 WebAssembly Generator

Execute decisions in client-side browsers, edge workers, and sandboxed runtimes.

Architecture:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    ir["Runtime IR"] --> gen["WASM Generator"]
    gen --> wasm[".wasm Module"]
    wasm --> browser["Browser (Zero-Latency Web UI)"]
    wasm --> edge["Edge Workers (Cloudflare / Fastly)"]
    wasm --> embed["Embedded Host (Wasmtime / Wasmer)"]
```
------------------------------------------------------------------------
Advantages:

-   instant startup with zero installation
-   deterministic and sandboxed security boundary
-   near-native execution performance across platforms

------------------------------------------------------------------------
<a id="contents-section-12"></a>
## 19.12 Backend Optimization Layer

Each backend performs target-specific instruction mappings while preserving exact mathematical and logical semantics:

| Runtime IR Opcode | Java | Rust | Spark SQL | LLVM IR |
| :--- | :--- | :--- | :--- | :--- |
| `COMPARE_GT` | `>` | `>` | `>` | `icmp sgt` |
| `ADD` | `+` | `+` | `+` | `add` |
| `MULTIPLY` | `*` | `*` | `*` | `mul` |
| `SUBTRACT` | `-` | `-` | `-` | `sub` |

------------------------------------------------------------------------
<a id="contents-section-13"></a>
## 19.13 Backend Validation

Every backend target must satisfy 100% semantic equivalence against the reference interpreter:

### Semantic Equivalence Tests
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart LR
    inp["Input Data<br/>speed: 120"] --> ref["Reference Interpreter"]
    inp --> gen["Target Generator (Rust / Go / Spark / LLVM)"]
    ref --> cmp{"Assert Equivalent<br/>Result: HIGH"}
    gen --> cmp
```
------------------------------------------------------------------------
<a id="contents-section-14"></a>
## 19.14 Backend Registry

Decoupled SPI mechanism for pluggable generator registration:
```java
public final class BackendRegistry {
    private static final Map<String, CodeGenerator> GENERATORS = new ConcurrentHashMap<>();
    public static void register(CodeGenerator generator) {
        GENERATORS.put(generator.target(), generator);
    }

    public static CodeGenerator get(String target) {
        CodeGenerator gen = GENERATORS.get(target);
        if (gen == null) {
            throw new IllegalArgumentException("Unknown backend target: " + target);
        }
        return gen;
    }
}
```
------------------------------------------------------------------------
<a id="contents-section-15"></a>
## 19.15 Custom Backend Development

A contributor creating a new backend implements four discrete stages:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart LR
    s1["Step 1<br/>Read RuntimeModel"] --> s2["Step 2<br/>Map Opcodes to Target"]
    s2 --> s3["Step 3<br/>Emit Artifact Source"]
    s3 --> s4["Step 4<br/>Run Compliance Tests"]
```
1. **Read RuntimeModel**: Consume the immutable Runtime IR graph.
2. **Map Opcodes**: Translate IR opcodes into target language constructs.
3. **Emit Artifact Source**: Output clean, compilable source code or binary files.
4. **Run Compliance Tests**: Validate against the Technology Compatibility Kit (TCK).

------------------------------------------------------------------------
<a id="contents-section-16"></a>
## 19.16 Example: Adding Python Backend

Adding a new target backend requires **zero modifications** to frontend parsing, FEEL AST construction, or semantic analysis:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    ir["Runtime IR (Unchanged)"] --> py["Python Generator"]
    py --> mod["Python Decision Module"]
```
Generated Python example:
```python
from dataclasses import dataclass
from enum import Enum

class Penalty(Enum):
    LOW = 1
    MEDIUM = 2
    HIGH = 3
@dataclass(frozen=True)
class TrafficInput:
    speed: int
    age: int

def evaluate(input_data: TrafficInput) -> Penalty:
    if input_data.speed > 100:
        return Penalty.HIGH
    return Penalty.LOW
```
------------------------------------------------------------------------
<a id="contents-section-17"></a>
## 19.17 Generated Artifact Metadata

Every generated artifact includes structured provenance metadata:
```json
{
  "compilerVersion": "1.0",
  "backend": "java",
  "model": "TrafficViolation",
  "runtimeIRChecksum": "a1b2c3d4e5f6",
  "timestamp": "2026-09-06T00:00:00Z"
}
```
Benefits:

-   traceability across build environments
-   runtime version compatibility checks
-   reproducible builds
------------------------------------------------------------------------
<a id="contents-section-18"></a>
## 19.18 Performance Comparison

| Backend | Typical Target Use Case | Latency Profile | Memory Overhead |
| :--- | :--- | :--- | :--- |
| **Java** | Enterprise backend microservices | Microseconds | Managed JVM heap |
| **Rust** | Ultra-low latency & embedded systems | Sub-microsecond | Zero runtime overhead |
| **Go** | Cloud microservices & Kubernetes | Microseconds | Minimal GC footprint |
| **Spark SQL** | Distributed big data analytics | Batch / Columnar | Off-heap column buffers |
| **LLVM** | Maximum bare-metal performance | Nanoseconds | Pure register / stack |
| **WASM** | Browser edge & sandboxed plugins | Near-native | Isolated linear memory |

------------------------------------------------------------------------
<a id="contents-section-19"></a>
## 19.19 Long-Term Vision

The DMN Compiler Toolkit follows the multi-target architectural paradigm established by modern compiler frameworks like LLVM:
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    subgraph LLVMCompiler["LLVM Compiler Architecture"]
        srcLang["C / C++ / Rust Source"] --> llvmIr["LLVM IR"]
        llvmIr --> llvmTargets["x86 / ARM / WASM Targets"]
    end

    subgraph DMNCompiler["DMN Compiler Architecture"]
        dmnLang["DMN XML / FEEL Models"] --> rir["Runtime IR"]
        rir --> dmnTargets["Java / Rust / Go / Spark / WASM"]
    end
```
------------------------------------------------------------------------
<a id="contents-section-20"></a>
## 19.20 Summary

The universal Runtime IR enables:

-   multiple target execution environments
-   complete language independence
-   backend innovation and specialized code generation
-   future extensibility without compiler pipeline regressions
```mermaid
%%{init: {'theme':'neutral'}}%%
flowchart TD
    dmn["DMN Models"] --> comp["Compiler Pipeline"]
    comp --> rir["Runtime IR"]
    rir --> jvm["JVM (Java)"]
    rir --> nat["Native (Rust / Go / LLVM)"]
    rir --> big["Analytics (Spark SQL)"]
    rir --> web["Edge / Web (WASM)"]
```

------------------------------------------------------------------------

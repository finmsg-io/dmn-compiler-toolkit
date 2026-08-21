# DMN Compiler Toolkit — Assets & Competitive Differentiators

This document details the complete asset catalog of the DMN Compiler Toolkit and explains the architectural innovations that make `dmn-generator-java` and the overall toolkit significantly faster, cleaner, and more modern than legacy DMN engines (e.g., Drools/Kie, Camunda, Trisotech, Red Hat Decision Manager).

---

## 1. Summary of Toolkit Assets

The toolkit is organized into 10 decoupled, single-responsibility Maven modules:

| Module | Category | Primary Function & Asset |
| --- | --- | --- |
| **`dmn-protobuf`** | Core Data Model | Canonical Protobuf schemas for DMN 1.5 definitions, FEEL AST, types, diagnostics, and semantic model sets. Provides immutable serialized artifacts across compiler stages. |
| **`dmn-frontend-xml`** | Frontend Reader/Writer | Namespace-aware, fast XML parser and writer. Supports DMN 1.2, 1.3, 1.4, and 1.5 XML vocabularies with QName preservation and source-location tracking. |
| **`dmn-feel-parser`** | Expression Parser | ANTLR4-based parser converting raw FEEL text into immutable Protobuf FEEL AST nodes with rich multi-error diagnostics. |
| **`dmn-semantic-analysis`** | Semantic Compiler | Symbol table management, type inference, constraint checking, reference resolution, and deterministic DRG dependency ordering (cycle detection, execution topological sorting). |
| **`dmn-runtime-ir`** | Intermediate Representation | Low-level, execution-focused Runtime IR with typed constant canonicalization, lexically scoped frame indexing, and IR optimization passes. |
| **`dmn-runtime`** | Interpreter | Deterministic, zero-reflection interpreter for Runtime IR; full specification correctness is not established. |
| **`dmn-compiler`** | Orchestration Facade | Public facade (`DmnCompiler`) and loader (`DmnModelLoader`) supporting resolver-independent multi-file DMN compilation (`DmnModelResolver`). |
| **`dmn-generator-java`** | Ahead-Of-Time (AOT) Code Generator | Generates readable, pure Java source code (`DmnJavaGenerator`) directly from Runtime IR for ultra-low-latency execution. |
| **`dmn-tck-runner`** | Conformance evidence | Strict runner for official CL2/CL3 cases; current verified case pass rate is 51.19%. |
| **`dmn-toolkit-parent`** | Build Infrastructure | Maven parent POM managing Java 21+ toolchain, dependencies, compiler options, and multi-module build lifecycle. |

---

## 2. Why `dmn-generator-java` is Faster, Modern, and Superior

Legacy DMN engines were designed 10–15 years ago around generic rule engines (e.g., RETE algorithm/Phreak) or dynamic XML/FEEL reflection interpreters. `dmn-generator-java` represents a modern compiler approach: **Ahead-of-Time (AOT) compilation to direct Java control flow**.

Below are the 5 core technical differentiators:

### 1. Zero-Walk AOT Execution (No AST Walking or Reflection at Runtime)
* **Legacy Engines**: Parse XML/FEEL at startup or per request, holding complex object graphs in memory and walking AST trees or executing rules via dynamic reflection on every execution.
* **`dmn-generator-java`**: Compiles DMN logic into pure, static Java methods. Execution consists solely of native `if/else` statements, direct arithmetic, and array index lookups. There is **zero XML parsing, zero FEEL parsing, zero AST walking, and zero reflection** during model evaluation.

### 2. Zero-Allocation Lexical Frame Indexing
* **Legacy Engines**: Instantiate new `HashMap` or `Context` objects at every nested expression, decision table rule, or function call, creating heavy Garbage Collection (GC) pressure in multi-threaded microservices.
* **`dmn-generator-java`**: Maps all input variables, decision outputs, local context entries, and function arguments to **dense integer slots** (`Object[]` or direct method arguments). Local variables reuse pre-calculated array offsets, keeping memory allocations near zero during evaluation.

### 3. Native JIT Compiler Friendly (HotSpot / GraalVM Optimization)
* **Legacy Engines**: Dynamic dispatch and deep reflection hinder JVM Just-In-Time (JIT) compilers (C2/Graal) from inlining calls or devirtualizing methods.
* **`dmn-generator-java`**: Emits plain, idiomatically clean Java code. HotSpot/GraalVM JIT engines can trivially inline generated decision logic, perform loop unrolling, and inline numerical calculations directly into native assembly CPU instructions.

### 4. Standalone & Lightweight (No Heavy Drools/Kie Dependency Bloat)
* **Legacy Engines**: Require importing hundreds of megabytes of legacy dependencies (`kie-api`, `drools-core`, `mvel`, `ecj` compiler, OSGi bundles), leading to slow application cold-starts and complex dependency conflicts.
* **`dmn-generator-java`**: Zero provider dependencies. Generated Java code depends only on standard Java standard library types (`java.math.BigDecimal`, `java.util.List`, `java.util.Map`). It can be embedded directly into microservices, AWS Lambda/Knative serverless functions, or compiled into native binaries via GraalVM `native-image`.

### 5. Conformance recovery and dual-backend evidence
* **`dmn-generator-java`**: The generator architecture exists, but strict evidence currently proves only
  **1,736/3,391 official CL2/CL3 cases** pass across both backends. No certification or full-parity claim
  is made.

---

## 3. Summary Comparison

| Metric / Differentiator | Legacy DMN Engines (Drools/Kie, Camunda) | DMN Compiler Toolkit (`dmn-generator-java`) |
| --- | --- | --- |
| **Execution Model** | Runtime AST interpretation or RETE rule engine | Ahead-of-Time (AOT) Java source generation |
| **Evaluation Overhead** | AST tree walking, dynamic reflection, string maps | Native Java control flow & indexed slot arrays |
| **Memory / GC Footprint** | High (creates temporary HashMaps per rule/node) | Extremely Low (zero-allocation array indexing) |
| **Dependencies** | Heavy (100MB+ Kie/Drools/MVEL/ECJ jars) | Lightweight (zero runtime framework dependencies) |
| **GraalVM Native Compatibility** | Difficult (requires extensive reflection configs) | Flawless (100% static Java code, zero reflection) |
| **OMG DMN conformance** | Product-specific | 1,736/3,391 CL2/CL3 cases currently pass (51.19%); not certified |

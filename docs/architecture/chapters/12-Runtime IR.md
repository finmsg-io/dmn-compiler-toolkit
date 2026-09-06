# Chapter 12 — Runtime IR [IMPLEMENTATION-ALIGNED]

<!-- generated-toc:start -->
## Table of contents

- [12.1 Purpose](#contents-section-1)
- [12.2 Design Goals](#contents-section-2)
- [12.3 Architectural Position](#contents-section-3)
- [12.4 Runtime IR Principles](#contents-section-4)
- [12.5 Runtime IR Package](#contents-section-5)
- [12.6 Runtime Model](#contents-section-6)
- [12.7 Runtime Decision](#contents-section-7)
- [12.8 Runtime Variable](#contents-section-8)
- [12.9 Runtime Expression](#contents-section-9)
- [12.10 Instruction Model](#contents-section-10)
- [12.11 Opcode Design](#contents-section-11)
- [12.12 Expression Graph vs Instruction Stream](#contents-section-12)
- [12.13 Execution Graph](#contents-section-13)
- [12.14 Constant Pool](#contents-section-14)
- [12.15 Runtime IR Serialization](#contents-section-15)
- [12.16 Example Transformation](#contents-section-16)
- [12.17 Runtime Execution Loop](#contents-section-17)
- [12.18 Runtime IR Optimization](#contents-section-18)
- [12.19 Code Generation](#contents-section-19)
- [12.20 Performance Considerations](#contents-section-20)
- [12.21 Thread Safety](#contents-section-21)
- [12.22 Testing Strategy](#contents-section-22)
- [12.23 Summary](#contents-section-23)
<!-- generated-toc:end -->


<a id="contents-section-1"></a>
## 12.1 Purpose

Implementation baseline as of August 2026: `dmn-runtime-ir` provides immutable runtime model, input, decision, executable BKM, structural type, recursive expression, relation, and decision-table contracts. `RuntimeIrLowerer` assigns deterministic integer IDs, global value slots, lexical local slots, and stable context-field indices; combines linked semantic models into one namespace-free runtime slot space; lowers every protobuf FEEL AST expression variant and all modeled decision logic; resolves statically known path and descendant members; lowers executable BKM functions; persists lexical frame layouts; merges declared and recursively discovered expression references into dependencies; recomputes deterministic runtime topological order; and rejects unsuccessful semantic results. Aggregate constructors enforce ID/slot uniqueness and bounds, dependency and evaluation-order integrity, table widths and aggregation compatibility, and lexical-frame reference bounds. `RuntimeIrOptimizer` builds typed constant pools and stable built-in bindings without changing lossless IR. The `dmn-runtime` module interprets scheduled decisions/BKMs, lexical closures, contexts, relations, collections, unary tests, built-ins, and decision tables. External JAVA/PMML host bindings, serialization, and further optimization remain future targets.

Internal lowering is separated by responsibility: `RuntimeModelIndex` owns linked-model source
addresses and type catalogs, while `RuntimeTypeLowerer` owns DMN/FEEL structural types and member
indices. `RuntimeExpressionLowerer` owns recursive FEEL expressions and lexical-local allocation,
with child and closure-capture transitions shared through `RuntimeLexicalFrame`.
`RuntimeDecisionTableLowerer` owns table structure and policy metadata, and
`RuntimeBoxedExpressionLowerer` owns recursive boxed logic. `RuntimeIrLowerer` remains the public
orchestration facade.

The Runtime Intermediate Representation (Runtime IR) is the central
execution representation of the DMN Compiler Toolkit.

It is the final internal representation before:

-   generated source code
-   native execution
-   interpreted execution
-   distributed execution

The Runtime IR is designed for **execution efficiency**, not for human
readability.

The compiler transforms:

```mermaid
flowchart TD
    XML["DMN XML"] --> Model["Semantic Model"] --> AST["FEEL AST"] --> IR["Optimized Runtime IR"]
```

At runtime:

```mermaid
flowchart TD
    App["Application"] --> IR["Runtime IR"] --> Res["Decision Result"]
```

No XML processing, FEEL parsing, or semantic analysis occurs.

------------------------------------------------------------------------

<a id="contents-section-2"></a>
## 12.2 Design Goals

The Runtime IR is optimized for:

-   minimal memory usage
-   fast execution
-   cache locality
-   deterministic behavior
-   language independence
-   serialization
-   code generation
-   parallel execution

------------------------------------------------------------------------

<a id="contents-section-3"></a>
## 12.3 Architectural Position

The Runtime IR is the stable contract between compiler and execution
targets.

```mermaid
flowchart TD
    Opt["Optimizer"] --> IR["Runtime IR"]
    IR --> Java["Java"]
    IR --> Rust["Rust"]
    IR --> Spark["Spark SQL"]
    Java --> Exec["Executable Artifact"]
```

------------------------------------------------------------------------

<a id="contents-section-4"></a>
## 12.4 Runtime IR Principles

### 12.4.1 RIR-001 --- No Source Language Concepts

Runtime IR must not contain:

-   XML elements
-   DMN XML identifiers
-   FEEL source strings
-   parser objects
-   AST nodes

Example:

Forbidden:

```java
RuntimeExpression {
    String feelExpression;
}
```
Correct:
```java
RuntimeExpression {
    opcode;
    operands;
}
```
------------------------------------------------------------------------

### 12.4.2 RIR-002 --- Integer-Based References

Runtime execution should avoid string lookup.

Bad:
```text
lookup variable "customerAge"
```
Good:
```text
LOAD_VARIABLE 12
```
Example:
Compilation:
```mermaid
flowchart TD
    Name["customerAge"] --> Id["VariableId = 12"]
```
Runtime:
```text
variables[12]
```
Benefits:
-   faster access
-   lower memory usage
-   better cache locality

------------------------------------------------------------------------
### 12.4.3 RIR-003 --- Immutable Representation

Runtime IR objects are immutable.

Example:
```java
public final class RuntimeDecision {
    private final int id;
    private final int rootExpression;
}
```
No runtime mutation.
Benefits:
-   thread safety
-   sharing between requests
-   safe caching
------------------------------------------------------------------------
<a id="contents-section-5"></a>
## 12.5 Runtime IR Package

Package:
```text
io.finmsg.dmn.ir
```
Structure:
```mermaid
flowchart TD
    IR["ir"] --> C1["RuntimeModel"]
    IR --> C2["RuntimeDecision"]
    IR --> C3["RuntimeExpression"]
    IR --> C4["RuntimeInstruction"]
    IR --> C5["RuntimeVariable"]
    IR --> C6["RuntimeFunction"]
    IR --> C7["ExecutionGraph"]
    IR --> C8["ConstantPool"]
    IR --> C9["serialization"]
```
------------------------------------------------------------------------

<a id="contents-section-6"></a>
## 12.6 Runtime Model

The root execution artifact.

Example:
```java
public final class RuntimeModel {
    private final List<RuntimeDecision> decisions;
    private final List<RuntimeExpression> expressions;
    private final ConstantPool constants;
}
```
Contains:
-   executable decisions
-   expressions
-   variables
-   functions
-   constants
------------------------------------------------------------------------
Example:
```mermaid
flowchart TD
    Model["RuntimeModel"] --> D["Decisions"]
    Model --> E["Expressions"]
    Model --> V["Variables"]
    Model --> F["Functions"]
    Model --> C["Constants"]
```
------------------------------------------------------------------------

<a id="contents-section-7"></a>
## 12.7 Runtime Decision

Represents an executable decision.

Example:
Semantic Model:
```text
Decision:
DeterminePenalty
```
Runtime IR:
```text
RuntimeDecision
id:
42
rootExpression:
105
```
------------------------------------------------------------------------
Java:
```java
public record RuntimeDecision(
    int id,
    int expressionId
){}
```
------------------------------------------------------------------------

<a id="contents-section-8"></a>
## 12.8 Runtime Variable

Variables are resolved during compilation.

Semantic Model:
```text
speed
```
Runtime IR:
```text
VariableId:
7
```
------------------------------------------------------------------------
Example:
```java
public record RuntimeVariable(
    int id,
    Type type
){}
```
------------------------------------------------------------------------
Runtime access:
```java
Object value =
    context.get(7);
```
------------------------------------------------------------------------

<a id="contents-section-9"></a>
## 12.9 Runtime Expression

Expressions are represented as executable operations.

Example:
FEEL:
```feel
speed > 100
```
Runtime IR:
```text
Expression 200
LOAD_VARIABLE 7
LOAD_CONSTANT 3
GREATER_THAN
```
------------------------------------------------------------------------
Model:
```java
public final class RuntimeExpression {
    private final Opcode opcode;
    private final int[] operands;
}
```
------------------------------------------------------------------------

<a id="contents-section-10"></a>
## 12.10 Instruction Model

The Runtime IR can use an instruction-based design.

Example:
```text
Instruction
opcode
operand1
operand2
```
------------------------------------------------------------------------
Example:
```text
0001 LOAD_VARIABLE 7
0002 LOAD_CONSTANT 3
0003 GREATER_THAN
0004 RETURN
```
------------------------------------------------------------------------
Java representation:
```java
public record Instruction(
    Opcode opcode,
    int operand
){}
```
------------------------------------------------------------------------

<a id="contents-section-11"></a>
## 12.11 Opcode Design

Opcodes represent executable operations.

Example:
```text
LOAD_CONSTANT
LOAD_VARIABLE
STORE_VARIABLE
ADD
SUBTRACT
MULTIPLY
DIVIDE
COMPARE_EQ
COMPARE_GT
AND
OR
CALL_FUNCTION
JUMP
RETURN
```
------------------------------------------------------------------------
Example:
```text
Opcode.COMPARE_GREATER_THAN
```
replaces:
```text
FEEL operator >
```
------------------------------------------------------------------------

<a id="contents-section-12"></a>
## 12.12 Expression Graph vs Instruction Stream

Two possible execution models are supported.

------------------------------------------------------------------------
### 12.12.1 Model A --- Expression Graph

Example:
```mermaid
flowchart TD
    gt[">"] --> speed["speed"]
    gt --> c100["100"]
```
Advantages:
-   easy optimization
-   common subexpression elimination
-   graph analysis
------------------------------------------------------------------------
### 12.12.2 Model B --- Instruction Stream

Example:
```text
LOAD speed
LOAD 100
COMPARE_GT
```
Advantages:
-   fast interpreter
-   simple execution loop
-   good cache behavior

------------------------------------------------------------------------
Recommended architecture:
Use both.
Compiler:
```mermaid
flowchart TD
    AST["FEEL AST"] --> Graph["Expression Graph"] --> Opt["Optimization"] --> Stream["Instruction Stream"]
```
------------------------------------------------------------------------

<a id="contents-section-13"></a>
## 12.13 Execution Graph

The Runtime IR contains the Decision Requirements Graph.

Example:
```mermaid
flowchart TD
    input["InputData"] --> decA["Decision A"]
    decA --> decB["Decision B"]
    decB --> decC["Decision C"]
```
Runtime:
```java
evaluate(C)
requires:
    B
requires:
    A
```
------------------------------------------------------------------------
Model:
```java
public final class ExecutionGraph {
    List<Node> nodes;
    List<Edge> dependencies;
}
```
------------------------------------------------------------------------

<a id="contents-section-14"></a>
## 12.14 Constant Pool

Constants are stored separately.

Example:
Instead of:
```text
100
100
100
```
Store:
```text
ConstantPool
[0]
100
```
Instructions:
```text
LOAD_CONSTANT 0
```
Benefits:
-   memory reduction
-   sharing
-   serialization efficiency
------------------------------------------------------------------------
<a id="contents-section-15"></a>
## 12.15 Runtime IR Serialization

Current policy: Runtime IR is process-local. The Java records are immutable compiler/runtime
contracts, but they are not a persistence or wire format and do not implement Java
`Serializable`. Their record shape, enum ordering, expression ordinals, and Java class names must
not be stored as durable compatibility identifiers.

No versioned persistence schema is implemented because the repository currently has no persistent
compilation cache, runtime artifact loader, or deployment transport that consumes Runtime IR.
In-memory compilation and the optimized companion model are the only active use cases.

Introduce a persistence schema only when one of these concrete consumers is implemented:

- a compilation cache that survives process or compiler restarts;
- deployment of precompiled IR independently from generated code;
- transport of IR between compiler and runtime processes or languages.

When triggered, persistence must use a separate versioned schema owned by `dmn-runtime-ir`; it
must not reuse the semantic protobuf model and must not serialize Java records directly. The
envelope must contain at least:

- schema major/minor version;
- required feature/capability identifiers;
- compiler version and deterministic source/model-set fingerprint;
- the lossless or optimized IR payload kind;
- integrity metadata and configured size/depth limits.

Compatibility rules:

- readers reject unknown major versions and unsupported required capabilities;
- readers may accept newer minor versions only when unknown fields are safely ignorable;
- stable numeric IDs must be explicitly assigned in the schema, never derived from Java enum
  ordinals;
- migrations occur between persistence messages, not by mutating historical Java records;
- deterministic byte serialization and read/write/read equivalence require conformance tests;
- deserialization is untrusted input and must enforce bounds before allocation.

Protocol Buffers is the preferred first candidate once a consumer exists, subject to a dedicated
ADR covering that consumer's compatibility lifetime and deployment constraints.

------------------------------------------------------------------------

<a id="contents-section-16"></a>
## 12.16 Example Transformation

Input FEEL:
```feel
speed > 100 and age < 25
```
FEEL AST:
```mermaid
flowchart TD
    andNode["AND"] --> gtNode[">"]
    andNode --> ltNode["<"]
    gtNode --> speed["speed"]
    gtNode --> c100["100"]
    ltNode --> age["age"]
    ltNode --> c25["25"]
```
Runtime IR:
```text
0 LOAD_VARIABLE speedId
1 LOAD_CONSTANT 100
2 GREATER_THAN
3 LOAD_VARIABLE ageId
4 LOAD_CONSTANT 25
5 LESS_THAN
6 AND
7 RETURN
```
------------------------------------------------------------------------

<a id="contents-section-17"></a>
## 12.17 Runtime Execution Loop

A simple interpreter:
```java
while(true){
    Instruction instruction =
        code[ip++];

    switch(instruction.opcode()){
        case LOAD_CONSTANT:
            stack.push(
              constants.get(
                instruction.argument()
              )
            );
            break;

        case ADD:
            executeAdd();
            break;
    }
}
```
------------------------------------------------------------------------

Later this can be replaced by:

-   generated Java
-   bytecode generation
-   GraalVM native image
-   LLVM backend
------------------------------------------------------------------------
<a id="contents-section-18"></a>
## 12.18 Runtime IR Optimization

Optimizations target the Runtime IR.

Examples:
------------------------------------------------------------------------
### 12.18.1 Dead instruction elimination

Before:
```text
LOAD x
LOAD y
ADD
POP
```
After:
```text
removed
```
------------------------------------------------------------------------
### 12.18.2 Constant folding

Before:
```text
LOAD 10
LOAD 20
ADD
```
After:
```text
LOAD 30
```
------------------------------------------------------------------------
### 12.18.3 Variable slot optimization

Before:
```text
variable lookup:
customer.age
```
After:
```text
slot 12
```
------------------------------------------------------------------------

<a id="contents-section-19"></a>
## 12.19 Code Generation

Runtime IR becomes:
### 12.19.1 Java
```java
if(speed > 100){
 return HIGH;
}
```
------------------------------------------------------------------------
### 12.19.2 Spark SQL
```sql
CASE
WHEN speed > 100
THEN 'HIGH'
END
```
------------------------------------------------------------------------
### 12.19.3 Rust
```rust
if speed > 100 {
    Result::High
}
```
------------------------------------------------------------------------

<a id="contents-section-20"></a>
## 12.20 Performance Considerations

Runtime IR avoids:

  Traditional Engine   Runtime IR
  -------------------- -----------------
  XML parsing          none
  FEEL parsing         none
  reflection           none
  string lookup        integer IDs
  dynamic dispatch     opcode dispatch
  object graphs        compact arrays

------------------------------------------------------------------------

<a id="contents-section-21"></a>
## 12.21 Thread Safety

Runtime IR is immutable.

Therefore:
```mermaid
flowchart TD
    Model["One RuntimeModel"] --> T1["Thread 1"]
    Model --> T2["Thread 2"]
    Model --> T3["Thread 3"]
```
No synchronization required.
------------------------------------------------------------------------
<a id="contents-section-22"></a>
## 12.22 Testing Strategy

Runtime IR tests:
### 12.22.1 Compilation correctness
```mermaid
flowchart TB
    dmn["DMN"] --> ir["Runtime IR"]
```
------------------------------------------------------------------------
### 12.22.2 Serialization
```mermaid
flowchart TB
    runtimeInput["Runtime IR"] --> protobuf["Protobuf"] --> runtimeOutput["Runtime IR"]
```
------------------------------------------------------------------------
### 12.22.3 Execution equivalence

Compare:
```text
Reference DMN Engine
vs
Runtime IR Execution
```
------------------------------------------------------------------------
### 12.22.4 Performance
Measure:
-   execution latency
-   throughput
-   memory allocation
-   startup time
------------------------------------------------------------------------
<a id="contents-section-23"></a>
## 12.23 Summary
Runtime IR is the key architectural differentiator.

It provides:
-   compiler/runtime separation
-   high-performance execution
-   multi-language generation
-   deterministic artifacts
-   low memory footprint

The final compiler pipeline is:
```mermaid
flowchart TD
    XML["DMN XML"] --> Model["Semantic Model"]
    Model --> AST["FEEL AST"]
    AST --> Ana["Semantic Analysis"]
    Ana --> Opt["Optimizations"]
    Opt --> IR["Runtime IR"]
    IR --> Java["Java"]
    IR --> Rust["Rust"]
    IR --> Spark["Spark"]
```

------------------------------------------------------------------------

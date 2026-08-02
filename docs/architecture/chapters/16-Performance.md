# Chapter 16 — Performance [FUTURE]

## 16.1 Purpose

Performance is a primary architectural objective of the DMN Compiler
Toolkit.

The project is designed around the principle:

> Spend complexity during compilation to achieve minimal complexity
> during execution.

Traditional DMN engines frequently perform:

-   XML interpretation
-   FEEL parsing
-   type resolution
-   dependency analysis
-   expression evaluation

during runtime.

The DMN Compiler Toolkit moves these activities into the compilation
phase.

The runtime executes only optimized Runtime IR or generated code.

------------------------------------------------------------------------

# 16.2 Performance Philosophy

The architecture follows a compiler-oriented performance model.

Traditional interpreter:

``` text
             Runtime

DMN XML

  |

  v

Parse XML

  |

  v

Parse FEEL

  |

  v

Resolve Types

  |

  v

Evaluate Decision
```

Every execution repeats work.

------------------------------------------------------------------------

Compiler approach:

``` text
             Build Time

DMN XML

  |

  v

Semantic Analysis

  |

  v

Optimization

  |

  v

Runtime IR

  |

  v

Generated Code

             Runtime

Input

  |

  v

Decision Execution
```

------------------------------------------------------------------------

# 16.3 Performance Goals

Target characteristics:

  Area               Goal
  ------------------ --------------------------------------
  Runtime latency    microsecond to low millisecond range
  Memory footprint   minimal allocations
  Throughput         millions of evaluations/sec possible
  Startup time       immediate execution
  Predictability     deterministic performance
  Scalability        horizontal and batch execution

------------------------------------------------------------------------

# 16.4 Performance Principles

## PERF-001 --- Compile Once, Execute Many Times

The most important optimization.

Example:

One compilation:

``` text
traffic.dmn

        |

        v

Runtime IR
```

Millions of executions:

``` text
request 1
request 2
request 3
...
request N
```

No repeated parsing.

------------------------------------------------------------------------

# 16.5 No Runtime XML Processing

Forbidden:

``` java
Decision execute(){

    parseXml();

}
```

------------------------------------------------------------------------

Correct:

``` java
Decision execute(){

    executeRuntimeIR();

}
```

------------------------------------------------------------------------

Benefits:

-   no XML allocations
-   no parsing overhead
-   no schema handling

------------------------------------------------------------------------

# 16.6 No Runtime FEEL Parsing

Traditional:

``` text
"speed > 100"

       |

       v

Parser

       |

       v

Evaluation
```

Every execution.

------------------------------------------------------------------------

Compiler:

``` text
"speed > 100"

       |

       v

FEEL AST

       |

       v

Runtime IR

       |

       v

Java bytecode
```

Once.

------------------------------------------------------------------------

# 16.7 Memory Efficiency

The runtime avoids unnecessary objects.

------------------------------------------------------------------------

## Object-heavy design

Example:

``` java
Expression {

    Operator operator;

    List<Expression> children;

    Metadata metadata;

}
```

Many allocations.

------------------------------------------------------------------------

## Runtime IR design

Example:

``` text
ExpressionOpcode

operand1

operand2
```

Compact.

------------------------------------------------------------------------

Example:

``` text
COMPARE_GT

variableId=5

constantId=12
```

------------------------------------------------------------------------

# 16.8 Integer-Based References

Runtime IR avoids string lookups.

Slow:

``` java
variables.get("customer.age");
```

------------------------------------------------------------------------

Fast:

``` java
variables[12];
```

------------------------------------------------------------------------

Runtime representation:

``` text
Variable Table

0 -> customerId

1 -> age

2 -> country
```

------------------------------------------------------------------------

Expression:

``` text
LOAD_VARIABLE 1
```

------------------------------------------------------------------------

Benefits:

-   cache friendly
-   faster lookup
-   smaller memory

------------------------------------------------------------------------

# 16.9 Immutable Runtime IR

Runtime IR is immutable.

Benefits:

-   thread safe
-   reusable
-   cacheable
-   sharable

Example:

``` text
RuntimeModel

        |

        +----------+

        |          |

 Thread 1      Thread 2
```

No synchronization.

------------------------------------------------------------------------

# 16.10 Zero Reflection

Reflection introduces:

-   slower execution
-   poor JIT optimization
-   additional metadata

Forbidden:

``` java
method.invoke()
```

------------------------------------------------------------------------

Preferred:

Generated:

``` java
decision.evaluate()
```

------------------------------------------------------------------------

Benefits:

-   JVM inlining
-   escape analysis
-   predictable execution

------------------------------------------------------------------------

# 16.11 Generated Code Optimization

Generated Java should look like manually optimized code.

Example:

Generated:

``` java
if(input.speed() > 100){

    return HIGH;

}

return LOW;
```

------------------------------------------------------------------------

Not:

``` java
Expression.evaluate(
    tree,
    context
);
```

------------------------------------------------------------------------

The JVM can optimize the first form.

------------------------------------------------------------------------

# 16.12 JVM Optimization Strategy

The Java backend benefits from:

## Method Inlining

Small decision methods:

``` java
calculatePenalty()
```

can be inlined.

------------------------------------------------------------------------

## Branch Prediction

Simple branches:

``` java
if(condition)
```

allow CPU optimization.

------------------------------------------------------------------------

## Escape Analysis

Avoid unnecessary temporary objects.

------------------------------------------------------------------------

## JIT Compilation

Generated code becomes optimized machine code.

------------------------------------------------------------------------

# 16.13 Runtime IR Memory Layout

Recommended design:

    RuntimeModel

    +----------------+
    | Decisions      |
    +----------------+

    +----------------+
    | Expressions    |
    +----------------+

    +----------------+
    | Constants      |
    +----------------+

    +----------------+
    | Functions      |
    +----------------+

------------------------------------------------------------------------

Arrays are preferred over linked structures.

Example:

``` java
RuntimeExpression[]
```

instead of:

``` java
List<Node>
```

------------------------------------------------------------------------

Benefits:

-   better locality
-   fewer allocations
-   faster iteration

------------------------------------------------------------------------

# 16.14 Decision Graph Optimization

DMN decisions form a dependency graph.

Example:

    InputData

       |

       v

    Decision A

       |

       v

    Decision B

       |

       v

    Decision C

------------------------------------------------------------------------

Optimization:

Remove unused nodes.

Example:

Before:

    A
    |
    B
    |
    C
    |
    D (unused)

After:

    A
    |
    B
    |
    C

------------------------------------------------------------------------

# 16.15 Constant Folding

Compile time:

FEEL:

    10 + 20

Compiler:

    30

Runtime:

``` java
return 30;
```

------------------------------------------------------------------------

No calculation during execution.

------------------------------------------------------------------------

# 16.16 Decision Inlining

Before:

    Decision A

          |

    Decision B

Runtime:

    call A()
    call B()

------------------------------------------------------------------------

After:

    single optimized expression

------------------------------------------------------------------------

Benefits:

-   fewer calls
-   better JIT optimization

------------------------------------------------------------------------

# 16.17 Common Subexpression Elimination

Example:

Before:

    customer.age > 18

    customer.age > 18

------------------------------------------------------------------------

After:

    ageCheck = customer.age > 18

Reuse result.

------------------------------------------------------------------------

# 16.18 Batch Execution

For high-volume environments:

Example:

    100000 decisions

should not require:

    100000 object graphs

------------------------------------------------------------------------

Possible future model:

    Input Column Arrays

           |

           v

    Vectorized Runtime

           |

           v

    Output Column Arrays

------------------------------------------------------------------------

Important for:

-   Spark
-   Databricks
-   analytics workloads

------------------------------------------------------------------------

# 16.19 Parallel Execution

Decision graphs can expose parallelism.

Example:

            A

           / \

          B   C

           \ /

            D

------------------------------------------------------------------------

B and C can execute independently.

------------------------------------------------------------------------

Future runtime:

``` text
Virtual Threads

or

ForkJoin execution
```

------------------------------------------------------------------------

# 16.20 Compilation Performance

Compilation itself must also scale.

Targets:

-   large DMN repositories
-   CI/CD builds
-   automated deployment

------------------------------------------------------------------------

Optimization techniques:

## Incremental compilation

Only changed models compile.

------------------------------------------------------------------------

## Compiler caching

Example:

    DMN Hash

        |

        v

    Runtime IR Cache

------------------------------------------------------------------------

## Parallel compilation

Example:

    Model A ---- Thread 1

    Model B ---- Thread 2

    Model C ---- Thread 3

------------------------------------------------------------------------

# 16.21 Performance Metrics

The compiler exposes:

## Compilation Metrics

Example:

    XML parsing:

    42 ms

    FEEL parsing:

    15 ms

    Optimization:

    8 ms

    Code generation:

    20 ms

------------------------------------------------------------------------

## Runtime Metrics

Example:

    Decision:

    TrafficViolation

    Executions:

    1,000,000

    Average:

    4 µs

    Allocations:

    0 bytes

------------------------------------------------------------------------

# 16.22 Benchmark Strategy

Benchmark categories:

------------------------------------------------------------------------

## Micro Benchmarks

Examples:

-   expression evaluation
-   function calls
-   variable access

Technology:

    JMH

------------------------------------------------------------------------

## Macro Benchmarks

Examples:

-   complete DMN models
-   thousands of decisions
-   realistic workloads

------------------------------------------------------------------------

## Comparison Benchmarks

Compare:

    Interpreter

    vs

    Runtime IR

    vs

    Generated Java

------------------------------------------------------------------------

# 16.23 Performance Testing Rules

Every optimization must prove:

1.  same semantics
2.  measurable improvement
3.  no unacceptable complexity

------------------------------------------------------------------------

Benchmark before:

    optimization

Benchmark after:

    optimization

------------------------------------------------------------------------

# 16.24 Expected Performance Levels

Approximate target hierarchy:

    Slowest

    XML Interpreter

            |

    FEEL Interpreter

            |

    Runtime IR Interpreter

            |

    Generated Java

            |

    Generated Native Code

    Fastest

------------------------------------------------------------------------

# 16.25 Performance Summary

The architecture achieves performance through:

-   compile-time analysis
-   immutable Runtime IR
-   integer references
-   zero reflection
-   generated code
-   optimized execution graphs
-   cache-friendly structures
-   deterministic compilation

The core principle:

    Complexity during compilation

                +

    Simplicity during execution

                =

    High-performance DMN execution

------------------------------------------------------------------------

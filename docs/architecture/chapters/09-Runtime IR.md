# Chapter 9 --- Runtime IR [PARTIALLY IMPLEMENTED]

## 9.1 Purpose

Implementation baseline as of August 2026: `dmn-runtime-ir` provides immutable runtime model, input, decision, BKM, structural type, recursive expression, relation, and decision-table contracts. `RuntimeIrLowerer` assigns deterministic integer IDs, global value slots, and lexical local slots; lowers local named and structural types; converts dependencies and semantic compilation order to integer references; lowers every protobuf FEEL AST expression variant; lowers all modeled decision logic forms including recursively nested boxed contexts, relations, lists, functions, and DMN invocations; and rejects unsuccessful semantic results. Executable BKM function bodies, constant pools, serialization, optimization, and complete model-set lowering remain targets described by the rest of this chapter.

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

``` text
DMN XML

    |

    v

Semantic Model

    |

    v

FEEL AST

    |

    v

Optimized Runtime IR
```

At runtime:

``` text
Application

    |

    v

Runtime IR

    |

    v

Decision Result
```

No XML processing, FEEL parsing, or semantic analysis occurs.

------------------------------------------------------------------------

# 9.2 Design Goals

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

# 9.3 Architectural Position

The Runtime IR is the stable contract between compiler and execution
targets.

``` text
                     Optimizer
                         |
                         v

              +---------------------+
              |      Runtime IR     |
              +---------------------+
                         |
        +----------------+----------------+
        |                |                |
        v                v                v

      Java            Rust             Spark SQL

        |
        v

   Executable Artifact
```

------------------------------------------------------------------------

# 9.4 Runtime IR Principles

## RIR-001 --- No Source Language Concepts

Runtime IR must not contain:

-   XML elements
-   DMN XML identifiers
-   FEEL source strings
-   parser objects
-   AST nodes

Example:

Forbidden:

``` java
RuntimeExpression {

    String feelExpression;

}
```

Correct:

``` java
RuntimeExpression {

    opcode;

    operands;

}
```

------------------------------------------------------------------------

# RIR-002 --- Integer-Based References

Runtime execution should avoid string lookup.

Bad:

``` text
lookup variable "customerAge"
```

Good:

``` text
LOAD_VARIABLE 12
```

Example:

Compilation:

``` text
customerAge

        |

        v

VariableId = 12
```

Runtime:

``` text
variables[12]
```

Benefits:

-   faster access
-   lower memory usage
-   better cache locality

------------------------------------------------------------------------

# RIR-003 --- Immutable Representation

Runtime IR objects are immutable.

Example:

``` java
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

# 9.5 Runtime IR Package

Package:

``` text
io.finmsg.dmn.ir
```

Structure:

``` text
ir

├── RuntimeModel

├── RuntimeDecision

├── RuntimeExpression

├── RuntimeInstruction

├── RuntimeVariable

├── RuntimeFunction

├── ExecutionGraph

├── ConstantPool

└── serialization
```

------------------------------------------------------------------------

# 9.6 Runtime Model

The root execution artifact.

Example:

``` java
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

``` text
RuntimeModel

 |
 +-- Decisions
 |
 +-- Expressions
 |
 +-- Variables
 |
 +-- Functions
 |
 +-- Constants
```

------------------------------------------------------------------------

# 9.7 Runtime Decision

Represents an executable decision.

Example:

Semantic Model:

``` text
Decision:

DeterminePenalty
```

Runtime IR:

``` text
RuntimeDecision

id:

42

rootExpression:

105
```

------------------------------------------------------------------------

Java:

``` java
public record RuntimeDecision(

    int id,

    int expressionId

){}
```

------------------------------------------------------------------------

# 9.8 Runtime Variable

Variables are resolved during compilation.

Semantic Model:

``` text
speed
```

Runtime IR:

``` text
VariableId:

7
```

------------------------------------------------------------------------

Example:

``` java
public record RuntimeVariable(

    int id,

    Type type

){}
```

------------------------------------------------------------------------

Runtime access:

``` java
Object value =
    context.get(7);
```

------------------------------------------------------------------------

# 9.9 Runtime Expression

Expressions are represented as executable operations.

Example:

FEEL:

``` feel
speed > 100
```

Runtime IR:

``` text
Expression 200

LOAD_VARIABLE 7

LOAD_CONSTANT 3

GREATER_THAN
```

------------------------------------------------------------------------

Model:

``` java
public final class RuntimeExpression {

    private final Opcode opcode;

    private final int[] operands;

}
```

------------------------------------------------------------------------

# 9.10 Instruction Model

The Runtime IR can use an instruction-based design.

Example:

``` text
Instruction

opcode

operand1

operand2
```

------------------------------------------------------------------------

Example:

``` text
0001 LOAD_VARIABLE 7

0002 LOAD_CONSTANT 3

0003 GREATER_THAN

0004 RETURN
```

------------------------------------------------------------------------

Java representation:

``` java
public record Instruction(

    Opcode opcode,

    int operand

){}
```

------------------------------------------------------------------------

# 9.11 Opcode Design

Opcodes represent executable operations.

Example:

``` text
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

``` text
Opcode.COMPARE_GREATER_THAN
```

replaces:

``` text
FEEL operator >
```

------------------------------------------------------------------------

# 9.12 Expression Graph vs Instruction Stream

Two possible execution models are supported.

------------------------------------------------------------------------

## Model A --- Expression Graph

Example:

``` text
        >
       / \
    speed 100
```

Advantages:

-   easy optimization
-   common subexpression elimination
-   graph analysis

------------------------------------------------------------------------

## Model B --- Instruction Stream

Example:

``` text
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

``` text
FEEL AST

    |

    v

Expression Graph

    |

Optimization

    |

Instruction Stream
```

------------------------------------------------------------------------

# 9.13 Execution Graph

The Runtime IR contains the Decision Requirements Graph.

Example:

``` text
InputData

   |

Decision A

   |

Decision B

   |

Decision C
```

Runtime:

``` java
evaluate(C)

requires:

    B

requires:

    A
```

------------------------------------------------------------------------

Model:

``` java
public final class ExecutionGraph {

    List<Node> nodes;

    List<Edge> dependencies;

}
```

------------------------------------------------------------------------

# 9.14 Constant Pool

Constants are stored separately.

Example:

Instead of:

``` text
100

100

100
```

Store:

``` text
ConstantPool

[0]

100
```

Instructions:

``` text
LOAD_CONSTANT 0
```

Benefits:

-   memory reduction
-   sharing
-   serialization efficiency

------------------------------------------------------------------------

# 9.15 Runtime IR Serialization

Runtime IR supports persistence.

Example:

``` text
traffic.dmn

        |

        v

traffic.runtime.ir
```

Possible formats:

-   protobuf
-   flatbuffers
-   custom binary format

------------------------------------------------------------------------

Recommended initial format:

``` text
Protocol Buffers
```

Reasons:

-   language support
-   schema evolution
-   debugging tools
-   compact representation

------------------------------------------------------------------------

# 9.16 Example Transformation

Input FEEL:

``` feel
speed > 100 and age < 25
```

FEEL AST:

``` text
             AND

          /       \

         >         <

      speed      age

        100       25
```

Runtime IR:

``` text
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

# 9.17 Runtime Execution Loop

A simple interpreter:

``` java
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

# 9.18 Runtime IR Optimization

Optimizations target the Runtime IR.

Examples:

------------------------------------------------------------------------

## Dead instruction elimination

Before:

``` text
LOAD x

LOAD y

ADD

POP
```

After:

``` text
removed
```

------------------------------------------------------------------------

## Constant folding

Before:

``` text
LOAD 10

LOAD 20

ADD
```

After:

``` text
LOAD 30
```

------------------------------------------------------------------------

## Variable slot optimization

Before:

``` text
variable lookup:

customer.age
```

After:

``` text
slot 12
```

------------------------------------------------------------------------

# 9.19 Code Generation

Runtime IR becomes:

## Java

``` java
if(speed > 100){

 return HIGH;

}
```

------------------------------------------------------------------------

## Spark SQL

``` sql
CASE
WHEN speed > 100
THEN 'HIGH'
END
```

------------------------------------------------------------------------

## Rust

``` rust
if speed > 100 {
    Result::High
}
```

------------------------------------------------------------------------

# 9.20 Performance Considerations

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

# 9.21 Thread Safety

Runtime IR is immutable.

Therefore:

``` text
One RuntimeModel

        |

        +---- Thread 1

        +---- Thread 2

        +---- Thread 3
```

No synchronization required.

------------------------------------------------------------------------

# 9.22 Testing Strategy

Runtime IR tests:

## Compilation correctness

``` text
DMN

↓

Runtime IR
```

------------------------------------------------------------------------

## Serialization

``` text
Runtime IR

↓

protobuf

↓

Runtime IR
```

------------------------------------------------------------------------

## Execution equivalence

Compare:

``` text
Reference DMN Engine

vs

Runtime IR Execution
```

------------------------------------------------------------------------

## Performance

Measure:

-   execution latency
-   throughput
-   memory allocation
-   startup time

------------------------------------------------------------------------

# 9.23 Summary

Runtime IR is the key architectural differentiator.

It provides:

-   compiler/runtime separation
-   high-performance execution
-   multi-language generation
-   deterministic artifacts
-   low memory footprint

The final compiler pipeline is:

``` text
                 DMN XML

                    |

                    v

             Semantic Model

                    |

                    v

                FEEL AST

                    |

                    v

          Semantic Analysis

                    |

                    v

              Optimizations

                    |

                    v

              Runtime IR

                    |

        +-----------+------------+

        |           |            |

       Java       Rust        Spark
```

------------------------------------------------------------------------

# Chapter 10 --- Compiler Passes \[PROVISIONAL\]

## 10.1 Purpose

Compiler passes are the transformation stages that progressively refine
the internal representation of the DMN model.

Each pass performs one well-defined operation and transforms one
representation into another.

The compiler does not directly transform DMN XML into executable code.

Instead:

``` text
DMN XML

    |

    v

Semantic Model

    |

    v

Validated Model

    |

    v

Optimized Model

    |

    v

Runtime IR
```

Each arrow represents one or more compiler passes.

------------------------------------------------------------------------

# 10.2 Compiler Pass Principles

Compiler passes follow these architectural rules:

## CP-001 --- Single Responsibility

A pass performs exactly one transformation.

Good:

``` text
Resolve References

↓

Infer Types

↓

Build Dependency Graph

↓

Constant Folding
```

Bad:

``` text
AnalyzeEverythingPass
```

------------------------------------------------------------------------

## CP-002 --- Immutable Input

A compiler pass never modifies its input.

Example:

Incorrect:

``` java
model.setDecision(...)
```

Correct:

``` java
OptimizedModel result =
    pass.apply(model);
```

------------------------------------------------------------------------

## CP-003 --- Deterministic Output

Given:

``` text
same input
+
same compiler version
```

the result must always be identical.

------------------------------------------------------------------------

## CP-004 --- Independently Testable

Every pass has:

-   unit tests
-   input fixture
-   expected output
-   diagnostics verification

------------------------------------------------------------------------

## CP-005 --- Composable

Passes are assembled into pipelines.

Example:

``` java
CompilerPipeline pipeline =
    Pipeline.builder()

    .add(new ReferenceResolutionPass())

    .add(new TypeInferencePass())

    .add(new ConstantFoldingPass())

    .build();
```

------------------------------------------------------------------------

# 10.3 Compiler Pipeline Overview

The complete compilation pipeline:

``` text
                    DMN XML

                       |

                       v

              XML Frontend Pass

                       |

                       v

              Semantic Model

                       |

                       v

          Reference Resolution Pass

                       |

                       v

          FEEL Parsing Pass

                       |

                       v

          Type Analysis Pass

                       |

                       v

          Dependency Graph Pass

                       |

                       v

          Decision Normalization Pass

                       |

                       v

          Optimization Passes

                       |

                       v

              Runtime IR Builder

                       |

                       v

              Runtime IR
```

------------------------------------------------------------------------

# 10.4 Pass Categories

Compiler passes are divided into four categories.

------------------------------------------------------------------------

## 10.4.1 Analysis Passes

Analysis passes collect information.

Examples:

``` text
Type Analysis

Dependency Analysis

Usage Analysis

Complexity Analysis
```

They do not modify semantics.

Output:

``` text
AnalysisResult
```

------------------------------------------------------------------------

## 10.4.2 Validation Passes

Validation passes detect invalid models.

Examples:

``` text
Reference Validation

Type Validation

Cycle Detection

Decision Table Validation
```

------------------------------------------------------------------------

## 10.4.3 Transformation Passes

Transformation passes modify the representation.

Examples:

``` text
Normalization

Inlining

Constant Folding

Expression Simplification
```

------------------------------------------------------------------------

## 10.4.4 Lowering Passes

Lowering converts a higher abstraction level into a lower one.

Examples:

``` text
Semantic Model

        ↓

Runtime IR
```

------------------------------------------------------------------------

# 10.5 Compiler Pass API

The basic abstraction:

``` java
public interface CompilerPass<I,O> {

    String name();

    O execute(
        I input,
        CompilerContext context
    );

}
```

------------------------------------------------------------------------

Example:

``` java
public final class ConstantFoldingPass
implements CompilerPass<RuntimeExpression,
                         RuntimeExpression> {

    public String name(){

        return "constant-folding";
    }

    public RuntimeExpression execute(
        RuntimeExpression input,
        CompilerContext context){

        return optimize(input);
    }
}
```

------------------------------------------------------------------------

# 10.6 Compiler Context

Passes receive shared compilation context.

Example:

``` java
public final class CompilerContext {

    private final DiagnosticCollector diagnostics;

    private final SymbolTable symbols;

    private final CompilerOptions options;

}
```

Contains:

-   diagnostics
-   configuration
-   symbol tables
-   type environment
-   statistics

------------------------------------------------------------------------

Rules:

The context:

MUST NOT contain:

-   mutable global state
-   cached compiler models
-   hidden dependencies

------------------------------------------------------------------------

# 10.7 Pass Manager

The Pass Manager executes passes in order.

Example:

``` java
PassManager manager =
    new PassManager();

manager.add(
    new ReferenceResolutionPass()
);

manager.add(
    new TypeInferencePass()
);

manager.add(
    new ConstantFoldingPass()
);

Result result =
    manager.execute(model);
```

------------------------------------------------------------------------

Responsibilities:

-   ordering
-   lifecycle management
-   diagnostics collection
-   timing metrics
-   error handling

------------------------------------------------------------------------

# 10.8 Phase 1 --- Reference Resolution

## Purpose

Resolve all symbolic references.

Example:

Before:

``` text
Decision:

CalculateFine

uses:

DriverAge
```

After:

``` text
Decision:

CalculateFine

InputReference:

VariableId=15
```

------------------------------------------------------------------------

Responsibilities:

-   resolve decision references
-   resolve variables
-   resolve functions
-   resolve imports

------------------------------------------------------------------------

Output:

``` text
Resolved Semantic Model
```

------------------------------------------------------------------------

# 10.9 Phase 2 --- FEEL Parsing

## Purpose

Convert FEEL source into AST.

Example:

Input:

``` feel
speed > limit
```

Output:

``` text
BinaryExpression

 >
 
speed

limit
```

------------------------------------------------------------------------

Rules:

-   parse exactly once
-   attach AST references
-   preserve source locations

------------------------------------------------------------------------

# 10.10 Phase 3 --- Type Resolution

## Purpose

Determine the type of every expression.

Example:

Input:

``` feel
speed > 100
```

Analysis:

``` text
speed

number

100

number
```

Result:

``` text
boolean
```

------------------------------------------------------------------------

Type information is attached:

``` text
BinaryExpression

operator:

GREATER_THAN

resultType:

boolean
```

------------------------------------------------------------------------

# 10.11 Phase 4 --- Dependency Graph Construction

## Purpose

Create the Decision Requirements Graph.

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

------------------------------------------------------------------------

Used for:

-   execution order
-   cycle detection
-   optimization
-   incremental compilation

------------------------------------------------------------------------

# 10.12 Phase 5 --- Cycle Detection

DMN decisions must not contain invalid cycles.

Example:

Invalid:

``` text
A

|

B

|

A
```

------------------------------------------------------------------------

The pass produces:

Error:

``` text
DMN-2001

Circular decision dependency detected:

A -> B -> A
```

------------------------------------------------------------------------

# 10.13 Phase 6 --- Decision Table Normalization

Different DMN tables can express the same logic.

The optimizer converts them into canonical form.

Example:

Before:

``` text
Rule 1

age > 18

Adult

Rule 2

age <= 18

Minor
```

After:

``` text
DecisionTree

age

 |
 +-- >18 Adult

 |
 +-- <=18 Minor
```

------------------------------------------------------------------------

Benefits:

-   faster evaluation
-   easier optimization
-   simpler code generation

------------------------------------------------------------------------

# 10.14 Phase 7 --- Constant Folding

Compile-time evaluation.

Example:

Before:

``` feel
10 + 20
```

After:

``` text
30
```

------------------------------------------------------------------------

Runtime benefit:

No calculation required.

------------------------------------------------------------------------

# 10.15 Phase 8 --- Expression Simplification

Examples:

## Boolean algebra

Before:

``` feel
x and true
```

After:

``` feel
x
```

------------------------------------------------------------------------

Before:

``` feel
x or false
```

After:

``` feel
x
```

------------------------------------------------------------------------

# 10.16 Phase 9 --- Common Subexpression Elimination

Example:

Before:

``` text
Decision A:

tax = income * 0.2

Decision B:

fee = income * 0.2
```

After:

``` text
SharedExpression:

income * 0.2
```

------------------------------------------------------------------------

Benefits:

-   less computation
-   smaller generated code

------------------------------------------------------------------------

# 10.17 Phase 10 --- Decision Inlining

Small decisions may be embedded.

Before:

``` text
Decision B

calls

Decision A
```

After:

``` text
Decision B

contains A logic
```

------------------------------------------------------------------------

Advantages:

-   removes function calls
-   improves generated code

------------------------------------------------------------------------

Tradeoff:

Avoid excessive code growth.

------------------------------------------------------------------------

# 10.18 Phase 11 --- Dead Decision Elimination

Unused decisions are removed.

Example:

Model:

``` text
Decision A

Decision B

Decision C
```

Application uses:

``` text
Decision A
```

If:

``` text
B

and

C

are unreachable
```

remove them.

------------------------------------------------------------------------

Benefits:

-   smaller Runtime IR
-   smaller generated artifacts

------------------------------------------------------------------------

# 10.19 Phase 12 --- Runtime IR Lowering

Final compiler pass.

Transforms:

``` text
Optimized Semantic Model
```

into:

``` text
Runtime IR
```

Operations:

-   assign integer IDs
-   flatten expressions
-   create execution graph
-   create constant pool
-   create instruction stream

------------------------------------------------------------------------

Example:

Before:

``` feel
speed > 100
```

After:

``` text
LOAD_VAR 7

LOAD_CONST 0

COMPARE_GT
```

------------------------------------------------------------------------

# 10.20 Pass Pipeline Definition

Recommended initial pipeline:

``` text
1. XML Import Resolution

2. Model Validation

3. FEEL Parsing

4. Reference Resolution

5. Type Inference

6. Dependency Graph Construction

7. Cycle Detection

8. Decision Table Normalization

9. Constant Folding

10. Expression Simplification

11. Common Subexpression Elimination

12. Decision Inlining

13. Dead Code Elimination

14. Runtime IR Generation
```

------------------------------------------------------------------------

# 10.21 Pass Diagnostics

Every pass reports:

Example:

``` text
PASS:

Constant Folding

Statistics:

Expressions analyzed:
1520

Constants folded:
340

Execution time:
12 ms
```

------------------------------------------------------------------------

Diagnostics contain:

-   pass name
-   duration
-   warnings
-   errors
-   statistics

------------------------------------------------------------------------

# 10.22 Incremental Compilation

The pass architecture enables incremental builds.

Example:

Change:

``` text
Decision A
```

Compiler determines:

Affected:

``` text
A

B

C
```

Unaffected:

``` text
D

E
```

Only affected parts are rebuilt.

------------------------------------------------------------------------

# 10.23 Parallel Execution

Some analysis passes can execute concurrently.

Example:

``` text
Type Analysis

        |

        +---- Decision A

        +---- Decision B

        +---- Decision C
```

Requires:

-   immutable models
-   deterministic passes

------------------------------------------------------------------------

# 10.24 Testing Strategy

Each pass has:

## Unit Test

Input:

``` text
small model
```

Expected:

``` text
transformed model
```

------------------------------------------------------------------------

## Golden Test

Input:

``` text
DMN file
```

Expected:

``` text
Runtime IR snapshot
```

------------------------------------------------------------------------

## Regression Test

Known bugs become permanent tests.

------------------------------------------------------------------------

# 10.25 Summary

Compiler passes transform the DMN model from a business description into
an optimized executable representation.

The architecture provides:

-   deterministic compilation
-   independent optimization
-   incremental builds
-   easy extension
-   backend independence

The compiler is now:

``` text
DMN XML

 |

Semantic Model

 |

FEEL AST

 |

Analysis Passes

 |

Optimization Passes

 |

Runtime IR

 |

Generated Artifact
```

------------------------------------------------------------------------

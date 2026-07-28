# Chapter 8 --- FEEL AST \[PROVISIONAL SCHEMA\]

## 8.1 Purpose

The FEEL Abstract Syntax Tree (AST) is the compiler representation of
FEEL expressions.

It transforms FEEL from a text-based language into a structured,
analyzable representation.

The FEEL AST is the bridge between:

-   Semantic Model
-   Semantic Analyzer
-   Optimizer
-   Runtime IR Generator
-   Code Generators

The compiler never performs semantic analysis on FEEL source text.

The pipeline is:

``` text
FEEL Source

    |
    v

ANTLR Lexer

    |
    v

ANTLR Parser

    |
    v

FEEL AST

    |
    v

Semantic Analysis

    |
    v

Runtime IR
```

------------------------------------------------------------------------

# 8.2 Design Goals

The FEEL AST must provide:

-   complete FEEL language representation
-   compiler-friendly structure
-   immutable nodes
-   source location tracking
-   type information attachment
-   optimization support
-   deterministic transformation
-   independence from ANTLR runtime

------------------------------------------------------------------------

# 8.3 Architectural Position

The FEEL compiler component:

``` text
                 FEEL Text
                     |
                     v
              +--------------+
              | Lexer        |
              | ANTLR4       |
              +--------------+
                     |
                     v
              +--------------+
              | Parser       |
              | ANTLR4       |
              +--------------+
                     |
                     v
              +--------------+
              | AST Builder  |
              +--------------+
                     |
                     v
              +--------------+
              | FEEL AST     |
              +--------------+
                     |
        +------------+-------------+
        |                          |
        v                          v

 Semantic Analysis            Optimizer

        |
        v

 Runtime IR
```

------------------------------------------------------------------------

# 8.4 Design Principle

## FEEL text exists only at the frontend boundary

After parsing:

Forbidden:

``` java
evaluate("speed > 100")
```

Allowed:

``` java
evaluate(
    BinaryExpression(
        GREATER_THAN,
        Variable("speed"),
        Literal(100)
    )
);
```

------------------------------------------------------------------------

# 8.5 FEEL Package Structure

Package:

``` text
io.finmsg.dmn.feel
```

Structure:

``` text
feel

├── parser

├── lexer

├── ast

├── visitor

├── type

├── analysis

├── optimizer

└── printer
```

------------------------------------------------------------------------

# 8.6 AST Node Design

All AST nodes share a common abstraction.

Example:

``` java
public sealed interface ExpressionNode
permits
    LiteralNode,
    VariableNode,
    BinaryExpressionNode,
    FunctionCallNode,
    ContextNode {

    SourceLocation location();

}
```

------------------------------------------------------------------------

Every node contains:

-   node type
-   child expressions
-   source location
-   optional type information

------------------------------------------------------------------------

# 8.7 Expression Hierarchy

The FEEL AST hierarchy:

``` text
ExpressionNode

├── LiteralNode
│
├── VariableNode
│
├── BinaryExpressionNode
│
├── UnaryExpressionNode
│
├── FunctionCallNode
│
├── ContextNode
│
├── ListNode
│
├── RangeNode
│
├── IfExpressionNode
│
├── ForExpressionNode
│
├── QuantifiedExpressionNode
│
└── FilterExpressionNode
```

------------------------------------------------------------------------

# 8.8 Literal Expressions

Represents constants.

Examples:

``` feel
100
```

``` feel
"HIGH"
```

``` feel
true
```

AST:

``` text
LiteralNode

value:

100

type:

number
```

Java:

``` java
public record LiteralNode(
    Object value
)
implements ExpressionNode {}
```

------------------------------------------------------------------------

# 8.9 Variable Expressions

Represents references.

Example:

``` feel
speed
```

AST:

``` text
VariableNode

name:

speed
```

During semantic analysis:

Before:

``` text
Variable(speed)
```

After:

``` text
Variable(
    referenceId = 12,
    type = number
)
```

------------------------------------------------------------------------

# 8.10 Binary Expressions

Represents operators with two operands.

Example:

``` feel
speed > 100
```

AST:

``` text
        >
       / \
  speed 100
```

Model:

``` java
public final class BinaryExpressionNode {

    Operator operator;

    ExpressionNode left;

    ExpressionNode right;
}
```

------------------------------------------------------------------------

Supported operators:

Arithmetic:

``` text
+
-
*
/
mod
```

Comparison:

``` text
=
!=
<
>
<=
>=
```

Logical:

``` text
and
or
```

------------------------------------------------------------------------

# 8.11 Unary Expressions

Example:

``` feel
not active
```

AST:

``` text
UnaryExpression

operator:

NOT

operand:

Variable(active)
```

------------------------------------------------------------------------

# 8.12 Function Calls

Example:

``` feel
substring(name,1,3)
```

AST:

``` text
FunctionCall

name:

substring

arguments:

    name

    1

    3
```

------------------------------------------------------------------------

Model:

``` java
public final class FunctionCallNode {

    String name;

    List<ExpressionNode> arguments;

}
```

------------------------------------------------------------------------

Functions are resolved during semantic analysis.

Before:

``` text
substring
```

After:

``` text
BuiltInFunctionId.SUBSTRING
```

------------------------------------------------------------------------

# 8.13 Context Expressions

FEEL contexts:

Example:

``` feel
{
 name: "John",
 age: 30
}
```

AST:

``` text
ContextNode

entries:

name -> "John"

age -> 30
```

------------------------------------------------------------------------

Used heavily in DMN.

------------------------------------------------------------------------

# 8.14 List Expressions

Example:

``` feel
[1,2,3]
```

AST:

``` text
ListNode

elements:

1

2

3
```

------------------------------------------------------------------------

# 8.15 Range Expressions

Example:

``` feel
[18..65]
```

AST:

``` text
RangeNode

lower:

18

upper:

65

lowerIncluded:

true
```

------------------------------------------------------------------------

# 8.16 Conditional Expressions

Example:

``` feel
if speed > 100
then "HIGH"
else "LOW"
```

AST:

``` text
IfExpression

condition

    speed > 100

then

    HIGH

else

    LOW
```

------------------------------------------------------------------------

# 8.17 Iteration Expressions

FEEL supports:

``` feel
for x in list return x.name
```

AST:

``` text
ForExpression

variable:

x

source:

list

body:

x.name
```

------------------------------------------------------------------------

# 8.18 Quantified Expressions

Example:

``` feel
some x in drivers satisfies x.age > 18
```

AST:

``` text
QuantifiedExpression

quantifier:

SOME

variable:

x

condition:

x.age > 18
```

------------------------------------------------------------------------

# 8.19 AST Visitor Pattern

Compiler passes should not depend on concrete node types.

Use visitors:

``` java
public interface ExpressionVisitor<R> {

    R visitLiteral(
        LiteralNode node
    );

    R visitBinary(
        BinaryExpressionNode node
    );

}
```

------------------------------------------------------------------------

Used by:

-   type inference
-   optimization
-   code generation
-   pretty printing

------------------------------------------------------------------------

# 8.20 Source Location Tracking

Every AST node stores location.

Example:

``` text
traffic.dmn

line 42

column 15
```

Model:

``` java
public record SourceLocation(

    String file,

    int line,

    int column

){}
```

------------------------------------------------------------------------

Benefits:

-   precise compiler errors
-   debugging
-   IDE integration

------------------------------------------------------------------------

# 8.21 FEEL Type Information

Types are attached after analysis.

Example:

Before:

``` text
speed > 100
```

AST:

``` text
BinaryExpression

unknown
```

After:

``` text
BinaryExpression

operator:

GREATER_THAN

left:

number

right:

number

result:

boolean
```

------------------------------------------------------------------------

# 8.22 AST Immutability

AST nodes are immutable.

Example:

Good:

``` java
BinaryExpressionNode optimized =
    node.replaceChildren(
        newLeft,
        newRight
    );
```

Bad:

``` java
node.setLeft(newLeft);
```

------------------------------------------------------------------------

Benefits:

-   safe optimization
-   parallel compilation
-   deterministic behavior

------------------------------------------------------------------------

# 8.23 AST Optimization

Some optimizations happen before Runtime IR generation.

Examples:

## Constant Folding

Input:

``` feel
10 + 20
```

AST:

``` text
+
10
20
```

Output:

``` text
30
```

------------------------------------------------------------------------

## Boolean Simplification

Input:

``` feel
x and true
```

Output:

``` feel
x
```

------------------------------------------------------------------------

## Dead Branch Removal

Input:

``` feel
if true
then A
else B
```

Output:

``` feel
A
```

------------------------------------------------------------------------

# 8.24 AST → Runtime IR Lowering

Transformation:

``` text
FEEL AST

BinaryExpression

operator:

>

left:

speed

right:

100

        |

        v

Runtime IR

LOAD_VARIABLE speed

LOAD_CONSTANT 100

COMPARE_GREATER_THAN
```

------------------------------------------------------------------------

# 8.25 FEEL AST Testing

Tests include:

## Parser tests

``` text
FEEL

↓

AST
```

------------------------------------------------------------------------

## Visitor tests

Every node is visited correctly.

------------------------------------------------------------------------

## Type tests

``` text
Expression

↓

Type
```

------------------------------------------------------------------------

## Optimization tests

``` text
Before

↓

After
```

------------------------------------------------------------------------

# 8.26 Summary

The FEEL AST provides:

-   language-level representation
-   parser independence
-   optimization foundation
-   type analysis foundation
-   backend-independent expression model

The compiler pipeline now becomes:

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

Runtime IR

   |

   v

Generated Code
```

------------------------------------------------------------------------

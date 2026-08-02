# Chapter 17 — Public API [FUTURE]

## 17.1 Purpose

The Public API defines the stable integration boundary between users and
the DMN Compiler Toolkit.

The architecture separates:

-   internal compiler implementation
-   public developer-facing interfaces

Users should not need to understand:

-   XML parsing
-   FEEL parsing
-   compiler passes
-   optimization
-   Runtime IR internals

The public API provides a simple abstraction:

``` text
DMN Model

     |

     v

Compiler API

     |

     v

Executable Decision Model
```

------------------------------------------------------------------------

# 17.2 API Design Principles

## API-001 --- Simple Entry Point

The common use case should require minimal code.

Example:

``` java
Compiler compiler =
    Compiler.builder()
            .build();

DecisionModel model =
    compiler.compile(
        Path.of("traffic.dmn")
    );
```

------------------------------------------------------------------------

## API-002 --- Hide Compiler Internals

Application code should never directly access:

``` java
XmlReader

FeelParser

SemanticAnalyzer

Optimizer

RuntimeBuilder
```

------------------------------------------------------------------------

Instead:

``` java
Compiler
```

is the facade.

------------------------------------------------------------------------

## API-003 --- Stable Contracts

Public interfaces evolve slowly.

Internal modules may change:

``` text
VTD-XML

↓

different XML parser
```

without affecting users.

------------------------------------------------------------------------

# 17.3 Public API Modules

Recommended module:

``` text
dmn-api
```

Dependencies:

``` text
dmn-runtime-api

dmn-model-api
```

The API module must NOT depend on:

``` text
antlr

vtd-xml

compiler passes
```

------------------------------------------------------------------------

Package:

``` text
io.finmsg.dmn.api
```

Structure:

``` text
api

├── Compiler

├── CompilerConfiguration

├── CompilationResult

├── DecisionModel

├── DecisionExecutor

├── Diagnostic

├── Backend

└── Version
```

------------------------------------------------------------------------

# 17.4 Compiler API

Main entry point:

``` java
public interface Compiler {

    CompilationResult compile(
        Source source
    );

}
```

------------------------------------------------------------------------

Example:

``` java
CompilationResult result =
    compiler.compile(
        Source.file(
            "traffic.dmn"
        )
    );
```

------------------------------------------------------------------------

# 17.5 Compiler Builder

Configuration is explicit.

Example:

``` java
Compiler compiler =
    Compiler.builder()

        .backend(
            Backend.JAVA
        )

        .optimization(
            OptimizationLevel.MAX
        )

        .build();
```

------------------------------------------------------------------------

Possible options:

``` text
backend

optimization level

diagnostic mode

cache

security limits

source mapping
```

------------------------------------------------------------------------

# 17.6 Compilation Result

Compilation returns a structured result.

Example:

``` java
public interface CompilationResult {

    boolean successful();

    DecisionModel model();

    List<Diagnostic> diagnostics();

}
```

------------------------------------------------------------------------

Usage:

``` java
if(result.successful()) {

    DecisionModel model =
        result.model();

}
```

------------------------------------------------------------------------

# 17.7 Diagnostics API

Compiler errors are first-class objects.

Example:

``` java
public record Diagnostic(

    Severity severity,

    String code,

    String message,

    SourceLocation location

){}
```

------------------------------------------------------------------------

Example result:

``` text
ERROR DMN-2004

Unknown variable:

speedLimit

traffic.dmn

line 25
```

------------------------------------------------------------------------

# 17.8 Decision Model API

The compiled decision model represents executable output.

Example:

``` java
public interface DecisionModel {

    String name();

    DecisionExecutor executor();

}
```

------------------------------------------------------------------------

The application does not know whether the model came from:

``` text
Java generation

Bytecode

Interpreter

Native backend
```

------------------------------------------------------------------------

# 17.9 Decision Execution API

Runtime API:

``` java
public interface DecisionExecutor {

    DecisionResult execute(
        DecisionInput input
    );

}
```

------------------------------------------------------------------------

Example:

``` java
DecisionResult result =
    executor.execute(
        input
    );
```

------------------------------------------------------------------------

# 17.10 Input API

Two supported modes.

------------------------------------------------------------------------

## Generic Input

Flexible:

``` java
DecisionInput input =
    DecisionInput.builder()

        .put(
            "speed",
            120
        )

        .build();
```

------------------------------------------------------------------------

## Generated Input

High-performance:

``` java
TrafficInput input =
    new TrafficInput(
        120
    );
```

------------------------------------------------------------------------

# 17.11 Result API

Example:

``` java
public interface DecisionResult {

    Object value();

    Map<String,Object> outputs();

}
```

------------------------------------------------------------------------

Example:

``` java
Penalty penalty =
    result.value();
```

------------------------------------------------------------------------

# 17.12 Backend Selection

The API supports multiple targets.

Example:

``` java
Compiler compiler =
    Compiler.builder()

        .backend(
            Backend.RUST
        )

        .build();
```

------------------------------------------------------------------------

Available:

``` text
JAVA

RUST

GO

SPARK_SQL

WASM
```

------------------------------------------------------------------------

# 17.13 Compilation Pipeline API

Advanced users may access pipeline stages.

Example:

``` java
CompilerPipeline pipeline =
    PipelineBuilder.create()

        .xmlFrontend()

        .feelParser()

        .optimizer()

        .runtimeBuilder()

        .build();
```

------------------------------------------------------------------------

This API is considered advanced.

------------------------------------------------------------------------

# 17.14 Streaming Compilation API

For large environments:

Example:

``` java
compiler.compile(
    InputStream stream
);
```

------------------------------------------------------------------------

Benefits:

-   lower memory
-   CI/CD integration
-   repository scanning

------------------------------------------------------------------------

# 17.15 Incremental Compilation API

Large repositories require incremental builds.

Example:

``` java
CompilationCache cache =
    new CompilationCache();

compiler.compileIncremental(
    source,
    cache
);
```

------------------------------------------------------------------------

Only changed artifacts are rebuilt.

------------------------------------------------------------------------

# 17.16 Maven Plugin API

The compiler integrates into builds.

Example:

``` xml
<plugin>

    <groupId>
        io.finmsg.dmn
    </groupId>

    <artifactId>
        dmn-maven-plugin
    </artifactId>

</plugin>
```

------------------------------------------------------------------------

Build:

``` text
mvn compile
```

Pipeline:

``` text
DMN Files

    |

    v

Compiler

    |

    v

Generated Java

    |

    v

javac

    |

    v

Application
```

------------------------------------------------------------------------

# 17.17 CLI API

Command-line access:

Example:

``` bash
dmn compile traffic.dmn
```

------------------------------------------------------------------------

Options:

``` bash
--backend java

--optimize max

--output target/generated

--diagnostics verbose
```

------------------------------------------------------------------------

Example:

``` bash
dmn inspect traffic.dmn
```

shows:

``` text
Decisions:

Penalty

Dependencies:

SpeedCheck -> Penalty

Runtime IR size:

3.2 KB
```

------------------------------------------------------------------------

# 17.18 IDE Integration

Future support:

-   IntelliJ plugin
-   VS Code extension
-   Language Server Protocol

The API enables:

``` text
Editor

 |

 v

Compiler Diagnostics API

 |

 v

Compiler
```

------------------------------------------------------------------------

# 17.19 Versioning Strategy

Public API follows semantic versioning.

Example:

``` text
1.0.0
```

Meaning:

``` text
Major

breaking changes

Minor

new features

Patch

bug fixes
```

------------------------------------------------------------------------

# 17.20 API Compatibility Rules

Allowed:

``` text
Add new methods

Add new backends

Add new diagnostics
```

------------------------------------------------------------------------

Breaking:

``` text
Remove interfaces

Change method signatures

Change result semantics
```

------------------------------------------------------------------------

# 17.21 Example Complete Application

Application code:

``` java
public class Application {

public static void main(
    String[] args
){

Compiler compiler =
    Compiler.builder()
            .build();

DecisionModel model =
    compiler.compile(
        Path.of(
            "traffic.dmn"
        )
    )
    .model();

DecisionResult result =
    model.executor()
         .execute(
             input
         );

System.out.println(
    result.value()
);

}

}
```

------------------------------------------------------------------------

# 17.22 Public API Summary

The public API provides:

-   simple compiler entry point
-   stable integration boundary
-   backend independence
-   structured diagnostics
-   runtime execution abstraction
-   Maven integration
-   CLI support
-   future IDE integration

The user sees:

``` text
DMN File

   |

   v

Compiler API

   |

   v

Decision Result
```

The internal compiler remains free to evolve.

------------------------------------------------------------------------

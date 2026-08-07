# Chapter 1 — Vision [NORMATIVE]

<!-- generated-toc:start -->
## Table of contents

- [Why another DMN implementation?](#contents-section-1)
<!-- generated-toc:end -->


<a id="contents-section-1"></a>
## Why another DMN implementation?

Most existing DMN implementations focus on XML processing and runtime
interpretation. While this approach faithfully implements the DMN
specification, it often results in runtimes that repeatedly perform
parsing, validation, type resolution, dependency analysis, and
expression interpretation during execution.

The DMN Compiler Toolkit takes a fundamentally different approach.

The toolkit treats DMN as a programming language that should be compiled
rather than interpreted. XML is regarded solely as an interchange
format. During compilation, the XML model is transformed into a semantic
representation, analyzed, optimized, and finally converted into a
compact Runtime Intermediate Representation (Runtime IR). Runtime
execution operates exclusively on this optimized representation.

The objective is to provide a modern compiler infrastructure capable of
generating highly optimized executable artifacts for multiple target
platforms while remaining fully compatible with the OMG DMN
specification.

<a id="contents-section-2"></a>
### Goals

-   High-performance DMN compiler
-   Modern compiler architecture
-   XML-independent runtime
-   Multi-language code generation
-   Open-source quality
-   Extensible backend architecture
-   Deterministic compilation
-   Small memory footprint
-   Excellent developer experience
-   Clear separation of concerns
-   Long-term maintainability

<a id="contents-section-3"></a>
### Non-goals

-   Graphical editor
-   BPMN execution
-   Workflow engine
-   XML object model
-   Reflection-based runtime
-   Vendor-specific execution engines

------------------------------------------------------------------------

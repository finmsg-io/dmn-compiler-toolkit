# Chapter 1 — Vision [NORMATIVE]

<!-- generated-toc:start -->
## Table of contents

- [1.1 Purpose and Architectural Philosophy](#contents-section-1)
- [1.2 Strategic Architectural Goals](#contents-section-2)
- [1.3 Explicit Non-Goals](#contents-section-3)
<!-- generated-toc:end -->

<a id="contents-section-1"></a>
## 1.1 Purpose and Architectural Philosophy

Most existing DMN implementations focus on XML processing and runtime interpretation. While this approach faithfully implements the DMN specification, it often results in runtimes that repeatedly perform parsing, validation, type resolution, dependency analysis, and expression interpretation during execution.

The DMN Compiler Toolkit takes a fundamentally different approach:

The toolkit treats DMN as a programming language that should be compiled rather than interpreted. XML is regarded solely as an interchange format. During compilation, the XML model is transformed into a semantic representation, analyzed, optimized, and finally converted into a compact Runtime Intermediate Representation (Runtime IR). Runtime execution operates exclusively on this optimized representation.

The objective is to provide a modern compiler infrastructure capable of generating highly optimized executable artifacts for multiple target platforms while remaining fully compatible with the OMG DMN specification.

<a id="contents-section-2"></a>
## 1.2 Strategic Architectural Goals

- **High-Performance DMN Compiler**: Native-speed compilation and sub-microsecond evaluation latency.
- **Modern Compiler Architecture**: Multi-pass lowering, SSA-style Runtime IR, and dead-code elimination.
- **XML-Independent Runtime**: Zero XML parsers, DOM trees, or schema dependencies at runtime.
- **Multi-Language Code Generation**: Pluggable backend code generators (Pure Java, Spark SQL, LLVM, WASM).
- **Deterministic Compilation**: Byte-identical output for identical inputs and configuration.
- **Small Memory Footprint**: Compact, primitive-slot-based in-memory representations.
- **Clear Separation of Concerns**: Decoupled frontend, semantic analysis, optimizer, and generators.
- **Long-Term Maintainability**: Strict modular boundaries, hermetic builds, and high test coverage.

<a id="contents-section-3"></a>
## 1.3 Explicit Non-Goals

- **Graphical DMN Editor**: Visual modeling is left to specialized third-party modeling tools.
- **BPMN / Workflow Engine**: Execution is strictly focused on stateless DMN decision logic.
- **DOM / JAXB Object Model**: In-memory XML representations are strictly excluded from runtime.
- **Reflection-Based Dynamic Execution**: No runtime reflection, bytecode weaving, or dynamic proxy invocation.
- **Vendor-Specific Extensions**: Adherence to standard OMG DMN 1.5 without proprietary lock-in.


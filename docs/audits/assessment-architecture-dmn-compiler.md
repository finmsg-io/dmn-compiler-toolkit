# `dmn-compiler` architecture assessment

<!-- generated-toc:start -->
## Table of contents

- [Assessment](#contents-section-1)
- [Target orchestration flow](#contents-section-2)
- [Improvement opportunities](#contents-section-3)
<!-- generated-toc:end -->

Serialized on: 2026-08-02  
Source: referenced ChatGPT conversation “Architecture Assessment”

This document preserves an architectural opinion from the referenced conversation. It is not a
fresh implementation verification; consult the [implementation assessment](assessment-implementation-dmn-compiler.md)
and current [module TODO](../todos/dmn-compiler.md) for evidence-backed status. In particular, the
flow below is a target: the current verified module contains resolver contracts and implementations,
but not yet the complete loader and compiler facade.

<a id="contents-section-1"></a>
## Assessment

| Dimension | Rating |
| --- | --- |
| Architecture | ★★★★★ |
| Responsibility | ★★★★★ |
| Coupling | ★★★★★ |
| Cohesion | ★★★★★ |
| Maturity | Excellent architectural direction |

The conversation identifies `dmn-compiler` as the missing top-level orchestration layer and states
the key dependency rule: the compiler may know every stage, but no stage should know the compiler.

<a id="contents-section-2"></a>
## Target orchestration flow

```text
resolver
  -> loader
  -> XML frontend
  -> FEEL parser
  -> semantic analysis
  -> Runtime IR
  -> generator or runtime backend
```

<a id="contents-section-3"></a>
## Improvement opportunities

- Introduce a compiler backend/generator SPI when a second backend makes the abstraction concrete.
- Consider `CompilerPass`, `PassManager`, and `PassContext` when optimization composition requires it.
- Normalize stage diagnostics into one compiler-facing diagnostic model.
- Use an immutable compiler context/options object without turning it into a service locator.
- Keep future Java, C++, Rust, or other generators replaceable without changing compiler stages.
